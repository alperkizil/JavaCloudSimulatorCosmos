package com.cloudsimulator.PlacementStrategy.task;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.LaneSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Workload-aware task assignment strategy that considers task size and VM processing power.
 *
 * Algorithm:
 * For each task:
 *   1. Estimate the VM's completion time if the task were appended to its FIFO
 *      queue, by replaying the per-vCPU FIFO scheduler via {@link LaneSchedule}
 *      (the same analytic mirror of {@code VM.executeOneSecond} the scheduling
 *      objectives use): tasks dispatch in queue order onto vCPU lanes running at
 *      the host-clamped effective per-vCPU IPS, with concurrent GPU tasks capped
 *      by the VM's bound GPU count (head-of-line non-blocking).
 *   2. Select the VM with the lowest estimated completion time.
 *   3. Ties are broken by selecting the first VM encountered.
 *
 * Where:
 * - each lane runs one task at a time at vm.effectiveIpsPerVcpu
 * - a task of length L costs ceil(L / effectiveIpsPerVcpu) ticks
 * - concurrency width = vm.requestedVcpuCount lanes, and additionally at most
 *   vm.boundGpuCount concurrent GPU-workload tasks (a GPU task needs a lane AND
 *   a GPU) — without the GPU cap the estimate systematically under-counts
 *   completion time on GPU/MIXED VMs whose GPU count is below their vCPU count
 *
 * Characteristics:
 * - Considers task instruction length, VM speed (clamped to host per-core IPS),
 *   parallelism width, GPU concurrency cap, and current queue load
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
     * Estimates the VM's completion time after appending the new task to its
     * FIFO queue, by replaying the per-vCPU FIFO scheduler via
     * {@link LaneSchedule} — including the GPU concurrency cap
     * (vm.boundGpuCount) that the previous hand-rolled least-loaded-lane
     * estimate ignored.
     *
     * LaneSchedule costs each task by its full instruction length; in the
     * offline pipeline this strategy runs in (assignment before any execution)
     * queued tasks have executed nothing, so remaining == full length.
     */
    private long estimateCompletionTicks(VM vm, Task newTask, long effIps) {
        List<Task> queue = new ArrayList<>(vm.getAssignedTasks());
        queue.add(newTask);

        List<Integer> order = new ArrayList<>(queue.size());
        for (int i = 0; i < queue.size(); i++) {
            order.add(i);
        }

        return LaneSchedule
            .schedule(order, queue, effIps, vm.getRequestedVcpuCount(), vm.getBoundGpuCount())
            .getCompletionTicks();
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
