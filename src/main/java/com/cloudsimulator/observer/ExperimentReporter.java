package com.cloudsimulator.observer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes the canonical experiment CSVs from analyzed {@link AlgorithmRunResult}s,
 * reproducing the existing schema byte-for-byte so the Python plotters and stats
 * scripts keep working unchanged.
 *
 * <p>Header strings and format specifiers are copied verbatim from the
 * {@code FinalExperiment} runners (the {@code WaitingTimeExperimentRunner}
 * variant, which produced the {@code reports/new} oracle):</p>
 * <ul>
 *   <li>{@code scenario_N_pareto_graph_data.csv} &mdash; objective {@code %.6f},
 *       energy {@code %.9f}</li>
 *   <li>{@code scenario_N_performance_metrics.csv} &mdash; per-seed + MEAN/STDDEV
 *       rows + a {@code Universal_Pareto,ALL} trailer</li>
 *   <li>{@code experiment_summary.csv}</li>
 *   <li>{@code plot_options.json}</li>
 * </ul>
 *
 * <p>The objective column <em>order</em> (objective then Energy) is the Python
 * contract; the names come from the {@link ExperimentSpec} via each run's
 * objective names. The {@code HV/GD/IGD/Spacing} columns carry the legacy
 * indicators (from {@link ParetoAnalyzer#computeLegacyIndicators}); the fixed
 * trio is emitted by {@code recompute_hv.py}, not here.</p>
 *
 * <p>Note: {@code quality_indicators.csv} is intentionally NOT written here. It
 * is produced by {@code scripts/recompute_hv.py} with full-precision Python
 * floats and cannot be byte-reproduced from Java {@code %.6f} formatting.</p>
 */
public final class ExperimentReporter {

    /** Bundles one scenario's analyzed data for reporting. */
    public static final class ScenarioReport {
        public final int scenarioNumber;
        public final String scenarioName;
        public final List<String> objectiveNames;
        /** Algorithm label &rarr; that label's per-seed runs (already analyzed). */
        public final Map<String, List<AlgorithmRunResult>> runsByLabel;
        public final List<double[]> universalFront;
        public final double universalHV;
        /** Algorithm label &rarr; that algorithm's aggregate (union-over-seeds) front. */
        public final Map<String, List<double[]>> algorithmFronts;

        public ScenarioReport(int scenarioNumber, String scenarioName, List<String> objectiveNames,
                              Map<String, List<AlgorithmRunResult>> runsByLabel,
                              List<double[]> universalFront, double universalHV,
                              Map<String, List<double[]>> algorithmFronts) {
            this.scenarioNumber = scenarioNumber;
            this.scenarioName = scenarioName;
            this.objectiveNames = objectiveNames;
            this.runsByLabel = runsByLabel;
            this.universalFront = universalFront;
            this.universalHV = universalHV;
            this.algorithmFronts = algorithmFronts;
        }
    }

    /** Default results root folder (relative to the working/root directory). */
    public static final String DEFAULT_RESULTS_ROOT = "results";

    /**
     * Writes all artifacts into a fresh per-experiment folder under the default
     * {@value #DEFAULT_RESULTS_ROOT} root: {@code results/<experimentId>/}, where
     * the id is resolved from the spec ({@code name_timestamp}, or
     * {@code generatedId_timestamp} when unnamed).
     *
     * @return the experiment folder that was created
     * @throws IOException if the directory cannot be created
     */
    public Path writeExperiment(ExperimentSpec spec, List<ScenarioReport> scenarios) throws IOException {
        return writeExperiment(DEFAULT_RESULTS_ROOT, spec.resolveExperimentId(), scenarios);
    }

    /**
     * Writes all artifacts into {@code <resultsRoot>/<experimentId>/}. Use this
     * overload to control the root folder or to pass a pre-resolved id (resolve
     * the id once and reuse it, since each unnamed resolution generates a new id).
     *
     * @return the experiment folder that was created
     * @throws IOException if the directory cannot be created
     */
    public Path writeExperiment(String resultsRoot, String experimentId,
                                List<ScenarioReport> scenarios) throws IOException {
        Path dir = Paths.get(resultsRoot, experimentId);
        writeAll(dir.toString(), scenarios);
        return dir;
    }

    /**
     * Writes all artifacts for a campaign into {@code outputDir}: per-scenario
     * pareto-graph, performance-metrics and algorithm-fronts CSVs, the experiment
     * summary, and {@code plot_options.json}.
     *
     * @throws IOException if the output directory cannot be created
     */
    public void writeAll(String outputDir, List<ScenarioReport> scenarios) throws IOException {
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        for (ScenarioReport scenario : scenarios) {
            writeParetoGraphData(dir, scenario);
            writePerformanceMetrics(dir, scenario);
            writeAlgorithmParetoFronts(dir, scenario);
        }
        writeExperimentSummary(dir, scenarios);
        writePlotOptions(dir, scenarios.size());
    }

    // -------------------------------------------------------------------------
    // scenario_N_pareto_graph_data.csv
    // -------------------------------------------------------------------------

    void writeParetoGraphData(Path dir, ScenarioReport s) throws IOException {
        String file = "scenario_" + s.scenarioNumber + "_pareto_graph_data.csv";
        try (PrintWriter w = new PrintWriter(new FileWriter(dir.resolve(file).toFile()))) {
            w.println("Algorithm,Seed," + s.objectiveNames.get(0) + ","
                + s.objectiveNames.get(1) + ",IsUniversalPareto");

            for (Map.Entry<String, List<AlgorithmRunResult>> entry : s.runsByLabel.entrySet()) {
                for (AlgorithmRunResult ar : entry.getValue()) {
                    for (double[] sol : ar.getFront()) {
                        boolean isUniv = ParetoAnalyzer.isInUniversalPareto(sol, s.universalFront);
                        w.printf("%s,%d,%.6f,%.9f,%b%n", ar.getLabel(), ar.getSeed(),
                            sol[0], sol[1], isUniv);
                    }
                }
            }
            for (double[] sol : s.universalFront) {
                w.printf("Universal_Pareto,0,%.6f,%.9f,true%n", sol[0], sol[1]);
            }
        }
    }

    // -------------------------------------------------------------------------
    // scenario_N_performance_metrics.csv
    // -------------------------------------------------------------------------

    void writePerformanceMetrics(Path dir, ScenarioReport s) throws IOException {
        String file = "scenario_" + s.scenarioNumber + "_performance_metrics.csv";
        try (PrintWriter w = new PrintWriter(new FileWriter(dir.resolve(file).toFile()))) {
            w.println("Algorithm,Seed,HV,GD,IGD,Spacing,NonDomSolutions,TotalSolutions,ParetoContribution,TimeMs");

            for (Map.Entry<String, List<AlgorithmRunResult>> entry : s.runsByLabel.entrySet()) {
                String label = entry.getKey();
                List<AlgorithmRunResult> seeds = entry.getValue();

                for (AlgorithmRunResult ar : seeds) {
                    w.printf("%s,%d,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d%n",
                        ar.getLabel(), ar.getSeed(), ar.getHv(), ar.getGd(), ar.getIgd(), ar.getSpacing(),
                        nonDomCount(ar), ar.getTotalCount(), ar.getParetoContributionCount(), ar.getRuntimeMs());
                }

                double[] hvs = collect(seeds, AlgorithmRunResult::getHv);
                double[] gds = collect(seeds, AlgorithmRunResult::getGd);
                double[] igds = collect(seeds, AlgorithmRunResult::getIgd);
                double[] spacings = collect(seeds, AlgorithmRunResult::getSpacing);
                double avgND = seeds.stream().mapToInt(ExperimentReporter::nonDomCount).average().orElse(0);
                double avgTotal = seeds.stream().mapToInt(AlgorithmRunResult::getTotalCount).average().orElse(0);
                int unionPCont = ParetoAnalyzer.unionContributionCount(seeds, s.universalFront);
                long avgTime = avgSuccessfulTime(seeds);

                w.printf("%s,MEAN,%.6f,%.6f,%.6f,%.6f,%.0f,%.0f,%d,%d%n",
                    label, mean(hvs), mean(gds), mean(igds), mean(spacings),
                    avgND, avgTotal, unionPCont, avgTime);

                w.printf("%s,STDDEV,%.6f,%.6f,%.6f,%.6f,,,,%n",
                    label, stddev(hvs), stddev(gds), stddev(igds), stddev(spacings));
            }

            int univSize = s.universalFront.size();
            w.printf("Universal_Pareto,ALL,%.6f,0.000000,0.000000,0.000000,%d,%d,%d,0%n",
                s.universalHV, univSize, univSize, univSize);
        }
    }

    // -------------------------------------------------------------------------
    // scenario_N_algorithm_pareto_fronts.csv (additive; not part of the legacy schema)
    // -------------------------------------------------------------------------

    /**
     * Writes each algorithm's aggregate (union-over-seeds) non-dominated front.
     * This is a NEW file, not part of the byte-compatible legacy schema, so it
     * leaves the existing CSVs untouched. Rows are grouped by algorithm and
     * sorted by the first objective (as produced by
     * {@link ParetoAnalyzer#computeAlgorithmFront}).
     */
    void writeAlgorithmParetoFronts(Path dir, ScenarioReport s) throws IOException {
        String file = "scenario_" + s.scenarioNumber + "_algorithm_pareto_fronts.csv";
        try (PrintWriter w = new PrintWriter(new FileWriter(dir.resolve(file).toFile()))) {
            w.println("Algorithm," + s.objectiveNames.get(0) + "," + s.objectiveNames.get(1));
            if (s.algorithmFronts != null) {
                for (Map.Entry<String, List<double[]>> entry : s.algorithmFronts.entrySet()) {
                    String label = entry.getKey();
                    for (double[] point : entry.getValue()) {
                        w.printf("%s,%.6f,%.9f%n", label, point[0], point[1]);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // experiment_summary.csv
    // -------------------------------------------------------------------------

    void writeExperimentSummary(Path dir, List<ScenarioReport> scenarios) throws IOException {
        Path file = dir.resolve("experiment_summary.csv");
        try (PrintWriter w = new PrintWriter(new FileWriter(file.toFile()))) {
            // Header objective names are taken from the first scenario (all
            // scenarios in a campaign share the same objectives).
            List<String> objNames = scenarios.isEmpty()
                ? List.of("Objective1", "Objective2") : scenarios.get(0).objectiveNames;
            w.println("Scenario,ScenarioName,Algorithm,Seed,HV,GD,IGD,Spacing,NonDomSolutions,"
                + "ParetoContribution,TimeMs," + objNames.get(0) + "_Best," + objNames.get(1) + "_Best");

            for (ScenarioReport s : scenarios) {
                for (Map.Entry<String, List<AlgorithmRunResult>> entry : s.runsByLabel.entrySet()) {
                    String label = entry.getKey();
                    List<AlgorithmRunResult> seeds = entry.getValue();

                    for (AlgorithmRunResult ar : seeds) {
                        double bestObj1 = bestObjective(ar.getFront(), 0);
                        double bestObj2 = bestObjective(ar.getFront(), 1);
                        w.printf("%d,%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%.6f,%.9f%n",
                            s.scenarioNumber, s.scenarioName, ar.getLabel(), ar.getSeed(),
                            ar.getHv(), ar.getGd(), ar.getIgd(), ar.getSpacing(),
                            nonDomCount(ar), ar.getParetoContributionCount(), ar.getRuntimeMs(),
                            bestObj1, bestObj2);
                    }

                    double[] hvs = collect(seeds, AlgorithmRunResult::getHv);
                    double[] gds = collect(seeds, AlgorithmRunResult::getGd);
                    double[] igds = collect(seeds, AlgorithmRunResult::getIgd);
                    double[] spacings = collect(seeds, AlgorithmRunResult::getSpacing);
                    int unionPCont = ParetoAnalyzer.unionContributionCount(seeds, s.universalFront);
                    long avgTime = avgSuccessfulTime(seeds);
                    double bestObj1All = bestObjectiveAcross(seeds, 0);
                    double bestObj2All = bestObjectiveAcross(seeds, 1);

                    w.printf("%d,%s,%s,MEAN,%.6f,%.6f,%.6f,%.6f,,%d,%d,%.6f,%.9f%n",
                        s.scenarioNumber, s.scenarioName, label,
                        mean(hvs), mean(gds), mean(igds), mean(spacings),
                        unionPCont, avgTime, bestObj1All, bestObj2All);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // plot_options.json
    // -------------------------------------------------------------------------

    void writePlotOptions(Path dir, int scenarioCount) throws IOException {
        Path file = dir.resolve("plot_options.json");
        try (PrintWriter w = new PrintWriter(new FileWriter(file.toFile()))) {
            w.println("{");
            w.println("  \"dpi\": 300,");
            w.println("  \"width\": 18,");
            w.println("  \"height\": 7,");
            w.println("  \"marker_size\": 10,");
            w.println("  \"show_legend\": true,");
            w.println("  \"show_labels\": true,");
            w.println("  \"scenarios\": " + scenarioCount);
            w.println("}");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers (mirror the runners' aggregation)
    // -------------------------------------------------------------------------

    private static int nonDomCount(AlgorithmRunResult ar) {
        return ar.getNonDominatedFront() != null ? ar.getNonDominatedCount() : ar.getTotalCount();
    }

    private static double[] collect(List<AlgorithmRunResult> seeds,
                                    java.util.function.ToDoubleFunction<AlgorithmRunResult> f) {
        return seeds.stream().mapToDouble(f).toArray();
    }

    private static long avgSuccessfulTime(List<AlgorithmRunResult> seeds) {
        long successfulRuns = seeds.stream().filter(a -> a.getRuntimeMs() > 0).count();
        if (successfulRuns == 0) {
            return 0;
        }
        long sum = seeds.stream().filter(a -> a.getRuntimeMs() > 0).mapToLong(AlgorithmRunResult::getRuntimeMs).sum();
        return sum / successfulRuns;
    }

    private static double bestObjective(List<double[]> front, int index) {
        double best = Double.MAX_VALUE;
        for (double[] sol : front) {
            if (!ParetoAnalyzer.isFailureSentinel(sol) && sol[index] < best) {
                best = sol[index];
            }
        }
        return best;
    }

    private static double bestObjectiveAcross(List<AlgorithmRunResult> seeds, int index) {
        double best = Double.MAX_VALUE;
        for (AlgorithmRunResult ar : seeds) {
            double b = bestObjective(ar.getFront(), index);
            if (b < best) {
                best = b;
            }
        }
        return best;
    }

    private static double mean(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double stddev(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double m = mean(values);
        double sumSq = 0;
        for (double v : values) {
            sumSq += (v - m) * (v - m);
        }
        return Math.sqrt(sumSq / values.length);
    }
}
