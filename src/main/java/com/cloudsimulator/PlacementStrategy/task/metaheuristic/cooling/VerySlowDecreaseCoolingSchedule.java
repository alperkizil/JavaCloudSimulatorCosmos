package com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling;

/**
 * Very slow decrease cooling schedule for Simulated Annealing.
 *
 * Formula: T_{i+1} = T_i / (1 + beta * T_i)
 *
 * Also known as: Lundy-Mees schedule
 *
 * Where:
 * - T_i is the temperature at iteration i
 * - beta is a small positive constant (typically 0.0001 to 0.01)
 *
 * Properties:
 * - Very gradual temperature decrease
 * - Rate of decrease depends on current temperature
 * - At high temperatures: decreases slowly (more exploration)
 * - At low temperatures: decreases even more slowly (thorough local search)
 * - Never reaches zero
 * - Smaller beta = slower cooling
 *
 * Recommended beta values:
 * - 0.0001: Very slow cooling (most thorough)
 * - 0.001: Slow cooling (balanced)
 * - 0.01: Moderate cooling
 *
 * Reference: El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 *           Lundy & Mees, 1986
 */
public class VerySlowDecreaseCoolingSchedule implements CoolingSchedule {

    private final double beta;

    /**
     * Default beta value.
     */
    public static final double DEFAULT_BETA = 0.001;

    /**
     * Creates a very slow decrease cooling schedule with default beta (0.001).
     */
    public VerySlowDecreaseCoolingSchedule() {
        this(DEFAULT_BETA);
    }

    /**
     * Creates a very slow decrease cooling schedule.
     *
     * @param beta Rate parameter (small positive value, typically 0.0001 to 0.01)
     */
    public VerySlowDecreaseCoolingSchedule(double beta) {
        if (beta <= 0) {
            throw new IllegalArgumentException("Beta must be positive");
        }
        this.beta = beta;
    }

    /**
     * Creates a schedule configured to reach approximately the target temperature
     * after a specified number of iterations from an initial temperature.
     *
     * Derivation: T_n ≈ T_0 / (1 + n * beta * T_0) for large n
     * Solving: beta ≈ (T_0 - T_n) / (n * T_0 * T_n)
     *
     * @param initialTemperature Starting temperature
     * @param finalTemperature Target ending temperature
     * @param iterations Number of iterations
     * @return Configured schedule
     */
    public static VerySlowDecreaseCoolingSchedule withTargetIterations(
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

        // beta ≈ (T_0 - T_n) / (n * T_0 * T_n)
        double beta = (initialTemperature - finalTemperature) /
            (iterations * initialTemperature * finalTemperature);
        return new VerySlowDecreaseCoolingSchedule(beta);
    }

    @Override
    public double updateTemperature(double currentTemperature, int iteration) {
        // T_{i+1} = T_i / (1 + beta * T_i)
        return currentTemperature / (1.0 + beta * currentTemperature);
    }

    @Override
    public String getName() {
        return "VerySlowDecrease";
    }

    @Override
    public String getDescription() {
        return String.format("Very slow decrease (Lundy-Mees): T = T / (1 + %.6f * T)", beta);
    }

    public double getBeta() {
        return beta;
    }

    @Override
    public String toString() {
        return String.format("VerySlowDecreaseCoolingSchedule{beta=%.6f}", beta);
    }
}
