package com.cloudsimulator.strategy.task.metaheuristic.termination;

/**
 * Terminates after a specified time limit.
 *
 * Useful for real-time systems or when you have a strict time budget
 * for optimization. The algorithm will complete the current generation
 * before checking the time limit.
 */
public class TimeLimitTermination implements TerminationCondition {

    private final long maxTimeMillis;

    /**
     * Creates a time limit termination condition.
     *
     * @param maxTimeMillis Maximum time in milliseconds before termination
     */
    public TimeLimitTermination(long maxTimeMillis) {
        if (maxTimeMillis <= 0) {
            throw new IllegalArgumentException("maxTimeMillis must be positive");
        }
        this.maxTimeMillis = maxTimeMillis;
    }

    /**
     * Creates a time limit termination condition with seconds.
     *
     * @param seconds Maximum time in seconds before termination
     * @return TimeLimitTermination configured for the specified seconds
     */
    public static TimeLimitTermination seconds(long seconds) {
        return new TimeLimitTermination(seconds * 1000);
    }

    /**
     * Creates a time limit termination condition with minutes.
     *
     * @param minutes Maximum time in minutes before termination
     * @return TimeLimitTermination configured for the specified minutes
     */
    public static TimeLimitTermination minutes(long minutes) {
        return new TimeLimitTermination(minutes * 60 * 1000);
    }

    @Override
    public boolean shouldTerminate(AlgorithmStatistics stats) {
        stats.updateElapsedTime();
        return stats.getElapsedTimeMillis() >= maxTimeMillis;
    }

    @Override
    public String getDescription() {
        if (maxTimeMillis >= 60000) {
            return "Terminate after " + (maxTimeMillis / 60000) + " minutes";
        } else if (maxTimeMillis >= 1000) {
            return "Terminate after " + (maxTimeMillis / 1000) + " seconds";
        }
        return "Terminate after " + maxTimeMillis + " milliseconds";
    }

    @Override
    public double getProgress(AlgorithmStatistics stats) {
        stats.updateElapsedTime();
        return (double) stats.getElapsedTimeMillis() / maxTimeMillis;
    }

    public long getMaxTimeMillis() {
        return maxTimeMillis;
    }
}
