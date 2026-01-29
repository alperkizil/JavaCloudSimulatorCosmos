package com.cloudsimulator.parameterTest;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
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

// Metaheuristics - MOEA AMOSA
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_AMOSATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;

// Objectives
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.MakespanObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;

// Termination conditions
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;

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
 * Parameter Testing for MOEA Framework AMOSA
 * (Archived Multi-Objective Simulated Annealing)
 *
 * Tests various AMOSA parameters on the same dataset to find optimal configurations.
 *
 * Fixed parameters (for fair comparison - 40,000 total fitness evaluations):
 *   - Population Size (Archive Limit): 200
 *   - Generations: 200
 *   - Total Evaluations: 200 x 200 = 40,000
 *
 * Parameters tested:
 *   - Initial Temperature
 *   - Cooling Rate (Alpha)
 *   - Mutation Rate
 *
 * AMOSA is a simulated annealing-based multi-objective optimization algorithm
 * that uses an archive to store non-dominated solutions. Key features:
 *   - Single-solution based search
 *   - Temperature-controlled acceptance of worse solutions
 *   - Archive for maintaining Pareto front
 *   - Clustering-based archive truncation
 *
 * Usage:
 *   Compile: javac -d target/classes parameterTest/AMOSA_ParameterTest.java
 *   Run:     java -cp target/classes parameterTest.AMOSA_ParameterTest
 *
 * Note: Requires MOEA Framework library on classpath.
 */
public class AMOSA_ParameterTest {

    // =========================================================================
    // CONFIGURATION - Modify these to test different parameters
    // =========================================================================

    /** Configuration file to use for all tests */
    private static final String CONFIG_FILE = "configs/experiment1new/1_seed_123.cosc";

    /** Output directory for results */
    private static final String OUTPUT_DIR = "reports/amosa_parameter_tests";

    /** Number of iterations per parameter configuration for statistical significance */
    private static final int ITERATIONS_PER_CONFIG = 5;

    /** Seed increment between iterations */
    private static final int SEED_INCREMENT = 100;

    /** Enable verbose logging during optimization */
    private static final boolean VERBOSE_LOGGING = false;

    /** Solution selection weights */
    private static final double WEIGHT_MAKESPAN = 0.7;
    private static final double WEIGHT_ENERGY = 0.3;

    // =========================================================================
    // FIXED PARAMETERS (40,000 total fitness evaluations)
    // =========================================================================

    /** Fixed population size (archive limit) */
    private static final int POPULATION_SIZE = 200;

    /** Fixed number of generations */
    private static final int GENERATIONS = 200;

    /** Total fitness evaluations = POPULATION_SIZE x GENERATIONS = 40,000 */
    private static final int TOTAL_EVALUATIONS = POPULATION_SIZE * GENERATIONS;

    // =========================================================================
    // PARAMETER RANGES TO TEST
    // =========================================================================

    /** Initial temperatures to test */
    private static final double[] INITIAL_TEMPERATURES = {100.0, 500.0, 1000.0, 5000.0};

    /** Cooling rates (alpha) to test */
    private static final double[] COOLING_RATES = {0.80, 0.85, 0.90, 0.95, 0.99};

    /** Mutation rates to test (affects neighbor generation) */
    private static final double[] MUTATION_RATES = {0.05, 0.1, 0.15, 0.2, 0.3};

    // Default values when testing other parameters
    private static final double DEFAULT_INITIAL_TEMPERATURE = 1000.0;
    private static final double DEFAULT_COOLING_RATE = 0.95;
    private static final double DEFAULT_MUTATION_RATE = 0.1;

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("  MOEA AMOSA Parameter Testing");
        System.out.println("========================================================");
        System.out.println();
        System.out.println("Note: AMOSA is a simulated annealing-based multi-objective");
        System.out.println("optimizer with archive for Pareto front maintenance.");
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
        System.out.println("  Population Size (Archive Limit): " + POPULATION_SIZE);
        System.out.println("  Generations: " + GENERATIONS);
        System.out.println("  Total Evaluations: " + TOTAL_EVALUATIONS);
        System.out.println();

        List<ParameterTestResult> allResults = new ArrayList<>();

        // Test each parameter category
        System.out.println("Testing Initial Temperatures...");
        allResults.addAll(testInitialTemperatures());

        System.out.println("\nTesting Cooling Rates...");
        allResults.addAll(testCoolingRates());

        System.out.println("\nTesting Mutation Rates...");
        allResults.addAll(testMutationRates());

        // Save results to CSV
        saveResultsToCSV(allResults);

        // Print summary
        printSummary(allResults);

        System.out.println("\n========================================================");
        System.out.println("  MOEA AMOSA Parameter Testing Complete");
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
            NSGA2Configuration config = createConfig(DEFAULT_MUTATION_RATE);
            ParameterTestResult result = runParameterTest("InitialTemperature", String.valueOf(initTemp), config,
                initTemp, DEFAULT_COOLING_RATE);
            results.add(result);
        }
        return results;
    }

    private static List<ParameterTestResult> testCoolingRates() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (double coolingRate : COOLING_RATES) {
            System.out.println("  Cooling Rate: " + coolingRate);
            NSGA2Configuration config = createConfig(DEFAULT_MUTATION_RATE);
            ParameterTestResult result = runParameterTest("CoolingRate", String.valueOf(coolingRate), config,
                DEFAULT_INITIAL_TEMPERATURE, coolingRate);
            results.add(result);
        }
        return results;
    }

    private static List<ParameterTestResult> testMutationRates() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (double mutationRate : MUTATION_RATES) {
            System.out.println("  Mutation Rate: " + mutationRate);
            NSGA2Configuration config = createConfig(mutationRate);
            ParameterTestResult result = runParameterTest("MutationRate", String.valueOf(mutationRate), config,
                DEFAULT_INITIAL_TEMPERATURE, DEFAULT_COOLING_RATE);
            results.add(result);
        }
        return results;
    }

    // =========================================================================
    // AMOSA CONFIGURATION BUILDER
    // =========================================================================

    private static NSGA2Configuration createConfig(double mutationRate) {
        return NSGA2Configuration.builder()
            .populationSize(POPULATION_SIZE)  // Fixed at 200 (archive soft limit)
            .crossoverRate(0.9)               // Not used in AMOSA but kept for compatibility
            .mutationRate(mutationRate)       // Affects neighbor generation
            .addObjective(new MakespanObjective())
            .addObjective(new EnergyObjective())
            .terminationCondition(new GenerationCountTermination(GENERATIONS))  // Fixed at 200
            .verboseLogging(VERBOSE_LOGGING)
            .build();
    }

    // =========================================================================
    // TEST EXECUTION
    // =========================================================================

    private static ParameterTestResult runParameterTest(String parameterName, String parameterValue,
            NSGA2Configuration amosaConfig, double initialTemperature, double coolingRate) {

        ParameterTestResult result = new ParameterTestResult(parameterName, parameterValue);

        try {
            // Parse base configuration to get seed
            FileConfigParser parser = new FileConfigParser();
            ExperimentConfiguration baseConfig = parser.parse(CONFIG_FILE);
            long baseSeed = baseConfig.getRandomSeed();

            for (int iter = 1; iter <= ITERATIONS_PER_CONFIG; iter++) {
                long seed = baseSeed + (iter - 1) * SEED_INCREMENT;

                try {
                    SingleRunResult runResult = runSingleIteration(amosaConfig, seed, initialTemperature, coolingRate);
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

    private static SingleRunResult runSingleIteration(NSGA2Configuration amosaConfig, long seed,
            double initialTemperature, double coolingRate) throws Exception {
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

        // Set hosts for energy objective
        for (var obj : amosaConfig.getObjectives()) {
            if (obj instanceof EnergyObjective) {
                ((EnergyObjective) obj).setHosts(context.getHosts());
            }
        }

        // Create MOEA AMOSA strategy
        MOEA_AMOSATaskSchedulingStrategy amosaStrategy =
            new MOEA_AMOSATaskSchedulingStrategy(amosaConfig);
        amosaStrategy.setSelectionWeights(new double[]{WEIGHT_MAKESPAN, WEIGHT_ENERGY});
        amosaStrategy.setSelectionMethod(MOEA_AMOSATaskSchedulingStrategy.SolutionSelectionMethod.WEIGHTED_SUM);

        // Set AMOSA-specific parameters
        amosaStrategy.setInitialTemperature(initialTemperature);
        amosaStrategy.setAlpha(coolingRate);

        // Step 5: Task Assignment
        TaskAssignmentStep taskAssignmentStep = new TaskAssignmentStep(amosaStrategy);
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

        // Get Pareto front size (archive size)
        ParetoFront paretoFront = amosaStrategy.getLastParetoFront();
        int paretoSize = paretoFront != null ? paretoFront.size() : 0;

        return new SingleRunResult(seed, makespan, energy, endTime - startTime, paretoSize);
    }

    // =========================================================================
    // RESULTS HANDLING
    // =========================================================================

    private static void saveResultsToCSV(List<ParameterTestResult> results) {
        Path filePath = Paths.get(OUTPUT_DIR, "amosa_parameter_results.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
            writer.println("Parameter,Value,AvgMakespan,StdMakespan,MinMakespan,MaxMakespan," +
                          "AvgEnergy,StdEnergy,MinEnergy,MaxEnergy,AvgArchiveSize,AvgTimeMs,Iterations");

            for (ParameterTestResult result : results) {
                if (result.runs.isEmpty()) continue;

                double[] makespans = result.runs.stream().mapToDouble(r -> r.makespan).toArray();
                double[] energies = result.runs.stream().mapToDouble(r -> r.energy).toArray();
                double[] times = result.runs.stream().mapToDouble(r -> r.executionTimeMs).toArray();
                double[] paretoSizes = result.runs.stream().mapToDouble(r -> r.paretoSize).toArray();

                writer.printf("%s,%s,%.4f,%.4f,%.4f,%.4f,%.9f,%.9f,%.9f,%.9f,%.2f,%.2f,%d%n",
                    result.parameterName,
                    result.parameterValue,
                    mean(makespans), stdDev(makespans), min(makespans), max(makespans),
                    mean(energies), stdDev(energies), min(energies), max(energies),
                    mean(paretoSizes),
                    mean(times),
                    result.runs.size());
            }

            System.out.println("\nResults saved to: " + filePath);
        } catch (IOException e) {
            System.err.println("ERROR: Could not write results: " + e.getMessage());
        }

        // Also save detailed per-run results
        Path detailedPath = Paths.get(OUTPUT_DIR, "amosa_parameter_detailed.csv");
        try (PrintWriter writer = new PrintWriter(new FileWriter(detailedPath.toFile()))) {
            writer.println("Parameter,Value,Seed,Makespan,Energy,ArchiveSize,TimeMs");

            for (ParameterTestResult result : results) {
                for (SingleRunResult run : result.runs) {
                    writer.printf("%s,%s,%d,%.4f,%.9f,%d,%d%n",
                        result.parameterName,
                        result.parameterValue,
                        run.seed,
                        run.makespan,
                        run.energy,
                        run.paretoSize,
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
                    double[] paretoSizes = bestForParam.runs.stream().mapToDouble(r -> r.paretoSize).toArray();
                    System.out.printf("  %s: Best = %s (Avg Makespan: %.2f, Avg Archive Size: %.1f)%n",
                        currentParam, bestForParam.parameterValue, mean(makespans), mean(paretoSizes));
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
            double[] paretoSizes = bestForParam.runs.stream().mapToDouble(r -> r.paretoSize).toArray();
            System.out.printf("  %s: Best = %s (Avg Makespan: %.2f, Avg Archive Size: %.1f)%n",
                currentParam, bestForParam.parameterValue, mean(makespans), mean(paretoSizes));
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
        final int paretoSize;

        SingleRunResult(long seed, double makespan, double energy, long executionTimeMs, int paretoSize) {
            this.seed = seed;
            this.makespan = makespan;
            this.energy = energy;
            this.executionTimeMs = executionTimeMs;
            this.paretoSize = paretoSize;
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
