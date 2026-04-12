package com.cloudsimulator.FinalExperiment;

import com.cloudsimulator.config.*;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

import com.cloudsimulator.steps.*;
import com.cloudsimulator.PlacementStrategy.hostPlacement.PowerAwareLoadBalancingHostPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.VMPlacement.BestFitVMPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ParameterTuningRunner {

    private static final double TIEBREAKER_WEIGHT = 0.001;
    private static final int NUM_RUNS = 10;
    private static final int TOTAL_EVALS = 40_000;  // Fixed budget for GA and SA
    private static final long BASE_SEED = 1200L;
    private static final String REPORTS_DIR = "reports/parameter_tuning";

    // Scenario config (reused from main runner)
    private static final WorkloadType[] CPU_WORKLOADS = {
        WorkloadType.SEVEN_ZIP, WorkloadType.DATABASE, WorkloadType.LLM_CPU,
        WorkloadType.IMAGE_GEN_CPU, WorkloadType.CINEBENCH, WorkloadType.VERACRYPT
    };
    private static final WorkloadType[] GPU_WORKLOADS = {
        WorkloadType.FURMARK, WorkloadType.IMAGE_GEN_GPU, WorkloadType.LLM_GPU
    };
    private static final long[] INSTRUCTION_LENGTHS = {
        1_000_000_000L, 2_000_000_000L, 3_000_000_000L, 4_000_000_000L,
        5_000_000_000L, 7_000_000_000L, 8_000_000_000L, 10_000_000_000L,
        12_000_000_000L, 15_000_000_000L
    };
    private static final int[][] SCENARIO_TASK_COUNTS = {{50,50},{20,80},{80,20}};
    private static final String[] SCENARIO_NAMES = {"Balanced","GPU_Stress","CPU_Stress"};

    // =========================================================================
    // GA PARAMETER CONFIGS
    // =========================================================================
    static class GAParams {
        final String name;
        final int popSize, generations, eliteCount, tournamentSize;
        final double crossoverRate, mutationRate;
        GAParams(String name, int pop, int gen, double cx, double mut, int elite, int tourn) {
            this.name=name; this.popSize=pop; this.generations=gen;
            this.crossoverRate=cx; this.mutationRate=mut;
            this.eliteCount=elite; this.tournamentSize=tourn;
        }
    }

    static class SAParams {
        final String name;
        final double initialTemp, coolingRate;
        final int totalEvals, itersPerTemp;
        SAParams(String name, double temp, double cool, int evals, int itersPerT) {
            this.name=name; this.initialTemp=temp; this.coolingRate=cool;
            this.totalEvals=evals; this.itersPerTemp=itersPerT;
        }
    }

    static class RunResult {
        double makespan, energy;
        long timeMs;
    }

    private static List<GAParams> buildGAGrid() {
        // All configs: pop × gen = 40,000 evaluations
        List<GAParams> g = new ArrayList<>();
        // Baseline: 200×200 = 40k
        g.add(new GAParams("GA_base",           200, 200, 0.9,  0.1,  20, 3));
        // Population size (adjust gen to keep pop×gen=40k)
        g.add(new GAParams("GA_pop100",         100, 400, 0.9,  0.1,  10, 3));
        g.add(new GAParams("GA_pop300",         300, 133, 0.9,  0.1,  30, 3));
        g.add(new GAParams("GA_pop500",         500, 80,  0.9,  0.1,  50, 3));
        // Crossover rate
        g.add(new GAParams("GA_cx70",           200, 200, 0.7,  0.1,  20, 3));
        g.add(new GAParams("GA_cx80",           200, 200, 0.8,  0.1,  20, 3));
        g.add(new GAParams("GA_cx95",           200, 200, 0.95, 0.1,  20, 3));
        // Mutation rate
        g.add(new GAParams("GA_mut01",          200, 200, 0.9,  0.01, 20, 3));
        g.add(new GAParams("GA_mut05",          200, 200, 0.9,  0.05, 20, 3));
        g.add(new GAParams("GA_mut20",          200, 200, 0.9,  0.2,  20, 3));
        g.add(new GAParams("GA_mut30",          200, 200, 0.9,  0.3,  20, 3));
        // Elite count
        g.add(new GAParams("GA_elite5",         200, 200, 0.9,  0.1,  5,  3));
        g.add(new GAParams("GA_elite10",        200, 200, 0.9,  0.1,  10, 3));
        g.add(new GAParams("GA_elite40",        200, 200, 0.9,  0.1,  40, 3));
        // Tournament size
        g.add(new GAParams("GA_tourn2",         200, 200, 0.9,  0.1,  20, 2));
        g.add(new GAParams("GA_tourn5",         200, 200, 0.9,  0.1,  20, 5));
        g.add(new GAParams("GA_tourn7",         200, 200, 0.9,  0.1,  20, 7));
        // Combined promising configs (all 40k budget)
        g.add(new GAParams("GA_explore",        200, 200, 0.85, 0.2,  10, 2));
        g.add(new GAParams("GA_exploit",        200, 200, 0.95, 0.05, 40, 5));
        return g;
    }

    private static List<SAParams> buildSAGrid() {
        // All configs: 40,000 total evaluations
        List<SAParams> s = new ArrayList<>();
        // Baseline
        s.add(new SAParams("SA_base",           1000,  0.95, TOTAL_EVALS, 100));
        // Temperature
        s.add(new SAParams("SA_temp100",        100,   0.95, TOTAL_EVALS, 100));
        s.add(new SAParams("SA_temp500",        500,   0.95, TOTAL_EVALS, 100));
        s.add(new SAParams("SA_temp5000",       5000,  0.95, TOTAL_EVALS, 100));
        s.add(new SAParams("SA_temp10000",      10000, 0.95, TOTAL_EVALS, 100));
        // Cooling rate
        s.add(new SAParams("SA_cool85",         1000,  0.85, TOTAL_EVALS, 100));
        s.add(new SAParams("SA_cool90",         1000,  0.90, TOTAL_EVALS, 100));
        s.add(new SAParams("SA_cool97",         1000,  0.97, TOTAL_EVALS, 100));
        s.add(new SAParams("SA_cool99",         1000,  0.99, TOTAL_EVALS, 100));
        // Iterations per temperature
        s.add(new SAParams("SA_ipt50",          1000,  0.95, TOTAL_EVALS, 50));
        s.add(new SAParams("SA_ipt200",         1000,  0.95, TOTAL_EVALS, 200));
        s.add(new SAParams("SA_ipt500",         1000,  0.95, TOTAL_EVALS, 500));
        // Combined promising configs (all 40k budget)
        s.add(new SAParams("SA_hot_slow",       5000,  0.99, TOTAL_EVALS, 200));
        s.add(new SAParams("SA_moderate",       2000,  0.97, TOTAL_EVALS, 150));
        s.add(new SAParams("SA_hot_fast_ipt",   5000,  0.90, TOTAL_EVALS, 300));
        return s;
    }

    // =========================================================================
    // MAIN
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("  PARAMETER TUNING RUNNER (GA & SA only)");
        System.out.println("  Runs per config: " + NUM_RUNS + " seeds x 3 scenarios");
        System.out.println("============================================================");

        try { Files.createDirectories(Paths.get(REPORTS_DIR)); }
        catch (IOException e) { System.err.println("Cannot create dir: " + e.getMessage()); return; }

        List<GAParams> gaGrid = buildGAGrid();
        List<SAParams> saGrid = buildSAGrid();

        // Results: configName -> scenario -> list of [makespan, energy] per run
        Map<String, double[]> gaMakespanResults = new LinkedHashMap<>();
        Map<String, double[]> gaEnergyResults = new LinkedHashMap<>();
        Map<String, double[]> saMakespanResults = new LinkedHashMap<>();
        Map<String, double[]> saEnergyResults = new LinkedHashMap<>();

        // ---- GA TUNING ----
        System.out.println("\n===== GA PARAMETER TUNING (" + gaGrid.size() + " configs) =====");
        for (int gi = 0; gi < gaGrid.size(); gi++) {
            GAParams gp = gaGrid.get(gi);
            System.out.printf("\n[%d/%d] %s (pop=%d gen=%d cx=%.2f mut=%.2f elite=%d tourn=%d)%n",
                gi+1, gaGrid.size(), gp.name, gp.popSize, gp.generations,
                gp.crossoverRate, gp.mutationRate, gp.eliteCount, gp.tournamentSize);

            double sumMakespan = 0, sumEnergy = 0;
            double sumMakespanE = 0, sumEnergyE = 0;
            int totalRuns = 0;

            for (int s = 0; s < 3; s++) {
                for (int run = 0; run < NUM_RUNS; run++) {
                    long seed = BASE_SEED + run;
                    ExperimentConfiguration config = buildScenarioConfig(s + 1, seed);
                    RandomGenerator.initialize(seed);

                    // GA_Makespan
                    RunResult rM = runGA(gp, config, true, seed);
                    sumMakespan += rM.makespan;
                    sumEnergy += rM.energy;

                    // GA_Energy
                    RandomGenerator.initialize(seed);
                    config = buildScenarioConfig(s + 1, seed);
                    RunResult rE = runGA(gp, config, false, seed);
                    sumMakespanE += rE.makespan;
                    sumEnergyE += rE.energy;

                    totalRuns++;
                }
            }
            gaMakespanResults.put(gp.name, new double[]{sumMakespan/totalRuns, sumEnergy/totalRuns});
            gaEnergyResults.put(gp.name, new double[]{sumMakespanE/totalRuns, sumEnergyE/totalRuns});
            System.out.printf("  GA_Makespan avg: makespan=%.2f energy=%.6f | GA_Energy avg: makespan=%.2f energy=%.6f%n",
                sumMakespan/totalRuns, sumEnergy/totalRuns, sumMakespanE/totalRuns, sumEnergyE/totalRuns);
        }

        // ---- SA TUNING ----
        System.out.println("\n===== SA PARAMETER TUNING (" + saGrid.size() + " configs) =====");
        for (int si = 0; si < saGrid.size(); si++) {
            SAParams sp = saGrid.get(si);
            System.out.printf("\n[%d/%d] %s (temp=%.0f cool=%.2f evals=%d ipt=%d)%n",
                si+1, saGrid.size(), sp.name, sp.initialTemp, sp.coolingRate,
                sp.totalEvals, sp.itersPerTemp);

            double sumMakespan = 0, sumEnergy = 0;
            double sumMakespanE = 0, sumEnergyE = 0;
            int totalRuns = 0;

            for (int s = 0; s < 3; s++) {
                for (int run = 0; run < NUM_RUNS; run++) {
                    long seed = BASE_SEED + run;
                    ExperimentConfiguration config = buildScenarioConfig(s + 1, seed);
                    RandomGenerator.initialize(seed);

                    // SA_Makespan
                    RunResult rM = runSA(sp, config, true, seed);
                    sumMakespan += rM.makespan;
                    sumEnergy += rM.energy;

                    // SA_Energy
                    RandomGenerator.initialize(seed);
                    config = buildScenarioConfig(s + 1, seed);
                    RunResult rE = runSA(sp, config, false, seed);
                    sumMakespanE += rE.makespan;
                    sumEnergyE += rE.energy;

                    totalRuns++;
                }
            }
            saMakespanResults.put(sp.name, new double[]{sumMakespan/totalRuns, sumEnergy/totalRuns});
            saEnergyResults.put(sp.name, new double[]{sumMakespanE/totalRuns, sumEnergyE/totalRuns});
            System.out.printf("  SA_Makespan avg: makespan=%.2f energy=%.6f | SA_Energy avg: makespan=%.2f energy=%.6f%n",
                sumMakespan/totalRuns, sumEnergy/totalRuns, sumMakespanE/totalRuns, sumEnergyE/totalRuns);
        }

        // ---- PRINT SUMMARY ----
        printSummary("GA_Makespan", gaMakespanResults, true);
        printSummary("GA_Energy", gaEnergyResults, false);
        printSummary("SA_Makespan", saMakespanResults, true);
        printSummary("SA_Energy", saEnergyResults, false);

        // ---- WRITE CSV ----
        writeCSV(gaMakespanResults, gaEnergyResults, saMakespanResults, saEnergyResults);

        System.out.println("\n============================================================");
        System.out.println("  TUNING COMPLETE - results in " + REPORTS_DIR);
        System.out.println("============================================================");
    }

    // =========================================================================
    // RUN GA / SA
    // =========================================================================
    private static RunResult runGA(GAParams gp, ExperimentConfiguration baseConfig,
            boolean makespanPrimary, long seed) {
        ExperimentConfiguration config = baseConfig.clone();
        RandomGenerator.initialize(seed);
        SimulationContext context = runPipeline(config);

        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(context.getHosts());

        SchedulingObjective primary = makespanPrimary ? makespan : energy;
        SchedulingObjective tiebreaker = makespanPrimary ? energy : makespan;

        GAConfiguration gaConfig = GAConfiguration.builder()
            .populationSize(gp.popSize)
            .crossoverRate(gp.crossoverRate)
            .mutationRate(gp.mutationRate)
            .eliteCount(gp.eliteCount)
            .tournamentSize(gp.tournamentSize)
            .addWeightedObjective(primary, 1.0)
            .addWeightedObjective(tiebreaker, TIEBREAKER_WEIGHT)
            .terminationCondition(new GenerationCountTermination(gp.generations))
            .verboseLogging(false)
            .build();

        TaskAssignmentStrategy strategy = new GenerationalGATaskSchedulingStrategy(gaConfig);
        return finishRun(context, strategy);
    }

    private static RunResult runSA(SAParams sp, ExperimentConfiguration baseConfig,
            boolean makespanPrimary, long seed) {
        ExperimentConfiguration config = baseConfig.clone();
        RandomGenerator.initialize(seed);
        SimulationContext context = runPipeline(config);

        MakespanObjective makespan = new MakespanObjective();
        EnergyObjective energy = new EnergyObjective();
        energy.setHosts(context.getHosts());

        SchedulingObjective primary = makespanPrimary ? makespan : energy;
        SchedulingObjective tiebreaker = makespanPrimary ? energy : makespan;

        SAConfiguration saConfig = SAConfiguration.builder()
            .initialTemperature(sp.initialTemp)
            .coolingSchedule(new GeometricCoolingSchedule(sp.coolingRate))
            .terminationCondition(new FitnessEvaluationsTermination(sp.totalEvals))
            .iterationsPerTemperature(sp.itersPerTemp)
            .addWeightedObjective(primary, 1.0)
            .addWeightedObjective(tiebreaker, TIEBREAKER_WEIGHT)
            .verboseLogging(false)
            .build();

        TaskAssignmentStrategy strategy = new SimulatedAnnealingTaskSchedulingStrategy(saConfig);
        return finishRun(context, strategy);
    }

    private static SimulationContext runPipeline(ExperimentConfiguration config) {
        SimulationContext context = new SimulationContext();
        new InitializationStep(config).execute(context);
        new HostPlacementStep(new PowerAwareLoadBalancingHostPlacementStrategy()).execute(context);
        new UserDatacenterMappingStep().execute(context);
        new VMPlacementStep(new BestFitVMPlacementStrategy()).execute(context);
        return context;
    }

    private static RunResult finishRun(SimulationContext context, TaskAssignmentStrategy strategy) {
        long t0 = System.currentTimeMillis();
        new TaskAssignmentStep(strategy).execute(context);
        new VMExecutionStep().execute(context);
        TaskExecutionStep taskExec = new TaskExecutionStep();
        taskExec.execute(context);
        EnergyCalculationStep energyCalc = new EnergyCalculationStep();
        energyCalc.execute(context);
        long t1 = System.currentTimeMillis();

        RunResult r = new RunResult();
        r.makespan = taskExec.getMakespan();
        r.energy = energyCalc.getTotalITEnergyKWh();
        r.timeMs = t1 - t0;
        return r;
    }

    // =========================================================================
    // SCENARIO CONFIG (same as main runner)
    // =========================================================================
    private static ExperimentConfiguration buildScenarioConfig(int scenario, long seed) {
        ExperimentConfiguration config = new ExperimentConfiguration();
        config.setRandomSeed(seed);
        config.addDatacenterConfig(new DatacenterConfig("DC-Experiment", 20, 100000.0));
        for (int i = 0; i < 4; i++)
            config.addHostConfig(new HostConfig(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0, 32768, 10000, 1048576, "StandardPowerModel"));
        for (int i = 0; i < 3; i++)
            config.addHostConfig(new HostConfig(2_800_000_000L, 8, ComputeType.GPU_ONLY, 4, 32768, 10000, 1048576, "HighPerformancePowerModel"));
        for (int i = 0; i < 3; i++)
            config.addHostConfig(new HostConfig(3_000_000_000L, 32, ComputeType.CPU_GPU_MIXED, 4, 65536, 10000, 1048576, "HighPerformancePowerModel"));

        // 15 VMs (same as main runner)
        config.addVMConfig(new VMConfig("ExperimentUser", 4_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 4_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 2_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 2_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 1_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 4_000_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.GPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 4_000_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.GPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 1_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));
        config.addVMConfig(new VMConfig("ExperimentUser", 3_500_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));
        config.addVMConfig(new VMConfig("ExperimentUser", 3_500_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));
        config.addVMConfig(new VMConfig("ExperimentUser", 2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));
        config.addVMConfig(new VMConfig("ExperimentUser", 2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));
        config.addVMConfig(new VMConfig("ExperimentUser", 1_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));

        int idx = scenario - 1;
        List<TaskConfig> tasks = new ArrayList<>();
        tasks.addAll(generateTasks(SCENARIO_TASK_COUNTS[idx][0], CPU_WORKLOADS, "CPU", scenario));
        tasks.addAll(generateTasks(SCENARIO_TASK_COUNTS[idx][1], GPU_WORKLOADS, "GPU", scenario));
        for (TaskConfig tc : tasks) config.addTaskConfig(tc);
        Map<WorkloadType, Integer> taskCounts = new HashMap<>();
        for (TaskConfig tc : tasks) taskCounts.merge(tc.getWorkloadType(), 1, Integer::sum);
        config.addUserConfig(new UserConfig("ExperimentUser", List.of("DC-Experiment"), 5, 5, 5, taskCounts));
        return config;
    }

    private static List<TaskConfig> generateTasks(int count, WorkloadType[] types, String prefix, int scenario) {
        List<TaskConfig> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WorkloadType wt = types[i % types.length];
            long instrLen = INSTRUCTION_LENGTHS[i % INSTRUCTION_LENGTHS.length];
            tasks.add(new TaskConfig("S" + scenario + "_" + prefix + "_" + wt.name() + "_" + i, "ExperimentUser", instrLen, wt));
        }
        return tasks;
    }

    // =========================================================================
    // OUTPUT
    // =========================================================================
    private static void printSummary(String label, Map<String, double[]> results, boolean sortByMakespan) {
        System.out.println("\n------------------------------------------------------------");
        System.out.println("  " + label + " RESULTS (sorted by " + (sortByMakespan ? "makespan" : "energy") + ")");
        System.out.println("------------------------------------------------------------");
        System.out.printf("%-22s | %-12s | %-12s%n", "Config", "Avg Makespan", "Avg Energy");
        System.out.println("-".repeat(52));

        List<Map.Entry<String, double[]>> sorted = new ArrayList<>(results.entrySet());
        if (sortByMakespan) {
            sorted.sort((a, b) -> {
                int cmp = Double.compare(a.getValue()[0], b.getValue()[0]);
                return cmp != 0 ? cmp : Double.compare(a.getValue()[1], b.getValue()[1]);
            });
        } else {
            sorted.sort((a, b) -> {
                int cmp = Double.compare(a.getValue()[1], b.getValue()[1]);
                return cmp != 0 ? cmp : Double.compare(a.getValue()[0], b.getValue()[0]);
            });
        }

        for (Map.Entry<String, double[]> e : sorted) {
            System.out.printf("%-22s | %-12.2f | %-12.6f%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
        }
    }

    private static void writeCSV(Map<String, double[]> gaMakespan, Map<String, double[]> gaEnergy,
            Map<String, double[]> saMakespan, Map<String, double[]> saEnergy) {
        String file = REPORTS_DIR + "/tuning_results.csv";
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("Algorithm,Config,AvgMakespan,AvgEnergy");
            for (var e : gaMakespan.entrySet())
                w.printf("GA_Makespan,%s,%.4f,%.9f%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
            for (var e : gaEnergy.entrySet())
                w.printf("GA_Energy,%s,%.4f,%.9f%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
            for (var e : saMakespan.entrySet())
                w.printf("SA_Makespan,%s,%.4f,%.9f%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
            for (var e : saEnergy.entrySet())
                w.printf("SA_Energy,%s,%.4f,%.9f%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
            System.out.println("  Wrote: " + file);
        } catch (IOException e) {
            System.err.println("ERROR writing CSV: " + e.getMessage());
        }
    }
}
