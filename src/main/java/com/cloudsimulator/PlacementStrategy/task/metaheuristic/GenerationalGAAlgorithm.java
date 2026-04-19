package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.CrossoverOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.selection.SelectionOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.selection.TournamentSelection;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.AlgorithmStatistics;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Generational Genetic Algorithm with Elitism for task scheduling optimization.
 *
 * This algorithm evolves a population of scheduling solutions using:
 * - Tournament selection for parent selection
 * - Configurable crossover (uniform, two-point, order-based)
 * - Mutation with repair for constraint handling
 * - Elitism to preserve best solutions across generations
 *
 * Key features:
 * - Single objective or weighted-sum multi-objective optimization
 * - Configurable elitism (absolute count or percentage)
 * - Uses simulator's RandomGenerator for reproducibility
 * - Comprehensive statistics tracking
 *
 * Algorithm flow:
 * 1. Initialize random population
 * 2. Evaluate all solutions
 * 3. WHILE termination condition not met:
 *    a. Select elite individuals (preserved unchanged)
 *    b. Select parents using tournament selection
 *    c. Apply crossover to create offspring
 *    d. Apply mutation to offspring
 *    e. Repair invalid solutions
 *    f. Evaluate offspring
 *    g. New population = elite + offspring
 *    h. Update statistics
 * 4. Return best solution
 */
public class GenerationalGAAlgorithm {

    private final GAConfiguration config;
    private final List<Task> tasks;
    private final List<VM> vms;

    // Operators
    private final SelectionOperator selectionOperator;
    private final CrossoverOperator crossoverOperator;
    private final MutationOperator mutationOperator;
    private final RepairOperator repairOperator;

    // Random generator (from simulator)
    private final RandomGenerator random;

    // Statistics tracking
    private final GAStatistics statistics;

    // Current population and fitness
    private List<SchedulingSolution> population;
    private double[] fitnessValues;

    // Non-dominated archive (multi-objective view for a single-objective search)
    private NonDominatedArchive archive;

    /**
     * Creates a new Generational GA algorithm.
     *
     * @param config Configuration for the algorithm
     * @param tasks  Tasks to schedule
     * @param vms    VMs to assign tasks to
     */
    public GenerationalGAAlgorithm(GAConfiguration config, List<Task> tasks, List<VM> vms) {
        this.config = config;
        this.tasks = new ArrayList<>(tasks);
        this.vms = new ArrayList<>(vms);

        // Use the simulator's random generator for reproducibility
        this.random = RandomGenerator.getInstance();

        // Initialize operators
        this.repairOperator = new RepairOperator(tasks, vms, new java.util.Random(random.getSeed()));
        this.selectionOperator = new TournamentSelection(config.getTournamentSize());
        this.crossoverOperator = new CrossoverOperator(
            config.getCrossoverType(),
            config.getNumObjectives(),
            new java.util.Random(random.getSeed())
        );
        this.mutationOperator = new MutationOperator(
            vms.size(),
            repairOperator,
            new java.util.Random(random.getSeed())
        );

        // Initialize statistics
        this.statistics = new GAStatistics(config.isVerboseLogging());
        if (config.isVerboseLogging()) {
            this.statistics.setOutputFormat(GAStatistics.OutputFormat.DEFAULT);
        }

        // Initialize population array
        this.population = new ArrayList<>();
        this.fitnessValues = new double[config.getPopulationSize()];
    }

    /**
     * Runs the genetic algorithm and returns the best solution.
     *
     * @return Best solution found
     */
    public SchedulingSolution run() {
        if (tasks.isEmpty() || vms.isEmpty()) {
            System.err.println("[GA] No tasks or VMs to optimize");
            return null;
        }

        // Check feasibility
        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[GA] Problem is infeasible: some tasks have no valid VMs");
            List<Integer> infeasible = repairOperator.getInfeasibleTasks();
            System.err.println("[GA] Infeasible task indices: " + infeasible);
            return null;
        }

        // Log configuration
        if (config.isVerboseLogging()) {
            System.out.println("\n=== Generational GA Starting ===");
            System.out.println(config);
            System.out.println("Tasks: " + tasks.size() + ", VMs: " + vms.size());
            System.out.println();
        }

        // Initialize statistics
        statistics.reset();
        statistics.startTimer();

        // Create algorithm statistics for termination condition
        AlgorithmStatistics algoStats = new AlgorithmStatistics(config.getNumObjectives());

        // Reset the non-dominated archive for this run
        archive = new NonDominatedArchive(buildMinimizationArray());

        // Step 1: Initialize population
        initializePopulation();

        // Step 2: Evaluate initial population
        evaluatePopulation();
        statistics.updateGeneration(0, population, fitnessValues, isMinimization());

        if (config.isVerboseLogging()) {
            System.out.println(statistics.formatCurrentGeneration());
        }

        // Step 3: Evolution loop
        int generation = 0;
        while (!config.getTerminationCondition().shouldTerminate(algoStats)) {
            generation++;

            // Update algorithm statistics for termination check
            algoStats.setCurrentGeneration(generation);
            algoStats.setTotalFitnessEvaluations(statistics.getTotalFitnessEvaluations());
            algoStats.setElapsedTimeMillis(statistics.getElapsedTimeMillis());

            // Evolve one generation
            evolveGeneration();

            // Evaluate new population
            evaluatePopulation();

            // Update statistics
            statistics.updateGeneration(generation, population, fitnessValues, isMinimization());

            // Log progress
            if (config.isVerboseLogging() && generation % config.getLogInterval() == 0) {
                System.out.println(statistics.formatCurrentGeneration());
            }

            // Update algorithm statistics with best fitness
            algoStats.setBestObjectiveValues(new double[]{statistics.getBestFitness()});
        }

        // Final log
        if (config.isVerboseLogging()) {
            System.out.println("\n=== GA Completed ===");
            System.out.println(statistics);
        }

        return statistics.getBestSolution();
    }

    /**
     * Initializes the population with injected heuristic seeds (if any) followed
     * by random solutions to fill the remaining slots.
     */
    private void initializePopulation() {
        population.clear();
        int popSize = config.getPopulationSize();
        int numTasks = tasks.size();
        int numVMs = vms.size();
        int numObjectives = config.getNumObjectives();

        // Inject heuristic seeds first so elitism preserves them across generations.
        int seedsInjected = 0;
        for (int[] seed : config.getSeedAssignments()) {
            if (population.size() >= popSize) break;
            if (seed == null || seed.length != numTasks) continue;

            SchedulingSolution solution = new SchedulingSolution(numTasks, numVMs, numObjectives);
            solution.setTaskAssignment(seed.clone());
            repairOperator.repair(solution);
            solution.rebuildTaskOrdering();
            population.add(solution);
            seedsInjected++;
        }

        if (seedsInjected > 0 && config.isVerboseLogging()) {
            System.out.println("[GA] Injected " + seedsInjected + " heuristic seed(s) into initial population");
        }

        // Fill remainder with random solutions.
        while (population.size() < popSize) {
            SchedulingSolution solution = new SchedulingSolution(numTasks, numVMs, numObjectives);

            for (int taskIdx = 0; taskIdx < numTasks; taskIdx++) {
                List<Integer> validVms = repairOperator.getValidVmsForTask(taskIdx);
                if (!validVms.isEmpty()) {
                    int vmIdx = validVms.get(random.nextInt(validVms.size()));
                    solution.setAssignedVM(taskIdx, vmIdx);
                }
            }

            solution.rebuildTaskOrdering();
            population.add(solution);
        }

        fitnessValues = new double[popSize];
    }

    /**
     * Evolves the population for one generation.
     */
    private void evolveGeneration() {
        int popSize = config.getPopulationSize();
        int eliteCount = config.getEliteCount();

        // Step 1: Select elite individuals
        List<SchedulingSolution> elite = selectElite(eliteCount);

        // Step 2: Create offspring to fill remaining slots
        int offspringNeeded = popSize - eliteCount;
        List<SchedulingSolution> offspring = new ArrayList<>(offspringNeeded);

        while (offspring.size() < offspringNeeded) {
            // Select two parents
            SchedulingSolution parent1 = selectionOperator.select(
                population, fitnessValues, isMinimization());
            SchedulingSolution parent2 = selectionOperator.select(
                population, fitnessValues, isMinimization());

            // Apply crossover
            SchedulingSolution[] children;
            if (random.nextDouble() < config.getCrossoverRate()) {
                children = crossoverOperator.crossover(parent1, parent2);
            } else {
                children = new SchedulingSolution[]{parent1.copy(), parent2.copy()};
            }

            // Apply mutation
            for (SchedulingSolution child : children) {
                mutationOperator.mutate(child, config.getMutationRate());
            }

            // Repair invalid solutions
            for (SchedulingSolution child : children) {
                repairOperator.repair(child);
            }

            // Add offspring (up to needed count)
            for (SchedulingSolution child : children) {
                if (offspring.size() < offspringNeeded) {
                    offspring.add(child);
                }
            }
        }

        // Step 3: Create new population = elite + offspring
        population.clear();
        population.addAll(elite);
        population.addAll(offspring);
    }

    /**
     * Selects the elite individuals (best fitness).
     *
     * @param count Number of elite individuals to select
     * @return List of elite individuals (copies)
     */
    private List<SchedulingSolution> selectElite(int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        // Create indices sorted by fitness
        Integer[] indices = new Integer[population.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        // Sort by fitness (best first)
        final boolean minimize = isMinimization();
        Arrays.sort(indices, (a, b) -> {
            if (minimize) {
                return Double.compare(fitnessValues[a], fitnessValues[b]);
            } else {
                return Double.compare(fitnessValues[b], fitnessValues[a]);
            }
        });

        // Select top individuals
        List<SchedulingSolution> elite = new ArrayList<>(count);
        for (int i = 0; i < Math.min(count, indices.length); i++) {
            elite.add(population.get(indices[i]).copy());
        }

        return elite;
    }

    /**
     * Evaluates all solutions in the population.
     */
    private void evaluatePopulation() {
        for (int i = 0; i < population.size(); i++) {
            SchedulingSolution solution = population.get(i);
            fitnessValues[i] = evaluateFitness(solution);
            statistics.incrementEvaluations();
            if (archive != null) {
                archive.offer(solution);
            }
        }
    }

    /**
     * Builds a boolean array indicating which raw objectives are minimization,
     * for use by the non-dominated archive. The archive operates on raw
     * objective vectors regardless of whether fitness is weighted-sum.
     */
    private boolean[] buildMinimizationArray() {
        List<SchedulingObjective> objectives = config.getObjectives();
        boolean[] mins = new boolean[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            mins[i] = objectives.get(i).isMinimization();
        }
        return mins;
    }

    /**
     * Evaluates the fitness of a single solution.
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
            // If objective is maximization, negate the contribution
            if (objective.isMinimization()) {
                weightedSum += weight * value;
            } else {
                weightedSum -= weight * value; // Negate for maximization objectives
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
     * Gets the statistics for this algorithm run.
     *
     * @return Statistics object
     */
    public GAStatistics getStatistics() {
        return statistics;
    }

    /**
     * Gets the current population.
     *
     * @return Current population
     */
    public List<SchedulingSolution> getPopulation() {
        return population;
    }

    /**
     * Gets the fitness values for the current population.
     *
     * @return Fitness values array
     */
    public double[] getFitnessValues() {
        return fitnessValues;
    }

    /**
     * Gets the configuration.
     *
     * @return Configuration
     */
    public GAConfiguration getConfig() {
        return config;
    }

    /**
     * Gets the non-dominated solutions collected across the whole run.
     * Empty before run() has been called.
     */
    public List<SchedulingSolution> getArchive() {
        if (archive == null) return new ArrayList<>();
        return archive.getMembers();
    }
}
