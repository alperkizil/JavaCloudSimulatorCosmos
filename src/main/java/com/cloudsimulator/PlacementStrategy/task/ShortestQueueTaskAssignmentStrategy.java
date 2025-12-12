package com.cloudsimulator.PlacementStrategy.task;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.List;
import java.util.Optional;

/**
 * Task assignment strategy that assigns each task to the VM with the shortest task queue.
 *
 * Algorithm:
 * For each task:
 *   1. Iterate through all candidate VMs
 *   2. Count the number of pending tasks in each VM's queue
 *   3. Select the VM with the fewest pending tasks
 *   4. Ties are broken by selecting the first VM encountered
 *
 * Characteristics:
 * - Time complexity: O(m) per task where m = number of candidate VMs
 * - Balances number of tasks across VMs
 * - Does not consider task size (a VM with 1 huge task looks "shorter" than one with 3 small tasks)
 * - Does not consider VM processing power differences
 *
 * Use case:
 * - When tasks have similar execution times
 * - When VMs have similar processing power
 * - Better load distribution than FirstAvailable
 */
public class ShortestQueueTaskAssignmentStrategy implements TaskAssignmentStrategy {

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        if (candidateVMs == null || candidateVMs.isEmpty()) {
            return Optional.empty();
        }

        VM bestVM = null;
        int shortestQueueLength = Integer.MAX_VALUE;

        for (VM vm : candidateVMs) {
            int queueLength = vm.getAssignedTasks().size();

            if (queueLength < shortestQueueLength) {
                shortestQueueLength = queueLength;
                bestVM = vm;
            }
        }

        return Optional.ofNullable(bestVM);
    }

    @Override
    public String getStrategyName() {
        return "ShortestQueue";
    }

    @Override
    public String getDescription() {
        return "Assigns each task to the VM with the fewest pending tasks. " +
               "Balances task count but ignores task size and VM processing power.";
    }
}
