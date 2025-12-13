package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

import org.moeaframework.core.Problem;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.EncodingUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * MOEA Framework Problem adapter for task scheduling.
 *
 * This class bridges our domain model (Tasks, VMs, SchedulingObjective) with
 * MOEA Framework's Problem interface. It handles:
 *
 * 1. Decision Variables: Integer encoding of task-to-VM assignments
 *    - Each task has one decision variable (the VM index it's assigned to)
 *    - Variables are constrained to only valid VMs for each task
 *
 * 2. Objectives: Uses our existing SchedulingObjective implementations
 *    - Typically Makespan and Energy objectives
 *    - Supports arbitrary number of objectives
 *
 * 3. Constraints: User ownership and compute type compatibility
 *    - Encoded as MOEA Framework constraints (constraint violation = 0 when feasible)
 *    - Tasks can only be assigned to VMs owned by the same user
 *    - GPU tasks require GPU-capable VMs
 *
 * The encoding uses indices into a per-task valid VM list to ensure all
 * generated solutions are valid with respect to user/compute constraints.
 */
public class TaskSchedulingProblem implements Problem {

    private final List<Task> tasks;
    private final List<VM> vms;
    private final List<SchedulingObjective> objectives;

    // Pre-computed valid VMs for each task (for constraint enforcement)
    // validVMsForTask[taskIndex] = list of valid VM indices
    private final List<List<Integer>> validVMsForTask;

    // Problem name for MOEA Framework
    private final String problemName;

    // Flag indicating if the problem is feasible (every task has at least one valid VM)
    private final boolean feasible;

    /**
     * Creates a new TaskSchedulingProblem.
     *
     * @param tasks      List of tasks to schedule
     * @param vms        List of available VMs
     * @param objectives List of objectives to optimize
     */
    public TaskSchedulingProblem(List<Task> tasks, List<VM> vms, List<SchedulingObjective> objectives) {
        this.tasks = new ArrayList<>(tasks);
        this.vms = new ArrayList<>(vms);
        this.objectives = new ArrayList<>(objectives);
        this.problemName = "TaskScheduling";

        // Pre-compute valid VMs for each task
        this.validVMsForTask = new ArrayList<>();
        boolean allTasksHaveValidVM = true;

        for (Task task : tasks) {
            List<Integer> validVMs = new ArrayList<>();
            for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
                VM vm = vms.get(vmIdx);
                // Check user ownership and compute type compatibility
                if (vm.getUserId().equals(task.getUserId()) && vm.canAcceptTask(task)) {
                    validVMs.add(vmIdx);
                }
            }
            validVMsForTask.add(validVMs);

            if (validVMs.isEmpty()) {
                allTasksHaveValidVM = false;
            }
        }

        this.feasible = allTasksHaveValidVM;
    }

    @Override
    public String getName() {
        return problemName;
    }

    @Override
    public int getNumberOfVariables() {
        return tasks.size();
    }

    @Override
    public int getNumberOfObjectives() {
        return objectives.size();
    }

    @Override
    public int getNumberOfConstraints() {
        // We encode constraints implicitly through variable bounds,
        // but add explicit constraints for any remaining violations
        return tasks.size(); // One constraint per task (valid assignment)
    }

    @Override
    public void evaluate(Solution solution) {
        // Convert MOEA Solution to our SchedulingSolution
        SchedulingSolution schedulingSolution = convertToSchedulingSolution(solution);

        // Evaluate each objective using our objective implementations
        for (int i = 0; i < objectives.size(); i++) {
            double value = objectives.get(i).evaluate(schedulingSolution, tasks, vms);
            solution.setObjective(i, value);
        }

        // Evaluate constraints (0 = feasible, >0 = violation)
        evaluateConstraints(solution, schedulingSolution);
    }

    /**
     * Evaluates constraint violations for the solution.
     * Uses MOEA Framework's constraint handling where 0 = satisfied, >0 = violated.
     */
    private void evaluateConstraints(Solution solution, SchedulingSolution schedulingSolution) {
        int[] assignment = schedulingSolution.getTaskAssignment();

        for (int taskIdx = 0; taskIdx < tasks.size(); taskIdx++) {
            int vmIdx = assignment[taskIdx];
            List<Integer> validVMs = validVMsForTask.get(taskIdx);

            // Constraint: assigned VM must be in the valid set
            if (validVMs.contains(vmIdx)) {
                solution.setConstraint(taskIdx, 0.0); // Satisfied
            } else {
                solution.setConstraint(taskIdx, 1.0); // Violated
            }
        }
    }

    @Override
    public Solution newSolution() {
        Solution solution = new Solution(getNumberOfVariables(), getNumberOfObjectives(), getNumberOfConstraints());
        RandomGenerator rng = RandomGenerator.getInstance();

        for (int taskIdx = 0; taskIdx < tasks.size(); taskIdx++) {
            List<Integer> validVMs = validVMsForTask.get(taskIdx);

            int assignedVM;
            if (validVMs.isEmpty()) {
                // No valid VM - assign to first VM (will be constraint-violated)
                assignedVM = 0;
            } else {
                // Randomly select from valid VMs using simulator's RNG for reproducibility
                int randomIndex = rng.nextInt(validVMs.size());
                assignedVM = validVMs.get(randomIndex);
            }

            // Use integer encoding
            solution.setVariable(taskIdx, EncodingUtils.newInt(0, vms.size() - 1));
            EncodingUtils.setInt(solution.getVariable(taskIdx), assignedVM);
        }

        return solution;
    }

    @Override
    public void close() {
        // No resources to clean up
    }

    /**
     * Converts a MOEA Framework Solution to our SchedulingSolution.
     *
     * @param solution MOEA Framework solution
     * @return Equivalent SchedulingSolution
     */
    public SchedulingSolution convertToSchedulingSolution(Solution solution) {
        SchedulingSolution schedulingSolution = new SchedulingSolution(
            tasks.size(), vms.size(), objectives.size());

        int[] assignment = new int[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            assignment[i] = EncodingUtils.getInt(solution.getVariable(i));
        }
        schedulingSolution.setTaskAssignment(assignment);
        schedulingSolution.rebuildTaskOrdering();

        // Copy objective values if evaluated
        for (int i = 0; i < objectives.size(); i++) {
            schedulingSolution.setObjectiveValue(i, solution.getObjective(i));
        }
        schedulingSolution.setEvaluated(true);

        return schedulingSolution;
    }

    /**
     * Converts our SchedulingSolution back to a MOEA Framework Solution.
     *
     * @param schedulingSolution Our solution representation
     * @return Equivalent MOEA Framework Solution
     */
    public Solution convertToMOEASolution(SchedulingSolution schedulingSolution) {
        Solution solution = new Solution(getNumberOfVariables(), getNumberOfObjectives(), getNumberOfConstraints());

        int[] assignment = schedulingSolution.getTaskAssignment();
        for (int i = 0; i < tasks.size(); i++) {
            solution.setVariable(i, EncodingUtils.newInt(0, vms.size() - 1));
            EncodingUtils.setInt(solution.getVariable(i), assignment[i]);
        }

        // Copy objective values
        for (int i = 0; i < objectives.size(); i++) {
            solution.setObjective(i, schedulingSolution.getObjectiveValue(i));
        }

        // Evaluate constraints
        evaluateConstraints(solution, schedulingSolution);

        return solution;
    }

    /**
     * Checks if the problem is feasible (every task has at least one valid VM).
     *
     * @return true if all tasks can be assigned to at least one valid VM
     */
    public boolean isFeasible() {
        return feasible;
    }

    /**
     * Gets the list of valid VM indices for a specific task.
     *
     * @param taskIndex Index of the task
     * @return List of valid VM indices
     */
    public List<Integer> getValidVMsForTask(int taskIndex) {
        return new ArrayList<>(validVMsForTask.get(taskIndex));
    }

    /**
     * Gets the tasks being scheduled.
     *
     * @return List of tasks
     */
    public List<Task> getTasks() {
        return new ArrayList<>(tasks);
    }

    /**
     * Gets the available VMs.
     *
     * @return List of VMs
     */
    public List<VM> getVMs() {
        return new ArrayList<>(vms);
    }

    /**
     * Gets the objectives being optimized.
     *
     * @return List of objectives
     */
    public List<SchedulingObjective> getObjectives() {
        return new ArrayList<>(objectives);
    }

    /**
     * Creates a random feasible solution using the simulator's RNG.
     * This ensures reproducibility when the same seed is used.
     *
     * @return A new random feasible solution
     */
    public Solution createRandomFeasibleSolution() {
        return newSolution();
    }

    @Override
    public String toString() {
        return String.format("TaskSchedulingProblem{tasks=%d, vms=%d, objectives=%d, feasible=%s}",
            tasks.size(), vms.size(), objectives.size(), feasible);
    }
}
