package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.core.Algorithm;

/**
 * MOEA Framework NSGA-II implementation for task scheduling.
 *
 * NSGA-II (Non-dominated Sorting Genetic Algorithm II) is a popular
 * multi-objective evolutionary algorithm that uses:
 * - Non-dominated sorting: Partitions population into Pareto fronts
 * - Crowding distance: Diversity preservation metric
 * - Binary tournament selection: Based on rank and crowding distance
 * - Elitism: Combines parent and offspring populations
 *
 * Reference: Deb et al. (2002) "A Fast and Elitist Multiobjective
 * Genetic Algorithm: NSGA-II"
 *
 * This implementation wraps MOEA Framework's NSGA-II and uses our
 * simulator's RandomGenerator seed for experiment reproducibility.
 */
public class MOEA_NSGAII extends AbstractMOEAStrategy {

    /**
     * Creates a new MOEA_NSGAII strategy with the given configuration.
     *
     * @param config Configuration parameters
     */
    public MOEA_NSGAII(MOEAConfiguration config) {
        super(config);
    }

    /**
     * Creates a MOEA_NSGAII strategy with default configuration.
     */
    public MOEA_NSGAII() {
        super(MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.NSGA2)
            .build());
    }

    @Override
    protected String getAlgorithmName() {
        return "MOEA_NSGAII";
    }

    @Override
    public String getStrategyName() {
        return "MOEA_NSGAII";
    }

    @Override
    protected Algorithm createAlgorithm(TaskSchedulingProblem problem) {
        // Use simple constructor - MOEA Framework auto-selects appropriate
        // operators based on the variable types in the problem
        NSGAII algorithm = new NSGAII(problem);

        // Configure population size via the algorithm's configuration
        algorithm.setInitialPopulationSize(config.getPopulationSize());

        return algorithm;
    }

    @Override
    public String getDescription() {
        return "MOEA Framework NSGA-II: Non-dominated Sorting Genetic Algorithm II " +
               "with crowding distance diversity preservation. " +
               "Population=" + config.getPopulationSize() +
               ", MaxEvaluations=" + config.getMaxEvaluations() +
               ", Selection=" + config.getSolutionSelection();
    }
}
