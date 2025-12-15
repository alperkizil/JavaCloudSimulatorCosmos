package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.MultiObjectiveSimulationResult;
import com.cloudsimulator.engine.MultiObjectiveSimulationResult.SolutionSimulationResult;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Reporter for generating CSV reports from multi-objective simulation results.
 *
 * Generates two reports:
 * 1. pareto_front.csv - Summary of all Pareto front solutions with predicted vs simulated metrics
 * 2. pareto_details.csv - Detailed metrics for each solution
 *
 * Usage:
 * <pre>
 * MultiObjectiveSimulationResult moResult = engine.runMultiObjective();
 *
 * ParetoFrontReporter reporter = new ParetoFrontReporter();
 * reporter.generateReports(moResult, "./reports/pareto_experiment");
 * </pre>
 */
public class ParetoFrontReporter {

    private Path outputDirectory;
    private int totalFilesGenerated;
    private long totalBytesWritten;

    /**
     * Generates all Pareto front reports to the specified directory.
     *
     * @param result Multi-objective simulation result
     * @param outputDir Output directory path
     * @throws IOException if writing fails
     */
    public void generateReports(MultiObjectiveSimulationResult result, String outputDir) throws IOException {
        generateReports(result, Paths.get(outputDir));
    }

    /**
     * Generates all Pareto front reports to the specified directory.
     *
     * @param result Multi-objective simulation result
     * @param outputDir Output directory path
     * @throws IOException if writing fails
     */
    public void generateReports(MultiObjectiveSimulationResult result, Path outputDir) throws IOException {
        this.outputDirectory = outputDir;
        Files.createDirectories(outputDir);

        totalFilesGenerated = 0;
        totalBytesWritten = 0;

        // Generate summary report
        generateSummaryReport(result);

        // Generate detailed comparison report
        generateComparisonReport(result);

        System.out.println("[ParetoFrontReporter] Generated " + totalFilesGenerated +
            " files (" + formatBytes(totalBytesWritten) + ") to " + outputDir.toAbsolutePath());
    }

    /**
     * Generates a summary report of the Pareto front.
     */
    private void generateSummaryReport(MultiObjectiveSimulationResult result) throws IOException {
        Path filePath = outputDirectory.resolve("pareto_summary.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            // Header
            StringBuilder header = new StringBuilder();
            header.append("solution_index");

            // Add predicted objective columns
            List<String> objectiveNames = result.getObjectiveNames();
            for (String name : objectiveNames) {
                header.append(",predicted_").append(name.toLowerCase().replace(" ", "_"));
            }

            // Add simulated metric columns
            header.append(",simulated_makespan_s");
            header.append(",simulated_energy_kwh");
            header.append(",simulated_carbon_kg");
            header.append(",makespan_discrepancy_s");
            header.append(",energy_discrepancy_kwh");
            header.append(",simulation_time_ms");
            header.append("\n");

            writer.write(header.toString());

            // Data rows
            for (SolutionSimulationResult solResult : result.getSolutionResults()) {
                StringBuilder row = new StringBuilder();
                row.append(solResult.getSolutionIndex());

                // Predicted objectives
                double[] predicted = solResult.getPredictedObjectives();
                for (double val : predicted) {
                    row.append(",").append(String.format("%.4f", val));
                }

                // Simulated metrics
                row.append(",").append(solResult.getSimulatedMakespan());
                row.append(",").append(String.format("%.6f", solResult.getSimulatedEnergyKWh()));
                row.append(",").append(String.format("%.6f", solResult.getSimulatedCarbonKg()));
                row.append(",").append(solResult.getMakespanDiscrepancy());
                row.append(",").append(String.format("%.6f", solResult.getEnergyDiscrepancy()));
                row.append(",").append(solResult.getSimulationTimeMs());
                row.append("\n");

                writer.write(row.toString());
            }
        }

        totalFilesGenerated++;
        totalBytesWritten += Files.size(filePath);
    }

    /**
     * Generates a detailed comparison report.
     */
    private void generateComparisonReport(MultiObjectiveSimulationResult result) throws IOException {
        Path filePath = outputDirectory.resolve("pareto_comparison.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            // Header
            StringBuilder header = new StringBuilder();
            header.append("solution_index");
            header.append(",is_best_makespan");
            header.append(",is_best_energy");

            // Add predicted objective columns
            List<String> objectiveNames = result.getObjectiveNames();
            for (String name : objectiveNames) {
                header.append(",predicted_").append(name.toLowerCase().replace(" ", "_"));
            }

            // Add simulated columns
            header.append(",simulated_makespan_s");
            header.append(",simulated_energy_kwh");
            header.append(",simulated_throughput");
            header.append(",simulated_avg_turnaround_s");
            header.append(",task_completion_rate");
            header.append(",avg_cpu_utilization");
            header.append(",avg_gpu_utilization");
            header.append("\n");

            writer.write(header.toString());

            // Find best solutions
            SolutionSimulationResult bestMakespan = result.getBestBySimulatedMakespan();
            SolutionSimulationResult bestEnergy = result.getBestBySimulatedEnergy();

            // Data rows
            for (SolutionSimulationResult solResult : result.getSolutionResults()) {
                StringBuilder row = new StringBuilder();
                row.append(solResult.getSolutionIndex());

                // Best flags
                row.append(",").append(solResult == bestMakespan ? "1" : "0");
                row.append(",").append(solResult == bestEnergy ? "1" : "0");

                // Predicted objectives
                double[] predicted = solResult.getPredictedObjectives();
                for (double val : predicted) {
                    row.append(",").append(String.format("%.4f", val));
                }

                // Simulated metrics from summary
                if (solResult.getSimulationSummary() != null) {
                    var summary = solResult.getSimulationSummary();
                    var perf = summary.getPerformance();
                    var infra = summary.getInfrastructure();
                    var tasks = summary.getTasks();

                    row.append(",").append(perf != null ? perf.makespanSeconds : 0);
                    row.append(",").append(String.format("%.6f",
                        summary.getEnergy() != null ? summary.getEnergy().totalITEnergyKWh : 0.0));
                    row.append(",").append(String.format("%.4f",
                        perf != null ? perf.throughputTasksPerSecond : 0.0));
                    row.append(",").append(String.format("%.2f",
                        perf != null ? perf.avgTurnaroundTimeSeconds : 0.0));
                    row.append(",").append(String.format("%.4f",
                        tasks != null ? tasks.completionRate : 0.0));
                    row.append(",").append(String.format("%.4f",
                        infra != null ? infra.avgCpuUtilization : 0.0));
                    row.append(",").append(String.format("%.4f",
                        infra != null ? infra.avgGpuUtilization : 0.0));
                } else {
                    // No summary available
                    row.append(",").append(solResult.getSimulatedMakespan());
                    row.append(",").append(String.format("%.6f", solResult.getSimulatedEnergyKWh()));
                    row.append(",0.0,0.0,0.0,0.0,0.0");
                }

                row.append("\n");
                writer.write(row.toString());
            }
        }

        totalFilesGenerated++;
        totalBytesWritten += Files.size(filePath);
    }

    /**
     * Creates a timestamped output directory for Pareto front reports.
     *
     * @param baseDir Base directory for reports
     * @param prefix Optional prefix for folder name
     * @return Created directory path
     * @throws IOException if creation fails
     */
    public static Path createParetoReportDirectory(String baseDir, String prefix) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(dateFormatter);
        String uniqueId = UUID.randomUUID().toString().substring(0, 6);

        String folderName;
        if (prefix != null && !prefix.isEmpty()) {
            folderName = String.format("%s_pareto_%s_%s", prefix, timestamp, uniqueId);
        } else {
            folderName = String.format("pareto_%s_%s", timestamp, uniqueId);
        }

        Path dir = Paths.get(baseDir).resolve(folderName);
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Formats bytes for display.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Gets the output directory where reports were saved.
     */
    public Path getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Gets total files generated.
     */
    public int getTotalFilesGenerated() {
        return totalFilesGenerated;
    }

    /**
     * Gets total bytes written.
     */
    public long getTotalBytesWritten() {
        return totalBytesWritten;
    }
}
