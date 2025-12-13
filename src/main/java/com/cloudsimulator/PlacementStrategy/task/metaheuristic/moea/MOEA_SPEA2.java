package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import org.moeaframework.algorithm.SPEA2;
import org.moeaframework.core.Algorithm;
import org.moeaframework.core.Initialization;
import org.moeaframework.core.Variation;
import org.moeaframework.core.initialization.RandomInitialization;
import org.moeaframework.core.operator.integer.PM as IntegerPM;
import org.moeaframework.core.operator.integer.SBX as IntegerSBX;

/**
 * MOEA Framework SPEA2 implementation for task scheduling.
 *
 * SPEA2 (Strength Pareto Evolutionary Algorithm 2) is a multi-objective
 * evolutionary algorithm that uses:
 * - Strength-based fitness: Each solution's fitness is based on the number
 *   of solutions it dominates and is dominated by
 * - Fine-grained density estimation: Uses k-th nearest neighbor distance
 *   for better diversity preservation than crowding distance
 * - External archive: Maintains non-dominated solutions across generations
 * - Environmental selection: Truncates archive using density information
 *
 * SPEA2 typically provides better diversity preservation compared to NSGA-II,
 * especially for problems with more than two objectives.
 *
 * Reference: Zitzler et al. (2001) "SPEA2: Improving the Strength Pareto
 * Evolutionary Algorithm"
 *
 * This implementation wraps MOEA Framework's SPEA2 and uses our
 * simulator's RandomGenerator seed for experiment reproducibility.
 */
public class MOEA_SPEA2 extends AbstractMOEAStrategy {

    /**
     * Creates a new MOEA_SPEA2 strategy with the given configuration.
     *
     * @param config Configuration parameters
     */
    public MOEA_SPEA2(MOEAConfiguration config) {
        super(config);
    }

    /**
     * Creates a MOEA_SPEA2 strategy with default configuration.
     */
    public MOEA_SPEA2() {
        super(MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.SPEA2)
            .build());
    }

    @Override
    protected String getAlgorithmName() {
        return "MOEA_SPEA2";
    }

    @Override
    public String getStrategyName() {
        return "MOEA_SPEA2";
    }

    @Override
    protected Algorithm createAlgorithm(TaskSchedulingProblem problem) {
        // Create variation operators for integer variables
        Variation variation = new org.moeaframework.core.operator.CompoundVariation(
            new IntegerSBX(config.getCrossoverRate(), 15.0),
            new IntegerPM(config.getMutationRate(), 20.0)
        );

        // Create initialization
        Initialization initialization = new RandomInitialization(problem);

        // Calculate offspring size (typically equal to population size)
        int offspringSize = config.getPopulationSize();

        // Determine k for k-th nearest neighbor (sqrt of archive size is common)
        int k = (int) Math.sqrt(config.getSpea2ArchiveSize());
        if (k < 1) k = 1;

        // Create SPEA2 algorithm
        SPEA2 algorithm = new SPEA2(
            problem,
            initialization,
            variation,
            offspringSize,
            config.getSpea2ArchiveSize()
        );

        return algorithm;
    }

    @Override
    public String getDescription() {
        return "MOEA Framework SPEA2: Strength Pareto Evolutionary Algorithm 2 " +
               "with fine-grained density estimation and external archive. " +
               "Population=" + config.getPopulationSize() +
               ", ArchiveSize=" + config.getSpea2ArchiveSize() +
               ", MaxEvaluations=" + config.getMaxEvaluations() +
               ", Selection=" + config.getSolutionSelection();
    }
}
