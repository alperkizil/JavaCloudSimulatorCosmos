package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.problem.AbstractProblem;

import java.util.List;

/**
 * Adapts the cloud task scheduling problem to MOEA Framework's Problem interface.
 *
 * Encoding:
 * - Each decision variable represents a task's VM assignment
 * - Variable i ∈ [0, numVMs-1] indicates which VM task i is assigned to
 * - Real values are rounded to integers during evaluation
 *
 * This adapter allows using MOEA Framework's algorithms (NSGA-II, NSGA-III, MOEA/D, etc.)
 * with our existing objective functions and repair operator.
 */
public class TaskSchedulingProblem extends AbstractProblem {

    private final List<Task> tasks;
    private final List<VM> vms;
    private final List<SchedulingObjective> objectives;
    private final RepairOperator repairOperator;

    /**
     * Creates a task scheduling problem for MOEA Framework.
     *
     * @param tasks          List of tasks to schedule
     * @param vms            List of available VMs
     * @param objectives     Scheduling objectives to optimize
     * @param repairOperator Repair operator for fixing invalid assignments
     */
    public TaskSchedulingProblem(List<Task> tasks, List<VM> vms,
                                  List<SchedulingObjective> objectives,
                                  RepairOperator repairOperator) {
        super(tasks.size(), objectives.size(), 0); // numVars, numObjectives, numConstraints
        this.tasks = tasks;
        this.vms = vms;
        this.objectives = objectives;
        this.repairOperator = repairOperator;
    }

    @Override
    public String getName() {
        return "CloudTaskScheduling";
    }

    @Override
    public Solution newSolution() {
        Solution solution = new Solution(numberOfVariables, numberOfObjectives);

        // Each variable represents a task's VM assignment
        // Range: [0, numVMs - 1] as real values (will be rounded during evaluation)
        for (int i = 0; i < numberOfVariables; i++) {
            // Use valid VM range for this task if available, otherwise use full range
            List<Integer> validVms = repairOperator.getValidVmsForTask(i);
            if (validVms != null && !validVms.isEmpty()) {
                // Still use full range [0, numVMs-1], repair will fix invalid assignments
                solution.setVariable(i, new RealVariable(0, vms.size() - 1));
            } else {
                solution.setVariable(i, new RealVariable(0, vms.size() - 1));
            }
        }

        return solution;
    }

    @Override
    public void evaluate(Solution solution) {
        // Decode MOEA solution to our SchedulingSolution
        SchedulingSolution schedulingSolution = decode(solution);

        // Apply repair to ensure valid assignments
        repairOperator.repair(schedulingSolution);

        // Evaluate each objective
        for (int i = 0; i < objectives.size(); i++) {
            double value = objectives.get(i).evaluate(schedulingSolution, tasks, vms);
            solution.setObjective(i, value);
        }

        // Store the repaired assignment back in the solution for later retrieval
        int[] assignment = schedulingSolution.getTaskAssignment();
        for (int i = 0; i < assignment.length; i++) {
            ((RealVariable) solution.getVariable(i)).setValue(assignment[i]);
        }
    }

    /**
     * Decodes a MOEA Framework Solution to our SchedulingSolution.
     *
     * @param solution MOEA Framework solution
     * @return Decoded SchedulingSolution
     */
    public SchedulingSolution decode(Solution solution) {
        SchedulingSolution schedulingSolution = new SchedulingSolution(
            tasks.size(), vms.size(), objectives.size()
        );

        int[] assignment = new int[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            RealVariable var = (RealVariable) solution.getVariable(i);
            // Round to nearest integer VM index
            int vmIndex = (int) Math.round(var.getValue());
            // Clamp to valid range
            vmIndex = Math.max(0, Math.min(vms.size() - 1, vmIndex));
            assignment[i] = vmIndex;
        }

        schedulingSolution.setTaskAssignment(assignment);
        schedulingSolution.rebuildTaskOrdering();

        return schedulingSolution;
    }

    /**
     * Encodes a SchedulingSolution to a MOEA Framework Solution.
     *
     * @param schedulingSolution Our scheduling solution
     * @return MOEA Framework solution
     */
    public Solution encode(SchedulingSolution schedulingSolution) {
        Solution solution = newSolution();
        int[] assignment = schedulingSolution.getTaskAssignment();

        for (int i = 0; i < assignment.length; i++) {
            ((RealVariable) solution.getVariable(i)).setValue(assignment[i]);
        }

        return solution;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public List<VM> getVms() {
        return vms;
    }

    public List<SchedulingObjective> getObjectives() {
        return objectives;
    }

    public RepairOperator getRepairOperator() {
        return repairOperator;
    }
}
