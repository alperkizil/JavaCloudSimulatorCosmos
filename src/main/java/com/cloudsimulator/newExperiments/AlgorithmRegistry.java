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
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GenerationalGAwithDominancePowerCeilingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SimulatedAnnealingTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SimulatedAnnealingWithDominanceStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SimulatedAnnealingWithDominancePowerCeilingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_NSGA2TaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_SPEA2TaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_AMOSATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_NSGA2PowerCeilingTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_SPEA2PowerCeilingTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_AMOSAPowerCeilingTaskSchedulingStrategy;
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

    /**
     * The default experiment arm set — the <b>7 front-producing metaheuristics</b>
     * (GA/SA dominance-archive ×4 + NSGA-II/SPEA-II/AMOSA), identical across all
     * three studies. Excluded as arms: the greedy heuristics (used only to seed the
     * metaheuristics' initial populations, so scoring them would double-count) and
     * the weighted single-objective GA/SA variants (they emit a single point, not a
     * Pareto set). The {@code create(...)} dispatch still supports every label if
     * requested explicitly.
     */
    public List<String> defaultLabels() {
        String P = primary.csvName();
        List<String> labels = new ArrayList<>();
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
     * The PowerCeiling arm set — the <b>7 base metaheuristics</b> (GA/SA dominance
     * ×4 + NSGA-II/SPEA-II/AMOSA), run <b>uncapped</b>. Power-cap feasibility is
     * assessed after the fact from each solution's coincident Step-8 peak, with the
     * cap thresholds derived dynamically from the observed peak distribution (see
     * {@link PowerCapCalibrator}) — so no fixed {@code _PC_*} search-time caps are
     * baked in. The constrained {@code _PC_} builders remain available via
     * {@code create(...)} for future use but are not part of the default arm set.
     */
    public List<String> defaultPowerCeilingLabels() {
        String P = primary.csvName();
        List<String> labels = new ArrayList<>();
        // Dominance-archive variants of GA/SA
        labels.add("GA_" + P + "_Dominance");
        labels.add("GA_Energy_Dominance");
        labels.add("SA_" + P + "_Dominance");
        labels.add("SA_Energy_Dominance");
        // Multi-objective metaheuristics
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

        // ---- Power-ceiling (constrained) variants: label suffix "_PC_<cap>kW" ----
        // The cap is parsed from the suffix (e.g. "_PC_190kW" -> 190000.0 W) and the
        // build is delegated to createPowerCeiling. (Phase 2 calls that method directly
        // with dynamically-derived caps that don't fit a kW label.)
        if (label.contains("_PC_")) {
            double cap = parsePowerCapWatts(label);
            String core = label.substring(0, label.lastIndexOf("_PC_"));
            TaskAssignmentStrategy pc = createPowerCeiling(core, context, seed, cap);
            if (pc != null) {
                return pc;
            }
        }
        throw new IllegalArgumentException("Unknown algorithm: " + label);
    }

    /**
     * Builds the constrained (power-ceiling) variant of a base metaheuristic
     * {@code core} label at an explicit {@code capWatts}. This is the path Phase 2
     * uses to enforce the dynamically-derived caps (whose values don't fit a
     * {@code "_PC_<n>kW"} label). Returns {@code null} if {@code core} has no
     * power-ceiling variant. Warm-start seeds match the uncapped base builders.
     */
    public TaskAssignmentStrategy createPowerCeiling(String core, SimulationContext context,
                                                     long seed, double capWatts) {
        List<Host> hosts = context.getHosts();
        String P = primary.csvName();
        if (core.equals("NSGA-II")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createNSGA2PowerCeilingStrategy(hosts, seed, waSeed, eaSeed, capWatts);
        }
        if (core.equals("SPEA-II")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createSPEA2PowerCeilingStrategy(hosts, seed, waSeed, eaSeed, capWatts);
        }
        if (core.equals("AMOSA")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createAMOSAPowerCeilingStrategy(hosts, seed, waSeed, eaSeed, capWatts);
        }
        if (core.equals("GA_" + P + "_Dominance")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            return createGADominancePowerCeilingStrategy(
                primary.newObjective(), createEnergyObjective(hosts), waSeed, hosts, capWatts);
        }
        if (core.equals("GA_Energy_Dominance")) {
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createGADominancePowerCeilingStrategy(
                createEnergyObjective(hosts), primary.newObjective(), eaSeed, hosts, capWatts);
        }
        if (core.equals("SA_" + P + "_Dominance")) {
            int[] waSeed = computeHeuristicSeed(new WorkloadAwareTaskAssignmentStrategy(), context);
            return createSADominancePowerCeilingStrategy(
                primary.newObjective(), createEnergyObjective(hosts), waSeed, hosts, capWatts);
        }
        if (core.equals("SA_Energy_Dominance")) {
            int[] eaSeed = computeHeuristicSeed(new EnergyAwareTaskAssignmentStrategy(), context);
            return createSADominancePowerCeilingStrategy(
                createEnergyObjective(hosts), primary.newObjective(), eaSeed, hosts, capWatts);
        }
        return null;
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

    // ---- Power-ceiling (constrained) builders (lifted; constants -> AlgorithmParameters) ----
    // Each keeps its intrinsic search-time power constraint (powerCapWatts). The MOEA
    // variants deliberately do NOT set a selection method: they use constrained
    // (Deb) domination, matching the legacy runner.

    private TaskAssignmentStrategy createNSGA2PowerCeilingStrategy(List<Host> hosts, long seed,
                                                                  int[] waSeed, int[] eaSeed,
                                                                  double powerCapWatts) {
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
        return new MOEA_NSGA2PowerCeilingTaskSchedulingStrategy(builder.build(), powerCapWatts, hosts);
    }

    private TaskAssignmentStrategy createSPEA2PowerCeilingStrategy(List<Host> hosts, long seed,
                                                                  int[] waSeed, int[] eaSeed,
                                                                  double powerCapWatts) {
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
        return new MOEA_SPEA2PowerCeilingTaskSchedulingStrategy(builder.build(), powerCapWatts, hosts);
    }

    private TaskAssignmentStrategy createAMOSAPowerCeilingStrategy(List<Host> hosts, long seed,
                                                                  int[] waSeed, int[] eaSeed,
                                                                  double powerCapWatts) {
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
        MOEA_AMOSAPowerCeilingTaskSchedulingStrategy strategy =
            new MOEA_AMOSAPowerCeilingTaskSchedulingStrategy(builder.build(), powerCapWatts, hosts);
        strategy.setInitialTemperature(p.amosaInitialTemperature);
        strategy.setAlpha(p.amosaAlpha);
        strategy.setSoftLimit(p.amosaSoftLimit);
        strategy.setHardLimit(p.amosaHardLimit);
        strategy.setGamma(p.amosaGamma);
        strategy.setIterationsPerTemperature(p.amosaIterationsPerTemp);
        strategy.setHillClimbingIterations(p.amosaHillClimbingIters);
        return strategy;
    }

    private TaskAssignmentStrategy createGADominancePowerCeilingStrategy(SchedulingObjective primaryObjective,
                                                                        SchedulingObjective tiebreakerObjective,
                                                                        int[] heuristicSeed,
                                                                        List<Host> hosts,
                                                                        double powerCapWatts) {
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
        return new GenerationalGAwithDominancePowerCeilingStrategy(builder.build(), powerCapWatts, hosts);
    }

    private TaskAssignmentStrategy createSADominancePowerCeilingStrategy(SchedulingObjective primaryObjective,
                                                                        SchedulingObjective tiebreakerObjective,
                                                                        int[] heuristicSeed,
                                                                        List<Host> hosts,
                                                                        double powerCapWatts) {
        SAConfiguration config = buildSAConfig(primaryObjective, tiebreakerObjective, heuristicSeed);
        return new SimulatedAnnealingWithDominancePowerCeilingStrategy(config, powerCapWatts, hosts);
    }

    /** Parses the cap (Watts) from a label suffix like {@code "_PC_190kW"} -> {@code 190000.0}. */
    private static double parsePowerCapWatts(String label) {
        int idx = label.lastIndexOf("_PC_");
        if (idx < 0) {
            throw new IllegalArgumentException("Not a power-ceiling label: " + label);
        }
        String token = label.substring(idx + 4); // e.g. "190kW"
        if (!token.endsWith("kW")) {
            throw new IllegalArgumentException("Malformed power-cap suffix in label: " + label);
        }
        return Double.parseDouble(token.substring(0, token.length() - 2)) * 1000.0;
    }
}
