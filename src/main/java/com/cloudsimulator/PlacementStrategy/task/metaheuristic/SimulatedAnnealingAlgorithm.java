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
    private double initialTemperatureActual; // Actual T0 used (may be auto-calculated)
    private int reheatsPerformed;

    // Non-dominated archive (multi-objective view for a single-objective search)
    private NonDominatedArchive archive;

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

        // Reset the non-dominated archive for this run
        archive = new NonDominatedArchive(buildMinimizationArray());

        // Step 1: Generate initial solution (s = s0) — seeded if a heuristic
        // seed is available, random otherwise. Extra seeds beyond the first
        // are evaluated purely to populate the archive.
        currentSolution = generateInitialSolution();
        currentFitness = evaluateFitness(currentSolution);
        statistics.incrementEvaluations();
        archive.offer(currentSolution);

        offerExtraSeedsToArchive();

        // Keep track of best solution
        bestSolution = currentSolution.copy();
        bestFitness = currentFitness;

        // Step 2: Initialize temperature (T = Tmax)
        if (config.isAutoInitialTemperature()) {
            temperature = calculateInitialTemperature();
        } else {
            temperature = config.getInitialTemperature();
        }
        initialTemperatureActual = temperature;
        reheatsPerformed = 0;

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
            if (config.isReheatEnabled()) {
                System.out.println("Reheating: enabled (factor=" + config.getReheatFactor() +
                    ", threshold=" + config.getReheatStagnationThreshold() +
                    ", maxReheats=" + config.getMaxReheats() + ")");
            }
            if (config.isAdaptiveIterationsEnabled()) {
                System.out.println("Adaptive iterations: enabled [" +
                    config.getMinIterationsPerTemperature() + "-" +
                    config.getMaxIterationsPerTemperature() + "]");
            }
            if (config.isTemperatureScaledPerturbation()) {
                System.out.println("Temperature-scaled perturbation: enabled (max=" +
                    config.getMaxPerturbationMutations() + " mutations)");
            }
            System.out.println();
        }

        // Step 3: Main SA loop
        int temperatureStep = 0;
        int noImprovementSteps = 0;
        CoolingSchedule coolingSchedule = config.getCoolingSchedule();
        double previousAcceptanceRate = 1.0; // Start high for adaptive iterations

        while (!config.getTerminationCondition().shouldTerminate(algoStats)) {
            temperatureStep++;

            // === Adaptive iterations-per-temperature ===
            int iterationsThisStep = config.getIterationsPerTemperature();
            if (config.isAdaptiveIterationsEnabled()) {
                iterationsThisStep = calculateAdaptiveIterations(previousAcceptanceRate);
            }

            // Track acceptance statistics for this temperature step
            int accepted = 0;
            int rejected = 0;
            int improving = 0;

            // Inner loop: equilibrium at fixed temperature
            for (int i = 0; i < iterationsThisStep; i++) {
                // Generate random neighbor s' (with temperature-scaled perturbation)
                SchedulingSolution neighbor = generateNeighbor(currentSolution);

                // Evaluate neighbor
                double neighborFitness = evaluateFitness(neighbor);
                statistics.incrementEvaluations();
                archive.offer(neighbor);

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

                    // Update best if this is a new best
                    if (isBetter(currentFitness, bestFitness)) {
                        bestSolution = currentSolution.copy();
                        bestFitness = currentFitness;
                    }
                } else {
                    rejected++;
                }
            }

            // Calculate acceptance rate for this temperature step
            double acceptanceRate = (accepted + rejected) > 0
                ? (double) accepted / (accepted + rejected) : 0.0;
            previousAcceptanceRate = acceptanceRate;

            // Track no-improvement steps for reheating
            double previousBest = statistics.getGlobalBestFitness();
            boolean improved = isBetter(bestFitness, previousBest)
                || (statistics.getCurrentTemperatureStep() == 0); // First step always counts

            // Update statistics
            statistics.updateTemperatureStep(temperatureStep, temperature,
                bestSolution, currentFitness, accepted, rejected, improving, isMinimization());

            // Log progress
            if (config.isVerboseLogging() && temperatureStep % config.getLogInterval() == 0) {
                System.out.println(statistics.formatCurrentTemperatureStep());
            }

            // === Reheating on stagnation ===
            if (config.isReheatEnabled()) {
                noImprovementSteps = statistics.getNoImprovementSteps();
                if (noImprovementSteps >= config.getReheatStagnationThreshold()
                        && reheatsPerformed < config.getMaxReheats()) {
                    double oldTemp = temperature;
                    temperature = temperature * config.getReheatFactor();
                    // Cap at initial temperature to avoid excessive exploration
                    temperature = Math.min(temperature, initialTemperatureActual);
                    reheatsPerformed++;

                    if (config.isVerboseLogging()) {
                        System.out.printf(
                            "  [REHEAT %d/%d] Stagnation at step %d (no improvement for %d steps). " +
                            "Temperature: %.4f -> %.4f%n",
                            reheatsPerformed, config.getMaxReheats(),
                            temperatureStep, noImprovementSteps, oldTemp, temperature);
                    }

                    // Reset current solution to best known to search from the best basin
                    currentSolution = bestSolution.copy();
                    currentFitness = bestFitness;
                }
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

        // Final log
        if (config.isVerboseLogging()) {
            System.out.println("\n=== SA Completed ===");
            System.out.println(statistics);
            if (config.isReheatEnabled()) {
                System.out.println("Reheats performed: " + reheatsPerformed + "/" + config.getMaxReheats());
            }
        }

        return bestSolution;
    }

    /**
     * Generates the initial solution. Uses the first heuristic seed if one was
     * configured; otherwise builds a random assignment respecting constraints.
     *
     * @return Initial solution
     */
    private SchedulingSolution generateInitialSolution() {
        int numTasks = tasks.size();
        int numVMs = vms.size();
        int numObjectives = config.getNumObjectives();

        List<int[]> seeds = config.getSeedAssignments();
        if (seeds != null && !seeds.isEmpty()) {
            int[] primary = seeds.get(0);
            if (primary != null && primary.length == numTasks) {
                SchedulingSolution seeded = new SchedulingSolution(numTasks, numVMs, numObjectives);
                seeded.setTaskAssignment(primary.clone());
                repairOperator.repair(seeded);
                seeded.rebuildTaskOrdering();
                if (config.isVerboseLogging()) {
                    System.out.println("[SA] Using heuristic seed as initial solution");
                }
                return seeded;
            }
        }

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
     * Evaluates any seeds beyond the first (which was already used as the
     * initial solution) and offers them to the archive so they survive the
     * run regardless of what SA's fitness-driven search does.
     */
    private void offerExtraSeedsToArchive() {
        List<int[]> seeds = config.getSeedAssignments();
        if (seeds == null || seeds.size() <= 1) return;

        int numTasks = tasks.size();
        int numVMs = vms.size();
        int numObjectives = config.getNumObjectives();

        int offered = 0;
        for (int i = 1; i < seeds.size(); i++) {
            int[] seed = seeds.get(i);
            if (seed == null || seed.length != numTasks) continue;
            SchedulingSolution extra = new SchedulingSolution(numTasks, numVMs, numObjectives);
            extra.setTaskAssignment(seed.clone());
            repairOperator.repair(extra);
            extra.rebuildTaskOrdering();
            evaluateFitness(extra);
            statistics.incrementEvaluations();
            if (archive.offer(extra)) offered++;
        }

        if (offered > 0 && config.isVerboseLogging()) {
            System.out.println("[SA] Offered " + offered + " additional seed(s) to the archive");
        }
    }

    /**
     * Generates a neighbor solution by applying mutation.
     * When temperature-scaled perturbation is enabled, the number of mutations
     * scales with the current temperature: more mutations at high T (large jumps),
     * single mutation at low T (fine-grained search).
     *
     * @param solution Current solution
     * @return Neighbor solution
     */
    private SchedulingSolution generateNeighbor(SchedulingSolution solution) {
        SchedulingSolution neighbor = solution.copy();

        if (config.isTemperatureScaledPerturbation() && initialTemperatureActual > 0) {
            // Scale mutations: max at T_initial, 1 at T ≈ 0
            double temperatureRatio = temperature / initialTemperatureActual;
            int maxMut = config.getMaxPerturbationMutations();
            int numMutations = 1 + (int) (temperatureRatio * (maxMut - 1));
            numMutations = Math.max(1, Math.min(numMutations, maxMut));

            if (numMutations > 1) {
                neighborOperator.mutateMultiple(neighbor, numMutations);
            } else {
                neighborOperator.mutateSingle(neighbor);
            }
        } else {
            // Standard: single mutation per neighbor
            neighborOperator.mutateSingle(neighbor);
        }

        // Repair if needed
        repairOperator.repair(neighbor);

        return neighbor;
    }

    /**
     * Calculates the number of iterations for the current temperature step
     * based on the previous step's acceptance rate.
     *
     * - High acceptance (> highThreshold): random walk phase, fewer iterations
     * - Low acceptance (< lowThreshold): stuck phase, fewer iterations
     * - Moderate acceptance: productive exploration, more iterations
     *
     * @param acceptanceRate Previous step's acceptance rate
     * @return Number of iterations for this temperature step
     */
    private int calculateAdaptiveIterations(double acceptanceRate) {
        int min = config.getMinIterationsPerTemperature();
        int max = config.getMaxIterationsPerTemperature();
        double highThresh = config.getAdaptiveIterHighAcceptanceThreshold();
        double lowThresh = config.getAdaptiveIterLowAcceptanceThreshold();

        if (acceptanceRate > highThresh || acceptanceRate < lowThresh) {
            // Non-productive phase: use minimum iterations
            return min;
        } else {
            // Productive phase: scale up toward maximum
            // Peak at the midpoint between thresholds
            double midpoint = (highThresh + lowThresh) / 2.0;
            double halfRange = (highThresh - lowThresh) / 2.0;
            double distanceFromMid = Math.abs(acceptanceRate - midpoint);
            double productivity = 1.0 - (distanceFromMid / halfRange);
            return min + (int) (productivity * (max - min));
        }
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
        if (archive != null) archive.offer(initial);

        // Sample neighbors and collect positive deltas
        List<Double> positiveDeltaEs = new ArrayList<>();

        for (int i = 0; i < sampleSize; i++) {
            SchedulingSolution neighbor = generateNeighbor(initial);
            double neighborFitness = evaluateFitness(neighbor);
            statistics.incrementEvaluations();
            if (archive != null) archive.offer(neighbor);

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

    /**
     * Gets the non-dominated solutions collected across the whole run.
     * Empty before run() has been called.
     */
    public List<SchedulingSolution> getArchive() {
        if (archive == null) return new ArrayList<>();
        return archive.getMembers();
    }

    /**
     * Builds a boolean array indicating which raw objectives are minimization.
     */
    private boolean[] buildMinimizationArray() {
        List<SchedulingObjective> objectives = config.getObjectives();
        boolean[] mins = new boolean[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            mins[i] = objectives.get(i).isMinimization();
        }
        return mins;
    }
}
