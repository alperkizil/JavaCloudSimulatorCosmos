package com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling;

/**
 * Adaptive cooling schedule for Simulated Annealing.
 *
 * In an adaptive cooling schedule, the cooling rate is dynamic and depends
 * on information obtained during the search, specifically the acceptance rate.
 *
 * Algorithm:
 * - If acceptance rate > target + tolerance: cool faster (exploring too much)
 * - If acceptance rate < target - tolerance: cool slower (converging too fast)
 * - Otherwise: use normal cooling rate
 *
 * Target acceptance rate guidelines:
 * - 0.4 to 0.6: Good balance between exploration and exploitation
 * - Higher target: More exploration, slower convergence
 * - Lower target: More exploitation, faster convergence
 *
 * Properties:
 * - Self-tuning based on search dynamics
 * - Maintains good balance between exploration and exploitation
 * - At high temperatures with high acceptance: cools faster
 * - At low temperatures with low acceptance: cools slower
 * - Adapts to problem landscape automatically
 *
 * Reference: El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 */
public class AdaptiveCoolingSchedule implements CoolingSchedule {

    private final double targetAcceptanceRate;
    private final double tolerance;
    private final double fastCoolingRate;
    private final double normalCoolingRate;
    private final double slowCoolingRate;

    /**
     * Default values for adaptive cooling.
     */
    public static final double DEFAULT_TARGET_ACCEPTANCE_RATE = 0.5;
    public static final double DEFAULT_TOLERANCE = 0.1;
    public static final double DEFAULT_FAST_COOLING_RATE = 0.85;
    public static final double DEFAULT_NORMAL_COOLING_RATE = 0.95;
    public static final double DEFAULT_SLOW_COOLING_RATE = 0.99;

    /**
     * Creates an adaptive cooling schedule with default parameters.
     */
    public AdaptiveCoolingSchedule() {
        this(DEFAULT_TARGET_ACCEPTANCE_RATE, DEFAULT_TOLERANCE,
             DEFAULT_FAST_COOLING_RATE, DEFAULT_NORMAL_COOLING_RATE, DEFAULT_SLOW_COOLING_RATE);
    }

    /**
     * Creates an adaptive cooling schedule with custom target acceptance rate.
     *
     * @param targetAcceptanceRate Target acceptance rate (0.0 to 1.0, typically 0.4 to 0.6)
     */
    public AdaptiveCoolingSchedule(double targetAcceptanceRate) {
        this(targetAcceptanceRate, DEFAULT_TOLERANCE,
             DEFAULT_FAST_COOLING_RATE, DEFAULT_NORMAL_COOLING_RATE, DEFAULT_SLOW_COOLING_RATE);
    }

    /**
     * Creates a fully customized adaptive cooling schedule.
     *
     * @param targetAcceptanceRate Target acceptance rate (0.0 to 1.0)
     * @param tolerance Tolerance around target rate
     * @param fastCoolingRate Rate when acceptance too high (e.g., 0.85)
     * @param normalCoolingRate Normal cooling rate (e.g., 0.95)
     * @param slowCoolingRate Rate when acceptance too low (e.g., 0.99)
     */
    public AdaptiveCoolingSchedule(double targetAcceptanceRate, double tolerance,
            double fastCoolingRate, double normalCoolingRate, double slowCoolingRate) {
        if (targetAcceptanceRate <= 0.0 || targetAcceptanceRate >= 1.0) {
            throw new IllegalArgumentException(
                "Target acceptance rate must be between 0.0 and 1.0 (exclusive)");
        }
        if (tolerance < 0.0 || tolerance >= 0.5) {
            throw new IllegalArgumentException("Tolerance must be between 0.0 and 0.5");
        }
        if (fastCoolingRate <= 0.0 || fastCoolingRate >= 1.0) {
            throw new IllegalArgumentException("Fast cooling rate must be between 0.0 and 1.0");
        }
        if (normalCoolingRate <= 0.0 || normalCoolingRate >= 1.0) {
            throw new IllegalArgumentException("Normal cooling rate must be between 0.0 and 1.0");
        }
        if (slowCoolingRate <= 0.0 || slowCoolingRate >= 1.0) {
            throw new IllegalArgumentException("Slow cooling rate must be between 0.0 and 1.0");
        }
        if (fastCoolingRate >= normalCoolingRate || normalCoolingRate >= slowCoolingRate) {
            throw new IllegalArgumentException(
                "Cooling rates must be ordered: fast < normal < slow");
        }

        this.targetAcceptanceRate = targetAcceptanceRate;
        this.tolerance = tolerance;
        this.fastCoolingRate = fastCoolingRate;
        this.normalCoolingRate = normalCoolingRate;
        this.slowCoolingRate = slowCoolingRate;
    }

    @Override
    public boolean isAdaptive() {
        return true;
    }

    @Override
    public double updateTemperature(double currentTemperature, int iteration) {
        // Without acceptance rate info, use normal cooling
        return currentTemperature * normalCoolingRate;
    }

    @Override
    public double updateTemperature(double currentTemperature, int iteration, double acceptanceRate) {
        double coolingRate;

        if (acceptanceRate > targetAcceptanceRate + tolerance) {
            // Acceptance too high - exploring too much, cool faster
            coolingRate = fastCoolingRate;
        } else if (acceptanceRate < targetAcceptanceRate - tolerance) {
            // Acceptance too low - converging too fast, cool slower
            coolingRate = slowCoolingRate;
        } else {
            // Acceptance in target range - normal cooling
            coolingRate = normalCoolingRate;
        }

        return currentTemperature * coolingRate;
    }

    @Override
    public String getName() {
        return "Adaptive";
    }

    @Override
    public String getDescription() {
        return String.format(
            "Adaptive cooling: target acceptance=%.0f%% ± %.0f%%, " +
            "rates: fast=%.2f, normal=%.2f, slow=%.2f",
            targetAcceptanceRate * 100, tolerance * 100,
            fastCoolingRate, normalCoolingRate, slowCoolingRate);
    }

    public double getTargetAcceptanceRate() {
        return targetAcceptanceRate;
    }

    public double getTolerance() {
        return tolerance;
    }

    public double getFastCoolingRate() {
        return fastCoolingRate;
    }

    public double getNormalCoolingRate() {
        return normalCoolingRate;
    }

    public double getSlowCoolingRate() {
        return slowCoolingRate;
    }

    @Override
    public String toString() {
        return String.format(
            "AdaptiveCoolingSchedule{target=%.2f, tolerance=%.2f, fast=%.2f, normal=%.2f, slow=%.2f}",
            targetAcceptanceRate, tolerance, fastCoolingRate, normalCoolingRate, slowCoolingRate);
    }
}
