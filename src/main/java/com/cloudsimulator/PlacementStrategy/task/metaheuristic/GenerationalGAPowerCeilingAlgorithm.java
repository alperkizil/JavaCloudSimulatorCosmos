package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.PowerCeilingEnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.CrossoverOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.selection.SelectionOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.selection.TournamentSelection;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.AlgorithmStatistics;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Constraint-aware duplicate of {@link GenerationalGAAlgorithm}. The search
 * dynamics (fitness, selection, crossover, mutation, elitism) are identical;
 * the only difference is the archive — a {@link ConstrainedNonDominatedArchive}
 * that applies Deb's constrained-domination against a per-solution peak-power
 * violation computed via {@link PowerCeilingEnergyObjective}.
 *
 * Additive: the base GenerationalGAAlgorithm is untouched.
 */
public class GenerationalGAPowerCeilingAlgorithm {

    private final GAConfiguration config;
    private final List<Task> tasks;
    private final List<VM> vms;
    private final double powerCapWatts;
    private final List<Host> hosts;

    private final SelectionOperator selectionOperator;
    private final CrossoverOperator crossoverOperator;
    private final MutationOperator mutationOperator;
    private final RepairOperator repairOperator;

    private final RandomGenerator random;
    private final GAStatistics statistics;

    private List<SchedulingSolution> population;
    private double[] fitnessValues;

    private ConstrainedNonDominatedArchive archive;
    private final PowerCeilingEnergyObjective meter;

    public GenerationalGAPowerCeilingAlgorithm(GAConfiguration config, List<Task> tasks, List<VM> vms,
                                               double powerCapWatts, List<Host> hosts) {
        this.config = config;
        this.tasks = new ArrayList<>(tasks);
        this.vms = new ArrayList<>(vms);
        this.powerCapWatts = powerCapWatts;
        this.hosts = hosts;

        this.random = RandomGenerator.getInstance();

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

        this.statistics = new GAStatistics(config.isVerboseLogging());
        if (config.isVerboseLogging()) {
            this.statistics.setOutputFormat(GAStatistics.OutputFormat.DEFAULT);
        }

        this.population = new ArrayList<>();
        this.fitnessValues = new double[config.getPopulationSize()];

        this.meter = new PowerCeilingEnergyObjective(powerCapWatts);
        if (hosts != null) this.meter.setHosts(hosts);
    }

    public SchedulingSolution run() {
        if (tasks.isEmpty() || vms.isEmpty()) {
            System.err.println("[GA-PC] No tasks or VMs to optimize");
            return null;
        }

        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[GA-PC] Problem is infeasible: some tasks have no valid VMs");
            return null;
        }

        if (config.isVerboseLogging()) {
            System.out.println("\n=== Generational GA (P_cap=" + powerCapWatts + " W) Starting ===");
            System.out.println(config);
            System.out.println("Tasks: " + tasks.size() + ", VMs: " + vms.size());
            System.out.println();
        }

        statistics.reset();
        statistics.startTimer();

        AlgorithmStatistics algoStats = new AlgorithmStatistics(config.getNumObjectives());
        archive = new ConstrainedNonDominatedArchive(buildMinimizationArray());

        initializePopulation();
        evaluatePopulation();
        statistics.updateGeneration(0, population, fitnessValues, isMinimization());

        if (config.isVerboseLogging()) {
            System.out.println(statistics.formatCurrentGeneration());
        }

        int generation = 0;
        while (!config.getTerminationCondition().shouldTerminate(algoStats)) {
            generation++;

            algoStats.setCurrentGeneration(generation);
            algoStats.setTotalFitnessEvaluations(statistics.getTotalFitnessEvaluations());
            algoStats.setElapsedTimeMillis(statistics.getElapsedTimeMillis());

            evolveGeneration();
            evaluatePopulation();
            statistics.updateGeneration(generation, population, fitnessValues, isMinimization());

            if (config.isVerboseLogging() && generation % config.getLogInterval() == 0) {
                System.out.println(statistics.formatCurrentGeneration());
            }

            algoStats.setBestObjectiveValues(new double[]{statistics.getBestFitness()});
        }

        if (config.isVerboseLogging()) {
            System.out.println("\n=== GA-PC Completed ===");
            System.out.println(statistics);
        }

        return statistics.getBestSolution();
    }

    private void initializePopulation() {
        population.clear();
        int popSize = config.getPopulationSize();
        int numTasks = tasks.size();
        int numVMs = vms.size();
        int numObjectives = config.getNumObjectives();

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
            System.out.println("[GA-PC] Injected " + seedsInjected + " heuristic seed(s) into initial population");
        }

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

    private void evolveGeneration() {
        int popSize = config.getPopulationSize();
        int eliteCount = config.getEliteCount();

        List<SchedulingSolution> elite = selectElite(eliteCount);

        int offspringNeeded = popSize - eliteCount;
        List<SchedulingSolution> offspring = new ArrayList<>(offspringNeeded);

        while (offspring.size() < offspringNeeded) {
            SchedulingSolution parent1 = selectionOperator.select(
                population, fitnessValues, isMinimization());
            SchedulingSolution parent2 = selectionOperator.select(
                population, fitnessValues, isMinimization());

            SchedulingSolution[] children;
            if (random.nextDouble() < config.getCrossoverRate()) {
                children = crossoverOperator.crossover(parent1, parent2);
            } else {
                children = new SchedulingSolution[]{parent1.copy(), parent2.copy()};
            }

            for (SchedulingSolution child : children) {
                mutationOperator.mutate(child, config.getMutationRate());
            }

            for (SchedulingSolution child : children) {
                repairOperator.repair(child);
            }

            for (SchedulingSolution child : children) {
                if (offspring.size() < offspringNeeded) {
                    offspring.add(child);
                }
            }
        }

        population.clear();
        population.addAll(elite);
        population.addAll(offspring);
    }

    private List<SchedulingSolution> selectElite(int count) {
        if (count <= 0) return new ArrayList<>();

        Integer[] indices = new Integer[population.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;

        final boolean minimize = isMinimization();
        Arrays.sort(indices, (a, b) -> minimize
            ? Double.compare(fitnessValues[a], fitnessValues[b])
            : Double.compare(fitnessValues[b], fitnessValues[a]));

        List<SchedulingSolution> elite = new ArrayList<>(count);
        for (int i = 0; i < Math.min(count, indices.length); i++) {
            elite.add(population.get(indices[i]).copy());
        }
        return elite;
    }

    private void evaluatePopulation() {
        for (int i = 0; i < population.size(); i++) {
            SchedulingSolution solution = population.get(i);
            fitnessValues[i] = evaluateFitness(solution);
            statistics.incrementEvaluations();
            if (archive != null) {
                meter.evaluate(solution, tasks, vms);
                double violation = Math.max(0.0, meter.getLastPeakPower() - powerCapWatts);
                archive.offer(solution, violation);
            }
        }
    }

    private boolean[] buildMinimizationArray() {
        List<SchedulingObjective> objectives = config.getObjectives();
        boolean[] mins = new boolean[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            mins[i] = objectives.get(i).isMinimization();
        }
        return mins;
    }

    private double evaluateFitness(SchedulingSolution solution) {
        List<SchedulingObjective> objectives = config.getObjectives();

        if (objectives.size() == 1) {
            SchedulingObjective objective = objectives.get(0);
            double value = objective.evaluate(solution, tasks, vms);
            solution.setObjectiveValue(0, value);
            return value;
        }

        double weightedSum = 0.0;
        Map<SchedulingObjective, Double> weights = config.getObjectiveWeights();

        for (int i = 0; i < objectives.size(); i++) {
            SchedulingObjective objective = objectives.get(i);
            double value = objective.evaluate(solution, tasks, vms);
            solution.setObjectiveValue(i, value);

            double weight = weights.getOrDefault(objective, 1.0);

            if (objective.isMinimization()) {
                weightedSum += weight * value;
            } else {
                weightedSum -= weight * value;
            }
        }

        return weightedSum;
    }

    private boolean isMinimization() {
        if (config.isWeightedSum()) return true;
        return config.getPrimaryObjective().isMinimization();
    }

    public GAStatistics getStatistics() { return statistics; }
    public List<SchedulingSolution> getPopulation() { return population; }
    public double[] getFitnessValues() { return fitnessValues; }
    public GAConfiguration getConfig() { return config; }
    public double getPowerCapWatts() { return powerCapWatts; }

    /**
     * Non-dominated solutions under Deb's constrained-domination rules.
     * May contain infeasible solutions when no feasible point has been found
     * yet. Use {@link #getFeasibleArchive()} to restrict to feasible points.
     */
    public List<SchedulingSolution> getArchive() {
        if (archive == null) return new ArrayList<>();
        return archive.getMembers();
    }

    public List<SchedulingSolution> getFeasibleArchive() {
        if (archive == null) return new ArrayList<>();
        return archive.getFeasibleMembers();
    }
}
