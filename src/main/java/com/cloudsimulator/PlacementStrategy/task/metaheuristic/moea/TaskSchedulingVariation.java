package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.CrossoverOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.core.Solution;
import org.moeaframework.core.Variation;

/**
 * Adapts our crossover, mutation, and repair operators to MOEA Framework's Variation interface.
 *
 * This allows using our existing genetic operators with MOEA Framework's algorithms
 * while maintaining the repair step to ensure constraint satisfaction. Using the
 * SAME domain operators as the native GA/SA arms (instead of MOEA's generic
 * real-coded SBX+PM on rounded VM indices) removes the operator-family confound
 * from the algorithm comparison: differences between arms then reflect the
 * metaheuristics' selection/archiving logic, not their operators.
 *
 * The variation process:
 * 1. Decode parent solutions to SchedulingSolutions (assignment + dispatch order)
 * 2. Apply crossover to produce offspring
 * 3. Apply mutation to offspring (REASSIGN and/or SWAP_ORDER, per operator type)
 * 4. Apply repair to ensure valid assignments
 * 5. Encode back to MOEA Solutions (order genes carried via the permutation)
 */
public class TaskSchedulingVariation implements Variation {

    private final CrossoverOperator crossoverOperator;
    private final MutationOperator mutationOperator;
    private final RepairOperator repairOperator;
    private final double crossoverRate;
    private final double mutationRate;
    private final int numTasks;
    private final int numVMs;
    private final int numObjectives;

    /**
     * Creates a variation operator that adapts our operators to MOEA Framework.
     *
     * @param crossoverOperator Our crossover operator
     * @param mutationOperator  Our mutation operator
     * @param repairOperator    Our repair operator
     * @param crossoverRate     Probability of applying crossover (0.0 to 1.0)
     * @param mutationRate      Per-gene mutation probability (0.0 to 1.0)
     * @param numTasks          Number of tasks
     * @param numVMs            Number of VMs
     * @param numObjectives     Number of objectives
     */
    public TaskSchedulingVariation(CrossoverOperator crossoverOperator,
                                    MutationOperator mutationOperator,
                                    RepairOperator repairOperator,
                                    double crossoverRate,
                                    double mutationRate,
                                    int numTasks,
                                    int numVMs,
                                    int numObjectives) {
        this.crossoverOperator = crossoverOperator;
        this.mutationOperator = mutationOperator;
        this.repairOperator = repairOperator;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.numTasks = numTasks;
        this.numVMs = numVMs;
        this.numObjectives = numObjectives;
    }

    @Override
    public int getArity() {
        return 2; // Requires 2 parents for crossover
    }

    @Override
    public String getName() {
        return "TaskSchedulingVariation";
    }

    @Override
    public Solution[] evolve(Solution[] parents) {
        if (parents.length != 2) {
            throw new IllegalArgumentException("TaskSchedulingVariation requires exactly 2 parents");
        }

        // Decode parents to our representation
        SchedulingSolution parent1 = decode(parents[0]);
        SchedulingSolution parent2 = decode(parents[1]);

        // Apply crossover
        SchedulingSolution[] offspring;
        if (org.moeaframework.core.PRNG.nextDouble() < crossoverRate) {
            offspring = crossoverOperator.crossover(parent1, parent2);
        } else {
            // No crossover - copy parents
            offspring = new SchedulingSolution[]{parent1.copy(), parent2.copy()};
        }

        // Apply mutation and repair to each offspring
        for (SchedulingSolution child : offspring) {
            mutationOperator.mutate(child, mutationRate);
            repairOperator.repair(child);
        }

        // Encode back to MOEA solutions, preserving the parents' constraint
        // count so constrained problems can still call setConstraint()
        int numConstraints = parents[0].getNumberOfConstraints();
        return new Solution[]{
            encode(offspring[0], numConstraints),
            encode(offspring[1], numConstraints)
        };
    }

    /**
     * Decodes a MOEA Solution (assignment genes + dispatch permutation) to our
     * SchedulingSolution. Shared encoding lives on TaskSchedulingProblem.
     */
    private SchedulingSolution decode(Solution solution) {
        return TaskSchedulingProblem.decodeSolution(solution, numTasks, numVMs, numObjectives);
    }

    /**
     * Encodes a SchedulingSolution (including its per-VM ordering) to a MOEA
     * Solution. Shared encoding lives on TaskSchedulingProblem.
     */
    private Solution encode(SchedulingSolution schedulingSolution, int numConstraints) {
        Solution solution = TaskSchedulingProblem.newShell(
            numTasks, numVMs, numObjectives, numConstraints);
        TaskSchedulingProblem.encodeInto(solution, schedulingSolution);
        return solution;
    }
}
