package com.cloudsimulator.FinalExperiment;

import com.cloudsimulator.config.*;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
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

// Termination & cooling
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.FitnessEvaluationsTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.GeometricCoolingSchedule;

// Performance metrics
import com.cloudsimulator.multiobjectivePerformance.PerfMet.PerformanceMetrics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ScenarioComparisonExperimentRunner {

    // =========================================================================
    // ALGORITHM PARAMETERS
    // =========================================================================
    private static final int POPULATION_SIZE = 200;
    private static final int GENERATIONS = 200;
    private static final double CROSSOVER_RATE = 0.95;
    private static final double MUTATION_RATE = 0.05;
    private static final double GA_ELITE_PERCENTAGE = 0.20;  // 20% of population
    private static final int GA_TOURNAMENT_SIZE = 5;
    private static final double SA_INITIAL_TEMPERATURE = 1000.0;
    private static final double SA_COOLING_RATE = 0.90;
    private static final int SA_TOTAL_EVALUATIONS = 40000;
    private static final long RANDOM_SEED = 42L;
    private static final boolean VERBOSE_LOGGING = true;
    private static final double TIEBREAKER_WEIGHT = 0.001;

    // AMOSA-specific parameters (AMOSA formula is inverted vs standard SA: prob=1/(1+exp(ΔDom*T)),
    // so high T means LESS acceptance — T=5 gives ~38% acceptance for ΔDom=0.1)
    private static final double AMOSA_INITIAL_TEMPERATURE = 5.0;
    private static final double AMOSA_ALPHA = 0.98;  // Slower cooling — more exploitation per temperature
    private static final int AMOSA_HARD_LIMIT = 100;
    private static final int AMOSA_SOFT_LIMIT = 100;
    private static final int AMOSA_ITERATIONS_PER_TEMP = 500;
    private static final int AMOSA_HILL_CLIMBING_ITERS = 50;  // Better refined initial solutions (was 20)
    private static final double AMOSA_GAMMA = 3.0;  // More diverse initialization (was 2.0)
    private static final double AMOSA_MUTATION_RATE = 0.10; // Moderate mutation — less destructive for SA refinement

    private static final String REPORTS_DIR = "reports/scenario_comparison";

    private static final String[] ALGORITHM_LABELS = {
        "GA_Makespan", "GA_Energy", "SA_Makespan", "SA_Energy",
        "NSGA-II", "SPEA-II", "AMOSA"
    };

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
        {50, 50},   // Scenario 1: Balanced
        {20, 80},   // Scenario 2: GPU Stress
        {80, 20}    // Scenario 3: CPU Stress
    };

    private static final String[] SCENARIO_NAMES = {
        "Balanced", "GPU_Stress", "CPU_Stress"
    };

    // =========================================================================
    // DATA CLASSES
    // =========================================================================

    static class AlgorithmResult {
        final String label;
        final List<double[]> solutions; // each: [makespan, energy]
        final long executionTimeMs;
        List<double[]> nonDominatedSolutions;
        double hv, gd, igd, spacing;
        int paretoContribution;

        AlgorithmResult(String label, List<double[]> solutions, long executionTimeMs) {
            this.label = label;
            this.solutions = solutions;
            this.executionTimeMs = executionTimeMs;
        }
    }

    static class ScenarioResult {
        final int scenarioNumber;
        final String scenarioName;
        final Map<String, AlgorithmResult> algorithmResults = new LinkedHashMap<>();
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
        System.out.println("============================================================");
        System.out.println("  SCENARIO COMPARISON EXPERIMENT RUNNER");
        System.out.println("  Algorithms: GA(M), GA(E), SA(M), SA(E), NSGA-II, SPEA-II, AMOSA");
        System.out.println("  Scenarios: Balanced, GPU Stress, CPU Stress");
        System.out.println("  Seed: " + RANDOM_SEED);
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

            // Build configuration for this scenario
            ExperimentConfiguration baseConfig = buildScenarioConfig(scenarioNum);

            // Run each algorithm
            for (String label : ALGORITHM_LABELS) {
                System.out.println();
                System.out.println("------------------------------------------------------------");
                System.out.println("  Running: " + label + " (Scenario " + scenarioNum + ")");
                System.out.println("------------------------------------------------------------");

                try {
                    AlgorithmResult result = runAlgorithm(label, baseConfig);
                    scenarioResult.algorithmResults.put(label, result);
                    System.out.println("  Completed in " + result.executionTimeMs + " ms, " +
                        result.solutions.size() + " solution(s)");
                } catch (Throwable e) {
                    System.err.println("  ERROR running " + label + ": " + e.getMessage());
                    e.printStackTrace();
                    // Add empty result so we can continue
                    List<double[]> empty = new ArrayList<>();
                    empty.add(new double[]{Double.MAX_VALUE, Double.MAX_VALUE});
                    scenarioResult.algorithmResults.put(label,
                        new AlgorithmResult(label, empty, 0));
                }
            }

            // Compute Universal Pareto front
            System.out.println();
            System.out.println("  Computing Universal Pareto front...");
            scenarioResult.universalParetoSet = computeUniversalPareto(scenarioResult.algorithmResults);
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

    private static ExperimentConfiguration buildScenarioConfig(int scenario) {
        ExperimentConfiguration config = new ExperimentConfiguration();
        config.setRandomSeed(RANDOM_SEED);

        // 1 Datacenter
        config.addDatacenterConfig(new DatacenterConfig("DC-Experiment", 20, 100000.0));

        // 10 Hosts (4 CPU, 3 GPU, 3 MIXED)
        for (int i = 0; i < 4; i++) {
            config.addHostConfig(new HostConfig(
                2_500_000_000L, 16, ComputeType.CPU_ONLY, 0,
                32768, 10000, 1048576, "StandardPowerModel"));
        }
        for (int i = 0; i < 3; i++) {
            config.addHostConfig(new HostConfig(
                2_800_000_000L, 8, ComputeType.GPU_ONLY, 4,
                32768, 10000, 1048576, "HighPerformancePowerModel"));
        }
        for (int i = 0; i < 3; i++) {
            config.addHostConfig(new HostConfig(
                3_000_000_000L, 32, ComputeType.CPU_GPU_MIXED, 4,
                65536, 10000, 1048576, "HighPerformancePowerModel"));
        }

        // 15 VMs with DIVERSE speeds to create makespan-energy trade-off
        // Fast VMs: finish tasks quickly but consume more power (speed scaling ^1.5)
        // Slow VMs: take longer but are energy-efficient
        // CPU VMs: 2 fast + 2 medium + 1 slow = 5
        config.addVMConfig(new VMConfig("ExperimentUser",
            4_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));  // 16B IPS (fast)
        config.addVMConfig(new VMConfig("ExperimentUser",
            4_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));  // 16B IPS (fast)
        config.addVMConfig(new VMConfig("ExperimentUser",
            2_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));  // 8B IPS (medium)
        config.addVMConfig(new VMConfig("ExperimentUser",
            2_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));  // 8B IPS (medium)
        config.addVMConfig(new VMConfig("ExperimentUser",
            1_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));  // 4B IPS (slow)

        // GPU VMs: 2 fast + 2 medium + 1 slow = 5
        config.addVMConfig(new VMConfig("ExperimentUser",
            4_000_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.GPU_ONLY));  // 16B IPS (fast)
        config.addVMConfig(new VMConfig("ExperimentUser",
            4_000_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.GPU_ONLY));  // 16B IPS (fast)
        config.addVMConfig(new VMConfig("ExperimentUser",
            2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));  // 8B IPS (medium)
        config.addVMConfig(new VMConfig("ExperimentUser",
            2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));  // 8B IPS (medium)
        config.addVMConfig(new VMConfig("ExperimentUser",
            1_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));  // 4B IPS (slow)

        // Mixed VMs: 2 fast + 2 medium + 1 slow = 5
        config.addVMConfig(new VMConfig("ExperimentUser",
            3_500_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));  // 14B IPS (fast)
        config.addVMConfig(new VMConfig("ExperimentUser",
            3_500_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));  // 14B IPS (fast)
        config.addVMConfig(new VMConfig("ExperimentUser",
            2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));  // 8B IPS (medium)
        config.addVMConfig(new VMConfig("ExperimentUser",
            2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));  // 8B IPS (medium)
        config.addVMConfig(new VMConfig("ExperimentUser",
            1_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));  // 4B IPS (slow)

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

    private static TaskAssignmentStrategy createStrategy(String label, List<Host> hosts) {
        switch (label) {
            case "GA_Makespan":
                return createGAStrategy(new MakespanObjective(), createEnergyObjective(hosts));
            case "GA_Energy":
                return createGAStrategy(createEnergyObjective(hosts), new MakespanObjective());
            case "SA_Makespan":
                return createSAStrategy(new MakespanObjective(), createEnergyObjective(hosts));
            case "SA_Energy":
                return createSAStrategy(createEnergyObjective(hosts), new MakespanObjective());
            case "NSGA-II":
                return createNSGA2Strategy(hosts);
            case "SPEA-II":
                return createSPEA2Strategy(hosts);
            case "AMOSA":
                return createAMOSAStrategy(hosts);
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
            .terminationCondition(new GenerationCountTermination(GENERATIONS))
            .verboseLogging(VERBOSE_LOGGING)
            .build();
        return new GenerationalGATaskSchedulingStrategy(config);
    }

    private static TaskAssignmentStrategy createSAStrategy(
            com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective primaryObjective,
            com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective tiebreakerObjective) {
        SAConfiguration config = SAConfiguration.builder()
            .initialTemperature(SA_INITIAL_TEMPERATURE)
            .coolingSchedule(new GeometricCoolingSchedule(SA_COOLING_RATE))
            .terminationCondition(new FitnessEvaluationsTermination(SA_TOTAL_EVALUATIONS))
            .addWeightedObjective(primaryObjective, 1.0)
            .addWeightedObjective(tiebreakerObjective, TIEBREAKER_WEIGHT)
            .verboseLogging(VERBOSE_LOGGING)
            .build();
        return new SimulatedAnnealingTaskSchedulingStrategy(config);
    }

    private static TaskAssignmentStrategy createNSGA2Strategy(List<Host> hosts) {
        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(POPULATION_SIZE)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(MUTATION_RATE)
            .addObjective(makespan)
            .addObjective(energy)
            .terminationCondition(new GenerationCountTermination(GENERATIONS))
            .randomSeed(RANDOM_SEED)
            .verboseLogging(VERBOSE_LOGGING)
            .build();
        MOEA_NSGA2TaskSchedulingStrategy strategy = new MOEA_NSGA2TaskSchedulingStrategy(config);
        strategy.setSelectionMethod(MOEA_NSGA2TaskSchedulingStrategy.SolutionSelectionMethod.KNEE_POINT);
        return strategy;
    }

    private static TaskAssignmentStrategy createSPEA2Strategy(List<Host> hosts) {
        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(POPULATION_SIZE)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(MUTATION_RATE)
            .addObjective(makespan)
            .addObjective(energy)
            .terminationCondition(new GenerationCountTermination(GENERATIONS))
            .randomSeed(RANDOM_SEED)
            .verboseLogging(VERBOSE_LOGGING)
            .build();
        MOEA_SPEA2TaskSchedulingStrategy strategy = new MOEA_SPEA2TaskSchedulingStrategy(config);
        strategy.setSelectionMethod(MOEA_SPEA2TaskSchedulingStrategy.SolutionSelectionMethod.KNEE_POINT);
        return strategy;
    }

    private static TaskAssignmentStrategy createAMOSAStrategy(List<Host> hosts) {
        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(hosts);
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(AMOSA_SOFT_LIMIT)
            .crossoverRate(CROSSOVER_RATE)
            .mutationRate(AMOSA_MUTATION_RATE)
            .addObjective(makespan)
            .addObjective(energy)
            .terminationCondition(new GenerationCountTermination(GENERATIONS))
            .randomSeed(RANDOM_SEED)
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
            ExperimentConfiguration baseConfig) {

        long startTime = System.currentTimeMillis();

        // Clone config and reinitialize random generator
        ExperimentConfiguration config = baseConfig.clone();
        RandomGenerator.initialize(RANDOM_SEED);

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
        TaskAssignmentStrategy strategy = createStrategy(label, context.getHosts());

        // Step 5: Task Assignment (TIMED)
        long assignStart = System.currentTimeMillis();
        TaskAssignmentStep taskAssignStep = new TaskAssignmentStep(strategy);
        taskAssignStep.execute(context);
        long assignTime = System.currentTimeMillis() - assignStart;
        System.out.println("  Task assignment took: " + assignTime + " ms");

        // Step 6: VM Execution
        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        // Step 7: Task Execution Analysis
        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        // Step 8: Energy Calculation
        EnergyCalculationStep energyCalcStep = new EnergyCalculationStep();
        energyCalcStep.execute(context);

        long endTime = System.currentTimeMillis();

        // Debug: compare objective estimate vs actual simulation
        double actualMakespan = taskExecStep.getMakespan();
        double actualEnergyKWh = energyCalcStep.getTotalITEnergyKWh();
        System.out.printf("  [DEBUG] Actual simulation: makespan=%d s, energy=%.6f kWh%n",
            (long) actualMakespan, actualEnergyKWh);

        // Extract solutions
        List<double[]> solutions = extractSolutions(
            strategy, taskExecStep, energyCalcStep);

        return new AlgorithmResult(label, solutions, endTime - startTime);
    }

    // =========================================================================
    // SOLUTION EXTRACTION
    // =========================================================================

    private static List<double[]> extractSolutions(
            TaskAssignmentStrategy strategy,
            TaskExecutionStep taskStep,
            EnergyCalculationStep energyStep) {

        List<double[]> solutions = new ArrayList<>();

        // MOEA strategies: get full Pareto front from optimizer
        if (strategy instanceof MOEA_NSGA2TaskSchedulingStrategy) {
            ParetoFront front = ((MOEA_NSGA2TaskSchedulingStrategy) strategy).getLastParetoFront();
            addParetoSolutions(front, solutions);
        } else if (strategy instanceof MOEA_SPEA2TaskSchedulingStrategy) {
            ParetoFront front = ((MOEA_SPEA2TaskSchedulingStrategy) strategy).getLastParetoFront();
            addParetoSolutions(front, solutions);
        } else if (strategy instanceof MOEA_AMOSATaskSchedulingStrategy) {
            ParetoFront front = ((MOEA_AMOSATaskSchedulingStrategy) strategy).getLastParetoFront();
            addParetoSolutions(front, solutions);
        }

        // Always add the actual simulation result for the selected/executed solution.
        // This ensures consistent measurement across all algorithms (GA, SA, MOEA).
        double actualMakespan = taskStep.getMakespan();
        double actualEnergy = energyStep.getTotalITEnergyKWh();
        solutions.add(new double[]{actualMakespan, actualEnergy});

        return solutions;
    }

    private static void addParetoSolutions(ParetoFront front, List<double[]> solutions) {
        if (front != null && !front.isEmpty()) {
            int skipped = 0;
            for (SchedulingSolution sol : front.getSolutions()) {
                double[] objs = sol.getObjectiveValues();
                if (objs != null && objs.length >= 2) {
                    solutions.add(new double[]{objs[0], objs[1]});
                } else {
                    skipped++;
                }
            }
            if (skipped > 0) {
                System.out.println("  [DEBUG] Skipped " + skipped + " solutions with null/short objectives");
            }
        }
    }

    // =========================================================================
    // UNIVERSAL PARETO COMPUTATION
    // =========================================================================

    private static List<double[]> computeUniversalPareto(
            Map<String, AlgorithmResult> algorithmResults) {

        List<double[]> allSolutions = new ArrayList<>();
        for (AlgorithmResult result : algorithmResults.values()) {
            allSolutions.addAll(result.solutions);
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
        for (AlgorithmResult algoResult : scenarioResult.algorithmResults.values()) {
            int contribution = 0;
            for (double[] sol : algoResult.solutions) {
                if (isInUniversalPareto(sol, scenarioResult.universalParetoSet)) {
                    contribution++;
                }
            }
            algoResult.paretoContribution = contribution;
            System.out.println("    " + algoResult.label + ": " + contribution +
                " / " + scenarioResult.universalParetoSet.size());
        }
    }

    private static void calculatePerformanceMetrics(ScenarioResult scenarioResult) {
        ArrayList<ArrayList<ArrayList<Double>>> allParetos = new ArrayList<>();

        List<String> algoOrder = new ArrayList<>(scenarioResult.algorithmResults.keySet());

        for (String label : algoOrder) {
            AlgorithmResult algoResult = scenarioResult.algorithmResults.get(label);
            algoResult.nonDominatedSolutions = filterToNonDominated(algoResult.solutions);

            ArrayList<ArrayList<Double>> paretoList = new ArrayList<>();
            for (double[] sol : algoResult.nonDominatedSolutions) {
                ArrayList<Double> point = new ArrayList<>();
                point.add(sol[0]);
                point.add(sol[1]);
                paretoList.add(point);
            }
            if (paretoList.isEmpty()) {
                ArrayList<Double> dummy = new ArrayList<>();
                dummy.add(Double.MAX_VALUE);
                dummy.add(Double.MAX_VALUE);
                paretoList.add(dummy);
            }
            allParetos.add(paretoList);
        }

        // Universal Pareto as reference (last index)
        ArrayList<ArrayList<Double>> universalList = new ArrayList<>();
        for (double[] sol : scenarioResult.universalParetoSet) {
            ArrayList<Double> point = new ArrayList<>();
            point.add(sol[0]);
            point.add(sol[1]);
            universalList.add(point);
        }
        allParetos.add(universalList);

        int referenceIndex = allParetos.size() - 1;
        PerformanceMetrics pm = new PerformanceMetrics(allParetos, referenceIndex);

        int index = 0;
        for (String label : algoOrder) {
            AlgorithmResult algoResult = scenarioResult.algorithmResults.get(label);
            try {
                algoResult.hv = pm.HV(index);
                algoResult.gd = pm.GD(index);
                algoResult.igd = pm.IGD(index);
                algoResult.spacing = algoResult.nonDominatedSolutions.size() > 1
                    ? pm.Spacing(index) : 0.0;
            } catch (Exception e) {
                System.err.println("  WARNING: Metrics failed for " + label + ": " + e.getMessage());
                algoResult.hv = 0;
                algoResult.gd = Double.MAX_VALUE;
                algoResult.igd = Double.MAX_VALUE;
                algoResult.spacing = 0;
            }
            index++;
        }

        scenarioResult.universalHV = pm.HV(referenceIndex);
    }

    // =========================================================================
    // CONSOLE SUMMARY
    // =========================================================================

    private static void printScenarioSummary(ScenarioResult result) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("  SCENARIO " + result.scenarioNumber + " (" + result.scenarioName + ") RESULTS");
        System.out.println("============================================================");
        System.out.printf("%-15s | %-10s | %-10s | %-10s | %-10s | %-6s | %-5s%n",
            "Algorithm", "HV", "GD", "IGD", "Spacing", "NDSol", "PCont");
        System.out.println("-".repeat(80));

        for (AlgorithmResult ar : result.algorithmResults.values()) {
            int ndCount = ar.nonDominatedSolutions != null
                ? ar.nonDominatedSolutions.size() : ar.solutions.size();
            System.out.printf("%-15s | %-10.6f | %-10.6f | %-10.6f | %-10.6f | %-6d | %-5d%n",
                ar.label, ar.hv, ar.gd, ar.igd, ar.spacing, ndCount, ar.paretoContribution);
        }

        System.out.printf("%-15s | %-10.6f | %-10s | %-10s | %-10s | %-6d | %-5d%n",
            "Universal", result.universalHV, "-", "-", "-",
            result.universalParetoSet.size(), result.universalParetoSet.size());
        System.out.println("-".repeat(80));
    }

    // =========================================================================
    // CSV OUTPUT — PER SCENARIO
    // =========================================================================

    private static void generateScenarioCSVs(ScenarioResult result) {
        Path outputDir = Paths.get(REPORTS_DIR);

        // 1. Pareto graph data CSV
        String graphFile = "scenario_" + result.scenarioNumber + "_pareto_graph_data.csv";
        try (PrintWriter w = new PrintWriter(new FileWriter(outputDir.resolve(graphFile).toFile()))) {
            w.println("Algorithm,Makespan,Energy,IsUniversalPareto");

            for (AlgorithmResult ar : result.algorithmResults.values()) {
                for (double[] sol : ar.solutions) {
                    boolean isUniv = isInUniversalPareto(sol, result.universalParetoSet);
                    w.printf("%s,%.6f,%.9f,%b%n", ar.label, sol[0], sol[1], isUniv);
                }
            }

            for (double[] sol : result.universalParetoSet) {
                w.printf("Universal_Pareto,%.6f,%.9f,true%n", sol[0], sol[1]);
            }

            System.out.println("  Wrote: " + graphFile);
        } catch (IOException e) {
            System.err.println("  ERROR writing " + graphFile + ": " + e.getMessage());
        }

        // 2. Performance metrics CSV
        String metricsFile = "scenario_" + result.scenarioNumber + "_performance_metrics.csv";
        try (PrintWriter w = new PrintWriter(new FileWriter(outputDir.resolve(metricsFile).toFile()))) {
            w.println("Algorithm,HV,GD,IGD,Spacing,NonDomSolutions,TotalSolutions,ParetoContribution,TimeMs");

            for (AlgorithmResult ar : result.algorithmResults.values()) {
                int ndCount = ar.nonDominatedSolutions != null
                    ? ar.nonDominatedSolutions.size() : ar.solutions.size();
                w.printf("%s,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d%n",
                    ar.label, ar.hv, ar.gd, ar.igd, ar.spacing,
                    ndCount, ar.solutions.size(), ar.paretoContribution, ar.executionTimeMs);
            }

            w.printf("Universal_Pareto,%.6f,0.000000,0.000000,0.000000,%d,%d,%d,0%n",
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
            w.println("Scenario,ScenarioName,Algorithm,HV,GD,IGD,Spacing,NonDomSolutions,ParetoContribution,TimeMs,Makespan_Best,Energy_Best");

            for (ScenarioResult sr : allResults) {
                for (AlgorithmResult ar : sr.algorithmResults.values()) {
                    double bestMakespan = ar.solutions.stream()
                        .mapToDouble(s -> s[0]).min().orElse(Double.MAX_VALUE);
                    double bestEnergy = ar.solutions.stream()
                        .mapToDouble(s -> s[1]).min().orElse(Double.MAX_VALUE);
                    int ndCount = ar.nonDominatedSolutions != null
                        ? ar.nonDominatedSolutions.size() : ar.solutions.size();

                    w.printf("%d,%s,%s,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%.6f,%.9f%n",
                        sr.scenarioNumber, sr.scenarioName, ar.label,
                        ar.hv, ar.gd, ar.igd, ar.spacing,
                        ndCount, ar.paretoContribution, ar.executionTimeMs,
                        bestMakespan, bestEnergy);
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
        Path scriptPath = Paths.get("scripts", "plot_scenario_pareto.py");

        if (!Files.exists(scriptPath)) {
            System.out.println();
            System.out.println("Python plotter not found at " + scriptPath);
            System.out.println("To generate plots manually, run:");
            System.out.println("  python3 scripts/plot_scenario_pareto.py " + REPORTS_DIR);
            return;
        }

        System.out.println();
        System.out.println("Executing Python plotter...");

        String[] pythonCommands;
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            pythonCommands = new String[]{"python"};
        } else {
            pythonCommands = new String[]{"python3", "python"};
        }

        for (String pythonCmd : pythonCommands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(pythonCmd, scriptPath.toString(), REPORTS_DIR);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("  [Python] " + line);
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    System.out.println("  Pareto plots generated successfully.");
                    return;
                }
            } catch (IOException e) {
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("  Could not run Python plotter automatically.");
        System.out.println("  Run manually: python3 scripts/plot_scenario_pareto.py " + REPORTS_DIR);
    }
}
