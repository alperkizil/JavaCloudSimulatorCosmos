package com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination;

/**
 * Termination condition based on temperature threshold.
 *
 * The Simulated Annealing algorithm terminates when the current
 * temperature falls below the specified minimum temperature.
 *
 * This is the classic termination condition for SA as described in:
 * El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 *
 * Usage:
 * <pre>
 * TerminationCondition term = new TemperatureTermination(0.001);
 * </pre>
 */
public class TemperatureTermination implements TerminationCondition {

    private final double minimumTemperature;
    private double currentTemperature;
    private double initialTemperature;

    /**
     * Creates a temperature-based termination condition.
     *
     * @param minimumTemperature The temperature threshold below which to terminate
     */
    public TemperatureTermination(double minimumTemperature) {
        if (minimumTemperature < 0) {
            throw new IllegalArgumentException("Minimum temperature must be non-negative");
        }
        this.minimumTemperature = minimumTemperature;
        this.currentTemperature = Double.MAX_VALUE;
        this.initialTemperature = Double.MAX_VALUE;
    }

    /**
     * Updates the current temperature.
     * This should be called by the SA algorithm after each temperature update.
     *
     * @param temperature Current temperature
     */
    public void setCurrentTemperature(double temperature) {
        this.currentTemperature = temperature;
        if (this.initialTemperature == Double.MAX_VALUE) {
            this.initialTemperature = temperature;
        }
    }

    @Override
    public boolean shouldTerminate(AlgorithmStatistics stats) {
        // Check temperature from stats if available, otherwise use internal tracking
        if (stats != null && stats.getCurrentTemperature() > 0) {
            currentTemperature = stats.getCurrentTemperature();
            if (initialTemperature == Double.MAX_VALUE) {
                initialTemperature = stats.getInitialTemperature();
            }
        }
        return currentTemperature <= minimumTemperature;
    }

    @Override
    public String getDescription() {
        return String.format("Temperature < %.6f", minimumTemperature);
    }

    @Override
    public double getProgress(AlgorithmStatistics stats) {
        if (initialTemperature == Double.MAX_VALUE || initialTemperature <= minimumTemperature) {
            return 0.0;
        }

        // Progress based on temperature decay
        // At T=T0, progress = 0; At T=Tmin, progress = 1
        // Using logarithmic scale since temperature typically decays exponentially
        double logInitial = Math.log(initialTemperature);
        double logMin = Math.log(Math.max(minimumTemperature, 1e-10));
        double logCurrent = Math.log(Math.max(currentTemperature, 1e-10));

        double progress = (logInitial - logCurrent) / (logInitial - logMin);
        return Math.max(0.0, Math.min(1.0, progress));
    }

    @Override
    public void reset() {
        this.currentTemperature = Double.MAX_VALUE;
        this.initialTemperature = Double.MAX_VALUE;
    }

    public double getMinimumTemperature() {
        return minimumTemperature;
    }

    public double getCurrentTemperature() {
        return currentTemperature;
    }

    @Override
    public String toString() {
        return String.format("TemperatureTermination{Tmin=%.6f, current=%.6f}",
            minimumTemperature, currentTemperature);
    }
}
