package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.core.Algorithm;
import org.moeaframework.core.Initialization;
import org.moeaframework.core.NondominatedSortingPopulation;
import org.moeaframework.core.Population;
import org.moeaframework.core.Selection;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variation;
import org.moeaframework.core.initialization.RandomInitialization;
import org.moeaframework.core.operator.TournamentSelection;
import org.moeaframework.core.operator.real.PM;
import org.moeaframework.core.operator.real.SBX;
import org.moeaframework.core.operator.integer.PM as IntegerPM;
import org.moeaframework.core.operator.integer.SBX as IntegerSBX;
import org.moeaframework.core.comparator.ChainedComparator;
import org.moeaframework.core.comparator.CrowdingComparator;
import org.moeaframework.core.comparator.ParetoDominanceComparator;

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
        // Create selection operator
        // Uses Pareto dominance comparator followed by crowding distance
        Selection selection = new TournamentSelection(2,
            new ChainedComparator(
                new ParetoDominanceComparator(),
                new CrowdingComparator()
            ));

        // Create variation operators for integer variables
        // SBX crossover and polynomial mutation adapted for integers
        Variation variation = new org.moeaframework.core.operator.CompoundVariation(
            new IntegerSBX(config.getCrossoverRate(), 15.0),
            new IntegerPM(config.getMutationRate(), 20.0)
        );

        // Create initialization
        Initialization initialization = new RandomInitialization(problem);

        // Create NSGA-II algorithm
        NSGAII algorithm = new NSGAII(
            problem,
            config.getPopulationSize(),
            new NondominatedSortingPopulation(),
            null, // Use default archive
            selection,
            variation,
            initialization
        );

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
