package com.cloudsimulator.PlacementStrategy.task;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.List;
import java.util.Map;

/**
 * Interface for multi-objective task scheduling strategies that produce Pareto fronts.
 *
 * Multi-objective strategies (like NSGA-II, SPEA-II) optimize multiple competing objectives
 * simultaneously and produce a set of non-dominated solutions (Pareto front) rather than
 * a single optimal solution.
 *
 * This interface extends TaskAssignmentStrategy to allow:
 * 1. Running optimization to produce a Pareto front
 * 2. Applying any specific solution from the front
 * 3. Simulating all solutions in the Pareto front
 *
 * Usage:
 * <pre>
 * if (strategy instanceof MultiObjectiveTaskSchedulingStrategy) {
 *     MultiObjectiveTaskSchedulingStrategy moStrategy =
 *         (MultiObjectiveTaskSchedulingStrategy) strategy;
 *
 *     // Run optimization
 *     ParetoFront front = moStrategy.optimizeAndGetParetoFront(tasks, vms);
 *
 *     // For each solution in the front:
 *     for (SchedulingSolution solution : front.getSolutions()) {
 *         context.resetForRescheduling();
 *         moStrategy.applySolution(solution, tasks, vms, currentTime);
 *         // Run simulation steps...
 *     }
 * }
 * </pre>
 */
public interface MultiObjectiveTaskSchedulingStrategy extends TaskAssignmentStrategy {

    /**
     * Runs multi-objective optimization and returns the complete Pareto front.
     *
     * @param tasks Tasks to schedule
     * @param vms   VMs to assign tasks to
     * @return Pareto front containing all non-dominated solutions
     */
    ParetoFront optimizeAndGetParetoFront(List<Task> tasks, List<VM> vms);

    /**
     * Gets the Pareto front from the last optimization run.
     *
     * @return Last Pareto front, or null if optimization hasn't been run
     */
    ParetoFront getLastParetoFront();

    /**
     * Applies a specific solution from the Pareto front to the tasks and VMs.
     * This assigns tasks to VMs according to the solution's assignment and ordering.
     *
     * @param solution    The scheduling solution to apply
     * @param tasks       Tasks to assign (should match those used in optimization)
     * @param vms         VMs to assign to (should match those used in optimization)
     * @param currentTime Current simulation time for assignment timestamps
     * @return Map of Task to assigned VM
     */
    Map<Task, VM> applySolution(SchedulingSolution solution, List<Task> tasks,
                                 List<VM> vms, long currentTime);

    /**
     * Gets the names of the objectives being optimized.
     *
     * @return List of objective names (e.g., ["Makespan", "Energy"])
     */
    List<String> getObjectiveNames();

    /**
     * Gets whether each objective is a minimization objective.
     *
     * @return Array of booleans, true if corresponding objective should be minimized
     */
    boolean[] getObjectiveMinimization();

    /**
     * Indicates that this is a multi-objective strategy.
     * Always returns true for implementations of this interface.
     *
     * @return true
     */
    default boolean isMultiObjective() {
        return true;
    }

    /**
     * Multi-objective strategies always use batch optimization.
     *
     * @return true
     */
    @Override
    default boolean isBatchOptimizing() {
        return true;
    }
}
