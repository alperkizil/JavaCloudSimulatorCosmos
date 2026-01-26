package com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination;

/**
 * Termination condition based on number of iterations without improvement.
 *
 * This condition is particularly useful for local search algorithms where
 * the search should stop when no progress is being made (local optimum reached).
 *
 * The condition is satisfied when the number of consecutive iterations (or generations)
 * without improvement exceeds a specified threshold.
 *
 * Usage:
 * <pre>
 * // Stop after 50 iterations without improvement
 * TerminationCondition condition = new NoImprovementTermination(50);
 *
 * // For GA: stop after 100 generations without improvement
 * TerminationCondition gaCondition = new NoImprovementTermination(100);
 * </pre>
 *
 * Reference: Talbi, E-G. "Metaheuristics: From Design to Implementation"
 */
public class NoImprovementTermination implements TerminationCondition {

    private final int maxIterationsWithoutImprovement;
    private int iterationsWithoutImprovement;
    private double lastBestFitness;
    private boolean initialized;

    /**
     * Creates a no-improvement termination condition.
     *
     * @param maxIterationsWithoutImprovement Maximum iterations allowed without improvement
     */
    public NoImprovementTermination(int maxIterationsWithoutImprovement) {
        if (maxIterationsWithoutImprovement < 1) {
            throw new IllegalArgumentException("Max iterations without improvement must be at least 1");
        }
        this.maxIterationsWithoutImprovement = maxIterationsWithoutImprovement;
        this.iterationsWithoutImprovement = 0;
        this.lastBestFitness = Double.NaN;
        this.initialized = false;
    }

    @Override
    public boolean shouldTerminate(AlgorithmStatistics stats) {
        double[] bestObjectives = stats.getBestObjectiveValues();
        if (bestObjectives == null || bestObjectives.length == 0) {
            return false;
        }

        // Use first objective for comparison (single-objective or primary objective)
        double currentBest = bestObjectives[0];

        if (!initialized) {
            // First call - initialize
            lastBestFitness = currentBest;
            initialized = true;
            iterationsWithoutImprovement = 0;
            return false;
        }

        // Check if there's improvement
        // Using a small epsilon for floating-point comparison
        double epsilon = 1e-10;
        boolean improved = Math.abs(currentBest - lastBestFitness) > epsilon;

        if (improved) {
            // Reset counter on improvement
            iterationsWithoutImprovement = 0;
            lastBestFitness = currentBest;
        } else {
            // Increment counter
            iterationsWithoutImprovement++;
        }

        return iterationsWithoutImprovement >= maxIterationsWithoutImprovement;
    }

    @Override
    public double getProgress(AlgorithmStatistics stats) {
        // Progress is based on how close we are to the max no-improvement threshold
        return Math.min(1.0, (double) iterationsWithoutImprovement / maxIterationsWithoutImprovement);
    }

    @Override
    public String getDescription() {
        return "No improvement for " + maxIterationsWithoutImprovement + " iterations";
    }

    /**
     * Resets the termination condition state.
     * Call this when starting a new optimization run.
     */
    public void reset() {
        this.iterationsWithoutImprovement = 0;
        this.lastBestFitness = Double.NaN;
        this.initialized = false;
    }

    /**
     * Gets the current count of iterations without improvement.
     *
     * @return Current no-improvement count
     */
    public int getIterationsWithoutImprovement() {
        return iterationsWithoutImprovement;
    }

    /**
     * Gets the maximum iterations without improvement threshold.
     *
     * @return Maximum threshold
     */
    public int getMaxIterationsWithoutImprovement() {
        return maxIterationsWithoutImprovement;
    }
}
