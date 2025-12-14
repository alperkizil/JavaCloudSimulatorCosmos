package com.cloudsimulator.PlacementStrategy.task;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;

import java.util.List;

/**
 * Interface for multi-objective task scheduling strategies.
 *
 * Strategies implementing this interface produce a Pareto front of non-dominated
 * solutions that can be individually simulated to validate estimated vs actual metrics.
 *
 * This interface is used by ParetoFrontSimulationStep to detect multi-objective
 * algorithms at runtime and access their Pareto fronts.
 *
 * @see ParetoFront
 * @see SchedulingObjective
 */
public interface MultiObjectiveSchedulingStrategy extends TaskAssignmentStrategy {

    /**
     * Gets the Pareto front from the last optimization run.
     *
     * The Pareto front contains all non-dominated solutions found during optimization.
     * Each solution represents a different trade-off between objectives.
     *
     * @return The Pareto front, or null if no optimization has been run
     */
    ParetoFront getParetoFront();

    /**
     * Gets the list of objectives used by this strategy.
     *
     * @return List of scheduling objectives (e.g., MakespanObjective, EnergyObjective)
     */
    List<SchedulingObjective> getObjectives();

    /**
     * Gets the random seed used for optimization reproducibility.
     *
     * @return The random seed
     */
    long getRandomSeed();
}
