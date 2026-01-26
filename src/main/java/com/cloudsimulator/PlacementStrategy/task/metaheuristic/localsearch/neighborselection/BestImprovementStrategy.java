package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.neighborselection;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.NeighborSelectionStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.NeighborhoodGenerator;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Best Improvement (Steepest Descent) neighbor selection strategy.
 *
 * This strategy evaluates ALL neighbors in the neighborhood and selects
 * the one that provides the greatest improvement in the objective function.
 *
 * Characteristics:
 * - Exhaustive exploration of the neighborhood
 * - Deterministic: always selects the best improving neighbor
 * - Time-consuming for large neighborhoods
 * - Guaranteed to find the best local move at each iteration
 *
 * Reference: Talbi, E-G. "Metaheuristics: From Design to Implementation", Chapter 2
 */
public class BestImprovementStrategy implements NeighborSelectionStrategy {

    private int lastEvaluationCount = 0;

    @Override
    public Optional<SchedulingSolution> selectNeighbor(
            SchedulingSolution current,
            NeighborhoodGenerator neighborhood,
            ToDoubleFunction<SchedulingSolution> evaluator,
            boolean isMinimization) {

        double currentFitness = evaluator.applyAsDouble(current);
        List<SchedulingSolution> neighbors = neighborhood.generateAll(current);

        lastEvaluationCount = neighbors.size();

        SchedulingSolution bestNeighbor = null;
        double bestFitness = currentFitness;

        for (SchedulingSolution neighbor : neighbors) {
            double neighborFitness = evaluator.applyAsDouble(neighbor);

            if (isImproving(neighborFitness, currentFitness, isMinimization)) {
                if (bestNeighbor == null || isBetter(neighborFitness, bestFitness, isMinimization)) {
                    bestNeighbor = neighbor;
                    bestFitness = neighborFitness;
                }
            }
        }

        return Optional.ofNullable(bestNeighbor);
    }

    @Override
    public String getName() {
        return "Best Improvement (Steepest Descent)";
    }

    @Override
    public int getLastEvaluationCount() {
        return lastEvaluationCount;
    }
}
