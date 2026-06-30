package com.cloudsimulator.newExperiments;

import com.cloudsimulator.observer.AlgorithmRunResult;
import com.cloudsimulator.observer.ExperimentReporter;
import com.cloudsimulator.observer.ExperimentSpec;
import com.cloudsimulator.observer.ParetoAnalyzer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Console output helpers for a campaign run (banner, per-scenario summary tables). */
public final class ConsoleReporter {

    private ConsoleReporter() {
    }

    public static void printBanner(ExperimentSpec spec, PrimaryObjective primary, ExperimentConfig infra,
                                   List<String> labels, String experimentId) {
        System.out.println("============================================================");
        System.out.println("  EXPERIMENT: " + primary.csvName() + " vs Energy");
        System.out.println("  Id: " + experimentId);
        System.out.println("  Objectives: " + spec.getObjectiveNames());
        System.out.println("  Algorithms (" + labels.size() + "): " + labels);
        System.out.println("  Scenarios: " + String.join(", ", infra.scenarioNames));
        System.out.println("  Seeds: " + infra.baseSeed + "-" + (infra.baseSeed + infra.numRuns - 1)
            + " (" + infra.numRuns + " runs each)");
        System.out.println("============================================================");
    }

    public static void printScenarioHeader(int scenarioNum, String scenarioName, ExperimentConfig infra) {
        int[] counts = infra.scenarioTaskCounts[scenarioNum - 1];
        System.out.println();
        System.out.println("************************************************************");
        System.out.println("  SCENARIO " + scenarioNum + ": " + scenarioName
            + "  (" + counts[0] + " CPU + " + counts[1] + " GPU tasks)");
        System.out.println("************************************************************");
    }

    public static void printScenarioSummary(ExperimentReporter.ScenarioReport report) {
        System.out.println();
        System.out.println("  SCENARIO " + report.scenarioNumber + " (" + report.scenarioName + ") RESULTS");
        System.out.printf("  %-28s | %-10s | %-10s | %-10s | %-10s | %-6s%n",
            "Algorithm", "HV", "GD", "IGD", "Spacing", "PCont");
        System.out.println("  " + "-".repeat(92));

        for (Map.Entry<String, List<AlgorithmRunResult>> entry : report.runsByLabel.entrySet()) {
            List<AlgorithmRunResult> seeds = entry.getValue();
            double[] hv = collect(seeds, AlgorithmRunResult::getHv);
            double[] gd = collect(seeds, AlgorithmRunResult::getGd);
            double[] igd = collect(seeds, AlgorithmRunResult::getIgd);
            double[] sp = collect(seeds, AlgorithmRunResult::getSpacing);
            int unionPCont = ParetoAnalyzer.unionContributionCount(seeds, report.universalFront);

            System.out.printf("  %-28s | %.4f | %.4f | %.4f | %.4f | %-6d%n",
                entry.getKey(), mean(hv), mean(gd), mean(igd), mean(sp), unionPCont);
        }
        System.out.printf("  %-28s | %.4f | %-10s | %-10s | %-10s | %-6d%n",
            "Universal", report.universalHV, "-", "-", "-", report.universalFront.size());
    }

    public static void printDone(Path outputDir) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  DONE. Results written to: " + outputDir.toAbsolutePath());
        System.out.println("============================================================");
    }

    private static double[] collect(List<AlgorithmRunResult> seeds,
                                    java.util.function.ToDoubleFunction<AlgorithmRunResult> f) {
        return seeds.stream().mapToDouble(f).toArray();
    }

    private static double mean(double[] v) {
        if (v.length == 0) {
            return 0;
        }
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return s / v.length;
    }
}
