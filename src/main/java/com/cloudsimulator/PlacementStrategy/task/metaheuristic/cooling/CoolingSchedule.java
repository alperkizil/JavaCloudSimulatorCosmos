package com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling;

/**
 * Interface for Simulated Annealing cooling schedules.
 *
 * A cooling schedule defines how the temperature decreases over time.
 * Different schedules provide different trade-offs between exploration
 * (high temperature) and exploitation (low temperature).
 *
 * Common cooling schedules include:
 * - Linear: T_i = T_0 - i * beta
 * - Geometric: T = alpha * T (most common)
 * - Logarithmic: T_i = T_0 / log(i + 1)
 * - Very slow decrease: T_{i+1} = T_i / (1 + beta * T_i)
 * - Adaptive: Dynamic rate based on acceptance ratio
 *
 * Reference: El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 */
public interface CoolingSchedule {

    /**
     * Updates the temperature based on the cooling schedule.
     *
     * @param currentTemperature Current temperature
     * @param iteration Current iteration/temperature step number
     * @return New temperature after cooling
     */
    double updateTemperature(double currentTemperature, int iteration);

    /**
     * Gets the name of this cooling schedule.
     *
     * @return Schedule name
     */
    String getName();

    /**
     * Gets a description of this cooling schedule.
     *
     * @return Schedule description including formula
     */
    String getDescription();

    /**
     * Checks if this is an adaptive cooling schedule that requires
     * acceptance rate information.
     *
     * @return true if adaptive
     */
    default boolean isAdaptive() {
        return false;
    }

    /**
     * Updates temperature with acceptance rate information (for adaptive schedules).
     * Default implementation ignores acceptance rate and calls the regular update.
     *
     * @param currentTemperature Current temperature
     * @param iteration Current iteration number
     * @param acceptanceRate Current acceptance rate (0.0 to 1.0)
     * @return New temperature after cooling
     */
    default double updateTemperature(double currentTemperature, int iteration, double acceptanceRate) {
        return updateTemperature(currentTemperature, iteration);
    }
}
