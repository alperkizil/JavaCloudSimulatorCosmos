package com.cloudsimulator.PlacementStrategy.task.metaheuristic.selection;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.List;

/**
 * Tournament selection operator for genetic algorithms.
 *
 * Tournament selection works by:
 * 1. Randomly selecting k individuals from the population
 * 2. Returning the individual with the best fitness among the selected
 *
 * Tournament size (k) controls selection pressure:
 * - k=2: Low selection pressure, more diversity preserved
 * - k=3-5: Moderate selection pressure
 * - k>5: High selection pressure, faster convergence but risk of premature convergence
 *
 * Uses the simulator's RandomGenerator for reproducibility.
 */
public class TournamentSelection implements SelectionOperator {

    private final int tournamentSize;
    private final RandomGenerator random;

    /**
     * Creates a tournament selection operator with specified tournament size.
     *
     * @param tournamentSize Number of individuals in each tournament (must be >= 2)
     */
    public TournamentSelection(int tournamentSize) {
        if (tournamentSize < 2) {
            throw new IllegalArgumentException("Tournament size must be at least 2");
        }
        this.tournamentSize = tournamentSize;
        this.random = RandomGenerator.getInstance();
    }

    /**
     * Creates a tournament selection operator with default size of 2 (binary tournament).
     */
    public TournamentSelection() {
        this(2);
    }

    @Override
    public SchedulingSolution select(List<SchedulingSolution> population,
                                      double[] fitnessValues,
                                      boolean isMinimization) {
        if (population.isEmpty()) {
            throw new IllegalArgumentException("Population cannot be empty");
        }

        if (population.size() != fitnessValues.length) {
            throw new IllegalArgumentException(
                "Population size must match fitness values length");
        }

        int popSize = population.size();

        // Handle small populations
        int actualTournamentSize = Math.min(tournamentSize, popSize);

        // Select tournament participants
        int bestIdx = random.nextInt(popSize);
        double bestFitness = fitnessValues[bestIdx];

        for (int i = 1; i < actualTournamentSize; i++) {
            int candidateIdx = random.nextInt(popSize);
            double candidateFitness = fitnessValues[candidateIdx];

            boolean isBetter = isMinimization
                ? candidateFitness < bestFitness
                : candidateFitness > bestFitness;

            if (isBetter) {
                bestIdx = candidateIdx;
                bestFitness = candidateFitness;
            }
        }

        // Return a copy to avoid modifying the original
        return population.get(bestIdx).copy();
    }

    /**
     * Gets the tournament size.
     *
     * @return Tournament size
     */
    public int getTournamentSize() {
        return tournamentSize;
    }

    @Override
    public String getName() {
        return "Tournament Selection (k=" + tournamentSize + ")";
    }

    @Override
    public String getDescription() {
        return "Selects the best individual from " + tournamentSize +
            " randomly chosen candidates. Higher tournament size increases " +
            "selection pressure toward better individuals.";
    }
}
