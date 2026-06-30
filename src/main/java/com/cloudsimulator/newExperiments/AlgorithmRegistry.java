package com.cloudsimulator.newExperiments;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.FirstAvailableTaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.ShortestQueueTaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.WorkloadAwareTaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.EnergyAwareTaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.RoundRobinTaskAssignmentStrategy;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GenerationalGATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GenerationalGAwithDominanceStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SimulatedAnnealingTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SimulatedAnnealingWithDominanceStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_NSGA2TaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_SPEA2TaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_AMOSATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.FitnessEvaluationsTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.AdaptiveCoolingSchedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps an algorithm label to a constructed {@link TaskAssignmentStrategy},
 * faithfully lifted from the legacy runners' {@code createStrategy} switch and
 * {@code create*Strategy} builders. Parameterized by {@link AlgorithmParameters}
 * and the {@link PrimaryObjective}, so the same registry serves the Makespan and
 * WaitingTime studies (the labels {@code GA_<primary>} substitute accordingly).
 *
 * <p>The metaheuristic warm-start seeds are computed from the <em>placed</em>
 * context via the same greedy heuristics ({@code WorkloadAware}/{@code EnergyAware});
 * those heuristics are RNG-free, so seeding does not perturb reproducibility.</p>
 */
public final class AlgorithmRegistry {

    private final AlgorithmParameters p;
    private final PrimaryObjective primary;

    public AlgorithmRegistry(AlgorithmParameters params, PrimaryObjective primary) {
        this.p = params;
        this.primary = primary;
    }

    /** The set of labels this registry understands, in canonical order. */
    public List<String> defaultLabels() {
        String P = primary.csvName();
        List<String> labels = new ArrayList<>();
        labels.add("FirstAvailable");
        labels.add("ShortestQueue");
        labels.add("WorkloadAware");
        labels.add("EnergyAware");
        labels.add("RoundRobin");
        labels.add("GA_" + P);
        labels.add("GA_Energy");
        labels.add("SA_" + P);
        labels.add("SA_Energy");
        labels.add("GA_" + P + "_Dominance");
        labels.add("GA_Energy_Dominance");
        labels.add("SA_" + P + "_Dominance");
        labels.add("SA_Energy_Dominance");
        labels.add("NSGA-II");
        labels.add("SPEA-II");
        labels.add("AMOSA");
        return labels;
    }

    /**
     * Constructs the strategy for {@code label}, computing heuristic seeds from the
     * (already placed) {@code context}. Must be called <em>after</em> VM placement.
     */
    public TaskAssignmentStrategy create(String label, SimulationContext context, long seed) {
        List<Host> hosts = context.getHosts();
        String P = primary.csvName();

        if (label.equals("FirstAvailable")) {
            return new FirstAvailableTaskAssignmentStrategy();
        }
        if (label.equals("ShortestQueue")) {
            return new ShortestQueueTaskAssignmentStrategy();
        }
        if (label.equals("WorkloadAware")) {
            return new WorkloadAwareTaskAssignmentStrategy();
        }
        if (label.equals("EnergyAware")) {
            return new EnergyAwareTaskAssignmentStrategy();
        }
        if (label.equals("RoundRobin")) {
            return new RoundRobinTaskAssignmentStrategy();
        }
        if (label.equals("GA_" + P)) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            return createGAStrategy(primary.newObjective(), createEnergyObjective(hosts), waSeed);
        }
        if (label.equals("GA_Energy")) {
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createGAStrategy(createEnergyObjective(hosts), primary.newObjective(), eaSeed);
        }
        if (label.equals("SA_" + P)) {
            return createSAStrategy(primary.newObjective(), createEnergyObjective(hosts), null);
        }
        if (label.equals("SA_Energy")) {
            return createSAStrategy(createEnergyObjective(hosts), primary.newObjective(), null);
        }
        if (label.equals("GA_" + P + "_Dominance")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            return createGADominanceStrategy(primary.newObjective(), createEnergyObjective(hosts), waSeed);
        }
        if (label.equals("GA_Energy_Dominance")) {
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createGADominanceStrategy(createEnergyObjective(hosts), primary.newObjective(), eaSeed);
        }
        if (label.equals("SA_" + P + "_Dominance")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            return createSADominanceStrategy(primary.newObjective(), createEnergyObjective(hosts), waSeed);
        }
        if (label.equals("SA_Energy_Dominance")) {
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createSADominanceStrategy(createEnergyObjective(hosts), primary.newObjective(), eaSeed);
        }
        if (label.equals("NSGA-II")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createNSGA2Strategy(hosts, seed, waSeed, eaSeed);
        }
        if (label.equals("SPEA-II")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createSPEA2Strategy(hosts, seed, waSeed, eaSeed);
        }
        if (label.equals("AMOSA")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createAMOSAStrategy(hosts, seed, waSeed, eaSeed);
        }
        throw new IllegalArgumentException("Unknown algorithm: " + label);
    }

    // ---- Heuristic warm-start seed (lifted verbatim) ----

    private int[] computeHeuristicSeed(TaskAssignmentStrategy heuristic, SimulationContext context) {
        List<Task> unassigned = new ArrayList<>();
        for (Task t : context.getTasks()) {
            if (!t.isAssigned()) unassigned.add(t);
        }
        List<VM> runningVMs = new ArrayList<>();
        for (VM vm : context.getVms()) {
            if (vm.isAssignedToHost()) runningVMs.add(vm);
        }

        if (runningVMs.isEmpty() || unassigned.isEmpty()) {
            context.resetForRescheduling();
            return null;
        }

        Map<Task, VM> assignments = heuristic.assignAll(unassigned, runningVMs, context.getCurrentTime());

        Map<Long, Integer> vmIdToIndex = new HashMap<>();
        for (int i = 0; i < runningVMs.size(); i++) {
            vmIdToIndex.put(runningVMs.get(i).getId(), i);
        }

        int[] seed = new int[unassigned.size()];
        for (int i = 0; i < unassigned.size(); i++) {
            VM assigned = assignments.get(unassigned.get(i));
            Integer idx = (assigned != null) ? vmIdToIndex.get(assigned.getId()) : null;
            seed[i] = (idx != null) ? idx : (i % runningVMs.size());
        }

        context.resetForRescheduling();
        return seed;
    }

    private EnergyObjective createEnergyObjective(List<Host> hosts) {
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        return energy;
    }

    // ---- Strategy builders (lifted; constants -> AlgorithmParameters) ----

    private TaskAssignmentStrategy createGAStrategy(SchedulingObjective primaryObjective,
                                                   SchedulingObjective tiebreakerObjective,
                                                   int[] heuristicSeed) {
        GAConfiguration.Builder builder = GAConfiguration.builder()
            .populationSize(p.populationSize)
            .crossoverRate(p.crossoverRate)
            .mutationRate(p.mutationRate)
            .elitePercentage(p.gaElitePercentage)
            .tournamentSize(p.gaTournamentSize)
            .addWeightedObjective(primaryObjective, 1.0)
            .addWeightedObjective(tiebreakerObjective, p.tiebreakerWeight)
            .terminationCondition(new GenerationCountTermination(p.iterationCount / p.populationSize - 1))
            .verboseLogging(p.verboseLogging);
        if (heuristicSeed != null) {
            builder.addSeedAssignment(heuristicSeed);
        }
        return new GenerationalGATaskSchedulingStrategy(builder.build());
    }

    private SAConfiguration buildSAConfig(SchedulingObjective primaryObjective,
                                          SchedulingObjective tiebreakerObjective,
                                          int[] heuristicSeed) {
        SAConfiguration.Builder builder = SAConfiguration.builder();

        if (p.saAutoTemperature) {
            builder.autoInitialTemperature(true)
                   .initialAcceptanceProbability(p.saInitialAcceptanceProbability)
                   .temperatureSampleSize(p.saTemperatureSampleSize);
        }

        builder.coolingSchedule(new AdaptiveCoolingSchedule(
                    p.saCoolingBaseRate, p.saCoolingMinRate, p.saCoolingMaxRate,
                    p.saCoolingLowAccept, p.saCoolingHighAccept))
               .iterationsPerTemperature(p.saIterationsPerTemp)
               .terminationCondition(new FitnessEvaluationsTermination(p.iterationCount));

        if (p.saReheatEnabled) {
            builder.reheatEnabled(true)
                   .reheatFactor(p.saReheatFactor)
                   .reheatStagnationThreshold(p.saReheatStagnationThreshold)
                   .maxReheats(p.saMaxReheats);
        }

        if (p.saAdaptiveIterations) {
            builder.adaptiveIterationsEnabled(true)
                   .adaptiveIterationsBounds(p.saMinItersPerTemp, p.saMaxItersPerTemp)
                   .adaptiveIterationsThresholds(p.saAdaptiveItersHighThreshold, p.saAdaptiveItersLowThreshold);
        }

        if (p.saScaledPerturbation) {
            builder.temperatureScaledPerturbation(true)
                   .maxPerturbationMutations(p.saMaxPerturbationMutations);
        }

        builder.addWeightedObjective(primaryObjective, 1.0)
               .addWeightedObjective(tiebreakerObjective, p.tiebreakerWeight)
               .verboseLogging(p.verboseLogging);

        if (heuristicSeed != null) {
            builder.addSeedAssignment(heuristicSeed);
        }

        return builder.build();
    }

    private TaskAssignmentStrategy createSAStrategy(SchedulingObjective primaryObjective,
                                                   SchedulingObjective tiebreakerObjective,
                                                   int[] heuristicSeed) {
        return new SimulatedAnnealingTaskSchedulingStrategy(
            buildSAConfig(primaryObjective, tiebreakerObjective, heuristicSeed));
    }

    private TaskAssignmentStrategy createGADominanceStrategy(SchedulingObjective primaryObjective,
                                                            SchedulingObjective tiebreakerObjective,
                                                            int[] heuristicSeed) {
        GAConfiguration.Builder builder = GAConfiguration.builder()
            .populationSize(p.populationSize)
            .crossoverRate(p.crossoverRate)
            .mutationRate(p.mutationRate)
            .elitePercentage(p.gaElitePercentage)
            .tournamentSize(p.gaTournamentSize)
            .addWeightedObjective(primaryObjective, 1.0)
            .addWeightedObjective(tiebreakerObjective, p.tiebreakerWeight)
            .terminationCondition(new GenerationCountTermination(p.iterationCount / p.populationSize - 1))
            .verboseLogging(p.verboseLogging);
        if (heuristicSeed != null) {
            builder.addSeedAssignment(heuristicSeed);
        }
        return new GenerationalGAwithDominanceStrategy(builder.build());
    }

    private TaskAssignmentStrategy createSADominanceStrategy(SchedulingObjective primaryObjective,
                                                            SchedulingObjective tiebreakerObjective,
                                                            int[] heuristicSeed) {
        return new SimulatedAnnealingWithDominanceStrategy(
            buildSAConfig(primaryObjective, tiebreakerObjective, heuristicSeed));
    }

    private TaskAssignmentStrategy createNSGA2Strategy(List<Host> hosts, long seed, int[] waSeed, int[] eaSeed) {
        EnergyObjective energy = createEnergyObjective(hosts);
        NSGA2Configuration.Builder builder = NSGA2Configuration.builder()
            .populationSize(p.populationSize)
            .crossoverRate(p.crossoverRate)
            .mutationRate(p.mutationRate)
            .addObjective(primary.newObjective())
            .addObjective(energy)
            .terminationCondition(new GenerationCountTermination(p.iterationCount / p.populationSize))
            .randomSeed(seed)
            .verboseLogging(p.verboseLogging);
        if (waSeed != null) builder.addSeedAssignment(waSeed);
        if (eaSeed != null) builder.addSeedAssignment(eaSeed);
        MOEA_NSGA2TaskSchedulingStrategy strategy = new MOEA_NSGA2TaskSchedulingStrategy(builder.build());
        strategy.setSelectionMethod(MOEA_NSGA2TaskSchedulingStrategy.SolutionSelectionMethod.KNEE_POINT);
        return strategy;
    }

    private TaskAssignmentStrategy createSPEA2Strategy(List<Host> hosts, long seed, int[] waSeed, int[] eaSeed) {
        EnergyObjective energy = createEnergyObjective(hosts);
        NSGA2Configuration.Builder builder = NSGA2Configuration.builder()
            .populationSize(p.populationSize)
            .crossoverRate(p.crossoverRate)
            .mutationRate(p.mutationRate)
            .addObjective(primary.newObjective())
            .addObjective(energy)
            .terminationCondition(new GenerationCountTermination(p.iterationCount / p.populationSize))
            .randomSeed(seed)
            .verboseLogging(p.verboseLogging);
        if (waSeed != null) builder.addSeedAssignment(waSeed);
        if (eaSeed != null) builder.addSeedAssignment(eaSeed);
        MOEA_SPEA2TaskSchedulingStrategy strategy = new MOEA_SPEA2TaskSchedulingStrategy(builder.build());
        strategy.setSelectionMethod(MOEA_SPEA2TaskSchedulingStrategy.SolutionSelectionMethod.KNEE_POINT);
        return strategy;
    }

    private TaskAssignmentStrategy createAMOSAStrategy(List<Host> hosts, long seed, int[] waSeed, int[] eaSeed) {
        EnergyObjective energy = createEnergyObjective(hosts);
        NSGA2Configuration.Builder builder = NSGA2Configuration.builder()
            .populationSize(p.amosaSoftLimit)
            .crossoverRate(p.crossoverRate)
            .mutationRate(p.amosaMutationRate)
            .addObjective(primary.newObjective())
            .addObjective(energy)
            .terminationCondition(new FitnessEvaluationsTermination(p.iterationCount))
            .randomSeed(seed)
            .verboseLogging(p.verboseLogging);
        if (waSeed != null) builder.addSeedAssignment(waSeed);
        if (eaSeed != null) builder.addSeedAssignment(eaSeed);
        MOEA_AMOSATaskSchedulingStrategy strategy = new MOEA_AMOSATaskSchedulingStrategy(builder.build());
        strategy.setSelectionMethod(MOEA_AMOSATaskSchedulingStrategy.SolutionSelectionMethod.KNEE_POINT);
        strategy.setInitialTemperature(p.amosaInitialTemperature);
        strategy.setAlpha(p.amosaAlpha);
        strategy.setSoftLimit(p.amosaSoftLimit);
        strategy.setHardLimit(p.amosaHardLimit);
        strategy.setGamma(p.amosaGamma);
        strategy.setIterationsPerTemperature(p.amosaIterationsPerTemp);
        strategy.setHillClimbingIterations(p.amosaHillClimbingIters);
        return strategy;
    }
}
