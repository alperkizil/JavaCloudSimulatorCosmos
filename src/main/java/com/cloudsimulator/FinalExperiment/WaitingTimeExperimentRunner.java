package com.cloudsimulator.FinalExperiment;

import com.cloudsimulator.config.*;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
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

// Placement strategies
import com.cloudsimulator.PlacementStrategy.hostPlacement.PowerAwareLoadBalancingHostPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.VMPlacement.BestFitVMPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.MultiObjectiveTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.FirstAvailableTaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.ShortestQueueTaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.WorkloadAwareTaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.RoundRobinTaskAssignmentStrategy;

// Metaheuristics
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GenerationalGATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SimulatedAnnealingTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

// MOEA Framework strategies
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_NSGA2TaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_SPEA2TaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_AMOSATaskSchedulingStrategy;

// Objectives
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.MakespanObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.WaitingTimeObjective;

// Termination & cooling
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.FitnessEvaluationsTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.AdaptiveCoolingSchedule;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.GeometricCoolingSchedule;

// Performance metrics
import com.cloudsimulator.multiobjectivePerformance.PerfMet.PerformanceMetrics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class WaitingTimeExperimentRunner {

    // =========================================================================
    // ALGORITHM PARAMETERS
    // =========================================================================

    // ---- WHICH ALGORITHMS TO RUN ----
    // Edit this list to include/exclude algorithms. Comment out lines to skip.
    // Available: FirstAvailable, ShortestQueue, WorkloadAware, RoundRobin,
    //            GA_WaitingTime, GA_Energy, SA_WaitingTime, SA_Energy,
    //            NSGA-II, SPEA-II, AMOSA
    // Ordering here controls the order they appear in CSVs and plots.
    private static final String[] ALGORITHM_LABELS = {
        // Baseline heuristics (deterministic, non-preemptive, fast)
        "FirstAvailable", "ShortestQueue", "WorkloadAware", "RoundRobin",
        // Single-objective metaheuristics
        "GA_WaitingTime", "GA_Energy", "SA_WaitingTime", "SA_Energy",
        // Multi-objective metaheuristics
        "NSGA-II", "SPEA-II", "AMOSA"
    };

    private static final int POPULATION_SIZE = 200;
    private static final int ITERATION_COUNT = 40000;
    private static final double CROSSOVER_RATE = 0.95;
    private static final double MUTATION_RATE = 0.05;
    private static final double GA_ELITE_PERCENTAGE = 0.20;
    private static final int GA_TOURNAMENT_SIZE = 5;
    private static final boolean SA_AUTO_TEMPERATURE = true;
    private static final double SA_INITIAL_ACCEPTANCE_PROBABILITY = 0.8;
    private static final int SA_TEMPERATURE_SAMPLE_SIZE = 100;
    private static final int SA_ITERATIONS_PER_TEMP = 200;
    private static final boolean SA_REHEAT_ENABLED = true;
    private static final double SA_REHEAT_FACTOR = 5.0;
    private static final int SA_REHEAT_STAGNATION_THRESHOLD = 15;
    private static final int SA_MAX_REHEATS = 3;
    private static final boolean SA_ADAPTIVE_ITERATIONS = true;
    private static final int SA_MIN_ITERS_PER_TEMP = 50;
    private static final int SA_MAX_ITERS_PER_TEMP = 400;
    private static final boolean SA_SCALED_PERTURBATION = true;
    private static final int SA_MAX_PERTURBATION_MUTATIONS = 4;
    private static final long BASE_SEED = 200L;
    private static final int NUM_RUNS = 10;
    private static final boolean VERBOSE_LOGGING = true;
    private static final double TIEBREAKER_WEIGHT = 0.001;

    // AMOSA-specific parameters (AMOSA formula is inverted vs standard SA: prob=1/(1+exp(ΔDom*T)),
    // so high T means LESS acceptance of worse solutions).
    // ΔDom uses GEOMETRIC MEAN of normalized diffs — for 2 objectives with 30% diffs,
    // geo-mean ΔDom ≈ 0.3. This gives the sigmoid proper discrimination.
    //
    // Budget allocation (gamma=2.0, SL=100, hillClimbing=50):
    //   Init: 200 solutions × 50 hill-climb iters = ~10,200 evals (25% of budget)
    //   Main loop: ~29,800 evals → 142 temp steps at 200 iters/step
    //   T sweep: 15 × 0.95^142 ≈ 0.011 (acceptance: 1-18% → ~50%)
    private static final double AMOSA_INITIAL_TEMPERATURE = 15.0;
    private static final double AMOSA_ALPHA = 0.95;
    private static final int AMOSA_HARD_LIMIT = 50;
    private static final int AMOSA_SOFT_LIMIT = 100;
    private static final int AMOSA_ITERATIONS_PER_TEMP = 200;
    private static final int AMOSA_HILL_CLIMBING_ITERS = 50;
    private static final double AMOSA_GAMMA = 2.0;
    private static final double AMOSA_MUTATION_RATE = 0.01;

    private static final String REPORTS_BASE_DIR = "reports";
    private static String REPORTS_DIR; // Set at runtime with timestamp

    // CPU workload types (6)
    private static final WorkloadType[] CPU_WORKLOADS = {
        WorkloadType.SEVEN_ZIP, WorkloadType.DATABASE, WorkloadType.LLM_CPU,
        WorkloadType.IMAGE_GEN_CPU, WorkloadType.CINEBENCH, WorkloadType.VERACRYPT
    };

    // GPU workload types (3)
    private static final WorkloadType[] GPU_WORKLOADS = {
        WorkloadType.FURMARK, WorkloadType.IMAGE_GEN_GPU, WorkloadType.LLM_GPU
    };

    // Varied instruction lengths (1B - 15B) for task diversity
    private static final long[] INSTRUCTION_LENGTHS = {
        1_000_000_000L, 2_000_000_000L, 3_000_000_000L, 4_000_000_000L,
        5_000_000_000L, 7_000_000_000L, 8_000_000_000L, 10_000_000_000L,
        12_000_000_000L, 15_000_000_000L
    };

    // Scenario task distributions: [cpuTasks, gpuTasks]
    // More tasks than VMs to create contention and force real trade-offs
    private static final int[][] SCENARIO_TASK_COUNTS = {
        {250, 250},  // Scenario 1: Balanced
        {100, 400},  // Scenario 2: GPU Stress
        {400, 100}   // Scenario 3: CPU Stress
    };

    private static final String[] SCENARIO_NAMES = {
        "Balanced", "GPU_Stress", "CPU_Stress"
    };

    // =========================================================================
    // DATA CLASSES
    // =========================================================================

    static class AlgorithmResult {
        final String label;
        final long seed;
        final List<double[]> solutions; // each: [makespan, energy]
        final long executionTimeMs;
        List<double[]> nonDominatedSolutions;
        double hv, gd, igd, spacing;
        int paretoContribution;

        AlgorithmResult(String label, long seed, List<double[]> solutions, long executionTimeMs) {
            this.label = label;
            this.seed = seed;
            this.solutions = solutions;
            this.executionTimeMs = executionTimeMs;
        }
    }

    static class ScenarioResult {
        final int scenarioNumber;
        final String scenarioName;
        // Per-seed results: algorithmLabel -> list of AlgorithmResult (one per seed)
        final Map<String, List<AlgorithmResult>> perSeedResults = new LinkedHashMap<>();
        List<double[]> universalParetoSet;
        double universalHV;

        ScenarioResult(int scenarioNumber, String scenarioName) {
            this.scenarioNumber = scenarioNumber;
            this.scenarioName = scenarioName;
        }
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    public static void main(String[] args) {
        // Stamp output folder with current date-time (dd_MM_yyyy_HH_mm)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd_MM_yyyy_HH_mm"));
        REPORTS_DIR = REPORTS_BASE_DIR + "/" + timestamp;

        System.out.println("============================================================");
        System.out.println("  WAITING TIME vs ENERGY EXPERIMENT RUNNER");
        System.out.println("  Algorithms: GA(WT), GA(E), SA(WT), SA(E), NSGA-II, SPEA-II, AMOSA");
        System.out.println("  Objectives: WaitingTime (s) vs Energy (kWh)");
        System.out.println("  Scenarios: Balanced, GPU Stress, CPU Stress");
        System.out.println("  Seeds: " + BASE_SEED + "-" + (BASE_SEED + NUM_RUNS - 1) + " (" + NUM_RUNS + " runs each)");
        System.out.println("  Infrastructure: 40 hosts, 60 VMs, 500 tasks/scenario");
        System.out.println("============================================================");
        System.out.println();

        try {
            Files.createDirectories(Paths.get(REPORTS_DIR));
        } catch (IOException e) {
            System.err.println("ERROR: Could not create reports directory: " + e.getMessage());
            return;
        }

        List<ScenarioResult> allResults = new ArrayList<>();

        for (int s = 0; s < 3; s++) {
            int scenarioNum = s + 1;
            String scenarioName = SCENARIO_NAMES[s];
            System.out.println();
            System.out.println("************************************************************");
            System.out.println("  SCENARIO " + scenarioNum + ": " + scenarioName);
            System.out.println("  Tasks: " + SCENARIO_TASK_COUNTS[s][0] + " CPU + " +
                SCENARIO_TASK_COUNTS[s][1] + " GPU = " +
                (SCENARIO_TASK_COUNTS[s][0] + SCENARIO_TASK_COUNTS[s][1]) + " total");
            System.out.println("************************************************************");

            ScenarioResult scenarioResult = new ScenarioResult(scenarioNum, scenarioName);

            // Run each algorithm across all seeds, storing per-seed results
            for (String label : ALGORITHM_LABELS) {
                System.out.println();
                System.out.println("------------------------------------------------------------");
                System.out.println("  Running: " + label + " (Scenario " + scenarioNum +
                    ", " + NUM_RUNS + " seeds: " + BASE_SEED + "-" + (BASE_SEED + NUM_RUNS - 1) + ")");
                System.out.println("------------------------------------------------------------");

                List<AlgorithmResult> seedResults = new ArrayList<>();

                for (int run = 0; run < NUM_RUNS; run++) {
                    long seed = BASE_SEED + run;
                    System.out.println("  [Run " + (run + 1) + "/" + NUM_RUNS + "] seed=" + seed);

                    try {
                        ExperimentConfiguration config = buildScenarioConfig(scenarioNum, seed);
                        AlgorithmResult runResult = runAlgorithm(label, config, seed);
                        seedResults.add(runResult);
                        System.out.println("    Completed in " + runResult.executionTimeMs + " ms, " +
                            runResult.solutions.size() + " solution(s)");
                    } catch (Throwable e) {
                        System.err.println("    ERROR in run " + (run + 1) + " (seed=" + seed + "): " + e.getMessage());
                        e.printStackTrace();
                        List<double[]> fallback = new ArrayList<>();
                        fallback.add(new double[]{Double.MAX_VALUE, Double.MAX_VALUE});
                        seedResults.add(new AlgorithmResult(label, seed, fallback, 0));
                    }
                }

                scenarioResult.perSeedResults.put(label, seedResults);
                long successCount = seedResults.stream().filter(r -> r.executionTimeMs > 0).count();
                long avgTime = successCount > 0
                    ? seedResults.stream().filter(r -> r.executionTimeMs > 0)
                        .mapToLong(r -> r.executionTimeMs).sum() / successCount
                    : 0;
                int totalSolutions = seedResults.stream().mapToInt(r -> r.solutions.size()).sum();
                System.out.println("  " + label + " complete: " + totalSolutions +
                    " total solutions across " + NUM_RUNS + " runs (" + successCount +
                    " successful), avg time " + avgTime + " ms");
            }

            // Compute Universal Pareto front from all solutions across all seeds
            System.out.println();
            System.out.println("  Computing Universal Pareto front...");
            scenarioResult.universalParetoSet = computeUniversalPareto(scenarioResult.perSeedResults);
            System.out.println("  Universal Pareto size: " + scenarioResult.universalParetoSet.size());

            // Calculate Pareto contributions
            calculateParetoContributions(scenarioResult);

            // Calculate performance metrics
            System.out.println("  Calculating performance metrics...");
            calculatePerformanceMetrics(scenarioResult);

            // Generate CSVs for this scenario
            generateScenarioCSVs(scenarioResult);

            // Print summary table
            printScenarioSummary(scenarioResult);

            allResults.add(scenarioResult);
        }

        // Generate cross-scenario summary
        generateExperimentSummary(allResults);
        generatePlotOptionsJSON();

        // Try to run Python plotter
        executePythonPlotter();

        System.out.println();
        System.out.println("============================================================");
        System.out.println("  EXPERIMENT COMPLETE");
        System.out.println("  Results saved to: " + REPORTS_DIR);
        System.out.println("============================================================");
    }

    // =========================================================================
    // CONFIGURATION BUILDER
    // =========================================================================

    private static ExperimentConfiguration buildScenarioConfig(int scenario, long seed) {
        ExperimentConfiguration config = new ExperimentConfiguration();
        config.setRandomSeed(seed);

        // 1 Datacenter
        config.addDatacenterConfig(new DatacenterConfig("DC-Experiment", 50, 400000.0));

        // 40 Hosts (16 CPU, 12 GPU, 12 MIXED)
        // RAM, bandwidth, and storage are generous so only CPU cores and GPUs constrain placement
        for (int i = 0; i < 16; i++) {
            config.addHostConfig(new HostConfig(
                2_500_000_000L, 16, ComputeType.CPU_ONLY, 0,
                65536, 20000, 2097152, "StandardPowerModel"));
        }
        for (int i = 0; i < 12; i++) {
            config.addHostConfig(new HostConfig(
                2_800_000_000L, 8, ComputeType.GPU_ONLY, 4,
                65536, 20000, 2097152, "HighPerformancePowerModel"));
        }
        for (int i = 0; i < 12; i++) {
            config.addHostConfig(new HostConfig(
                3_000_000_000L, 32, ComputeType.CPU_GPU_MIXED, 4,
                131072, 20000, 2097152, "HighPerformancePowerModel"));
        }

        // 60 VMs with DIVERSE speeds to create waiting-time vs energy trade-off.
        // Speed range is 10x (500M to 5B IPS) — matches real-world cloud VM family spread
        // (e.g. burstable vs. compute-optimized instances) and combines with the
        // DVFS-like power scaling exponent (2.0) to give a wide Pareto front.
        // CPU VMs: 8 fast + 8 medium + 4 slow = 20
        for (int i = 0; i < 8; i++) {
            config.addVMConfig(new VMConfig("ExperimentUser",
                5_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));  // fast
        }
        for (int i = 0; i < 8; i++) {
            config.addVMConfig(new VMConfig("ExperimentUser",
                2_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));  // medium
        }
        for (int i = 0; i < 4; i++) {
            config.addVMConfig(new VMConfig("ExperimentUser",
                500_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));  // slow
        }

        // GPU VMs: 8 fast + 8 medium + 4 slow = 20
        for (int i = 0; i < 8; i++) {
            config.addVMConfig(new VMConfig("ExperimentUser",
                5_000_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.GPU_ONLY));  // fast
        }
        for (int i = 0; i < 8; i++) {
            config.addVMConfig(new VMConfig("ExperimentUser",
                2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));  // medium
        }
        for (int i = 0; i < 4; i++) {
            config.addVMConfig(new VMConfig("ExperimentUser",
                500_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));  // slow
        }

        // Mixed VMs: 8 fast + 8 medium + 4 slow = 20
        for (int i = 0; i < 8; i++) {
            config.addVMConfig(new VMConfig("ExperimentUser",
                5_000_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));  // fast
        }
        for (int i = 0; i < 8; i++) {
            config.addVMConfig(new VMConfig("ExperimentUser",
                2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));  // medium
        }
        for (int i = 0; i < 4; i++) {
            config.addVMConfig(new VMConfig("ExperimentUser",
                500_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));  // slow
        }

        // Tasks — scenario-specific distribution
        int idx = scenario - 1;
        int cpuCount = SCENARIO_TASK_COUNTS[idx][0];
        int gpuCount = SCENARIO_TASK_COUNTS[idx][1];

        List<TaskConfig> tasks = new ArrayList<>();
        tasks.addAll(generateTasks(cpuCount, CPU_WORKLOADS, "CPU", scenario));
        tasks.addAll(generateTasks(gpuCount, GPU_WORKLOADS, "GPU", scenario));
        for (TaskConfig tc : tasks) {
            config.addTaskConfig(tc);
        }

        // UserConfig with task counts
        Map<WorkloadType, Integer> taskCounts = new HashMap<>();
        for (TaskConfig tc : tasks) {
            taskCounts.merge(tc.getWorkloadType(), 1, Integer::sum);
        }
        config.addUserConfig(new UserConfig("ExperimentUser",
            List.of("DC-Experiment"), 5, 5, 5, taskCounts));

        return config;
    }

    private static List<TaskConfig> generateTasks(int count, WorkloadType[] types,
            String prefix, int scenario) {
        List<TaskConfig> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WorkloadType wt = types[i % types.length];
            long instrLen = INSTRUCTION_LENGTHS[i % INSTRUCTION_LENGTHS.length];
            String name = "S" + scenario + "_" + prefix + "_" + wt.name() + "_" + i;
            tasks.add(new TaskConfig(name, "ExperimentUser", instrLen, wt));
        }
        return tasks;
    }

    // =========================================================================
    // STRATEGY CREATION
    // =========================================================================

    private static TaskAssignmentStrategy createStrategy(String label, List<Host> hosts, long seed) {
        switch (label) {
            case "FirstAvailable":
                return new FirstAvailableTaskAssignmentStrategy();
            case "ShortestQueue":
                return new ShortestQueueTaskAssignmentStrategy();
            case "WorkloadAware":
                return new WorkloadAwareTaskAssignmentStrategy();
            case "RoundRobin":
                return new RoundRobinTaskAssignmentStrategy();
            case "GA_WaitingTime":
                return createGAStrategy(new WaitingTimeObjective(), createEnergyObjective(hosts));
            case "GA_Energy":
                return createGAStrategy(createEnergyObjective(hosts), new WaitingTimeObjective());
            case "SA_WaitingTime":
                return createSAStrategy(new WaitingTimeObjective(), createEnergyObjective(hosts));
            case "SA_Energy":
                return createSAStrategy(createEnergyObjective(hosts), new WaitingTimeObjective());
            case "NSGA-II":
                return createNSGA2Strategy(hosts, seed);
            case "SPEA-II":
                return createSPEA2Strategy(hosts, seed);
            case "AMOSA":
                return createAMOSAStrategy(hosts, seed);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + label);
        }
    }

    private static EnergyObjective createEnergyObjective(List<Host> hosts) {
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        return energy;
    }

    private static TaskAssignmentStrategy createGAStrategy(
            com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective primaryObjective,
            com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective tiebreakerObjective) {
        GAConfiguration config = GAConfiguration.builder()
            .populationSize(POPULATION_SIZE)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(MUTATION_RATE)
            .elitePercentage(GA_ELITE_PERCENTAGE)
            .tournamentSize(GA_TOURNAMENT_SIZE)
            .addWeightedObjective(primaryObjective, 1.0)
            .addWeightedObjective(tiebreakerObjective, TIEBREAKER_WEIGHT)
            .terminationCondition(new GenerationCountTermination(ITERATION_COUNT / POPULATION_SIZE - 1))
            .verboseLogging(VERBOSE_LOGGING)
            .build();
        return new GenerationalGATaskSchedulingStrategy(config);
    }

    private static TaskAssignmentStrategy createSAStrategy(
            com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective primaryObjective,
            com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective tiebreakerObjective) {
        SAConfiguration.Builder builder = SAConfiguration.builder();

        if (SA_AUTO_TEMPERATURE) {
            builder.autoInitialTemperature(true)
                   .initialAcceptanceProbability(SA_INITIAL_ACCEPTANCE_PROBABILITY)
                   .temperatureSampleSize(SA_TEMPERATURE_SAMPLE_SIZE);
        }

        builder.coolingSchedule(new AdaptiveCoolingSchedule(0.5, 0.15, 0.90, 0.97, 0.995))
               .iterationsPerTemperature(SA_ITERATIONS_PER_TEMP)
               .terminationCondition(new FitnessEvaluationsTermination(ITERATION_COUNT));

        if (SA_REHEAT_ENABLED) {
            builder.reheatEnabled(true)
                   .reheatFactor(SA_REHEAT_FACTOR)
                   .reheatStagnationThreshold(SA_REHEAT_STAGNATION_THRESHOLD)
                   .maxReheats(SA_MAX_REHEATS);
        }

        if (SA_ADAPTIVE_ITERATIONS) {
            builder.adaptiveIterationsEnabled(true)
                   .adaptiveIterationsBounds(SA_MIN_ITERS_PER_TEMP, SA_MAX_ITERS_PER_TEMP)
                   .adaptiveIterationsThresholds(0.7, 0.1);
        }

        if (SA_SCALED_PERTURBATION) {
            builder.temperatureScaledPerturbation(true)
                   .maxPerturbationMutations(SA_MAX_PERTURBATION_MUTATIONS);
        }

        builder.addWeightedObjective(primaryObjective, 1.0)
               .addWeightedObjective(tiebreakerObjective, TIEBREAKER_WEIGHT)
               .verboseLogging(VERBOSE_LOGGING);

        SAConfiguration config = builder.build();
        return new SimulatedAnnealingTaskSchedulingStrategy(config);
    }

    private static TaskAssignmentStrategy createNSGA2Strategy(List<Host> hosts, long seed) {
        WaitingTimeObjective waitingTime = new WaitingTimeObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(POPULATION_SIZE)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(MUTATION_RATE)
            .addObjective(waitingTime)
            .addObjective(energy)
            .terminationCondition(new GenerationCountTermination(ITERATION_COUNT / POPULATION_SIZE))
            .randomSeed(seed)
            .verboseLogging(VERBOSE_LOGGING)
            .build();
        MOEA_NSGA2TaskSchedulingStrategy strategy = new MOEA_NSGA2TaskSchedulingStrategy(config);
        strategy.setSelectionMethod(MOEA_NSGA2TaskSchedulingStrategy.SolutionSelectionMethod.KNEE_POINT);
        return strategy;
    }

    private static TaskAssignmentStrategy createSPEA2Strategy(List<Host> hosts, long seed) {
        WaitingTimeObjective waitingTime = new WaitingTimeObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(POPULATION_SIZE)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(MUTATION_RATE)
            .addObjective(waitingTime)
            .addObjective(energy)
            .terminationCondition(new GenerationCountTermination(ITERATION_COUNT / POPULATION_SIZE))
            .randomSeed(seed)
            .verboseLogging(VERBOSE_LOGGING)
            .build();
        MOEA_SPEA2TaskSchedulingStrategy strategy = new MOEA_SPEA2TaskSchedulingStrategy(config);
        strategy.setSelectionMethod(MOEA_SPEA2TaskSchedulingStrategy.SolutionSelectionMethod.KNEE_POINT);
        return strategy;
    }

    private static TaskAssignmentStrategy createAMOSAStrategy(List<Host> hosts, long seed) {
        WaitingTimeObjective waitingTime = new WaitingTimeObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(AMOSA_SOFT_LIMIT)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(AMOSA_MUTATION_RATE)
            .addObjective(waitingTime)
            .addObjective(energy)
            .terminationCondition(new FitnessEvaluationsTermination(ITERATION_COUNT))
            .randomSeed(seed)
            .verboseLogging(VERBOSE_LOGGING)
            .build();
        MOEA_AMOSATaskSchedulingStrategy strategy = new MOEA_AMOSATaskSchedulingStrategy(config);
        strategy.setSelectionMethod(MOEA_AMOSATaskSchedulingStrategy.SolutionSelectionMethod.KNEE_POINT);
        strategy.setInitialTemperature(AMOSA_INITIAL_TEMPERATURE);
        strategy.setAlpha(AMOSA_ALPHA);
        strategy.setSoftLimit(AMOSA_SOFT_LIMIT);
        strategy.setHardLimit(AMOSA_HARD_LIMIT);
        strategy.setGamma(AMOSA_GAMMA);
        strategy.setIterationsPerTemperature(AMOSA_ITERATIONS_PER_TEMP);
        strategy.setHillClimbingIterations(AMOSA_HILL_CLIMBING_ITERS);
        return strategy;
    }

    // =========================================================================
    // ALGORITHM PIPELINE (Steps 1-8, skip 9-10)
    // =========================================================================

    private static AlgorithmResult runAlgorithm(String label,
            ExperimentConfiguration baseConfig, long seed) {

        long startTime = System.currentTimeMillis();

        // Clone config and reinitialize random generator
        ExperimentConfiguration config = baseConfig.clone();
        RandomGenerator.initialize(seed);

        // Fresh simulation context
        SimulationContext context = new SimulationContext();

        // Step 1: Initialization
        InitializationStep initStep = new InitializationStep(config);
        initStep.execute(context);

        // Step 2: Host Placement
        HostPlacementStep hostStep = new HostPlacementStep(
            new PowerAwareLoadBalancingHostPlacementStrategy());
        hostStep.execute(context);

        // Step 3: User-Datacenter Mapping
        UserDatacenterMappingStep userStep = new UserDatacenterMappingStep();
        userStep.execute(context);

        // Step 4: VM Placement
        VMPlacementStep vmStep = new VMPlacementStep(new BestFitVMPlacementStrategy());
        vmStep.execute(context);

        // Create strategy AFTER step 4 so hosts are available for EnergyObjective
        TaskAssignmentStrategy strategy = createStrategy(label, context.getHosts(), seed);

        // Step 5: Task Assignment (TIMED)
        long assignStart = System.currentTimeMillis();
        TaskAssignmentStep taskAssignStep = new TaskAssignmentStep(strategy);
        taskAssignStep.execute(context);
        long assignTime = System.currentTimeMillis() - assignStart;
        System.out.println("  Task assignment took: " + assignTime + " ms");

        // Step 6: VM Execution (for the selected/single solution)
        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        // Step 7: Task Execution Analysis
        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        // Step 8: Energy Calculation
        EnergyCalculationStep energyCalcStep = new EnergyCalculationStep();
        energyCalcStep.execute(context);

        // Debug: show actual simulation result for the selected solution
        double actualWaitingTime = taskExecStep.getAverageWaitingTime();
        double actualEnergyKWh = energyCalcStep.getTotalITEnergyKWh();
        double actualMakespan = taskExecStep.getMakespan();
        System.out.printf("  [DEBUG] Selected solution simulation: avgWaitingTime=%.2f s, energy=%.6f kWh, makespan=%d s%n",
            actualWaitingTime, actualEnergyKWh, (long) actualMakespan);

        // Extract solutions: simulate all Pareto front solutions for MO algorithms
        List<double[]> solutions;
        if (strategy instanceof MultiObjectiveTaskSchedulingStrategy) {
            solutions = simulateAllParetoSolutions(strategy, context);
            if (solutions.isEmpty()) {
                solutions = new ArrayList<>();
                solutions.add(new double[]{actualWaitingTime, actualEnergyKWh});
            }
        } else {
            solutions = new ArrayList<>();
            solutions.add(new double[]{actualWaitingTime, actualEnergyKWh});
        }

        long endTime = System.currentTimeMillis();
        return new AlgorithmResult(label, seed, solutions, endTime - startTime);
    }

    // =========================================================================
    // PARETO FRONT SIMULATION
    // =========================================================================

    /**
     * Simulates every solution in the Pareto front through Steps 6-8.
     * For each solution: resets all simulation state (tasks, VMs, hosts,
     * datacenters), applies the solution's task assignments, then runs
     * the full VM execution, task analysis, and energy calculation pipeline.
     *
     * This ensures multi-objective algorithm results reflect actual simulation
     * outcomes rather than the optimizer's internal estimates.
     */
    private static List<double[]> simulateAllParetoSolutions(
            TaskAssignmentStrategy strategy, SimulationContext context) {

        MultiObjectiveTaskSchedulingStrategy moStrategy =
            (MultiObjectiveTaskSchedulingStrategy) strategy;
        ParetoFront front = moStrategy.getLastParetoFront();

        if (front == null || front.isEmpty()) {
            return new ArrayList<>();
        }

        List<SchedulingSolution> paretoSolutions = front.getSolutions();
        List<double[]> simulatedResults = new ArrayList<>();

        // Pre-compute the running VMs list (order preserved across resets
        // since resetForRescheduling preserves host-VM assignments)
        List<VM> runningVMs = new ArrayList<>();
        for (VM vm : context.getVms()) {
            if (vm.isAssignedToHost()) {
                runningVMs.add(vm);
            }
        }

        System.out.println("  Simulating " + paretoSolutions.size() +
            " Pareto front solutions through full pipeline (Steps 6-8)...");

        for (int i = 0; i < paretoSolutions.size(); i++) {
            SchedulingSolution solution = paretoSolutions.get(i);

            // === FULL STATE RESET ===
            // Reset tasks, VMs, hosts, and clock (preserves infrastructure placement)
            context.resetForRescheduling();

            // Reset datacenter statistics (not covered by context.resetForRescheduling)
            for (CloudDatacenter dc : context.getDatacenters()) {
                dc.setActiveSeconds(0);
                dc.setTotalMomentaryPowerDraw(0.0);
            }

            // Apply this solution's task-to-VM assignments
            moStrategy.applySolution(solution, context.getTasks(), runningVMs,
                context.getCurrentTime());

            // Step 6: VM Execution
            VMExecutionStep vmExec = new VMExecutionStep();
            vmExec.execute(context);

            // Step 7: Task Execution Analysis
            TaskExecutionStep taskExec = new TaskExecutionStep();
            taskExec.execute(context);

            // Step 8: Energy Calculation
            EnergyCalculationStep energyCalc = new EnergyCalculationStep();
            energyCalc.execute(context);

            double simWaitingTime = taskExec.getAverageWaitingTime();
            double simEnergy = energyCalc.getTotalITEnergyKWh();
            simulatedResults.add(new double[]{simWaitingTime, simEnergy});

            if ((i + 1) % 10 == 0 || i == paretoSolutions.size() - 1) {
                System.out.printf("    Simulated %d/%d solutions (latest: avgWaitingTime=%.2f s, energy=%.6f kWh)%n",
                    i + 1, paretoSolutions.size(), simWaitingTime, simEnergy);
            }
        }

        return simulatedResults;
    }

    // =========================================================================
    // UNIVERSAL PARETO COMPUTATION
    // =========================================================================

    private static List<double[]> computeUniversalPareto(
            Map<String, List<AlgorithmResult>> perSeedResults) {

        List<double[]> allSolutions = new ArrayList<>();
        for (List<AlgorithmResult> seedResults : perSeedResults.values()) {
            for (AlgorithmResult result : seedResults) {
                allSolutions.addAll(result.solutions);
            }
        }

        List<double[]> universalPareto = new ArrayList<>();
        for (double[] candidate : allSolutions) {
            boolean isDominated = false;
            for (double[] other : allSolutions) {
                if (candidate != other && dominates(other, candidate)) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                boolean duplicate = false;
                for (double[] existing : universalPareto) {
                    if (Math.abs(existing[0] - candidate[0]) < 1e-9 &&
                        Math.abs(existing[1] - candidate[1]) < 1e-9) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    universalPareto.add(candidate);
                }
            }
        }

        universalPareto.sort((a, b) -> Double.compare(a[0], b[0]));
        return universalPareto;
    }

    private static boolean dominates(double[] a, double[] b) {
        boolean atLeastOneBetter = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > b[i]) return false;
            if (a[i] < b[i]) atLeastOneBetter = true;
        }
        return atLeastOneBetter;
    }

    private static List<double[]> filterToNonDominated(List<double[]> solutions) {
        if (solutions.size() <= 1) return new ArrayList<>(solutions);

        List<double[]> nonDominated = new ArrayList<>();
        for (double[] candidate : solutions) {
            boolean isDominated = false;
            for (double[] other : solutions) {
                if (candidate != other && dominates(other, candidate)) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                boolean duplicate = false;
                for (double[] existing : nonDominated) {
                    if (Math.abs(existing[0] - candidate[0]) < 1e-9 &&
                        Math.abs(existing[1] - candidate[1]) < 1e-9) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) nonDominated.add(candidate);
            }
        }
        nonDominated.sort((a, b) -> Double.compare(a[0], b[0]));
        return nonDominated;
    }

    private static boolean isInUniversalPareto(double[] sol, List<double[]> universalPareto) {
        for (double[] u : universalPareto) {
            if (Math.abs(u[0] - sol[0]) < 1e-9 && Math.abs(u[1] - sol[1]) < 1e-9)
                return true;
        }
        return false;
    }

    // =========================================================================
    // PARETO CONTRIBUTIONS & PERFORMANCE METRICS
    // =========================================================================

    private static void calculateParetoContributions(ScenarioResult scenarioResult) {
        System.out.println("  Pareto contributions:");
        for (Map.Entry<String, List<AlgorithmResult>> entry : scenarioResult.perSeedResults.entrySet()) {
            int totalContribution = 0;
            for (AlgorithmResult ar : entry.getValue()) {
                int contribution = 0;
                for (double[] sol : ar.solutions) {
                    if (isInUniversalPareto(sol, scenarioResult.universalParetoSet)) {
                        contribution++;
                    }
                }
                ar.paretoContribution = contribution;
                totalContribution += contribution;
            }
            System.out.println("    " + entry.getKey() + ": " + totalContribution +
                " / " + scenarioResult.universalParetoSet.size() + " (across " + NUM_RUNS + " seeds)");
        }
    }

    private static void calculatePerformanceMetrics(ScenarioResult scenarioResult) {
        // Build universal Pareto reference list (shared across all per-seed computations)
        ArrayList<ArrayList<Double>> universalList = new ArrayList<>();
        for (double[] sol : scenarioResult.universalParetoSet) {
            ArrayList<Double> point = new ArrayList<>();
            point.add(sol[0]);
            point.add(sol[1]);
            universalList.add(point);
        }

        // Compute universal HV once
        ArrayList<ArrayList<ArrayList<Double>>> universalOnly = new ArrayList<>();
        universalOnly.add(universalList);
        PerformanceMetrics universalPM = new PerformanceMetrics(universalOnly, 0);
        scenarioResult.universalHV = universalPM.HV(0);

        // Compute per-seed metrics for each algorithm against the universal Pareto
        for (Map.Entry<String, List<AlgorithmResult>> entry : scenarioResult.perSeedResults.entrySet()) {
            for (AlgorithmResult ar : entry.getValue()) {
                ar.nonDominatedSolutions = filterToNonDominated(ar.solutions);

                ArrayList<ArrayList<Double>> seedParetoList = new ArrayList<>();
                for (double[] sol : ar.nonDominatedSolutions) {
                    ArrayList<Double> point = new ArrayList<>();
                    point.add(sol[0]);
                    point.add(sol[1]);
                    seedParetoList.add(point);
                }
                if (seedParetoList.isEmpty()) {
                    ArrayList<Double> dummy = new ArrayList<>();
                    dummy.add(Double.MAX_VALUE);
                    dummy.add(Double.MAX_VALUE);
                    seedParetoList.add(dummy);
                }

                // Build 2-entry array: [0] = this seed's solutions, [1] = universal reference
                ArrayList<ArrayList<ArrayList<Double>>> pair = new ArrayList<>();
                pair.add(seedParetoList);
                pair.add(universalList);
                PerformanceMetrics pm = new PerformanceMetrics(pair, 1);

                try {
                    ar.hv = pm.HV(0);
                    ar.gd = pm.GD(0);
                    ar.igd = pm.IGD(0);
                    ar.spacing = ar.nonDominatedSolutions.size() > 1
                        ? pm.Spacing(0) : 0.0;
                } catch (Exception e) {
                    System.err.println("  WARNING: Metrics failed for " + ar.label +
                        " seed=" + ar.seed + ": " + e.getMessage());
                    ar.hv = 0;
                    ar.gd = Double.MAX_VALUE;
                    ar.igd = Double.MAX_VALUE;
                    ar.spacing = 0;
                }
            }
        }
    }

    // =========================================================================
    // CONSOLE SUMMARY
    // =========================================================================

    private static void printScenarioSummary(ScenarioResult result) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  SCENARIO " + result.scenarioNumber + " (" + result.scenarioName +
            ") RESULTS (mean ± stddev over " + NUM_RUNS + " seeds)");
        System.out.println("============================================================");
        System.out.printf("%-15s | %-18s | %-18s | %-18s | %-18s | %-6s%n",
            "Algorithm", "HV", "GD", "IGD", "Spacing", "PCont");
        System.out.println("-".repeat(105));

        for (Map.Entry<String, List<AlgorithmResult>> entry : result.perSeedResults.entrySet()) {
            List<AlgorithmResult> seeds = entry.getValue();
            double[] hvs = seeds.stream().mapToDouble(a -> a.hv).toArray();
            double[] gds = seeds.stream().mapToDouble(a -> a.gd).toArray();
            double[] igds = seeds.stream().mapToDouble(a -> a.igd).toArray();
            double[] spacings = seeds.stream().mapToDouble(a -> a.spacing).toArray();
            int totalPCont = seeds.stream().mapToInt(a -> a.paretoContribution).sum();

            System.out.printf("%-15s | %8.6f±%-8.6f | %8.6f±%-8.6f | %8.6f±%-8.6f | %8.6f±%-8.6f | %-6d%n",
                entry.getKey(),
                mean(hvs), stddev(hvs),
                mean(gds), stddev(gds),
                mean(igds), stddev(igds),
                mean(spacings), stddev(spacings),
                totalPCont);
        }

        System.out.printf("%-15s | %-18.6f | %-18s | %-18s | %-18s | %-6d%n",
            "Universal", result.universalHV, "-", "-", "-",
            result.universalParetoSet.size());
        System.out.println("-".repeat(105));
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double stddev(double[] values) {
        double m = mean(values);
        double sumSq = 0;
        for (double v : values) sumSq += (v - m) * (v - m);
        return Math.sqrt(sumSq / values.length);
    }

    // =========================================================================
    // CSV OUTPUT — PER SCENARIO
    // =========================================================================

    private static void generateScenarioCSVs(ScenarioResult result) {
        Path outputDir = Paths.get(REPORTS_DIR);

        // 1. Pareto graph data CSV (with seed column)
        String graphFile = "scenario_" + result.scenarioNumber + "_pareto_graph_data.csv";
        try (PrintWriter w = new PrintWriter(new FileWriter(outputDir.resolve(graphFile).toFile()))) {
            w.println("Algorithm,Seed,WaitingTime,Energy,IsUniversalPareto");

            for (Map.Entry<String, List<AlgorithmResult>> entry : result.perSeedResults.entrySet()) {
                for (AlgorithmResult ar : entry.getValue()) {
                    for (double[] sol : ar.solutions) {
                        boolean isUniv = isInUniversalPareto(sol, result.universalParetoSet);
                        w.printf("%s,%d,%.6f,%.9f,%b%n", ar.label, ar.seed, sol[0], sol[1], isUniv);
                    }
                }
            }

            for (double[] sol : result.universalParetoSet) {
                w.printf("Universal_Pareto,0,%.6f,%.9f,true%n", sol[0], sol[1]);
            }

            System.out.println("  Wrote: " + graphFile);
        } catch (IOException e) {
            System.err.println("  ERROR writing " + graphFile + ": " + e.getMessage());
        }

        // 2. Performance metrics CSV — per-seed rows + MEAN/STDDEV summary rows
        String metricsFile = "scenario_" + result.scenarioNumber + "_performance_metrics.csv";
        try (PrintWriter w = new PrintWriter(new FileWriter(outputDir.resolve(metricsFile).toFile()))) {
            w.println("Algorithm,Seed,HV,GD,IGD,Spacing,NonDomSolutions,TotalSolutions,ParetoContribution,TimeMs");

            for (Map.Entry<String, List<AlgorithmResult>> entry : result.perSeedResults.entrySet()) {
                String label = entry.getKey();
                List<AlgorithmResult> seeds = entry.getValue();

                // Per-seed rows
                for (AlgorithmResult ar : seeds) {
                    int ndCount = ar.nonDominatedSolutions != null
                        ? ar.nonDominatedSolutions.size() : ar.solutions.size();
                    w.printf("%s,%d,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d%n",
                        ar.label, ar.seed, ar.hv, ar.gd, ar.igd, ar.spacing,
                        ndCount, ar.solutions.size(), ar.paretoContribution, ar.executionTimeMs);
                }

                // MEAN row
                double[] hvs = seeds.stream().mapToDouble(a -> a.hv).toArray();
                double[] gds = seeds.stream().mapToDouble(a -> a.gd).toArray();
                double[] igds = seeds.stream().mapToDouble(a -> a.igd).toArray();
                double[] spacings = seeds.stream().mapToDouble(a -> a.spacing).toArray();
                double avgND = seeds.stream().mapToInt(a -> a.nonDominatedSolutions != null
                    ? a.nonDominatedSolutions.size() : a.solutions.size()).average().orElse(0);
                double avgTotal = seeds.stream().mapToInt(a -> a.solutions.size()).average().orElse(0);
                int totalPCont = seeds.stream().mapToInt(a -> a.paretoContribution).sum();
                long successfulRuns = seeds.stream().filter(a -> a.executionTimeMs > 0).count();
                long avgTime = successfulRuns > 0
                    ? seeds.stream().filter(a -> a.executionTimeMs > 0)
                        .mapToLong(a -> a.executionTimeMs).sum() / successfulRuns
                    : 0;

                w.printf("%s,MEAN,%.6f,%.6f,%.6f,%.6f,%.0f,%.0f,%d,%d%n",
                    label, mean(hvs), mean(gds), mean(igds), mean(spacings),
                    avgND, avgTotal, totalPCont, avgTime);

                // STDDEV row
                w.printf("%s,STDDEV,%.6f,%.6f,%.6f,%.6f,,,,%n",
                    label, stddev(hvs), stddev(gds), stddev(igds), stddev(spacings));
            }

            w.printf("Universal_Pareto,ALL,%.6f,0.000000,0.000000,0.000000,%d,%d,%d,0%n",
                result.universalHV, result.universalParetoSet.size(),
                result.universalParetoSet.size(), result.universalParetoSet.size());

            System.out.println("  Wrote: " + metricsFile);
        } catch (IOException e) {
            System.err.println("  ERROR writing " + metricsFile + ": " + e.getMessage());
        }
    }

    // =========================================================================
    // CSV OUTPUT — EXPERIMENT SUMMARY
    // =========================================================================

    private static void generateExperimentSummary(List<ScenarioResult> allResults) {
        Path filePath = Paths.get(REPORTS_DIR, "experiment_summary.csv");

        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile()))) {
            w.println("Scenario,ScenarioName,Algorithm,Seed,HV,GD,IGD,Spacing,NonDomSolutions,ParetoContribution,TimeMs,WaitingTime_Best,Energy_Best");

            for (ScenarioResult sr : allResults) {
                for (Map.Entry<String, List<AlgorithmResult>> entry : sr.perSeedResults.entrySet()) {
                    String label = entry.getKey();
                    List<AlgorithmResult> seeds = entry.getValue();

                    // Per-seed rows
                    for (AlgorithmResult ar : seeds) {
                        double bestMakespan = ar.solutions.stream()
                            .mapToDouble(s -> s[0]).min().orElse(Double.MAX_VALUE);
                        double bestEnergy = ar.solutions.stream()
                            .mapToDouble(s -> s[1]).min().orElse(Double.MAX_VALUE);
                        int ndCount = ar.nonDominatedSolutions != null
                            ? ar.nonDominatedSolutions.size() : ar.solutions.size();

                        w.printf("%d,%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%.6f,%.9f%n",
                            sr.scenarioNumber, sr.scenarioName, ar.label, ar.seed,
                            ar.hv, ar.gd, ar.igd, ar.spacing,
                            ndCount, ar.paretoContribution, ar.executionTimeMs,
                            bestMakespan, bestEnergy);
                    }

                    // MEAN row
                    double[] hvs = seeds.stream().mapToDouble(a -> a.hv).toArray();
                    double[] gds = seeds.stream().mapToDouble(a -> a.gd).toArray();
                    double[] igds = seeds.stream().mapToDouble(a -> a.igd).toArray();
                    double[] spacings = seeds.stream().mapToDouble(a -> a.spacing).toArray();
                    int totalPCont = seeds.stream().mapToInt(a -> a.paretoContribution).sum();
                    long successfulRuns = seeds.stream().filter(a -> a.executionTimeMs > 0).count();
                long avgTime = successfulRuns > 0
                    ? seeds.stream().filter(a -> a.executionTimeMs > 0)
                        .mapToLong(a -> a.executionTimeMs).sum() / successfulRuns
                    : 0;
                    double bestMakespanAll = seeds.stream()
                        .flatMap(a -> a.solutions.stream())
                        .mapToDouble(s -> s[0]).min().orElse(Double.MAX_VALUE);
                    double bestEnergyAll = seeds.stream()
                        .flatMap(a -> a.solutions.stream())
                        .mapToDouble(s -> s[1]).min().orElse(Double.MAX_VALUE);

                    w.printf("%d,%s,%s,MEAN,%.6f,%.6f,%.6f,%.6f,,%d,%d,%.6f,%.9f%n",
                        sr.scenarioNumber, sr.scenarioName, label,
                        mean(hvs), mean(gds), mean(igds), mean(spacings),
                        totalPCont, avgTime,
                        bestMakespanAll, bestEnergyAll);
                }
            }

            System.out.println("  Wrote: experiment_summary.csv");
        } catch (IOException e) {
            System.err.println("  ERROR writing experiment_summary.csv: " + e.getMessage());
        }
    }

    private static void generatePlotOptionsJSON() {
        Path filePath = Paths.get(REPORTS_DIR, "plot_options.json");

        try (PrintWriter w = new PrintWriter(new FileWriter(filePath.toFile()))) {
            w.println("{");
            w.println("  \"dpi\": 300,");
            w.println("  \"width\": 18,");
            w.println("  \"height\": 7,");
            w.println("  \"marker_size\": 10,");
            w.println("  \"show_legend\": true,");
            w.println("  \"show_labels\": true,");
            w.println("  \"scenarios\": 3");
            w.println("}");
            System.out.println("  Wrote: plot_options.json");
        } catch (IOException e) {
            System.err.println("  ERROR writing plot_options.json: " + e.getMessage());
        }
    }

    // =========================================================================
    // PYTHON PLOTTER INVOCATION
    // =========================================================================

    private static void executePythonPlotter() {
        PythonPostProcessor.run(REPORTS_DIR,
            "scripts/recompute_hv.py",
            "scripts/plot_scenario_pareto.py",
            "scripts/statistical_tests.py"
        );
    }
}
