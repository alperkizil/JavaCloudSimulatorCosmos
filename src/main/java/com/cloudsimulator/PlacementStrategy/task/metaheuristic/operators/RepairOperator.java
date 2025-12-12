package com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Repair operator for fixing invalid scheduling solutions.
 *
 * A solution can become invalid due to:
 * 1. Task assigned to VM owned by different user
 * 2. Task assigned to VM with incompatible compute type
 * 3. Task assigned to non-existent VM index
 *
 * The repair strategy:
 * 1. Pre-compute valid VMs for each task (cached)
 * 2. For each invalid assignment, randomly select a valid VM
 * 3. Rebuild task ordering lists after repair
 */
public class RepairOperator {

    private final Random random;
    private final List<Task> tasks;
    private final List<VM> vms;

    // Cache: task index -> list of valid VM indices
    private final Map<Integer, List<Integer>> validVmsForTask;

    /**
     * Creates a repair operator for the given problem instance.
     *
     * @param tasks  All tasks in the problem
     * @param vms    All VMs in the problem
     * @param random Random number generator for tie-breaking
     */
    public RepairOperator(List<Task> tasks, List<VM> vms, Random random) {
        this.tasks = tasks;
        this.vms = vms;
        this.random = random;
        this.validVmsForTask = precomputeValidVms();
    }

    /**
     * Pre-computes valid VM indices for each task.
     * This is cached to avoid repeated computation during evolution.
     *
     * @return Map of task index to list of valid VM indices
     */
    private Map<Integer, List<Integer>> precomputeValidVms() {
        Map<Integer, List<Integer>> validVms = new HashMap<>();

        for (int taskIdx = 0; taskIdx < tasks.size(); taskIdx++) {
            Task task = tasks.get(taskIdx);
            List<Integer> valid = new ArrayList<>();

            for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
                VM vm = vms.get(vmIdx);

                // Check 1: Same user
                if (!vm.getUserId().equals(task.getUserId())) {
                    continue;
                }

                // Check 2: Compute type compatibility
                if (!vm.canAcceptTask(task)) {
                    continue;
                }

                valid.add(vmIdx);
            }

            validVms.put(taskIdx, valid);
        }

        return validVms;
    }

    /**
     * Repairs a solution by fixing all invalid task-to-VM assignments.
     *
     * @param solution The solution to repair (modified in place)
     * @return true if any repairs were made, false if solution was already valid
     */
    public boolean repair(SchedulingSolution solution) {
        boolean repaired = false;
        int[] assignment = solution.getTaskAssignment();

        for (int taskIdx = 0; taskIdx < assignment.length; taskIdx++) {
            int currentVmIdx = assignment[taskIdx];
            List<Integer> validVmIndices = validVmsForTask.get(taskIdx);

            if (validVmIndices == null || validVmIndices.isEmpty()) {
                // No valid VMs for this task - this is a problem setup issue
                // Assign to first VM as fallback (will be invalid but non-crashy)
                assignment[taskIdx] = 0;
                repaired = true;
                continue;
            }

            // Check if current assignment is valid
            if (!validVmIndices.contains(currentVmIdx)) {
                // REPAIR: Pick a random valid VM
                int repairedVmIdx = validVmIndices.get(random.nextInt(validVmIndices.size()));
                assignment[taskIdx] = repairedVmIdx;
                repaired = true;
            }
        }

        // Rebuild task ordering after repair
        if (repaired) {
            solution.rebuildTaskOrdering();
            solution.invalidate(); // Mark for re-evaluation
        }

        return repaired;
    }

    /**
     * Checks if a solution is valid (all assignments are valid).
     *
     * @param solution The solution to check
     * @return true if all assignments are valid
     */
    public boolean isValid(SchedulingSolution solution) {
        int[] assignment = solution.getTaskAssignment();

        for (int taskIdx = 0; taskIdx < assignment.length; taskIdx++) {
            int vmIdx = assignment[taskIdx];
            List<Integer> validVmIndices = validVmsForTask.get(taskIdx);

            if (validVmIndices == null || !validVmIndices.contains(vmIdx)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the list of valid VM indices for a specific task.
     *
     * @param taskIndex The task index
     * @return List of valid VM indices
     */
    public List<Integer> getValidVmsForTask(int taskIndex) {
        return validVmsForTask.getOrDefault(taskIndex, new ArrayList<>());
    }

    /**
     * Checks if any task has no valid VMs (infeasible problem).
     *
     * @return true if problem is feasible (all tasks have at least one valid VM)
     */
    public boolean isProblemFeasible() {
        for (List<Integer> validVms : validVmsForTask.values()) {
            if (validVms.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets tasks that have no valid VMs (for error reporting).
     *
     * @return List of task indices with no valid VMs
     */
    public List<Integer> getInfeasibleTasks() {
        List<Integer> infeasible = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : validVmsForTask.entrySet()) {
            if (entry.getValue().isEmpty()) {
                infeasible.add(entry.getKey());
            }
        }
        return infeasible;
    }
}
