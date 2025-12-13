package com.cloudsimulator.PlacementStrategy.task.metaheuristic.selection;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.List;

/**
 * Interface for selection operators in genetic algorithms.
 *
 * Selection operators choose individuals from the population to become parents
 * for the next generation. Different selection strategies provide different
 * selection pressure, balancing exploitation and exploration.
 *
 * Implementations:
 * - TournamentSelection: Binary or k-tournament selection
 * - (Future) RouletteWheelSelection: Fitness-proportionate selection
 * - (Future) RankSelection: Rank-based selection
 */
public interface SelectionOperator {

    /**
     * Selects a single individual from the population.
     *
     * @param population     The current population
     * @param fitnessValues  Fitness values for each individual (parallel to population)
     * @param isMinimization true if lower fitness values are better
     * @return Selected individual
     */
    SchedulingSolution select(List<SchedulingSolution> population,
                               double[] fitnessValues,
                               boolean isMinimization);

    /**
     * Selects multiple individuals from the population.
     *
     * @param population     The current population
     * @param fitnessValues  Fitness values for each individual
     * @param count          Number of individuals to select
     * @param isMinimization true if lower fitness values are better
     * @return List of selected individuals
     */
    default List<SchedulingSolution> selectMultiple(List<SchedulingSolution> population,
                                                     double[] fitnessValues,
                                                     int count,
                                                     boolean isMinimization) {
        List<SchedulingSolution> selected = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            selected.add(select(population, fitnessValues, isMinimization));
        }
        return selected;
    }

    /**
     * Gets the name of this selection operator.
     *
     * @return Operator name
     */
    String getName();

    /**
     * Gets a description of how this selection operator works.
     *
     * @return Operator description
     */
    String getDescription();
}
