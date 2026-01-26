package com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling;

/**
 * Linear cooling schedule for Simulated Annealing.
 *
 * Formula: T_i = T_0 - i * beta
 *
 * Where:
 * - T_i is the temperature at iteration i
 * - T_0 is the initial temperature
 * - beta is the cooling rate (temperature decrease per iteration)
 *
 * Properties:
 * - Simple and predictable cooling behavior
 * - Temperature decreases by a fixed amount each step
 * - Can reach zero or negative (clamped to minimum)
 * - Good for problems where uniform exploration across temperature range is desired
 *
 * Reference: El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 */
public class LinearCoolingSchedule implements CoolingSchedule {

    private final double initialTemperature;
    private final double beta;
    private final double minimumTemperature;

    /**
     * Creates a linear cooling schedule.
     *
     * @param initialTemperature Initial temperature T_0
     * @param beta Cooling rate (temperature decrease per iteration)
     */
    public LinearCoolingSchedule(double initialTemperature, double beta) {
        this(initialTemperature, beta, 0.0001);
    }

    /**
     * Creates a linear cooling schedule with minimum temperature.
     *
     * @param initialTemperature Initial temperature T_0
     * @param beta Cooling rate (temperature decrease per iteration)
     * @param minimumTemperature Minimum temperature (to prevent negative values)
     */
    public LinearCoolingSchedule(double initialTemperature, double beta, double minimumTemperature) {
        if (initialTemperature <= 0) {
            throw new IllegalArgumentException("Initial temperature must be positive");
        }
        if (beta <= 0) {
            throw new IllegalArgumentException("Beta must be positive");
        }
        if (minimumTemperature < 0) {
            throw new IllegalArgumentException("Minimum temperature must be non-negative");
        }
        this.initialTemperature = initialTemperature;
        this.beta = beta;
        this.minimumTemperature = minimumTemperature;
    }

    /**
     * Creates a linear cooling schedule that reaches the final temperature
     * after a specified number of iterations.
     *
     * @param initialTemperature Initial temperature
     * @param finalTemperature Target final temperature
     * @param totalIterations Number of iterations to reach final temperature
     * @return Configured LinearCoolingSchedule
     */
    public static LinearCoolingSchedule withTargetIterations(
            double initialTemperature, double finalTemperature, int totalIterations) {
        if (totalIterations <= 0) {
            throw new IllegalArgumentException("Total iterations must be positive");
        }
        double beta = (initialTemperature - finalTemperature) / totalIterations;
        return new LinearCoolingSchedule(initialTemperature, beta, finalTemperature);
    }

    @Override
    public double updateTemperature(double currentTemperature, int iteration) {
        double newTemp = initialTemperature - (iteration * beta);
        return Math.max(newTemp, minimumTemperature);
    }

    @Override
    public String getName() {
        return "Linear";
    }

    @Override
    public String getDescription() {
        return String.format("Linear cooling: T_i = %.2f - i * %.6f (min: %.6f)",
            initialTemperature, beta, minimumTemperature);
    }

    public double getInitialTemperature() {
        return initialTemperature;
    }

    public double getBeta() {
        return beta;
    }

    public double getMinimumTemperature() {
        return minimumTemperature;
    }

    @Override
    public String toString() {
        return String.format("LinearCoolingSchedule{T0=%.2f, beta=%.6f, Tmin=%.6f}",
            initialTemperature, beta, minimumTemperature);
    }
}
