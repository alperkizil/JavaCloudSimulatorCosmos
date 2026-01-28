package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
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
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.FirstAvailableTaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.ShortestQueueTaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.WorkloadAwareTaskAssignmentStrategy;

// Metaheuristics
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GenerationalGATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SimulatedAnnealingTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.LocalSearchTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.LocalSearchConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.NeighborhoodGenerator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.neighborselection.FirstImprovementStrategy;

// MOEA Framework strategies
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_NSGA2TaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_SPEA2TaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_MOEADTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_OMOPSOTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_AMOSATaskSchedulingStrategy;

// Genetic operators (for MOEA custom variation)
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.CrossoverOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;

// Objectives
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.MakespanObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;

// Termination conditions
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.FitnessEvaluationsTermination;

// Cooling schedules
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.GeometricCoolingSchedule;

// Performance metrics
import com.cloudsimulator.multiobjectivePerformance.PerfMet.PerformanceMetrics;
import com.cloudsimulator.multiobjectivePerformance.PerfMet.Dominance;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Flexible Experiment Main - Multi-algorithm cloud simulation runner.
 *
 * This file is designed for comparative experiments across multiple algorithms.
 * All configuration is done through simple constants at the top of the file.
 *
 * QUICK START:
 * 1. Set ALGORITHMS array to select which algorithms to run
 * 2. Set MULTI_OBJECTIVE_MODE to true for Pareto analysis, false for single-objective comparison
 * 3. Set EXTREME_POINTS_MODE to true if you want GA/SA/LS to find extreme Pareto points
 * 4. Set ITERATION_COUNT for statistical significance
 * 5. Adjust weights if using weighted-sum mode (ignored when EXTREME_POINTS_MODE = true)
 * 6. Run!
 *
 * ============================================================================
 * STRATEGY GUIDE:
 * ============================================================================
 *
 * HEURISTICS (fast, simple):
 *   1 = First Available    - Assigns to first compatible VM (baseline)
 *   2 = Shortest Queue     - Assigns to VM with fewest tasks
 *   3 = Workload Aware     - Considers completion time estimation
 *
 * GENETIC ALGORITHM (population-based):
 *   4 = GA                 - Generational GA with elitism
 *
 * SIMULATED ANNEALING (single-solution, memory-efficient):
 *   5 = SA                 - Classic SA with evaluation-count termination
 *
 * LOCAL SEARCH (fast local optimization):
 *   6 = Local Search       - First improvement hill climbing
 *
 * MOEA FRAMEWORK (true multi-objective, returns Pareto front):
 *   7 = MOEA_NSGAII        - MOEA Framework NSGA-II (MULTI_OBJECTIVE_MODE only)
 *   8 = MOEA_SPEA2         - MOEA Framework SPEA2 (MULTI_OBJECTIVE_MODE only)
 *   9 = MOEA_MOEAD         - MOEA Framework MOEA/D (MULTI_OBJECTIVE_MODE only)
 *  10 = MOEA_OMOPSO        - MOEA Framework OMOPSO (MULTI_OBJECTIVE_MODE only)
 *  11 = MOEA_AMOSA         - MOEA Framework AMOSA (MULTI_OBJECTIVE_MODE only)
 *
 * ============================================================================
 * MULTI-ALGORITHM MODE:
 * ============================================================================
 *
 * When multiple algorithms are selected:
 *   - Each algorithm runs for each config file
 *   - Each algorithm runs ITERATION_COUNT times with different seeds
 *   - Seeds increment by SEED_INCREMENT: baseSeed, baseSeed+100, baseSeed+200, ...
 *
 * When MULTI_OBJECTIVE_MODE = true:
 *   - Universal Pareto Set computed from all algorithm solutions
 *   - HV, GD, IGD, Spacing calculated for each algorithm
 *   - CSV outputs generated for external plotting
 *
 * When MULTI_OBJECTIVE_MODE = false:
 *   - Single-objective comparison (best/avg values)
 *   - No Pareto analysis (not applicable)
 *
 * ============================================================================
 * EXTREME POINTS MODE (for single-objective algorithms 4-6):
 * ============================================================================
 *
 * When EXTREME_POINTS_MODE = true (requires MULTI_OBJECTIVE_MODE = true):
 *   - GA, SA, and LocalSearch run TWICE per iteration
 *   - First run: weights (1.0, 0.0) to minimize makespan only
 *   - Second run: weights (0.0, 1.0) to minimize energy only
 *   - This captures the two extreme points of the Pareto front
 *   - Produces 2 solutions per iteration instead of 1
 *
 * When EXTREME_POINTS_MODE = false:
 *   - Single-objective algorithms use WEIGHT_MAKESPAN and WEIGHT_ENERGY
 *   - Produces 1 weighted-sum solution per iteration
 *
 * ============================================================================
 */
public class FlexibleExperimentMain {

    // =========================================================================
    // USER CONFIGURATION - CHANGE THESE VALUES
    // =========================================================================

    /**
     * Algorithms to run for each config file.
     * Multiple algorithms will be compared with Pareto analysis.
     *
     * Algorithm IDs:
     *   1 = First Available (heuristic)
     *   2 = Shortest Queue (heuristic)
     *   3 = Workload Aware (heuristic)
     *   4 = Genetic Algorithm (GA)
     *   5 = Simulated Annealing (SA)
     *   6 = Local Search (LS)
     *   7 = MOEA_NSGAII (multi-objective only)
     *   8 = MOEA_SPEA2 (multi-objective only)
     *   9 = MOEA_MOEAD (multi-objective only)
     *  10 = MOEA_OMOPSO (multi-objective only)
     *  11 = MOEA_AMOSA (multi-objective only)
     */
    private static final int[] ALGORITHMS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    /**
     * Number of iterations per algorithm per config file.
     * Each iteration uses a different seed: baseSeed + (iteration-1) * SEED_INCREMENT
     * Example: baseSeed=1, ITERATION_COUNT=10 -> seeds: 1, 101, 201, 301, ...
     */
    private static final int ITERATION_COUNT = 1;

    /**
     * Seed increment between iterations.
     */
    private static final int SEED_INCREMENT = 100;

    /**
     * Multi-objective mode:
     *   false = Single objective comparison (best value only, no Pareto metrics)
     *           NOTE: Strategies 7-8 (MOEA) will be skipped in this mode!
     *   true  = Multi-objective (weighted sum for GA/SA/LS, Pareto front for MOEA)
     *           Full Pareto analysis with HV, GD, IGD calculations
     */
    private static final boolean MULTI_OBJECTIVE_MODE = true;

    /**
     * Extreme Points Mode (only applies when MULTI_OBJECTIVE_MODE = true):
     *   When enabled, single-objective algorithms (GA, SA, LocalSearch) run TWICE per iteration:
     *     - First run: weights (1.0, 0.0) to minimize makespan only
     *     - Second run: weights (0.0, 1.0) to minimize energy only
     *   This captures the two extreme points of the Pareto front.
     *
     *   When disabled, single-objective algorithms use WEIGHT_MAKESPAN and WEIGHT_ENERGY
     *   to produce a single weighted-sum solution per iteration.
     */
    private static final boolean EXTREME_POINTS_MODE = false;

    /**
     * Objective weights (used for weighted-sum or to select primary objective)
     * These are automatically normalized, so (0.7, 0.3) is same as (70, 30)
     */
    private static final double WEIGHT_MAKESPAN = 0.7;
    private static final double WEIGHT_ENERGY = 0.3;

    /**
     * Number of experiment files to run (max from sorted list)
     */
    private static final int MAX_EXPERIMENTS = 2;

    /**
     * Enable verbose logging for metaheuristics
     */
    private static final boolean VERBOSE_LOGGING = false;

    // =========================================================================
    // UNIFIED ALGORITHM PARAMETERS (40,000 evaluations for fair comparison)
    // =========================================================================

    private static final int TOTAL_EVALUATIONS = 40000;
    private static final int POPULATION_SIZE = 200;
    private static final int GENERATIONS = 200;
    private static final double CROSSOVER_RATE = 0.9;
    private static final double MUTATION_RATE = 0.1;

    // GA-specific
    private static final int GA_ELITE_COUNT = 20;
    private static final int GA_TOURNAMENT_SIZE = 3;

    // SA-specific
    private static final double SA_INITIAL_TEMPERATURE = 1000.0;
    private static final double SA_COOLING_RATE = 0.95;

    // Local Search
    private static final int LS_MAX_ITERATIONS = 40000;
    private static final int LS_MAX_NO_IMPROVEMENT = 200;

    // =========================================================================
    // PLOT OPTIONS CONFIGURATION
    // =========================================================================

    /** DPI (dots per inch) for output images. Higher = better quality but larger files */
    private static final int PLOT_DPI = 300;

    /** Figure width in inches */
    private static final int PLOT_WIDTH = 14;

    /** Figure height in inches */
    private static final int PLOT_HEIGHT = 10;

    /** Base marker size for algorithm points */
    private static final int PLOT_MARKER_SIZE = 10;

    /** Marker shape: circle, square, triangle, diamond, star */
    private static final String PLOT_MARKER_SHAPE = "circle";

    /** Whether to show legend on plots */
    private static final boolean PLOT_SHOW_LEGEND = true;

    /** Whether to show labels on individual points */
    private static final boolean PLOT_SHOW_LABELS = false;

    /** XMode: Only show single-objective points if they are in Universal Pareto */
    private static final boolean PLOT_XMODE = false;

    // =========================================================================
    // DIRECTORY CONFIGURATION
    // =========================================================================

    private static final String CONFIG_DIRECTORY = "configs/experiment1new";
    private static final String REPORTS_DIRECTORY = "reports";

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("========================================================");
        System.out.println("  Multi-Algorithm Experiment Runner");
        System.out.println("========================================================");
        System.out.println();
        printConfiguration();
        System.out.println();

        // Validate configuration
        int[] effectiveAlgorithms = validateAlgorithms();
        if (effectiveAlgorithms.length == 0) {
            System.err.println("ERROR: No valid algorithms selected.");
            System.exit(1);
        }

        // Load configuration files
        List<File> configFiles = loadConfigFiles();
        if (configFiles.isEmpty()) {
            System.err.println("ERROR: No .cosc configuration files found in " + CONFIG_DIRECTORY);
            System.exit(1);
        }

        // Limit to MAX_EXPERIMENTS
        if (configFiles.size() > MAX_EXPERIMENTS) {
            configFiles = configFiles.subList(0, MAX_EXPERIMENTS);
        }

        System.out.println("Running " + configFiles.size() + " config(s) × " +
            effectiveAlgorithms.length + " algorithm(s) × " + ITERATION_COUNT + " iteration(s)");
        System.out.println("Total runs: " + (configFiles.size() * effectiveAlgorithms.length * ITERATION_COUNT));
        System.out.println();

        // Ensure reports directory exists
        ensureReportsDirectory();

        // Run experiments
        List<ConfigExperimentResults> allResults = new ArrayList<>();
        int configNumber = 1;

        for (File configFile : configFiles) {
            System.out.println("========================================================");
            System.out.println("  CONFIG " + configNumber + "/" + configFiles.size() + ": " + configFile.getName());
            System.out.println("========================================================");
            System.out.println();

            try {
                ConfigExperimentResults result = runMultiAlgorithmExperiment(
                    configFile, configNumber, effectiveAlgorithms);
                allResults.add(result);

                if (MULTI_OBJECTIVE_MODE) {
                    printMultiObjectiveComparison(result);
                } else {
                    printSingleObjectiveComparison(result);
                }

            } catch (Exception e) {
                System.err.println("ERROR: Config " + configNumber + " failed: " + e.getMessage());
                e.printStackTrace();
            }

            configNumber++;
            System.out.println();
        }

        // Execute Python plotting script (uses existing scripts/pareto_plotter.py)
        if (MULTI_OBJECTIVE_MODE) {
            executePythonPlotter();
        }

        // Print final summary
        printFinalSummary(allResults);
    }

    // =========================================================================
    // CONFIGURATION VALIDATION
    // =========================================================================

    /**
     * Validates and filters algorithms based on mode.
     * MOEA strategies (7-11) require MULTI_OBJECTIVE_MODE = true.
     */
    private static int[] validateAlgorithms() {
        List<Integer> valid = new ArrayList<>();

        for (int algo : ALGORITHMS) {
            if (algo < 1 || algo > 11) {
                System.err.println("WARNING: Invalid algorithm ID " + algo + ", skipping.");
                continue;
            }
            if ((algo >= 7 && algo <= 11) && !MULTI_OBJECTIVE_MODE) {
                System.err.println("WARNING: Algorithm " + algo + " (" + getStrategyName(algo) +
                    ") requires MULTI_OBJECTIVE_MODE=true, skipping.");
                continue;
            }
            valid.add(algo);
        }

        return valid.stream().mapToInt(i -> i).toArray();
    }

    private static void printConfiguration() {
        System.out.println("Configuration:");
        System.out.println("  Algorithms: " + Arrays.toString(ALGORITHMS));
        System.out.println("  Iteration Count: " + ITERATION_COUNT);
        System.out.println("  Seed Increment: " + SEED_INCREMENT);
        System.out.println("  Multi-objective Mode: " + MULTI_OBJECTIVE_MODE);
        System.out.println("  Extreme Points Mode: " + EXTREME_POINTS_MODE);
        if (EXTREME_POINTS_MODE && MULTI_OBJECTIVE_MODE) {
            System.out.println("    -> Single-obj algorithms will run twice: (1.0,0.0) and (0.0,1.0)");
        }
        System.out.println("  Weights: Makespan=" + WEIGHT_MAKESPAN + ", Energy=" + WEIGHT_ENERGY);
        System.out.println("  Max Experiments: " + MAX_EXPERIMENTS);
        System.out.println("  Verbose Logging: " + VERBOSE_LOGGING);
    }

    private static String getStrategyName(int strategy) {
        switch (strategy) {
            case 1: return "FirstAvailable";
            case 2: return "ShortestQueue";
            case 3: return "WorkloadAware";
            case 4: return "GA";
            case 5: return "SA";
            case 6: return "LocalSearch";
            case 7: return "MOEA_NSGAII";
            case 8: return "MOEA_SPEA2";
            case 9: return "MOEA_MOEAD";
            case 10: return "MOEA_OMOPSO";
            case 11: return "MOEA_AMOSA";
            default: return "Unknown";
        }
    }

    // =========================================================================
    // MULTI-ALGORITHM EXPERIMENT RUNNER
    // =========================================================================

    /**
     * Runs all selected algorithms for a single config file.
     */
    private static ConfigExperimentResults runMultiAlgorithmExperiment(
            File configFile, int configNumber, int[] algorithms) throws Exception {

        // Parse configuration to get base seed
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration baseConfig = parser.parse(configFile.getAbsolutePath());
        long baseSeed = baseConfig.getRandomSeed();

        ConfigExperimentResults configResult = new ConfigExperimentResults(
            configFile.getName(), baseSeed, configNumber);

        // Run each algorithm
        for (int algoId : algorithms) {
            System.out.println("------------------------------------------------------------");
            System.out.println("  Algorithm " + algoId + "/" + algorithms.length + ": " + getStrategyName(algoId));
            System.out.println("------------------------------------------------------------");

            AlgorithmAggregatedResult algoResult = new AlgorithmAggregatedResult(
                algoId, getStrategyName(algoId));

            // Check if this algorithm should use extreme points mode
            // Only applies to single-objective algorithms (4-6) in multi-objective mode
            boolean useExtremePoints = MULTI_OBJECTIVE_MODE && EXTREME_POINTS_MODE &&
                                       (algoId >= 4 && algoId <= 6);

            // Run iterations
            for (int iter = 1; iter <= ITERATION_COUNT; iter++) {
                long seed = baseSeed + (iter - 1) * SEED_INCREMENT;

                if (useExtremePoints) {
                    // EXTREME_POINTS_MODE: Run twice with different weights
                    System.out.print("  Iteration " + iter + "/" + ITERATION_COUNT +
                        " (seed=" + seed + ", extreme points)... ");

                    try {
                        // Run 1: Makespan optimization (1.0, 0.0)
                        AlgorithmRunResult makespanRun = runSingleAlgorithmIterationWithWeights(
                            configFile, algoId, iter, seed, 1.0, 0.0);
                        algoResult.runs.add(makespanRun);
                        algoResult.allSolutions.addAll(makespanRun.solutions);

                        // Run 2: Energy optimization (0.0, 1.0) - use seed+1 for diversity
                        AlgorithmRunResult energyRun = runSingleAlgorithmIterationWithWeights(
                            configFile, algoId, iter, seed + 1, 0.0, 1.0);
                        algoResult.runs.add(energyRun);
                        algoResult.allSolutions.addAll(energyRun.solutions);

                        long totalTime = makespanRun.executionTimeMs + energyRun.executionTimeMs;
                        System.out.println("done (" + totalTime + "ms, 2 extreme points)");

                    } catch (Exception e) {
                        System.out.println("FAILED: " + e.getMessage());
                        algoResult.runs.add(new AlgorithmRunResult(algoId, getStrategyName(algoId),
                            iter, seed, e));
                    }
                } else {
                    // Normal mode: single run with configured weights
                    System.out.print("  Iteration " + iter + "/" + ITERATION_COUNT + " (seed=" + seed + ")... ");

                    try {
                        AlgorithmRunResult runResult = runSingleAlgorithmIteration(
                            configFile, algoId, iter, seed);
                        algoResult.runs.add(runResult);
                        algoResult.allSolutions.addAll(runResult.solutions);

                        String solCount = runResult.solutions.size() == 1 ? "1 solution" :
                            runResult.solutions.size() + " solutions";
                        System.out.println("done (" + runResult.executionTimeMs + "ms, " + solCount + ")");

                    } catch (Exception e) {
                        System.out.println("FAILED: " + e.getMessage());
                        algoResult.runs.add(new AlgorithmRunResult(algoId, getStrategyName(algoId),
                            iter, seed, e));
                    }
                }
            }

            // Verify non-dominance for this algorithm's solutions
            algoResult.nonDominanceVerified = verifyNonDominance(algoResult.allSolutions);
            System.out.println("  Non-dominance verified: " + (algoResult.nonDominanceVerified ? "PASS" : "FAIL"));

            configResult.algorithmResults.put(algoId, algoResult);
        }

        // Compute Universal Pareto Set (only in multi-objective mode)
        if (MULTI_OBJECTIVE_MODE) {
            System.out.println();
            System.out.println("Computing Universal Pareto Set...");
            configResult.universalParetoSet = computeUniversalPareto(configResult.algorithmResults);
            System.out.println("  Universal Pareto: " + configResult.universalParetoSet.size() + " non-dominated solutions");

            // Calculate Pareto contributions for each algorithm
            calculateParetoContributions(configResult);

            // Calculate performance metrics for each algorithm
            System.out.println();
            System.out.println("Calculating Performance Metrics...");
            calculatePerformanceMetrics(configResult);

            // Generate CSV outputs
            generateCSVOutputs(configResult);
        }

        return configResult;
    }

    /**
     * Runs a single algorithm iteration for a config file.
     */
    private static AlgorithmRunResult runSingleAlgorithmIteration(
            File configFile, int algoId, int iteration, long seed) throws Exception {

        long startTime = System.currentTimeMillis();

        // Parse configuration
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse(configFile.getAbsolutePath());

        // Initialize random generator with iteration seed
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

        // Create task assignment strategy
        TaskAssignmentStrategy taskStrategy = createStrategy(
            algoId, context.getHosts(), context.getTasks(), context.getVms());

        // Step 5: Task Assignment
        TaskAssignmentStep taskAssignmentStep = new TaskAssignmentStep(taskStrategy);
        taskAssignmentStep.execute(context);

        // Steps 6-8: Execution and Energy
        VMExecutionStep vmExecutionStep = new VMExecutionStep();
        vmExecutionStep.execute(context);

        TaskExecutionStep taskExecutionStep = new TaskExecutionStep();
        taskExecutionStep.execute(context);

        EnergyCalculationStep energyCalculationStep = new EnergyCalculationStep();
        energyCalculationStep.execute(context);

        long endTime = System.currentTimeMillis();

        // Extract solutions
        List<double[]> solutions = extractSolutions(taskStrategy, taskExecutionStep, energyCalculationStep);

        // Verify non-dominance for this run
        boolean nonDominated = verifyNonDominance(solutions);

        return new AlgorithmRunResult(
            algoId, getStrategyName(algoId), iteration, seed,
            solutions, endTime - startTime, nonDominated);
    }

    /**
     * Runs a single algorithm iteration with explicit weights.
     * Used by EXTREME_POINTS_MODE to get extreme Pareto points.
     *
     * @param makespanWeight Weight for makespan objective (use 1.0 for makespan-only)
     * @param energyWeight Weight for energy objective (use 1.0 for energy-only)
     */
    private static AlgorithmRunResult runSingleAlgorithmIterationWithWeights(
            File configFile, int algoId, int iteration, long seed,
            double makespanWeight, double energyWeight) throws Exception {

        long startTime = System.currentTimeMillis();

        // Parse configuration
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse(configFile.getAbsolutePath());

        // Initialize random generator with iteration seed
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

        // Create task assignment strategy with explicit weights
        TaskAssignmentStrategy taskStrategy = createStrategyWithWeights(
            algoId, context.getHosts(), makespanWeight, energyWeight);

        // Step 5: Task Assignment
        TaskAssignmentStep taskAssignmentStep = new TaskAssignmentStep(taskStrategy);
        taskAssignmentStep.execute(context);

        // Steps 6-8: Execution and Energy
        VMExecutionStep vmExecutionStep = new VMExecutionStep();
        vmExecutionStep.execute(context);

        TaskExecutionStep taskExecutionStep = new TaskExecutionStep();
        taskExecutionStep.execute(context);

        EnergyCalculationStep energyCalculationStep = new EnergyCalculationStep();
        energyCalculationStep.execute(context);

        long endTime = System.currentTimeMillis();

        // Extract solutions (single solution for weighted-sum methods)
        List<double[]> solutions = extractSolutions(taskStrategy, taskExecutionStep, energyCalculationStep);

        // Verify non-dominance for this run (trivially true for single solution)
        boolean nonDominated = verifyNonDominance(solutions);

        return new AlgorithmRunResult(
            algoId, getStrategyName(algoId), iteration, seed,
            solutions, endTime - startTime, nonDominated);
    }

    /**
     * Extracts solutions [makespan, energy] from strategy results.
     */
    private static List<double[]> extractSolutions(
            TaskAssignmentStrategy strategy,
            TaskExecutionStep taskStep,
            EnergyCalculationStep energyStep) {

        List<double[]> solutions = new ArrayList<>();

        // For MOEA strategies, get all Pareto front solutions
        if (strategy instanceof MOEA_NSGA2TaskSchedulingStrategy) {
            ParetoFront front = ((MOEA_NSGA2TaskSchedulingStrategy) strategy).getLastParetoFront();
            if (front != null && !front.isEmpty()) {
                for (SchedulingSolution sol : front.getSolutions()) {
                    double[] objs = sol.getObjectiveValues();
                    if (objs != null && objs.length >= 2) {
                        solutions.add(new double[]{objs[0], objs[1]});
                    }
                }
            }
        } else if (strategy instanceof MOEA_SPEA2TaskSchedulingStrategy) {
            ParetoFront front = ((MOEA_SPEA2TaskSchedulingStrategy) strategy).getLastParetoFront();
            if (front != null && !front.isEmpty()) {
                for (SchedulingSolution sol : front.getSolutions()) {
                    double[] objs = sol.getObjectiveValues();
                    if (objs != null && objs.length >= 2) {
                        solutions.add(new double[]{objs[0], objs[1]});
                    }
                }
            }
        } else if (strategy instanceof MOEA_MOEADTaskSchedulingStrategy) {
            ParetoFront front = ((MOEA_MOEADTaskSchedulingStrategy) strategy).getLastParetoFront();
            if (front != null && !front.isEmpty()) {
                for (SchedulingSolution sol : front.getSolutions()) {
                    double[] objs = sol.getObjectiveValues();
                    if (objs != null && objs.length >= 2) {
                        solutions.add(new double[]{objs[0], objs[1]});
                    }
                }
            }
        } else if (strategy instanceof MOEA_OMOPSOTaskSchedulingStrategy) {
            ParetoFront front = ((MOEA_OMOPSOTaskSchedulingStrategy) strategy).getLastParetoFront();
            if (front != null && !front.isEmpty()) {
                for (SchedulingSolution sol : front.getSolutions()) {
                    double[] objs = sol.getObjectiveValues();
                    if (objs != null && objs.length >= 2) {
                        solutions.add(new double[]{objs[0], objs[1]});
                    }
                }
            }
        } else if (strategy instanceof MOEA_AMOSATaskSchedulingStrategy) {
            ParetoFront front = ((MOEA_AMOSATaskSchedulingStrategy) strategy).getLastParetoFront();
            if (front != null && !front.isEmpty()) {
                for (SchedulingSolution sol : front.getSolutions()) {
                    double[] objs = sol.getObjectiveValues();
                    if (objs != null && objs.length >= 2) {
                        solutions.add(new double[]{objs[0], objs[1]});
                    }
                }
            }
        }

        // If no Pareto front (single-solution algorithms), use actual execution results
        if (solutions.isEmpty()) {
            double makespan = taskStep.getMakespan();
            double energy = energyStep.getTotalITEnergyKWh();
            solutions.add(new double[]{makespan, energy});
        }

        return solutions;
    }

    // =========================================================================
    // STRATEGY FACTORY METHODS
    // =========================================================================

    private static TaskAssignmentStrategy createStrategy(int algoId, List<Host> hosts,
            List<Task> tasks, List<VM> vms) {
        switch (algoId) {
            case 1: return new FirstAvailableTaskAssignmentStrategy();
            case 2: return new ShortestQueueTaskAssignmentStrategy();
            case 3: return new WorkloadAwareTaskAssignmentStrategy();
            case 4: return createGAStrategy(hosts);
            case 5: return createSAStrategy(hosts);
            case 6: return createLocalSearchStrategy(hosts);
            case 7: return createMOEA_NSGAIIStrategy(hosts, tasks, vms);
            case 8: return createMOEA_SPEA2Strategy(hosts, tasks, vms);
            case 9: return createMOEA_MOEADStrategy(hosts, tasks, vms);
            case 10: return createMOEA_OMOPSOStrategy(hosts, tasks, vms);
            case 11: return createMOEA_AMOSAStrategy(hosts, tasks, vms);
            default: return new FirstAvailableTaskAssignmentStrategy();
        }
    }

    private static TaskAssignmentStrategy createGAStrategy(List<Host> hosts) {
        GAConfiguration.Builder builder = GAConfiguration.builder()
            .populationSize(POPULATION_SIZE)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(MUTATION_RATE)
            .eliteCount(GA_ELITE_COUNT)
            .tournamentSize(GA_TOURNAMENT_SIZE)
            .terminationCondition(new GenerationCountTermination(GENERATIONS))
            .verboseLogging(VERBOSE_LOGGING);

        if (MULTI_OBJECTIVE_MODE) {
            MakespanObjective makespan = new MakespanObjective();
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);
            builder.addWeightedObjective(makespan, WEIGHT_MAKESPAN);
            builder.addWeightedObjective(energy, WEIGHT_ENERGY);
        } else {
            builder.objective(createPrimaryObjective(hosts));
        }

        return new GenerationalGATaskSchedulingStrategy(builder.build());
    }

    private static TaskAssignmentStrategy createSAStrategy(List<Host> hosts) {
        SAConfiguration.Builder builder = SAConfiguration.builder()
            .initialTemperature(SA_INITIAL_TEMPERATURE)
            .coolingSchedule(new GeometricCoolingSchedule(SA_COOLING_RATE))
            .terminationCondition(new FitnessEvaluationsTermination(TOTAL_EVALUATIONS))
            .verboseLogging(VERBOSE_LOGGING);

        if (MULTI_OBJECTIVE_MODE) {
            MakespanObjective makespan = new MakespanObjective();
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);
            builder.addWeightedObjective(makespan, WEIGHT_MAKESPAN);
            builder.addWeightedObjective(energy, WEIGHT_ENERGY);
        } else {
            builder.objective(createPrimaryObjective(hosts));
        }

        return new SimulatedAnnealingTaskSchedulingStrategy(builder.build());
    }

    private static TaskAssignmentStrategy createLocalSearchStrategy(List<Host> hosts) {
        LocalSearchConfiguration.Builder builder = LocalSearchConfiguration.builder()
            .neighborSelectionStrategy(new FirstImprovementStrategy())
            .neighborhoodType(NeighborhoodGenerator.NeighborhoodType.COMBINED)
            .maxIterations(LS_MAX_ITERATIONS)
            .maxIterationsWithoutImprovement(LS_MAX_NO_IMPROVEMENT)
            .verboseLogging(VERBOSE_LOGGING);

        if (MULTI_OBJECTIVE_MODE) {
            MakespanObjective makespan = new MakespanObjective();
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);
            builder.addWeightedObjective(makespan, WEIGHT_MAKESPAN);
            builder.addWeightedObjective(energy, WEIGHT_ENERGY);
        } else {
            builder.objective(createPrimaryObjective(hosts));
        }

        return new LocalSearchTaskSchedulingStrategy(builder.build());
    }

    // =========================================================================
    // OVERLOADED STRATEGY CREATORS FOR EXTREME POINTS MODE
    // =========================================================================

    /**
     * Creates GA strategy with explicit weights (used by EXTREME_POINTS_MODE).
     */
    private static TaskAssignmentStrategy createGAStrategyWithWeights(List<Host> hosts,
            double makespanWeight, double energyWeight) {
        GAConfiguration.Builder builder = GAConfiguration.builder()
            .populationSize(POPULATION_SIZE)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(MUTATION_RATE)
            .eliteCount(GA_ELITE_COUNT)
            .tournamentSize(GA_TOURNAMENT_SIZE)
            .terminationCondition(new GenerationCountTermination(GENERATIONS))
            .verboseLogging(VERBOSE_LOGGING);

        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        builder.addWeightedObjective(makespan, makespanWeight);
        builder.addWeightedObjective(energy, energyWeight);

        return new GenerationalGATaskSchedulingStrategy(builder.build());
    }

    /**
     * Creates SA strategy with explicit weights (used by EXTREME_POINTS_MODE).
     */
    private static TaskAssignmentStrategy createSAStrategyWithWeights(List<Host> hosts,
            double makespanWeight, double energyWeight) {
        SAConfiguration.Builder builder = SAConfiguration.builder()
            .initialTemperature(SA_INITIAL_TEMPERATURE)
            .coolingSchedule(new GeometricCoolingSchedule(SA_COOLING_RATE))
            .terminationCondition(new FitnessEvaluationsTermination(TOTAL_EVALUATIONS))
            .verboseLogging(VERBOSE_LOGGING);

        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        builder.addWeightedObjective(makespan, makespanWeight);
        builder.addWeightedObjective(energy, energyWeight);

        return new SimulatedAnnealingTaskSchedulingStrategy(builder.build());
    }

    /**
     * Creates LocalSearch strategy with explicit weights (used by EXTREME_POINTS_MODE).
     */
    private static TaskAssignmentStrategy createLocalSearchStrategyWithWeights(List<Host> hosts,
            double makespanWeight, double energyWeight) {
        LocalSearchConfiguration.Builder builder = LocalSearchConfiguration.builder()
            .neighborSelectionStrategy(new FirstImprovementStrategy())
            .neighborhoodType(NeighborhoodGenerator.NeighborhoodType.COMBINED)
            .maxIterations(LS_MAX_ITERATIONS)
            .maxIterationsWithoutImprovement(LS_MAX_NO_IMPROVEMENT)
            .verboseLogging(VERBOSE_LOGGING);

        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        builder.addWeightedObjective(makespan, makespanWeight);
        builder.addWeightedObjective(energy, energyWeight);

        return new LocalSearchTaskSchedulingStrategy(builder.build());
    }

    /**
     * Creates strategy for a given algorithm with explicit weights.
     * Used by EXTREME_POINTS_MODE for running with (1.0, 0.0) and (0.0, 1.0).
     */
    private static TaskAssignmentStrategy createStrategyWithWeights(int algoId, List<Host> hosts,
            double makespanWeight, double energyWeight) {
        switch (algoId) {
            case 4: return createGAStrategyWithWeights(hosts, makespanWeight, energyWeight);
            case 5: return createSAStrategyWithWeights(hosts, makespanWeight, energyWeight);
            case 6: return createLocalSearchStrategyWithWeights(hosts, makespanWeight, energyWeight);
            default:
                throw new IllegalArgumentException(
                    "createStrategyWithWeights only supports algorithms 4-6, got: " + algoId);
        }
    }

    private static TaskAssignmentStrategy createMOEA_NSGAIIStrategy(List<Host> hosts,
            List<Task> tasks, List<VM> vms) {
        NSGA2Configuration config = createMOEAConfiguration(hosts);
        MOEA_NSGA2TaskSchedulingStrategy strategy = new MOEA_NSGA2TaskSchedulingStrategy(config);
        strategy.setSelectionWeights(new double[]{WEIGHT_MAKESPAN, WEIGHT_ENERGY});
        strategy.setSelectionMethod(MOEA_NSGA2TaskSchedulingStrategy.SolutionSelectionMethod.WEIGHTED_SUM);
        return strategy;
    }

    private static TaskAssignmentStrategy createMOEA_SPEA2Strategy(List<Host> hosts,
            List<Task> tasks, List<VM> vms) {
        NSGA2Configuration config = createMOEAConfiguration(hosts);
        MOEA_SPEA2TaskSchedulingStrategy strategy = new MOEA_SPEA2TaskSchedulingStrategy(config);
        strategy.setSelectionWeights(new double[]{WEIGHT_MAKESPAN, WEIGHT_ENERGY});
        strategy.setSelectionMethod(MOEA_SPEA2TaskSchedulingStrategy.SolutionSelectionMethod.WEIGHTED_SUM);
        return strategy;
    }

    private static TaskAssignmentStrategy createMOEA_MOEADStrategy(List<Host> hosts,
            List<Task> tasks, List<VM> vms) {
        NSGA2Configuration config = createMOEAConfiguration(hosts);
        MOEA_MOEADTaskSchedulingStrategy strategy = new MOEA_MOEADTaskSchedulingStrategy(config);
        strategy.setSelectionWeights(new double[]{WEIGHT_MAKESPAN, WEIGHT_ENERGY});
        strategy.setSelectionMethod(MOEA_MOEADTaskSchedulingStrategy.SolutionSelectionMethod.WEIGHTED_SUM);
        return strategy;
    }

    private static TaskAssignmentStrategy createMOEA_OMOPSOStrategy(List<Host> hosts,
            List<Task> tasks, List<VM> vms) {
        NSGA2Configuration config = createMOEAConfiguration(hosts);
        MOEA_OMOPSOTaskSchedulingStrategy strategy = new MOEA_OMOPSOTaskSchedulingStrategy(config);
        strategy.setSelectionWeights(new double[]{WEIGHT_MAKESPAN, WEIGHT_ENERGY});
        strategy.setSelectionMethod(MOEA_OMOPSOTaskSchedulingStrategy.SolutionSelectionMethod.WEIGHTED_SUM);
        return strategy;
    }

    private static TaskAssignmentStrategy createMOEA_AMOSAStrategy(List<Host> hosts,
            List<Task> tasks, List<VM> vms) {
        NSGA2Configuration config = createMOEAConfiguration(hosts);
        MOEA_AMOSATaskSchedulingStrategy strategy = new MOEA_AMOSATaskSchedulingStrategy(config);
        strategy.setSelectionWeights(new double[]{WEIGHT_MAKESPAN, WEIGHT_ENERGY});
        strategy.setSelectionMethod(MOEA_AMOSATaskSchedulingStrategy.SolutionSelectionMethod.WEIGHTED_SUM);
        return strategy;
    }

    private static NSGA2Configuration createMOEAConfiguration(List<Host> hosts) {
        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);

        return NSGA2Configuration.builder()
            .populationSize(POPULATION_SIZE)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(MUTATION_RATE)
            .addObjective(makespan)
            .addObjective(energy)
            .terminationCondition(new GenerationCountTermination(GENERATIONS))
            .verboseLogging(VERBOSE_LOGGING)
            .build();
    }

    private static SchedulingObjective createPrimaryObjective(List<Host> hosts) {
        if (WEIGHT_MAKESPAN >= WEIGHT_ENERGY) {
            return new MakespanObjective();
        } else {
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);
            return energy;
        }
    }

    // =========================================================================
    // UNIVERSAL PARETO & NON-DOMINANCE
    // =========================================================================

    /**
     * Computes the Universal Pareto Set from all algorithm solutions.
     * Returns only non-dominated solutions.
     */
    private static List<double[]> computeUniversalPareto(
            Map<Integer, AlgorithmAggregatedResult> algorithmResults) {

        // Collect ALL solutions from all algorithms
        List<double[]> allSolutions = new ArrayList<>();
        for (AlgorithmAggregatedResult result : algorithmResults.values()) {
            allSolutions.addAll(result.allSolutions);
        }

        // Filter to non-dominated only
        List<double[]> universalPareto = new ArrayList<>();
        for (double[] candidate : allSolutions) {
            boolean isDominated = false;
            for (double[] other : allSolutions) {
                if (candidate != other && dominates(other, candidate)) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                // Check for duplicates
                boolean duplicate = false;
                for (double[] existing : universalPareto) {
                    if (Math.abs(existing[0] - candidate[0]) < 1e-9 &&
                        Math.abs(existing[1] - candidate[1]) < 1e-9) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    universalPareto.add(candidate);
                }
            }
        }

        // Sort by first objective (makespan)
        universalPareto.sort((a, b) -> Double.compare(a[0], b[0]));
        return universalPareto;
    }

    /**
     * Calculates how many solutions each algorithm contributed to the Universal Pareto.
     * A solution is "contributed" if it appears in the Universal Pareto set.
     */
    private static void calculateParetoContributions(ConfigExperimentResults configResult) {
        System.out.println("  Pareto contributions:");

        for (AlgorithmAggregatedResult algoResult : configResult.algorithmResults.values()) {
            int contribution = 0;

            // Count how many of this algorithm's solutions are in the Universal Pareto
            for (double[] sol : algoResult.allSolutions) {
                if (isInUniversalPareto(sol, configResult.universalParetoSet)) {
                    contribution++;
                }
            }

            algoResult.paretoContribution = contribution;
            System.out.println("    " + algoResult.algorithmName + ": " + contribution +
                " / " + configResult.universalParetoSet.size() +
                " (" + String.format("%.1f", 100.0 * contribution / configResult.universalParetoSet.size()) + "%)");
        }
    }

    /**
     * Checks if solution a dominates solution b using standard Pareto dominance.
     * a dominates b if: a is at least as good in ALL objectives AND strictly better in AT LEAST ONE.
     * Both objectives are minimization (lower is better).
     */
    private static boolean dominates(double[] a, double[] b) {
        boolean atLeastOneBetter = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > b[i]) {
                return false;  // a is worse in at least one objective
            }
            if (a[i] < b[i]) {
                atLeastOneBetter = true;
            }
        }
        return atLeastOneBetter;
    }

    /**
     * Filters a list of solutions to its non-dominated subset.
     * Used to ensure each algorithm's solution set is a proper Pareto front
     * before calculating metrics like HV, GD, IGD.
     *
     * @param solutions List of solutions (may contain dominated points)
     * @return List containing only non-dominated solutions
     */
    private static List<double[]> filterToNonDominated(List<double[]> solutions) {
        if (solutions.size() <= 1) {
            return new ArrayList<>(solutions);
        }

        List<double[]> nonDominated = new ArrayList<>();
        for (double[] candidate : solutions) {
            boolean isDominated = false;
            for (double[] other : solutions) {
                if (candidate != other && dominates(other, candidate)) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                // Check for duplicates
                boolean duplicate = false;
                for (double[] existing : nonDominated) {
                    if (Math.abs(existing[0] - candidate[0]) < 1e-9 &&
                        Math.abs(existing[1] - candidate[1]) < 1e-9) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    nonDominated.add(candidate);
                }
            }
        }

        // Sort by first objective (makespan)
        nonDominated.sort((a, b) -> Double.compare(a[0], b[0]));
        return nonDominated;
    }

    /**
     * Verifies all solutions in a list are non-dominated (internal check).
     * Uses Dominance.compare() from PerfMet package.
     */
    private static boolean verifyNonDominance(List<double[]> solutions) {
        if (solutions.size() <= 1) return true;

        for (int i = 0; i < solutions.size(); i++) {
            for (int j = 0; j < solutions.size(); j++) {
                if (i != j) {
                    ArrayList<Double> sol1 = toArrayList(solutions.get(i));
                    ArrayList<Double> sol2 = toArrayList(solutions.get(j));
                    // If sol2 dominates sol1, then sol1 shouldn't be in the front
                    if (Dominance.compare(sol2, sol1) == -1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static ArrayList<Double> toArrayList(double[] arr) {
        ArrayList<Double> list = new ArrayList<>();
        list.add(arr[0]);
        list.add(arr[1]);
        return list;
    }

    // =========================================================================
    // PERFORMANCE METRICS CALCULATION
    // =========================================================================

    /**
     * Calculates HV, GD, IGD, Spacing for each algorithm using PerfMet.
     *
     * IMPORTANT: Each algorithm's solutions are filtered to their non-dominated
     * subset before metrics calculation. This ensures proper Pareto front comparison
     * even when multiple iterations yield dominated points.
     */
    private static void calculatePerformanceMetrics(ConfigExperimentResults configResult) {
        // Build the allParetos list for PerformanceMetrics
        // Format: [algo1, algo2, ..., algoN, universalPareto]
        ArrayList<ArrayList<ArrayList<Double>>> allParetos = new ArrayList<>();

        // Add each algorithm's solutions (filtered to non-dominated)
        List<Integer> algoOrder = new ArrayList<>(configResult.algorithmResults.keySet());
        algoOrder.sort(Integer::compareTo);

        for (int algoId : algoOrder) {
            AlgorithmAggregatedResult algoResult = configResult.algorithmResults.get(algoId);

            // Filter to non-dominated subset before metrics calculation
            // This is crucial when aggregating across multiple iterations
            algoResult.nonDominatedSolutions = filterToNonDominated(algoResult.allSolutions);

            int totalSolutions = algoResult.allSolutions.size();
            int nonDominatedCount = algoResult.nonDominatedSolutions.size();
            if (totalSolutions != nonDominatedCount) {
                System.out.println("  " + algoResult.algorithmName + ": " + nonDominatedCount +
                    " non-dominated / " + totalSolutions + " total solutions");
            }

            ArrayList<ArrayList<Double>> paretoList = new ArrayList<>();
            for (double[] sol : algoResult.nonDominatedSolutions) {
                ArrayList<Double> point = new ArrayList<>();
                point.add(sol[0]);
                point.add(sol[1]);
                paretoList.add(point);
            }
            // Handle empty Pareto (shouldn't happen, but be safe)
            if (paretoList.isEmpty()) {
                ArrayList<Double> dummy = new ArrayList<>();
                dummy.add(Double.MAX_VALUE);
                dummy.add(Double.MAX_VALUE);
                paretoList.add(dummy);
            }
            allParetos.add(paretoList);
        }

        // Add Universal Pareto as the last (reference)
        ArrayList<ArrayList<Double>> universalList = new ArrayList<>();
        for (double[] sol : configResult.universalParetoSet) {
            ArrayList<Double> point = new ArrayList<>();
            point.add(sol[0]);
            point.add(sol[1]);
            universalList.add(point);
        }
        allParetos.add(universalList);

        // Create PerformanceMetrics with Universal Pareto as reference (last index)
        int referenceIndex = allParetos.size() - 1;
        PerformanceMetrics pm = new PerformanceMetrics(allParetos, referenceIndex);

        // Calculate metrics for each algorithm
        int index = 0;
        for (int algoId : algoOrder) {
            AlgorithmAggregatedResult algoResult = configResult.algorithmResults.get(algoId);
            try {
                algoResult.hv = pm.HV(index);
                algoResult.gd = pm.GD(index);
                algoResult.igd = pm.IGD(index);
                // Spacing requires at least 2 non-dominated solutions
                if (algoResult.nonDominatedSolutions.size() > 1) {
                    algoResult.spacing = pm.Spacing(index);
                } else {
                    algoResult.spacing = 0.0; // Single solution has no spacing
                }
            } catch (Exception e) {
                System.err.println("  WARNING: Metrics calculation failed for " +
                    algoResult.algorithmName + ": " + e.getMessage());
                algoResult.hv = 0;
                algoResult.gd = Double.MAX_VALUE;
                algoResult.igd = Double.MAX_VALUE;
                algoResult.spacing = 0;
            }
            index++;
        }

        // Calculate metrics for Universal Pareto itself
        configResult.universalHV = pm.HV(referenceIndex);
    }

    // =========================================================================
    // CSV OUTPUT GENERATION
    // =========================================================================

    private static void generateCSVOutputs(ConfigExperimentResults configResult) {
        // Create output directory
        String dirName = "config_" + configResult.configNumber + "_seed_" + configResult.baseSeed;
        Path outputDir = Paths.get(REPORTS_DIRECTORY, dirName);

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            System.err.println("ERROR: Could not create output directory: " + e.getMessage());
            return;
        }

        // Generate algorithm-specific CSVs
        for (Map.Entry<Integer, AlgorithmAggregatedResult> entry : configResult.algorithmResults.entrySet()) {
            generateAlgorithmCSV(outputDir, entry.getValue());
        }

        // Generate Universal Pareto CSV
        generateUniversalParetoCSV(outputDir, configResult);

        // Generate combined Pareto graph data
        generateParetoGraphDataCSV(outputDir, configResult);

        // Generate performance metrics CSV
        generatePerformanceMetricsCSV(outputDir, configResult);

        // Generate plot options JSON for Python plotter
        generatePlotOptionsJSON(outputDir);

        System.out.println("  CSV outputs saved to: " + outputDir);
    }

    private static void generateAlgorithmCSV(Path outputDir, AlgorithmAggregatedResult algoResult) {
        String fileName = "algorithm_" + algoResult.algorithmId + "_" + algoResult.algorithmName + ".csv";
        Path filePath = outputDir.resolve(fileName);

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
            writer.println("Iteration,Seed,Makespan,Energy,NonDominated");

            for (AlgorithmRunResult run : algoResult.runs) {
                if (run.error != null) {
                    writer.printf("%d,%d,ERROR,ERROR,false%n", run.iteration, run.seed);
                } else {
                    for (double[] sol : run.solutions) {
                        writer.printf("%d,%d,%.6f,%.9f,%b%n",
                            run.iteration, run.seed, sol[0], sol[1], run.nonDominanceVerified);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("ERROR: Could not write " + fileName + ": " + e.getMessage());
        }
    }

    private static void generateUniversalParetoCSV(Path outputDir, ConfigExperimentResults configResult) {
        Path filePath = outputDir.resolve("universal_pareto.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
            writer.println("Makespan,Energy,SourceAlgorithm,SourceIteration");

            // For each universal Pareto solution, find which algorithm it came from
            for (double[] sol : configResult.universalParetoSet) {
                String sourceAlgo = "Unknown";
                int sourceIter = 0;

                outer:
                for (AlgorithmAggregatedResult algoResult : configResult.algorithmResults.values()) {
                    for (AlgorithmRunResult run : algoResult.runs) {
                        for (double[] runSol : run.solutions) {
                            if (Math.abs(runSol[0] - sol[0]) < 1e-9 &&
                                Math.abs(runSol[1] - sol[1]) < 1e-9) {
                                sourceAlgo = algoResult.algorithmName;
                                sourceIter = run.iteration;
                                break outer;
                            }
                        }
                    }
                }

                writer.printf("%.6f,%.9f,%s,%d%n", sol[0], sol[1], sourceAlgo, sourceIter);
            }
        } catch (IOException e) {
            System.err.println("ERROR: Could not write universal_pareto.csv: " + e.getMessage());
        }
    }

    private static void generateParetoGraphDataCSV(Path outputDir, ConfigExperimentResults configResult) {
        Path filePath = outputDir.resolve("pareto_graph_data.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
            writer.println("Algorithm,Makespan,Energy,Iteration,Seed,IsUniversalPareto");

            // Add all algorithm solutions
            for (AlgorithmAggregatedResult algoResult : configResult.algorithmResults.values()) {
                for (AlgorithmRunResult run : algoResult.runs) {
                    if (run.error != null) continue;
                    for (double[] sol : run.solutions) {
                        boolean isUniversal = isInUniversalPareto(sol, configResult.universalParetoSet);
                        writer.printf("%s,%.6f,%.9f,%d,%d,%b%n",
                            algoResult.algorithmName, sol[0], sol[1],
                            run.iteration, run.seed, isUniversal);
                    }
                }
            }

            // Add Universal Pareto points
            for (double[] sol : configResult.universalParetoSet) {
                writer.printf("Universal_Pareto,%.6f,%.9f,0,0,true%n", sol[0], sol[1]);
            }

        } catch (IOException e) {
            System.err.println("ERROR: Could not write pareto_graph_data.csv: " + e.getMessage());
        }
    }

    private static boolean isInUniversalPareto(double[] sol, List<double[]> universalPareto) {
        for (double[] u : universalPareto) {
            if (Math.abs(u[0] - sol[0]) < 1e-9 && Math.abs(u[1] - sol[1]) < 1e-9) {
                return true;
            }
        }
        return false;
    }

    private static void generatePerformanceMetricsCSV(Path outputDir, ConfigExperimentResults configResult) {
        Path filePath = outputDir.resolve("performance_metrics.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
            // NonDomSolutions = solutions used for metrics calculation (filtered Pareto front)
            // TotalSolutions = all solutions across iterations (may include dominated)
            // ParetoContribution = number of solutions contributed to Universal Pareto
            writer.println("Algorithm,HV,GD,IGD,Spacing,NonDomSolutions,TotalSolutions,ParetoContribution,IterationCount,AvgTimeMs,NonDominanceVerified");

            List<Integer> algoOrder = new ArrayList<>(configResult.algorithmResults.keySet());
            algoOrder.sort(Integer::compareTo);

            for (int algoId : algoOrder) {
                AlgorithmAggregatedResult algoResult = configResult.algorithmResults.get(algoId);
                long avgTime = algoResult.runs.stream()
                    .filter(r -> r.error == null)
                    .mapToLong(r -> r.executionTimeMs)
                    .sum() / Math.max(1, algoResult.runs.size());

                int ndCount = algoResult.nonDominatedSolutions != null ?
                    algoResult.nonDominatedSolutions.size() : algoResult.allSolutions.size();

                writer.printf("%s,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d,%d,%b%n",
                    algoResult.algorithmName,
                    algoResult.hv,
                    algoResult.gd,
                    algoResult.igd,
                    algoResult.spacing,
                    ndCount,
                    algoResult.allSolutions.size(),
                    algoResult.paretoContribution,
                    algoResult.runs.size(),
                    avgTime,
                    algoResult.nonDominanceVerified);
            }

            // Add Universal Pareto row
            writer.printf("Universal_Pareto,%.6f,0.000000,0.000000,0.000000,%d,%d,%d,N/A,N/A,true%n",
                configResult.universalHV, configResult.universalParetoSet.size(),
                configResult.universalParetoSet.size(), configResult.universalParetoSet.size());

        } catch (IOException e) {
            System.err.println("ERROR: Could not write performance_metrics.csv: " + e.getMessage());
        }
    }

    /**
     * Generates plot_options.json with visualization configuration for the Python plotter.
     */
    private static void generatePlotOptionsJSON(Path outputDir) {
        Path filePath = outputDir.resolve("plot_options.json");

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
            writer.println("{");
            writer.println("  \"dpi\": " + PLOT_DPI + ",");
            writer.println("  \"width\": " + PLOT_WIDTH + ",");
            writer.println("  \"height\": " + PLOT_HEIGHT + ",");
            writer.println("  \"marker_size\": " + PLOT_MARKER_SIZE + ",");
            writer.println("  \"marker_shape\": \"" + PLOT_MARKER_SHAPE + "\",");
            writer.println("  \"show_legend\": " + PLOT_SHOW_LEGEND + ",");
            writer.println("  \"show_labels\": " + PLOT_SHOW_LABELS + ",");
            writer.println("  \"xmode\": " + PLOT_XMODE);
            writer.println("}");
        } catch (IOException e) {
            System.err.println("ERROR: Could not write plot_options.json: " + e.getMessage());
        }
    }

    /**
     * Executes the Python plotting script to generate Pareto front visualizations.
     * Uses OS detection to choose the correct Python command:
     * - Windows: python (python3 does not exist)
     * - Linux/Mac: python3 first, then python as fallback
     */
    private static void executePythonPlotter() {
        // Use the existing script from scripts folder (not generated)
        Path scriptPath = Paths.get("scripts", "pareto_plotter.py");

        if (!Files.exists(scriptPath)) {
            System.err.println("WARNING: Python script not found at " + scriptPath);
            printManualPythonInstructions();
            return;
        }

        System.out.println();
        System.out.println("Executing Python plotter...");

        // Detect OS and choose appropriate Python command
        // Windows: "python" only (python3 doesn't exist on Windows)
        // Linux/Mac: Try "python3" first, then "python"
        String[] pythonCommands;
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            pythonCommands = new String[]{"python"};
        } else {
            pythonCommands = new String[]{"python3", "python"};
        }

        boolean success = false;

        for (String pythonCmd : pythonCommands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd,
                    scriptPath.toString(),
                    REPORTS_DIRECTORY
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();

                // Read output
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("  [Python] " + line);
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    System.out.println("Pareto plots generated successfully.");
                    success = true;
                    break;
                } else {
                    System.err.println("WARNING: " + pythonCmd + " exited with code " + exitCode);
                }
            } catch (IOException e) {
                // Python command not found, try next
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("WARNING: Python execution interrupted");
                break;
            }
        }

        if (!success) {
            printManualPythonInstructions();
        }
    }

    /**
     * Prints instructions for manually running the Python plotter.
     */
    private static void printManualPythonInstructions() {
        System.out.println();
        System.out.println("Could not automatically execute Python script.");
        System.out.println("To generate plots manually, run:");
        System.out.println("  python scripts/pareto_plotter.py " + REPORTS_DIRECTORY + "/");
        System.out.println();
        System.out.println("Required Python packages: matplotlib, pandas");
        System.out.println("Install with: pip install matplotlib pandas");
    }

    // =========================================================================
    // OUTPUT DISPLAY
    // =========================================================================

    private static void printMultiObjectiveComparison(ConfigExperimentResults result) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  MULTI-OBJECTIVE COMPARISON (Config: " + result.configFileName + ")");
        System.out.println("============================================================");
        System.out.println();

        // Header: ND_Sol = Non-Dominated Solutions, P_Cont = Pareto Contribution
        System.out.printf("%-15s | %-7s | %-7s | %-7s | %-7s | %-6s | %-6s | %-5s%n",
            "Algorithm", "HV", "GD", "IGD", "Spacing", "ND_Sol", "P_Cont", "Valid");
        System.out.println("-".repeat(85));

        List<Integer> algoOrder = new ArrayList<>(result.algorithmResults.keySet());
        algoOrder.sort(Integer::compareTo);

        for (int algoId : algoOrder) {
            AlgorithmAggregatedResult algoResult = result.algorithmResults.get(algoId);
            // Show non-dominated count (what metrics are based on)
            int ndCount = algoResult.nonDominatedSolutions != null ?
                algoResult.nonDominatedSolutions.size() : algoResult.allSolutions.size();
            System.out.printf("%-15s | %-7.4f | %-7.4f | %-7.4f | %-7.4f | %-6d | %-6d | %-5s%n",
                algoResult.algorithmName,
                algoResult.hv,
                algoResult.gd,
                algoResult.igd,
                algoResult.spacing,
                ndCount,
                algoResult.paretoContribution,
                algoResult.nonDominanceVerified ? "PASS" : "FAIL");
        }

        System.out.println("-".repeat(85));
        System.out.printf("%-15s | %-7.4f | %-7s | %-7s | %-7s | %-6d | %-6d | %-5s%n",
            "Universal", result.universalHV, "0.0000", "0.0000", "-",
            result.universalParetoSet.size(), result.universalParetoSet.size(), "REF");
        System.out.println();
    }

    private static void printSingleObjectiveComparison(ConfigExperimentResults result) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  SINGLE-OBJECTIVE COMPARISON (Config: " + result.configFileName + ")");
        String primaryObj = WEIGHT_MAKESPAN >= WEIGHT_ENERGY ? "Makespan" : "Energy";
        System.out.println("  Primary Objective: " + primaryObj);
        System.out.println("============================================================");
        System.out.println();

        System.out.printf("%-15s | %-12s | %-12s | %-12s | %-12s | %-10s%n",
            "Algorithm", "Best Make.", "Avg Make.", "Best Energy", "Avg Energy", "Iterations");
        System.out.println("-".repeat(85));

        List<Integer> algoOrder = new ArrayList<>(result.algorithmResults.keySet());
        algoOrder.sort(Integer::compareTo);

        double bestPrimary = Double.MAX_VALUE;
        String bestAlgo = "";

        for (int algoId : algoOrder) {
            AlgorithmAggregatedResult algoResult = result.algorithmResults.get(algoId);

            if (algoResult.allSolutions.isEmpty()) {
                System.out.printf("%-15s | %-12s | %-12s | %-12s | %-12s | %-10d%n",
                    algoResult.algorithmName, "N/A", "N/A", "N/A", "N/A", 0);
                continue;
            }

            double bestMakespan = algoResult.allSolutions.stream()
                .mapToDouble(s -> s[0]).min().orElse(Double.MAX_VALUE);
            double avgMakespan = algoResult.allSolutions.stream()
                .mapToDouble(s -> s[0]).average().orElse(0);
            double bestEnergy = algoResult.allSolutions.stream()
                .mapToDouble(s -> s[1]).min().orElse(Double.MAX_VALUE);
            double avgEnergy = algoResult.allSolutions.stream()
                .mapToDouble(s -> s[1]).average().orElse(0);

            double primary = WEIGHT_MAKESPAN >= WEIGHT_ENERGY ? bestMakespan : bestEnergy;
            if (primary < bestPrimary) {
                bestPrimary = primary;
                bestAlgo = algoResult.algorithmName;
            }

            System.out.printf("%-15s | %-12.2f | %-12.2f | %-12.6f | %-12.6f | %-10d%n",
                algoResult.algorithmName, bestMakespan, avgMakespan,
                bestEnergy, avgEnergy, algoResult.runs.size());
        }

        System.out.println("-".repeat(85));
        System.out.println();
        System.out.println("Winner: " + bestAlgo + " (Best " + primaryObj + ": " +
            String.format("%.4f", bestPrimary) + ")");
        System.out.println();
        System.out.println("NOTE: Pareto analysis (HV, GD, IGD) requires MULTI_OBJECTIVE_MODE = true");
        System.out.println();
    }

    private static void printFinalSummary(List<ConfigExperimentResults> allResults) {
        System.out.println();
        System.out.println("========================================================");
        System.out.println("  FINAL SUMMARY - ALL CONFIGS");
        System.out.println("========================================================");
        System.out.println();

        System.out.println("Configs processed: " + allResults.size());
        System.out.println("Mode: " + (MULTI_OBJECTIVE_MODE ? "Multi-objective (Pareto analysis)" :
            "Single-objective comparison"));
        System.out.println();

        if (MULTI_OBJECTIVE_MODE && !allResults.isEmpty()) {
            // Average metrics across all configs
            System.out.println("Average Performance Metrics Across All Configs:");
            System.out.println();
            System.out.printf("%-15s | %-10s | %-10s | %-10s%n",
                "Algorithm", "Avg HV", "Avg GD", "Avg IGD");
            System.out.println("-".repeat(55));

            // Collect all algorithm IDs
            Map<Integer, List<Double>> hvByAlgo = new HashMap<>();
            Map<Integer, List<Double>> gdByAlgo = new HashMap<>();
            Map<Integer, List<Double>> igdByAlgo = new HashMap<>();

            for (ConfigExperimentResults config : allResults) {
                for (Map.Entry<Integer, AlgorithmAggregatedResult> entry : config.algorithmResults.entrySet()) {
                    int algoId = entry.getKey();
                    AlgorithmAggregatedResult algoResult = entry.getValue();

                    hvByAlgo.computeIfAbsent(algoId, k -> new ArrayList<>()).add(algoResult.hv);
                    gdByAlgo.computeIfAbsent(algoId, k -> new ArrayList<>()).add(algoResult.gd);
                    igdByAlgo.computeIfAbsent(algoId, k -> new ArrayList<>()).add(algoResult.igd);
                }
            }

            List<Integer> algoOrder = new ArrayList<>(hvByAlgo.keySet());
            algoOrder.sort(Integer::compareTo);

            for (int algoId : algoOrder) {
                double avgHV = hvByAlgo.get(algoId).stream().mapToDouble(d -> d).average().orElse(0);
                double avgGD = gdByAlgo.get(algoId).stream().mapToDouble(d -> d).average().orElse(0);
                double avgIGD = igdByAlgo.get(algoId).stream().mapToDouble(d -> d).average().orElse(0);

                System.out.printf("%-15s | %-10.4f | %-10.4f | %-10.4f%n",
                    getStrategyName(algoId), avgHV, avgGD, avgIGD);
            }

            System.out.println("-".repeat(55));
        }

        System.out.println();
        System.out.println("Reports saved to: " + REPORTS_DIRECTORY + "/");
        System.out.println("Run: python scripts/pareto_plotter.py " + REPORTS_DIRECTORY + "/");
        System.out.println("========================================================");
    }

    // =========================================================================
    // FILE HANDLING
    // =========================================================================

    private static List<File> loadConfigFiles() {
        List<File> configFiles = new ArrayList<>();
        File configDir = new File(CONFIG_DIRECTORY);

        if (!configDir.exists() || !configDir.isDirectory()) {
            System.err.println("Configuration directory not found: " + CONFIG_DIRECTORY);
            return configFiles;
        }

        File[] files = configDir.listFiles((dir, name) -> name.endsWith(".cosc"));
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
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

    // =========================================================================
    // DATA STRUCTURES
    // =========================================================================

    /**
     * Result for a single algorithm run (one iteration).
     */
    private static class AlgorithmRunResult {
        final int algorithmId;
        final String algorithmName;
        final int iteration;
        final long seed;
        final List<double[]> solutions;  // Each: [makespan, energy]
        final long executionTimeMs;
        final boolean nonDominanceVerified;
        final Exception error;

        AlgorithmRunResult(int algorithmId, String algorithmName, int iteration, long seed,
                          List<double[]> solutions, long executionTimeMs, boolean nonDominanceVerified) {
            this.algorithmId = algorithmId;
            this.algorithmName = algorithmName;
            this.iteration = iteration;
            this.seed = seed;
            this.solutions = solutions;
            this.executionTimeMs = executionTimeMs;
            this.nonDominanceVerified = nonDominanceVerified;
            this.error = null;
        }

        AlgorithmRunResult(int algorithmId, String algorithmName, int iteration, long seed, Exception error) {
            this.algorithmId = algorithmId;
            this.algorithmName = algorithmName;
            this.iteration = iteration;
            this.seed = seed;
            this.solutions = new ArrayList<>();
            this.executionTimeMs = 0;
            this.nonDominanceVerified = false;
            this.error = error;
        }
    }

    /**
     * Aggregated results for one algorithm across all iterations.
     */
    private static class AlgorithmAggregatedResult {
        final int algorithmId;
        final String algorithmName;
        final List<AlgorithmRunResult> runs = new ArrayList<>();
        final List<double[]> allSolutions = new ArrayList<>();  // Combined from all iterations
        List<double[]> nonDominatedSolutions = new ArrayList<>();  // Filtered to non-dominated only

        // Performance metrics (calculated against Universal Pareto)
        double hv;
        double gd;
        double igd;
        double spacing;
        boolean nonDominanceVerified;
        int paretoContribution;  // Number of solutions contributed to Universal Pareto

        AlgorithmAggregatedResult(int algorithmId, String algorithmName) {
            this.algorithmId = algorithmId;
            this.algorithmName = algorithmName;
        }
    }

    /**
     * Results for one config file experiment.
     */
    private static class ConfigExperimentResults {
        final String configFileName;
        final long baseSeed;
        final int configNumber;
        final Map<Integer, AlgorithmAggregatedResult> algorithmResults = new HashMap<>();
        List<double[]> universalParetoSet = new ArrayList<>();
        double universalHV;

        ConfigExperimentResults(String configFileName, long baseSeed, int configNumber) {
            this.configFileName = configFileName;
            this.baseSeed = baseSeed;
            this.configNumber = configNumber;
        }
    }
}
