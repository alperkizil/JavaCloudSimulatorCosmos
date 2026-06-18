package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.VmState;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GAConfiguration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.GenerationalGATaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.MakespanObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Consistency check for the per-vCPU FIFO scheduler redesign.
 *
 * For each scenario it runs the GA to optimize makespan, then:
 *   - reads the PREDICTED makespan from MakespanObjective (the analytic lane model)
 *   - runs the actual discrete simulation (VM.executeOneSecond tick loop) on the
 *     SAME solution and measures the SIMULATED makespan
 * and asserts they are equal. Energy (objective vs tick-by-tick host accounting)
 * is reported for sanity.
 *
 * Run:
 *   javac -cp "lib/*:target/classes" -d target/test-classes \
 *       src/test/java/com/cloudsimulator/LaneConsistencyCheck.java
 *   java -cp "target/test-classes:target/classes:lib/*" \
 *       com.cloudsimulator.LaneConsistencyCheck
 */
public class LaneConsistencyCheck {

    private static final long HOST_PER_CORE_IPS = 2_000_000_000L; // clamp threshold

    public static void main(String[] args) {
        System.out.println("=== Per-vCPU FIFO scheduler consistency check (predicted vs simulated) ===\n");

        boolean allPassed = true;
        long[] seeds = {1L, 7L, 42L, 123L, 2024L};

        for (long seed : seeds) {
            allPassed &= runScenario(seed);
        }

        System.out.println("\n================================================================");
        System.out.println(allPassed
            ? "ALL SCENARIOS CONSISTENT: predicted makespan == simulated makespan"
            : "INCONSISTENCY DETECTED (see above)");
        System.out.println("================================================================");
        if (!allPassed) {
            System.exit(1);
        }
    }

    private static boolean runScenario(long seed) {
        RandomGenerator.initialize(seed);

        // ---- Build infrastructure: one host, VMs placed on it, fresh tasks ----
        Host host = new Host(HOST_PER_CORE_IPS, 32, ComputeType.CPU_ONLY, 0);
        host.setRamCapacityMB(1_000_000);
        host.activate(0, 1L); // assign to a datacenter so updateState() runs

        List<VM> vms = createVMs();
        for (VM vm : vms) {
            host.assignVM(vm); // sets effectiveIpsPerVcpu = min(requested, host per-core IPS)
        }

        List<Task> tasks = createTasks();

        // ---- GA-optimized solution ----
        GAConfiguration config = GAConfiguration.builder()
            .populationSize(40)
            .crossoverRate(0.9)
            .mutationRate(0.15)
            .elitePercentage(0.1)
            .tournamentSize(3)
            .objective(new MakespanObjective())
            .terminationCondition(new GenerationCountTermination(80))
            .verboseLogging(false)
            .build();

        SchedulingSolution gaSolution =
            new GenerationalGATaskSchedulingStrategy(config).optimize(tasks, vms);

        boolean ok = check(seed, "GA  ", gaSolution, host, vms, tasks, true);

        // ---- Random (often imbalanced) solutions: stress lane queueing off-optimum ----
        java.util.Random rng = new java.util.Random(seed);
        long minM = Long.MAX_VALUE, maxM = Long.MIN_VALUE;
        boolean randomOk = true;
        int randomCount = 6;
        for (int r = 0; r < randomCount; r++) {
            SchedulingSolution rs = randomSolution(tasks.size(), vms.size(), rng);
            long predicted = (long) new MakespanObjective().evaluate(rs, tasks, vms);
            long simulated = simulate(host, vms, tasks, rs)[0];
            randomOk &= predicted == simulated;
            minM = Math.min(minM, simulated);
            maxM = Math.max(maxM, simulated);
            if (predicted != simulated) {
                System.out.printf("seed=%-5d  RANDOM #%d MISMATCH: predicted=%d simulated=%d%n",
                    seed, r, predicted, simulated);
            }
        }
        System.out.printf("           random x%d: %s  (simulated makespan range %d..%d)%n",
            randomCount, randomOk ? "all MATCH" : "MISMATCH", minM, maxM);

        return ok && randomOk;
    }

    /** Evaluates predicted vs simulated for one solution; prints and returns match. */
    private static boolean check(long seed, String label, SchedulingSolution solution,
                                 Host host, List<VM> vms, List<Task> tasks, boolean withEnergy) {
        double predictedMakespan = new MakespanObjective().evaluate(solution, tasks, vms);

        double predictedEnergyKwh = 0.0;
        if (withEnergy) {
            EnergyObjective energyObj = new EnergyObjective();
            energyObj.setHosts(List.of(host)); // align power reference with the host model
            predictedEnergyKwh = energyObj.evaluate(solution, tasks, vms);
        }

        long[] sim = simulate(host, vms, tasks, solution);
        long simulatedMakespan = sim[0];
        double simulatedEnergyKwh = Double.longBitsToDouble(sim[1]);

        boolean makespanMatch = (long) predictedMakespan == simulatedMakespan;
        System.out.printf("seed=%-5d  %s predicted makespan=%4.0f  simulated makespan=%4d  -> %s%n",
            seed, label, predictedMakespan, simulatedMakespan, makespanMatch ? "MATCH" : "MISMATCH");
        if (withEnergy) {
            double rel = predictedEnergyKwh == 0.0 ? 0.0
                : Math.abs(simulatedEnergyKwh - predictedEnergyKwh) / predictedEnergyKwh;
            System.out.printf("           predicted energy=%.6f kWh  simulated energy=%.6f kWh  (rel diff %.4f%%)%n",
                predictedEnergyKwh, simulatedEnergyKwh, rel * 100.0);
        }
        return makespanMatch;
    }

    /** Builds a solution assigning each task to a uniformly random VM. */
    private static SchedulingSolution randomSolution(int numTasks, int numVMs, java.util.Random rng) {
        SchedulingSolution s = new SchedulingSolution(numTasks, numVMs, 1);
        for (int t = 0; t < numTasks; t++) {
            s.setAssignedVM(t, rng.nextInt(numVMs));
        }
        s.rebuildTaskOrdering();
        return s;
    }

    /**
     * Runs the discrete simulation (mirrors VMExecutionStep) on the given solution
     * and returns {makespan, doubleToLongBits(energyKwh)}.
     */
    private static long[] simulate(Host host, List<VM> vms, List<Task> tasks, SchedulingSolution solution) {
        // Reset execution state.
        for (VM vm : vms) {
            vm.resetForRescheduling(); // clears queues; sets RUNNING since placed
        }
        for (Task t : tasks) {
            t.reset();
        }
        host.resetForRescheduling();

        // Assign tasks to VM queues in the solution's per-VM order.
        for (int v = 0; v < vms.size(); v++) {
            for (int taskIdx : solution.getTaskOrderForVM(v)) {
                vms.get(v).assignTask(tasks.get(taskIdx));
            }
        }
        for (VM vm : vms) {
            vm.start();
        }

        long t = 0;
        final long cap = 100_000_000L;
        while (anyTasksRemaining(vms) && t < cap) {
            for (VM vm : vms) {
                if (vm.getVmState() == VmState.RUNNING && vm.isAssignedToHost()) {
                    vm.executeOneSecond(t);
                    vm.updateState();
                }
            }
            host.updateState();
            t++;
        }

        long firstStart = Long.MAX_VALUE;
        long lastEnd = Long.MIN_VALUE;
        for (Task task : tasks) {
            if (task.getTaskExecStartTime() != null) {
                firstStart = Math.min(firstStart, task.getTaskExecStartTime());
            }
            if (task.getTaskExecEndTime() != null) {
                lastEnd = Math.max(lastEnd, task.getTaskExecEndTime());
            }
        }
        long makespan = (lastEnd >= firstStart) ? (lastEnd - firstStart + 1) : 0;
        return new long[]{ makespan, Double.doubleToLongBits(host.getTotalEnergyConsumedKWh()) };
    }

    private static boolean anyTasksRemaining(List<VM> vms) {
        for (VM vm : vms) {
            if (!vm.getAssignedTasks().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** VMs that exercise clamping (A), no clamping (B), and the clamp boundary (C). */
    private static List<VM> createVMs() {
        List<VM> vms = new ArrayList<>();
        // A: 3.0 GIPS/vCPU requested -> clamps to host 2.0 GIPS/core; 4 lanes.
        vms.add(new VM("u", 3_000_000_000L, 4, 0, 4096, 10240, 100, ComputeType.CPU_ONLY));
        // B: 1.0 GIPS/vCPU (below clamp); 2 lanes.
        vms.add(new VM("u", 1_000_000_000L, 2, 0, 4096, 10240, 100, ComputeType.CPU_ONLY));
        // C: exactly the clamp threshold; 3 lanes.
        vms.add(new VM("u", 2_000_000_000L, 3, 0, 4096, 10240, 100, ComputeType.CPU_ONLY));
        return vms;
    }

    /** Mixed CPU workloads of varying length so lanes queue and VMs differ. */
    private static List<Task> createTasks() {
        List<Task> tasks = new ArrayList<>();
        long[] lengths = {
            1_500_000_000L, 9_000_000_000L, 4_000_000_000L, 7_500_000_000L,
            2_200_000_000L, 6_000_000_000L, 3_300_000_000L, 8_800_000_000L,
            5_100_000_000L, 1_000_000_000L, 10_000_000_000L, 2_700_000_000L
        };
        WorkloadType[] wl = {
            WorkloadType.SEVEN_ZIP, WorkloadType.DATABASE, WorkloadType.LLM_CPU,
            WorkloadType.VERACRYPT, WorkloadType.CINEBENCH, WorkloadType.PRIME95SmallFFT
        };
        for (int i = 0; i < lengths.length; i++) {
            tasks.add(new Task("T" + i, "u", lengths[i], wl[i % wl.length]));
        }
        return tasks;
    }
}
