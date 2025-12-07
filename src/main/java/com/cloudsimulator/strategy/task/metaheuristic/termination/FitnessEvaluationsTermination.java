package com.cloudsimulator.strategy.task.metaheuristic.termination;

/**
 * Terminates after a specified number of fitness evaluations.
 *
 * This provides a fair comparison between algorithms that may have
 * different population sizes or generation structures, as it
 * normalizes on the computational budget (number of evaluations).
 */
public class FitnessEvaluationsTermination implements TerminationCondition {

    private final long maxEvaluations;

    /**
     * Creates a fitness evaluations termination condition.
     *
     * @param maxEvaluations Maximum number of fitness evaluations before termination
     */
    public FitnessEvaluationsTermination(long maxEvaluations) {
        if (maxEvaluations <= 0) {
            throw new IllegalArgumentException("maxEvaluations must be positive");
        }
        this.maxEvaluations = maxEvaluations;
    }

    @Override
    public boolean shouldTerminate(AlgorithmStatistics stats) {
        return stats.getTotalFitnessEvaluations() >= maxEvaluations;
    }

    @Override
    public String getDescription() {
        return "Terminate after " + maxEvaluations + " fitness evaluations";
    }

    @Override
    public double getProgress(AlgorithmStatistics stats) {
        return (double) stats.getTotalFitnessEvaluations() / maxEvaluations;
    }

    public long getMaxEvaluations() {
        return maxEvaluations;
    }
}
