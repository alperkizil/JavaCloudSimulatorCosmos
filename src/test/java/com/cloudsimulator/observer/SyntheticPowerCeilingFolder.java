package com.cloudsimulator.observer;

import com.cloudsimulator.newExperiments.PowerCapCalibrator;
import com.cloudsimulator.newExperiments.PowerCeilingFeasibilityReporter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Writes a small but complete SYNTHETIC PowerCeiling result folder using the
 * real pipeline classes ({@link ParetoAnalyzer}, {@link ExperimentReporter},
 * {@link PowerCeilingFeasibilityReporter}) on fabricated fronts — no simulation.
 *
 * <p>Purpose: development/test fixture for {@code scripts/results_explorer.py}'s
 * PowerCap mode (no real PowerCeiling campaign folder exists in the repo). The
 * folder carries everything a real run would: per-scenario graph/metrics/fronts/
 * collaboration CSVs, {@code feasibility_summary.csv} (+ 3D CSVs), the native
 * {@code *_by_cap.csv} files, {@code experiment_summary.csv} and
 * {@code plot_options.json}. Fully deterministic (fixed RNG seeds), so the
 * explorer's recompute-vs-native parity check is reproducible.</p>
 *
 * <p>Usage: {@code java ... SyntheticPowerCeilingFolder [outDir]}
 * (default {@code results/SyntheticPowerCeiling_TEST}).</p>
 */
public final class SyntheticPowerCeilingFolder {

    private static final String[] BASES = {
        "GA_Energy_Dominance", "SA_WaitingTime_Dominance", "NSGA-II", "AMOSA"};
    private static final String[] SCENARIOS = {"Balanced", "GPU_Stress"};
    private static final long[] SEEDS = {200, 201, 202};
    private static final double[] TARGETS = PowerCapCalibrator.DEFAULT_FEASIBILITY_TARGETS;
    private static final List<String> OBJECTIVES = List.of("WaitingTime", "Energy");

    public static void main(String[] args) throws IOException {
        String outDir = args.length > 0 ? args[0] : "results/SyntheticPowerCeiling_TEST";

        // ---- Phase 1: uncapped runs (peaks pooled for cap derivation) ----------
        List<List<AlgorithmRunResult>> perScenario = new ArrayList<>();
        List<AlgorithmRunResult> allUncapped = new ArrayList<>();
        for (int s = 0; s < SCENARIOS.length; s++) {
            List<AlgorithmRunResult> runs = new ArrayList<>();
            for (String base : BASES) {
                for (long seed : SEEDS) {
                    runs.add(makeRun(base, s + 1, SCENARIOS[s], seed, tierShift(0), null));
                }
            }
            perScenario.add(runs);
            allUncapped.addAll(runs);
        }
        double[] caps = PowerCapCalibrator.deriveCaps(allUncapped, TARGETS);
        System.out.printf(java.util.Locale.US,
            "Derived caps: %.1f / %.1f / %.1f W%n", caps[0], caps[1], caps[2]);

        // ---- Phase 2: constrained _PC<tier> arms under each cap ----------------
        for (int s = 0; s < SCENARIOS.length; s++) {
            List<AlgorithmRunResult> runs = perScenario.get(s);
            for (int c = 0; c < caps.length; c++) {
                String tier = String.format(java.util.Locale.US, "%.0f", TARGETS[c]);
                for (String base : BASES) {
                    for (long seed : SEEDS) {
                        runs.add(makeRun(base + "_PC" + tier, s + 1, SCENARIOS[s], seed,
                            tierShift(c + 1), caps[c]));
                    }
                }
            }
        }

        // ---- Analyze + write (mirrors CampaignRunner's reporting stage) --------
        List<ExperimentReporter.ScenarioReport> reports = new ArrayList<>();
        for (int s = 0; s < SCENARIOS.length; s++) {
            List<AlgorithmRunResult> runs = perScenario.get(s);
            ParetoAnalyzer.ScenarioAnalysis an = ParetoAnalyzer.analyzeScenario(runs);
            Map<String, List<AlgorithmRunResult>> byLabel = new LinkedHashMap<>();
            for (AlgorithmRunResult r : runs) {
                byLabel.computeIfAbsent(r.getLabel(), k -> new ArrayList<>()).add(r);
            }
            reports.add(new ExperimentReporter.ScenarioReport(
                s + 1, SCENARIOS[s], OBJECTIVES, byLabel, an.universalFront, an.universalHV,
                an.algorithmFronts, an.seedCollaboration, an.universalHvFixed));
        }

        ExperimentReporter reporter = new ExperimentReporter();
        reporter.writeAll(outDir, reports);
        PowerCeilingFeasibilityReporter.writeReports(outDir, reports, caps);

        Map<String, Double> capWattsByTier = new LinkedHashMap<>();
        for (int c = 0; c < caps.length; c++) {
            capWattsByTier.put(
                "PC" + String.format(java.util.Locale.US, "%.0f", TARGETS[c]), caps[c]);
        }
        Path dir = Paths.get(outDir);
        for (int s = 0; s < SCENARIOS.length; s++) {
            List<ParetoAnalyzer.TierAnalysis> tiers =
                ParetoAnalyzer.analyzeScenarioByTier(perScenario.get(s));
            reporter.writeByCapReports(dir, s + 1, OBJECTIVES, tiers, capWattsByTier);
        }
        System.out.println("Synthetic PowerCeiling folder written to: " + outDir);
    }

    /** Tier index 0 = uncapped, 1..3 = PC90/PC60/PC30 (tighter = larger shift). */
    private static double[] tierShift(int tierIdx) {
        double[][] shifts = {
            {1.00, 1.000},  // uncapped
            {1.16, 0.968},  // PC90: waits stretch, energy drops slightly
            {1.48, 0.925},  // PC60
            {2.05, 0.872},  // PC30
        };
        return shifts[tierIdx];
    }

    /**
     * Fabricates one run: a mostly-non-dominated trade-off front (with a little
     * noise so NonDom &lt; Total occasionally, like re-simulated archives) plus
     * a coincident-peak list. Deterministic per (label, scenario, seed).
     */
    private static AlgorithmRunResult makeRun(String label, int scenarioNum, String scenarioName,
                                              long seed, double[] shift, Double capWatts) {
        Random rng = new Random(label.hashCode() * 1_000_003L + scenarioNum * 7919L + seed);
        int n = 8 + rng.nextInt(6);
        double wtScale = shift[0], eScale = shift[1];
        double wt0 = (2.0 + 0.4 * scenarioNum) * wtScale;
        double span = 8.0 * wtScale;
        double e0 = 0.42 * eScale, amp = 0.30 * eScale;

        List<double[]> front = new ArrayList<>();
        List<Double> peaks = new ArrayList<>();
        double lastE = Double.POSITIVE_INFINITY;
        for (int k = 0; k < n; k++) {
            double f = k / (double) (n - 1);
            double wt = wt0 + span * Math.pow(f, 1.3) * (1 + (rng.nextDouble() - 0.5) * 0.04);
            double e = (e0 + amp * Math.pow(1 - f * 0.9, 1.6)) * (1 + (rng.nextDouble() - 0.5) * 0.02);
            if (rng.nextDouble() > 0.15) {
                e = Math.min(e, lastE - 1e-4);  // keep most of the front a staircase
            }
            lastE = Math.min(lastE, e);
            front.add(new double[] {wt, e});
            if (capWatts == null) {
                peaks.add(180_000.0 + rng.nextDouble() * 160_000.0);
            } else {
                peaks.add(capWatts * (0.70 + 0.29 * rng.nextDouble()));
            }
        }
        return new AlgorithmRunResult(label, scenarioNum, scenarioName, seed,
            OBJECTIVES, front, peaks, 900 + rng.nextInt(600));
    }

    private SyntheticPowerCeilingFolder() {
    }
}
