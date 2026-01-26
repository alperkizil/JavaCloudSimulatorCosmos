package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Strategy interface for selecting a neighbor from a neighborhood in local search.
 *
 * Different strategies include:
 * - Best Improvement (Steepest Descent): Select the best neighbor among all improving neighbors
 * - First Improvement: Select the first improving neighbor found
 * - Random Selection: Randomly select among improving neighbors
 *
 * This interface enables the Strategy pattern, allowing different neighbor selection
 * strategies to be swapped without changing the core local search algorithm.
 *
 * Reference: Talbi, E-G. "Metaheuristics: From Design to Implementation"
 */
public interface NeighborSelectionStrategy {

    /**
     * Selects a neighbor from the neighborhood based on the strategy.
     *
     * @param current       The current solution
     * @param neighborhood  Generator to produce neighbors
     * @param evaluator     Function to evaluate solution fitness (lower is better for minimization)
     * @param isMinimization True if we're minimizing the objective
     * @return An improving neighbor if found, empty otherwise
     */
    Optional<SchedulingSolution> selectNeighbor(
            SchedulingSolution current,
            NeighborhoodGenerator neighborhood,
            ToDoubleFunction<SchedulingSolution> evaluator,
            boolean isMinimization);

    /**
     * Returns the name of this selection strategy.
     *
     * @return Strategy name for logging and identification
     */
    String getName();

    /**
     * Returns the number of neighbors evaluated during the last selection.
     * Useful for statistics and performance analysis.
     *
     * @return Number of neighbors evaluated in last selectNeighbor call
     */
    int getLastEvaluationCount();

    /**
     * Checks if a neighbor is improving compared to the current solution.
     *
     * @param neighborFitness Fitness of the neighbor
     * @param currentFitness  Fitness of the current solution
     * @param isMinimization  True if minimizing
     * @return True if neighbor improves upon current
     */
    default boolean isImproving(double neighborFitness, double currentFitness, boolean isMinimization) {
        if (isMinimization) {
            return neighborFitness < currentFitness;
        } else {
            return neighborFitness > currentFitness;
        }
    }

    /**
     * Compares two fitness values and returns true if first is better than second.
     *
     * @param fitness1       First fitness value
     * @param fitness2       Second fitness value
     * @param isMinimization True if minimizing
     * @return True if fitness1 is better than fitness2
     */
    default boolean isBetter(double fitness1, double fitness2, boolean isMinimization) {
        if (isMinimization) {
            return fitness1 < fitness2;
        } else {
            return fitness1 > fitness2;
        }
    }
}
