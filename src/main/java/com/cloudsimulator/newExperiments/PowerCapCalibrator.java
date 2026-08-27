package com.cloudsimulator.newExperiments;

import com.cloudsimulator.observer.AlgorithmRunResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Derives power-cap thresholds dynamically from an <em>uncapped</em> run's observed
 * coincident-peak distribution, instead of hardcoding them.
 *
 * <p>Two schemes are available.</p>
 *
 * <p><b>Anchored (default, {@link #deriveCapsFromAnchor}).</b> Each cap is a fraction
 * of <em>P_ref</em>, the peak drawn by the latency-optimal schedule — see
 * {@link #referencePeakWatts}. A tier then states a physically meaningful demand
 * ("run this workload at 80% of the power the fastest schedule wants") that means the
 * same thing in every scenario, and does not depend on how many points each arm
 * happens to contribute to the pool. Intended to be derived <em>per scenario</em>,
 * since P_ref is a per-scenario quantity.</p>
 *
 * <p><b>Percentile ({@link #deriveCaps}).</b> Each cap sits at the peak percentile
 * matching a target feasibility fraction: a cap at the <em>t</em>-th percentile of the
 * observed peaks makes ~<em>t%</em> of solutions feasible. Self-referential — the
 * tier is defined by what the arms already did — so it cannot demand a reduction
 * the arms were not already close to making, and it is weighted by archive size (the
 * arms that emit the most points dominate the pooled distribution). Retained for
 * reproducing earlier campaigns.</p>
 */
public final class PowerCapCalibrator {

    private PowerCapCalibrator() {}

    /** Default target feasibility fractions (%) for the three cap tiers, loose→tight. */
    public static final double[] DEFAULT_FEASIBILITY_TARGETS = {90.0, 60.0, 30.0};

    /** Default anchor fractions (% of P_ref) for the cap tiers, loose→tight. */
    public static final double[] DEFAULT_ANCHOR_FRACTIONS = {90.0, 85.0, 80.0, 75.0};

    /**
     * Fraction of the fastest solutions whose median peak defines P_ref. Anchoring on
     * the head of the distribution rather than on the single fastest point keeps the
     * tiers from riding on one lucky seed — the per-seed spread of the single
     * fastest solution's peak is a few percent — while landing within ~2.5% of it.
     */
    private static final double ANCHOR_HEAD_FRACTION = 0.01;

    /**
     * Pools every non-null coincident peak across the given runs and returns the cap
     * (Watts) for each target feasibility percent, in the same order as
     * {@code targetFeasibilityPercents}. Returns an empty array if no peaks exist.
     */
    public static double[] deriveCaps(List<AlgorithmRunResult> runs, double[] targetFeasibilityPercents) {
        List<Double> peaks = poolPeaks(runs);
        return deriveCapsFromPeaks(peaks, targetFeasibilityPercents);
    }

    /** As {@link #deriveCaps} but from a pre-pooled peak list (Watts). */
    public static double[] deriveCapsFromPeaks(List<Double> peaksWatts, double[] targetFeasibilityPercents) {
        if (peaksWatts == null || peaksWatts.isEmpty() || targetFeasibilityPercents == null) {
            return new double[0];
        }
        List<Double> sorted = new ArrayList<>(peaksWatts);
        Collections.sort(sorted);
        double[] caps = new double[targetFeasibilityPercents.length];
        for (int i = 0; i < targetFeasibilityPercents.length; i++) {
            caps[i] = percentile(sorted, targetFeasibilityPercents[i]);
        }
        return caps;
    }

    // ---- Anchored caps (default scheme) ------------------------------------

    /**
     * P_ref for the given runs: the coincident peak drawn by the latency-optimal
     * schedule, in Watts.
     *
     * <p>Taken as the median peak over the fastest {@link #ANCHOR_HEAD_FRACTION} of
     * solutions (ranked by the primary objective, index 0 of each front vector —
     * average waiting time for the PowerCeiling study), so a single outlying seed
     * cannot move every tier. Returns {@code NaN} when no run carries peaks.</p>
     */
    public static double referencePeakWatts(List<AlgorithmRunResult> runs) {
        List<double[]> byPrimary = poolPrimaryAndPeak(runs);
        if (byPrimary.isEmpty()) {
            return Double.NaN;
        }
        byPrimary.sort(Comparator.comparingDouble(pair -> pair[0]));
        int head = Math.max(1, (int) Math.round(ANCHOR_HEAD_FRACTION * byPrimary.size()));
        List<Double> headPeaks = new ArrayList<>(head);
        for (int i = 0; i < head; i++) {
            headPeaks.add(byPrimary.get(i)[1]);
        }
        Collections.sort(headPeaks);
        return percentile(headPeaks, 50.0);
    }

    /**
     * Caps (Watts) at each given fraction of P_ref, in the same order as
     * {@code anchorFractionPercents}. Returns an empty array if no peaks exist.
     *
     * <p>Derive this from <em>one scenario's</em> uncapped runs: P_ref is a
     * per-scenario quantity, so pooling scenarios would make a tier mean a different
     * physical demand in each.</p>
     */
    public static double[] deriveCapsFromAnchor(List<AlgorithmRunResult> runs,
                                                double[] anchorFractionPercents) {
        return capsFromAnchor(referencePeakWatts(runs), anchorFractionPercents);
    }

    /** As {@link #deriveCapsFromAnchor} but from an already-computed P_ref (Watts). */
    public static double[] capsFromAnchor(double referencePeakWatts, double[] anchorFractionPercents) {
        if (anchorFractionPercents == null || !Double.isFinite(referencePeakWatts)) {
            return new double[0];
        }
        double[] caps = new double[anchorFractionPercents.length];
        for (int i = 0; i < caps.length; i++) {
            caps[i] = referencePeakWatts * anchorFractionPercents[i] / 100.0;
        }
        return caps;
    }

    /**
     * Flattens every run into {@code [primaryObjective, peakWatts]} pairs. The front
     * and the aux-peak list are built in lockstep by the runner, so index {@code i} of
     * one matches index {@code i} of the other; runs without peaks are skipped, and a
     * length mismatch is truncated to the shorter of the two rather than trusted.
     */
    private static List<double[]> poolPrimaryAndPeak(List<AlgorithmRunResult> runs) {
        List<double[]> pairs = new ArrayList<>();
        if (runs == null) {
            return pairs;
        }
        for (AlgorithmRunResult run : runs) {
            List<Double> peaks = run.getAuxPeakPowerWatts();
            List<double[]> front = run.getFront();
            if (peaks == null || front == null) {
                continue;
            }
            int n = Math.min(peaks.size(), front.size());
            for (int i = 0; i < n; i++) {
                Double peak = peaks.get(i);
                double[] objectives = front.get(i);
                if (peak == null || !Double.isFinite(peak)
                        || objectives == null || objectives.length == 0
                        || !Double.isFinite(objectives[0])) {
                    continue;
                }
                pairs.add(new double[] {objectives[0], peak});
            }
        }
        return pairs;
    }

    /** Flattens every run's non-null {@code auxPeakPowerWatts} into one list. */
    public static List<Double> poolPeaks(List<AlgorithmRunResult> runs) {
        List<Double> peaks = new ArrayList<>();
        if (runs == null) {
            return peaks;
        }
        for (AlgorithmRunResult run : runs) {
            List<Double> p = run.getAuxPeakPowerWatts();
            if (p == null) {
                continue;
            }
            for (Double v : p) {
                if (v != null && Double.isFinite(v)) {
                    peaks.add(v);
                }
            }
        }
        return peaks;
    }

    /**
     * Linear-interpolated percentile of an already-sorted (ascending) list, using the
     * {@code (n-1)*p/100} index convention (matches numpy's default and the Python
     * calibration script).
     */
    static double percentile(List<Double> sortedAscending, double percent) {
        int n = sortedAscending.size();
        if (n == 0) {
            return Double.NaN;
        }
        if (n == 1) {
            return sortedAscending.get(0);
        }
        double clamped = Math.max(0.0, Math.min(100.0, percent));
        double k = (n - 1) * clamped / 100.0;
        int lo = (int) Math.floor(k);
        int hi = (int) Math.ceil(k);
        if (lo == hi) {
            return sortedAscending.get(lo);
        }
        double frac = k - lo;
        return sortedAscending.get(lo) * (1.0 - frac) + sortedAscending.get(hi) * frac;
    }
}
