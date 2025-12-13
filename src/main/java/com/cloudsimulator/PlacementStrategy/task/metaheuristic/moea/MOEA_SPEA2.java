package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import org.moeaframework.algorithm.SPEA2;
import org.moeaframework.core.Algorithm;

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
        // Use simple constructor - MOEA Framework auto-selects appropriate
        // operators based on the variable types in the problem
        SPEA2 algorithm = new SPEA2(problem);

        // Configure population size
        algorithm.setInitialPopulationSize(config.getPopulationSize());

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
