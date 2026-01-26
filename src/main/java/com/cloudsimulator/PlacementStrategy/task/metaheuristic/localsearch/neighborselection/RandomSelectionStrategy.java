package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.neighborselection;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.NeighborSelectionStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.NeighborhoodGenerator;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Random Selection neighbor selection strategy.
 *
 * This strategy identifies all improving neighbors and then randomly
 * selects one among them. This adds stochasticity to the local search,
 * which can help explore different regions of the search space.
 *
 * Characteristics:
 * - Evaluates all neighbors to find improving ones
 * - Randomly selects among the improving set
 * - Adds diversity to the search trajectory
 * - Non-deterministic: different runs may produce different results
 * - Same computational cost as Best Improvement in terms of evaluations
 *
 * Reference: Talbi, E-G. "Metaheuristics: From Design to Implementation", Chapter 2
 */
public class RandomSelectionStrategy implements NeighborSelectionStrategy {

    private final RandomGenerator random;
    private int lastEvaluationCount = 0;

    /**
     * Creates a RandomSelectionStrategy using the global RandomGenerator.
     */
    public RandomSelectionStrategy() {
        this.random = RandomGenerator.getInstance();
    }

    /**
     * Creates a RandomSelectionStrategy with a specific random generator.
     *
     * @param random Random number generator for reproducibility
     */
    public RandomSelectionStrategy(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public Optional<SchedulingSolution> selectNeighbor(
            SchedulingSolution current,
            NeighborhoodGenerator neighborhood,
            ToDoubleFunction<SchedulingSolution> evaluator,
            boolean isMinimization) {

        double currentFitness = evaluator.applyAsDouble(current);
        List<SchedulingSolution> neighbors = neighborhood.generateAll(current);

        lastEvaluationCount = neighbors.size();

        // Collect all improving neighbors
        List<SchedulingSolution> improvingNeighbors = new ArrayList<>();

        for (SchedulingSolution neighbor : neighbors) {
            double neighborFitness = evaluator.applyAsDouble(neighbor);
            if (isImproving(neighborFitness, currentFitness, isMinimization)) {
                improvingNeighbors.add(neighbor);
            }
        }

        if (improvingNeighbors.isEmpty()) {
            return Optional.empty();
        }

        // Randomly select one improving neighbor
        int selectedIndex = random.nextInt(improvingNeighbors.size());
        return Optional.of(improvingNeighbors.get(selectedIndex));
    }

    @Override
    public String getName() {
        return "Random Selection";
    }

    @Override
    public int getLastEvaluationCount() {
        return lastEvaluationCount;
    }
}
