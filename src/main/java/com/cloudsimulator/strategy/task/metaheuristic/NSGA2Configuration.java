package com.cloudsimulator.strategy.task.metaheuristic;

import com.cloudsimulator.strategy.task.metaheuristic.operators.CrossoverOperator;
import com.cloudsimulator.strategy.task.metaheuristic.termination.GenerationCountTermination;
import com.cloudsimulator.strategy.task.metaheuristic.termination.TerminationCondition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for the NSGA-II algorithm.
 *
 * Provides a builder pattern for easy and flexible configuration.
 * All parameters have sensible defaults.
 *
 * Example usage:
 * <pre>
 * NSGA2Configuration config = NSGA2Configuration.builder()
 *     .populationSize(150)
 *     .crossoverRate(0.9)
 *     .mutationRate(0.1)
 *     .addObjective(new MakespanObjective())
 *     .addObjective(new EnergyObjective())
 *     .terminationCondition(new GenerationCountTermination(200))
 *     .randomSeed(42L)
 *     .build();
 * </pre>
 */
public class NSGA2Configuration {

    // Population parameters
    private final int populationSize;

    // Genetic operator parameters
    private final double crossoverRate;
    private final double mutationRate;
    private final CrossoverOperator.CrossoverType crossoverType;

    // Objectives
    private final List<SchedulingObjective> objectives;

    // Termination
    private final TerminationCondition terminationCondition;

    // Randomness
    private final Long randomSeed;

    // Logging
    private final boolean verboseLogging;
    private final int logInterval;

    /**
     * Private constructor - use Builder.
     */
    private NSGA2Configuration(Builder builder) {
        this.populationSize = builder.populationSize;
        this.crossoverRate = builder.crossoverRate;
        this.mutationRate = builder.mutationRate;
        this.crossoverType = builder.crossoverType;
        this.objectives = new ArrayList<>(builder.objectives);
        this.terminationCondition = builder.terminationCondition;
        this.randomSeed = builder.randomSeed;
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
     * Creates a default configuration with specified objectives.
     *
     * @param objectives Objectives to optimize
     * @return Default configuration
     */
    public static NSGA2Configuration defaultConfig(SchedulingObjective... objectives) {
        return builder()
            .objectives(Arrays.asList(objectives))
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

    public List<SchedulingObjective> getObjectives() {
        return objectives;
    }

    public int getNumObjectives() {
        return objectives.size();
    }

    public TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }

    public Long getRandomSeed() {
        return randomSeed;
    }

    public boolean hasRandomSeed() {
        return randomSeed != null;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public int getLogInterval() {
        return logInterval;
    }

    /**
     * Gets an array indicating which objectives are minimization.
     *
     * @return Boolean array where true = minimize, false = maximize
     */
    public boolean[] getMinimizationArray() {
        boolean[] result = new boolean[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            result[i] = objectives.get(i).isMinimization();
        }
        return result;
    }

    /**
     * Gets the names of all objectives.
     *
     * @return List of objective names
     */
    public List<String> getObjectiveNames() {
        List<String> names = new ArrayList<>();
        for (SchedulingObjective obj : objectives) {
            names.add(obj.getName());
        }
        return names;
    }

    @Override
    public String toString() {
        return "NSGA2Configuration{" +
            "populationSize=" + populationSize +
            ", crossoverRate=" + crossoverRate +
            ", mutationRate=" + mutationRate +
            ", crossoverType=" + crossoverType +
            ", objectives=" + getObjectiveNames() +
            ", terminationCondition=" + terminationCondition.getDescription() +
            ", randomSeed=" + randomSeed +
            '}';
    }

    /**
     * Builder for NSGA2Configuration.
     */
    public static class Builder {
        private int populationSize = 100;
        private double crossoverRate = 0.9;
        private double mutationRate = 0.1;
        private CrossoverOperator.CrossoverType crossoverType = CrossoverOperator.CrossoverType.UNIFORM;
        private List<SchedulingObjective> objectives = new ArrayList<>();
        private TerminationCondition terminationCondition = new GenerationCountTermination(100);
        private Long randomSeed = null;
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
         * Adds an objective to optimize.
         *
         * @param objective Objective to add
         * @return this builder
         */
        public Builder addObjective(SchedulingObjective objective) {
            this.objectives.add(objective);
            return this;
        }

        /**
         * Sets all objectives to optimize.
         *
         * @param objectives List of objectives
         * @return this builder
         */
        public Builder objectives(List<SchedulingObjective> objectives) {
            this.objectives = new ArrayList<>(objectives);
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
         * Sets the random seed for reproducibility.
         *
         * @param randomSeed Seed value
         * @return this builder
         */
        public Builder randomSeed(long randomSeed) {
            this.randomSeed = randomSeed;
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
            this.logInterval = logInterval;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return Configured NSGA2Configuration
         * @throws IllegalStateException if no objectives are specified
         */
        public NSGA2Configuration build() {
            if (objectives.isEmpty()) {
                throw new IllegalStateException("At least one objective must be specified");
            }
            return new NSGA2Configuration(this);
        }
    }
}
