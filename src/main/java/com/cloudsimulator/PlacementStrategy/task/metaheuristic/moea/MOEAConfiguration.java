package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.MakespanObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for MOEA Framework-based task scheduling strategies.
 *
 * Supports configuration of:
 * - Common evolutionary algorithm parameters (population size, crossover/mutation rates)
 * - Algorithm-specific parameters (epsilon values, temperature schedules)
 * - Solution selection method from Pareto front
 * - Logging and debugging options
 *
 * Uses Builder pattern for fluent configuration.
 */
public class MOEAConfiguration {

    /**
     * Supported MOEA Framework algorithms.
     */
    public enum Algorithm {
        /** Non-dominated Sorting Genetic Algorithm II */
        NSGA2,
        /** Strength Pareto Evolutionary Algorithm 2 */
        SPEA2,
        /** Epsilon-dominance MOEA */
        EPSILON_MOEA,
        /** Archived Multi-Objective Simulated Annealing */
        AMOSA
    }

    /**
     * Methods for selecting a single solution from the Pareto front.
     */
    public enum SolutionSelection {
        /** Select the knee point (balanced trade-off) */
        KNEE_POINT,
        /** Select the solution with best value for first objective (typically Makespan) */
        BEST_FIRST_OBJECTIVE,
        /** Select the solution with best value for second objective (typically Energy) */
        BEST_SECOND_OBJECTIVE,
        /** Select using weighted sum of normalized objectives */
        WEIGHTED_SUM,
        /** Select the first solution in the Pareto front */
        FIRST
    }

    // Algorithm selection
    private final Algorithm algorithm;

    // Common evolutionary parameters
    private final int populationSize;
    private final int maxEvaluations;
    private final double crossoverRate;
    private final double mutationRate;

    // Objectives
    private final List<SchedulingObjective> objectives;

    // Solution selection
    private final SolutionSelection solutionSelection;
    private final double[] selectionWeights;

    // Algorithm-specific: ε-MOEA
    private final double[] epsilons;

    // Algorithm-specific: AMOSA
    private final double initialTemperature;
    private final double finalTemperature;
    private final double coolingRate;
    private final int archiveSize;
    private final int iterationsPerTemperature;

    // Algorithm-specific: SPEA2
    private final int spea2ArchiveSize;

    // Logging
    private final boolean verboseLogging;
    private final int logInterval;

    private MOEAConfiguration(Builder builder) {
        this.algorithm = builder.algorithm;
        this.populationSize = builder.populationSize;
        this.maxEvaluations = builder.maxEvaluations;
        this.crossoverRate = builder.crossoverRate;
        this.mutationRate = builder.mutationRate;
        this.objectives = new ArrayList<>(builder.objectives);
        this.solutionSelection = builder.solutionSelection;
        this.selectionWeights = builder.selectionWeights != null ? builder.selectionWeights.clone() : null;
        this.epsilons = builder.epsilons != null ? builder.epsilons.clone() : null;
        this.initialTemperature = builder.initialTemperature;
        this.finalTemperature = builder.finalTemperature;
        this.coolingRate = builder.coolingRate;
        this.archiveSize = builder.archiveSize;
        this.iterationsPerTemperature = builder.iterationsPerTemperature;
        this.spea2ArchiveSize = builder.spea2ArchiveSize;
        this.verboseLogging = builder.verboseLogging;
        this.logInterval = builder.logInterval;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public Algorithm getAlgorithm() { return algorithm; }
    public int getPopulationSize() { return populationSize; }
    public int getMaxEvaluations() { return maxEvaluations; }
    public double getCrossoverRate() { return crossoverRate; }
    public double getMutationRate() { return mutationRate; }
    public List<SchedulingObjective> getObjectives() { return new ArrayList<>(objectives); }
    public SolutionSelection getSolutionSelection() { return solutionSelection; }
    public double[] getSelectionWeights() { return selectionWeights != null ? selectionWeights.clone() : null; }
    public double[] getEpsilons() { return epsilons != null ? epsilons.clone() : null; }
    public double getInitialTemperature() { return initialTemperature; }
    public double getFinalTemperature() { return finalTemperature; }
    public double getCoolingRate() { return coolingRate; }
    public int getArchiveSize() { return archiveSize; }
    public int getIterationsPerTemperature() { return iterationsPerTemperature; }
    public int getSpea2ArchiveSize() { return spea2ArchiveSize; }
    public boolean isVerboseLogging() { return verboseLogging; }
    public int getLogInterval() { return logInterval; }

    /**
     * Builder for MOEAConfiguration with sensible defaults.
     */
    public static class Builder {
        // Defaults
        private Algorithm algorithm = Algorithm.NSGA2;
        private int populationSize = 100;
        private int maxEvaluations = 10000;
        private double crossoverRate = 0.9;
        private double mutationRate = 0.1;
        private List<SchedulingObjective> objectives = new ArrayList<>();
        private SolutionSelection solutionSelection = SolutionSelection.KNEE_POINT;
        private double[] selectionWeights = null;

        // ε-MOEA defaults (epsilons for Makespan and Energy)
        private double[] epsilons = new double[]{10.0, 0.001}; // 10 seconds, 0.001 kWh

        // AMOSA defaults (sensible for scheduling problems)
        private double initialTemperature = 1000.0;
        private double finalTemperature = 0.01;
        private double coolingRate = 0.9;
        private int archiveSize = 100;
        private int iterationsPerTemperature = 100;

        // SPEA2 defaults
        private int spea2ArchiveSize = 100;

        // Logging defaults
        private boolean verboseLogging = false;
        private int logInterval = 100;

        public Builder() {
            // Add default objectives
            objectives.add(new MakespanObjective());
            objectives.add(new EnergyObjective());
        }

        public Builder algorithm(Algorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder populationSize(int populationSize) {
            if (populationSize < 10) {
                throw new IllegalArgumentException("Population size must be at least 10");
            }
            this.populationSize = populationSize;
            return this;
        }

        public Builder maxEvaluations(int maxEvaluations) {
            if (maxEvaluations < 100) {
                throw new IllegalArgumentException("Max evaluations must be at least 100");
            }
            this.maxEvaluations = maxEvaluations;
            return this;
        }

        public Builder crossoverRate(double crossoverRate) {
            if (crossoverRate < 0.0 || crossoverRate > 1.0) {
                throw new IllegalArgumentException("Crossover rate must be between 0.0 and 1.0");
            }
            this.crossoverRate = crossoverRate;
            return this;
        }

        public Builder mutationRate(double mutationRate) {
            if (mutationRate < 0.0 || mutationRate > 1.0) {
                throw new IllegalArgumentException("Mutation rate must be between 0.0 and 1.0");
            }
            this.mutationRate = mutationRate;
            return this;
        }

        /**
         * Sets the objectives to optimize. Clears default objectives.
         */
        public Builder objectives(List<SchedulingObjective> objectives) {
            this.objectives = new ArrayList<>(objectives);
            return this;
        }

        /**
         * Adds an objective to the list.
         */
        public Builder addObjective(SchedulingObjective objective) {
            this.objectives.add(objective);
            return this;
        }

        /**
         * Clears all objectives.
         */
        public Builder clearObjectives() {
            this.objectives.clear();
            return this;
        }

        public Builder solutionSelection(SolutionSelection solutionSelection) {
            this.solutionSelection = solutionSelection;
            return this;
        }

        /**
         * Sets weights for WEIGHTED_SUM solution selection.
         * Weights should sum to 1.0 and have same length as objectives.
         */
        public Builder selectionWeights(double... weights) {
            this.selectionWeights = weights.clone();
            return this;
        }

        /**
         * Sets epsilon values for ε-MOEA.
         * Each epsilon defines the resolution for each objective.
         * Smaller values = finer granularity in Pareto front.
         *
         * @param epsilons Epsilon values for each objective
         */
        public Builder epsilons(double... epsilons) {
            this.epsilons = epsilons.clone();
            return this;
        }

        /**
         * Sets AMOSA initial temperature.
         * Higher temperature = more exploration initially.
         * Default: 1000.0
         */
        public Builder initialTemperature(double initialTemperature) {
            if (initialTemperature <= 0) {
                throw new IllegalArgumentException("Initial temperature must be positive");
            }
            this.initialTemperature = initialTemperature;
            return this;
        }

        /**
         * Sets AMOSA final temperature.
         * Algorithm stops when temperature falls below this.
         * Default: 0.01
         */
        public Builder finalTemperature(double finalTemperature) {
            if (finalTemperature <= 0) {
                throw new IllegalArgumentException("Final temperature must be positive");
            }
            this.finalTemperature = finalTemperature;
            return this;
        }

        /**
         * Sets AMOSA cooling rate.
         * Temperature decreases by this factor each cooling step.
         * Default: 0.9 (temperature reduced by 10% each step)
         */
        public Builder coolingRate(double coolingRate) {
            if (coolingRate <= 0 || coolingRate >= 1.0) {
                throw new IllegalArgumentException("Cooling rate must be between 0 and 1 (exclusive)");
            }
            this.coolingRate = coolingRate;
            return this;
        }

        /**
         * Sets AMOSA/SPEA2 archive size.
         * Maximum number of non-dominated solutions to maintain.
         * Default: 100
         */
        public Builder archiveSize(int archiveSize) {
            if (archiveSize < 10) {
                throw new IllegalArgumentException("Archive size must be at least 10");
            }
            this.archiveSize = archiveSize;
            this.spea2ArchiveSize = archiveSize;
            return this;
        }

        /**
         * Sets AMOSA iterations per temperature level.
         * More iterations = more thorough search at each temperature.
         * Default: 100
         */
        public Builder iterationsPerTemperature(int iterations) {
            if (iterations < 1) {
                throw new IllegalArgumentException("Iterations per temperature must be at least 1");
            }
            this.iterationsPerTemperature = iterations;
            return this;
        }

        /**
         * Sets SPEA2 archive size separately from AMOSA.
         */
        public Builder spea2ArchiveSize(int size) {
            if (size < 10) {
                throw new IllegalArgumentException("SPEA2 archive size must be at least 10");
            }
            this.spea2ArchiveSize = size;
            return this;
        }

        public Builder verboseLogging(boolean verbose) {
            this.verboseLogging = verbose;
            return this;
        }

        public Builder logInterval(int interval) {
            this.logInterval = interval;
            return this;
        }

        public MOEAConfiguration build() {
            if (objectives.isEmpty()) {
                throw new IllegalStateException("At least one objective must be configured");
            }

            // Validate epsilon count matches objectives for ε-MOEA
            if (algorithm == Algorithm.EPSILON_MOEA && epsilons.length != objectives.size()) {
                throw new IllegalStateException(
                    "Number of epsilon values (" + epsilons.length +
                    ") must match number of objectives (" + objectives.size() + ")");
            }

            // Validate weights for WEIGHTED_SUM selection
            if (solutionSelection == SolutionSelection.WEIGHTED_SUM) {
                if (selectionWeights == null || selectionWeights.length != objectives.size()) {
                    throw new IllegalStateException(
                        "Selection weights must be provided and match number of objectives for WEIGHTED_SUM selection");
                }
            }

            return new MOEAConfiguration(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MOEAConfiguration{\n");
        sb.append("  algorithm=").append(algorithm).append("\n");
        sb.append("  populationSize=").append(populationSize).append("\n");
        sb.append("  maxEvaluations=").append(maxEvaluations).append("\n");
        sb.append("  crossoverRate=").append(crossoverRate).append("\n");
        sb.append("  mutationRate=").append(mutationRate).append("\n");
        sb.append("  objectives=").append(objectives.size()).append(" [");
        for (int i = 0; i < objectives.size(); i++) {
            sb.append(objectives.get(i).getName());
            if (i < objectives.size() - 1) sb.append(", ");
        }
        sb.append("]\n");
        sb.append("  solutionSelection=").append(solutionSelection).append("\n");

        if (algorithm == Algorithm.EPSILON_MOEA) {
            sb.append("  epsilons=").append(java.util.Arrays.toString(epsilons)).append("\n");
        }
        if (algorithm == Algorithm.AMOSA) {
            sb.append("  initialTemperature=").append(initialTemperature).append("\n");
            sb.append("  finalTemperature=").append(finalTemperature).append("\n");
            sb.append("  coolingRate=").append(coolingRate).append("\n");
            sb.append("  archiveSize=").append(archiveSize).append("\n");
            sb.append("  iterationsPerTemperature=").append(iterationsPerTemperature).append("\n");
        }
        if (algorithm == Algorithm.SPEA2) {
            sb.append("  spea2ArchiveSize=").append(spea2ArchiveSize).append("\n");
        }

        sb.append("}");
        return sb.toString();
    }
}
