package com.cloudsimulator.parameterTest;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
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

// Placement strategies
import com.cloudsimulator.PlacementStrategy.hostPlacement.PowerAwareLoadBalancingHostPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.VMPlacement.BestFitVMPlacementStrategy;

// Metaheuristics - SA
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SimulatedAnnealingTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SAConfiguration;

// Objectives
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.MakespanObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;

// Termination conditions
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.FitnessEvaluationsTermination;

// Cooling schedules
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.CoolingSchedule;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.GeometricCoolingSchedule;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.LinearCoolingSchedule;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.AdaptiveCoolingSchedule;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parameter Testing for Simulated Annealing (SA)
 *
 * Tests various SA parameters on the same dataset to find optimal configurations.
 *
 * Fixed parameters (for fair comparison - 40,000 total fitness evaluations):
 *   - Total Evaluations: 40,000
 *
 * Parameters tested:
 *   - Initial Temperature
 *   - Cooling Rate (for Geometric Cooling)
 *   - Cooling Schedule Type (Geometric, Linear, Adaptive)
 *
 * Usage:
 *   Compile: javac -d target/classes parameterTest/SAParameterTest.java
 *   Run:     java -cp target/classes parameterTest.SAParameterTest
 */
public class SAParameterTest {

    // =========================================================================
    // CONFIGURATION - Modify these to test different parameters
    // =========================================================================

    /** Configuration file to use for all tests */
    private static final String CONFIG_FILE = "configs/experiment1new/1_seed_123.cosc";

    /** Output directory for results */
    private static final String OUTPUT_DIR = "reports/sa_parameter_tests";

    /** Number of iterations per parameter configuration for statistical significance */
    private static final int ITERATIONS_PER_CONFIG = 5;

    /** Seed increment between iterations */
    private static final int SEED_INCREMENT = 100;

    /** Enable verbose logging during optimization */
    private static final boolean VERBOSE_LOGGING = false;

    /** Multi-objective mode (weighted sum) */
    private static final boolean MULTI_OBJECTIVE = true;
    private static final double WEIGHT_MAKESPAN = 0.7;
    private static final double WEIGHT_ENERGY = 0.3;

    // =========================================================================
    // FIXED PARAMETERS (40,000 total fitness evaluations)
    // =========================================================================

    /** Fixed total number of fitness evaluations */
    private static final int TOTAL_EVALUATIONS = 40000;

    // =========================================================================
    // PARAMETER RANGES TO TEST
    // =========================================================================

    /** Initial temperatures to test */
    private static final double[] INITIAL_TEMPERATURES = {100.0, 500.0, 1000.0, 5000.0, 10000.0};

    /** Cooling rates to test (for Geometric Cooling) */
    private static final double[] COOLING_RATES = {0.80, 0.85, 0.90, 0.95, 0.99};

    /** Cooling schedule types to test: 0=Geometric, 1=Linear, 2=Adaptive */
    private static final int[] COOLING_SCHEDULE_TYPES = {0, 1, 2};

    // Default values when testing other parameters
    private static final double DEFAULT_INITIAL_TEMPERATURE = 1000.0;
    private static final double DEFAULT_COOLING_RATE = 0.95;

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("  Simulated Annealing Parameter Testing");
        System.out.println("========================================================");
        System.out.println();

        // Validate config file exists
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            System.err.println("ERROR: Configuration file not found: " + CONFIG_FILE);
            System.err.println("Please ensure the config file exists before running tests.");
            System.exit(1);
        }

        // Create output directory
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            System.err.println("ERROR: Could not create output directory: " + e.getMessage());
            System.exit(1);
        }

        // Print fixed parameters
        System.out.println("Fixed Parameters:");
        System.out.println("  Total Evaluations: " + TOTAL_EVALUATIONS);
        System.out.println();

        List<ParameterTestResult> allResults = new ArrayList<>();

        // Test each parameter category
        System.out.println("Testing Initial Temperatures...");
        allResults.addAll(testInitialTemperatures());

        System.out.println("\nTesting Cooling Rates (Geometric)...");
        allResults.addAll(testCoolingRates());

        System.out.println("\nTesting Cooling Schedule Types...");
        allResults.addAll(testCoolingScheduleTypes());

        // Save results to CSV
        saveResultsToCSV(allResults);

        // Print summary
        printSummary(allResults);

        System.out.println("\n========================================================");
        System.out.println("  SA Parameter Testing Complete");
        System.out.println("  Results saved to: " + OUTPUT_DIR);
        System.out.println("========================================================");
    }

    // =========================================================================
    // PARAMETER TEST METHODS
    // =========================================================================

    private static List<ParameterTestResult> testInitialTemperatures() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (double initTemp : INITIAL_TEMPERATURES) {
            System.out.println("  Initial Temperature: " + initTemp);
            SAConfiguration config = createConfig(initTemp, new GeometricCoolingSchedule(DEFAULT_COOLING_RATE));
            ParameterTestResult result = runParameterTest("InitialTemperature", String.valueOf(initTemp), config);
            results.add(result);
        }
        return results;
    }

    private static List<ParameterTestResult> testCoolingRates() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (double coolingRate : COOLING_RATES) {
            System.out.println("  Cooling Rate: " + coolingRate);
            SAConfiguration config = createConfig(DEFAULT_INITIAL_TEMPERATURE, new GeometricCoolingSchedule(coolingRate));
            ParameterTestResult result = runParameterTest("CoolingRate", String.valueOf(coolingRate), config);
            results.add(result);
        }
        return results;
    }

    private static List<ParameterTestResult> testCoolingScheduleTypes() {
        List<ParameterTestResult> results = new ArrayList<>();

        for (int scheduleType : COOLING_SCHEDULE_TYPES) {
            String scheduleName = getCoolingScheduleName(scheduleType);
            CoolingSchedule schedule = createCoolingSchedule(scheduleType);
            System.out.println("  Cooling Schedule: " + scheduleName);

            SAConfiguration config = createConfig(DEFAULT_INITIAL_TEMPERATURE, schedule);
            ParameterTestResult result = runParameterTest("CoolingSchedule", scheduleName, config);
            results.add(result);
        }
        return results;
    }

    private static String getCoolingScheduleName(int type) {
        switch (type) {
            case 0: return "Geometric";
            case 1: return "Linear";
            case 2: return "Adaptive";
            default: return "Unknown";
        }
    }

    private static CoolingSchedule createCoolingSchedule(int type) {
        switch (type) {
            case 0: return new GeometricCoolingSchedule(DEFAULT_COOLING_RATE);
            case 1: return new LinearCoolingSchedule(DEFAULT_INITIAL_TEMPERATURE, 1.0);
            case 2: return new AdaptiveCoolingSchedule();
            default: return new GeometricCoolingSchedule(DEFAULT_COOLING_RATE);
        }
    }

    // =========================================================================
    // SA CONFIGURATION BUILDER
    // =========================================================================

    private static SAConfiguration createConfig(double initialTemperature, CoolingSchedule coolingSchedule) {

        SAConfiguration.Builder builder = SAConfiguration.builder()
            .initialTemperature(initialTemperature)
            .coolingSchedule(coolingSchedule)
            .terminationCondition(new FitnessEvaluationsTermination(TOTAL_EVALUATIONS))  // Fixed at 40,000
            .verboseLogging(VERBOSE_LOGGING);

        if (MULTI_OBJECTIVE) {
            builder.addWeightedObjective(new MakespanObjective(), WEIGHT_MAKESPAN);
            builder.addWeightedObjective(new EnergyObjective(), WEIGHT_ENERGY);
        } else {
            builder.objective(new MakespanObjective());
        }

        return builder.build();
    }

    // =========================================================================
    // TEST EXECUTION
    // =========================================================================

    private static ParameterTestResult runParameterTest(String parameterName, String parameterValue,
            SAConfiguration saConfig) {

        ParameterTestResult result = new ParameterTestResult(parameterName, parameterValue);

        try {
            // Parse base configuration to get seed
            FileConfigParser parser = new FileConfigParser();
            ExperimentConfiguration baseConfig = parser.parse(CONFIG_FILE);
            long baseSeed = baseConfig.getRandomSeed();

            for (int iter = 1; iter <= ITERATIONS_PER_CONFIG; iter++) {
                long seed = baseSeed + (iter - 1) * SEED_INCREMENT;

                try {
                    SingleRunResult runResult = runSingleIteration(saConfig, seed);
                    result.addRun(runResult);
                } catch (Exception e) {
                    System.err.println("    Iteration " + iter + " failed: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("  Parameter test failed: " + e.getMessage());
        }

        return result;
    }

    private static SingleRunResult runSingleIteration(SAConfiguration saConfig, long seed) throws Exception {
        long startTime = System.currentTimeMillis();

        // Parse configuration
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse(CONFIG_FILE);

        // Initialize random generator
        RandomGenerator.initialize(seed);

        // Create simulation context
        SimulationContext context = new SimulationContext();

        // Create placement strategies
        PowerAwareLoadBalancingHostPlacementStrategy hostStrategy =
            new PowerAwareLoadBalancingHostPlacementStrategy();
        BestFitVMPlacementStrategy vmStrategy = new BestFitVMPlacementStrategy();

        // Execute simulation pipeline (Steps 1-4)
        InitializationStep initStep = new InitializationStep(config);
        initStep.execute(context);

        HostPlacementStep hostPlacementStep = new HostPlacementStep(hostStrategy);
        hostPlacementStep.execute(context);

        UserDatacenterMappingStep userMappingStep = new UserDatacenterMappingStep();
        userMappingStep.execute(context);

        VMPlacementStep vmPlacementStep = new VMPlacementStep(vmStrategy);
        vmPlacementStep.execute(context);

        // Set hosts for energy objective if multi-objective
        if (MULTI_OBJECTIVE) {
            for (var obj : saConfig.getWeightedObjectives().keySet()) {
                if (obj instanceof EnergyObjective) {
                    ((EnergyObjective) obj).setHosts(context.getHosts());
                }
            }
        }

        // Create SA strategy
        SimulatedAnnealingTaskSchedulingStrategy saStrategy =
            new SimulatedAnnealingTaskSchedulingStrategy(saConfig);

        // Step 5: Task Assignment
        TaskAssignmentStep taskAssignmentStep = new TaskAssignmentStep(saStrategy);
        taskAssignmentStep.execute(context);

        // Steps 6-8: Execution and Energy
        VMExecutionStep vmExecutionStep = new VMExecutionStep();
        vmExecutionStep.execute(context);

        TaskExecutionStep taskExecutionStep = new TaskExecutionStep();
        taskExecutionStep.execute(context);

        EnergyCalculationStep energyCalculationStep = new EnergyCalculationStep();
        energyCalculationStep.execute(context);

        long endTime = System.currentTimeMillis();

        // Extract results
        double makespan = taskExecutionStep.getMakespan();
        double energy = energyCalculationStep.getTotalITEnergyKWh();

        return new SingleRunResult(seed, makespan, energy, endTime - startTime);
    }

    // =========================================================================
    // RESULTS HANDLING
    // =========================================================================

    private static void saveResultsToCSV(List<ParameterTestResult> results) {
        Path filePath = Paths.get(OUTPUT_DIR, "sa_parameter_results.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
            writer.println("Parameter,Value,AvgMakespan,StdMakespan,MinMakespan,MaxMakespan," +
                          "AvgEnergy,StdEnergy,MinEnergy,MaxEnergy,AvgTimeMs,Iterations");

            for (ParameterTestResult result : results) {
                if (result.runs.isEmpty()) continue;

                double[] makespans = result.runs.stream().mapToDouble(r -> r.makespan).toArray();
                double[] energies = result.runs.stream().mapToDouble(r -> r.energy).toArray();
                double[] times = result.runs.stream().mapToDouble(r -> r.executionTimeMs).toArray();

                writer.printf("%s,%s,%.4f,%.4f,%.4f,%.4f,%.9f,%.9f,%.9f,%.9f,%.2f,%d%n",
                    result.parameterName,
                    result.parameterValue,
                    mean(makespans), stdDev(makespans), min(makespans), max(makespans),
                    mean(energies), stdDev(energies), min(energies), max(energies),
                    mean(times),
                    result.runs.size());
            }

            System.out.println("\nResults saved to: " + filePath);
        } catch (IOException e) {
            System.err.println("ERROR: Could not write results: " + e.getMessage());
        }

        // Also save detailed per-run results
        Path detailedPath = Paths.get(OUTPUT_DIR, "sa_parameter_detailed.csv");
        try (PrintWriter writer = new PrintWriter(new FileWriter(detailedPath.toFile()))) {
            writer.println("Parameter,Value,Seed,Makespan,Energy,TimeMs");

            for (ParameterTestResult result : results) {
                for (SingleRunResult run : result.runs) {
                    writer.printf("%s,%s,%d,%.4f,%.9f,%d%n",
                        result.parameterName,
                        result.parameterValue,
                        run.seed,
                        run.makespan,
                        run.energy,
                        run.executionTimeMs);
                }
            }
        } catch (IOException e) {
            System.err.println("ERROR: Could not write detailed results: " + e.getMessage());
        }
    }

    private static void printSummary(List<ParameterTestResult> results) {
        System.out.println("\n========================================================");
        System.out.println("  SUMMARY - Best Configuration per Parameter");
        System.out.println("========================================================\n");

        String currentParam = "";
        ParameterTestResult bestForParam = null;
        double bestMakespan = Double.MAX_VALUE;

        for (ParameterTestResult result : results) {
            if (!result.parameterName.equals(currentParam)) {
                if (bestForParam != null) {
                    double[] makespans = bestForParam.runs.stream().mapToDouble(r -> r.makespan).toArray();
                    System.out.printf("  %s: Best = %s (Avg Makespan: %.2f)%n",
                        currentParam, bestForParam.parameterValue, mean(makespans));
                }
                currentParam = result.parameterName;
                bestForParam = null;
                bestMakespan = Double.MAX_VALUE;
            }

            if (!result.runs.isEmpty()) {
                double avgMakespan = result.runs.stream().mapToDouble(r -> r.makespan).average().orElse(Double.MAX_VALUE);
                if (avgMakespan < bestMakespan) {
                    bestMakespan = avgMakespan;
                    bestForParam = result;
                }
            }
        }

        if (bestForParam != null) {
            double[] makespans = bestForParam.runs.stream().mapToDouble(r -> r.makespan).toArray();
            System.out.printf("  %s: Best = %s (Avg Makespan: %.2f)%n",
                currentParam, bestForParam.parameterValue, mean(makespans));
        }
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    private static double mean(double[] values) {
        return Arrays.stream(values).average().orElse(0);
    }

    private static double stdDev(double[] values) {
        if (values.length < 2) return 0;
        double mean = mean(values);
        double variance = Arrays.stream(values).map(v -> Math.pow(v - mean, 2)).sum() / (values.length - 1);
        return Math.sqrt(variance);
    }

    private static double min(double[] values) {
        return Arrays.stream(values).min().orElse(0);
    }

    private static double max(double[] values) {
        return Arrays.stream(values).max().orElse(0);
    }

    // =========================================================================
    // DATA STRUCTURES
    // =========================================================================

    private static class SingleRunResult {
        final long seed;
        final double makespan;
        final double energy;
        final long executionTimeMs;

        SingleRunResult(long seed, double makespan, double energy, long executionTimeMs) {
            this.seed = seed;
            this.makespan = makespan;
            this.energy = energy;
            this.executionTimeMs = executionTimeMs;
        }
    }

    private static class ParameterTestResult {
        final String parameterName;
        final String parameterValue;
        final List<SingleRunResult> runs = new ArrayList<>();

        ParameterTestResult(String parameterName, String parameterValue) {
            this.parameterName = parameterName;
            this.parameterValue = parameterValue;
        }

        void addRun(SingleRunResult run) {
            runs.add(run);
        }
    }
}
