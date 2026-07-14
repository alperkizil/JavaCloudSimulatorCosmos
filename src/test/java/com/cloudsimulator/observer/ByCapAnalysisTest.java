package com.cloudsimulator.observer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the cap-tier partitioned analysis and the additive {@code *_by_cap.csv}
 * writers on a hand-computed synthetic scenario:
 *
 * <ol>
 *   <li>label parsing ({@code capTierOf} / {@code baseLabelOf}), including that the
 *       legacy {@code _PC_<n>kW} suffix form is NOT treated as a campaign tier;</li>
 *   <li>{@code analyzeScenarioByTier}: tier ordering (Uncapped, then descending),
 *       base relabelling, per-tier universal fronts and per-tier contribution
 *       counts (points dominated globally must win within their tier);</li>
 *   <li>the global analysis is not disturbed — the caller's run objects keep
 *       their scenario-wide indicator values byte-for-byte;</li>
 *   <li>{@code writeByCapReports} round-trip: headers, tier rows, CapWatts
 *       fields and the per-tier universal fronts parsed back from disk.</li>
 * </ol>
 *
 * <p>Usage: {@code java ... com.cloudsimulator.observer.ByCapAnalysisTest}
 * (no arguments; writes into a temp directory it deletes on success).</p>
 */
public class ByCapAnalysisTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) throws IOException {
        System.out.println("=== ByCapAnalysisTest ===");

        testLabelParsing();

        // ---- Synthetic scenario: 2 arms x 2 tiers x 2 seeds --------------------
        // Uncapped points dominate every PC60 point globally, so the per-tier
        // analysis must resurrect the PC60 points as that tier's universal front.
        List<AlgorithmRunResult> runs = new ArrayList<>();
        List<String> objs = List.of("WaitingTime", "Energy");
        for (long seed : new long[] {200, 201}) {
            runs.add(run("A", seed, objs, p(1.0, 10.0), p(2.0, 6.0)));
            runs.add(run("B", seed, objs, p(1.5, 7.0), p(3.0, 5.0)));
            runs.add(run("A_PC60", seed, objs, p(4.0, 9.0), p(5.0, 8.0)));
            runs.add(run("B_PC60", seed, objs, p(4.5, 8.5), p(6.0, 7.5)));
        }

        // Global analysis first (the campaign's real order), snapshot indicators.
        ParetoAnalyzer.ScenarioAnalysis global = ParetoAnalyzer.analyzeScenario(runs);
        check("global universal size", 4, global.universalFront.size());
        Map<AlgorithmRunResult, double[]> snapshot = new LinkedHashMap<>();
        for (AlgorithmRunResult r : runs) {
            snapshot.put(r, new double[] {r.getHv(), r.getGd(), r.getIgd(), r.getSpacing(),
                r.getHvFixed(), r.getParetoContributionCount()});
        }
        // Globally, the constrained arms contribute nothing.
        for (AlgorithmRunResult r : runs) {
            if (r.getLabel().endsWith("_PC60")) {
                check("global contribution of " + r.getLabel(), 0, r.getParetoContributionCount());
            }
        }

        // ---- Tier-partitioned analysis ----------------------------------------
        List<ParetoAnalyzer.TierAnalysis> tiers = ParetoAnalyzer.analyzeScenarioByTier(runs);
        check("tier count", 2, tiers.size());
        check("tier order [0]", ParetoAnalyzer.UNCAPPED_TIER, tiers.get(0).tier);
        check("tier order [1]", "PC60", tiers.get(1).tier);

        ParetoAnalyzer.TierAnalysis pc60 = tiers.get(1);
        check("PC60 universal size", 4, pc60.analysis.universalFront.size());
        double[][] expectedPc60 = {p(4.0, 9.0), p(4.5, 8.5), p(5.0, 8.0), p(6.0, 7.5)};
        for (int i = 0; i < expectedPc60.length; i++) {
            double[] got = pc60.analysis.universalFront.get(i);
            checkClose("PC60 universal[" + i + "].x", expectedPc60[i][0], got[0], 1e-12);
            checkClose("PC60 universal[" + i + "].y", expectedPc60[i][1], got[1], 1e-12);
        }
        // Hand-computed HV_fixed of the PC60 universal front in the tier frame:
        // ideal (4, 7.5), nadir (6, 9) -> normalized staircase HV = 0.62666../1.21.
        checkClose("PC60 universal HV_fixed", 0.6266666666 / 1.21,
            pc60.analysis.universalHvFixed, 1e-6);

        for (AlgorithmRunResult copy : pc60.runs) {
            check("PC60 copy base label", true,
                copy.getLabel().equals("A") || copy.getLabel().equals("B"));
            check("PC60 per-tier contribution of " + copy.getLabel() + " seed " + copy.getSeed(),
                2, copy.getParetoContributionCount());
        }

        ParetoAnalyzer.TierAnalysis uncapped = tiers.get(0);
        check("Uncapped universal size", 4, uncapped.analysis.universalFront.size());
        for (AlgorithmRunResult copy : uncapped.runs) {
            check("Uncapped per-tier contribution of " + copy.getLabel(),
                2, copy.getParetoContributionCount());
        }

        // ---- Global indicators must be untouched by the tier pass -------------
        for (Map.Entry<AlgorithmRunResult, double[]> e : snapshot.entrySet()) {
            AlgorithmRunResult r = e.getKey();
            double[] s = e.getValue();
            checkClose("undisturbed HV of " + r.getLabel() + "/" + r.getSeed(), s[0], r.getHv(), 0.0);
            checkClose("undisturbed GD", s[1], r.getGd(), 0.0);
            checkClose("undisturbed IGD", s[2], r.getIgd(), 0.0);
            checkClose("undisturbed Spacing", s[3], r.getSpacing(), 0.0);
            checkClose("undisturbed HV_fixed", s[4], r.getHvFixed(), 0.0);
            check("undisturbed contribution", (int) s[5], r.getParetoContributionCount());
        }

        // ---- Reporter round-trip ----------------------------------------------
        Path dir = Files.createTempDirectory("bycap_test_");
        Map<String, Double> capWatts = new LinkedHashMap<>();
        capWatts.put("PC60", 212400.0);
        new ExperimentReporter().writeByCapReports(dir, 1, objs, tiers, capWatts);

        List<String> uni = Files.readAllLines(dir.resolve("scenario_1_universal_fronts_by_cap.csv"));
        check("universal_fronts header", "CapTier,CapWatts,WaitingTime,Energy", uni.get(0));
        check("universal_fronts rows", 1 + 4 + 4, uni.size());
        check("uncapped row has empty CapWatts", true, uni.get(1).startsWith("Uncapped,,"));
        check("pc60 row carries CapWatts", true, uni.get(5).startsWith("PC60,212400.000,"));
        String[] firstPc60 = uni.get(5).split(",", -1);
        checkClose("pc60 universal row x", 4.0, Double.parseDouble(firstPc60[2]), 1e-9);
        checkClose("pc60 universal row y", 9.0, Double.parseDouble(firstPc60[3]), 1e-9);

        List<String> pm = Files.readAllLines(dir.resolve("scenario_1_performance_metrics_by_cap.csv"));
        check("metrics header",
            "CapTier,CapWatts,Algorithm,Seed,HV,GD,IGD,Spacing,NonDomSolutions,"
                + "TotalSolutions,ParetoContribution,TimeMs,HV_fixed", pm.get(0));
        // Per tier: 2 labels x (2 seed rows + MEAN + STDDEV) + 1 universal trailer = 9.
        check("metrics rows", 1 + 9 + 9, pm.size());
        int pc60Rows = 0;
        for (String line : pm) {
            if (line.startsWith("PC60,212400.000,A,") || line.startsWith("PC60,212400.000,B,")) {
                String[] tok = line.split(",", -1);
                if (tok[3].equals("200") || tok[3].equals("201")) {
                    pc60Rows++;
                    check("per-tier ParetoContribution column", "2", tok[10]);
                }
            }
        }
        check("pc60 per-seed metric rows", 4, pc60Rows);
        check("per-tier universal trailer present", true,
            pm.stream().anyMatch(l -> l.startsWith("PC60,212400.000,Universal_Pareto,ALL,")));

        List<String> sc = Files.readAllLines(dir.resolve("scenario_1_seed_collaboration_by_cap.csv"));
        check("collab header",
            "CapTier,Algorithm,Seed,SeedUniversalFrontSize,SeedUniversalHV_fixed,"
                + "ContributionCount,ContributionPct", sc.get(0));
        check("collab has PC60 rows", true, sc.stream().anyMatch(l -> l.startsWith("PC60,A,200,")));
        check("collab has per-tier universal trailer", true,
            sc.stream().anyMatch(l -> l.startsWith("PC60,Universal_Pareto,MEAN,")));

        // Cleanup on success only (leave evidence behind on failure).
        if (failures == 0) {
            try (var walk = Files.walk(dir)) {
                walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(pth -> pth.toFile().delete());
            }
        } else {
            System.out.println("Artifacts kept at: " + dir);
        }

        System.out.println();
        System.out.println("Checks: " + checks + ", failures: " + failures);
        if (failures == 0) {
            System.out.println("=== ByCapAnalysisTest PASSED ===");
        } else {
            System.out.println("=== ByCapAnalysisTest FAILED ===");
            System.exit(1);
        }
    }

    private static void testLabelParsing() {
        check("tier of plain label", ParetoAnalyzer.UNCAPPED_TIER, ParetoAnalyzer.capTierOf("NSGA-II"));
        check("tier of _PC90", "PC90", ParetoAnalyzer.capTierOf("GA_Energy_Dominance_PC90"));
        check("tier of _PC60", "PC60", ParetoAnalyzer.capTierOf("SA_WaitingTime_Dominance_PC60"));
        check("legacy kW suffix is not a tier", ParetoAnalyzer.UNCAPPED_TIER,
            ParetoAnalyzer.capTierOf("NSGA-II_PC_190kW"));
        check("base of _PC30", "AMOSA", ParetoAnalyzer.baseLabelOf("AMOSA_PC30"));
        check("base of plain label", "SPEA-II", ParetoAnalyzer.baseLabelOf("SPEA-II"));
        check("base keeps legacy kW suffix", "NSGA-II_PC_190kW",
            ParetoAnalyzer.baseLabelOf("NSGA-II_PC_190kW"));
    }

    private static AlgorithmRunResult run(String label, long seed, List<String> objs, double[]... pts) {
        List<double[]> front = new ArrayList<>();
        for (double[] pt : pts) {
            front.add(pt);
        }
        return new AlgorithmRunResult(label, 1, "Synthetic", seed, objs, front, null, 1000);
    }

    private static double[] p(double x, double y) {
        return new double[] {x, y};
    }

    private static void check(String what, Object expected, Object actual) {
        checks++;
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            System.out.println("  FAIL " + what + ": expected=" + expected + " actual=" + actual);
            failures++;
        }
    }

    private static void checkClose(String what, double expected, double actual, double tol) {
        checks++;
        boolean ok = (Double.isNaN(expected) && Double.isNaN(actual))
            || Math.abs(expected - actual) <= tol;
        if (!ok) {
            System.out.printf("  FAIL %s: expected=%.12f actual=%.12f (|d|=%.3e)%n",
                what, expected, actual, Math.abs(expected - actual));
            failures++;
        }
    }
}
