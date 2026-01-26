package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.neighborselection;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.NeighborSelectionStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch.NeighborhoodGenerator;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * First Improvement neighbor selection strategy.
 *
 * This strategy iterates through the neighborhood and selects the FIRST
 * neighbor that improves the current solution. The exploration is cyclic,
 * meaning it remembers where it left off and continues from that position
 * in subsequent iterations.
 *
 * Characteristics:
 * - Partial evaluation of the neighborhood (stops at first improvement)
 * - Faster average case than Best Improvement
 * - May miss better moves within the same neighborhood
 * - Cyclic exploration ensures all neighbors are eventually considered
 * - In worst case (no improvement), evaluates entire neighborhood
 *
 * Reference: Talbi, E-G. "Metaheuristics: From Design to Implementation", Chapter 2
 */
public class FirstImprovementStrategy implements NeighborSelectionStrategy {

    private int lastEvaluationCount = 0;
    private int startIndex = 0; // For cyclic exploration

    @Override
    public Optional<SchedulingSolution> selectNeighbor(
            SchedulingSolution current,
            NeighborhoodGenerator neighborhood,
            ToDoubleFunction<SchedulingSolution> evaluator,
            boolean isMinimization) {

        double currentFitness = evaluator.applyAsDouble(current);
        Iterator<SchedulingSolution> iterator = neighborhood.generateIterator(current);

        lastEvaluationCount = 0;
        int neighborhoodSize = neighborhood.getNeighborhoodSize(current);

        if (neighborhoodSize == 0) {
            return Optional.empty();
        }

        // Skip to the start index for cyclic exploration
        int skipped = 0;
        while (skipped < startIndex && iterator.hasNext()) {
            iterator.next();
            skipped++;
        }

        // Evaluate from startIndex to end
        int evaluated = 0;
        while (iterator.hasNext()) {
            SchedulingSolution neighbor = iterator.next();
            lastEvaluationCount++;
            evaluated++;

            double neighborFitness = evaluator.applyAsDouble(neighbor);
            if (isImproving(neighborFitness, currentFitness, isMinimization)) {
                // Update start index for next iteration (cyclic)
                startIndex = (startIndex + evaluated) % neighborhoodSize;
                return Optional.of(neighbor);
            }
        }

        // Wrap around: evaluate from beginning to startIndex
        if (startIndex > 0) {
            iterator = neighborhood.generateIterator(current);
            int wrapCount = 0;
            while (wrapCount < startIndex && iterator.hasNext()) {
                SchedulingSolution neighbor = iterator.next();
                lastEvaluationCount++;
                wrapCount++;

                double neighborFitness = evaluator.applyAsDouble(neighbor);
                if (isImproving(neighborFitness, currentFitness, isMinimization)) {
                    // Update start index for next iteration
                    startIndex = wrapCount % neighborhoodSize;
                    return Optional.of(neighbor);
                }
            }
        }

        // No improving neighbor found - reset start index for next call
        startIndex = 0;
        return Optional.empty();
    }

    @Override
    public String getName() {
        return "First Improvement";
    }

    @Override
    public int getLastEvaluationCount() {
        return lastEvaluationCount;
    }

    /**
     * Resets the cyclic exploration to start from the beginning.
     * Useful when the solution structure changes significantly.
     */
    public void resetCycle() {
        startIndex = 0;
    }
}
