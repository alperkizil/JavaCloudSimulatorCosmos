package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.CrossoverOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.AlgorithmStatistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * NSGA-II (Non-dominated Sorting Genetic Algorithm II) implementation.
 *
 * NSGA-II is a popular multi-objective evolutionary algorithm that:
 * 1. Uses non-dominated sorting to rank solutions by Pareto dominance
 * 2. Uses crowding distance to maintain diversity along the Pareto front
 * 3. Uses elitism by combining parents and offspring before selection
 *
 * Reference:
 * Deb, K., Pratap, A., Agarwal, S., & Meyarivan, T. (2002).
 * A fast and elitist multiobjective genetic algorithm: NSGA-II.
 * IEEE Transactions on Evolutionary Computation, 6(2), 182-197.
 */
public class NSGA2Algorithm {

    private final NSGA2Configuration config;
    private final List<Task> tasks;
    private final List<VM> vms;
    private final Random random;

    // Operators
    private final RepairOperator repairOperator;
    private final CrossoverOperator crossoverOperator;
    private final MutationOperator mutationOperator;

    // Algorithm state
    private List<SchedulingSolution> population;
    private AlgorithmStatistics statistics;
    private boolean[] isMinimization;

    /**
     * Creates an NSGA-II algorithm instance.
     *
     * @param config Configuration parameters
     * @param tasks  Tasks to schedule
     * @param vms    VMs to assign tasks to
     */
    public NSGA2Algorithm(NSGA2Configuration config, List<Task> tasks, List<VM> vms) {
        this.config = config;
        this.tasks = tasks;
        this.vms = vms;

        // Initialize random generator
        if (config.hasRandomSeed()) {
            this.random = new Random(config.getRandomSeed());
        } else {
            this.random = new Random();
        }

        // Initialize operators
        this.repairOperator = new RepairOperator(tasks, vms, random);
        this.crossoverOperator = new CrossoverOperator(
            config.getCrossoverType(),
            config.getNumObjectives(),
            random
        );
        this.mutationOperator = new MutationOperator(
            vms.size(),
            repairOperator,
            random
        );

        // Initialize statistics
        this.statistics = new AlgorithmStatistics(config.getNumObjectives());
        this.isMinimization = config.getMinimizationArray();
    }

    /**
     * Runs the NSGA-II algorithm and returns the Pareto front.
     *
     * @return Pareto front containing non-dominated solutions
     */
    public ParetoFront run() {
        // Check feasibility
        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[NSGA-II] Problem is infeasible. Some tasks have no valid VMs.");
            List<Integer> infeasible = repairOperator.getInfeasibleTasks();
            for (int taskIdx : infeasible) {
                System.err.println("  Task " + taskIdx + " (" + tasks.get(taskIdx).getName() + ") has no valid VMs");
            }
            return new ParetoFront(config.getObjectiveNames(), isMinimization);
        }

        // Initialize population
        initializePopulation();

        // Evaluate initial population
        evaluatePopulation(population);

        // Perform non-dominated sorting
        List<List<SchedulingSolution>> fronts = nonDominatedSort(population);
        assignCrowdingDistance(fronts);

        if (config.isVerboseLogging()) {
            System.out.println("[NSGA-II] Initial population created. Front 0 size: " + fronts.get(0).size());
        }

        // Main evolutionary loop
        while (!config.getTerminationCondition().shouldTerminate(statistics)) {
            // Create offspring through selection, crossover, and mutation
            List<SchedulingSolution> offspring = createOffspring();

            // Evaluate offspring
            evaluatePopulation(offspring);

            // Combine parents and offspring
            List<SchedulingSolution> combined = new ArrayList<>(population);
            combined.addAll(offspring);

            // Non-dominated sorting on combined population
            fronts = nonDominatedSort(combined);
            assignCrowdingDistance(fronts);

            // Select next generation
            population = selectNextGeneration(fronts);

            // Update statistics
            statistics.incrementGeneration();
            updateBestValues();

            // Logging
            if (config.isVerboseLogging() &&
                statistics.getCurrentGeneration() % config.getLogInterval() == 0) {
                System.out.println("[NSGA-II] Generation " + statistics.getCurrentGeneration() +
                    " | Front 0 size: " + fronts.get(0).size() +
                    " | Evaluations: " + statistics.getTotalFitnessEvaluations());
            }
        }

        // Extract final Pareto front
        fronts = nonDominatedSort(population);
        List<SchedulingSolution> paretoSolutions = new ArrayList<>(fronts.get(0));

        if (config.isVerboseLogging()) {
            System.out.println("[NSGA-II] Completed. Final Pareto front size: " + paretoSolutions.size());
        }

        statistics.setParetoFront(paretoSolutions);
        statistics.setNonDominatedCount(paretoSolutions.size());

        return new ParetoFront(paretoSolutions, config.getObjectiveNames(), isMinimization);
    }

    /**
     * Initializes the population with random valid solutions.
     */
    private void initializePopulation() {
        population = new ArrayList<>(config.getPopulationSize());
        int numTasks = tasks.size();
        int numVMs = vms.size();
        int numObjectives = config.getNumObjectives();

        for (int i = 0; i < config.getPopulationSize(); i++) {
            SchedulingSolution solution = new SchedulingSolution(numTasks, numVMs, numObjectives);

            // Random assignment
            for (int taskIdx = 0; taskIdx < numTasks; taskIdx++) {
                List<Integer> validVms = repairOperator.getValidVmsForTask(taskIdx);
                if (!validVms.isEmpty()) {
                    int vmIdx = validVms.get(random.nextInt(validVms.size()));
                    solution.setAssignedVM(taskIdx, vmIdx);
                }
            }

            // Build task ordering
            solution.rebuildTaskOrdering();

            // Shuffle task order within each VM for diversity
            shuffleTaskOrders(solution);

            population.add(solution);
        }
    }

    /**
     * Shuffles task execution order within each VM.
     */
    private void shuffleTaskOrders(SchedulingSolution solution) {
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            List<Integer> order = solution.getTaskOrderForVM(vmIdx);
            Collections.shuffle(order, random);
        }
    }

    /**
     * Evaluates objective values for all solutions in a population.
     */
    private void evaluatePopulation(List<SchedulingSolution> pop) {
        for (SchedulingSolution solution : pop) {
            if (!solution.isEvaluated()) {
                evaluateSolution(solution);
            }
        }
        statistics.addFitnessEvaluations(pop.size());
    }

    /**
     * Evaluates all objectives for a single solution.
     */
    private void evaluateSolution(SchedulingSolution solution) {
        double[] values = new double[config.getNumObjectives()];
        for (int i = 0; i < config.getNumObjectives(); i++) {
            values[i] = config.getObjectives().get(i).evaluate(solution, tasks, vms);
        }
        solution.setObjectiveValues(values);
    }

    /**
     * Creates offspring through selection, crossover, and mutation.
     */
    private List<SchedulingSolution> createOffspring() {
        List<SchedulingSolution> offspring = new ArrayList<>(config.getPopulationSize());

        while (offspring.size() < config.getPopulationSize()) {
            // Binary tournament selection
            SchedulingSolution parent1 = binaryTournamentSelection();
            SchedulingSolution parent2 = binaryTournamentSelection();

            // Crossover
            SchedulingSolution[] children;
            if (random.nextDouble() < config.getCrossoverRate()) {
                children = crossoverOperator.crossover(parent1, parent2);
            } else {
                children = new SchedulingSolution[]{parent1.copy(), parent2.copy()};
            }

            // Mutation and repair
            for (SchedulingSolution child : children) {
                mutationOperator.mutate(child, config.getMutationRate());
                repairOperator.repair(child);
                offspring.add(child);

                if (offspring.size() >= config.getPopulationSize()) {
                    break;
                }
            }
        }

        return offspring;
    }

    /**
     * Binary tournament selection based on rank and crowding distance.
     */
    private SchedulingSolution binaryTournamentSelection() {
        int idx1 = random.nextInt(population.size());
        int idx2 = random.nextInt(population.size());

        SchedulingSolution sol1 = population.get(idx1);
        SchedulingSolution sol2 = population.get(idx2);

        // Compare by rank first, then by crowding distance
        if (sol1.getRank() < sol2.getRank()) {
            return sol1;
        } else if (sol2.getRank() < sol1.getRank()) {
            return sol2;
        } else {
            // Same rank - prefer higher crowding distance (more diverse)
            return sol1.getCrowdingDistance() > sol2.getCrowdingDistance() ? sol1 : sol2;
        }
    }

    /**
     * Performs non-dominated sorting to partition population into fronts.
     *
     * @param pop Population to sort
     * @return List of fronts, where front[0] is the Pareto front
     */
    private List<List<SchedulingSolution>> nonDominatedSort(List<SchedulingSolution> pop) {
        // Reset domination data
        for (SchedulingSolution sol : pop) {
            sol.resetDominationData();
        }

        // Calculate domination relationships
        for (int i = 0; i < pop.size(); i++) {
            SchedulingSolution p = pop.get(i);

            for (int j = i + 1; j < pop.size(); j++) {
                SchedulingSolution q = pop.get(j);

                if (p.dominates(q, isMinimization)) {
                    p.addDominatedSolution(q);
                    q.incrementDominationCount();
                } else if (q.dominates(p, isMinimization)) {
                    q.addDominatedSolution(p);
                    p.incrementDominationCount();
                }
            }
        }

        // Build fronts
        List<List<SchedulingSolution>> fronts = new ArrayList<>();
        List<SchedulingSolution> currentFront = new ArrayList<>();

        // First front: solutions dominated by no one
        for (SchedulingSolution sol : pop) {
            if (sol.getDominationCount() == 0) {
                sol.setRank(0);
                currentFront.add(sol);
            }
        }
        fronts.add(currentFront);

        // Subsequent fronts
        int frontIndex = 0;
        while (frontIndex < fronts.size() && !fronts.get(frontIndex).isEmpty()) {
            List<SchedulingSolution> nextFront = new ArrayList<>();

            for (SchedulingSolution p : fronts.get(frontIndex)) {
                for (SchedulingSolution q : p.getDominatedSolutions()) {
                    q.decrementDominationCount();
                    if (q.getDominationCount() == 0) {
                        q.setRank(frontIndex + 1);
                        nextFront.add(q);
                    }
                }
            }

            if (!nextFront.isEmpty()) {
                fronts.add(nextFront);
            }
            frontIndex++;

            // Safety check to avoid infinite loops
            if (frontIndex > pop.size()) {
                break;
            }
        }

        return fronts;
    }

    /**
     * Assigns crowding distance to solutions within each front.
     */
    private void assignCrowdingDistance(List<List<SchedulingSolution>> fronts) {
        for (List<SchedulingSolution> front : fronts) {
            if (front.isEmpty()) continue;

            int n = front.size();

            // Initialize crowding distance
            for (SchedulingSolution sol : front) {
                sol.setCrowdingDistance(0.0);
            }

            // Calculate crowding distance for each objective
            for (int m = 0; m < config.getNumObjectives(); m++) {
                final int objIndex = m;

                // Sort by this objective
                front.sort(Comparator.comparingDouble(s -> s.getObjectiveValue(objIndex)));

                // Boundary solutions get infinite distance
                front.get(0).setCrowdingDistance(Double.POSITIVE_INFINITY);
                front.get(n - 1).setCrowdingDistance(Double.POSITIVE_INFINITY);

                // Calculate range
                double minVal = front.get(0).getObjectiveValue(objIndex);
                double maxVal = front.get(n - 1).getObjectiveValue(objIndex);
                double range = maxVal - minVal;

                if (range == 0) {
                    continue; // Skip if all solutions have same value for this objective
                }

                // Add normalized distance for intermediate solutions
                for (int i = 1; i < n - 1; i++) {
                    double prevVal = front.get(i - 1).getObjectiveValue(objIndex);
                    double nextVal = front.get(i + 1).getObjectiveValue(objIndex);
                    double distance = (nextVal - prevVal) / range;

                    front.get(i).setCrowdingDistance(
                        front.get(i).getCrowdingDistance() + distance
                    );
                }
            }
        }
    }

    /**
     * Selects the next generation from sorted fronts.
     */
    private List<SchedulingSolution> selectNextGeneration(List<List<SchedulingSolution>> fronts) {
        List<SchedulingSolution> nextGen = new ArrayList<>(config.getPopulationSize());

        for (List<SchedulingSolution> front : fronts) {
            if (nextGen.size() + front.size() <= config.getPopulationSize()) {
                // Add entire front
                nextGen.addAll(front);
            } else {
                // Need to select subset based on crowding distance
                int remaining = config.getPopulationSize() - nextGen.size();
                front.sort((a, b) -> Double.compare(b.getCrowdingDistance(), a.getCrowdingDistance()));
                nextGen.addAll(front.subList(0, remaining));
                break;
            }
        }

        return nextGen;
    }

    /**
     * Updates best objective values in statistics.
     */
    private void updateBestValues() {
        for (SchedulingSolution sol : population) {
            statistics.updateBestValues(sol.getObjectiveValues(), isMinimization);
        }
    }

    /**
     * Gets the algorithm statistics.
     *
     * @return Current statistics
     */
    public AlgorithmStatistics getStatistics() {
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
}
