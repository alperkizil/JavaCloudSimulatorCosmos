package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

/**
 * Per-run reference scales for weighted-sum fitness.
 *
 * Raw objective values live in very different units (makespan / waiting time
 * in seconds, energy in kWh), so applying the configured weights directly to
 * raw values distorts the intended weight ratio: a 0.001 "tiebreaker" weight
 * on a seconds-scale objective can contribute as much to the scalar fitness
 * as a 1.0 primary weight on a kWh-scale objective.
 *
 * This class captures a reference scale per objective from the first
 * evaluated solution of a run and divides every subsequent raw value by it,
 * so each objective contributes O(1) to the weighted sum and the configured
 * weights alone control relative influence. The tiebreaker semantics —
 * the secondary objective only separates solutions whose primary values are
 * (near-)equal — then hold independent of objective units.
 *
 * The reference solution (the heuristic seed when present, the first random
 * solution otherwise) is deterministic for a fixed RandomGenerator seed, so
 * normalization does not affect reproducibility. Only the scalar fitness is
 * normalized: raw objective values are still stored on each solution, and
 * the non-dominated archives operate on those raw vectors unchanged.
 */
final class ObjectiveScaleNormalizer {

    private double[] scales;

    /** True once reference scales have been captured. */
    boolean isInitialized() {
        return scales != null;
    }

    /**
     * Captures the reference scales from the first evaluated solution's raw
     * objective values. A (near-)zero raw value falls back to a scale of 1.0
     * (no scaling) to avoid division by zero.
     */
    void initializeFrom(double[] rawObjectiveValues) {
        scales = new double[rawObjectiveValues.length];
        for (int i = 0; i < rawObjectiveValues.length; i++) {
            double magnitude = Math.abs(rawObjectiveValues[i]);
            scales[i] = magnitude > 1e-12 ? magnitude : 1.0;
        }
    }

    /** Divides a raw objective value by its captured reference scale. */
    double normalize(int objectiveIndex, double rawValue) {
        return rawValue / scales[objectiveIndex];
    }
}
