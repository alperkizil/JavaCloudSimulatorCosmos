package com.cloudsimulator.PlacementStrategy.task;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.List;
import java.util.Optional;

/**
 * Workload-aware task assignment strategy that considers task size and VM processing power.
 *
 * Algorithm:
 * For each task:
 *   1. Estimate the VM's completion time if the task were added, under the
 *      per-vCPU FIFO scheduler: the VM's queued tasks plus the new task are
 *      distributed across its vCPU lanes (each running at the host-clamped
 *      effective per-vCPU IPS, least-loaded lane first), and the estimate is the
 *      busiest lane's finish time.
 *   2. Select the VM with the lowest estimated completion time.
 *   3. Ties are broken by selecting the first VM encountered.
 *
 * Where:
 * - each lane runs one task at a time at vm.effectiveIpsPerVcpu
 * - a task of length L costs ceil(L / effectiveIpsPerVcpu) ticks
 * - lane count = vm.requestedVcpuCount (true parallelism width)
 *
 * Characteristics:
 * - Time complexity: O(m * k) per task where m = VMs, k = avg queue length
 * - Considers task instruction length, VM speed (clamped to host per-core IPS),
 *   parallelism width, and current per-lane queue load
 * - Minimizes makespan with a greedy heuristic consistent with the simulation
 *
 * Use case:
 * - Heterogeneous VM environments (different processing powers / widths)
 * - Tasks with varying execution times
 * - When minimizing total completion time is important
 */
public class WorkloadAwareTaskAssignmentStrategy implements TaskAssignmentStrategy {

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        if (candidateVMs == null || candidateVMs.isEmpty()) {
            return Optional.empty();
        }

        VM bestVM = null;
        long lowestCompletionTicks = Long.MAX_VALUE;

        for (VM vm : candidateVMs) {
            long effIps = vm.getEffectiveIpsPerVcpu();
            if (effIps <= 0) {
                continue; // VM has no usable processing power
            }

            long completionTicks = estimateCompletionTicks(vm, task, effIps);
            if (completionTicks < lowestCompletionTicks) {
                lowestCompletionTicks = completionTicks;
                bestVM = vm;
            }
        }

        return Optional.ofNullable(bestVM);
    }

    /**
     * Estimates the VM's completion time (busiest vCPU lane) after adding the new
     * task, mirroring the per-vCPU FIFO scheduler: queued tasks (by remaining
     * instructions) and the new task are placed on the least-loaded lane in turn,
     * each costing ceil(instructions / effIpsPerVcpu) ticks.
     */
    private long estimateCompletionTicks(VM vm, Task newTask, long effIps) {
        long[] lanes = new long[Math.max(1, vm.getRequestedVcpuCount())];

        for (Task queued : vm.getAssignedTasks()) {
            addToLeastLoaded(lanes, ceilDiv(queued.getRemainingInstructions(), effIps));
        }
        addToLeastLoaded(lanes, ceilDiv(newTask.getInstructionLength(), effIps));

        long max = 0L;
        for (long load : lanes) {
            if (load > max) {
                max = load;
            }
        }
        return max;
    }

    private static void addToLeastLoaded(long[] lanes, long ticks) {
        int idx = 0;
        for (int i = 1; i < lanes.length; i++) {
            if (lanes[i] < lanes[idx]) {
                idx = i;
            }
        }
        lanes[idx] += ticks;
    }

    private static long ceilDiv(long instructions, long ipsPerVcpu) {
        return (instructions + ipsPerVcpu - 1) / ipsPerVcpu;
    }

    @Override
    public String getStrategyName() {
        return "WorkloadAware";
    }

    @Override
    public String getDescription() {
        return "Assigns each task to minimize estimated completion time under the " +
               "per-vCPU FIFO scheduler. Considers task length, host-clamped VM " +
               "speed, parallelism width, and current per-lane queue load.";
    }
}
