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

    // Neighbor generation
    private final MutationOperator.MutationType neighborType;

    // Objectives with weights (for weighted sum)
    private final List<SchedulingObjective> objectives;
    private final Map<SchedulingObjective, Double> objectiveWeights;

    // Termination
    private final TerminationCondition terminationCondition;

    // Reheating parameters
    private final boolean reheatEnabled;
    private final double reheatFactor;
    private final int reheatStagnationThreshold;
    private final int maxReheats;

    // Adaptive iterations-per-temperature
    private final boolean adaptiveIterationsEnabled;
    private final int minIterationsPerTemperature;
    private final int maxIterationsPerTemperature;
    private final double adaptiveIterHighAcceptanceThreshold;
    private final double adaptiveIterLowAcceptanceThreshold;

    // Temperature-scaled perturbation
    private final boolean temperatureScaledPerturbation;
    private final int maxPerturbationMutations;

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
        this.neighborType = builder.neighborType;
        this.objectives = new ArrayList<>(builder.objectives);
        this.objectiveWeights = new LinkedHashMap<>(builder.objectiveWeights);
        this.terminationCondition = builder.terminationCondition;
        this.reheatEnabled = builder.reheatEnabled;
        this.reheatFactor = builder.reheatFactor;
        this.reheatStagnationThreshold = builder.reheatStagnationThreshold;
        this.maxReheats = builder.maxReheats;
        this.adaptiveIterationsEnabled = builder.adaptiveIterationsEnabled;
        this.minIterationsPerTemperature = builder.minIterationsPerTemperature;
        this.maxIterationsPerTemperature = builder.maxIterationsPerTemperature;
        this.adaptiveIterHighAcceptanceThreshold = builder.adaptiveIterHighAcceptanceThreshold;
        this.adaptiveIterLowAcceptanceThreshold = builder.adaptiveIterLowAcceptanceThreshold;
        this.temperatureScaledPerturbation = builder.temperatureScaledPerturbation;
        this.maxPerturbationMutations = builder.maxPerturbationMutations;
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

    // Reheating getters

    public boolean isReheatEnabled() {
        return reheatEnabled;
    }

    public double getReheatFactor() {
        return reheatFactor;
    }

    public int getReheatStagnationThreshold() {
        return reheatStagnationThreshold;
    }

    public int getMaxReheats() {
        return maxReheats;
    }

    // Adaptive iterations getters

    public boolean isAdaptiveIterationsEnabled() {
        return adaptiveIterationsEnabled;
    }

    public int getMinIterationsPerTemperature() {
        return minIterationsPerTemperature;
    }

    public int getMaxIterationsPerTemperature() {
        return maxIterationsPerTemperature;
    }

    public double getAdaptiveIterHighAcceptanceThreshold() {
        return adaptiveIterHighAcceptanceThreshold;
    }

    public double getAdaptiveIterLowAcceptanceThreshold() {
        return adaptiveIterLowAcceptanceThreshold;
    }

    // Temperature-scaled perturbation getters

    public boolean isTemperatureScaledPerturbation() {
        return temperatureScaledPerturbation;
    }

    public int getMaxPerturbationMutations() {
        return maxPerturbationMutations;
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
        if (reheatEnabled) {
            sb.append(", reheat=factor:").append(reheatFactor)
              .append("/threshold:").append(reheatStagnationThreshold)
              .append("/max:").append(maxReheats);
        }
        if (adaptiveIterationsEnabled) {
            sb.append(", adaptiveIters=[").append(minIterationsPerTemperature)
              .append("-").append(maxIterationsPerTemperature).append("]");
        }
        if (temperatureScaledPerturbation) {
            sb.append(", scaledPerturb=max:").append(maxPerturbationMutations);
        }
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

        // Neighbor generation default
        private MutationOperator.MutationType neighborType = MutationOperator.MutationType.COMBINED;

        // Objectives
        private List<SchedulingObjective> objectives = new ArrayList<>();
        private Map<SchedulingObjective, Double> objectiveWeights = new LinkedHashMap<>();

        // Termination default (temperature-based)
        private TerminationCondition terminationCondition = null; // Will be set in build()

        // Reheating defaults
        private boolean reheatEnabled = false;
        private double reheatFactor = 5.0;
        private int reheatStagnationThreshold = 15;
        private int maxReheats = 3;

        // Adaptive iterations defaults
        private boolean adaptiveIterationsEnabled = false;
        private int minIterationsPerTemperature = 50;
        private int maxIterationsPerTemperature = 400;
        private double adaptiveIterHighAcceptanceThreshold = 0.7;
        private double adaptiveIterLowAcceptanceThreshold = 0.1;

        // Temperature-scaled perturbation defaults
        private boolean temperatureScaledPerturbation = false;
        private int maxPerturbationMutations = 4;

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
         * Enables reheating when SA stagnates (no improvement for N temperature steps).
         * Reheating multiplies current temperature by reheatFactor to escape local optima.
         *
         * @param enabled true to enable reheating
         * @return this builder
         */
        public Builder reheatEnabled(boolean enabled) {
            this.reheatEnabled = enabled;
            return this;
        }

        /**
         * Sets the reheat factor. Temperature is multiplied by this when reheating.
         *
         * @param factor Reheat multiplier (typically 3-10)
         * @return this builder
         */
        public Builder reheatFactor(double factor) {
            if (factor <= 1.0) {
                throw new IllegalArgumentException("Reheat factor must be > 1.0");
            }
            this.reheatFactor = factor;
            return this;
        }

        /**
         * Sets the number of no-improvement temperature steps before a reheat triggers.
         *
         * @param threshold Stagnation threshold in temperature steps
         * @return this builder
         */
        public Builder reheatStagnationThreshold(int threshold) {
            if (threshold < 1) {
                throw new IllegalArgumentException("Stagnation threshold must be >= 1");
            }
            this.reheatStagnationThreshold = threshold;
            return this;
        }

        /**
         * Sets the maximum number of reheats allowed per run.
         *
         * @param maxReheats Maximum reheats (typically 2-5)
         * @return this builder
         */
        public Builder maxReheats(int maxReheats) {
            if (maxReheats < 1) {
                throw new IllegalArgumentException("Max reheats must be >= 1");
            }
            this.maxReheats = maxReheats;
            return this;
        }

        /**
         * Enables adaptive iterations-per-temperature. Instead of fixed iterations,
         * the count is adjusted based on acceptance rate:
         * - High acceptance (random walk) -> fewer iterations (don't waste budget)
         * - Moderate acceptance (productive) -> more iterations (exploit this range)
         * - Low acceptance (stuck) -> fewer iterations (move to next temperature)
         *
         * @param enabled true to enable adaptive iterations
         * @return this builder
         */
        public Builder adaptiveIterationsEnabled(boolean enabled) {
            this.adaptiveIterationsEnabled = enabled;
            return this;
        }

        /**
         * Sets the min/max bounds for adaptive iterations-per-temperature.
         *
         * @param min Minimum iterations per temperature step
         * @param max Maximum iterations per temperature step
         * @return this builder
         */
        public Builder adaptiveIterationsBounds(int min, int max) {
            if (min < 1 || max < min) {
                throw new IllegalArgumentException("Bounds must satisfy 1 <= min <= max");
            }
            this.minIterationsPerTemperature = min;
            this.maxIterationsPerTemperature = max;
            return this;
        }

        /**
         * Sets the acceptance rate thresholds for adaptive iteration adjustment.
         *
         * @param highThreshold Above this, reduce iterations (random walk phase)
         * @param lowThreshold  Below this, reduce iterations (stuck phase)
         * @return this builder
         */
        public Builder adaptiveIterationsThresholds(double highThreshold, double lowThreshold) {
            if (lowThreshold < 0 || highThreshold > 1.0 || lowThreshold >= highThreshold) {
                throw new IllegalArgumentException(
                    "Thresholds must satisfy 0 <= low < high <= 1.0");
            }
            this.adaptiveIterHighAcceptanceThreshold = highThreshold;
            this.adaptiveIterLowAcceptanceThreshold = lowThreshold;
            return this;
        }

        /**
         * Enables temperature-scaled perturbation. At high temperatures, multiple
         * mutations are applied per neighbor to create larger jumps. At low temperatures,
         * single mutations are used for fine-grained search.
         *
         * @param enabled true to enable temperature-scaled perturbation
         * @return this builder
         */
        public Builder temperatureScaledPerturbation(boolean enabled) {
            this.temperatureScaledPerturbation = enabled;
            return this;
        }

        /**
         * Sets the maximum number of mutations applied per neighbor at the highest temperature.
         * The actual count scales linearly from maxMutations (at T_initial) to 1 (at T_final).
         *
         * @param maxMutations Maximum mutations per neighbor (typically 3-5)
         * @return this builder
         */
        public Builder maxPerturbationMutations(int maxMutations) {
            if (maxMutations < 1) {
                throw new IllegalArgumentException("Max perturbation mutations must be >= 1");
            }
            this.maxPerturbationMutations = maxMutations;
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
