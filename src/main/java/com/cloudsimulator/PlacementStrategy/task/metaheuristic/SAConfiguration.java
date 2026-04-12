package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.CoolingSchedule;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.cooling.GeometricCoolingSchedule;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.TerminationCondition;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.TemperatureTermination;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the Simulated Annealing algorithm.
 *
 * Provides a builder pattern for easy and flexible configuration.
 * All parameters have sensible defaults.
 *
 * Key features:
 * - Single objective optimization (default) or weighted sum multi-objective
 * - Multiple cooling schedules (linear, geometric, logarithmic, adaptive)
 * - Auto-calculation of initial temperature based on acceptance probability
 * - Configurable neighbor generation using mutation operators
 * - Uses the simulator's RandomGenerator for reproducibility
 *
 * Example usage:
 * <pre>
 * // Basic configuration with geometric cooling
 * SAConfiguration config = SAConfiguration.builder()
 *     .initialTemperature(1000.0)
 *     .finalTemperature(0.001)
 *     .coolingSchedule(new GeometricCoolingSchedule(0.95))
 *     .iterationsPerTemperature(100)
 *     .objective(new MakespanObjective())
 *     .verboseLogging(true)
 *     .build();
 *
 * // Auto-temperature with adaptive cooling
 * SAConfiguration config = SAConfiguration.builder()
 *     .autoInitialTemperature(true)
 *     .initialAcceptanceProbability(0.8)
 *     .coolingSchedule(new AdaptiveCoolingSchedule())
 *     .objective(new MakespanObjective())
 *     .build();
 *
 * // Weighted-sum multi-objective
 * SAConfiguration config = SAConfiguration.builder()
 *     .initialTemperature(1000.0)
 *     .coolingSchedule(new GeometricCoolingSchedule(0.95))
 *     .addWeightedObjective(new MakespanObjective(), 0.7)
 *     .addWeightedObjective(new EnergyObjective(), 0.3)
 *     .build();
 * </pre>
 *
 * Reference: El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 */
public class SAConfiguration {

    // Temperature parameters
    private final double initialTemperature;
    private final double finalTemperature;
    private final boolean autoInitialTemperature;
    private final double initialAcceptanceProbability;
    private final int temperatureSampleSize;

    // Cooling schedule
    private final CoolingSchedule coolingSchedule;

    // Equilibrium parameters
    private final int iterationsPerTemperature;

    // Multi-start restart
    private final int numberOfRestarts;

    // Neighbor generation
    private final MutationOperator.MutationType neighborType;

    // Objectives with weights (for weighted sum)
    private final List<SchedulingObjective> objectives;
    private final Map<SchedulingObjective, Double> objectiveWeights;

    // Termination
    private final TerminationCondition terminationCondition;

    // Logging
    private final boolean verboseLogging;
    private final int logInterval;

    /**
     * Private constructor - use Builder.
     */
    private SAConfiguration(Builder builder) {
        this.initialTemperature = builder.initialTemperature;
        this.finalTemperature = builder.finalTemperature;
        this.autoInitialTemperature = builder.autoInitialTemperature;
        this.initialAcceptanceProbability = builder.initialAcceptanceProbability;
        this.temperatureSampleSize = builder.temperatureSampleSize;
        this.coolingSchedule = builder.coolingSchedule;
        this.iterationsPerTemperature = builder.iterationsPerTemperature;
        this.numberOfRestarts = builder.numberOfRestarts;
        this.neighborType = builder.neighborType;
        this.objectives = new ArrayList<>(builder.objectives);
        this.objectiveWeights = new LinkedHashMap<>(builder.objectiveWeights);
        this.terminationCondition = builder.terminationCondition;
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
    public static SAConfiguration defaultConfig(SchedulingObjective objective) {
        return builder()
            .objective(objective)
            .build();
    }

    // Getters

    public double getInitialTemperature() {
        return initialTemperature;
    }

    public double getFinalTemperature() {
        return finalTemperature;
    }

    public boolean isAutoInitialTemperature() {
        return autoInitialTemperature;
    }

    public double getInitialAcceptanceProbability() {
        return initialAcceptanceProbability;
    }

    public int getTemperatureSampleSize() {
        return temperatureSampleSize;
    }

    public CoolingSchedule getCoolingSchedule() {
        return coolingSchedule;
    }

    public int getIterationsPerTemperature() {
        return iterationsPerTemperature;
    }

    public int getNumberOfRestarts() {
        return numberOfRestarts;
    }

    public MutationOperator.MutationType getNeighborType() {
        return neighborType;
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
        sb.append("SAConfiguration{");
        if (autoInitialTemperature) {
            sb.append("autoT0=true, acceptProb=").append(initialAcceptanceProbability);
        } else {
            sb.append("T0=").append(initialTemperature);
        }
        sb.append(", Tmin=").append(finalTemperature);
        sb.append(", cooling=").append(coolingSchedule.getName());
        sb.append(", itersPerT=").append(iterationsPerTemperature);
        if (numberOfRestarts > 1) {
            sb.append(", restarts=").append(numberOfRestarts);
        }
        sb.append(", neighbor=").append(neighborType);
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
        sb.append(", termination=").append(terminationCondition.getDescription());
        sb.append('}');
        return sb.toString();
    }

    /**
     * Builder for SAConfiguration.
     */
    public static class Builder {
        // Temperature defaults
        private double initialTemperature = 1000.0;
        private double finalTemperature = 0.001;
        private boolean autoInitialTemperature = false;
        private double initialAcceptanceProbability = 0.8;
        private int temperatureSampleSize = 100;

        // Cooling schedule default
        private CoolingSchedule coolingSchedule = new GeometricCoolingSchedule(0.95);

        // Equilibrium default
        private int iterationsPerTemperature = 100;

        // Multi-start default (1 = single run, no restarts)
        private int numberOfRestarts = 1;

        // Neighbor generation default
        private MutationOperator.MutationType neighborType = MutationOperator.MutationType.COMBINED;

        // Objectives
        private List<SchedulingObjective> objectives = new ArrayList<>();
        private Map<SchedulingObjective, Double> objectiveWeights = new LinkedHashMap<>();

        // Termination default (temperature-based)
        private TerminationCondition terminationCondition = null; // Will be set in build()

        // Logging defaults
        private boolean verboseLogging = false;
        private int logInterval = 10;

        /**
         * Sets the initial temperature.
         * This disables auto-calculation.
         *
         * @param initialTemperature Starting temperature (must be positive)
         * @return this builder
         */
        public Builder initialTemperature(double initialTemperature) {
            if (initialTemperature <= 0) {
                throw new IllegalArgumentException("Initial temperature must be positive");
            }
            this.initialTemperature = initialTemperature;
            this.autoInitialTemperature = false;
            return this;
        }

        /**
         * Sets the final/minimum temperature.
         * This is the stopping criterion for temperature-based termination.
         *
         * @param finalTemperature Minimum temperature (must be positive and < initial)
         * @return this builder
         */
        public Builder finalTemperature(double finalTemperature) {
            if (finalTemperature <= 0) {
                throw new IllegalArgumentException("Final temperature must be positive");
            }
            this.finalTemperature = finalTemperature;
            return this;
        }

        /**
         * Enables auto-calculation of initial temperature.
         * The temperature will be calculated to achieve the target acceptance probability.
         *
         * @param autoInitialTemperature true to enable auto-calculation
         * @return this builder
         */
        public Builder autoInitialTemperature(boolean autoInitialTemperature) {
            this.autoInitialTemperature = autoInitialTemperature;
            return this;
        }

        /**
         * Sets the target initial acceptance probability for auto-temperature calculation.
         * Only used when autoInitialTemperature is true.
         *
         * @param probability Target acceptance probability (0.0 to 1.0, typically 0.8)
         * @return this builder
         */
        public Builder initialAcceptanceProbability(double probability) {
            if (probability <= 0.0 || probability >= 1.0) {
                throw new IllegalArgumentException(
                    "Initial acceptance probability must be between 0.0 and 1.0 (exclusive)");
            }
            this.initialAcceptanceProbability = probability;
            return this;
        }

        /**
         * Sets the number of neighbors to sample for auto-temperature calculation.
         *
         * @param sampleSize Number of neighbors to sample (typically 50-200)
         * @return this builder
         */
        public Builder temperatureSampleSize(int sampleSize) {
            if (sampleSize < 10) {
                throw new IllegalArgumentException("Sample size must be at least 10");
            }
            this.temperatureSampleSize = sampleSize;
            return this;
        }

        /**
         * Sets the cooling schedule.
         *
         * @param coolingSchedule Cooling schedule to use
         * @return this builder
         */
        public Builder coolingSchedule(CoolingSchedule coolingSchedule) {
            this.coolingSchedule = coolingSchedule;
            return this;
        }

        /**
         * Sets the number of iterations at each temperature level (equilibrium condition).
         *
         * @param iterations Iterations per temperature (must be positive)
         * @return this builder
         */
        public Builder iterationsPerTemperature(int iterations) {
            if (iterations < 1) {
                throw new IllegalArgumentException("Iterations per temperature must be at least 1");
            }
            this.iterationsPerTemperature = iterations;
            return this;
        }

        /**
         * Sets the number of independent restarts (multi-start SA).
         * The evaluation budget is divided equally among restarts.
         * Each restart begins from a new random solution at full temperature.
         * The global best across all restarts is returned.
         *
         * @param numberOfRestarts Number of restarts (must be at least 1)
         * @return this builder
         */
        public Builder numberOfRestarts(int numberOfRestarts) {
            if (numberOfRestarts < 1) {
                throw new IllegalArgumentException("Number of restarts must be at least 1");
            }
            this.numberOfRestarts = numberOfRestarts;
            return this;
        }

        /**
         * Sets the neighbor generation type.
         *
         * @param neighborType Type of mutation for neighbor generation
         * @return this builder
         */
        public Builder neighborType(MutationOperator.MutationType neighborType) {
            this.neighborType = neighborType;
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
         * If not specified, defaults to temperature-based termination.
         *
         * @param terminationCondition Condition for stopping the algorithm
         * @return this builder
         */
        public Builder terminationCondition(TerminationCondition terminationCondition) {
            this.terminationCondition = terminationCondition;
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
         * Sets the logging interval (print status every N temperature steps).
         *
         * @param logInterval Number of temperature steps between log outputs
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
         * @return Configured SAConfiguration
         * @throws IllegalStateException if no objectives are specified
         */
        public SAConfiguration build() {
            if (objectives.isEmpty()) {
                throw new IllegalStateException("At least one objective must be specified");
            }

            // Validate temperature settings
            if (!autoInitialTemperature && initialTemperature <= finalTemperature) {
                throw new IllegalStateException(
                    "Initial temperature (" + initialTemperature +
                    ") must be greater than final temperature (" + finalTemperature + ")");
            }

            // Set default termination condition if not specified
            if (terminationCondition == null) {
                terminationCondition = new TemperatureTermination(finalTemperature);
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

            return new SAConfiguration(this);
        }
    }
}
