package com.cloudsimulator.newExperiments;

import com.cloudsimulator.observer.ExperimentSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * New-side driver for the reduced-scale byte-diff parity gate. Reads the SAME
 * {@code -Dparity.*} system properties the patched legacy runner reads, so one
 * command line configures both sides identically, then writes to a fixed output
 * folder {@code results/<parity.outId>/} for diffing.
 *
 * <p>Usage: {@code java -Dparity.pop=8 -Dparity.iters=16 -Dparity.runs=2
 * -Dparity.scenarios=1 -Dparity.algos=FirstAvailable,GA_WaitingTime,...
 * -Dparity.primary=WAITING_TIME -Dparity.outId=parity_new ParityRun}</p>
 */
public final class ParityRun {

    public static void main(String[] args) {
        PrimaryObjective primary = PrimaryObjective.valueOf(
            System.getProperty("parity.primary", "WAITING_TIME"));
        String outId = System.getProperty("parity.outId", "parity_new");

        AlgorithmParameters params = AlgorithmParameters.defaults();
        params.verboseLogging = false;
        params.populationSize = Integer.getInteger("parity.pop", 8);
        params.iterationCount = Integer.getInteger("parity.iters", 16);

        ExperimentConfig infra = ExperimentConfig.defaults();
        infra.numRuns = Integer.getInteger("parity.runs", 2);

        int scenarioLimit = Math.min(infra.scenarioNames.length,
            Integer.getInteger("parity.scenarios", 1));
        infra.scenarioNames = trim(infra.scenarioNames, scenarioLimit);
        infra.scenarioTaskCounts = trim(infra.scenarioTaskCounts, scenarioLimit);

        // Study selector: defaults from the primary objective, but "powerceiling"
        // can be requested explicitly (it also uses WAITING_TIME primary).
        String specName = System.getProperty("parity.spec",
            primary == PrimaryObjective.MAKESPAN ? "scenariocomparison" : "waitingtime");
        boolean powerCeiling = specName.equalsIgnoreCase("powerceiling");

        String[] labels = resolveLabels(primary, powerCeiling);

        ExperimentSpec spec;
        if (powerCeiling) {
            spec = ExperimentSpec.powerCeiling("parity");
        } else if (specName.equalsIgnoreCase("scenariocomparison")) {
            spec = ExperimentSpec.scenarioComparison("parity");
        } else {
            spec = ExperimentSpec.waitingTime("parity");
        }

        new CampaignRunner(spec, primary, infra, params, labels).run(outId);
    }

    /** Subset of the registry's canonical labels selected by {@code -Dparity.algos}. */
    private static String[] resolveLabels(PrimaryObjective primary, boolean powerCeiling) {
        AlgorithmRegistry registry = new AlgorithmRegistry(AlgorithmParameters.defaults(), primary);
        List<String> canonical = powerCeiling
            ? registry.defaultPowerCeilingLabels()
            : registry.defaultLabels();
        String csv = System.getProperty("parity.algos");
        if (csv == null || csv.isBlank()) {
            return canonical.toArray(new String[0]);
        }
        java.util.Set<String> want = new java.util.LinkedHashSet<>(java.util.Arrays.asList(csv.split(",")));
        List<String> out = new ArrayList<>();
        for (String l : canonical) {
            if (want.contains(l)) {
                out.add(l);
            }
        }
        return out.toArray(new String[0]);
    }

    private static String[] trim(String[] a, int n) {
        String[] out = new String[n];
        System.arraycopy(a, 0, out, 0, n);
        return out;
    }

    private static int[][] trim(int[][] a, int n) {
        int[][] out = new int[n][];
        System.arraycopy(a, 0, out, 0, n);
        return out;
    }
}
