package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.neighborselection.BestImprovementStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.TerminationCondition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the Local Search algorithm.
 *
 * Provides a builder pattern for easy and flexible configuration,
 * following the same patterns as GAConfiguration and SAConfiguration.
 *
 * Key features:
 * - Swappable neighbor selection strategies (Best, First, Random)
 * - Configurable neighborhood types (REASSIGN, SWAP_ORDER, COMBINED)
 * - Single objective or weighted-sum multi-objective optimization
 * - Configurable termination conditions
 *
 * Example usage:
 * <pre>
 * // Basic configuration with best improvement
 * LocalSearchConfiguration config = LocalSearchConfiguration.builder()
 *     .neighborSelectionStrategy(new BestImprovementStrategy())
 *     .neighborhoodType(NeighborhoodGenerator.NeighborhoodType.COMBINED)
 *     .objective(new MakespanObjective())
 *     .maxIterations(1000)
 *     .verboseLogging(true)
 *     .build();
 *
 * // First improvement with early stopping
 * LocalSearchConfiguration config = LocalSearchConfiguration.builder()
 *     .neighborSelectionStrategy(new FirstImprovementStrategy())
 *     .neighborhoodType(NeighborhoodGenerator.NeighborhoodType.REASSIGN)
 *     .objective(new MakespanObjective())
 *     .maxIterationsWithoutImprovement(100)
 *     .build();
 *
 * // Weighted-sum multi-objective
 * LocalSearchConfiguration config = LocalSearchConfiguration.builder()
 *     .neighborSelectionStrategy(new RandomSelectionStrategy())
 *     .addWeightedObjective(new MakespanObjective(), 0.7)
 *     .addWeightedObjective(new EnergyObjective(), 0.3)
 *     .build();
 * </pre>
 *
 * Reference: Talbi, E-G. "Metaheuristics: From Design to Implementation", Chapter 2
 */
public class LocalSearchConfiguration {

    // Neighbor selection
    private final NeighborSelectionStrategy neighborSelectionStrategy;
    private final NeighborhoodGenerator.NeighborhoodType neighborhoodType;

    // Objectives with weights (for weighted sum)
    private final List<SchedulingObjective> objectives;
    private final Map<SchedulingObjective, Double> objectiveWeights;

    // Termination
    private final TerminationCondition terminationCondition;
    private final int maxIterations;
    private final int maxIterationsWithoutImprovement;

    // Logging
    private final boolean verboseLogging;
    private final int logInterval;

    /**
     * Private constructor - use Builder.
     */
    private LocalSearchConfiguration(Builder builder) {
        this.neighborSelectionStrategy = builder.neighborSelectionStrategy;
        this.neighborhoodType = builder.neighborhoodType;
        this.objectives = new ArrayList<>(builder.objectives);
        this.objectiveWeights = new LinkedHashMap<>(builder.objectiveWeights);
        this.terminationCondition = builder.terminationCondition;
        this.maxIterations = builder.maxIterations;
        this.maxIterationsWithoutImprovement = builder.maxIterationsWithoutImprovement;
        this.verboseLogging = builder.verboseLogging;
        this.logInterval = builder.logInterval;
    }

    /**
     * Creates a new configuration builder.
     *
     * @return New Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default configuration with specified objective.
     *
     * @param objective Single objective to optimize
     * @return Default configuration
     */
    public static LocalSearchConfiguration defaultConfig(SchedulingObjective objective) {
        return builder()
            .objective(objective)
            .build();
    }

    // Getters

    public NeighborSelectionStrategy getNeighborSelectionStrategy() {
        return neighborSelectionStrategy;
    }

    public NeighborhoodGenerator.NeighborhoodType getNeighborhoodType() {
        return neighborhoodType;
    }

    public List<SchedulingObjective> getObjectives() {
        return objectives;
    }

    public int getNumObjectives() {
        return objectives.size();
    }

    /**
     * Gets the weight for a specific objective.
     *
     * @param objective The objective
     * @return Weight (1.0 if not specified)
     */
    public double getObjectiveWeight(SchedulingObjective objective) {
        return objectiveWeights.getOrDefault(objective, 1.0);
    }

    /**
     * Gets all objective weights.
     *
     * @return Map of objectives to weights
     */
    public Map<SchedulingObjective, Double> getObjectiveWeights() {
        return objectiveWeights;
    }

    /**
     * Checks if using weighted sum multi-objective optimization.
     *
     * @return true if multiple objectives with weights
     */
    public boolean isWeightedSum() {
        return objectives.size() > 1;
    }

    public TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public int getMaxIterationsWithoutImprovement() {
        return maxIterationsWithoutImprovement;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public int getLogInterval() {
        return logInterval;
    }

    /**
     * Gets the primary objective (first objective for single-objective,
     * or the objective with highest weight for weighted sum).
     *
     * @return Primary objective
     */
    public SchedulingObjective getPrimaryObjective() {
        if (objectives.isEmpty()) {
            throw new IllegalStateException("No objectives configured");
        }

        if (objectives.size() == 1) {
            return objectives.get(0);
        }

        // Find objective with highest weight
        SchedulingObjective primary = objectives.get(0);
        double maxWeight = getObjectiveWeight(primary);

        for (SchedulingObjective obj : objectives) {
            double weight = getObjectiveWeight(obj);
            if (weight > maxWeight) {
                maxWeight = weight;
                primary = obj;
            }
        }

        return primary;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LocalSearchConfiguration{");
        sb.append("strategy=").append(neighborSelectionStrategy.getName());
        sb.append(", neighborhood=").append(neighborhoodType);
        sb.append(", objectives=[");
        for (int i = 0; i < objectives.size(); i++) {
            if (i > 0) sb.append(", ");
            SchedulingObjective obj = objectives.get(i);
            sb.append(obj.getName());
            if (isWeightedSum()) {
                sb.append(":").append(String.format("%.2f", getObjectiveWeight(obj)));
            }
        }
        sb.append("]");
        sb.append(", maxIter=").append(maxIterations);
        sb.append(", maxNoImprove=").append(maxIterationsWithoutImprovement);
        if (terminationCondition != null) {
            sb.append(", termination=").append(terminationCondition.getDescription());
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Builder for LocalSearchConfiguration.
     */
    public static class Builder {
        // Neighbor selection defaults
        private NeighborSelectionStrategy neighborSelectionStrategy = new BestImprovementStrategy();
        private NeighborhoodGenerator.NeighborhoodType neighborhoodType =
            NeighborhoodGenerator.NeighborhoodType.COMBINED;

        // Objectives
        private List<SchedulingObjective> objectives = new ArrayList<>();
        private Map<SchedulingObjective, Double> objectiveWeights = new LinkedHashMap<>();

        // Termination defaults
        private TerminationCondition terminationCondition = null;
        private int maxIterations = 10000;
        private int maxIterationsWithoutImprovement = 100;

        // Logging defaults
        private boolean verboseLogging = false;
        private int logInterval = 10;

        /**
         * Sets the neighbor selection strategy.
         *
         * @param strategy Strategy to use (BestImprovement, FirstImprovement, RandomSelection)
         * @return this builder
         */
        public Builder neighborSelectionStrategy(NeighborSelectionStrategy strategy) {
            this.neighborSelectionStrategy = strategy;
            return this;
        }

        /**
         * Sets the neighborhood type.
         *
         * @param type Type of moves to generate neighbors (REASSIGN, SWAP_ORDER, COMBINED)
         * @return this builder
         */
        public Builder neighborhoodType(NeighborhoodGenerator.NeighborhoodType type) {
            this.neighborhoodType = type;
            return this;
        }

        /**
         * Sets a single objective to optimize.
         * This clears any previously set objectives.
         *
         * @param objective The objective to optimize
         * @return this builder
         */
        public Builder objective(SchedulingObjective objective) {
            this.objectives.clear();
            this.objectiveWeights.clear();
            this.objectives.add(objective);
            this.objectiveWeights.put(objective, 1.0);
            return this;
        }

        /**
         * Adds an objective with a weight for weighted sum optimization.
         *
         * @param objective The objective to add
         * @param weight    The weight for this objective (will be normalized)
         * @return this builder
         */
        public Builder addWeightedObjective(SchedulingObjective objective, double weight) {
            if (weight < 0.0) {
                throw new IllegalArgumentException("Weight must be non-negative");
            }
            this.objectives.add(objective);
            this.objectiveWeights.put(objective, weight);
            return this;
        }

        /**
         * Sets the termination condition.
         *
         * @param terminationCondition Condition for stopping the algorithm
         * @return this builder
         */
        public Builder terminationCondition(TerminationCondition terminationCondition) {
            this.terminationCondition = terminationCondition;
            return this;
        }

        /**
         * Sets the maximum number of iterations (safety limit).
         *
         * @param maxIterations Maximum iterations
         * @return this builder
         */
        public Builder maxIterations(int maxIterations) {
            if (maxIterations < 1) {
                throw new IllegalArgumentException("Max iterations must be at least 1");
            }
            this.maxIterations = maxIterations;
            return this;
        }

        /**
         * Sets the maximum iterations without improvement before stopping.
         * This is the primary termination criterion for local search.
         *
         * @param maxIterationsWithoutImprovement Iterations without improvement before stopping
         * @return this builder
         */
        public Builder maxIterationsWithoutImprovement(int maxIterationsWithoutImprovement) {
            if (maxIterationsWithoutImprovement < 1) {
                throw new IllegalArgumentException("Max iterations without improvement must be at least 1");
            }
            this.maxIterationsWithoutImprovement = maxIterationsWithoutImprovement;
            return this;
        }

        /**
         * Enables or disables verbose logging.
         *
         * @param verboseLogging true to enable verbose output
         * @return this builder
         */
        public Builder verboseLogging(boolean verboseLogging) {
            this.verboseLogging = verboseLogging;
            return this;
        }

        /**
         * Sets the logging interval (print status every N iterations).
         *
         * @param logInterval Number of iterations between log outputs
         * @return this builder
         */
        public Builder logInterval(int logInterval) {
            if (logInterval < 1) {
                throw new IllegalArgumentException("Log interval must be at least 1");
            }
            this.logInterval = logInterval;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return Configured LocalSearchConfiguration
         * @throws IllegalStateException if no objectives are specified
         */
        public LocalSearchConfiguration build() {
            if (objectives.isEmpty()) {
                throw new IllegalStateException("At least one objective must be specified");
            }

            // Normalize weights if using weighted sum
            if (objectives.size() > 1) {
                double totalWeight = objectiveWeights.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

                if (totalWeight > 0) {
                    for (SchedulingObjective obj : objectives) {
                        double normalized = objectiveWeights.get(obj) / totalWeight;
                        objectiveWeights.put(obj, normalized);
                    }
                }
            }

            return new LocalSearchConfiguration(this);
        }
    }
}
