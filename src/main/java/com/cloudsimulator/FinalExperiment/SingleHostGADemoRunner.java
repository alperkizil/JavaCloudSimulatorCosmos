package com.cloudsimulator.FinalExperiment;

import com.cloudsimulator.config.*;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.VmState;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.model.VMUtilization;
import com.cloudsimulator.utils.RandomGenerator;

import com.cloudsimulator.steps.InitializationStep;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.steps.UserDatacenterMappingStep;
import com.cloudsimulator.steps.VMPlacementStep;
import com.cloudsimulator.steps.TaskAssignmentStep;
import com.cloudsimulator.steps.TaskExecutionStep;
import com.cloudsimulator.steps.EnergyCalculationStep;

import com.cloudsimulator.PlacementStrategy.hostPlacement.PowerAwareLoadBalancingHostPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.VMPlacement.BestFitVMPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GenerationalGATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.MakespanObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step-by-step demonstration runner for a single, hand-specified scenario:
 *
 *   1 Datacenter, 1 User, 1 Host (16 cores @ 3.0 GIPS/core, CPU_GPU_MIXED, 2 GPUs),
 *   4 VMs (each 4 vCPU / 4 GB / 40 GB):
 *      VM1 GPU_ONLY      (1 GPU)  req 5.0 GIPS/vCPU  -> clamped to 3.0
 *      VM2 CPU_GPU_MIXED (1 GPU)  req 2.0 GIPS/vCPU  -> 2.0 (no clamp)
 *      VM3 CPU_ONLY      (0 GPU)  req 5.0 GIPS/vCPU  -> clamped to 3.0
 *      VM4 CPU_ONLY      (0 GPU)  req 0.5 GIPS/vCPU  -> 0.5 (no clamp)
 *   100 tasks (50 SEVEN_ZIP CPU + 50 FURMARK GPU), each 1e9 instructions.
 *   Task scheduler: Generational GA, Energy primary (makespan as a true tiebreaker).
 *
 * This is a teaching harness: it narrates each phase of the pipeline and prints a
 * per-tick trace of the discrete simulation loop and the workload-aware power model.
 * It does not touch the main experiment runner.
 */
public class SingleHostGADemoRunner {

    private static final long SEED = 42L;
    private static final long HOST_IPS_PER_CORE = 3_000_000_000L; // 3.0 GIPS/core
    private static final long TASK_LEN = 1_000_000_000L;          // 1e9 instructions/task
    private static final int  CPU_TASKS = 50;
    private static final int  GPU_TASKS = 50;
    private static final String USER = "DemoUser";
    private static final String DC = "DC-Demo";

    public static void main(String[] args) {
        RandomGenerator.initialize(SEED);

        banner("PHASE 0  -  BUILD INFRASTRUCTURE & CONFIG");
        ExperimentConfiguration config = buildConfig();

        // ---- Pipeline steps 1-4 : create entities, place host, map user, place VMs ----
        SimulationContext context = new SimulationContext();
        new InitializationStep(config).execute(context);
        new HostPlacementStep(new PowerAwareLoadBalancingHostPlacementStrategy()).execute(context);
        new UserDatacenterMappingStep().execute(context);
        new VMPlacementStep(new BestFitVMPlacementStrategy()).execute(context);

        Host host = context.getHosts().get(0);
        List<VM> vms = runningVMs(context);
        printInfrastructure(host, vms);

        // ---- The energy objective also wires the per-core power-scaling reference ----
        EnergyObjective energyObj = new EnergyObjective();
        energyObj.setHosts(context.getHosts()); // sets referenceVmIps = median host IPS/core on every host's power model
        MakespanObjective makespanObj = new MakespanObjective();

        printPowerReference(host, vms, energyObj);

        // ---- Step 5 : GA task assignment (Energy primary, makespan tiebreaker) ----
        banner("PHASE 1  -  TASK SCHEDULING (Genetic Algorithm, objective = Energy)");
        GAConfiguration gaConfig = GAConfiguration.builder()
            .populationSize(120)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .elitePercentage(0.10)
            .tournamentSize(3)
            // Energy weight 1.0; makespan weight 1e-8 keeps makespan a TRUE tiebreaker
            // (energy ~1e-3 kWh vs makespan ~1e1 s; fitness = w1*energyKWh + w2*makespanS).
            .addWeightedObjective(energyObj, 1.0)
            .addWeightedObjective(makespanObj, 1e-8)
            .terminationCondition(new GenerationCountTermination(200))
            .verboseLogging(false)
            .build();

        GenerationalGATaskSchedulingStrategy ga = new GenerationalGATaskSchedulingStrategy(gaConfig);
        TaskAssignmentStrategy strategy = ga;

        long t0 = System.currentTimeMillis();
        new TaskAssignmentStep(strategy).execute(context);
        long gaMs = System.currentTimeMillis() - t0;

        printAssignment(vms, gaMs);

        // ---- What the GA *predicted* (analytic objectives, lane model) ----
        SchedulingSolution best = ga.getLastSolution();
        List<Task> taskList = new ArrayList<>(context.getTasks());
        double predEnergyKWh = energyObj.evaluate(best, taskList, vms);
        double predMakespan  = makespanObj.evaluate(best, taskList, vms);
        System.out.printf("%n  GA predicted (analytic): makespan = %.0f s, energy = %.6f kWh%n",
            predMakespan, predEnergyKWh);

        // ---- Step 6 : discrete simulation loop, instrumented per tick ----
        banner("PHASE 2  -  EXECUTION LOOP (discrete 1-second ticks) + POWER PER TICK");
        TraceResult trace = runInstrumentedLoop(context, host, vms);

        // ---- Steps 7-8 : authoritative makespan & energy from the standard analysers ----
        banner("PHASE 3  -  RESULTS, RECONCILIATION & POWER DECOMPOSITION");
        TaskExecutionStep taskExec = new TaskExecutionStep();
        taskExec.execute(context);
        EnergyCalculationStep energyCalc = new EnergyCalculationStep();
        energyCalc.execute(context);

        printResults(host, vms, taskExec, energyCalc, trace, predMakespan, predEnergyKWh);
    }

    // =====================================================================
    // CONFIG
    // =====================================================================

    private static ExperimentConfiguration buildConfig() {
        ExperimentConfiguration cfg = new ExperimentConfiguration();
        cfg.setRandomSeed(SEED);

        cfg.addDatacenterConfig(new DatacenterConfig(DC, 50, 400000.0));

        // Single host: 16 cores @ 3.0 GIPS/core, MIXED (can host CPU/GPU/mixed VMs), 2 GPUs,
        // generous RAM/disk/bw so only cores+GPUs constrain placement ("unlimited RAM/disk").
        cfg.addHostConfig(new HostConfig(
            HOST_IPS_PER_CORE, 16, ComputeType.CPU_GPU_MIXED, 2,
            1_048_576, 100_000, 10_485_760, "MeasurementBasedPowerModel"));

        // 4 VMs: 4 vCPU, 4 GB RAM, 40 GB disk each. Speed spread 5G / 2G / 5G / 0.5G.
        cfg.addVMConfig(new VMConfig(USER, 5_000_000_000L, 4, 1, 4096, 40960, 1000, ComputeType.GPU_ONLY));       // VM1
        cfg.addVMConfig(new VMConfig(USER, 2_000_000_000L, 4, 1, 4096, 40960, 1000, ComputeType.CPU_GPU_MIXED));  // VM2
        cfg.addVMConfig(new VMConfig(USER, 5_000_000_000L, 4, 0, 4096, 40960, 1000, ComputeType.CPU_ONLY));       // VM3
        cfg.addVMConfig(new VMConfig(USER, 500_000_000L,  4, 0, 4096, 40960, 1000, ComputeType.CPU_ONLY));        // VM4

        // 100 tasks: 50 SEVEN_ZIP (CPU), 50 FURMARK (GPU), each 1e9 instructions.
        Map<WorkloadType, Integer> taskCounts = new LinkedHashMap<>();
        for (int i = 0; i < CPU_TASKS; i++) {
            cfg.addTaskConfig(new TaskConfig("CPU_SEVEN_ZIP_" + i, USER, TASK_LEN, WorkloadType.SEVEN_ZIP));
        }
        for (int i = 0; i < GPU_TASKS; i++) {
            cfg.addTaskConfig(new TaskConfig("GPU_FURMARK_" + i, USER, TASK_LEN, WorkloadType.FURMARK));
        }
        taskCounts.put(WorkloadType.SEVEN_ZIP, CPU_TASKS);
        taskCounts.put(WorkloadType.FURMARK, GPU_TASKS);

        cfg.addUserConfig(new UserConfig(USER, List.of(DC), 5, 5, 5, taskCounts));
        return cfg;
    }

    // =====================================================================
    // INSTRUMENTED EXECUTION LOOP  (mirror of VMExecutionStep, with logging)
    // =====================================================================

    static class TraceResult {
        long makespanTicks;
        double idleEnergyJ;                       // host base-idle energy over the run
        Map<Long, Double> vmIncrEnergyJ = new LinkedHashMap<>(); // per-VM incremental (above idle)
        Map<WorkloadType, Double> wlIncrEnergyJ = new LinkedHashMap<>();
    }

    private static TraceResult runInstrumentedLoop(SimulationContext context, Host host, List<VM> vms) {
        TraceResult tr = new TraceResult();
        for (VM vm : vms) tr.vmIncrEnergyJ.put(vm.getId(), 0.0);

        // Start all placed VMs (RUNNING), exactly as VMExecutionStep does.
        for (VM vm : context.getVms()) {
            if (vm.isAssignedToHost() && vm.getVmState() != VmState.RUNNING) vm.start();
        }

        MeasurementBasedPowerModel pm = host.getMeasurementBasedPowerModel();
        long tick = 0;

        System.out.println("  Legend: each VM line shows  <#busy lanes>x<WORKLOAD>  out of 4 vCPU lanes.");
        System.out.println("  Host power per tick = 75.79 W idle  +  Sum(active lane incremental power).");
        System.out.println();

        while (!allAssignedTasksComplete(context.getTasks())) {
            long currentTime = context.getCurrentTime();

            // 1) advance every RUNNING VM by one second (per-vCPU FIFO lanes)
            for (VM vm : context.getVms()) {
                if (vm.isAssignedToHost() && vm.getVmState() == VmState.RUNNING) {
                    vm.executeOneSecond(currentTime);
                    vm.updateState();
                }
            }
            // 2) every host updates power+energy from this tick's VM lane utilizations
            host.updateState();

            // ---- accumulate per-VM / per-workload incremental energy for the decomposition ----
            for (VM vm : vms) {
                long effIps = vm.getEffectiveIpsPerVcpu();
                for (VMUtilization.LaneUtilization lane : vm.getActiveLaneUtilizations()) {
                    double laneP = pm.calculateIncrementalPowerWithSpeedScaling(
                        lane.getWorkloadType(), lane.getCpuUtilization(), lane.getGpuUtilization(), effIps);
                    tr.vmIncrEnergyJ.merge(vm.getId(), laneP, Double::sum);
                    tr.wlIncrEnergyJ.merge(lane.getWorkloadType(), laneP, Double::sum);
                }
            }
            tr.idleEnergyJ += host.getOtherComponentsPowerDraw(); // = base idle (75.79 W) while host has VMs

            // ---- print the tick ----
            boolean verbose = (tick < 3);
            printTick(tick, vms, host, pm, verbose);

            context.advanceTime();
            tick++;
        }

        tr.makespanTicks = tick;
        return tr;
    }

    private static boolean allAssignedTasksComplete(List<Task> tasks) {
        for (Task t : tasks) {
            if (t.isAssigned() && !t.isCompleted()) return false;
        }
        return true;
    }

    private static void printTick(long tick, List<VM> vms, Host host, MeasurementBasedPowerModel pm, boolean verbose) {
        StringBuilder line = new StringBuilder();
        line.append(String.format("  t=%3d | ", tick));
        for (VM vm : vms) {
            line.append("VM").append(vmIndex(vm, vms) + 1).append(":").append(laneSummary(vm)).append("  ");
        }
        line.append(String.format("| host P=%6.1fW (idle %.2f + cpu %.1f + gpu %.1f)  E=%.0fJ",
            host.getCurrentTotalPowerDraw(), host.getOtherComponentsPowerDraw(),
            host.getCurrentCpuPowerDraw(), host.getCurrentGpuPowerDraw(),
            host.getTotalEnergyConsumed()));
        System.out.println(line);

        if (verbose) {
            for (VM vm : vms) {
                long effIps = vm.getEffectiveIpsPerVcpu();
                List<VMUtilization.LaneUtilization> lanes = vm.getActiveLaneUtilizations();
                if (lanes.isEmpty()) continue;
                double factor = pm.calculateSpeedPowerFactor(effIps);
                for (int i = 0; i < lanes.size(); i++) {
                    VMUtilization.LaneUtilization l = lanes.get(i);
                    double base = pm.calculateIncrementalPower(l.getWorkloadType(), l.getCpuUtilization(), l.getGpuUtilization());
                    double laneP = base * factor;
                    System.out.printf("         +- VM%d lane%d: %-10s cpu=%.2f gpu=%.2f | base=%6.2fW x speedFactor(%.0fM)=%.3f -> %6.2fW%n",
                        vmIndex(vm, vms) + 1, i, l.getWorkloadType(), l.getCpuUtilization(), l.getGpuUtilization(),
                        base, effIps / 1_000_000.0, factor, laneP);
                }
            }
        }
    }

    private static String laneSummary(VM vm) {
        List<VMUtilization.LaneUtilization> lanes = vm.getActiveLaneUtilizations();
        if (lanes.isEmpty()) return "----------";
        Map<WorkloadType, Integer> counts = new LinkedHashMap<>();
        for (VMUtilization.LaneUtilization l : lanes) counts.merge(l.getWorkloadType(), 1, Integer::sum);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<WorkloadType, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getValue()).append("x").append(shortWl(e.getKey()));
        }
        return String.format("%-10s", sb.toString());
    }

    private static String shortWl(WorkloadType wt) {
        switch (wt) {
            case SEVEN_ZIP: return "7ZIP";
            case FURMARK:   return "FUR";
            default:        return wt.name();
        }
    }

    // =====================================================================
    // PRINTING HELPERS
    // =====================================================================

    private static void printInfrastructure(Host host, List<VM> vms) {
        System.out.printf("  Datacenter: 1   User: 1   Host: 1   VMs: %d   (placed: %d)%n",
            vms.size(), vms.size());
        System.out.printf("  HOST  id=%d  type=%s  cores=%d  GPUs=%d  IPS/core=%.1fG  idle=%.2fW%n",
            host.getId(), host.getComputeType(), host.getNumberOfCpuCores(), host.getNumberOfGpus(),
            host.getInstructionsPerSecond() / 1e9, MeasurementBasedPowerModel.REFERENCE_IDLE_POWER);
        System.out.printf("        cores used = %d/%d   GPUs used = %d/%d   (1:1 vCPU<->core, no oversubscription)%n",
            host.getNumberOfCpuCores() - host.getAvailableCpuCores(), host.getNumberOfCpuCores(),
            host.getNumberOfGpus() - host.getAvailableGpus(), host.getNumberOfGpus());
        System.out.println();
        System.out.println("  VM    type            vCPU  GPU  reqIPS/vCPU  effIPS/vCPU (A2 clamp = min(req, host/core))");
        for (VM vm : vms) {
            boolean clamped = vm.getEffectiveIpsPerVcpu() < vm.getRequestedIpsPerVcpu();
            System.out.printf("  VM%-3d %-15s %3d  %3d   %7.1fG     %7.1fG  %s%n",
                vmIndex(vm, vms) + 1, vm.getComputeType(), vm.getRequestedVcpuCount(), vm.getRequestedGpuCount(),
                vm.getRequestedIpsPerVcpu() / 1e9, vm.getEffectiveIpsPerVcpu() / 1e9,
                clamped ? "<- CLAMPED to host core speed" : "(no clamp)");
        }
    }

    private static void printPowerReference(Host host, List<VM> vms, EnergyObjective energyObj) {
        long ref = host.getMeasurementBasedPowerModel().getReferenceVmIps();
        System.out.println();
        System.out.printf("  Power-scaling reference IPS = %.1fG (median host IPS/core); exponent = %.1f (quadratic).%n",
            ref / 1e9, MeasurementBasedPowerModel.POWER_SCALING_EXPONENT);
        System.out.println("  speedFactor(VM) = (effIPS/vCPU / referenceIPS) ^ 2   - faster lanes draw quadratically more power.");
        MeasurementBasedPowerModel pm = host.getMeasurementBasedPowerModel();
        for (VM vm : vms) {
            double f = pm.calculateSpeedPowerFactor(vm.getEffectiveIpsPerVcpu());
            double sevenZip = pm.calculateIncrementalPower(WorkloadType.SEVEN_ZIP, 1.0, 0.0) * f;
            double furmark  = pm.calculateIncrementalPower(WorkloadType.FURMARK, 0.08, 1.0) * f;
            System.out.printf("    VM%d effIPS=%.1fG -> speedFactor=%.3f | per-lane incr: SEVEN_ZIP=%6.2fW  FURMARK=%6.2fW%n",
                vmIndex(vm, vms) + 1, vm.getEffectiveIpsPerVcpu() / 1e9, f, sevenZip, furmark);
        }
    }

    private static void printAssignment(List<VM> vms, long gaMs) {
        System.out.printf("  GA finished in %d ms. Resulting task-to-VM assignment (FIFO queue per VM):%n%n", gaMs);
        System.out.println("  VM    type             totalTasks   SEVEN_ZIP(CPU)   FURMARK(GPU)   ticks/task @ effIPS");
        for (VM vm : vms) {
            int seven = 0, fur = 0;
            for (Task t : vm.getAssignedTasks()) {
                if (t.getWorkloadType() == WorkloadType.SEVEN_ZIP) seven++;
                else if (t.getWorkloadType() == WorkloadType.FURMARK) fur++;
            }
            long ticksPerTask = ceilDiv(TASK_LEN, vm.getEffectiveIpsPerVcpu());
            System.out.printf("  VM%-3d %-15s  %8d   %12d   %12d   %d tick(s)%n",
                vmIndex(vm, vms) + 1, vm.getComputeType(), vm.getAssignedTasks().size(),
                seven, fur, ticksPerTask);
        }
        System.out.println();
        System.out.println("  Note: GPU tasks (FURMARK) are only admissible on VM1 (GPU_ONLY) or VM2 (MIXED);");
        System.out.println("        CPU tasks (SEVEN_ZIP) only on VM2 (MIXED), VM3, VM4 (canAcceptTask gating).");
    }

    private static void printResults(Host host, List<VM> vms, TaskExecutionStep taskExec,
            EnergyCalculationStep energyCalc, TraceResult tr, double predMakespan, double predEnergyKWh) {

        long makespan = taskExec.getMakespan();
        double simEnergyKWh = energyCalc.getTotalITEnergyKWh();
        double simEnergyJ = energyCalc.getTotalITEnergyJoules();

        System.out.printf("  Makespan        : predicted %.0f s   |   simulated %d s%n", predMakespan, makespan);
        System.out.printf("  Energy (IT)     : predicted %.6f kWh |   simulated %.6f kWh%n", predEnergyKWh, simEnergyKWh);
        System.out.printf("  Tasks completed : %d / %d%n", taskExec.getCompletedTasks(), CPU_TASKS + GPU_TASKS);
        System.out.println();

        System.out.println("  -- Energy decomposition (simulated, summed per active lane each tick) --");
        System.out.printf("    Host base-idle : %10.1f J   (75.79 W x %d ticks x 1 host)%n", tr.idleEnergyJ, makespan);
        double totalIncr = 0.0;
        for (VM vm : vms) {
            double e = tr.vmIncrEnergyJ.getOrDefault(vm.getId(), 0.0);
            totalIncr += e;
            if (e > 0) {
                System.out.printf("    VM%d incremental: %10.1f J%n", vmIndex(vm, vms) + 1, e);
            }
        }
        System.out.printf("    Sum incremental  : %10.1f J%n", totalIncr);
        System.out.printf("    TOTAL (idle+incr): %8.1f J  = %.6f kWh   (host counter: %.1f J)%n",
            tr.idleEnergyJ + totalIncr, (tr.idleEnergyJ + totalIncr) / 3_600_000.0, simEnergyJ);
        System.out.println();
        System.out.println("  -- Incremental energy by workload --");
        for (Map.Entry<WorkloadType, Double> e : tr.wlIncrEnergyJ.entrySet()) {
            System.out.printf("    %-10s : %10.1f J%n", e.getKey(), e.getValue());
        }
        System.out.println();
        System.out.printf("  Lockstep check  : predicted vs simulated energy match within %.4f%%%n",
            simEnergyKWh == 0 ? 0.0 : Math.abs(predEnergyKWh - simEnergyKWh) / simEnergyKWh * 100.0);
    }

    // =====================================================================
    // SMALL UTILITIES
    // =====================================================================

    private static List<VM> runningVMs(SimulationContext context) {
        List<VM> out = new ArrayList<>();
        for (VM vm : context.getVms()) if (vm.isAssignedToHost()) out.add(vm);
        return out;
    }

    private static int vmIndex(VM vm, List<VM> vms) {
        for (int i = 0; i < vms.size(); i++) if (vms.get(i) == vm) return i;
        return -1;
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("============================================================================");
        System.out.println("  " + title);
        System.out.println("============================================================================");
    }
}
