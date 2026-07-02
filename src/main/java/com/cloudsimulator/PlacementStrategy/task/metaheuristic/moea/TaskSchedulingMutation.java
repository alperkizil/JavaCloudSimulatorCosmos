package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.core.Solution;
import org.moeaframework.core.operator.Mutation;

/**
 * Adapts the project's domain-specific MutationOperator to MOEA Framework's Mutation interface.
 *
 * This allows AMOSA to use constraint-aware mutations (reassign to valid VMs,
 * swap task ordering) instead of generic Polynomial Mutation, which is designed
 * for continuous optimization and unaware of task-VM compatibility constraints.
 * With the permutation-carrying encoding, SWAP_ORDER moves survive the
 * encode/decode round-trip, so order-mutating types (COMBINED) are effective.
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
        boolean mutated = mutationOperator.mutate(schedulingSolution, mutationRate);

        // Guarantee at least one mutation. With rate=0.01 and 100 tasks,
        // P(0 mutations) = e^(-1) ≈ 37%. For SA-based search (AMOSA),
        // every neighbor must be distinct to avoid wasting evaluations.
        if (!mutated) {
            mutationOperator.mutateSingle(schedulingSolution);
        }

        // Repair any constraint violations
        repairOperator.repair(schedulingSolution);

        // Encode back to MOEA Solution, preserving parent's constraint count
        // so constrained problems (e.g. PowerCeilingSchedulingProblem) can
        // still call setConstraint() during evaluation.
        return encode(schedulingSolution, child.getNumberOfObjectives(),
            child.getNumberOfConstraints());
    }

    private SchedulingSolution decode(Solution solution) {
        return TaskSchedulingProblem.decodeSolution(
            solution, numTasks, numVMs, solution.getNumberOfObjectives());
    }

    private Solution encode(SchedulingSolution schedulingSolution, int numObjectives,
                             int numConstraints) {
        Solution solution = TaskSchedulingProblem.newShell(
            numTasks, numVMs, numObjectives, numConstraints);
        TaskSchedulingProblem.encodeInto(solution, schedulingSolution);
        return solution;
    }
}
