package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.core.Solution;
import org.moeaframework.core.operator.Mutation;
import org.moeaframework.core.variable.RealVariable;

/**
 * Adapts the project's domain-specific MutationOperator to MOEA Framework's Mutation interface.
 *
 * This allows AMOSA to use constraint-aware mutations (reassign to valid VMs,
 * swap task ordering) instead of generic Polynomial Mutation, which is designed
 * for continuous optimization and unaware of task-VM compatibility constraints.
 */
public class TaskSchedulingMutation implements Mutation {

    private final MutationOperator mutationOperator;
    private final RepairOperator repairOperator;
    private final double mutationRate;
    private final int numTasks;
    private final int numVMs;

    public TaskSchedulingMutation(MutationOperator mutationOperator,
                                   RepairOperator repairOperator,
                                   double mutationRate,
                                   int numTasks,
                                   int numVMs) {
        this.mutationOperator = mutationOperator;
        this.repairOperator = repairOperator;
        this.mutationRate = mutationRate;
        this.numTasks = numTasks;
        this.numVMs = numVMs;
    }

    @Override
    public String getName() {
        return "TaskSchedulingMutation";
    }

    @Override
    public Solution mutate(Solution parent) {
        Solution child = parent.copy();

        // Decode MOEA Solution to SchedulingSolution
        SchedulingSolution schedulingSolution = decode(child);

        // Apply domain-specific mutation (reassign to valid VMs, swap ordering)
        mutationOperator.mutate(schedulingSolution, mutationRate);

        // Repair any constraint violations
        repairOperator.repair(schedulingSolution);

        // Encode back to MOEA Solution
        return encode(schedulingSolution, child.getNumberOfObjectives());
    }

    private SchedulingSolution decode(Solution solution) {
        SchedulingSolution schedulingSolution = new SchedulingSolution(numTasks, numVMs, solution.getNumberOfObjectives());
        int[] assignment = new int[numTasks];

        for (int i = 0; i < numTasks; i++) {
            RealVariable var = (RealVariable) solution.getVariable(i);
            int vmIndex = (int) Math.round(var.getValue());
            vmIndex = Math.max(0, Math.min(numVMs - 1, vmIndex));
            assignment[i] = vmIndex;
        }

        schedulingSolution.setTaskAssignment(assignment);
        schedulingSolution.rebuildTaskOrdering();
        return schedulingSolution;
    }

    private Solution encode(SchedulingSolution schedulingSolution, int numObjectives) {
        Solution solution = new Solution(numTasks, numObjectives);
        int[] assignment = schedulingSolution.getTaskAssignment();

        for (int i = 0; i < numTasks; i++) {
            RealVariable var = new RealVariable(0, numVMs - 1);
            var.setValue(assignment[i]);
            solution.setVariable(i, var);
        }

        return solution;
    }
}
