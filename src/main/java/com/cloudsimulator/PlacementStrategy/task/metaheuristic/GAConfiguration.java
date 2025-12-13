package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.CrossoverOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.TerminationCondition;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the Generational Genetic Algorithm with Elitism.
 *
 * Provides a builder pattern for easy and flexible configuration.
 * All parameters have sensible defaults.
 *
 * Key features:
 * - Single objective optimization (default) or weighted sum multi-objective
 * - Configurable elitism (absolute count or percentage)
 * - Tournament selection with configurable size
 * - Uses the simulator's RandomGenerator for reproducibility
 *
 * Example usage:
 * <pre>
 * GAConfiguration config = GAConfiguration.builder()
 *     .populationSize(100)
 *     .crossoverRate(0.9)
 *     .mutationRate(0.1)
 *     .eliteCount(10)                    // Absolute elitism
 *     .tournamentSize(3)
 *     .objective(new MakespanObjective()) // Single objective
 *     .terminationCondition(new GenerationCountTermination(200))
 *     .verboseLogging(true)
 *     .build();
 *
 * // Or with weighted sum multi-objective:
 * GAConfiguration config = GAConfiguration.builder()
 *     .populationSize(100)
 *     .elitePercentage(0.1)              // Percentage elitism (10%)
 *     .addWeightedObjective(new MakespanObjective(), 0.7)
 *     .addWeightedObjective(new EnergyObjective(), 0.3)
 *     .build();
 * </pre>
 */
public class GAConfiguration {

    /**
     * Elitism type enumeration.
     */
    public enum ElitismType {
        ABSOLUTE,    // Fixed number of elite individuals
        PERCENTAGE   // Percentage of population
    }

    // Population parameters
    private final int populationSize;

    // Genetic operator parameters
    private final double crossoverRate;
    private final double mutationRate;
    private final CrossoverOperator.CrossoverType crossoverType;

    // Selection parameters
    private final int tournamentSize;

    // Elitism parameters
    private final ElitismType elitismType;
    private final int eliteCount;         // For ABSOLUTE elitism
    private final double elitePercentage; // For PERCENTAGE elitism

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
    private GAConfiguration(Builder builder) {
        this.populationSize = builder.populationSize;
        this.crossoverRate = builder.crossoverRate;
        this.mutationRate = builder.mutationRate;
        this.crossoverType = builder.crossoverType;
        this.tournamentSize = builder.tournamentSize;
        this.elitismType = builder.elitismType;
        this.eliteCount = builder.eliteCount;
        this.elitePercentage = builder.elitePercentage;
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
    public static GAConfiguration defaultConfig(SchedulingObjective objective) {
        return builder()
            .objective(objective)
            .build();
    }

    // Getters

    public int getPopulationSize() {
        return populationSize;
    }

    public double getCrossoverRate() {
        return crossoverRate;
    }

    public double getMutationRate() {
        return mutationRate;
    }

    public CrossoverOperator.CrossoverType getCrossoverType() {
        return crossoverType;
    }

    public int getTournamentSize() {
        return tournamentSize;
    }

    public ElitismType getElitismType() {
        return elitismType;
    }

    /**
     * Gets the number of elite individuals to preserve.
     * Calculates from percentage if elitism type is PERCENTAGE.
     *
     * @return Number of elite individuals
     */
    public int getEliteCount() {
        if (elitismType == ElitismType.PERCENTAGE) {
            return Math.max(1, (int) Math.round(populationSize * elitePercentage));
        }
        return eliteCount;
    }

    public double getElitePercentage() {
        return elitePercentage;
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
        sb.append("GAConfiguration{");
        sb.append("populationSize=").append(populationSize);
        sb.append(", crossoverRate=").append(crossoverRate);
        sb.append(", mutationRate=").append(mutationRate);
        sb.append(", crossoverType=").append(crossoverType);
        sb.append(", tournamentSize=").append(tournamentSize);
        sb.append(", elitism=");
        if (elitismType == ElitismType.PERCENTAGE) {
            sb.append(String.format("%.0f%%", elitePercentage * 100));
        } else {
            sb.append(eliteCount);
        }
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
     * Builder for GAConfiguration.
     */
    public static class Builder {
        private int populationSize = 100;
        private double crossoverRate = 0.9;
        private double mutationRate = 0.1;
        private CrossoverOperator.CrossoverType crossoverType = CrossoverOperator.CrossoverType.UNIFORM;
        private int tournamentSize = 2;
        private ElitismType elitismType = ElitismType.PERCENTAGE;
        private int eliteCount = 10;
        private double elitePercentage = 0.1; // 10% default
        private List<SchedulingObjective> objectives = new ArrayList<>();
        private Map<SchedulingObjective, Double> objectiveWeights = new LinkedHashMap<>();
        private TerminationCondition terminationCondition = new GenerationCountTermination(100);
        private boolean verboseLogging = false;
        private int logInterval = 10;

        /**
         * Sets the population size.
         *
         * @param populationSize Number of solutions in each generation
         * @return this builder
         */
        public Builder populationSize(int populationSize) {
            if (populationSize < 4) {
                throw new IllegalArgumentException("Population size must be at least 4");
            }
            this.populationSize = populationSize;
            return this;
        }

        /**
         * Sets the crossover rate.
         *
         * @param crossoverRate Probability of crossover (0.0 to 1.0)
         * @return this builder
         */
        public Builder crossoverRate(double crossoverRate) {
            if (crossoverRate < 0.0 || crossoverRate > 1.0) {
                throw new IllegalArgumentException("Crossover rate must be between 0.0 and 1.0");
            }
            this.crossoverRate = crossoverRate;
            return this;
        }

        /**
         * Sets the mutation rate.
         *
         * @param mutationRate Probability of mutation per gene (0.0 to 1.0)
         * @return this builder
         */
        public Builder mutationRate(double mutationRate) {
            if (mutationRate < 0.0 || mutationRate > 1.0) {
                throw new IllegalArgumentException("Mutation rate must be between 0.0 and 1.0");
            }
            this.mutationRate = mutationRate;
            return this;
        }

        /**
         * Sets the crossover type.
         *
         * @param crossoverType Type of crossover operator
         * @return this builder
         */
        public Builder crossoverType(CrossoverOperator.CrossoverType crossoverType) {
            this.crossoverType = crossoverType;
            return this;
        }

        /**
         * Sets the tournament size for selection.
         *
         * @param tournamentSize Number of individuals in each tournament
         * @return this builder
         */
        public Builder tournamentSize(int tournamentSize) {
            if (tournamentSize < 2) {
                throw new IllegalArgumentException("Tournament size must be at least 2");
            }
            this.tournamentSize = tournamentSize;
            return this;
        }

        /**
         * Sets absolute elitism (keep top N individuals).
         *
         * @param eliteCount Number of elite individuals to preserve
         * @return this builder
         */
        public Builder eliteCount(int eliteCount) {
            if (eliteCount < 0) {
                throw new IllegalArgumentException("Elite count must be non-negative");
            }
            this.elitismType = ElitismType.ABSOLUTE;
            this.eliteCount = eliteCount;
            return this;
        }

        /**
         * Sets percentage-based elitism (keep top X% of population).
         *
         * @param elitePercentage Percentage of elite individuals (0.0 to 1.0)
         * @return this builder
         */
        public Builder elitePercentage(double elitePercentage) {
            if (elitePercentage < 0.0 || elitePercentage > 1.0) {
                throw new IllegalArgumentException("Elite percentage must be between 0.0 and 1.0");
            }
            this.elitismType = ElitismType.PERCENTAGE;
            this.elitePercentage = elitePercentage;
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
         * Sets the logging interval (print status every N generations).
         *
         * @param logInterval Number of generations between log outputs
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
         * @return Configured GAConfiguration
         * @throws IllegalStateException if no objectives are specified
         */
        public GAConfiguration build() {
            if (objectives.isEmpty()) {
                throw new IllegalStateException("At least one objective must be specified");
            }

            // Validate elite count doesn't exceed population
            int actualEliteCount = (elitismType == ElitismType.PERCENTAGE)
                ? Math.max(1, (int) Math.round(populationSize * elitePercentage))
                : eliteCount;

            if (actualEliteCount >= populationSize) {
                throw new IllegalStateException(
                    "Elite count (" + actualEliteCount + ") must be less than population size (" + populationSize + ")");
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

            return new GAConfiguration(this);
        }
    }
}
