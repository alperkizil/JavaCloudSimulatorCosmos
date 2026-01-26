package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.AlgorithmStatistics;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Local Search algorithm for task scheduling optimization.
 *
 * This algorithm implements the classic iterative improvement local search
 * metaheuristic as described in:
 * El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 *
 * Algorithm pseudocode:
 * <pre>
 * s = s0;                              // Generate initial solution
 * While not Termination Criterion Do
 *     Generate N(s);                   // Generate neighborhood
 *     If there is no better neighbor Then Stop;  // Local optimum
 *     s = s';                          // Select better neighbor s' ∈ N(s)
 * Endwhile
 * Output: Final solution found (local optima)
 * </pre>
 *
 * Key features:
 * - Swappable neighbor selection strategies (Best Improvement, First Improvement, Random)
 * - Single objective or weighted-sum multi-objective optimization
 * - Comprehensive statistics tracking
 * - Can accept external initial solution (useful for hybrid algorithms)
 */
public class LocalSearchAlgorithm {

    private final LocalSearchConfiguration config;
    private final List<Task> tasks;
    private final List<VM> vms;

    // Operators
    private final RepairOperator repairOperator;
    private final NeighborhoodGenerator neighborhoodGenerator;
    private final NeighborSelectionStrategy selectionStrategy;

    // Random generator (from simulator)
    private final RandomGenerator random;

    // Statistics tracking
    private final LocalSearchStatistics statistics;

    // Current state
    private SchedulingSolution currentSolution;
    private SchedulingSolution bestSolution;
    private double currentFitness;
    private double bestFitness;

    // External initial solution (optional)
    private SchedulingSolution providedInitialSolution;

    /**
     * Creates a new Local Search algorithm.
     *
     * @param config Configuration for the algorithm
     * @param tasks  Tasks to schedule
     * @param vms    VMs to assign tasks to
     */
    public LocalSearchAlgorithm(LocalSearchConfiguration config, List<Task> tasks, List<VM> vms) {
        this.config = config;
        this.tasks = new ArrayList<>(tasks);
        this.vms = new ArrayList<>(vms);

        // Use the simulator's random generator for reproducibility
        this.random = RandomGenerator.getInstance();

        // Initialize repair operator
        this.repairOperator = new RepairOperator(tasks, vms, new java.util.Random(random.getSeed()));

        // Initialize neighborhood generator
        this.neighborhoodGenerator = new DefaultNeighborhoodGenerator(
            repairOperator,
            config.getNeighborhoodType(),
            vms.size(),
            random
        );

        // Get selection strategy from config
        this.selectionStrategy = config.getNeighborSelectionStrategy();

        // Initialize statistics
        this.statistics = new LocalSearchStatistics(config.isVerboseLogging());
        if (config.isVerboseLogging()) {
            this.statistics.setOutputFormat(LocalSearchStatistics.OutputFormat.DEFAULT);
        }
    }

    /**
     * Sets an external initial solution.
     * Use this for hybrid algorithms that want to improve an existing solution.
     *
     * @param initialSolution Solution to start from
     */
    public void setInitialSolution(SchedulingSolution initialSolution) {
        this.providedInitialSolution = initialSolution.copy();
    }

    /**
     * Runs the Local Search algorithm and returns the best solution (local optimum).
     *
     * @return Best solution found (local optimum)
     */
    public SchedulingSolution run() {
        if (tasks.isEmpty() || vms.isEmpty()) {
            System.err.println("[LS] No tasks or VMs to optimize");
            return null;
        }

        // Check feasibility
        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[LS] Problem is infeasible: some tasks have no valid VMs");
            List<Integer> infeasible = repairOperator.getInfeasibleTasks();
            System.err.println("[LS] Infeasible task indices: " + infeasible);
            return null;
        }

        // Log configuration
        if (config.isVerboseLogging()) {
            System.out.println("\n=== Local Search Starting ===");
            System.out.println(config);
            System.out.println("Tasks: " + tasks.size() + ", VMs: " + vms.size());
            System.out.println("Neighborhood size estimate: " + estimateNeighborhoodSize());
            System.out.println();
        }

        // Initialize statistics
        statistics.reset();
        statistics.startTimer();

        // Create algorithm statistics for termination condition (if provided)
        AlgorithmStatistics algoStats = new AlgorithmStatistics(config.getNumObjectives());

        // Step 1: Generate initial solution (s = s0)
        if (providedInitialSolution != null) {
            currentSolution = providedInitialSolution.copy();
            // Ensure it's valid
            repairOperator.repair(currentSolution);
        } else {
            currentSolution = generateInitialSolution();
        }

        currentFitness = evaluateFitness(currentSolution);
        statistics.setInitialSolution(currentSolution, currentFitness);

        // Keep track of best solution
        bestSolution = currentSolution.copy();
        bestFitness = currentFitness;

        if (config.isVerboseLogging()) {
            System.out.println("Initial fitness: " + currentFitness);
            System.out.println();
        }

        // Create evaluator function for selection strategy
        ToDoubleFunction<SchedulingSolution> evaluator = this::evaluateFitness;

        // Step 2: Main local search loop
        int iteration = 0;
        boolean localOptimumReached = false;

        while (!shouldTerminate(iteration, algoStats, localOptimumReached)) {
            iteration++;

            // Generate neighborhood and select neighbor using the strategy
            Optional<SchedulingSolution> selectedNeighbor = selectionStrategy.selectNeighbor(
                currentSolution,
                neighborhoodGenerator,
                evaluator,
                isMinimization()
            );

            int neighborsEvaluated = selectionStrategy.getLastEvaluationCount();
            boolean improved = false;

            if (selectedNeighbor.isPresent()) {
                // Move to the selected neighbor (s = s')
                currentSolution = selectedNeighbor.get();
                currentFitness = evaluateFitness(currentSolution);
                improved = true;

                // Update best if this is a new best
                if (isBetter(currentFitness, bestFitness)) {
                    bestSolution = currentSolution.copy();
                    bestFitness = currentFitness;
                }
            } else {
                // No improving neighbor found - local optimum reached
                localOptimumReached = true;
            }

            // Update statistics
            statistics.updateIteration(iteration, currentSolution, currentFitness,
                neighborsEvaluated, improved, isMinimization());

            // Update algorithm statistics for termination condition
            algoStats.setCurrentGeneration(iteration);
            algoStats.setTotalFitnessEvaluations(statistics.getTotalFitnessEvaluations());
            algoStats.setElapsedTimeMillis(statistics.getElapsedTimeMillis());
            algoStats.setBestObjectiveValues(new double[]{bestFitness});

            // Log progress
            if (config.isVerboseLogging() && iteration % config.getLogInterval() == 0) {
                System.out.println(statistics.formatCurrentIteration());
            }
        }

        // Set termination reason
        if (localOptimumReached) {
            statistics.setTerminationReason(LocalSearchStatistics.TerminationReason.LOCAL_OPTIMUM);
        } else if (iteration >= config.getMaxIterations()) {
            statistics.setTerminationReason(LocalSearchStatistics.TerminationReason.MAX_ITERATIONS);
        } else if (statistics.getNoImprovementIterations() >= config.getMaxIterationsWithoutImprovement()) {
            statistics.setTerminationReason(LocalSearchStatistics.TerminationReason.MAX_NO_IMPROVEMENT);
        } else if (config.getTerminationCondition() != null) {
            statistics.setTerminationReason(LocalSearchStatistics.TerminationReason.TERMINATION_CONDITION);
        }

        // Final log
        if (config.isVerboseLogging()) {
            System.out.println("\n=== Local Search Completed ===");
            System.out.println(statistics);
        }

        return bestSolution;
    }

    /**
     * Checks if the algorithm should terminate.
     */
    private boolean shouldTerminate(int iteration, AlgorithmStatistics algoStats, boolean localOptimumReached) {
        // Always stop if local optimum is reached
        if (localOptimumReached) {
            return true;
        }

        // Check max iterations
        if (iteration >= config.getMaxIterations()) {
            return true;
        }

        // Check max iterations without improvement
        if (statistics.getNoImprovementIterations() >= config.getMaxIterationsWithoutImprovement()) {
            return true;
        }

        // Check custom termination condition
        if (config.getTerminationCondition() != null &&
            config.getTerminationCondition().shouldTerminate(algoStats)) {
            return true;
        }

        return false;
    }

    /**
     * Estimates the neighborhood size for logging.
     */
    private int estimateNeighborhoodSize() {
        // Create a dummy solution to estimate
        SchedulingSolution dummy = new SchedulingSolution(tasks.size(), vms.size(), config.getNumObjectives());
        for (int i = 0; i < tasks.size(); i++) {
            List<Integer> validVms = repairOperator.getValidVmsForTask(i);
            if (!validVms.isEmpty()) {
                dummy.setAssignedVM(i, validVms.get(0));
            }
        }
        dummy.rebuildTaskOrdering();
        return neighborhoodGenerator.getNeighborhoodSize(dummy);
    }

    /**
     * Generates the initial solution randomly.
     *
     * @return Initial solution
     */
    private SchedulingSolution generateInitialSolution() {
        int numTasks = tasks.size();
        int numVMs = vms.size();
        int numObjectives = config.getNumObjectives();

        SchedulingSolution solution = new SchedulingSolution(numTasks, numVMs, numObjectives);

        // Random assignment respecting constraints
        for (int taskIdx = 0; taskIdx < numTasks; taskIdx++) {
            List<Integer> validVms = repairOperator.getValidVmsForTask(taskIdx);
            if (!validVms.isEmpty()) {
                int vmIdx = validVms.get(random.nextInt(validVms.size()));
                solution.setAssignedVM(taskIdx, vmIdx);
            }
        }

        solution.rebuildTaskOrdering();
        return solution;
    }

    /**
     * Evaluates the fitness of a solution.
     *
     * @param solution Solution to evaluate
     * @return Fitness value (single objective or weighted sum)
     */
    private double evaluateFitness(SchedulingSolution solution) {
        List<SchedulingObjective> objectives = config.getObjectives();

        if (objectives.size() == 1) {
            // Single objective
            SchedulingObjective objective = objectives.get(0);
            double value = objective.evaluate(solution, tasks, vms);
            solution.setObjectiveValue(0, value);
            return value;
        }

        // Weighted sum multi-objective
        double weightedSum = 0.0;
        Map<SchedulingObjective, Double> weights = config.getObjectiveWeights();

        for (int i = 0; i < objectives.size(); i++) {
            SchedulingObjective objective = objectives.get(i);
            double value = objective.evaluate(solution, tasks, vms);
            solution.setObjectiveValue(i, value);

            double weight = weights.getOrDefault(objective, 1.0);

            // Handle minimization vs maximization
            // For weighted sum, we want to minimize the combined value
            if (objective.isMinimization()) {
                weightedSum += weight * value;
            } else {
                weightedSum -= weight * value;
            }
        }

        return weightedSum;
    }

    /**
     * Checks if the optimization is minimization.
     *
     * @return true if minimizing (lower is better)
     */
    private boolean isMinimization() {
        // For weighted sum, we always minimize the weighted sum
        if (config.isWeightedSum()) {
            return true;
        }

        // For single objective, use the objective's direction
        return config.getPrimaryObjective().isMinimization();
    }

    /**
     * Checks if fitness1 is better than fitness2.
     *
     * @param fitness1 First fitness value
     * @param fitness2 Second fitness value
     * @return true if fitness1 is better
     */
    private boolean isBetter(double fitness1, double fitness2) {
        if (isMinimization()) {
            return fitness1 < fitness2;
        } else {
            return fitness1 > fitness2;
        }
    }

    /**
     * Gets the statistics for this algorithm run.
     *
     * @return Statistics object
     */
    public LocalSearchStatistics getStatistics() {
        return statistics;
    }

    /**
     * Gets the current solution.
     *
     * @return Current solution
     */
    public SchedulingSolution getCurrentSolution() {
        return currentSolution;
    }

    /**
     * Gets the best solution found (local optimum).
     *
     * @return Best solution
     */
    public SchedulingSolution getBestSolution() {
        return bestSolution;
    }

    /**
     * Gets the configuration.
     *
     * @return Configuration
     */
    public LocalSearchConfiguration getConfig() {
        return config;
    }

    /**
     * Gets the repair operator (useful for hybrid algorithms).
     *
     * @return Repair operator
     */
    public RepairOperator getRepairOperator() {
        return repairOperator;
    }

    /**
     * Gets the neighborhood generator (useful for hybrid algorithms).
     *
     * @return Neighborhood generator
     */
    public NeighborhoodGenerator getNeighborhoodGenerator() {
        return neighborhoodGenerator;
    }
}
