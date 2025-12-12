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
 *   1. Calculate estimated completion time for each candidate VM:
 *      estimated_time = (current_queue_workload + task.instructionLength) / vm.totalIPS
 *   2. Select the VM with the lowest estimated completion time
 *   3. Ties are broken by selecting the first VM encountered
 *
 * Where:
 * - current_queue_workload = sum of remaining instructions for all pending tasks in VM queue
 * - task.instructionLength = total instructions for the new task
 * - vm.totalIPS = vm.ipsPerVCPU * vm.vcpuCount
 *
 * Characteristics:
 * - Time complexity: O(m * k) per task where m = VMs, k = avg queue length
 * - Considers task instruction length (bigger tasks matter more)
 * - Considers VM processing power (faster VMs can handle more)
 * - Considers current queue load (not just count, but total work)
 * - Minimizes makespan (total time to complete all tasks) with greedy heuristic
 *
 * Use case:
 * - Heterogeneous VM environments (different processing powers)
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
        double lowestEstimatedTime = Double.MAX_VALUE;

        for (VM vm : candidateVMs) {
            // Calculate current queue workload (sum of remaining instructions)
            long currentQueueWorkload = calculateQueueWorkload(vm);

            // Add the new task's instruction length
            long totalWorkload = currentQueueWorkload + task.getInstructionLength();

            // Calculate VM's total IPS
            long vmTotalIps = vm.getTotalRequestedIps();

            // Avoid division by zero
            if (vmTotalIps == 0) {
                continue;
            }

            // Estimate completion time for all tasks including this one
            double estimatedTime = (double) totalWorkload / vmTotalIps;

            if (estimatedTime < lowestEstimatedTime) {
                lowestEstimatedTime = estimatedTime;
                bestVM = vm;
            }
        }

        return Optional.ofNullable(bestVM);
    }

    /**
     * Calculates the total remaining workload in a VM's task queue.
     *
     * @param vm The VM to calculate workload for
     * @return Total remaining instructions across all queued tasks
     */
    private long calculateQueueWorkload(VM vm) {
        long totalWorkload = 0;

        for (Task queuedTask : vm.getAssignedTasks()) {
            // Use remaining instructions (not total) for tasks in progress
            totalWorkload += queuedTask.getRemainingInstructions();
        }

        return totalWorkload;
    }

    @Override
    public String getStrategyName() {
        return "WorkloadAware";
    }

    @Override
    public String getDescription() {
        return "Assigns each task to minimize estimated completion time. " +
               "Considers task instruction length, VM processing power, and current queue workload.";
    }
}
