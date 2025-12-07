package com.cloudsimulator.strategy.task.metaheuristic.termination;

import com.cloudsimulator.strategy.task.metaheuristic.SchedulingSolution;

import java.util.List;

/**
 * Holds statistics about the current state of the optimization algorithm.
 * Used by TerminationCondition implementations to decide when to stop.
 */
public class AlgorithmStatistics {

    /**
     * Current generation/iteration number (0-indexed).
     */
    private int currentGeneration;

    /**
     * Total number of fitness/objective evaluations performed so far.
     */
    private long totalFitnessEvaluations;

    /**
     * Elapsed time since algorithm start in milliseconds.
     */
    private long elapsedTimeMillis;

    /**
     * Start time of the algorithm in milliseconds (System.currentTimeMillis).
     */
    private long startTimeMillis;

    /**
     * Best objective values found so far (one per objective).
     * For minimization objectives, these are the minimum values.
     * For maximization objectives, these are the maximum values.
     */
    private double[] bestObjectiveValues;

    /**
     * The current best solution found (based on some criteria).
     * For multi-objective, this might be a representative solution from the Pareto front.
     */
    private SchedulingSolution bestSolution;

    /**
     * Current Pareto front (for multi-objective algorithms).
     */
    private List<SchedulingSolution> paretoFront;

    /**
     * Current population size.
     */
    private int populationSize;

    /**
     * Number of non-dominated solutions in current population.
     */
    private int nonDominatedCount;

    /**
     * Creates a new AlgorithmStatistics instance and starts the timer.
     *
     * @param numObjectives Number of objectives being optimized
     */
    public AlgorithmStatistics(int numObjectives) {
        this.currentGeneration = 0;
        this.totalFitnessEvaluations = 0;
        this.startTimeMillis = System.currentTimeMillis();
        this.elapsedTimeMillis = 0;
        this.bestObjectiveValues = new double[numObjectives];

        // Initialize best values to worst possible
        for (int i = 0; i < numObjectives; i++) {
            bestObjectiveValues[i] = Double.MAX_VALUE;
        }
    }

    /**
     * Updates the elapsed time based on current time.
     */
    public void updateElapsedTime() {
        this.elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis;
    }

    /**
     * Increments the generation counter.
     */
    public void incrementGeneration() {
        this.currentGeneration++;
    }

    /**
     * Adds to the total fitness evaluation count.
     *
     * @param count Number of evaluations to add
     */
    public void addFitnessEvaluations(long count) {
        this.totalFitnessEvaluations += count;
    }

    /**
     * Updates best objective values if the new values are better.
     *
     * @param objectiveValues    New objective values to consider
     * @param isMinimization     Array indicating which objectives are minimization
     */
    public void updateBestValues(double[] objectiveValues, boolean[] isMinimization) {
        for (int i = 0; i < objectiveValues.length && i < bestObjectiveValues.length; i++) {
            if (isMinimization[i]) {
                if (objectiveValues[i] < bestObjectiveValues[i]) {
                    bestObjectiveValues[i] = objectiveValues[i];
                }
            } else {
                if (objectiveValues[i] > bestObjectiveValues[i]) {
                    bestObjectiveValues[i] = objectiveValues[i];
                }
            }
        }
    }

    /**
     * Resets statistics for a new algorithm run.
     */
    public void reset() {
        this.currentGeneration = 0;
        this.totalFitnessEvaluations = 0;
        this.startTimeMillis = System.currentTimeMillis();
        this.elapsedTimeMillis = 0;
        for (int i = 0; i < bestObjectiveValues.length; i++) {
            bestObjectiveValues[i] = Double.MAX_VALUE;
        }
        this.bestSolution = null;
        this.paretoFront = null;
    }

    // Getters and Setters

    public int getCurrentGeneration() {
        return currentGeneration;
    }

    public void setCurrentGeneration(int currentGeneration) {
        this.currentGeneration = currentGeneration;
    }

    public long getTotalFitnessEvaluations() {
        return totalFitnessEvaluations;
    }

    public void setTotalFitnessEvaluations(long totalFitnessEvaluations) {
        this.totalFitnessEvaluations = totalFitnessEvaluations;
    }

    public long getElapsedTimeMillis() {
        return elapsedTimeMillis;
    }

    public void setElapsedTimeMillis(long elapsedTimeMillis) {
        this.elapsedTimeMillis = elapsedTimeMillis;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public double[] getBestObjectiveValues() {
        return bestObjectiveValues;
    }

    public double getBestObjectiveValue(int index) {
        return bestObjectiveValues[index];
    }

    public void setBestObjectiveValues(double[] bestObjectiveValues) {
        this.bestObjectiveValues = bestObjectiveValues;
    }

    public SchedulingSolution getBestSolution() {
        return bestSolution;
    }

    public void setBestSolution(SchedulingSolution bestSolution) {
        this.bestSolution = bestSolution;
    }

    public List<SchedulingSolution> getParetoFront() {
        return paretoFront;
    }

    public void setParetoFront(List<SchedulingSolution> paretoFront) {
        this.paretoFront = paretoFront;
    }

    public int getPopulationSize() {
        return populationSize;
    }

    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    public int getNonDominatedCount() {
        return nonDominatedCount;
    }

    public void setNonDominatedCount(int nonDominatedCount) {
        this.nonDominatedCount = nonDominatedCount;
    }

    @Override
    public String toString() {
        return "AlgorithmStatistics{" +
            "generation=" + currentGeneration +
            ", evaluations=" + totalFitnessEvaluations +
            ", elapsedMs=" + elapsedTimeMillis +
            ", nonDominated=" + nonDominatedCount +
            '}';
    }
}
