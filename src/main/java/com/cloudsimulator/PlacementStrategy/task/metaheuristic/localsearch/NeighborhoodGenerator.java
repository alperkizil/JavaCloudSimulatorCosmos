package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.Iterator;
import java.util.List;

/**
 * Interface for generating neighborhoods in local search algorithms.
 *
 * A neighborhood N(s) of a solution s is the set of all solutions that can be
 * reached from s by applying a single move (e.g., reassigning a task to a different VM,
 * swapping task order, etc.).
 *
 * This interface provides both eager (generate all) and lazy (iterate one by one)
 * methods to support different selection strategies:
 * - Best Improvement needs all neighbors to find the best
 * - First Improvement can stop early when finding an improving neighbor
 *
 * Reference: Talbi, E-G. "Metaheuristics: From Design to Implementation"
 */
public interface NeighborhoodGenerator {

    /**
     * Generates all neighbors of the given solution.
     * Use this for strategies that need to evaluate the entire neighborhood (e.g., Best Improvement).
     *
     * @param solution The current solution
     * @return List of all neighboring solutions
     */
    List<SchedulingSolution> generateAll(SchedulingSolution solution);

    /**
     * Returns an iterator that generates neighbors lazily, one at a time.
     * Use this for strategies that may stop early (e.g., First Improvement).
     *
     * @param solution The current solution
     * @return Iterator over neighboring solutions
     */
    Iterator<SchedulingSolution> generateIterator(SchedulingSolution solution);

    /**
     * Generates a single random neighbor of the solution.
     * Use this for strategies that sample the neighborhood randomly.
     *
     * @param solution The current solution
     * @return A random neighboring solution
     */
    SchedulingSolution generateRandom(SchedulingSolution solution);

    /**
     * Returns the theoretical size of the neighborhood for a given solution.
     * This is useful for statistics and progress tracking.
     *
     * @param solution The current solution
     * @return The number of possible neighbors
     */
    int getNeighborhoodSize(SchedulingSolution solution);

    /**
     * Returns the name of this neighborhood generator.
     *
     * @return Generator name for logging
     */
    String getName();

    /**
     * Neighborhood types based on move operators.
     */
    enum NeighborhoodType {
        /**
         * Reassign a single task to a different valid VM.
         * Size: O(numTasks * avgValidVMs)
         */
        REASSIGN,

        /**
         * Swap the order of two tasks on the same VM.
         * Size: O(numVMs * avgTasksPerVM^2)
         */
        SWAP_ORDER,

        /**
         * Combined: union of REASSIGN and SWAP_ORDER neighborhoods.
         * Provides a larger search space.
         */
        COMBINED
    }
}
