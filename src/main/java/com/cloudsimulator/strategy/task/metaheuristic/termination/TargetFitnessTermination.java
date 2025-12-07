package com.cloudsimulator.strategy.task.metaheuristic.termination;

/**
 * Terminates when a target fitness value is reached for a specific objective.
 *
 * Useful when you know the optimal or acceptable solution quality
 * and want to stop as soon as it's achieved. Particularly useful for:
 * - Benchmarking against known optima
 * - Early stopping when "good enough" is reached
 * - Constraint satisfaction problems
 */
public class TargetFitnessTermination implements TerminationCondition {

    private final int objectiveIndex;
    private final double targetValue;
    private final boolean isMinimization;
    private final double tolerance;

    /**
     * Creates a target fitness termination condition.
     *
     * @param objectiveIndex  Index of the objective to check (0-based)
     * @param targetValue     Target value to reach
     * @param isMinimization  true if the objective is being minimized (stop when <= target)
     */
    public TargetFitnessTermination(int objectiveIndex, double targetValue, boolean isMinimization) {
        this(objectiveIndex, targetValue, isMinimization, 0.0);
    }

    /**
     * Creates a target fitness termination condition with tolerance.
     *
     * @param objectiveIndex  Index of the objective to check (0-based)
     * @param targetValue     Target value to reach
     * @param isMinimization  true if the objective is being minimized
     * @param tolerance       Tolerance for comparison (e.g., 0.01 for 1% tolerance)
     */
    public TargetFitnessTermination(int objectiveIndex, double targetValue,
                                    boolean isMinimization, double tolerance) {
        if (objectiveIndex < 0) {
            throw new IllegalArgumentException("objectiveIndex must be non-negative");
        }
        if (tolerance < 0) {
            throw new IllegalArgumentException("tolerance must be non-negative");
        }
        this.objectiveIndex = objectiveIndex;
        this.targetValue = targetValue;
        this.isMinimization = isMinimization;
        this.tolerance = tolerance;
    }

    @Override
    public boolean shouldTerminate(AlgorithmStatistics stats) {
        double[] bestValues = stats.getBestObjectiveValues();

        if (objectiveIndex >= bestValues.length) {
            return false; // Objective doesn't exist
        }

        double currentBest = bestValues[objectiveIndex];
        double adjustedTarget = isMinimization
            ? targetValue * (1 + tolerance)
            : targetValue * (1 - tolerance);

        if (isMinimization) {
            return currentBest <= adjustedTarget;
        } else {
            return currentBest >= adjustedTarget;
        }
    }

    @Override
    public String getDescription() {
        String comparison = isMinimization ? "<=" : ">=";
        String toleranceStr = tolerance > 0
            ? " (±" + String.format("%.1f", tolerance * 100) + "%)"
            : "";
        return "Terminate when objective " + objectiveIndex + " " +
               comparison + " " + targetValue + toleranceStr;
    }

    @Override
    public double getProgress(AlgorithmStatistics stats) {
        double[] bestValues = stats.getBestObjectiveValues();

        if (objectiveIndex >= bestValues.length) {
            return 0.0;
        }

        double currentBest = bestValues[objectiveIndex];

        // Progress is harder to define for target-based termination
        // Return NaN to indicate progress is not applicable
        return Double.NaN;
    }

    public int getObjectiveIndex() {
        return objectiveIndex;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public boolean isMinimization() {
        return isMinimization;
    }

    public double getTolerance() {
        return tolerance;
    }
}
