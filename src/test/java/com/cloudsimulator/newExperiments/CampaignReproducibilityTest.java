package com.cloudsimulator.newExperiments;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.observer.AlgorithmRunResult;
import com.cloudsimulator.observer.ExperimentSpec;

import java.util.List;

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
}
