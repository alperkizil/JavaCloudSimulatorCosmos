package com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination;

/**
 * Interface for termination conditions used by metaheuristic algorithms.
 *
 * Termination conditions determine when the optimization should stop.
 * Multiple conditions can be combined using CompositeTermination.
 *
 * Built-in conditions:
 * - GenerationCountTermination: Stop after N generations
 * - FitnessEvaluationsTermination: Stop after N fitness evaluations
 * - TimeLimitTermination: Stop after N milliseconds
 * - TargetFitnessTermination: Stop when target fitness is reached
 * - CompositeTermination: Combine multiple conditions with AND/OR logic
 */
public interface TerminationCondition {

    /**
     * Checks if the algorithm should terminate based on current statistics.
     *
     * @param stats Current algorithm statistics
     * @return true if the algorithm should stop, false to continue
     */
    boolean shouldTerminate(AlgorithmStatistics stats);

    /**
     * Gets a human-readable description of this termination condition.
     *
     * @return Description string
     */
    String getDescription();

    /**
     * Gets the progress towards termination as a percentage (0.0 to 1.0).
     * Returns Double.NaN if progress cannot be determined.
     *
     * @param stats Current algorithm statistics
     * @return Progress percentage (0.0 to 1.0) or NaN
     */
    default double getProgress(AlgorithmStatistics stats) {
        return Double.NaN;
    }

    /**
     * Resets any internal state for a new algorithm run.
     * Default implementation does nothing.
     */
    default void reset() {
        // No-op by default
    }
}
