package com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Combines multiple termination conditions using AND or OR logic.
 *
 * AND mode: Terminates when ALL conditions are met
 * OR mode:  Terminates when ANY condition is met (default)
 *
 * Example usage:
 * <pre>
 * // Stop after 100 generations OR 60 seconds, whichever comes first
 * TerminationCondition term = CompositeTermination.or(
 *     new GenerationCountTermination(100),
 *     TimeLimitTermination.seconds(60)
 * );
 *
 * // Stop after 100 generations AND reaching target fitness
 * TerminationCondition term = CompositeTermination.and(
 *     new GenerationCountTermination(100),
 *     new TargetFitnessTermination(0, 1000.0, true)
 * );
 * </pre>
 */
public class CompositeTermination implements TerminationCondition {

    /**
     * Logic mode for combining conditions.
     */
    public enum LogicMode {
        AND,  // All conditions must be met
        OR    // Any condition must be met
    }

    private final List<TerminationCondition> conditions;
    private final LogicMode mode;

    /**
     * Creates a composite termination with specified conditions and logic mode.
     *
     * @param conditions List of termination conditions
     * @param mode       Logic mode (AND or OR)
     */
    public CompositeTermination(List<TerminationCondition> conditions, LogicMode mode) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("At least one condition is required");
        }
        this.conditions = new ArrayList<>(conditions);
        this.mode = mode;
    }

    /**
     * Creates a composite termination using OR logic (terminates when ANY condition is met).
     *
     * @param conditions Termination conditions
     * @return CompositeTermination with OR logic
     */
    public static CompositeTermination or(TerminationCondition... conditions) {
        return new CompositeTermination(Arrays.asList(conditions), LogicMode.OR);
    }

    /**
     * Creates a composite termination using OR logic from a list.
     *
     * @param conditions List of termination conditions
     * @return CompositeTermination with OR logic
     */
    public static CompositeTermination or(List<TerminationCondition> conditions) {
        return new CompositeTermination(conditions, LogicMode.OR);
    }

    /**
     * Creates a composite termination using AND logic (terminates when ALL conditions are met).
     *
     * @param conditions Termination conditions
     * @return CompositeTermination with AND logic
     */
    public static CompositeTermination and(TerminationCondition... conditions) {
        return new CompositeTermination(Arrays.asList(conditions), LogicMode.AND);
    }

    /**
     * Creates a composite termination using AND logic from a list.
     *
     * @param conditions List of termination conditions
     * @return CompositeTermination with AND logic
     */
    public static CompositeTermination and(List<TerminationCondition> conditions) {
        return new CompositeTermination(conditions, LogicMode.AND);
    }

    @Override
    public boolean shouldTerminate(AlgorithmStatistics stats) {
        if (mode == LogicMode.AND) {
            // All conditions must be met
            for (TerminationCondition condition : conditions) {
                if (!condition.shouldTerminate(stats)) {
                    return false;
                }
            }
            return true;
        } else {
            // Any condition must be met
            for (TerminationCondition condition : conditions) {
                if (condition.shouldTerminate(stats)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public String getDescription() {
        String separator = mode == LogicMode.AND ? " AND " : " OR ";
        return "(" + conditions.stream()
            .map(TerminationCondition::getDescription)
            .collect(Collectors.joining(separator)) + ")";
    }

    @Override
    public double getProgress(AlgorithmStatistics stats) {
        if (mode == LogicMode.OR) {
            // For OR, return the maximum progress (closest to terminating)
            double maxProgress = 0.0;
            for (TerminationCondition condition : conditions) {
                double progress = condition.getProgress(stats);
                if (!Double.isNaN(progress) && progress > maxProgress) {
                    maxProgress = progress;
                }
            }
            return maxProgress;
        } else {
            // For AND, return the minimum progress (furthest from all being met)
            double minProgress = 1.0;
            for (TerminationCondition condition : conditions) {
                double progress = condition.getProgress(stats);
                if (!Double.isNaN(progress) && progress < minProgress) {
                    minProgress = progress;
                }
            }
            return minProgress;
        }
    }

    @Override
    public void reset() {
        for (TerminationCondition condition : conditions) {
            condition.reset();
        }
    }

    /**
     * Adds a new condition to this composite.
     *
     * @param condition Condition to add
     * @return this for method chaining
     */
    public CompositeTermination add(TerminationCondition condition) {
        this.conditions.add(condition);
        return this;
    }

    public List<TerminationCondition> getConditions() {
        return conditions;
    }

    public LogicMode getMode() {
        return mode;
    }
}
