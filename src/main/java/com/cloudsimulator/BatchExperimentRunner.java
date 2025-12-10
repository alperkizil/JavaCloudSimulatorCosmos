package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationEngine;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.steps.*;
import com.cloudsimulator.steps.EnergyCalculationStep.CarbonIntensityRegion;
import com.cloudsimulator.strategy.*;
import com.cloudsimulator.strategy.task.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Batch runner for executing multiple simulation experiments and comparing results.
 *
 * This runner:
 * 1. Finds all .cosc config files in a directory
 * 2. Runs each configuration through all 10 simulation steps
 * 3. Collects and aggregates results
 * 4. Generates a comparison report
 *
 * Usage:
 *   java com.cloudsimulator.BatchExperimentRunner [configDir] [reportDir]
 *
 * Example:
 *   java com.cloudsimulator.BatchExperimentRunner configs/sampleScenario ./reports
 */
public class BatchExperimentRunner {

    // Configuration
    private String configDirectory;
    private String reportOutputDir = "./reports";

    // Strategy choices (consistent across all experiments)
    private HostPlacementStrategy hostPlacementStrategy = new FirstFitHostPlacementStrategy();
    private VMPlacementStrategy vmPlacementStrategy = new FirstFitVMPlacementStrategy();
    private TaskAssignmentStrategy taskAssignmentStrategy = new FirstAvailableTaskAssignmentStrategy();

    // Energy configuration
    private double pue = 1.5;
    private CarbonIntensityRegion carbonRegion = CarbonIntensityRegion.US_AVERAGE;
    private double electricityCostPerKWh = 0.10;

    // SLA configuration
    private long primarySLAThreshold = 3600; // 1 hour

    // Results storage
    private List<ExperimentResult> results = new ArrayList<>();
    private long totalExecutionTimeMs;

    /**
     * Container for individual experiment results.
     */
    public static class ExperimentResult {
        public String configFile;
        public long seed;
        public long executionTimeMs;
        public SimulationSummary summary;
        public boolean success;
        public String errorMessage;

        // Key metrics for comparison
        public int totalTasks;
        public int completedTasks;
        public double completionRate;
        public long makespanSeconds;
        public double throughput;
        public double avgTurnaroundTime;
        public double p90TurnaroundTime;
        public double p99TurnaroundTime;
        public double totalEnergyKWh;
        public double carbonFootprintKg;
        public double estimatedCostDollars;
        public double slaCompliancePercent;

        public void extractMetrics() {
            if (summary != null) {
                totalTasks = summary.getTasks().totalTasks;
                completedTasks = summary.getTasks().completedTasks;
                completionRate = summary.getTasks().completionRate;
                makespanSeconds = summary.getPerformance().makespanSeconds;
                throughput = summary.getPerformance().throughputTasksPerSecond;
                avgTurnaroundTime = summary.getPerformance().avgTurnaroundTimeSeconds;
                p90TurnaroundTime = summary.getPerformance().p90TurnaroundTimeSeconds;
                p99TurnaroundTime = summary.getPerformance().p99TurnaroundTimeSeconds;
                totalEnergyKWh = summary.getEnergy().totalFacilityEnergyKWh;
                carbonFootprintKg = summary.getEnergy().carbonFootprintKg;
                estimatedCostDollars = summary.getEnergy().estimatedCostDollars;
                slaCompliancePercent = summary.getSla().slaCompliancePercent;
            }
        }
    }

    public BatchExperimentRunner(String configDirectory) {
        this.configDirectory = configDirectory;
    }

    /**
     * Runs all experiments in the config directory.
     *
     * @return List of ExperimentResult objects
     */
    public List<ExperimentResult> runAll() {
        long batchStartTime = System.currentTimeMillis();
        results.clear();

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        JavaCloudSimulatorCosmos - Batch Experiment Runner    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Find all .cosc files
        List<File> configFiles = findConfigFiles();

        if (configFiles.isEmpty()) {
            System.out.println("ERROR: No .cosc files found in: " + configDirectory);
            return results;
        }

        System.out.println("Found " + configFiles.size() + " configuration files in: " + configDirectory);
        System.out.println();

        // Print experiment plan
        System.out.println("═══ EXPERIMENT PLAN ═══");
        System.out.printf("  Host Placement: %s%n", hostPlacementStrategy.getStrategyName());
        System.out.printf("  VM Placement: %s%n", vmPlacementStrategy.getStrategyName());
        System.out.printf("  Task Assignment: %s%n", taskAssignmentStrategy.getStrategyName());
        System.out.printf("  PUE: %.2f%n", pue);
        System.out.printf("  Carbon Region: %s%n", carbonRegion);
        System.out.printf("  SLA Threshold: %d seconds%n", primarySLAThreshold);
        System.out.println();

        // Create batch output directory
        String batchTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String batchDir = reportOutputDir + "/batch_" + batchTimestamp;

        try {
            Files.createDirectories(Paths.get(batchDir));
        } catch (IOException e) {
            System.out.println("ERROR: Could not create batch directory: " + e.getMessage());
            return results;
        }

        // Run each experiment
        int experimentNum = 1;
        for (File configFile : configFiles) {
            System.out.println("════════════════════════════════════════════════════════════════");
            System.out.printf("EXPERIMENT %d/%d: %s%n", experimentNum, configFiles.size(), configFile.getName());
            System.out.println("════════════════════════════════════════════════════════════════");

            ExperimentResult result = runSingleExperiment(configFile.getAbsolutePath(), batchDir);
            results.add(result);

            // Print brief summary
            if (result.success) {
                System.out.printf("  ✓ Completed in %.1f seconds%n", result.executionTimeMs / 1000.0);
                System.out.printf("    Makespan: %d s, Throughput: %.4f tasks/s, SLA: %.1f%%%n",
                    result.makespanSeconds, result.throughput, result.slaCompliancePercent);
            } else {
                System.out.printf("  ✗ FAILED: %s%n", result.errorMessage);
            }
            System.out.println();

            experimentNum++;
        }

        totalExecutionTimeMs = System.currentTimeMillis() - batchStartTime;

        // Generate comparison report
        generateComparisonReport(batchDir);

        // Print final summary
        printBatchSummary(batchDir);

        return results;
    }

    /**
     * Runs a single experiment.
     */
    private ExperimentResult runSingleExperiment(String configPath, String batchDir) {
        ExperimentResult result = new ExperimentResult();
        result.configFile = configPath;

        long startTime = System.currentTimeMillis();

        try {
            // Load configuration
            FileConfigParser parser = new FileConfigParser();
            ExperimentConfiguration config = parser.parse(configPath);
            result.seed = config.getRandomSeed();

            // Create experiment-specific output directory
            String experimentDir = batchDir + "/seed_" + config.getRandomSeed();

            // Create simulation engine
            SimulationEngine engine = new SimulationEngine();
            engine.configure(config);

            // Add all 10 simulation steps
            engine.addStep(new InitializationStep(config));
            engine.addStep(new HostPlacementStep(hostPlacementStrategy));
            engine.addStep(new UserDatacenterMappingStep());
            engine.addStep(new VMPlacementStep(vmPlacementStrategy));
            engine.addStep(new TaskAssignmentStep(taskAssignmentStrategy));
            engine.addStep(new VMExecutionStep());
            engine.addStep(new TaskExecutionStep());

            EnergyCalculationStep energyStep = new EnergyCalculationStep();
            energyStep.setPUE(pue);
            energyStep.setCarbonIntensity(carbonRegion);
            energyStep.setElectricityCostPerKWh(electricityCostPerKWh);
            engine.addStep(energyStep);

            MetricsCollectionStep metricsStep = new MetricsCollectionStep();
            metricsStep.setPrimarySLAThreshold(primarySLAThreshold);
            engine.addStep(metricsStep);

            ReportingStep reportingStep = new ReportingStep();
            reportingStep.setBaseOutputDirectory(experimentDir);
            reportingStep.setCustomPrefix("seed_" + config.getRandomSeed());
            engine.addStep(reportingStep);

            // Run simulation
            engine.run();

            // Store results
            result.summary = metricsStep.getSummary();
            result.extractMetrics();
            result.success = true;

        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            e.printStackTrace();
        }

        result.executionTimeMs = System.currentTimeMillis() - startTime;
        return result;
    }

    /**
     * Finds all .cosc files in the config directory.
     */
    private List<File> findConfigFiles() {
        File dir = new File(configDirectory);
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".cosc"));
        if (files == null) {
            return Collections.emptyList();
        }

        // Sort by filename (which typically includes the seed number)
        return Arrays.stream(files)
            .sorted(Comparator.comparing(File::getName))
            .collect(Collectors.toList());
    }

    /**
     * Generates a comparison CSV report of all experiments.
     */
    private void generateComparisonReport(String batchDir) {
        String reportPath = batchDir + "/batch_comparison.csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(reportPath))) {
            // Header
            writer.println("seed,config_file,execution_time_ms,success," +
                "total_tasks,completed_tasks,completion_rate," +
                "makespan_seconds,throughput_tasks_per_sec," +
                "avg_turnaround_s,p90_turnaround_s,p99_turnaround_s," +
                "total_energy_kwh,carbon_footprint_kg,estimated_cost_dollars," +
                "sla_compliance_percent");

            // Data rows
            for (ExperimentResult result : results) {
                writer.printf("%d,%s,%d,%b,",
                    result.seed,
                    new File(result.configFile).getName(),
                    result.executionTimeMs,
                    result.success);

                if (result.success) {
                    writer.printf("%d,%d,%.4f,",
                        result.totalTasks,
                        result.completedTasks,
                        result.completionRate);
                    writer.printf("%d,%.6f,",
                        result.makespanSeconds,
                        result.throughput);
                    writer.printf("%.2f,%.2f,%.2f,",
                        result.avgTurnaroundTime,
                        result.p90TurnaroundTime,
                        result.p99TurnaroundTime);
                    writer.printf("%.6f,%.6f,%.6f,",
                        result.totalEnergyKWh,
                        result.carbonFootprintKg,
                        result.estimatedCostDollars);
                    writer.printf("%.2f%n",
                        result.slaCompliancePercent);
                } else {
                    writer.println(",,,,,,,,,,,,");
                }
            }

            System.out.println("Comparison report saved to: " + reportPath);

        } catch (IOException e) {
            System.out.println("ERROR: Could not write comparison report: " + e.getMessage());
        }

        // Generate statistics summary
        generateStatisticsSummary(batchDir);
    }

    /**
     * Generates aggregate statistics across all experiments.
     */
    private void generateStatisticsSummary(String batchDir) {
        String summaryPath = batchDir + "/batch_statistics.txt";

        List<ExperimentResult> successfulResults = results.stream()
            .filter(r -> r.success)
            .collect(Collectors.toList());

        if (successfulResults.isEmpty()) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(summaryPath))) {
            writer.println("╔══════════════════════════════════════════════════════════════╗");
            writer.println("║              BATCH EXPERIMENT STATISTICS                     ║");
            writer.println("╚══════════════════════════════════════════════════════════════╝");
            writer.println();

            writer.printf("Total Experiments: %d%n", results.size());
            writer.printf("Successful: %d%n", successfulResults.size());
            writer.printf("Failed: %d%n", results.size() - successfulResults.size());
            writer.println();

            // Calculate statistics
            DoubleSummaryStatistics makespanStats = successfulResults.stream()
                .mapToDouble(r -> r.makespanSeconds).summaryStatistics();
            DoubleSummaryStatistics throughputStats = successfulResults.stream()
                .mapToDouble(r -> r.throughput).summaryStatistics();
            DoubleSummaryStatistics turnaroundStats = successfulResults.stream()
                .mapToDouble(r -> r.avgTurnaroundTime).summaryStatistics();
            DoubleSummaryStatistics p90Stats = successfulResults.stream()
                .mapToDouble(r -> r.p90TurnaroundTime).summaryStatistics();
            DoubleSummaryStatistics energyStats = successfulResults.stream()
                .mapToDouble(r -> r.totalEnergyKWh).summaryStatistics();
            DoubleSummaryStatistics carbonStats = successfulResults.stream()
                .mapToDouble(r -> r.carbonFootprintKg).summaryStatistics();
            DoubleSummaryStatistics costStats = successfulResults.stream()
                .mapToDouble(r -> r.estimatedCostDollars).summaryStatistics();
            DoubleSummaryStatistics slaStats = successfulResults.stream()
                .mapToDouble(r -> r.slaCompliancePercent).summaryStatistics();

            writer.println("═══ MAKESPAN (seconds) ═══");
            writer.printf("  Min: %.0f%n", makespanStats.getMin());
            writer.printf("  Max: %.0f%n", makespanStats.getMax());
            writer.printf("  Avg: %.2f%n", makespanStats.getAverage());
            writer.printf("  StdDev: %.2f%n", calculateStdDev(successfulResults.stream()
                .mapToDouble(r -> r.makespanSeconds).toArray()));
            writer.println();

            writer.println("═══ THROUGHPUT (tasks/sec) ═══");
            writer.printf("  Min: %.6f%n", throughputStats.getMin());
            writer.printf("  Max: %.6f%n", throughputStats.getMax());
            writer.printf("  Avg: %.6f%n", throughputStats.getAverage());
            writer.println();

            writer.println("═══ AVG TURNAROUND TIME (seconds) ═══");
            writer.printf("  Min: %.2f%n", turnaroundStats.getMin());
            writer.printf("  Max: %.2f%n", turnaroundStats.getMax());
            writer.printf("  Avg: %.2f%n", turnaroundStats.getAverage());
            writer.println();

            writer.println("═══ P90 TURNAROUND TIME (seconds) ═══");
            writer.printf("  Min: %.2f%n", p90Stats.getMin());
            writer.printf("  Max: %.2f%n", p90Stats.getMax());
            writer.printf("  Avg: %.2f%n", p90Stats.getAverage());
            writer.println();

            writer.println("═══ ENERGY CONSUMPTION (kWh) ═══");
            writer.printf("  Min: %.6f%n", energyStats.getMin());
            writer.printf("  Max: %.6f%n", energyStats.getMax());
            writer.printf("  Avg: %.6f%n", energyStats.getAverage());
            writer.printf("  Total: %.6f%n", energyStats.getSum());
            writer.println();

            writer.println("═══ CARBON FOOTPRINT (kg CO2) ═══");
            writer.printf("  Min: %.6f%n", carbonStats.getMin());
            writer.printf("  Max: %.6f%n", carbonStats.getMax());
            writer.printf("  Avg: %.6f%n", carbonStats.getAverage());
            writer.printf("  Total: %.6f%n", carbonStats.getSum());
            writer.println();

            writer.println("═══ ESTIMATED COST ($) ═══");
            writer.printf("  Min: $%.4f%n", costStats.getMin());
            writer.printf("  Max: $%.4f%n", costStats.getMax());
            writer.printf("  Avg: $%.4f%n", costStats.getAverage());
            writer.printf("  Total: $%.4f%n", costStats.getSum());
            writer.println();

            writer.println("═══ SLA COMPLIANCE (%%) ═══");
            writer.printf("  Min: %.2f%%%n", slaStats.getMin());
            writer.printf("  Max: %.2f%%%n", slaStats.getMax());
            writer.printf("  Avg: %.2f%%%n", slaStats.getAverage());
            writer.println();

            writer.println("═══ EXPERIMENT CONFIGURATION ═══");
            writer.printf("  Host Placement: %s%n", hostPlacementStrategy.getStrategyName());
            writer.printf("  VM Placement: %s%n", vmPlacementStrategy.getStrategyName());
            writer.printf("  Task Assignment: %s%n", taskAssignmentStrategy.getStrategyName());
            writer.printf("  PUE: %.2f%n", pue);
            writer.printf("  Carbon Region: %s%n", carbonRegion);
            writer.printf("  SLA Threshold: %d seconds%n", primarySLAThreshold);
            writer.println();

            writer.printf("Total batch execution time: %.2f seconds%n", totalExecutionTimeMs / 1000.0);

            System.out.println("Statistics summary saved to: " + summaryPath);

        } catch (IOException e) {
            System.out.println("ERROR: Could not write statistics summary: " + e.getMessage());
        }
    }

    /**
     * Calculates standard deviation.
     */
    private double calculateStdDev(double[] values) {
        if (values.length == 0) return 0;
        double mean = Arrays.stream(values).average().orElse(0);
        double sumSquaredDiff = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .sum();
        return Math.sqrt(sumSquaredDiff / values.length);
    }

    /**
     * Prints the final batch summary.
     */
    private void printBatchSummary(String batchDir) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                   BATCH RUN COMPLETE                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        long successCount = results.stream().filter(r -> r.success).count();
        long failCount = results.size() - successCount;

        System.out.printf("Experiments: %d total, %d successful, %d failed%n",
            results.size(), successCount, failCount);
        System.out.println();

        if (successCount > 0) {
            // Calculate aggregate statistics
            List<ExperimentResult> successful = results.stream()
                .filter(r -> r.success)
                .collect(Collectors.toList());

            DoubleSummaryStatistics makespanStats = successful.stream()
                .mapToDouble(r -> r.makespanSeconds).summaryStatistics();
            DoubleSummaryStatistics throughputStats = successful.stream()
                .mapToDouble(r -> r.throughput).summaryStatistics();
            DoubleSummaryStatistics slaStats = successful.stream()
                .mapToDouble(r -> r.slaCompliancePercent).summaryStatistics();
            DoubleSummaryStatistics energyStats = successful.stream()
                .mapToDouble(r -> r.totalEnergyKWh).summaryStatistics();

            System.out.println("═══ AGGREGATE STATISTICS ═══");
            System.out.println();
            System.out.println("┌──────────────────────┬───────────┬───────────┬───────────┐");
            System.out.println("│       Metric         │    Min    │    Avg    │    Max    │");
            System.out.println("├──────────────────────┼───────────┼───────────┼───────────┤");
            System.out.printf("│ Makespan (sec)       │ %9.0f │ %9.1f │ %9.0f │%n",
                makespanStats.getMin(), makespanStats.getAverage(), makespanStats.getMax());
            System.out.printf("│ Throughput (t/s)     │ %9.4f │ %9.4f │ %9.4f │%n",
                throughputStats.getMin(), throughputStats.getAverage(), throughputStats.getMax());
            System.out.printf("│ SLA Compliance (%%)   │ %8.2f%% │ %8.2f%% │ %8.2f%% │%n",
                slaStats.getMin(), slaStats.getAverage(), slaStats.getMax());
            System.out.printf("│ Energy (kWh)         │ %9.4f │ %9.4f │ %9.4f │%n",
                energyStats.getMin(), energyStats.getAverage(), energyStats.getMax());
            System.out.println("└──────────────────────┴───────────┴───────────┴───────────┘");
            System.out.println();
        }

        System.out.println("═══ OUTPUT ═══");
        System.out.println("  Batch Directory: " + batchDir);
        System.out.println("  Comparison Report: " + batchDir + "/batch_comparison.csv");
        System.out.println("  Statistics Summary: " + batchDir + "/batch_statistics.txt");
        System.out.println();

        System.out.printf("Total batch execution time: %.2f seconds (%.2f minutes)%n",
            totalExecutionTimeMs / 1000.0, totalExecutionTimeMs / 60000.0);
        System.out.println();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Configuration Setters
    // ═══════════════════════════════════════════════════════════════════════

    public void setReportOutputDir(String dir) {
        this.reportOutputDir = dir;
    }

    public void setHostPlacementStrategy(HostPlacementStrategy strategy) {
        this.hostPlacementStrategy = strategy;
    }

    public void setVmPlacementStrategy(VMPlacementStrategy strategy) {
        this.vmPlacementStrategy = strategy;
    }

    public void setTaskAssignmentStrategy(TaskAssignmentStrategy strategy) {
        this.taskAssignmentStrategy = strategy;
    }

    public void setPue(double pue) {
        this.pue = pue;
    }

    public void setCarbonRegion(CarbonIntensityRegion region) {
        this.carbonRegion = region;
    }

    public void setElectricityCostPerKWh(double cost) {
        this.electricityCostPerKWh = cost;
    }

    public void setPrimarySLAThreshold(long seconds) {
        this.primarySLAThreshold = seconds;
    }

    // Getters
    public List<ExperimentResult> getResults() {
        return results;
    }

    public long getTotalExecutionTimeMs() {
        return totalExecutionTimeMs;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Main Method
    // ═══════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        String configDir = "configs/sampleScenario";
        String reportDir = "./reports";

        if (args.length >= 1) {
            configDir = args[0];
        }
        if (args.length >= 2) {
            reportDir = args[1];
        }

        BatchExperimentRunner runner = new BatchExperimentRunner(configDir);
        runner.setReportOutputDir(reportDir);

        // You can customize strategies here:
        // runner.setHostPlacementStrategy(new PowerAwareConsolidatingHostPlacementStrategy());
        // runner.setVmPlacementStrategy(new PowerAwareVMPlacementStrategy());
        // runner.setTaskAssignmentStrategy(new WorkloadAwareTaskAssignmentStrategy());

        runner.runAll();
    }
}
