package com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling;

/**
 * Geometric cooling schedule for Simulated Annealing.
 *
 * Formula: T_{i+1} = alpha * T_i
 *
 * Where:
 * - T_i is the temperature at iteration i
 * - alpha is the cooling rate (typically 0.8 to 0.99)
 *
 * Properties:
 * - Most commonly used cooling schedule
 * - Temperature decreases exponentially
 * - Never reaches zero (asymptotic approach)
 * - alpha close to 1.0 = slower cooling = more exploration
 * - alpha close to 0.8 = faster cooling = quicker convergence
 *
 * Recommended alpha values:
 * - 0.95-0.99: Slow cooling, thorough search
 * - 0.90-0.95: Balanced cooling (default)
 * - 0.80-0.90: Fast cooling, quick convergence
 *
 * Reference: El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 */
public class GeometricCoolingSchedule implements CoolingSchedule {

    private final double alpha;

    /**
     * Default cooling rate (0.95 - balanced).
     */
    public static final double DEFAULT_ALPHA = 0.95;

    /**
     * Creates a geometric cooling schedule with default alpha (0.95).
     */
    public GeometricCoolingSchedule() {
        this(DEFAULT_ALPHA);
    }

    /**
     * Creates a geometric cooling schedule.
     *
     * @param alpha Cooling rate (0.0 to 1.0, typically 0.8 to 0.99)
     */
    public GeometricCoolingSchedule(double alpha) {
        if (alpha <= 0.0 || alpha >= 1.0) {
            throw new IllegalArgumentException("Alpha must be between 0.0 and 1.0 (exclusive)");
        }
        this.alpha = alpha;
    }

    /**
     * Creates a geometric cooling schedule that reaches approximately
     * the final temperature after a specified number of iterations.
     *
     * Formula: alpha = (T_final / T_initial)^(1/iterations)
     *
     * @param initialTemperature Initial temperature
     * @param finalTemperature Target final temperature
     * @param iterations Number of temperature steps
     * @return Configured GeometricCoolingSchedule
     */
    public static GeometricCoolingSchedule withTargetIterations(
            double initialTemperature, double finalTemperature, int iterations) {
        if (initialTemperature <= 0 || finalTemperature <= 0) {
            throw new IllegalArgumentException("Temperatures must be positive");
        }
        if (finalTemperature >= initialTemperature) {
            throw new IllegalArgumentException("Final temperature must be less than initial");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("Iterations must be positive");
        }

        double alpha = Math.pow(finalTemperature / initialTemperature, 1.0 / iterations);
        return new GeometricCoolingSchedule(alpha);
    }

    @Override
    public double updateTemperature(double currentTemperature, int iteration) {
        return currentTemperature * alpha;
    }

    @Override
    public String getName() {
        return "Geometric";
    }

    @Override
    public String getDescription() {
        return String.format("Geometric cooling: T = %.4f * T (exponential decay)", alpha);
    }

    public double getAlpha() {
        return alpha;
    }

    @Override
    public String toString() {
        return String.format("GeometricCoolingSchedule{alpha=%.4f}", alpha);
    }
}
