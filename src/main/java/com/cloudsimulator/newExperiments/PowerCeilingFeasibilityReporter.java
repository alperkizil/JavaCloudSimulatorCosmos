package com.cloudsimulator.newExperiments;

import com.cloudsimulator.observer.AlgorithmRunResult;
import com.cloudsimulator.observer.ExperimentReporter.ScenarioReport;

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
 * PowerCeiling feasibility reporter. Writes the three additional power-ceiling
 * CSVs on top of the canonical experiment CSVs, consumed by the Python plotter
 * ({@code scripts/plot_power_ceiling.py}).
 *
 * <p>This is a faithful port of the legacy
 * {@code FinalExperiment.PowerCeilingReporter} (identical headers, format
 * specifiers, non-domination/dedup logic and per-seed aggregation), rewired to
 * read from the observer {@link ScenarioReport}/{@link AlgorithmRunResult} model
 * instead of the legacy {@code ScenarioResult}/{@code AlgorithmResult}. The peak
 * power values it consumes are the <em>coincident</em> Step-8 peaks captured per
 * solution in {@link CampaignRunner} (a deliberate deviation from the legacy
 * analytical sweep — the feasibility claim reflects what the simulator drew).</p>
 *
 * <p>Outputs (into {@code results/<experimentId>/}):</p>
 * <ol>
 *   <li>{@code feasibility_summary.csv} — per (scenario, algorithm, cap)
 *       aggregation across seeds: mean/std/min/max feasibility rate plus totals.</li>
 *   <li>{@code pareto_3d_feasible.csv} — per (scenario, algorithm, cap) the
 *       feasibility-filtered non-dominated (WT, energy) front with the peak-power
 *       column preserved. Pooled across seeds.</li>
 *   <li>{@code pareto_3d_all.csv} — unfiltered (scenario, algorithm) 3D points
 *       (WT, energy, peak).</li>
 * </ol>
 */
public final class PowerCeilingFeasibilityReporter {

    private PowerCeilingFeasibilityReporter() {}

    public static void writeReports(String reportsDir,
                                    List<ScenarioReport> scenarios,
                                    double[] capLevelsWatts) {
        writeFeasibilitySummary(reportsDir, scenarios, capLevelsWatts);
        writeFeasibleParetoFronts(reportsDir, scenarios, capLevelsWatts);
        writeAllPoints3D(reportsDir, scenarios);
    }

    // ---------------------------------------------------------------------
    // 1. Seed-aggregated feasibility summary
    // ---------------------------------------------------------------------

    private static void writeFeasibilitySummary(String reportsDir,
                                                List<ScenarioReport> scenarios,
                                                double[] capLevelsWatts) {
        Path filePath = Paths.get(reportsDir, "feasibility_summary.csv");

        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile()))) {
            w.println("Scenario,ScenarioName,Algorithm,CapWatts,NumSeeds,"
                + "MeanFeasibilityRate,StdFeasibilityRate,MinFeasibilityRate,MaxFeasibilityRate,"
                + "TotalSolutionsAcrossSeeds,TotalFeasibleAcrossSeeds");

            for (ScenarioReport sr : scenarios) {
                for (Map.Entry<String, List<AlgorithmRunResult>> entry : sr.runsByLabel.entrySet()) {
                    String label = entry.getKey();
                    List<AlgorithmRunResult> seeds = entry.getValue();

                    for (double cap : capLevelsWatts) {
                        List<Double> perSeedRates = new ArrayList<>();
                        long totalSolutions = 0;
                        long totalFeasible = 0;

                        for (AlgorithmRunResult ar : seeds) {
                            List<Double> peaks = ar.getAuxPeakPowerWatts();
                            if (peaks == null || peaks.isEmpty()) continue;
                            int total = peaks.size();
                            long feasible = peaks.stream().filter(p -> p <= cap).count();
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
                                                  List<ScenarioReport> scenarios,
                                                  double[] capLevelsWatts) {
        Path filePath = Paths.get(reportsDir, "pareto_3d_feasible.csv");

        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile()))) {
            w.println("Scenario,ScenarioName,Algorithm,CapWatts,WaitingTime,Energy,PeakPowerWatts,Seed");

            for (ScenarioReport sr : scenarios) {
                for (Map.Entry<String, List<AlgorithmRunResult>> entry : sr.runsByLabel.entrySet()) {
                    String label = entry.getKey();
                    List<AlgorithmRunResult> seeds = entry.getValue();

                    for (double cap : capLevelsWatts) {
                        // Pool feasible points across seeds, preserving seed id for provenance.
                        List<Point3D> pool = new ArrayList<>();
                        for (AlgorithmRunResult ar : seeds) {
                            List<Double> peaks = ar.getAuxPeakPowerWatts();
                            if (peaks == null) continue;
                            List<double[]> solutions = ar.getFront();
                            for (int i = 0; i < solutions.size(); i++) {
                                double peak = i < peaks.size() ? peaks.get(i) : 0.0;
                                if (peak > cap) continue;
                                double[] obj = solutions.get(i);
                                pool.add(new Point3D(obj[0], obj[1], peak, ar.getSeed()));
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
                                         List<ScenarioReport> scenarios) {
        Path filePath = Paths.get(reportsDir, "pareto_3d_all.csv");

        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile()))) {
            w.println("Scenario,ScenarioName,Algorithm,Seed,WaitingTime,Energy,PeakPowerWatts");

            for (ScenarioReport sr : scenarios) {
                for (Map.Entry<String, List<AlgorithmRunResult>> entry : sr.runsByLabel.entrySet()) {
                    String label = entry.getKey();
                    for (AlgorithmRunResult ar : entry.getValue()) {
                        List<double[]> solutions = ar.getFront();
                        if (solutions == null) continue;
                        List<Double> peaks = ar.getAuxPeakPowerWatts();
                        for (int i = 0; i < solutions.size(); i++) {
                            double[] obj = solutions.get(i);
                            double peak = (peaks != null && i < peaks.size()) ? peaks.get(i) : 0.0;
                            w.printf("%d,%s,%s,%d,%.6f,%.9f,%.3f%n",
                                sr.scenarioNumber, sr.scenarioName, label, ar.getSeed(),
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
    // Helpers (lifted verbatim from the legacy PowerCeilingReporter)
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
        // a dominates b if a <= b on both objectives and strictly less on one
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
