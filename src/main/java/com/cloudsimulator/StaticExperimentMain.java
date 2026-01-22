package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.reporter.SimpleReporter;
import com.cloudsimulator.utils.RandomGenerator;

// Steps
import com.cloudsimulator.steps.InitializationStep;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.steps.UserDatacenterMappingStep;
import com.cloudsimulator.steps.VMPlacementStep;
import com.cloudsimulator.steps.TaskAssignmentStep;
import com.cloudsimulator.steps.VMExecutionStep;
import com.cloudsimulator.steps.TaskExecutionStep;
import com.cloudsimulator.steps.EnergyCalculationStep;
import com.cloudsimulator.steps.MetricsCollectionStep;
import com.cloudsimulator.steps.ReportingStep;

// Placement strategies
import com.cloudsimulator.PlacementStrategy.hostPlacement.PowerAwareLoadBalancingHostPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.VMPlacement.BestFitVMPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GenerationalGATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Static main file that runs 10 cloud simulation experiments.
 *
 * This class loads all 10 configuration files from configs/experiment1/ directory
 * and runs each experiment sequentially with hardcoded strategies:
 * - Host Placement: PowerAwareLoadBalancingHostPlacementStrategy
 * - VM Placement: BestFitVMPlacementStrategy
 * - Task Assignment: GenerationalGATaskSchedulingStrategy (Energy objective only)
 *
 * GA Configuration:
 * - Population: 100
 * - Generations: 200
 * - Crossover rate: 0.9
 * - Mutation rate: 0.1
 * - Elite count: 10
 * - Objective: Energy Consumption ONLY
 * - Verbose logging enabled
 *
 * Output: CSV reports saved to reports/ directory using SimpleReporter
 */
public class StaticExperimentMain {

    private static final String CONFIG_DIRECTORY = "configs/experiment1";
    private static final String REPORTS_DIRECTORY = "reports";

    // GA Configuration parameters
    private static final int GA_POPULATION_SIZE = 100;
    private static final int GA_GENERATIONS = 200;
    private static final double GA_CROSSOVER_RATE = 0.9;
    private static final double GA_MUTATION_RATE = 0.1;
    private static final int GA_ELITE_COUNT = 10;

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("  Static Experiment Runner - 10 Cloud Simulations");
        System.out.println("========================================================");
        System.out.println();

        // Load configuration files
        List<File> configFiles = loadConfigFiles();
        if (configFiles.isEmpty()) {
            System.err.println("ERROR: No .cosc configuration files found in " + CONFIG_DIRECTORY);
            System.exit(1);
        }

        System.out.println("Found " + configFiles.size() + " configuration files:");
        for (File f : configFiles) {
            System.out.println("  - " + f.getName());
        }
        System.out.println();

        // Ensure reports directory exists
        ensureReportsDirectory();

        // Run experiments
        List<ExperimentResult> results = new ArrayList<>();
        int experimentNumber = 1;

        for (File configFile : configFiles) {
            System.out.println("========================================================");
            System.out.println("  EXPERIMENT " + experimentNumber + "/" + configFiles.size());
            System.out.println("  Config: " + configFile.getName());
            System.out.println("========================================================");
            System.out.println();

            try {
                ExperimentResult result = runExperiment(configFile, experimentNumber);
                results.add(result);
                printExperimentSummary(result);
            } catch (Exception e) {
                System.err.println("ERROR: Experiment " + experimentNumber + " failed: " + e.getMessage());
                e.printStackTrace();
                results.add(new ExperimentResult(configFile.getName(), experimentNumber, e));
            }

            experimentNumber++;
            System.out.println();
        }

        // Print final summary
        printFinalSummary(results);
    }

    /**
     * Loads all .cosc configuration files from the experiment directory.
     */
    private static List<File> loadConfigFiles() {
        List<File> configFiles = new ArrayList<>();
        File configDir = new File(CONFIG_DIRECTORY);

        if (!configDir.exists() || !configDir.isDirectory()) {
            System.err.println("Configuration directory not found: " + CONFIG_DIRECTORY);
            return configFiles;
        }

        File[] files = configDir.listFiles((dir, name) -> name.endsWith(".cosc"));
        if (files != null) {
            // Sort by filename for consistent order
            Arrays.sort(files, (f1, f2) -> {
                // Extract the number from the beginning of the filename for proper numeric sorting
                try {
                    int num1 = Integer.parseInt(f1.getName().split("_")[0]);
                    int num2 = Integer.parseInt(f2.getName().split("_")[0]);
                    return Integer.compare(num1, num2);
                } catch (NumberFormatException e) {
                    return f1.getName().compareTo(f2.getName());
                }
            });
            configFiles.addAll(Arrays.asList(files));
        }

        return configFiles;
    }

    /**
     * Ensures the reports directory exists.
     */
    private static void ensureReportsDirectory() {
        Path reportsPath = Paths.get(REPORTS_DIRECTORY);
        if (!Files.exists(reportsPath)) {
            try {
                Files.createDirectories(reportsPath);
                System.out.println("Created reports directory: " + REPORTS_DIRECTORY);
            } catch (IOException e) {
                System.err.println("WARNING: Could not create reports directory: " + e.getMessage());
            }
        }
    }

    /**
     * Runs a single experiment with the given configuration file.
     */
    private static ExperimentResult runExperiment(File configFile, int experimentNumber) throws Exception {
        long startTime = System.currentTimeMillis();

        // Parse configuration
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse(configFile.getAbsolutePath());

        // Initialize random generator with seed from config
        RandomGenerator.initialize(config.getRandomSeed());
        System.out.println("Seed: " + config.getRandomSeed());

        // Create simulation context
        SimulationContext context = new SimulationContext();

        // Create strategies
        PowerAwareLoadBalancingHostPlacementStrategy hostStrategy =
            new PowerAwareLoadBalancingHostPlacementStrategy();
        BestFitVMPlacementStrategy vmStrategy =
            new BestFitVMPlacementStrategy();

        // Create GA configuration for task scheduling (Energy objective ONLY)
        EnergyObjective energyObjective = new EnergyObjective();
        GAConfiguration gaConfig = GAConfiguration.builder()
            .populationSize(GA_POPULATION_SIZE)
            .crossoverRate(GA_CROSSOVER_RATE)
            .mutationRate(GA_MUTATION_RATE)
            .eliteCount(GA_ELITE_COUNT)
            .objective(energyObjective)
            .terminationCondition(new GenerationCountTermination(GA_GENERATIONS))
            .verboseLogging(true)
            .build();

        GenerationalGATaskSchedulingStrategy taskStrategy =
            new GenerationalGATaskSchedulingStrategy(gaConfig);

        System.out.println("Strategy Configuration:");
        System.out.println("  Host Placement: " + hostStrategy.getStrategyName());
        System.out.println("  VM Placement: " + vmStrategy.getStrategyName());
        System.out.println("  Task Assignment: " + taskStrategy.getStrategyName());
        System.out.println("  GA Population: " + GA_POPULATION_SIZE);
        System.out.println("  GA Generations: " + GA_GENERATIONS);
        System.out.println("  GA Crossover Rate: " + GA_CROSSOVER_RATE);
        System.out.println("  GA Mutation Rate: " + GA_MUTATION_RATE);
        System.out.println("  GA Elite Count: " + GA_ELITE_COUNT);
        System.out.println("  Objective: Energy Consumption ONLY");
        System.out.println();

        // Execute simulation pipeline
        System.out.println("--- Step 1: Initialization ---");
        InitializationStep initStep = new InitializationStep(config);
        initStep.execute(context);
        System.out.println("  Datacenters: " + context.getTotalDatacenterCount());
        System.out.println("  Hosts: " + context.getTotalHostCount());
        System.out.println("  Users: " + context.getUsers().size());
        System.out.println("  VMs: " + context.getTotalVMCount());
        System.out.println("  Tasks: " + context.getTotalTaskCount());
        System.out.println();

        System.out.println("--- Step 2: Host Placement ---");
        HostPlacementStep hostPlacementStep = new HostPlacementStep(hostStrategy);
        hostPlacementStep.execute(context);
        System.out.println("  Hosts Placed: " + hostPlacementStep.getHostsPlaced());
        System.out.println("  Hosts Failed: " + hostPlacementStep.getHostsFailed());
        System.out.println();

        System.out.println("--- Step 3: User-Datacenter Mapping ---");
        UserDatacenterMappingStep userMappingStep = new UserDatacenterMappingStep();
        userMappingStep.execute(context);
        System.out.println("  Users Processed: " + userMappingStep.getUsersProcessed());
        System.out.println("  Valid Mappings: " + userMappingStep.getValidMappings());
        System.out.println("  Reassigned Users: " + userMappingStep.getReassignedUsers());
        System.out.println();

        System.out.println("--- Step 4: VM Placement ---");
        VMPlacementStep vmPlacementStep = new VMPlacementStep(vmStrategy);
        vmPlacementStep.execute(context);
        System.out.println("  VMs Placed: " + vmPlacementStep.getVmsPlaced());
        System.out.println("  VMs Failed: " + vmPlacementStep.getVmsFailed());
        System.out.println("  Active Hosts: " + vmPlacementStep.getActiveHostCount());
        System.out.println();

        // Set hosts on energy objective for accurate energy prediction
        energyObjective.setHosts(context.getHosts());

        System.out.println("--- Step 5: Task Assignment (GA Optimization) ---");
        TaskAssignmentStep taskAssignmentStep = new TaskAssignmentStep(taskStrategy);
        taskAssignmentStep.execute(context);
        System.out.println("  Tasks Assigned: " + taskAssignmentStep.getTasksAssigned());
        System.out.println("  Tasks Failed: " + taskAssignmentStep.getTasksFailed());
        System.out.println();

        System.out.println("--- Step 6: VM Execution ---");
        VMExecutionStep vmExecutionStep = new VMExecutionStep();
        vmExecutionStep.execute(context);
        System.out.println();

        System.out.println("--- Step 7: Task Execution Analysis ---");
        TaskExecutionStep taskExecutionStep = new TaskExecutionStep();
        taskExecutionStep.execute(context);
        System.out.println();

        System.out.println("--- Step 8: Energy Calculation ---");
        EnergyCalculationStep energyCalculationStep = new EnergyCalculationStep();
        energyCalculationStep.execute(context);
        System.out.println();

        System.out.println("--- Step 9: Metrics Collection ---");
        MetricsCollectionStep metricsCollectionStep = new MetricsCollectionStep();
        metricsCollectionStep.execute(context);
        System.out.println();

        System.out.println("--- Step 10: Reporting ---");
        ReportingStep reportingStep = new ReportingStep();
        reportingStep.setBaseOutputDirectory(REPORTS_DIRECTORY);
        reportingStep.setCustomPrefix("experiment" + experimentNumber);
        reportingStep.execute(context);
        System.out.println();

        // Also generate SimpleReporter output
        SimulationSummary summary = context.getSimulationSummary();
        SimpleReporter simpleReporter = new SimpleReporter();
        simpleReporter.setOutputDirectory(REPORTS_DIRECTORY);
        try {
            simpleReporter.generateAndSaveReport(context, summary);
        } catch (IOException e) {
            System.err.println("WARNING: SimpleReporter failed: " + e.getMessage());
        }

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;

        // Collect results
        int completedTasks = (int) context.getTasks().stream().filter(Task::isCompleted).count();
        int failedTasks = (int) context.getTasks().stream()
            .filter(t -> t.isAssigned() && !t.isCompleted()).count();
        long makespan = taskExecutionStep.getMakespan();
        double totalEnergyKWh = energyCalculationStep.getTotalITEnergyKWh();

        return new ExperimentResult(
            configFile.getName(),
            experimentNumber,
            config.getRandomSeed(),
            makespan,
            totalEnergyKWh,
            completedTasks,
            failedTasks,
            context.getTotalTaskCount(),
            durationMs
        );
    }

    /**
     * Prints the summary for a single experiment.
     */
    private static void printExperimentSummary(ExperimentResult result) {
        System.out.println("========================================================");
        System.out.println("  EXPERIMENT " + result.experimentNumber + " RESULTS");
        System.out.println("========================================================");

        if (result.error != null) {
            System.out.println("  Status: FAILED");
            System.out.println("  Error: " + result.error.getMessage());
        } else {
            System.out.println("  Status: SUCCESS");
            System.out.println("  Config: " + result.configFileName);
            System.out.println("  Seed: " + result.seed);
            System.out.println("  Makespan: " + result.makespan + " seconds");
            System.out.println("  Total Energy: " + String.format("%.6f", result.totalEnergyKWh) + " kWh");
            System.out.println("  Tasks Completed: " + result.tasksCompleted + "/" + result.totalTasks);
            System.out.println("  Tasks Failed: " + result.tasksFailed);
            System.out.println("  Wall-clock Duration: " + result.durationMs + " ms");
        }
        System.out.println("========================================================");
    }

    /**
     * Prints the final summary for all experiments.
     */
    private static void printFinalSummary(List<ExperimentResult> results) {
        System.out.println();
        System.out.println("========================================================");
        System.out.println("  FINAL SUMMARY - ALL EXPERIMENTS");
        System.out.println("========================================================");
        System.out.println();
        System.out.printf("%-4s %-35s %-10s %-15s %-15s %-10s%n",
            "#", "Config File", "Seed", "Makespan (s)", "Energy (kWh)", "Completed");
        System.out.println("---------------------------------------------------------------------------------------------");

        int successCount = 0;
        int failedCount = 0;
        long totalMakespan = 0;
        double totalEnergy = 0;
        int totalCompleted = 0;
        int totalFailed = 0;
        long totalDuration = 0;

        for (ExperimentResult result : results) {
            if (result.error != null) {
                System.out.printf("%-4d %-35s %-10s %-15s %-15s %-10s%n",
                    result.experimentNumber,
                    truncate(result.configFileName, 35),
                    "N/A",
                    "FAILED",
                    "FAILED",
                    "FAILED");
                failedCount++;
            } else {
                System.out.printf("%-4d %-35s %-10d %-15d %-15.6f %d/%d%n",
                    result.experimentNumber,
                    truncate(result.configFileName, 35),
                    result.seed,
                    result.makespan,
                    result.totalEnergyKWh,
                    result.tasksCompleted,
                    result.totalTasks);
                successCount++;
                totalMakespan += result.makespan;
                totalEnergy += result.totalEnergyKWh;
                totalCompleted += result.tasksCompleted;
                totalFailed += result.tasksFailed;
                totalDuration += result.durationMs;
            }
        }

        System.out.println("---------------------------------------------------------------------------------------------");
        System.out.println();
        System.out.println("Summary Statistics:");
        System.out.println("  Experiments Succeeded: " + successCount);
        System.out.println("  Experiments Failed: " + failedCount);

        if (successCount > 0) {
            System.out.println("  Average Makespan: " + (totalMakespan / successCount) + " seconds");
            System.out.println("  Average Energy: " + String.format("%.6f", totalEnergy / successCount) + " kWh");
            System.out.println("  Total Tasks Completed: " + totalCompleted);
            System.out.println("  Total Tasks Failed: " + totalFailed);
            System.out.println("  Total Wall-clock Time: " + totalDuration + " ms (" +
                String.format("%.2f", totalDuration / 1000.0 / 60.0) + " minutes)");
        }

        System.out.println();
        System.out.println("Reports saved to: " + REPORTS_DIRECTORY + "/");
        System.out.println("========================================================");
    }

    /**
     * Truncates a string to a maximum length, adding "..." if truncated.
     */
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Container for experiment results.
     */
    private static class ExperimentResult {
        final String configFileName;
        final int experimentNumber;
        final long seed;
        final long makespan;
        final double totalEnergyKWh;
        final int tasksCompleted;
        final int tasksFailed;
        final int totalTasks;
        final long durationMs;
        final Exception error;

        ExperimentResult(String configFileName, int experimentNumber, long seed,
                         long makespan, double totalEnergyKWh,
                         int tasksCompleted, int tasksFailed, int totalTasks,
                         long durationMs) {
            this.configFileName = configFileName;
            this.experimentNumber = experimentNumber;
            this.seed = seed;
            this.makespan = makespan;
            this.totalEnergyKWh = totalEnergyKWh;
            this.tasksCompleted = tasksCompleted;
            this.tasksFailed = tasksFailed;
            this.totalTasks = totalTasks;
            this.durationMs = durationMs;
            this.error = null;
        }

        ExperimentResult(String configFileName, int experimentNumber, Exception error) {
            this.configFileName = configFileName;
            this.experimentNumber = experimentNumber;
            this.seed = 0;
            this.makespan = 0;
            this.totalEnergyKWh = 0;
            this.tasksCompleted = 0;
            this.tasksFailed = 0;
            this.totalTasks = 0;
            this.durationMs = 0;
            this.error = error;
        }
    }
}
