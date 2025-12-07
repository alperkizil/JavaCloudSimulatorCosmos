package com.cloudsimulator.strategy.task.metaheuristic.termination;

/**
 * Terminates after a specified number of generations.
 *
 * This is the most common termination condition for evolutionary algorithms.
 * A generation is typically one complete cycle of selection, crossover,
 * mutation, and replacement.
 */
public class GenerationCountTermination implements TerminationCondition {

    private final int maxGenerations;

    /**
     * Creates a generation count termination condition.
     *
     * @param maxGenerations Maximum number of generations before termination
     */
    public GenerationCountTermination(int maxGenerations) {
        if (maxGenerations <= 0) {
            throw new IllegalArgumentException("maxGenerations must be positive");
        }
        this.maxGenerations = maxGenerations;
    }

    @Override
    public boolean shouldTerminate(AlgorithmStatistics stats) {
        return stats.getCurrentGeneration() >= maxGenerations;
    }

    @Override
    public String getDescription() {
        return "Terminate after " + maxGenerations + " generations";
    }

    @Override
    public double getProgress(AlgorithmStatistics stats) {
        return (double) stats.getCurrentGeneration() / maxGenerations;
    }

    public int getMaxGenerations() {
        return maxGenerations;
    }
}
