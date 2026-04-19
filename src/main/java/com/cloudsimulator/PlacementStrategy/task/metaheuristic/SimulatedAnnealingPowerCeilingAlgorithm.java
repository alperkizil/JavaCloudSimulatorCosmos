package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.CoolingSchedule;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.PowerCeilingEnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.AlgorithmStatistics;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.TemperatureTermination;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Constraint-aware duplicate of {@link SimulatedAnnealingAlgorithm}. Search
 * dynamics (fitness, neighbour operator, acceptance probability, cooling,
 * reheat) are identical; the archive is replaced with a
 * {@link ConstrainedNonDominatedArchive} that applies Deb's constrained-
 * domination against a per-evaluation peak-power violation.
 *
 * Additive: the base SimulatedAnnealingAlgorithm is untouched.
 */
public class SimulatedAnnealingPowerCeilingAlgorithm {

    private final SAConfiguration config;
    private final List<Task> tasks;
    private final List<VM> vms;
    private final double powerCapWatts;
    private final List<Host> hosts;

    private final MutationOperator neighborOperator;
    private final RepairOperator repairOperator;
    private final RandomGenerator random;
    private final SAStatistics statistics;

    private SchedulingSolution currentSolution;
    private SchedulingSolution bestSolution;
    private double currentFitness;
    private double bestFitness;
    private double temperature;
    private double initialTemperatureActual;
    private int reheatsPerformed;

    private ConstrainedNonDominatedArchive archive;
    private final PowerCeilingEnergyObjective meter;

    public SimulatedAnnealingPowerCeilingAlgorithm(SAConfiguration config, List<Task> tasks, List<VM> vms,
                                                    double powerCapWatts, List<Host> hosts) {
        this.config = config;
        this.tasks = new ArrayList<>(tasks);
        this.vms = new ArrayList<>(vms);
        this.powerCapWatts = powerCapWatts;
        this.hosts = hosts;

        this.random = RandomGenerator.getInstance();

        this.repairOperator = new RepairOperator(tasks, vms, new java.util.Random(random.getSeed()));
        this.neighborOperator = new MutationOperator(
            config.getNeighborType(),
            vms.size(),
            repairOperator,
            new java.util.Random(random.getSeed())
        );

        this.statistics = new SAStatistics(config.isVerboseLogging());
        if (config.isVerboseLogging()) {
            this.statistics.setOutputFormat(SAStatistics.OutputFormat.DEFAULT);
        }

        this.meter = new PowerCeilingEnergyObjective(powerCapWatts);
        if (hosts != null) this.meter.setHosts(hosts);
    }

    public SchedulingSolution run() {
        if (tasks.isEmpty() || vms.isEmpty()) {
            System.err.println("[SA-PC] No tasks or VMs to optimize");
            return null;
        }

        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[SA-PC] Problem is infeasible: some tasks have no valid VMs");
            return null;
        }

        if (config.isVerboseLogging()) {
            System.out.println("\n=== SA (P_cap=" + powerCapWatts + " W) Starting ===");
            System.out.println(config);
            System.out.println("Tasks: " + tasks.size() + ", VMs: " + vms.size());
        }

        statistics.reset();
        statistics.startTimer();

        AlgorithmStatistics algoStats = new AlgorithmStatistics(config.getNumObjectives());
        archive = new ConstrainedNonDominatedArchive(buildMinimizationArray());

        currentSolution = generateInitialSolution();
        currentFitness = evaluateFitness(currentSolution);
        statistics.incrementEvaluations();
        offerToArchive(currentSolution);

        offerExtraSeedsToArchive();

        bestSolution = currentSolution.copy();
        bestFitness = currentFitness;

        if (config.isAutoInitialTemperature()) {
            temperature = calculateInitialTemperature();
        } else {
            temperature = config.getInitialTemperature();
        }
        initialTemperatureActual = temperature;
        reheatsPerformed = 0;

        algoStats.setInitialTemperature(temperature);
        algoStats.setCurrentTemperature(temperature);

        if (config.getTerminationCondition() instanceof TemperatureTermination) {
            ((TemperatureTermination) config.getTerminationCondition()).setCurrentTemperature(temperature);
        }

        int temperatureStep = 0;
        int noImprovementSteps;
        CoolingSchedule coolingSchedule = config.getCoolingSchedule();
        double previousAcceptanceRate = 1.0;

        while (!config.getTerminationCondition().shouldTerminate(algoStats)) {
            temperatureStep++;

            int iterationsThisStep = config.getIterationsPerTemperature();
            if (config.isAdaptiveIterationsEnabled()) {
                iterationsThisStep = calculateAdaptiveIterations(previousAcceptanceRate);
            }

            int accepted = 0;
            int rejected = 0;
            int improving = 0;

            for (int i = 0; i < iterationsThisStep; i++) {
                SchedulingSolution neighbor = generateNeighbor(currentSolution);
                double neighborFitness = evaluateFitness(neighbor);
                statistics.incrementEvaluations();
                offerToArchive(neighbor);

                double deltaE = neighborFitness - currentFitness;

                boolean accept = false;
                if (deltaE <= 0) {
                    accept = true;
                    improving++;
                } else {
                    double probability = Math.exp(-deltaE / temperature);
                    if (random.nextDouble() < probability) accept = true;
                }

                if (accept) {
                    currentSolution = neighbor;
                    currentFitness = neighborFitness;
                    accepted++;

                    if (isBetter(currentFitness, bestFitness)) {
                        bestSolution = currentSolution.copy();
                        bestFitness = currentFitness;
                    }
                } else {
                    rejected++;
                }
            }

            double acceptanceRate = (accepted + rejected) > 0
                ? (double) accepted / (accepted + rejected) : 0.0;
            previousAcceptanceRate = acceptanceRate;

            statistics.updateTemperatureStep(temperatureStep, temperature,
                bestSolution, currentFitness, accepted, rejected, improving, isMinimization());

            if (config.isVerboseLogging() && temperatureStep % config.getLogInterval() == 0) {
                System.out.println(statistics.formatCurrentTemperatureStep());
            }

            if (config.isReheatEnabled()) {
                noImprovementSteps = statistics.getNoImprovementSteps();
                if (noImprovementSteps >= config.getReheatStagnationThreshold()
                        && reheatsPerformed < config.getMaxReheats()) {
                    temperature = temperature * config.getReheatFactor();
                    temperature = Math.min(temperature, initialTemperatureActual);
                    reheatsPerformed++;
                    currentSolution = bestSolution.copy();
                    currentFitness = bestFitness;
                }
            }

            if (coolingSchedule.isAdaptive()) {
                temperature = coolingSchedule.updateTemperature(temperature, temperatureStep, acceptanceRate);
            } else {
                temperature = coolingSchedule.updateTemperature(temperature, temperatureStep);
            }

            algoStats.setCurrentGeneration(temperatureStep);
            algoStats.setCurrentTemperature(temperature);
            algoStats.setTotalFitnessEvaluations(statistics.getTotalFitnessEvaluations());
            algoStats.setElapsedTimeMillis(statistics.getElapsedTimeMillis());
            algoStats.setBestObjectiveValues(new double[]{bestFitness});

            if (config.getTerminationCondition() instanceof TemperatureTermination) {
                ((TemperatureTermination) config.getTerminationCondition()).setCurrentTemperature(temperature);
            }
        }

        if (config.isVerboseLogging()) {
            System.out.println("\n=== SA-PC Completed ===");
            System.out.println(statistics);
        }

        return bestSolution;
    }

    private void offerToArchive(SchedulingSolution solution) {
        if (archive == null) return;
        // evaluateFitness above already ran any EnergyObjective in the
        // objective list; skip the meter's parent energy integral.
        meter.computePowerProfileOnly(solution, tasks, vms);
        double violation = Math.max(0.0, meter.getLastPeakPower() - powerCapWatts);
        archive.offer(solution, violation);
    }

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
                return seeded;
            }
        }

        SchedulingSolution solution = new SchedulingSolution(numTasks, numVMs, numObjectives);
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

    private void offerExtraSeedsToArchive() {
        List<int[]> seeds = config.getSeedAssignments();
        if (seeds == null || seeds.size() <= 1) return;

        int numTasks = tasks.size();
        int numVMs = vms.size();
        int numObjectives = config.getNumObjectives();

        for (int i = 1; i < seeds.size(); i++) {
            int[] seed = seeds.get(i);
            if (seed == null || seed.length != numTasks) continue;
            SchedulingSolution extra = new SchedulingSolution(numTasks, numVMs, numObjectives);
            extra.setTaskAssignment(seed.clone());
            repairOperator.repair(extra);
            extra.rebuildTaskOrdering();
            evaluateFitness(extra);
            statistics.incrementEvaluations();
            offerToArchive(extra);
        }
    }

    private SchedulingSolution generateNeighbor(SchedulingSolution solution) {
        SchedulingSolution neighbor = solution.copy();

        if (config.isTemperatureScaledPerturbation() && initialTemperatureActual > 0) {
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
            neighborOperator.mutateSingle(neighbor);
        }

        repairOperator.repair(neighbor);
        return neighbor;
    }

    private int calculateAdaptiveIterations(double acceptanceRate) {
        int min = config.getMinIterationsPerTemperature();
        int max = config.getMaxIterationsPerTemperature();
        double highThresh = config.getAdaptiveIterHighAcceptanceThreshold();
        double lowThresh = config.getAdaptiveIterLowAcceptanceThreshold();

        if (acceptanceRate > highThresh || acceptanceRate < lowThresh) {
            return min;
        } else {
            double midpoint = (highThresh + lowThresh) / 2.0;
            double halfRange = (highThresh - lowThresh) / 2.0;
            double distanceFromMid = Math.abs(acceptanceRate - midpoint);
            double productivity = 1.0 - (distanceFromMid / halfRange);
            return min + (int) (productivity * (max - min));
        }
    }

    private double calculateInitialTemperature() {
        double targetProbability = config.getInitialAcceptanceProbability();
        int sampleSize = config.getTemperatureSampleSize();

        SchedulingSolution initial = generateInitialSolution();
        double initialFitness = evaluateFitness(initial);
        statistics.incrementEvaluations();
        offerToArchive(initial);

        List<Double> positiveDeltaEs = new ArrayList<>();

        for (int i = 0; i < sampleSize; i++) {
            SchedulingSolution neighbor = generateNeighbor(initial);
            double neighborFitness = evaluateFitness(neighbor);
            statistics.incrementEvaluations();
            offerToArchive(neighbor);

            double deltaE = neighborFitness - initialFitness;
            if (isMinimization() && deltaE > 0) positiveDeltaEs.add(deltaE);
            else if (!isMinimization() && deltaE < 0) positiveDeltaEs.add(-deltaE);
        }

        double avgDeltaE;
        if (positiveDeltaEs.isEmpty()) {
            avgDeltaE = Math.abs(initialFitness) * 0.1;
            if (avgDeltaE < 1.0) avgDeltaE = 100.0;
        } else {
            avgDeltaE = positiveDeltaEs.stream().mapToDouble(Double::doubleValue).average().orElse(100.0);
        }

        return -avgDeltaE / Math.log(targetProbability);
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
            if (objective.isMinimization()) weightedSum += weight * value;
            else weightedSum -= weight * value;
        }

        return weightedSum;
    }

    private boolean isMinimization() {
        if (config.isWeightedSum()) return true;
        return config.getPrimaryObjective().isMinimization();
    }

    private boolean isBetter(double fitness1, double fitness2) {
        if (isMinimization()) return fitness1 < fitness2;
        return fitness1 > fitness2;
    }

    private boolean[] buildMinimizationArray() {
        List<SchedulingObjective> objectives = config.getObjectives();
        boolean[] mins = new boolean[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            mins[i] = objectives.get(i).isMinimization();
        }
        return mins;
    }

    public SAStatistics getStatistics() { return statistics; }
    public double getCurrentTemperature() { return temperature; }
    public SchedulingSolution getCurrentSolution() { return currentSolution; }
    public SchedulingSolution getBestSolution() { return bestSolution; }
    public SAConfiguration getConfig() { return config; }
    public double getPowerCapWatts() { return powerCapWatts; }

    public List<SchedulingSolution> getArchive() {
        if (archive == null) return new ArrayList<>();
        return archive.getMembers();
    }

    public List<SchedulingSolution> getFeasibleArchive() {
        if (archive == null) return new ArrayList<>();
        return archive.getFeasibleMembers();
    }
}
