package com.cloudsimulator.newExperiments;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.observer.AlgorithmRunResult;
import com.cloudsimulator.observer.ExperimentSpec;
import com.cloudsimulator.observer.PowerCapFeasibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reduced-scale smoke + reproducibility check for the new campaign pipeline:
 * runs each representative code path (baseline, single-objective+seed,
 * multi-objective+seeds) twice with the same seed and asserts the captured fronts
 * are bit-identical. This is the determinism foundation of the parity gate.
 *
 * Main-method test (no JUnit); exits non-zero on failure.
 */
public class CampaignReproducibilityTest {

    private static int failures = 0;

    public static void main(String[] args) {
        System.out.println("=== Campaign reproducibility (reduced scale) ===\n");

        AlgorithmParameters params = reducedParams();
        ExperimentConfig infra = reducedInfra();
        ExperimentSpec spec = ExperimentSpec.waitingTime("repro-test");
        PrimaryObjective primary = PrimaryObjective.WAITING_TIME;

        AlgorithmRegistry registry = new AlgorithmRegistry(params, primary);
        CampaignRunner runner = new CampaignRunner(spec, primary, infra, params, new String[] {"FirstAvailable"});

        String[] labels = {"FirstAvailable", "GA_WaitingTime", "NSGA-II"};
        long seed = infra.baseSeed;

        for (String label : labels) {
            final String base = label;
            ExperimentConfiguration cfgA = infra.toExperimentConfiguration(1, seed);
            ExperimentConfiguration cfgB = infra.toExperimentConfiguration(1, seed);
            List<double[]> frontA = runner.runOne(base, (ctx, sd) -> registry.create(base, ctx, sd),
                cfgA, 1, "Balanced", seed).getFront();
            List<double[]> frontB = runner.runOne(base, (ctx, sd) -> registry.create(base, ctx, sd),
                cfgB, 1, "Balanced", seed).getFront();

            assertNonEmptyFinite(label, frontA);
            assertIdentical(label, frontA, frontB);
        }

        // ---- PowerCeiling: uncapped metaheuristics, coincident peaks + dynamic caps ----
        System.out.println();
        System.out.println("--- PowerCeiling (aux coincident peak + dynamic caps) ---");
        ExperimentSpec pcSpec = ExperimentSpec.powerCeiling("repro-test-pc");
        AlgorithmRegistry pcRegistry = new AlgorithmRegistry(params, primary);
        CampaignRunner pcRunner = new CampaignRunner(pcSpec, primary, infra, params, new String[] {"NSGA-II"});

        // Base metaheuristics run uncapped; peak capture is driven by the spec's aux
        // extractor, not by any _PC_ label.
        String[] pcLabels = {"NSGA-II", "GA_WaitingTime_Dominance", "SPEA-II"};
        List<AlgorithmRunResult> pcRuns = new ArrayList<>();
        for (String label : pcLabels) {
            final String base = label;
            ExperimentConfiguration cfgA = infra.toExperimentConfiguration(1, seed);
            ExperimentConfiguration cfgB = infra.toExperimentConfiguration(1, seed);
            AlgorithmRunResult rA = pcRunner.runOne(base, (ctx, sd) -> pcRegistry.create(base, ctx, sd),
                cfgA, 1, "Balanced", seed);
            AlgorithmRunResult rB = pcRunner.runOne(base, (ctx, sd) -> pcRegistry.create(base, ctx, sd),
                cfgB, 1, "Balanced", seed);
            pcRuns.add(rA);

            assertNonEmptyFinite(label, rA.getFront());
            assertIdentical(label, rA.getFront(), rB.getFront());
            // Caps derived dynamically from the pooled peaks — exercises the calibrator.
            double[] caps = PowerCapCalibrator.deriveCaps(pcRuns, PowerCapCalibrator.DEFAULT_FEASIBILITY_TARGETS);
            assertAuxPeaks(label, rA, rB, caps);
        }
        assertCalibratorPercentiles();

        System.out.println();
        if (failures == 0) {
            System.out.println("=== Reproducibility test PASSED ===");
        } else {
            System.out.println("=== Reproducibility test FAILED: " + failures + " issue(s) ===");
            System.exit(1);
        }
    }

    private static AlgorithmParameters reducedParams() {
        AlgorithmParameters p = AlgorithmParameters.defaults();
        p.verboseLogging = false;
        p.populationSize = 8;
        p.iterationCount = 16;
        p.amosaSoftLimit = 8;
        p.amosaHardLimit = 4;
        p.amosaIterationsPerTemp = 4;
        p.amosaHillClimbingIters = 2;
        return p;
    }

    private static ExperimentConfig reducedInfra() {
        ExperimentConfig c = ExperimentConfig.defaults();
        c.scenarioNames = new String[] {"Balanced"};
        c.scenarioTaskCounts = new int[][] {{6, 3}};
        c.numRuns = 2;
        return c;
    }

    private static void assertNonEmptyFinite(String label, List<double[]> front) {
        if (front.isEmpty()) {
            System.out.println("  FAIL: " + label + " produced an empty front");
            failures++;
            return;
        }
        for (double[] p : front) {
            if (p.length != 2 || !Double.isFinite(p[0]) || !Double.isFinite(p[1])) {
                System.out.println("  FAIL: " + label + " produced a non-finite point");
                failures++;
                return;
            }
        }
    }

    private static void assertIdentical(String label, List<double[]> a, List<double[]> b) {
        if (a.size() != b.size()) {
            System.out.println("  FAIL: " + label + " non-reproducible (front sizes " + a.size()
                + " vs " + b.size() + ")");
            failures++;
            return;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i)[0] != b.get(i)[0] || a.get(i)[1] != b.get(i)[1]) {
                System.out.println("  FAIL: " + label + " non-reproducible at point " + i
                    + " (" + a.get(i)[0] + "," + a.get(i)[1] + ") vs ("
                    + b.get(i)[0] + "," + b.get(i)[1] + ")");
                failures++;
                return;
            }
        }
        System.out.println("  ok:   " + label + " reproducible (" + a.size() + "-point front)");
    }

    /**
     * PowerCeiling checks: coincident peaks are captured (non-null), aligned with the
     * front, finite, reproducible across identical-seed runs, and the feasibility
     * fraction {@code (#peaks <= cap)/(#peaks)} matches {@link PowerCapFeasibility}.
     */
    private static void assertAuxPeaks(String label, AlgorithmRunResult a, AlgorithmRunResult b, double[] caps) {
        List<Double> pa = a.getAuxPeakPowerWatts();
        List<Double> pb = b.getAuxPeakPowerWatts();
        if (pa == null) { fail(label, "aux peaks null (expected coincident peaks)"); return; }
        if (pa.size() != a.getFront().size()) {
            fail(label, "peaks size " + pa.size() + " != front size " + a.getFront().size());
            return;
        }
        for (double v : pa) {
            if (!Double.isFinite(v) || v < 0) { fail(label, "non-finite/negative peak " + v); return; }
        }
        if (pb == null || pa.size() != pb.size()) { fail(label, "peaks non-reproducible (size)"); return; }
        for (int i = 0; i < pa.size(); i++) {
            if ((double) pa.get(i) != (double) pb.get(i)) { fail(label, "peaks non-reproducible at " + i); return; }
        }
        Map<String, Map<Double, Double>> rates = PowerCapFeasibility.feasibilityRates(List.of(a), caps);
        Map<Double, Double> r = rates.get(label);
        for (double cap : caps) {
            long feasible = pa.stream().filter(x -> x <= cap).count();
            double expected = (double) feasible / pa.size();
            double got = r.get(cap);
            if (Math.abs(expected - got) > 1e-12) {
                fail(label, "feasibility mismatch at cap " + cap + ": " + got + " vs " + expected);
                return;
            }
        }
        System.out.println("  ok:   " + label + " peaks captured (" + pa.size()
            + "), reproducible, feasibility consistent");
    }

    private static void fail(String label, String msg) {
        System.out.println("  FAIL: " + label + " " + msg);
        failures++;
    }

    /**
     * PowerCapCalibrator sanity: on peaks {0,10,...,100}, the {90,75,50,25}
     * feasibility targets must map exactly to caps {90,75,50,25} (in W here).
     */
    private static void assertCalibratorPercentiles() {
        List<Double> peaks = new ArrayList<>();
        for (int v = 0; v <= 100; v += 10) peaks.add((double) v);
        double[] caps = PowerCapCalibrator.deriveCapsFromPeaks(
            peaks, PowerCapCalibrator.DEFAULT_FEASIBILITY_TARGETS);
        double[] expected = {90, 75, 50, 25};
        boolean ok = caps.length == expected.length;
        for (int i = 0; ok && i < expected.length; i++) {
            if (Math.abs(caps[i] - expected[i]) > 1e-9) ok = false;
        }
        if (ok) {
            System.out.println("  ok:   PowerCapCalibrator percentiles {90,75,50,25} -> "
                + java.util.Arrays.toString(caps));
        } else {
            fail("PowerCapCalibrator", "percentiles wrong: got " + java.util.Arrays.toString(caps)
                + " expected " + java.util.Arrays.toString(expected));
        }
    }
}
