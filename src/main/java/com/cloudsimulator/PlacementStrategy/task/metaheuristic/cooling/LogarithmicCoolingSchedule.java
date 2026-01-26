package com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling;

/**
 * Logarithmic cooling schedule for Simulated Annealing.
 *
 * Formula: T_i = T_0 / log(i + e)
 *
 * Where:
 * - T_i is the temperature at iteration i
 * - T_0 is the initial temperature
 * - e is Euler's number (used to avoid division by zero and log(1)=0)
 *
 * Properties:
 * - Theoretically optimal for convergence to global optimum
 * - Very slow cooling (slowest of standard schedules)
 * - Guaranteed to find global optimum given infinite time
 * - Practically too slow for most applications
 * - Best suited for problems where solution quality is critical and time is not
 *
 * Reference: El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 *           S. Geman and D. Geman, "Stochastic Relaxation, Gibbs Distributions"
 */
public class LogarithmicCoolingSchedule implements CoolingSchedule {

    private final double initialTemperature;
    private final double base;

    /**
     * Creates a logarithmic cooling schedule.
     *
     * @param initialTemperature Initial temperature T_0
     */
    public LogarithmicCoolingSchedule(double initialTemperature) {
        this(initialTemperature, Math.E);
    }

    /**
     * Creates a logarithmic cooling schedule with custom base.
     *
     * @param initialTemperature Initial temperature T_0
     * @param base Base for the logarithm offset (default is e)
     */
    public LogarithmicCoolingSchedule(double initialTemperature, double base) {
        if (initialTemperature <= 0) {
            throw new IllegalArgumentException("Initial temperature must be positive");
        }
        if (base <= 1) {
            throw new IllegalArgumentException("Base must be greater than 1");
        }
        this.initialTemperature = initialTemperature;
        this.base = base;
    }

    @Override
    public double updateTemperature(double currentTemperature, int iteration) {
        // T_i = T_0 / log(i + base)
        // Using natural log; adding base ensures log argument > 1
        double denominator = Math.log(iteration + base);
        if (denominator <= 0) {
            denominator = 0.0001; // Safety guard
        }
        return initialTemperature / denominator;
    }

    @Override
    public String getName() {
        return "Logarithmic";
    }

    @Override
    public String getDescription() {
        return String.format("Logarithmic cooling: T_i = %.2f / log(i + %.2f)",
            initialTemperature, base);
    }

    public double getInitialTemperature() {
        return initialTemperature;
    }

    public double getBase() {
        return base;
    }

    @Override
    public String toString() {
        return String.format("LogarithmicCoolingSchedule{T0=%.2f, base=%.2f}",
            initialTemperature, base);
    }
}
