package com.cloudsimulator.strategy.task;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Strategy interface for task-to-VM assignment algorithms.
 * Implementations define how tasks are assigned to VMs based on
 * different optimization criteria (load balancing, workload awareness, etc.).
 *
 * Task assignment considers:
 * - User ownership: Tasks can only be assigned to VMs owned by the same user
 * - Compute type compatibility: GPU tasks require GPU-capable VMs
 * - Current VM queue load: Number and size of pending tasks
 *
 * This interface supports both single-task assignment (for online/streaming scenarios)
 * and batch assignment (for offline/planning scenarios like metaheuristics).
 */
public interface TaskAssignmentStrategy {

    /**
     * Selects a VM for a single task based on the strategy's algorithm.
     * The list of candidate VMs should already be filtered to only include
     * VMs that are owned by the task's user and are compute-type compatible.
     *
     * @param task          The task to assign
     * @param candidateVMs  Available VMs to choose from (already filtered by user and compatibility)
     * @return Optional containing the selected VM, or empty if no suitable VM found
     */
    Optional<VM> selectVM(Task task, List<VM> candidateVMs);

    /**
     * Assigns all tasks to VMs in batch mode.
     * This method allows strategies to consider the global assignment problem
     * rather than making greedy per-task decisions.
     *
     * Default implementation calls selectVM for each task sequentially.
     * Metaheuristic strategies should override this for global optimization.
     *
     * @param tasks         All tasks to assign
     * @param vms           All available VMs
     * @param currentTime   Current simulation time (for assignment timestamps)
     * @return Map of Task to assigned VM (tasks with no valid VM are excluded)
     */
    default Map<Task, VM> assignAll(List<Task> tasks, List<VM> vms, long currentTime) {
        java.util.LinkedHashMap<Task, VM> assignments = new java.util.LinkedHashMap<>();

        for (Task task : tasks) {
            // Filter VMs for this task
            List<VM> candidates = vms.stream()
                .filter(vm -> vm.getUserId().equals(task.getUserId()))
                .filter(vm -> vm.canAcceptTask(task))
                .collect(java.util.stream.Collectors.toList());

            Optional<VM> selected = selectVM(task, candidates);
            if (selected.isPresent()) {
                VM vm = selected.get();
                // Assign task to VM
                task.assignToVM(vm.getId(), currentTime);
                vm.assignTask(task);
                assignments.put(task, vm);
            }
        }

        return assignments;
    }

    /**
     * Gets the name of this strategy for logging and reporting purposes.
     *
     * @return Strategy name
     */
    String getStrategyName();

    /**
     * Gets a description of how this strategy works.
     *
     * @return Strategy description
     */
    String getDescription();

    /**
     * Indicates whether this strategy performs batch optimization.
     * Batch strategies (like NSGA-II) consider all tasks together for global optimization.
     * Non-batch strategies assign tasks one at a time in a greedy manner.
     *
     * @return true if this strategy optimizes the entire assignment batch
     */
    default boolean isBatchOptimizing() {
        return false;
    }
}
