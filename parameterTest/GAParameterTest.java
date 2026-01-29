package parameterTest;

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
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;

// Metaheuristics - GA
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GenerationalGATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

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
 * Parameter Testing for Standard Genetic Algorithm (GA)
 *
 * Tests various GA parameters on the same dataset to find optimal configurations.
 * Parameters tested:
 *   - Population Size
 *   - Crossover Rate
 *   - Mutation Rate
 *   - Elite Count / Elite Percentage
 *   - Tournament Size
 *   - Number of Generations
 *
 * Usage:
 *   Compile: javac -d target/classes parameterTest/GAParameterTest.java
 *   Run:     java -cp target/classes parameterTest.GAParameterTest
 */
public class GAParameterTest {

    // =========================================================================
    // CONFIGURATION - Modify these to test different parameters
    // =========================================================================

    /** Configuration file to use for all tests */
    private static final String CONFIG_FILE = "configs/experiment1new/1_seed_123.cosc";

    /** Output directory for results */
    private static final String OUTPUT_DIR = "reports/ga_parameter_tests";

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
    // PARAMETER RANGES TO TEST
    // =========================================================================

    /** Population sizes to test */
    private static final int[] POPULATION_SIZES = {50, 100, 200, 300};

    /** Crossover rates to test */
    private static final double[] CROSSOVER_RATES = {0.6, 0.7, 0.8, 0.9, 0.95};

    /** Mutation rates to test */
    private static final double[] MUTATION_RATES = {0.01, 0.05, 0.1, 0.15, 0.2};

    /** Elite percentages to test (as fraction of population) */
    private static final double[] ELITE_PERCENTAGES = {0.05, 0.1, 0.15, 0.2};

    /** Tournament sizes to test */
    private static final int[] TOURNAMENT_SIZES = {2, 3, 5, 7};

    /** Number of generations to test */
    private static final int[] GENERATION_COUNTS = {50, 100, 200, 300};

    // Default values when testing other parameters
    private static final int DEFAULT_POPULATION_SIZE = 100;
    private static final double DEFAULT_CROSSOVER_RATE = 0.9;
    private static final double DEFAULT_MUTATION_RATE = 0.1;
    private static final double DEFAULT_ELITE_PERCENTAGE = 0.1;
    private static final int DEFAULT_TOURNAMENT_SIZE = 3;
    private static final int DEFAULT_GENERATIONS = 100;

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("  GA Parameter Testing");
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

        List<ParameterTestResult> allResults = new ArrayList<>();

        // Test each parameter category
        System.out.println("Testing Population Sizes...");
        allResults.addAll(testPopulationSizes());

        System.out.println("\nTesting Crossover Rates...");
        allResults.addAll(testCrossoverRates());

        System.out.println("\nTesting Mutation Rates...");
        allResults.addAll(testMutationRates());

        System.out.println("\nTesting Elite Percentages...");
        allResults.addAll(testElitePercentages());

        System.out.println("\nTesting Tournament Sizes...");
        allResults.addAll(testTournamentSizes());

        System.out.println("\nTesting Generation Counts...");
        allResults.addAll(testGenerationCounts());

        // Save results to CSV
        saveResultsToCSV(allResults);

        // Print summary
        printSummary(allResults);

        System.out.println("\n========================================================");
        System.out.println("  GA Parameter Testing Complete");
        System.out.println("  Results saved to: " + OUTPUT_DIR);
        System.out.println("========================================================");
    }

    // =========================================================================
    // PARAMETER TEST METHODS
    // =========================================================================

    private static List<ParameterTestResult> testPopulationSizes() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (int popSize : POPULATION_SIZES) {
            System.out.println("  Population Size: " + popSize);
            GAConfiguration config = createConfig(
                popSize, DEFAULT_CROSSOVER_RATE, DEFAULT_MUTATION_RATE,
                DEFAULT_ELITE_PERCENTAGE, DEFAULT_TOURNAMENT_SIZE, DEFAULT_GENERATIONS);
            ParameterTestResult result = runParameterTest("PopulationSize", String.valueOf(popSize), config);
            results.add(result);
        }
        return results;
    }

    private static List<ParameterTestResult> testCrossoverRates() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (double crossoverRate : CROSSOVER_RATES) {
            System.out.println("  Crossover Rate: " + crossoverRate);
            GAConfiguration config = createConfig(
                DEFAULT_POPULATION_SIZE, crossoverRate, DEFAULT_MUTATION_RATE,
                DEFAULT_ELITE_PERCENTAGE, DEFAULT_TOURNAMENT_SIZE, DEFAULT_GENERATIONS);
            ParameterTestResult result = runParameterTest("CrossoverRate", String.valueOf(crossoverRate), config);
            results.add(result);
        }
        return results;
    }

    private static List<ParameterTestResult> testMutationRates() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (double mutationRate : MUTATION_RATES) {
            System.out.println("  Mutation Rate: " + mutationRate);
            GAConfiguration config = createConfig(
                DEFAULT_POPULATION_SIZE, DEFAULT_CROSSOVER_RATE, mutationRate,
                DEFAULT_ELITE_PERCENTAGE, DEFAULT_TOURNAMENT_SIZE, DEFAULT_GENERATIONS);
            ParameterTestResult result = runParameterTest("MutationRate", String.valueOf(mutationRate), config);
            results.add(result);
        }
        return results;
    }

    private static List<ParameterTestResult> testElitePercentages() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (double elitePct : ELITE_PERCENTAGES) {
            System.out.println("  Elite Percentage: " + elitePct);
            GAConfiguration config = createConfig(
                DEFAULT_POPULATION_SIZE, DEFAULT_CROSSOVER_RATE, DEFAULT_MUTATION_RATE,
                elitePct, DEFAULT_TOURNAMENT_SIZE, DEFAULT_GENERATIONS);
            ParameterTestResult result = runParameterTest("ElitePercentage", String.valueOf(elitePct), config);
            results.add(result);
        }
        return results;
    }

    private static List<ParameterTestResult> testTournamentSizes() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (int tournamentSize : TOURNAMENT_SIZES) {
            System.out.println("  Tournament Size: " + tournamentSize);
            GAConfiguration config = createConfig(
                DEFAULT_POPULATION_SIZE, DEFAULT_CROSSOVER_RATE, DEFAULT_MUTATION_RATE,
                DEFAULT_ELITE_PERCENTAGE, tournamentSize, DEFAULT_GENERATIONS);
            ParameterTestResult result = runParameterTest("TournamentSize", String.valueOf(tournamentSize), config);
            results.add(result);
        }
        return results;
    }

    private static List<ParameterTestResult> testGenerationCounts() {
        List<ParameterTestResult> results = new ArrayList<>();
        for (int generations : GENERATION_COUNTS) {
            System.out.println("  Generations: " + generations);
            GAConfiguration config = createConfig(
                DEFAULT_POPULATION_SIZE, DEFAULT_CROSSOVER_RATE, DEFAULT_MUTATION_RATE,
                DEFAULT_ELITE_PERCENTAGE, DEFAULT_TOURNAMENT_SIZE, generations);
            ParameterTestResult result = runParameterTest("Generations", String.valueOf(generations), config);
            results.add(result);
        }
        return results;
    }

    // =========================================================================
    // GA CONFIGURATION BUILDER
    // =========================================================================

    private static GAConfiguration createConfig(int populationSize, double crossoverRate,
            double mutationRate, double elitePercentage, int tournamentSize, int generations) {

        GAConfiguration.Builder builder = GAConfiguration.builder()
            .populationSize(populationSize)
            .crossoverRate(crossoverRate)
            .mutationRate(mutationRate)
            .elitePercentage(elitePercentage)
            .tournamentSize(tournamentSize)
            .terminationCondition(new GenerationCountTermination(generations))
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
            GAConfiguration gaConfig) {

        ParameterTestResult result = new ParameterTestResult(parameterName, parameterValue);

        try {
            // Parse base configuration to get seed
            FileConfigParser parser = new FileConfigParser();
            ExperimentConfiguration baseConfig = parser.parse(CONFIG_FILE);
            long baseSeed = baseConfig.getRandomSeed();

            for (int iter = 1; iter <= ITERATIONS_PER_CONFIG; iter++) {
                long seed = baseSeed + (iter - 1) * SEED_INCREMENT;

                try {
                    SingleRunResult runResult = runSingleIteration(gaConfig, seed);
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

    private static SingleRunResult runSingleIteration(GAConfiguration gaConfig, long seed) throws Exception {
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
            for (var obj : gaConfig.getWeightedObjectives().keySet()) {
                if (obj instanceof EnergyObjective) {
                    ((EnergyObjective) obj).setHosts(context.getHosts());
                }
            }
        }

        // Create GA strategy
        GenerationalGATaskSchedulingStrategy gaStrategy =
            new GenerationalGATaskSchedulingStrategy(gaConfig);

        // Step 5: Task Assignment
        TaskAssignmentStep taskAssignmentStep = new TaskAssignmentStep(gaStrategy);
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
        Path filePath = Paths.get(OUTPUT_DIR, "ga_parameter_results.csv");

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
        Path detailedPath = Paths.get(OUTPUT_DIR, "ga_parameter_detailed.csv");
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

        // Group by parameter name and find best
        String currentParam = "";
        ParameterTestResult bestForParam = null;
        double bestMakespan = Double.MAX_VALUE;

        for (ParameterTestResult result : results) {
            if (!result.parameterName.equals(currentParam)) {
                // Print previous best
                if (bestForParam != null) {
                    double[] makespans = bestForParam.runs.stream().mapToDouble(r -> r.makespan).toArray();
                    System.out.printf("  %s: Best = %s (Avg Makespan: %.2f)%n",
                        currentParam, bestForParam.parameterValue, mean(makespans));
                }
                // Reset for new parameter
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

        // Print last parameter's best
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
