package com.cloudsimulator.steps;

import com.cloudsimulator.model.SimulationSummary;

import java.io.*;

/**
 * Result from simulating a single Pareto solution.
 *
 * Contains both the estimated objectives (from NSGA-II optimization)
 * and actual objectives (from running the full simulation).
 * This allows comparison between estimated and actual performance.
 */
public class ParetoSimulationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int solutionIndex;
    private final double[] estimatedObjectives;
    private final double[] actualObjectives;
    private final SimulationSummary summary;

    /**
     * Creates a new Pareto simulation result.
     *
     * @param solutionIndex       Index of this solution in the Pareto front
     * @param estimatedObjectives Objective values estimated by NSGA-II
     * @param actualObjectives    Objective values from actual simulation
     * @param summary             Full simulation summary (may be null for lightweight results)
     */
    public ParetoSimulationResult(int solutionIndex,
                                   double[] estimatedObjectives,
                                   double[] actualObjectives,
                                   SimulationSummary summary) {
        this.solutionIndex = solutionIndex;
        this.estimatedObjectives = estimatedObjectives.clone();
        this.actualObjectives = actualObjectives.clone();
        this.summary = summary;
    }

    /**
     * Creates a result without full summary (for subprocess serialization).
     */
    public ParetoSimulationResult(int solutionIndex,
                                   double[] estimatedObjectives,
                                   double[] actualObjectives) {
        this(solutionIndex, estimatedObjectives, actualObjectives, null);
    }

    // Getters

    public int getSolutionIndex() {
        return solutionIndex;
    }

    public double[] getEstimatedObjectives() {
        return estimatedObjectives.clone();
    }

    public double[] getActualObjectives() {
        return actualObjectives.clone();
    }

    public SimulationSummary getSummary() {
        return summary;
    }

    // Convenience getters for standard 2-objective case (Makespan, Energy)

    /**
     * Gets estimated makespan (objective 0).
     */
    public double getEstimatedMakespan() {
        return estimatedObjectives.length > 0 ? estimatedObjectives[0] : 0.0;
    }

    /**
     * Gets actual makespan from simulation.
     */
    public double getActualMakespan() {
        return actualObjectives.length > 0 ? actualObjectives[0] : 0.0;
    }

    /**
     * Gets estimated energy (objective 1).
     */
    public double getEstimatedEnergy() {
        return estimatedObjectives.length > 1 ? estimatedObjectives[1] : 0.0;
    }

    /**
     * Gets actual energy from simulation.
     */
    public double getActualEnergy() {
        return actualObjectives.length > 1 ? actualObjectives[1] : 0.0;
    }

    // Error calculations

    /**
     * Gets makespan error as a ratio: (actual - estimated) / estimated.
     * Positive means actual was worse (higher) than estimated.
     */
    public double getMakespanError() {
        double estimated = getEstimatedMakespan();
        if (estimated == 0) return 0.0;
        return (getActualMakespan() - estimated) / estimated;
    }

    /**
     * Gets energy error as a ratio: (actual - estimated) / estimated.
     * Positive means actual was worse (higher) than estimated.
     */
    public double getEnergyError() {
        double estimated = getEstimatedEnergy();
        if (estimated == 0) return 0.0;
        return (getActualEnergy() - estimated) / estimated;
    }

    /**
     * Gets absolute makespan error.
     */
    public double getMakespanAbsoluteError() {
        return Math.abs(getActualMakespan() - getEstimatedMakespan());
    }

    /**
     * Gets absolute energy error.
     */
    public double getEnergyAbsoluteError() {
        return Math.abs(getActualEnergy() - getEstimatedEnergy());
    }

    // Serialization

    /**
     * Saves this result to a file.
     */
    public void saveToFile(String path) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(path)))) {
            oos.writeObject(this);
        }
    }

    /**
     * Loads a result from a file.
     */
    public static ParetoSimulationResult loadFromFile(String path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(path)))) {
            return (ParetoSimulationResult) ois.readObject();
        }
    }

    @Override
    public String toString() {
        return String.format(
            "ParetoSimulationResult{index=%d, estMakespan=%.2f, actMakespan=%.2f (%.1f%% error), " +
            "estEnergy=%.6f, actEnergy=%.6f (%.1f%% error)}",
            solutionIndex,
            getEstimatedMakespan(), getActualMakespan(), getMakespanError() * 100,
            getEstimatedEnergy(), getActualEnergy(), getEnergyError() * 100
        );
    }
}
