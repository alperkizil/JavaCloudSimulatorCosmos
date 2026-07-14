package com.cloudsimulator.observer;

import com.cloudsimulator.multiobjectivePerformance.PerfMet.PerformanceMetrics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single Java source of truth for the multi-objective quality indicators used
 * by the experiment campaigns.
 *
 * <p>The analyzer computes two families of indicators:</p>
 *
 * <ol>
 *   <li><b>Fixed-reference indicators</b> &mdash; {@code HV_fixed}, additive
 *       {@code epsilon+}, and {@code ParetoContribution_pct}. These are a faithful
 *       Java port of {@code scripts/recompute_hv.py} (the quality_indicators
 *       oracle): a 2D sweep-line hypervolume in normalized space against the
 *       fixed reference {@code (1.1, 1.1)} divided by {@code 1.21}; the union
 *       ideal/nadir computed over <em>all</em> points (including the
 *       {@code Universal_Pareto} rows, with no failure filtering); epsilon+
 *       normalized by the same union bounds and clamped at 0; and contribution
 *       as a percentage of the universal-set size. {@link #computeFixedIndicators}
 *       reproduces these so the parity test matches the Python oracle.</li>
 *
 *   <li><b>Legacy indicators</b> &mdash; {@code HV} (reference {@code (1,1)}),
 *       {@code GD}, {@code IGD}, {@code Spacing}. These feed the
 *       {@code performance_metrics} / {@code experiment_summary} CSV columns and
 *       are delegated to the existing {@link PerformanceMetrics} so those reports
 *       stay byte-compatible. (MOEA Framework cannot reproduce the fixed trio
 *       above &mdash; its hypervolume normalization and exact-match contribution
 *       differ from the Python oracle &mdash; and its {@code Spacing} ignores
 *       normalization, so it cannot be pinned to the union frame. Hence the
 *       legacy four are sourced from {@code PerformanceMetrics} rather than MOEA.)</li>
 * </ol>
 *
 * <p>The dominance convention is minimization of both objectives. Universal-front
 * construction and the integer contribution count mirror the runners
 * (weak Pareto dominance, de-duplication at 1e-9).</p>
 */
public final class ParetoAnalyzer {

    /** De-duplication tolerance used by the runners for raw objective points. */
    public static final double DEDUP_EPS = 1e-9;

    /**
     * Relative near-tie tolerance for the seed-collaboration CONTRIBUTION
     * CREDIT only (0.3%) — NOT used by union construction, de-duplication, or
     * the exact-match {@code ParetoContribution} columns, which all keep their
     * {@code DEDUP_EPS} semantics untouched. Empirically calibrated against
     * the committed campaign CSVs: 1e-4 recovers zero real near-ties across
     * all scenarios, 1e-3 essentially none; 3e-3 is the smallest value that
     * credits genuine floating-point/equally-good-schedule ties without
     * moving the dominant arms' counts. Re-validate against a fresh campaign
     * before publication.
     */
    public static final double CONTRIB_REL_EPS = 3e-3;

    /** Absolute floor for the near-tie tolerance, avoids collapse at values near 0. */
    private static final double CONTRIB_ABS_FLOOR = 1e-9;

    /** Floor applied to a normalization range (matches recompute_hv.py). */
    private static final double RANGE_FLOOR = 1e-12;

    /** Fixed normalized reference point used by recompute_hv.py. */
    private static final double REF_NORM = 1.1;

    /** Theoretical HV upper bound in the normalized frame: 1.1 * 1.1. */
    private static final double HV_MAX = REF_NORM * REF_NORM;

    private ParetoAnalyzer() {
    }

    // =========================================================================
    // Fixed-reference indicators (recompute_hv.py parity)
    // =========================================================================

    /** Holder for the three fixed-reference indicators. */
    public static final class FixedIndicators {
        public final double hvFixed;
        public final double epsilonPlus;
        public final double contributionPct;
        public final int nSolutions;

        FixedIndicators(double hvFixed, double epsilonPlus, double contributionPct, int nSolutions) {
            this.hvFixed = hvFixed;
            this.epsilonPlus = epsilonPlus;
            this.contributionPct = contributionPct;
            this.nSolutions = nSolutions;
        }
    }

    /**
     * Computes the union ideal/nadir bounds over all supplied points (no
     * filtering), replicating recompute_hv.py lines 130-133.
     *
     * @param allPoints every point, including any {@code Universal_Pareto} rows
     * @return {@code [idealX, idealY, nadirX, nadirY]}
     * @throws IllegalArgumentException if {@code allPoints} is empty
     */
    public static double[] unionBounds(List<double[]> allPoints) {
        if (allPoints == null || allPoints.isEmpty()) {
            throw new IllegalArgumentException("cannot compute bounds over an empty point set");
        }
        double idealX = Double.POSITIVE_INFINITY, idealY = Double.POSITIVE_INFINITY;
        double nadirX = Double.NEGATIVE_INFINITY, nadirY = Double.NEGATIVE_INFINITY;
        for (double[] p : allPoints) {
            idealX = Math.min(idealX, p[0]);
            idealY = Math.min(idealY, p[1]);
            nadirX = Math.max(nadirX, p[0]);
            nadirY = Math.max(nadirY, p[1]);
        }
        return new double[] {idealX, idealY, nadirX, nadirY};
    }

    /**
     * Normalizes a point to the union frame: {@code (v - ideal) / max(nadir-ideal, 1e-12)}.
     */
    public static double[] normalizePoint(double[] p, double[] bounds) {
        double rx = Math.max(bounds[2] - bounds[0], RANGE_FLOOR);
        double ry = Math.max(bounds[3] - bounds[1], RANGE_FLOOR);
        return new double[] {(p[0] - bounds[0]) / rx, (p[1] - bounds[1]) / ry};
    }

    /** Normalizes a list of points to the union frame. */
    public static List<double[]> normalize(List<double[]> points, double[] bounds) {
        List<double[]> out = new ArrayList<>(points.size());
        for (double[] p : points) {
            out.add(normalizePoint(p, bounds));
        }
        return out;
    }

    /**
     * Non-dominated sort with 9-decimal de-duplication, sorted ascending by x.
     * Mirrors recompute_hv.py {@code non_dominated_sort} (both objectives
     * minimized).
     */
    public static List<double[]> nonDominatedSort(List<double[]> points) {
        List<double[]> kept = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            double[] p = points.get(i);
            boolean dominated = false;
            for (int j = 0; j < points.size(); j++) {
                if (i == j) {
                    continue;
                }
                double[] q = points.get(j);
                if (q[0] <= p[0] && q[1] <= p[1] && (q[0] < p[0] || q[1] < p[1])) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                kept.add(p);
            }
        }
        // De-duplicate at 9 decimals, preserving first occurrence.
        List<double[]> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (double[] p : kept) {
            if (seen.add(round9Key(p[0], p[1]))) {
                deduped.add(p);
            }
        }
        deduped.sort((a, b) -> Double.compare(a[0], b[0]));
        return deduped;
    }

    /**
     * 2D hypervolume for minimization against the reference {@code (refX, refY)}.
     * Mirrors recompute_hv.py {@code hv_2d} (filters to non-dominated internally).
     */
    public static double hv2d(List<double[]> points, double refX, double refY) {
        List<double[]> nd = nonDominatedSort(points);
        List<double[]> inside = new ArrayList<>();
        for (double[] p : nd) {
            if (p[0] < refX && p[1] < refY) {
                inside.add(p);
            }
        }
        if (inside.isEmpty()) {
            return 0.0;
        }
        inside.sort((a, b) -> Double.compare(a[0], b[0]));
        double hv = 0.0;
        double prevY = refY;
        for (double[] p : inside) {
            hv += (refX - p[0]) * (prevY - p[1]);
            prevY = p[1];
        }
        return hv;
    }

    /**
     * HV_fixed of an already-normalized point set (the recompute_hv.py math:
     * sweep-line HV against {@code (1.1, 1.1)} divided by {@code 1.21}).
     * Shared by {@link #computeFixedIndicators} and the universal-front /
     * per-seed-front HV_fixed values emitted by {@link #analyzeScenario} and
     * {@link #analyzeSeedCollaboration}.
     */
    private static double hvFixedFromNormalized(List<double[]> normalizedPoints) {
        return hv2d(normalizedPoints, REF_NORM, REF_NORM) / HV_MAX;
    }

    /**
     * Additive epsilon indicator I_eps+(A, R): the minimum eps such that every
     * point in R is weakly dominated by some {@code (a - eps)} for {@code a} in A.
     * Inputs are expected normalized; minimization. Clamped at 0 and returns
     * {@code NaN} if either set is empty. Mirrors recompute_hv.py {@code epsilon_plus}.
     */
    public static double epsilonPlus(List<double[]> approx, List<double[]> reference) {
        if (approx == null || reference == null || approx.isEmpty() || reference.isEmpty()) {
            return Double.NaN;
        }
        double eps = 0.0;
        for (double[] rp : reference) {
            double best = Double.POSITIVE_INFINITY;
            for (double[] ap : approx) {
                double d = Math.max(ap[0] - rp[0], ap[1] - rp[1]);
                if (d < best) {
                    best = d;
                }
            }
            if (best > eps) {
                eps = best;
            }
        }
        return eps;
    }

    /**
     * Computes {@code HV_fixed}, {@code epsilon+} and {@code ParetoContribution_pct}
     * for one run's points against the universal set, given precomputed union
     * bounds. This is the exact analogue of recompute_hv.py's per-run loop body
     * and is what the parity test exercises.
     *
     * @param runPoints       this run's raw objective points (not pre-filtered)
     * @param universalPoints the universal-Pareto points (raw)
     * @param bounds          {@code [idealX, idealY, nadirX, nadirY]} over the union
     */
    public static FixedIndicators computeFixedIndicators(
            List<double[]> runPoints, List<double[]> universalPoints, double[] bounds) {

        List<double[]> runNorm = normalize(runPoints, bounds);
        List<double[]> univNorm = universalPoints.isEmpty()
            ? new ArrayList<>() : normalize(universalPoints, bounds);

        double hvFixed = hvFixedFromNormalized(runNorm);

        double epsPlus = univNorm.isEmpty() ? Double.NaN : epsilonPlus(runNorm, univNorm);

        double contributionPct;
        int univCount = universalPoints.size();
        if (univCount > 0) {
            Set<String> univSig = new HashSet<>();
            for (double[] u : univNorm) {
                univSig.add(round9Key(u[0], u[1]));
            }
            int matched = 0;
            for (double[] p : runNorm) {
                if (univSig.contains(round9Key(p[0], p[1]))) {
                    matched++;
                }
            }
            contributionPct = 100.0 * matched / univCount;
        } else {
            contributionPct = Double.NaN;
        }

        return new FixedIndicators(hvFixed, epsPlus, contributionPct, runPoints.size());
    }

    /**
     * Rounds two coordinates to 9 decimals (round-half-to-even, mirroring numpy)
     * and returns a stable key for set membership.
     */
    private static String round9Key(double x, double y) {
        long kx = (long) Math.rint(x * 1e9);
        long ky = (long) Math.rint(y * 1e9);
        return kx + "_" + ky;
    }

    // =========================================================================
    // Legacy indicators (delegated to PerformanceMetrics)
    // =========================================================================

    /** Holder for the legacy HV/GD/IGD/Spacing indicators. */
    public static final class LegacyIndicators {
        public final double hv;
        public final double gd;
        public final double igd;
        public final double spacing;

        LegacyIndicators(double hv, double gd, double igd, double spacing) {
            this.hv = hv;
            this.gd = gd;
            this.igd = igd;
            this.spacing = spacing;
        }
    }

    /**
     * Computes legacy HV/GD/IGD/Spacing for a seed's (non-dominated) front against
     * the universal reference, delegating to {@link PerformanceMetrics} exactly as
     * the runners do (so the CSV columns stay byte-compatible). On any failure it
     * returns the runners' fallback values ({@code hv=0}, {@code gd=igd=MAX},
     * {@code spacing=0}).
     *
     * @param seedNonDomFront the seed's non-dominated front (may be empty)
     * @param universalFront  the universal reference front (must be non-empty)
     */
    public static LegacyIndicators computeLegacyIndicators(
            List<double[]> seedNonDomFront, List<double[]> universalFront) {

        if (universalFront == null || universalFront.isEmpty()) {
            return new LegacyIndicators(0.0, Double.MAX_VALUE, Double.MAX_VALUE, 0.0);
        }

        ArrayList<ArrayList<Double>> seedList = toPointList(seedNonDomFront);
        if (seedList.isEmpty()) {
            // Mirror the runner's dummy-point fallback for an empty seed front.
            ArrayList<Double> dummy = new ArrayList<>();
            dummy.add(Double.MAX_VALUE);
            dummy.add(Double.MAX_VALUE);
            seedList.add(dummy);
        }
        ArrayList<ArrayList<Double>> universalList = toPointList(universalFront);

        ArrayList<ArrayList<ArrayList<Double>>> pair = new ArrayList<>();
        pair.add(seedList);
        pair.add(universalList);

        try {
            PerformanceMetrics pm = new PerformanceMetrics(pair, 1);
            double hv = pm.HV(0);
            double gd = pm.GD(0);
            double igd = pm.IGD(0);
            double spacing = (seedNonDomFront != null && seedNonDomFront.size() > 1)
                ? pm.Spacing(0) : 0.0;
            return new LegacyIndicators(hv, gd, igd, spacing);
        } catch (Exception e) {
            return new LegacyIndicators(0.0, Double.MAX_VALUE, Double.MAX_VALUE, 0.0);
        }
    }

    /**
     * Computes the universal-front hypervolume the way the runners do
     * ({@code PerformanceMetrics(universalOnly, 0).HV(0)}). Returns 0 for an
     * empty universal front.
     */
    public static double universalHV(List<double[]> universalFront) {
        if (universalFront == null || universalFront.isEmpty()) {
            return 0.0;
        }
        ArrayList<ArrayList<ArrayList<Double>>> universalOnly = new ArrayList<>();
        universalOnly.add(toPointList(universalFront));
        try {
            return new PerformanceMetrics(universalOnly, 0).HV(0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static ArrayList<ArrayList<Double>> toPointList(List<double[]> front) {
        ArrayList<ArrayList<Double>> list = new ArrayList<>();
        if (front != null) {
            for (double[] sol : front) {
                ArrayList<Double> point = new ArrayList<>();
                point.add(sol[0]);
                point.add(sol[1]);
                list.add(point);
            }
        }
        return list;
    }

    // =========================================================================
    // Universal-front construction & contribution (mirrors the runners)
    // =========================================================================

    /** Weak Pareto dominance for minimization: does {@code a} dominate {@code b}? */
    public static boolean dominates(double[] a, double[] b) {
        boolean atLeastOneBetter = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > b[i]) {
                return false;
            }
            if (a[i] < b[i]) {
                atLeastOneBetter = true;
            }
        }
        return atLeastOneBetter;
    }

    /** Failure sentinel: {@code [MAX, MAX]} or malformed point (excluded from analytics). */
    public static boolean isFailureSentinel(double[] sol) {
        return sol == null || sol.length < 2
            || sol[0] >= Double.MAX_VALUE || sol[1] >= Double.MAX_VALUE;
    }

    private static boolean matchesWithinEps(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < DEDUP_EPS && Math.abs(a[1] - b[1]) < DEDUP_EPS;
    }

    /**
     * Filters to the non-dominated set with 1e-9 de-duplication, sorted by x
     * (mirrors the runners' {@code filterToNonDominated}).
     */
    public static List<double[]> filterToNonDominated(List<double[]> solutions) {
        List<double[]> input = new ArrayList<>();
        for (double[] sol : solutions) {
            if (!isFailureSentinel(sol)) {
                input.add(sol);
            }
        }
        if (input.size() <= 1) {
            return input;
        }
        List<double[]> nonDominated = new ArrayList<>();
        for (double[] candidate : input) {
            boolean isDominated = false;
            for (double[] other : input) {
                if (candidate != other && dominates(other, candidate)) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                boolean duplicate = false;
                for (double[] existing : nonDominated) {
                    if (matchesWithinEps(existing, candidate)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    nonDominated.add(candidate);
                }
            }
        }
        nonDominated.sort((a, b) -> Double.compare(a[0], b[0]));
        return nonDominated;
    }

    /**
     * Builds the universal (union) Pareto front over all runs' fronts: collect
     * every non-failure point, keep the non-dominated ones, de-duplicate at 1e-9,
     * sort by x. Mirrors the runners' {@code computeUniversalPareto}.
     */
    public static List<double[]> computeUniversalPareto(List<AlgorithmRunResult> runs) {
        return nonDominatedUnion(runs);
    }

    /**
     * Builds one algorithm's aggregate Pareto front: pools that algorithm's
     * points across all of its seeds/runs, keeps the non-dominated ones,
     * de-duplicates at 1e-9, sorts by x. Same union logic as
     * {@link #computeUniversalPareto} but scoped to a single algorithm's runs
     * (e.g. NSGA-II over seeds 100&ndash;109).
     */
    public static List<double[]> computeAlgorithmFront(List<AlgorithmRunResult> labelRuns) {
        return nonDominatedUnion(labelRuns);
    }

    /** Shared "pool &rarr; non-dominated &rarr; dedup(1e-9) &rarr; sort by x" over a set of runs. */
    private static List<double[]> nonDominatedUnion(List<AlgorithmRunResult> runs) {
        List<double[]> all = new ArrayList<>();
        for (AlgorithmRunResult run : runs) {
            for (double[] sol : run.getFront()) {
                if (!isFailureSentinel(sol)) {
                    all.add(sol);
                }
            }
        }
        List<double[]> union = new ArrayList<>();
        for (double[] candidate : all) {
            boolean isDominated = false;
            for (double[] other : all) {
                if (candidate != other && dominates(other, candidate)) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                boolean duplicate = false;
                for (double[] existing : union) {
                    if (matchesWithinEps(existing, candidate)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    union.add(candidate);
                }
            }
        }
        union.sort((a, b) -> Double.compare(a[0], b[0]));
        return union;
    }

    /**
     * Counts the distinct universal-Pareto points matched by a run's points
     * (1e-9 tolerance) &mdash; the integer {@code ParetoContribution} column.
     */
    public static int paretoContributionCount(List<double[]> runPoints, List<double[]> universal) {
        Set<Integer> matched = new HashSet<>();
        for (double[] sol : runPoints) {
            if (isFailureSentinel(sol)) {
                continue;
            }
            for (int i = 0; i < universal.size(); i++) {
                if (matchesWithinEps(universal.get(i), sol)) {
                    matched.add(i);
                    break;
                }
            }
        }
        return matched.size();
    }

    /** @return {@code true} if {@code sol} matches any universal point within 1e-9. */
    public static boolean isInUniversalPareto(double[] sol, List<double[]> universal) {
        if (isFailureSentinel(sol)) {
            return false;
        }
        for (double[] u : universal) {
            if (matchesWithinEps(u, sol)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts the distinct universal points matched by <em>any</em> of a label's
     * runs (the union across seeds) &mdash; the integer used in the MEAN rows.
     */
    public static int unionContributionCount(List<AlgorithmRunResult> labelRuns, List<double[]> universal) {
        Set<Integer> matched = new HashSet<>();
        for (AlgorithmRunResult run : labelRuns) {
            for (double[] sol : run.getFront()) {
                if (isFailureSentinel(sol)) {
                    continue;
                }
                for (int i = 0; i < universal.size(); i++) {
                    if (matchesWithinEps(universal.get(i), sol)) {
                        matched.add(i);
                        break;
                    }
                }
            }
        }
        return matched.size();
    }

    // =========================================================================
    // Per-seed collaboration (near-tie credit) — additive, new report only
    // =========================================================================

    /**
     * Index of the nearest universal point within {@link #CONTRIB_REL_EPS}
     * relative tolerance on BOTH objectives (each objective's tolerance scaled
     * to that universal point's own magnitude, floored at
     * {@code CONTRIB_ABS_FLOOR}), or {@code null} if none is within tolerance.
     *
     * <p>Nearest-match — not first-match in iteration order — is required to
     * keep contribution credit monotonic in the tolerance: under first-match,
     * widening the tolerance can steal credit from an index a point used to
     * match exactly, because the widened search finds an earlier, unrelated
     * universal point first and stops.</p>
     */
    private static Integer nearestWithinRelEps(double[] p, List<double[]> universal, double relEps) {
        Integer bestIdx = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < universal.size(); i++) {
            double[] u = universal.get(i);
            boolean withinTolerance = true;
            double score = 0.0;
            for (int k = 0; k < 2; k++) {
                double tol = Math.max(Math.abs(u[k]) * relEps, CONTRIB_ABS_FLOOR);
                double d = Math.abs(p[k] - u[k]);
                if (d > tol) {
                    withinTolerance = false;
                    break;
                }
                double r = d / tol;
                score += r * r;
            }
            if (withinTolerance && score < bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Near-tie CONTRIBUTION CREDIT for the seed-collaboration scoreboard ONLY:
     * counts the distinct universal points that some run point lies within
     * {@link #CONTRIB_REL_EPS} of on both objectives (nearest-match). Does NOT
     * affect union construction or the exact-match
     * {@code ParetoContribution}/{@code ParetoContribution_pct} columns, which
     * keep their 1e-9 semantics untouched.
     */
    public static int paretoContributionCountEps(List<double[]> runPoints, List<double[]> universal) {
        Set<Integer> matched = new HashSet<>();
        for (double[] sol : runPoints) {
            if (isFailureSentinel(sol)) {
                continue;
            }
            Integer idx = nearestWithinRelEps(sol, universal, CONTRIB_REL_EPS);
            if (idx != null) {
                matched.add(idx);
            }
        }
        return matched.size();
    }

    /** One seed's all-arms universal front and each label's near-tie credit against it. */
    public static final class SeedCollaboration {
        public final long seed;
        public final List<double[]> seedUniversalFront;
        /** HV_fixed of this seed's universal front (scenario-wide union bounds). */
        public final double seedUniversalHvFixed;
        /** Algorithm label &rarr; near-tie-credited contribution count to THIS seed's front. */
        public final Map<String, Integer> contributionCounts;
        /** Algorithm label &rarr; {@code 100 * count / seedUniversalFront.size()}. */
        public final Map<String, Double> contributionPct;

        SeedCollaboration(long seed, List<double[]> seedUniversalFront, double seedUniversalHvFixed,
                          Map<String, Integer> contributionCounts, Map<String, Double> contributionPct) {
            this.seed = seed;
            this.seedUniversalFront = seedUniversalFront;
            this.seedUniversalHvFixed = seedUniversalHvFixed;
            this.contributionCounts = contributionCounts;
            this.contributionPct = contributionPct;
        }
    }

    /**
     * Per-seed collaboration analysis: groups {@code scenarioRuns} by
     * {@link AlgorithmRunResult#getSeed()} (all arms share seed values) and,
     * for each seed, builds THAT seed's all-arms universal front by reusing
     * {@link #computeUniversalPareto} — the same strict-dominance, 1e-9-dedup
     * union as the scenario-wide front, just scoped to one seed's runs — then
     * credits each label's contribution with near-tie matching
     * ({@link #paretoContributionCountEps}).
     *
     * <p>Unlike the scenario-wide universal front (a best-of-all-seeds
     * envelope that one lucky seed can colonize), this yields a per-seed
     * contribution share whose mean&plusmn;std across seeds is distributional
     * evidence of collaboration.</p>
     *
     * @param scenarioRuns all runs of one scenario
     * @param bounds       scenario-wide union bounds (for HV_fixed of each
     *                     seed front), or {@code null} to skip HV_fixed
     */
    public static List<SeedCollaboration> analyzeSeedCollaboration(
            List<AlgorithmRunResult> scenarioRuns, double[] bounds) {
        Map<Long, List<AlgorithmRunResult>> bySeed = new LinkedHashMap<>();
        for (AlgorithmRunResult run : scenarioRuns) {
            bySeed.computeIfAbsent(run.getSeed(), k -> new ArrayList<>()).add(run);
        }
        List<SeedCollaboration> out = new ArrayList<>();
        for (Map.Entry<Long, List<AlgorithmRunResult>> e : bySeed.entrySet()) {
            List<double[]> seedUniv = computeUniversalPareto(e.getValue());
            double hvFixed = (bounds == null || seedUniv.isEmpty())
                ? Double.NaN : hvFixedFromNormalized(normalize(seedUniv, bounds));
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (AlgorithmRunResult run : e.getValue()) {
                counts.merge(run.getLabel(),
                    paretoContributionCountEps(run.getFront(), seedUniv), Integer::sum);
            }
            int univSize = seedUniv.size();
            Map<String, Double> pct = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> ce : counts.entrySet()) {
                pct.put(ce.getKey(), univSize > 0 ? 100.0 * ce.getValue() / univSize : Double.NaN);
            }
            out.add(new SeedCollaboration(e.getKey(), seedUniv, hvFixed, counts, pct));
        }
        out.sort((x, y) -> Long.compare(x.seed, y.seed));
        return out;
    }

    // =========================================================================
    // Scenario-level analysis (live path)
    // =========================================================================

    /** Per-scenario analysis output. */
    public static final class ScenarioAnalysis {
        public final List<double[]> universalFront;
        public final double universalHV;
        /** Algorithm label &rarr; that algorithm's aggregate (union-over-seeds) front. */
        public final Map<String, List<double[]>> algorithmFronts;
        /** Per-seed all-arms collaboration (near-tie credit), sorted by seed. */
        public final List<SeedCollaboration> seedCollaboration;
        /** HV_fixed of the scenario-wide universal front (NaN if empty). */
        public final double universalHvFixed;

        ScenarioAnalysis(List<double[]> universalFront, double universalHV,
                         Map<String, List<double[]>> algorithmFronts,
                         List<SeedCollaboration> seedCollaboration,
                         double universalHvFixed) {
            this.universalFront = universalFront;
            this.universalHV = universalHV;
            this.algorithmFronts = algorithmFronts;
            this.seedCollaboration = seedCollaboration;
            this.universalHvFixed = universalHvFixed;
        }
    }

    /**
     * Analyses one scenario's runs in place: builds the universal front and union
     * bounds, then fills each {@link AlgorithmRunResult}'s indicator fields
     * (fixed-reference trio + legacy four + contribution count + non-dominated
     * front). Returns the universal front and its hypervolume for reporting.
     *
     * @param scenarioRuns all runs belonging to a single scenario
     */
    public static ScenarioAnalysis analyzeScenario(List<AlgorithmRunResult> scenarioRuns) {
        List<double[]> universal = computeUniversalPareto(scenarioRuns);

        // Per-algorithm aggregate fronts (union over each algorithm's own seeds).
        Map<String, List<AlgorithmRunResult>> runsByLabel = new LinkedHashMap<>();
        for (AlgorithmRunResult run : scenarioRuns) {
            runsByLabel.computeIfAbsent(run.getLabel(), k -> new ArrayList<>()).add(run);
        }
        Map<String, List<double[]>> algorithmFronts = new LinkedHashMap<>();
        for (Map.Entry<String, List<AlgorithmRunResult>> e : runsByLabel.entrySet()) {
            algorithmFronts.put(e.getKey(), computeAlgorithmFront(e.getValue()));
        }

        // Union bounds over every non-failure point plus the universal front
        // (the universal points are a subset of run points, so this matches
        // recompute_hv.py's "all points" basis).
        List<double[]> allPoints = new ArrayList<>();
        for (AlgorithmRunResult run : scenarioRuns) {
            for (double[] sol : run.getFront()) {
                if (!isFailureSentinel(sol)) {
                    allPoints.add(sol);
                }
            }
        }
        allPoints.addAll(universal);

        double universalHV = universalHV(universal);
        if (allPoints.isEmpty()) {
            // Nothing to analyse; leave indicator defaults in place.
            return new ScenarioAnalysis(universal, universalHV, algorithmFronts,
                analyzeSeedCollaboration(scenarioRuns, null), Double.NaN);
        }
        double[] bounds = unionBounds(allPoints);
        List<SeedCollaboration> seedCollaboration = analyzeSeedCollaboration(scenarioRuns, bounds);
        double universalHvFixed = universal.isEmpty()
            ? Double.NaN : hvFixedFromNormalized(normalize(universal, bounds));

        for (AlgorithmRunResult run : scenarioRuns) {
            List<double[]> front = run.getFront();
            List<double[]> nonDom = filterToNonDominated(front);
            run.setNonDominatedFront(nonDom);

            LegacyIndicators legacy = computeLegacyIndicators(nonDom, universal);
            run.setHv(legacy.hv);
            run.setGd(legacy.gd);
            run.setIgd(legacy.igd);
            run.setSpacing(legacy.spacing);

            FixedIndicators fixed = computeFixedIndicators(front, universal, bounds);
            run.setHvFixed(fixed.hvFixed);
            run.setEpsilonPlus(fixed.epsilonPlus);
            run.setParetoContributionPct(fixed.contributionPct);

            run.setParetoContributionCount(paretoContributionCount(front, universal));
        }

        return new ScenarioAnalysis(universal, universalHV, algorithmFronts,
            seedCollaboration, universalHvFixed);
    }

    // =========================================================================
    // Cap-tier partitioned analysis (PowerCeiling studies)
    // =========================================================================

    /** Tier name used for arms without a {@code _PC<digits>} label suffix. */
    public static final String UNCAPPED_TIER = "Uncapped";

    /**
     * Campaign cap-tier suffix as produced by {@code CampaignRunner} Phase 2
     * ({@code <base>_PC<targetPercent>}, e.g. {@code NSGA-II_PC60}). End-anchored
     * and digits-only, so the legacy {@code FinalExperiment} suffix form
     * {@code _PC_<n>kW} does NOT match and keeps its existing handling.
     */
    private static final java.util.regex.Pattern TIER_SUFFIX =
        java.util.regex.Pattern.compile("_PC(\\d+)$");

    /** @return the cap tier of a label ({@code "PC90"}, ...) or {@link #UNCAPPED_TIER}. */
    public static String capTierOf(String label) {
        java.util.regex.Matcher m = TIER_SUFFIX.matcher(label);
        return m.find() ? "PC" + m.group(1) : UNCAPPED_TIER;
    }

    /** @return the label with any {@code _PC<digits>} tier suffix stripped. */
    public static String baseLabelOf(String label) {
        return TIER_SUFFIX.matcher(label).replaceFirst("");
    }

    /** One cap tier's scenario analysis over copies of that tier's runs. */
    public static final class TierAnalysis {
        /** {@code "Uncapped"} or {@code "PC<targetPercent>"}. */
        public final String tier;
        /**
         * Analyzed COPIES of the tier's runs, relabelled to their BASE algorithm
         * names (tier identity lives in {@link #tier}). The original scenario
         * runs are never touched, so the global analysis stays intact.
         */
        public final List<AlgorithmRunResult> runs;
        public final ScenarioAnalysis analysis;

        TierAnalysis(String tier, List<AlgorithmRunResult> runs, ScenarioAnalysis analysis) {
            this.tier = tier;
            this.runs = runs;
            this.analysis = analysis;
        }
    }

    /**
     * Partitions a scenario's runs by cap tier (label suffix {@code _PC<digits>},
     * everything else = {@code Uncapped}) and runs the existing
     * {@link #analyzeScenario} unchanged on a COPY of each partition — so every
     * indicator, universal front, bound and collaboration table is computed
     * strictly within the tier, and the caller's run objects keep their
     * scenario-wide (global) indicator values.
     *
     * <p>Tier order: {@code Uncapped} first when present, then tiers by target
     * percent descending (loose to tight: PC90, PC60, PC30).</p>
     */
    public static List<TierAnalysis> analyzeScenarioByTier(List<AlgorithmRunResult> scenarioRuns) {
        Map<String, List<AlgorithmRunResult>> byTier = new LinkedHashMap<>();
        for (AlgorithmRunResult run : scenarioRuns) {
            byTier.computeIfAbsent(capTierOf(run.getLabel()), k -> new ArrayList<>()).add(run);
        }

        List<String> tiers = new ArrayList<>(byTier.keySet());
        tiers.sort((a, b) -> {
            if (a.equals(b)) {
                return 0;
            }
            if (a.equals(UNCAPPED_TIER)) {
                return -1;
            }
            if (b.equals(UNCAPPED_TIER)) {
                return 1;
            }
            return Integer.compare(Integer.parseInt(b.substring(2)), Integer.parseInt(a.substring(2)));
        });

        List<TierAnalysis> out = new ArrayList<>();
        for (String tier : tiers) {
            List<AlgorithmRunResult> copies = new ArrayList<>();
            for (AlgorithmRunResult r : byTier.get(tier)) {
                copies.add(new AlgorithmRunResult(baseLabelOf(r.getLabel()), r.getScenarioNumber(),
                    r.getScenarioName(), r.getSeed(), r.getObjectiveNames(), r.getFront(),
                    r.getAuxPeakPowerWatts(), r.getRuntimeMs()));
            }
            out.add(new TierAnalysis(tier, copies, analyzeScenario(copies)));
        }
        return out;
    }
}
