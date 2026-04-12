package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.CoolingSchedule;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.AlgorithmStatistics;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.TemperatureTermination;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simulated Annealing algorithm for task scheduling optimization.
 *
 * This algorithm implements the classic SA metaheuristic as described in:
 * El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 *
 * Algorithm pseudocode:
 * <pre>
 * Input: Cooling schedule
 * s = s0;                          // Generate initial solution
 * T = Tmax;                        // Starting temperature
 * Repeat
 *     Repeat                       // At fixed temperature
 *         Generate random neighbor s';
 *         ΔE = f(s') - f(s);
 *         If ΔE ≤ 0 Then s = s'   // Accept better solution
 *         Else Accept s' with probability e^(-ΔE/T)
 *     Until Equilibrium condition (iterations per temperature)
 *     T = g(T);                    // Temperature update
 * Until Stopping criteria (T < Tmin)
 * Output: Best solution found
 * </pre>
 *
 * Key features:
 * - Single objective or weighted-sum multi-objective optimization
 * - Multiple cooling schedules (linear, geometric, logarithmic, adaptive)
 * - Auto-calculation of initial temperature based on acceptance probability
 * - Uses simulator's RandomGenerator for reproducibility
 * - Comprehensive statistics tracking
 */
public class SimulatedAnnealingAlgorithm {

    private final SAConfiguration config;
    private final List<Task> tasks;
    private final List<VM> vms;

    // Operators
    private final MutationOperator neighborOperator;
    private final RepairOperator repairOperator;

    // Random generator (from simulator)
    private final RandomGenerator random;

    // Statistics tracking
    private final SAStatistics statistics;

    // Current state
    private SchedulingSolution currentSolution;
    private SchedulingSolution bestSolution;
    private double currentFitness;
    private double bestFitness;
    private double temperature;

    /**
     * Creates a new Simulated Annealing algorithm.
     *
     * @param config Configuration for the algorithm
     * @param tasks  Tasks to schedule
     * @param vms    VMs to assign tasks to
     */
    public SimulatedAnnealingAlgorithm(SAConfiguration config, List<Task> tasks, List<VM> vms) {
        this.config = config;
        this.tasks = new ArrayList<>(tasks);
        this.vms = new ArrayList<>(vms);

        // Use the simulator's random generator for reproducibility
        this.random = RandomGenerator.getInstance();

        // Initialize operators
        this.repairOperator = new RepairOperator(tasks, vms, new java.util.Random(random.getSeed()));
        this.neighborOperator = new MutationOperator(
            config.getNeighborType(),
            vms.size(),
            repairOperator,
            new java.util.Random(random.getSeed())
        );

        // Initialize statistics
        this.statistics = new SAStatistics(config.isVerboseLogging());
        if (config.isVerboseLogging()) {
            this.statistics.setOutputFormat(SAStatistics.OutputFormat.DEFAULT);
        }
    }

    /**
     * Runs the Simulated Annealing algorithm and returns the best solution.
     *
     * @return Best solution found
     */
    public SchedulingSolution run() {
        if (tasks.isEmpty() || vms.isEmpty()) {
            System.err.println("[SA] No tasks or VMs to optimize");
            return null;
        }

        // Check feasibility
        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[SA] Problem is infeasible: some tasks have no valid VMs");
            List<Integer> infeasible = repairOperator.getInfeasibleTasks();
            System.err.println("[SA] Infeasible task indices: " + infeasible);
            return null;
        }

        // Log configuration
        if (config.isVerboseLogging()) {
            System.out.println("\n=== Simulated Annealing Starting ===");
            System.out.println(config);
            System.out.println("Tasks: " + tasks.size() + ", VMs: " + vms.size());
            System.out.println();
        }

        // Initialize statistics
        statistics.reset();
        statistics.startTimer();

        // Create algorithm statistics for termination condition
        AlgorithmStatistics algoStats = new AlgorithmStatistics(config.getNumObjectives());

        int numberOfRestarts = config.getNumberOfRestarts();

        // Initialize global best (across all restarts)
        bestSolution = null;
        bestFitness = isMinimization() ? Double.MAX_VALUE : -Double.MAX_VALUE;

        // === Multi-start outer loop ===
        for (int restart = 0; restart < numberOfRestarts; restart++) {

            if (config.isVerboseLogging() && numberOfRestarts > 1) {
                System.out.println("--- Restart " + (restart + 1) + " of " + numberOfRestarts + " ---");
            }

            // Step 1: Generate initial solution (s = s0)
            currentSolution = generateInitialSolution();
            currentFitness = evaluateFitness(currentSolution);
            statistics.incrementEvaluations();

            // Update global best
            if (bestSolution == null || isBetter(currentFitness, bestFitness)) {
                bestSolution = currentSolution.copy();
                bestFitness = currentFitness;
            }

            // Step 2: Initialize temperature (T = Tmax)
            if (config.isAutoInitialTemperature()) {
                temperature = calculateInitialTemperature();
            } else {
                temperature = config.getInitialTemperature();
            }

            // Store initial temperature for statistics
            algoStats.setInitialTemperature(temperature);
            algoStats.setCurrentTemperature(temperature);

            // Update termination condition if temperature-based
            if (config.getTerminationCondition() instanceof TemperatureTermination) {
                ((TemperatureTermination) config.getTerminationCondition()).setCurrentTemperature(temperature);
            }

            if (config.isVerboseLogging()) {
                System.out.println("Initial temperature: " + temperature);
                System.out.println("Initial fitness: " + currentFitness);
                System.out.println();
            }

            // Calculate the evaluation budget for this restart
            long evalsAtRestartStart = statistics.getTotalFitnessEvaluations();
            long remainingBudget = 0;
            if (config.getTerminationCondition() instanceof
                    com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.FitnessEvaluationsTermination) {
                com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.FitnessEvaluationsTermination fet =
                    (com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.FitnessEvaluationsTermination)
                        config.getTerminationCondition();
                long totalBudget = fet.getMaxEvaluations();
                remainingBudget = totalBudget - evalsAtRestartStart;
            }
            long evalsPerRestart = (numberOfRestarts > 1 && remainingBudget > 0)
                ? remainingBudget / (numberOfRestarts - restart)
                : Long.MAX_VALUE;

            // Step 3: Main SA loop for this restart
            int temperatureStep = 0;
            CoolingSchedule coolingSchedule = config.getCoolingSchedule();

            while (!config.getTerminationCondition().shouldTerminate(algoStats)) {
                // Check per-restart budget
                long evalsUsedThisRestart = statistics.getTotalFitnessEvaluations() - evalsAtRestartStart;
                if (numberOfRestarts > 1 && evalsUsedThisRestart >= evalsPerRestart) {
                    break; // Move to next restart
                }

                temperatureStep++;

                // Track acceptance statistics for this temperature step
                int accepted = 0;
                int rejected = 0;
                int improving = 0;

                // Inner loop: equilibrium at fixed temperature
                for (int i = 0; i < config.getIterationsPerTemperature(); i++) {
                    // Generate random neighbor s'
                    SchedulingSolution neighbor = generateNeighbor(currentSolution);

                    // Evaluate neighbor
                    double neighborFitness = evaluateFitness(neighbor);
                    statistics.incrementEvaluations();

                    // Calculate ΔE = f(s') - f(s)
                    double deltaE = neighborFitness - currentFitness;

                    // Accept or reject
                    boolean accept = false;

                    if (deltaE <= 0) {
                        // Better solution (for minimization) - always accept
                        accept = true;
                        improving++;
                    } else {
                        // Worse solution - accept with probability e^(-ΔE/T)
                        double probability = Math.exp(-deltaE / temperature);
                        if (random.nextDouble() < probability) {
                            accept = true;
                        }
                    }

                    if (accept) {
                        currentSolution = neighbor;
                        currentFitness = neighborFitness;
                        accepted++;

                        // Update global best if this is a new best
                        if (isBetter(currentFitness, bestFitness)) {
                            bestSolution = currentSolution.copy();
                            bestFitness = currentFitness;
                        }
                    } else {
                        rejected++;
                    }
                }

                // Calculate acceptance rate for this temperature step
                double acceptanceRate = (double) accepted / (accepted + rejected);

                // Update statistics
                statistics.updateTemperatureStep(temperatureStep, temperature,
                    bestSolution, currentFitness, accepted, rejected, improving, isMinimization());

                // Log progress
                if (config.isVerboseLogging() && temperatureStep % config.getLogInterval() == 0) {
                    System.out.println(statistics.formatCurrentTemperatureStep());
                }

                // Temperature update: T = g(T)
                if (coolingSchedule.isAdaptive()) {
                    temperature = coolingSchedule.updateTemperature(temperature, temperatureStep, acceptanceRate);
                } else {
                    temperature = coolingSchedule.updateTemperature(temperature, temperatureStep);
                }

                // Update algorithm statistics for termination check
                algoStats.setCurrentGeneration(temperatureStep);
                algoStats.setCurrentTemperature(temperature);
                algoStats.setTotalFitnessEvaluations(statistics.getTotalFitnessEvaluations());
                algoStats.setElapsedTimeMillis(statistics.getElapsedTimeMillis());
                algoStats.setBestObjectiveValues(new double[]{bestFitness});

                // Update termination condition if temperature-based
                if (config.getTerminationCondition() instanceof TemperatureTermination) {
                    ((TemperatureTermination) config.getTerminationCondition()).setCurrentTemperature(temperature);
                }
            }
        }

        // Final log
        if (config.isVerboseLogging()) {
            System.out.println("\n=== SA Completed ===");
            System.out.println(statistics);
        }

        return bestSolution;
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
     * Generates a neighbor solution by applying mutation.
     *
     * @param solution Current solution
     * @return Neighbor solution
     */
    private SchedulingSolution generateNeighbor(SchedulingSolution solution) {
        SchedulingSolution neighbor = solution.copy();

        // Apply single mutation to create neighbor
        // Use a high mutation rate to ensure at least one change
        neighborOperator.mutateSingle(neighbor);

        // Repair if needed
        repairOperator.repair(neighbor);

        return neighbor;
    }

    /**
     * Calculates initial temperature for target acceptance probability.
     *
     * Uses the heuristic: T0 = -avgΔE / ln(probability)
     * where avgΔE is the average positive delta (cost increase for worse moves)
     *
     * @return Calculated initial temperature
     */
    private double calculateInitialTemperature() {
        double targetProbability = config.getInitialAcceptanceProbability();
        int sampleSize = config.getTemperatureSampleSize();

        // Generate initial solution
        SchedulingSolution initial = generateInitialSolution();
        double initialFitness = evaluateFitness(initial);
        statistics.incrementEvaluations();

        // Sample neighbors and collect positive deltas
        List<Double> positiveDeltaEs = new ArrayList<>();

        for (int i = 0; i < sampleSize; i++) {
            SchedulingSolution neighbor = generateNeighbor(initial);
            double neighborFitness = evaluateFitness(neighbor);
            statistics.incrementEvaluations();

            double deltaE = neighborFitness - initialFitness;

            // For minimization, positive delta means worse solution
            if (isMinimization() && deltaE > 0) {
                positiveDeltaEs.add(deltaE);
            } else if (!isMinimization() && deltaE < 0) {
                // For maximization, negative delta means worse solution
                positiveDeltaEs.add(-deltaE);
            }
        }

        // Calculate average positive delta
        double avgDeltaE;
        if (positiveDeltaEs.isEmpty()) {
            // No worse solutions found, use a default
            avgDeltaE = Math.abs(initialFitness) * 0.1;
            if (avgDeltaE < 1.0) avgDeltaE = 100.0; // Fallback
        } else {
            avgDeltaE = positiveDeltaEs.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(100.0);
        }

        // T0 = -avgΔE / ln(probability)
        double T0 = -avgDeltaE / Math.log(targetProbability);

        if (config.isVerboseLogging()) {
            System.out.println("Auto-calculated initial temperature:");
            System.out.println("  Sample size: " + sampleSize);
            System.out.println("  Positive deltas found: " + positiveDeltaEs.size());
            System.out.println("  Average positive ΔE: " + avgDeltaE);
            System.out.println("  Target acceptance probability: " + targetProbability);
            System.out.println("  Calculated T0: " + T0);
        }

        return T0;
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
            // If objective is maximization, negate the contribution
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
    public SAStatistics getStatistics() {
        return statistics;
    }

    /**
     * Gets the current temperature.
     *
     * @return Current temperature
     */
    public double getCurrentTemperature() {
        return temperature;
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
     * Gets the best solution found.
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
    public SAConfiguration getConfig() {
        return config;
    }
}
