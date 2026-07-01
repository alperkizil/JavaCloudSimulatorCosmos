package com.cloudsimulator.newExperiments;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.observer.AlgorithmRunResult;
import com.cloudsimulator.observer.ExperimentSpec;
import com.cloudsimulator.observer.PowerCapFeasibility;

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
            ExperimentConfiguration cfgA = infra.toExperimentConfiguration(1, seed);
            ExperimentConfiguration cfgB = infra.toExperimentConfiguration(1, seed);
            List<double[]> frontA = runner.runOne(registry, label, cfgA, 1, "Balanced", seed).getFront();
            List<double[]> frontB = runner.runOne(registry, label, cfgB, 1, "Balanced", seed).getFront();

            assertNonEmptyFinite(label, frontA);
            assertIdentical(label, frontA, frontB);
        }

        // ---- PowerCeiling: fronts reproducible + coincident peaks captured ----
        System.out.println();
        System.out.println("--- PowerCeiling (aux coincident peak) ---");
        ExperimentSpec pcSpec = ExperimentSpec.powerCeiling("repro-test-pc");
        AlgorithmRegistry pcRegistry = new AlgorithmRegistry(params, primary);
        CampaignRunner pcRunner = new CampaignRunner(pcSpec, primary, infra, params, new String[] {"NSGA-II"});
        double[] caps = pcSpec.getCapThresholdsWatts();

        String[] pcLabels = {
            "NSGA-II_PC_190kW", "GA_WaitingTime_Dominance_PC_120kW", "WorkloadAware_Admission_PC_190kW"
        };
        for (String label : pcLabels) {
            ExperimentConfiguration cfgA = infra.toExperimentConfiguration(1, seed);
            ExperimentConfiguration cfgB = infra.toExperimentConfiguration(1, seed);
            AlgorithmRunResult rA = pcRunner.runOne(pcRegistry, label, cfgA, 1, "Balanced", seed);
            AlgorithmRunResult rB = pcRunner.runOne(pcRegistry, label, cfgB, 1, "Balanced", seed);

            assertNonEmptyFinite(label, rA.getFront());
            assertIdentical(label, rA.getFront(), rB.getFront());
            assertAuxPeaks(label, rA, rB, caps);
        }

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
}
