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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Flexible Experiment Main - Easy-to-configure cloud simulation runner.
 *
 * This file is designed for easy experimentation. All configuration is done
 * through simple constants at the top of the file - no command line arguments needed.
 *
 * QUICK START:
 * 1. Set STRATEGY (1-8) to choose your algorithm
 * 2. Set MULTI_OBJECTIVE_MODE to true/false
 * 3. Adjust weights if using weighted-sum mode
 * 4. Run!
 *
 * ============================================================================
 * STRATEGY GUIDE:
 * ============================================================================
 *
 * HEURISTICS (fast, simple):
 *   1 = First Available    - Assigns to first compatible VM (baseline) [DEFAULT]
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
 *
 * ============================================================================
 * TERMINATION CRITERIA (40,000 fitness evaluations for fair comparison):
 * ============================================================================
 *
 *   GA:           200 population × 200 generations = 40,000 evaluations
 *   SA:           40,000 total fitness evaluations
 *   Local Search: 40,000 max iterations OR 200 iterations without improvement
 *   MOEA_NSGAII:  40,000 max evaluations
 *   MOEA_SPEA2:   40,000 max evaluations
 *
 * ============================================================================
 * OBJECTIVE MODES:
 * ============================================================================
 *
 * When MULTI_OBJECTIVE_MODE = false (single objective):
 *   - For strategies 4-6: Uses the objective with higher weight
 *   - Strategies 7-8 (MOEA): NOT AVAILABLE - will fallback to First Available
 *
 * When MULTI_OBJECTIVE_MODE = true (multi-objective):
 *   - For strategies 4-6: Uses weighted sum of objectives
 *   - For strategies 7-8 (MOEA): Returns Pareto front, selects knee point
 *
 * ============================================================================
 * GENETIC OPERATORS:
 * ============================================================================
 *
 * All evolutionary algorithms (GA, MOEA_NSGAII, MOEA_SPEA2) use identical operators:
 *   - Crossover: UNIFORM crossover
 *   - Mutation:  COMBINED (REASSIGN + SWAP_ORDER)
 *   - Repair:    Constraint satisfaction via RepairOperator
 *
 * ============================================================================
 */
public class FlexibleExperimentMain {

    // =========================================================================
    // USER CONFIGURATION - CHANGE THESE VALUES
    // =========================================================================

    /**
     * Strategy selection (see STRATEGY GUIDE above):
     *   1 = First Available (heuristic) [DEFAULT]
     *   2 = Shortest Queue (heuristic)
     *   3 = Workload Aware (heuristic)
     *   4 = Genetic Algorithm (GA)
     *   5 = Simulated Annealing (SA)
     *   6 = Local Search (LS)
     *   7 = MOEA_NSGAII (multi-objective only)
     *   8 = MOEA_SPEA2 (multi-objective only)
     */
    private static final int STRATEGY = 1;

    /**
     * Multi-objective mode:
     *   false = Single objective (uses objective with higher weight)
     *           NOTE: Strategies 7-8 (MOEA) require multi-objective mode!
     *   true  = Multi-objective (weighted sum for GA/SA/LS, Pareto front for MOEA)
     */
    private static final boolean MULTI_OBJECTIVE_MODE = false;

    /**
     * Objective weights (used for weighted-sum or to select primary objective)
     * These are automatically normalized, so (0.7, 0.3) is same as (70, 30)
     */
    private static final double WEIGHT_MAKESPAN = 0.7;
    private static final double WEIGHT_ENERGY = 0.3;

    /**
     * Number of experiment files to run (max 10)
     */
    private static final int MAX_EXPERIMENTS = 2;

    /**
     * Enable verbose logging for metaheuristics
     */
    private static final boolean VERBOSE_LOGGING = true;

    // =========================================================================
    // UNIFIED ALGORITHM PARAMETERS (40,000 evaluations for fair comparison)
    // =========================================================================

    /**
     * Total fitness evaluations budget - ensures fair comparison across algorithms.
     * All metaheuristic algorithms terminate after approximately this many evaluations.
     */
    private static final int TOTAL_EVALUATIONS = 40000;

    /**
     * Population size for population-based algorithms (GA, MOEA).
     * With 200 generations: 200 × 200 = 40,000 evaluations.
     */
    private static final int POPULATION_SIZE = 200;

    /**
     * Number of generations for GA and MOEA algorithms.
     * With population 200: 200 × 200 = 40,000 evaluations.
     */
    private static final int GENERATIONS = 200;

    /**
     * Crossover rate - probability of applying crossover operator.
     * Used by: GA, MOEA_NSGAII, MOEA_SPEA2
     */
    private static final double CROSSOVER_RATE = 0.9;

    /**
     * Mutation rate - per-gene probability of mutation.
     * Used by: GA, SA, MOEA_NSGAII, MOEA_SPEA2
     */
    private static final double MUTATION_RATE = 0.1;

    // =========================================================================
    // GA-SPECIFIC PARAMETERS
    // =========================================================================

    /** Number of elite individuals preserved each generation (10% of population) */
    private static final int GA_ELITE_COUNT = 20;

    /** Tournament size for selection */
    private static final int GA_TOURNAMENT_SIZE = 3;

    // =========================================================================
    // SA-SPECIFIC PARAMETERS
    // =========================================================================

    /** Initial temperature for SA (used with auto-termination) */
    private static final double SA_INITIAL_TEMPERATURE = 1000.0;

    /** Cooling rate for geometric cooling schedule */
    private static final double SA_COOLING_RATE = 0.95;

    // =========================================================================
    // LOCAL SEARCH PARAMETERS
    // =========================================================================

    /** Maximum iterations (40,000 for fair comparison) */
    private static final int LS_MAX_ITERATIONS = 40000;

    /** Stop after this many iterations without improvement */
    private static final int LS_MAX_NO_IMPROVEMENT = 200;

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
        System.out.println("  Flexible Experiment Runner");
        System.out.println("========================================================");
        System.out.println();
        printConfiguration();
        System.out.println();

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

        System.out.println("Running " + configFiles.size() + " experiment(s):");
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

    // =========================================================================
    // CONFIGURATION DISPLAY
    // =========================================================================

    private static void printConfiguration() {
        System.out.println("Current Configuration:");
        System.out.println("  Strategy: " + getStrategyName(STRATEGY) + " (" + STRATEGY + ")");
        System.out.println("  Multi-objective Mode: " + MULTI_OBJECTIVE_MODE);
        System.out.println("  Weights: Makespan=" + WEIGHT_MAKESPAN + ", Energy=" + WEIGHT_ENERGY);
        System.out.println("  Max Experiments: " + MAX_EXPERIMENTS);
        System.out.println("  Verbose Logging: " + VERBOSE_LOGGING);
    }

    private static String getStrategyName(int strategy) {
        switch (strategy) {
            case 1: return "First Available (Heuristic)";
            case 2: return "Shortest Queue (Heuristic)";
            case 3: return "Workload Aware (Heuristic)";
            case 4: return "Genetic Algorithm (GA)";
            case 5: return "Simulated Annealing (SA)";
            case 6: return "Local Search (LS)";
            case 7: return "MOEA_NSGAII (Multi-objective)";
            case 8: return "MOEA_SPEA2 (Multi-objective)";
            default: return "Unknown";
        }
    }

    // =========================================================================
    // STRATEGY FACTORY METHODS
    // =========================================================================

    /**
     * Creates a task assignment strategy based on STRATEGY constant.
     *
     * @param hosts List of hosts (needed for energy objective)
     * @param tasks List of tasks (needed for MOEA custom operators)
     * @param vms   List of VMs (needed for MOEA custom operators)
     * @return Configured TaskAssignmentStrategy
     */
    private static TaskAssignmentStrategy createStrategy(List<Host> hosts, List<Task> tasks, List<VM> vms) {
        // Validate MOEA strategies require multi-objective mode
        if ((STRATEGY == 7 || STRATEGY == 8) && !MULTI_OBJECTIVE_MODE) {
            System.err.println("========================================================");
            System.err.println("  ERROR: MOEA strategies (7, 8) require MULTI_OBJECTIVE_MODE = true");
            System.err.println("  Strategy " + STRATEGY + " (" + getStrategyName(STRATEGY) + ") is multi-objective only.");
            System.err.println("  Falling back to First Available strategy.");
            System.err.println("========================================================");
            return createFirstAvailableStrategy();
        }

        switch (STRATEGY) {
            case 1:
                return createFirstAvailableStrategy();
            case 2:
                return createShortestQueueStrategy();
            case 3:
                return createWorkloadAwareStrategy();
            case 4:
                return createGAStrategy(hosts);
            case 5:
                return createSAStrategy(hosts);
            case 6:
                return createLocalSearchStrategy(hosts);
            case 7:
                return createMOEA_NSGAIIStrategy(hosts, tasks, vms);
            case 8:
                return createMOEA_SPEA2Strategy(hosts, tasks, vms);
            default:
                System.err.println("Unknown strategy: " + STRATEGY + ", using First Available");
                return createFirstAvailableStrategy();
        }
    }

    /**
     * Creates a task assignment strategy based on STRATEGY constant.
     * Overload for backward compatibility - delegates to full version.
     *
     * @param hosts List of hosts (needed for energy objective)
     * @return Configured TaskAssignmentStrategy
     */
    private static TaskAssignmentStrategy createStrategy(List<Host> hosts) {
        // For strategies that don't need tasks/vms, pass empty lists
        return createStrategy(hosts, new ArrayList<>(), new ArrayList<>());
    }

    // -------------------------------------------------------------------------
    // Heuristic Strategies
    // -------------------------------------------------------------------------

    private static TaskAssignmentStrategy createFirstAvailableStrategy() {
        return new FirstAvailableTaskAssignmentStrategy();
    }

    private static TaskAssignmentStrategy createShortestQueueStrategy() {
        return new ShortestQueueTaskAssignmentStrategy();
    }

    private static TaskAssignmentStrategy createWorkloadAwareStrategy() {
        return new WorkloadAwareTaskAssignmentStrategy();
    }

    // -------------------------------------------------------------------------
    // GA Configuration
    // -------------------------------------------------------------------------

    private static TaskAssignmentStrategy createGAStrategy(List<Host> hosts) {
        GAConfiguration config = createGAConfiguration(hosts);
        return new GenerationalGATaskSchedulingStrategy(config);
    }

    /**
     * Creates GA configuration based on current settings.
     *
     * In single-objective mode: uses the objective with higher weight.
     * In multi-objective mode: uses weighted sum of both objectives.
     *
     * Uses unified parameters: POPULATION_SIZE × GENERATIONS = 40,000 evaluations.
     */
    private static GAConfiguration createGAConfiguration(List<Host> hosts) {
        GAConfiguration.Builder builder = GAConfiguration.builder()
            .populationSize(POPULATION_SIZE)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(MUTATION_RATE)
            .eliteCount(GA_ELITE_COUNT)
            .tournamentSize(GA_TOURNAMENT_SIZE)
            .terminationCondition(new GenerationCountTermination(GENERATIONS))
            .verboseLogging(VERBOSE_LOGGING);

        if (MULTI_OBJECTIVE_MODE) {
            // Weighted sum of objectives
            MakespanObjective makespan = new MakespanObjective();
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);

            builder.addWeightedObjective(makespan, WEIGHT_MAKESPAN);
            builder.addWeightedObjective(energy, WEIGHT_ENERGY);
        } else {
            // Single objective - use the one with higher weight
            SchedulingObjective objective = createPrimaryObjective(hosts);
            builder.objective(objective);
        }

        return builder.build();
    }

    /**
     * Creates a custom GA configuration with specific parameters.
     */
    public static GAConfiguration createGAConfigurationCustom(
            List<Host> hosts,
            int populationSize,
            int generations,
            double crossoverRate,
            double mutationRate,
            int eliteCount,
            int tournamentSize,
            double makespanWeight,
            double energyWeight,
            boolean multiObjective) {

        GAConfiguration.Builder builder = GAConfiguration.builder()
            .populationSize(populationSize)
            .crossoverRate(crossoverRate)
            .mutationRate(mutationRate)
            .eliteCount(eliteCount)
            .tournamentSize(tournamentSize)
            .terminationCondition(new GenerationCountTermination(generations))
            .verboseLogging(VERBOSE_LOGGING);

        if (multiObjective) {
            MakespanObjective makespan = new MakespanObjective();
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);
            builder.addWeightedObjective(makespan, makespanWeight);
            builder.addWeightedObjective(energy, energyWeight);
        } else {
            if (makespanWeight >= energyWeight) {
                builder.objective(new MakespanObjective());
            } else {
                EnergyObjective energy = new EnergyObjective();
                energy.setHosts(hosts);
                builder.objective(energy);
            }
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // SA Configuration
    // -------------------------------------------------------------------------

    private static TaskAssignmentStrategy createSAStrategy(List<Host> hosts) {
        SAConfiguration config = createSAConfiguration(hosts);
        return new SimulatedAnnealingTaskSchedulingStrategy(config);
    }

    /**
     * Creates SA configuration based on current settings.
     * Uses evaluation-count-based termination (TOTAL_EVALUATIONS) for fair comparison.
     * Geometric cooling schedule with automatic temperature steps.
     */
    private static SAConfiguration createSAConfiguration(List<Host> hosts) {
        SAConfiguration.Builder builder = SAConfiguration.builder()
            .initialTemperature(SA_INITIAL_TEMPERATURE)
            .coolingSchedule(new GeometricCoolingSchedule(SA_COOLING_RATE))
            .terminationCondition(new FitnessEvaluationsTermination(TOTAL_EVALUATIONS))
            .verboseLogging(VERBOSE_LOGGING);

        if (MULTI_OBJECTIVE_MODE) {
            // Weighted sum of objectives
            MakespanObjective makespan = new MakespanObjective();
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);

            builder.addWeightedObjective(makespan, WEIGHT_MAKESPAN);
            builder.addWeightedObjective(energy, WEIGHT_ENERGY);
        } else {
            // Single objective
            SchedulingObjective objective = createPrimaryObjective(hosts);
            builder.objective(objective);
        }

        return builder.build();
    }

    /**
     * Creates a custom SA configuration with evaluation-count termination.
     *
     * @param hosts           List of hosts (for energy objective)
     * @param initialTemp     Initial temperature
     * @param coolingRate     Cooling rate for geometric schedule
     * @param maxEvaluations  Total fitness evaluations (termination criterion)
     * @param makespanWeight  Weight for makespan objective
     * @param energyWeight    Weight for energy objective
     * @param multiObjective  true for weighted-sum, false for single objective
     * @return SAConfiguration
     */
    public static SAConfiguration createSAConfigurationCustom(
            List<Host> hosts,
            double initialTemp,
            double coolingRate,
            int maxEvaluations,
            double makespanWeight,
            double energyWeight,
            boolean multiObjective) {

        SAConfiguration.Builder builder = SAConfiguration.builder()
            .initialTemperature(initialTemp)
            .coolingSchedule(new GeometricCoolingSchedule(coolingRate))
            .terminationCondition(new FitnessEvaluationsTermination(maxEvaluations))
            .verboseLogging(VERBOSE_LOGGING);

        if (multiObjective) {
            MakespanObjective makespan = new MakespanObjective();
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);
            builder.addWeightedObjective(makespan, makespanWeight);
            builder.addWeightedObjective(energy, energyWeight);
        } else {
            if (makespanWeight >= energyWeight) {
                builder.objective(new MakespanObjective());
            } else {
                EnergyObjective energy = new EnergyObjective();
                energy.setHosts(hosts);
                builder.objective(energy);
            }
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Local Search Configuration
    // -------------------------------------------------------------------------

    private static TaskAssignmentStrategy createLocalSearchStrategy(List<Host> hosts) {
        LocalSearchConfiguration config = createLocalSearchConfiguration(hosts);
        return new LocalSearchTaskSchedulingStrategy(config);
    }

    /**
     * Creates Local Search configuration based on current settings.
     * Uses First Improvement strategy (good balance of speed and quality).
     */
    private static LocalSearchConfiguration createLocalSearchConfiguration(List<Host> hosts) {
        LocalSearchConfiguration.Builder builder = LocalSearchConfiguration.builder()
            .neighborSelectionStrategy(new FirstImprovementStrategy())
            .neighborhoodType(NeighborhoodGenerator.NeighborhoodType.COMBINED)
            .maxIterations(LS_MAX_ITERATIONS)
            .maxIterationsWithoutImprovement(LS_MAX_NO_IMPROVEMENT)
            .verboseLogging(VERBOSE_LOGGING);

        if (MULTI_OBJECTIVE_MODE) {
            // Weighted sum of objectives
            MakespanObjective makespan = new MakespanObjective();
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);

            builder.addWeightedObjective(makespan, WEIGHT_MAKESPAN);
            builder.addWeightedObjective(energy, WEIGHT_ENERGY);
        } else {
            // Single objective
            SchedulingObjective objective = createPrimaryObjective(hosts);
            builder.objective(objective);
        }

        return builder.build();
    }

    /**
     * Creates a custom Local Search configuration with specific parameters.
     */
    public static LocalSearchConfiguration createLocalSearchConfigurationCustom(
            List<Host> hosts,
            int maxIterations,
            int maxNoImprovement,
            double makespanWeight,
            double energyWeight,
            boolean multiObjective) {

        LocalSearchConfiguration.Builder builder = LocalSearchConfiguration.builder()
            .neighborSelectionStrategy(new FirstImprovementStrategy())
            .neighborhoodType(NeighborhoodGenerator.NeighborhoodType.COMBINED)
            .maxIterations(maxIterations)
            .maxIterationsWithoutImprovement(maxNoImprovement)
            .verboseLogging(VERBOSE_LOGGING);

        if (multiObjective) {
            MakespanObjective makespan = new MakespanObjective();
            EnergyObjective energy = new EnergyObjective();
            energy.setHosts(hosts);
            builder.addWeightedObjective(makespan, makespanWeight);
            builder.addWeightedObjective(energy, energyWeight);
        } else {
            if (makespanWeight >= energyWeight) {
                builder.objective(new MakespanObjective());
            } else {
                EnergyObjective energy = new EnergyObjective();
                energy.setHosts(hosts);
                builder.objective(energy);
            }
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // MOEA_NSGAII Configuration
    // -------------------------------------------------------------------------

    /**
     * Creates MOEA Framework NSGA-II strategy with custom genetic operators.
     * Uses the same CrossoverOperator and MutationOperator as the native GA
     * to ensure fair comparison between algorithms.
     *
     * @param hosts List of hosts (needed for energy objective)
     * @param tasks List of tasks (needed for custom operators)
     * @param vms   List of VMs (needed for custom operators)
     * @return Configured MOEA_NSGA2TaskSchedulingStrategy
     */
    private static TaskAssignmentStrategy createMOEA_NSGAIIStrategy(List<Host> hosts, List<Task> tasks, List<VM> vms) {
        NSGA2Configuration config = createMOEAConfiguration(hosts);
        MOEA_NSGA2TaskSchedulingStrategy strategy = new MOEA_NSGA2TaskSchedulingStrategy(config);

        // Set selection method based on weights
        if (WEIGHT_MAKESPAN > WEIGHT_ENERGY) {
            strategy.setSelectionWeights(new double[]{WEIGHT_MAKESPAN, WEIGHT_ENERGY});
        } else {
            strategy.setSelectionWeights(new double[]{WEIGHT_MAKESPAN, WEIGHT_ENERGY});
        }
        strategy.setSelectionMethod(MOEA_NSGA2TaskSchedulingStrategy.SolutionSelectionMethod.WEIGHTED_SUM);

        return strategy;
    }

    // -------------------------------------------------------------------------
    // MOEA_SPEA2 Configuration
    // -------------------------------------------------------------------------

    /**
     * Creates MOEA Framework SPEA2 strategy with custom genetic operators.
     * Uses the same CrossoverOperator and MutationOperator as the native GA
     * to ensure fair comparison between algorithms.
     *
     * @param hosts List of hosts (needed for energy objective)
     * @param tasks List of tasks (needed for custom operators)
     * @param vms   List of VMs (needed for custom operators)
     * @return Configured MOEA_SPEA2TaskSchedulingStrategy
     */
    private static TaskAssignmentStrategy createMOEA_SPEA2Strategy(List<Host> hosts, List<Task> tasks, List<VM> vms) {
        NSGA2Configuration config = createMOEAConfiguration(hosts);
        MOEA_SPEA2TaskSchedulingStrategy strategy = new MOEA_SPEA2TaskSchedulingStrategy(config);

        // Set selection method based on weights
        strategy.setSelectionWeights(new double[]{WEIGHT_MAKESPAN, WEIGHT_ENERGY});
        strategy.setSelectionMethod(MOEA_SPEA2TaskSchedulingStrategy.SolutionSelectionMethod.WEIGHTED_SUM);

        return strategy;
    }

    /**
     * Creates shared MOEA configuration for both NSGA-II and SPEA2.
     * Uses unified parameters: POPULATION_SIZE × GENERATIONS = TOTAL_EVALUATIONS.
     *
     * @param hosts List of hosts (needed for energy objective)
     * @return NSGA2Configuration (shared format for all MOEA algorithms)
     */
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

    /**
     * Creates a custom MOEA configuration with specific parameters.
     * Can be used for both MOEA_NSGAII and MOEA_SPEA2.
     *
     * @param hosts          List of hosts (for energy objective)
     * @param populationSize Population size
     * @param generations    Number of generations (total evals = pop × gen)
     * @param crossoverRate  Crossover probability
     * @param mutationRate   Mutation probability per gene
     * @return NSGA2Configuration
     */
    public static NSGA2Configuration createMOEAConfigurationCustom(
            List<Host> hosts,
            int populationSize,
            int generations,
            double crossoverRate,
            double mutationRate) {

        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);

        return NSGA2Configuration.builder()
            .populationSize(populationSize)
            .crossoverRate(crossoverRate)
            .mutationRate(mutationRate)
            .addObjective(makespan)
            .addObjective(energy)
            .terminationCondition(new GenerationCountTermination(generations))
            .verboseLogging(VERBOSE_LOGGING)
            .build();
    }

    // -------------------------------------------------------------------------
    // Pareto Front Display
    // -------------------------------------------------------------------------

    /**
     * Prints the full Pareto front for MOEA strategies.
     * Shows all non-dominated solutions with their objective values.
     *
     * @param strategy The task assignment strategy (must be MOEA_NSGA2 or MOEA_SPEA2)
     */
    private static void printParetoFront(TaskAssignmentStrategy strategy) {
        ParetoFront front = null;
        String algorithmName = "";

        if (strategy instanceof MOEA_NSGA2TaskSchedulingStrategy) {
            MOEA_NSGA2TaskSchedulingStrategy nsgaStrategy = (MOEA_NSGA2TaskSchedulingStrategy) strategy;
            front = nsgaStrategy.getLastParetoFront();
            algorithmName = "MOEA_NSGAII";
        } else if (strategy instanceof MOEA_SPEA2TaskSchedulingStrategy) {
            MOEA_SPEA2TaskSchedulingStrategy speaStrategy = (MOEA_SPEA2TaskSchedulingStrategy) strategy;
            front = speaStrategy.getLastParetoFront();
            algorithmName = "MOEA_SPEA2";
        }

        if (front == null || front.isEmpty()) {
            System.out.println("  [" + algorithmName + "] No Pareto front available");
            return;
        }

        System.out.println();
        System.out.println("  ========================================================");
        System.out.println("  " + algorithmName + " PARETO FRONT (" + front.size() + " non-dominated solutions)");
        System.out.println("  ========================================================");
        System.out.println();
        System.out.printf("  %-6s %-20s %-20s%n", "#", "Makespan (s)", "Energy (kWh)");
        System.out.println("  " + "-".repeat(50));

        List<SchedulingSolution> solutions = front.getSolutions();
        for (int i = 0; i < solutions.size(); i++) {
            SchedulingSolution sol = solutions.get(i);
            double[] objectives = sol.getObjectiveValues();
            if (objectives != null && objectives.length >= 2) {
                System.out.printf("  %-6d %-20.2f %-20.6f%n", (i + 1), objectives[0], objectives[1]);
            }
        }

        System.out.println("  " + "-".repeat(50));

        // Show special points
        SchedulingSolution bestMakespan = front.getBestForObjective(0);
        SchedulingSolution bestEnergy = front.getBestForObjective(1);
        SchedulingSolution kneePoint = front.getKneePoint();

        System.out.println();
        System.out.println("  Special Points:");
        if (bestMakespan != null && bestMakespan.getObjectiveValues() != null) {
            System.out.printf("    Best Makespan:  %.2f s, %.6f kWh%n",
                bestMakespan.getObjectiveValues()[0], bestMakespan.getObjectiveValues()[1]);
        }
        if (bestEnergy != null && bestEnergy.getObjectiveValues() != null) {
            System.out.printf("    Best Energy:    %.2f s, %.6f kWh%n",
                bestEnergy.getObjectiveValues()[0], bestEnergy.getObjectiveValues()[1]);
        }
        if (kneePoint != null && kneePoint.getObjectiveValues() != null) {
            System.out.printf("    Knee Point:     %.2f s, %.6f kWh%n",
                kneePoint.getObjectiveValues()[0], kneePoint.getObjectiveValues()[1]);
        }
        System.out.println("  ========================================================");
    }

    // -------------------------------------------------------------------------
    // Objective Helper
    // -------------------------------------------------------------------------

    /**
     * Creates the primary objective based on weights.
     * Returns the objective with higher weight.
     */
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
            // Sort by filename for consistent order
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
    // EXPERIMENT EXECUTION
    // =========================================================================

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

        // Create placement strategies
        PowerAwareLoadBalancingHostPlacementStrategy hostStrategy =
            new PowerAwareLoadBalancingHostPlacementStrategy();
        BestFitVMPlacementStrategy vmStrategy =
            new BestFitVMPlacementStrategy();

        System.out.println("Strategy Configuration:");
        System.out.println("  Host Placement: " + hostStrategy.getStrategyName());
        System.out.println("  VM Placement: " + vmStrategy.getStrategyName());
        System.out.println("  Task Assignment: " + getStrategyName(STRATEGY));
        System.out.println("  Multi-objective Mode: " + MULTI_OBJECTIVE_MODE);
        if (STRATEGY >= 4) {
            System.out.println("  Weights: Makespan=" + WEIGHT_MAKESPAN + ", Energy=" + WEIGHT_ENERGY);
        }
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

        // Create task assignment strategy (needs hosts for energy objective, tasks/vms for MOEA)
        TaskAssignmentStrategy taskStrategy = createStrategy(
            context.getHosts(),
            context.getTasks(),
            context.getVms()
        );

        System.out.println("--- Step 5: Task Assignment (" + getStrategyName(STRATEGY) + ") ---");
        TaskAssignmentStep taskAssignmentStep = new TaskAssignmentStep(taskStrategy);
        taskAssignmentStep.execute(context);
        System.out.println("  Tasks Assigned: " + taskAssignmentStep.getTasksAssigned());
        System.out.println("  Tasks Failed: " + taskAssignmentStep.getTasksFailed());

        // Print Pareto front for MOEA strategies
        if (STRATEGY == 7 || STRATEGY == 8) {
            printParetoFront(taskStrategy);
        }
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
        reportingStep.setCustomPrefix("flex_exp" + experimentNumber + "_s" + STRATEGY);
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

    // =========================================================================
    // OUTPUT FORMATTING
    // =========================================================================

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

    private static void printFinalSummary(List<ExperimentResult> results) {
        System.out.println();
        System.out.println("========================================================");
        System.out.println("  FINAL SUMMARY - ALL EXPERIMENTS");
        System.out.println("  Strategy: " + getStrategyName(STRATEGY));
        System.out.println("  Mode: " + (MULTI_OBJECTIVE_MODE ? "Multi-objective (weighted sum)" : "Single-objective"));
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

    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    // =========================================================================
    // RESULT CONTAINER
    // =========================================================================

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
