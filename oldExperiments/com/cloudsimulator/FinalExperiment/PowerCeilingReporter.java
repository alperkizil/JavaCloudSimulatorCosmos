package com.cloudsimulator.FinalExperiment;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 3d reporter. Writes additional power-ceiling CSVs on top of the
 * per-seed feasibility CSV the runner already produces, consumed by the
 * Python plotter (scripts/plot_power_ceiling.py).
 *
 * <p>Outputs:
 * <ol>
 *   <li>{@code feasibility_summary.csv} — per (scenario, algorithm, cap)
 *       aggregation across seeds: mean/std feasibility rate plus totals.
 *       Feeds the stacked feasibility-rate bars.</li>
 *   <li>{@code pareto_3d_feasible.csv} — for each (scenario, algorithm,
 *       cap), the feasibility-filtered constrained Pareto front over
 *       (waiting time, energy) with the peak-power column preserved for
 *       3D projection. Pooled across seeds.</li>
 *   <li>{@code pareto_3d_all.csv} — unfiltered (scenario, algorithm)
 *       3D points (WT, energy, peak). Used by the plotter to colour
 *       non-dominated points by feasibility at each cap.</li>
 * </ol>
 *
 * <p>Additive: no existing CSV or generator is modified.</p>
 */
public final class PowerCeilingReporter {

    private PowerCeilingReporter() {}

    public static void writeReports(String reportsDir,
                                    List<PowerCeilingWaitingTimeExperimentRunner.ScenarioResult> scenarios,
                                    double[] capLevelsWatts) {
        writeFeasibilitySummary(reportsDir, scenarios, capLevelsWatts);
        writeFeasibleParetoFronts(reportsDir, scenarios, capLevelsWatts);
        writeAllPoints3D(reportsDir, scenarios);
    }

    // ---------------------------------------------------------------------
    // 1. Seed-aggregated feasibility summary
    // ---------------------------------------------------------------------

    private static void writeFeasibilitySummary(String reportsDir,
                                                List<PowerCeilingWaitingTimeExperimentRunner.ScenarioResult> scenarios,
                                                double[] capLevelsWatts) {
        Path filePath = Paths.get(reportsDir, "feasibility_summary.csv");

        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile()))) {
            w.println("Scenario,ScenarioName,Algorithm,CapWatts,NumSeeds,"
                + "MeanFeasibilityRate,StdFeasibilityRate,MinFeasibilityRate,MaxFeasibilityRate,"
                + "TotalSolutionsAcrossSeeds,TotalFeasibleAcrossSeeds");

            for (var sr : scenarios) {
                for (Map.Entry<String, List<PowerCeilingWaitingTimeExperimentRunner.AlgorithmResult>> entry
                        : sr.perSeedResults.entrySet()) {
                    String label = entry.getKey();
                    List<PowerCeilingWaitingTimeExperimentRunner.AlgorithmResult> seeds = entry.getValue();

                    for (double cap : capLevelsWatts) {
                        List<Double> perSeedRates = new ArrayList<>();
                        long totalSolutions = 0;
                        long totalFeasible = 0;

                        for (var ar : seeds) {
                            if (ar.peakPowersWatts == null || ar.peakPowersWatts.isEmpty()) continue;
                            int total = ar.peakPowersWatts.size();
                            long feasible = ar.peakPowersWatts.stream()
                                .filter(p -> p <= cap).count();
                            perSeedRates.add((double) feasible / total);
                            totalSolutions += total;
                            totalFeasible += feasible;
                        }

                        if (perSeedRates.isEmpty()) continue;
                        double mean = perSeedRates.stream().mapToDouble(Double::doubleValue)
                            .average().orElse(0.0);
                        double std = stddev(perSeedRates, mean);
                        double min = perSeedRates.stream().mapToDouble(Double::doubleValue)
                            .min().orElse(0.0);
                        double max = perSeedRates.stream().mapToDouble(Double::doubleValue)
                            .max().orElse(0.0);

                        w.printf("%d,%s,%s,%.1f,%d,%.4f,%.4f,%.4f,%.4f,%d,%d%n",
                            sr.scenarioNumber, sr.scenarioName, label, cap,
                            perSeedRates.size(), mean, std, min, max,
                            totalSolutions, totalFeasible);
                    }
                }
            }
            System.out.println("  Wrote: feasibility_summary.csv");
        } catch (IOException e) {
            System.err.println("  ERROR writing feasibility_summary.csv: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 2. Feasibility-filtered constrained Pareto fronts
    // ---------------------------------------------------------------------

    private static void writeFeasibleParetoFronts(String reportsDir,
                                                  List<PowerCeilingWaitingTimeExperimentRunner.ScenarioResult> scenarios,
                                                  double[] capLevelsWatts) {
        Path filePath = Paths.get(reportsDir, "pareto_3d_feasible.csv");

        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile()))) {
            w.println("Scenario,ScenarioName,Algorithm,CapWatts,WaitingTime,Energy,PeakPowerWatts,Seed");

            for (var sr : scenarios) {
                for (Map.Entry<String, List<PowerCeilingWaitingTimeExperimentRunner.AlgorithmResult>> entry
                        : sr.perSeedResults.entrySet()) {
                    String label = entry.getKey();
                    List<PowerCeilingWaitingTimeExperimentRunner.AlgorithmResult> seeds = entry.getValue();

                    for (double cap : capLevelsWatts) {
                        // Pool feasible points across seeds, preserving seed id for provenance.
                        List<Point3D> pool = new ArrayList<>();
                        for (var ar : seeds) {
                            if (ar.peakPowersWatts == null) continue;
                            for (int i = 0; i < ar.solutions.size(); i++) {
                                double peak = i < ar.peakPowersWatts.size()
                                    ? ar.peakPowersWatts.get(i) : 0.0;
                                if (peak > cap) continue;
                                double[] obj = ar.solutions.get(i);
                                pool.add(new Point3D(obj[0], obj[1], peak, ar.seed));
                            }
                        }
                        if (pool.isEmpty()) continue;

                        List<Point3D> front = nonDominated2D(pool);
                        front.sort(Comparator.comparingDouble((Point3D p) -> p.wt)
                            .thenComparingDouble(p -> p.energy));

                        for (Point3D p : front) {
                            w.printf("%d,%s,%s,%.1f,%.6f,%.9f,%.3f,%d%n",
                                sr.scenarioNumber, sr.scenarioName, label, cap,
                                p.wt, p.energy, p.peak, p.seed);
                        }
                    }
                }
            }
            System.out.println("  Wrote: pareto_3d_feasible.csv");
        } catch (IOException e) {
            System.err.println("  ERROR writing pareto_3d_feasible.csv: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 3. All 3D points (for feasibility-coloured scatter plots)
    // ---------------------------------------------------------------------

    private static void writeAllPoints3D(String reportsDir,
                                         List<PowerCeilingWaitingTimeExperimentRunner.ScenarioResult> scenarios) {
        Path filePath = Paths.get(reportsDir, "pareto_3d_all.csv");

        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile()))) {
            w.println("Scenario,ScenarioName,Algorithm,Seed,WaitingTime,Energy,PeakPowerWatts");

            for (var sr : scenarios) {
                for (Map.Entry<String, List<PowerCeilingWaitingTimeExperimentRunner.AlgorithmResult>> entry
                        : sr.perSeedResults.entrySet()) {
                    String label = entry.getKey();
                    for (var ar : entry.getValue()) {
                        if (ar.solutions == null) continue;
                        for (int i = 0; i < ar.solutions.size(); i++) {
                            double[] obj = ar.solutions.get(i);
                            double peak = (ar.peakPowersWatts != null && i < ar.peakPowersWatts.size())
                                ? ar.peakPowersWatts.get(i) : 0.0;
                            w.printf("%d,%s,%s,%d,%.6f,%.9f,%.3f%n",
                                sr.scenarioNumber, sr.scenarioName, label, ar.seed,
                                obj[0], obj[1], peak);
                        }
                    }
                }
            }
            System.out.println("  Wrote: pareto_3d_all.csv");
        } catch (IOException e) {
            System.err.println("  ERROR writing pareto_3d_all.csv: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Non-dominated set over (wt, energy), minimization on both. */
    private static List<Point3D> nonDominated2D(Collection<Point3D> points) {
        List<Point3D> out = new ArrayList<>();
        for (Point3D p : points) {
            boolean dominated = false;
            for (Point3D q : points) {
                if (q == p) continue;
                if (dominates(q, p)) { dominated = true; break; }
            }
            if (!dominated) out.add(p);
        }
        // Deduplicate on (wt, energy) to avoid noise from multiple seeds hitting
        // the same objective vector.
        Map<String, Point3D> uniq = new LinkedHashMap<>();
        for (Point3D p : out) {
            String key = String.format("%.6f|%.9f", p.wt, p.energy);
            uniq.putIfAbsent(key, p);
        }
        return new ArrayList<>(uniq.values());
    }

    private static boolean dominates(Point3D a, Point3D b) {
        // a dominates b if a ≤ b on both objectives and strictly less on one
        if (a.wt > b.wt || a.energy > b.energy) return false;
        return a.wt < b.wt || a.energy < b.energy;
    }

    private static double stddev(List<Double> values, double mean) {
        if (values.size() < 2) return 0.0;
        double sumSq = 0.0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        return Math.sqrt(sumSq / (values.size() - 1));
    }

    private static final class Point3D {
        final double wt;
        final double energy;
        final double peak;
        final long seed;
        Point3D(double wt, double energy, double peak, long seed) {
            this.wt = wt;
            this.energy = energy;
            this.peak = peak;
            this.seed = seed;
        }
    }
}
