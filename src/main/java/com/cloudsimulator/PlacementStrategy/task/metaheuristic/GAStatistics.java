package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Statistics tracking for the Generational Genetic Algorithm.
 *
 * Tracks per-generation metrics including:
 * - Best, average, and worst fitness values
 * - Standard deviation of fitness
 * - Number of generations without improvement
 * - Best candidate solution
 * - Fitness evaluations count
 * - Elapsed time
 *
 * Supports configurable output format for generation logging.
 */
public class GAStatistics {

    /**
     * Output format for generation logging.
     */
    public enum OutputFormat {
        /**
         * Minimal output: Generation: X, Best: Y
         */
        MINIMAL,

        /**
         * Default output: Generation: X, Best Candidate: [...], Fitness: Y
         */
        DEFAULT,

        /**
         * Detailed output: All metrics including avg, worst, std dev, etc.
         */
        DETAILED,

        /**
         * CSV format for easy parsing
         */
        CSV
    }

    // Current generation data
    private int currentGeneration;
    private double bestFitness;
    private double averageFitness;
    private double worstFitness;
    private double standardDeviation;
    private int noImprovementGenerations;

    // Best solution tracking
    private SchedulingSolution bestSolution;
    private int bestSolutionGeneration;
    private double globalBestFitness;

    // Execution tracking
    private int totalFitnessEvaluations;
    private long startTimeMillis;
    private long elapsedTimeMillis;

    // History tracking (optional)
    private final List<GenerationRecord> history;
    private final boolean trackHistory;

    // Output configuration
    private OutputFormat outputFormat;

    /**
     * Creates a new statistics tracker.
     */
    public GAStatistics() {
        this(false);
    }

    /**
     * Creates a new statistics tracker with optional history tracking.
     *
     * @param trackHistory true to maintain history of all generations
     */
    public GAStatistics(boolean trackHistory) {
        this.trackHistory = trackHistory;
        this.history = new ArrayList<>();
        this.outputFormat = OutputFormat.DEFAULT;
        reset();
    }

    /**
     * Resets all statistics to initial state.
     */
    public void reset() {
        this.currentGeneration = 0;
        this.bestFitness = Double.MAX_VALUE;
        this.averageFitness = 0.0;
        this.worstFitness = 0.0;
        this.standardDeviation = 0.0;
        this.noImprovementGenerations = 0;
        this.bestSolution = null;
        this.bestSolutionGeneration = 0;
        this.globalBestFitness = Double.MAX_VALUE;
        this.totalFitnessEvaluations = 0;
        this.startTimeMillis = System.currentTimeMillis();
        this.elapsedTimeMillis = 0;
        this.history.clear();
    }

    /**
     * Starts the timer for execution tracking.
     */
    public void startTimer() {
        this.startTimeMillis = System.currentTimeMillis();
    }

    /**
     * Updates the elapsed time.
     */
    public void updateElapsedTime() {
        this.elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis;
    }

    /**
     * Updates statistics for a new generation.
     *
     * @param generation  Current generation number
     * @param population  Current population (for calculating stats)
     * @param fitnessValues Fitness values for all individuals
     * @param isMinimization true if lower fitness is better
     */
    public void updateGeneration(int generation, List<SchedulingSolution> population,
                                  double[] fitnessValues, boolean isMinimization) {
        this.currentGeneration = generation;
        updateElapsedTime();

        if (fitnessValues == null || fitnessValues.length == 0) {
            return;
        }

        // Calculate statistics
        double sum = 0.0;
        double min = fitnessValues[0];
        double max = fitnessValues[0];
        int bestIdx = 0;

        for (int i = 0; i < fitnessValues.length; i++) {
            double val = fitnessValues[i];
            sum += val;

            if (isMinimization) {
                if (val < min) {
                    min = val;
                    bestIdx = i;
                }
                if (val > max) {
                    max = val;
                }
            } else {
                if (val > max) {
                    max = val;
                    bestIdx = i;
                }
                if (val < min) {
                    min = val;
                }
            }
        }

        this.averageFitness = sum / fitnessValues.length;

        if (isMinimization) {
            this.bestFitness = min;
            this.worstFitness = max;
        } else {
            this.bestFitness = max;
            this.worstFitness = min;
        }

        // Calculate standard deviation
        double varianceSum = 0.0;
        for (double val : fitnessValues) {
            varianceSum += Math.pow(val - averageFitness, 2);
        }
        this.standardDeviation = Math.sqrt(varianceSum / fitnessValues.length);

        // Update best solution tracking
        boolean improved = false;
        if (isMinimization) {
            if (bestFitness < globalBestFitness) {
                globalBestFitness = bestFitness;
                bestSolution = population.get(bestIdx).copy();
                bestSolutionGeneration = generation;
                noImprovementGenerations = 0;
                improved = true;
            } else {
                noImprovementGenerations++;
            }
        } else {
            if (bestFitness > globalBestFitness) {
                globalBestFitness = bestFitness;
                bestSolution = population.get(bestIdx).copy();
                bestSolutionGeneration = generation;
                noImprovementGenerations = 0;
                improved = true;
            } else {
                noImprovementGenerations++;
            }
        }

        // Record history if enabled
        if (trackHistory) {
            history.add(new GenerationRecord(
                generation, bestFitness, averageFitness, worstFitness,
                standardDeviation, improved, elapsedTimeMillis
            ));
        }
    }

    /**
     * Increments the fitness evaluation counter.
     */
    public void incrementEvaluations() {
        totalFitnessEvaluations++;
    }

    /**
     * Adds to the fitness evaluation counter.
     *
     * @param count Number of evaluations to add
     */
    public void addEvaluations(int count) {
        totalFitnessEvaluations += count;
    }

    /**
     * Sets the output format for generation logging.
     *
     * @param format Output format
     */
    public void setOutputFormat(OutputFormat format) {
        this.outputFormat = format;
    }

    /**
     * Formats the current generation statistics as a string.
     *
     * @return Formatted statistics string
     */
    public String formatCurrentGeneration() {
        return formatGeneration(currentGeneration, bestSolution, bestFitness,
            averageFitness, worstFitness, standardDeviation, noImprovementGenerations);
    }

    /**
     * Formats generation statistics as a string.
     */
    private String formatGeneration(int generation, SchedulingSolution best,
                                     double bestFit, double avgFit, double worstFit,
                                     double stdDev, int noImprove) {
        switch (outputFormat) {
            case MINIMAL:
                return String.format("Generation: %d, Best: %.6f", generation, bestFit);

            case CSV:
                return String.format("%d,%.6f,%.6f,%.6f,%.6f,%d",
                    generation, bestFit, avgFit, worstFit, stdDev, noImprove);

            case DETAILED:
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Generation: %d%n", generation));
                sb.append(String.format("  Best Candidate: %s%n",
                    best != null ? Arrays.toString(best.getTaskAssignment()) : "null"));
                sb.append(String.format("  Fitness Value: %.6f%n", bestFit));
                sb.append(String.format("  Average Fitness: %.6f%n", avgFit));
                sb.append(String.format("  Worst Fitness: %.6f%n", worstFit));
                sb.append(String.format("  Std Deviation: %.6f%n", stdDev));
                sb.append(String.format("  No Improvement Generations: %d%n", noImprove));
                sb.append(String.format("  Elapsed Time: %d ms", elapsedTimeMillis));
                return sb.toString();

            case DEFAULT:
            default:
                return String.format(
                    "Generation: %d, Best Candidate: %s, Fitness Value: %.6f",
                    generation,
                    best != null ? Arrays.toString(best.getTaskAssignment()) : "null",
                    bestFit);
        }
    }

    /**
     * Gets the CSV header for CSV output format.
     *
     * @return CSV header string
     */
    public static String getCsvHeader() {
        return "generation,best_fitness,avg_fitness,worst_fitness,std_dev,no_improvement_gens";
    }

    // Getters

    public int getCurrentGeneration() {
        return currentGeneration;
    }

    public double getBestFitness() {
        return bestFitness;
    }

    public double getAverageFitness() {
        return averageFitness;
    }

    public double getWorstFitness() {
        return worstFitness;
    }

    public double getStandardDeviation() {
        return standardDeviation;
    }

    public int getNoImprovementGenerations() {
        return noImprovementGenerations;
    }

    public SchedulingSolution getBestSolution() {
        return bestSolution;
    }

    public int getBestSolutionGeneration() {
        return bestSolutionGeneration;
    }

    public double getGlobalBestFitness() {
        return globalBestFitness;
    }

    public int getTotalFitnessEvaluations() {
        return totalFitnessEvaluations;
    }

    public long getElapsedTimeMillis() {
        return elapsedTimeMillis;
    }

    public List<GenerationRecord> getHistory() {
        return history;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== GA Statistics Summary ===\n");
        sb.append(String.format("Generations: %d%n", currentGeneration));
        sb.append(String.format("Best Fitness: %.6f (found at generation %d)%n",
            globalBestFitness, bestSolutionGeneration));
        sb.append(String.format("Final Average Fitness: %.6f%n", averageFitness));
        sb.append(String.format("Final Worst Fitness: %.6f%n", worstFitness));
        sb.append(String.format("Final Std Deviation: %.6f%n", standardDeviation));
        sb.append(String.format("No Improvement Generations: %d%n", noImprovementGenerations));
        sb.append(String.format("Total Fitness Evaluations: %d%n", totalFitnessEvaluations));
        sb.append(String.format("Elapsed Time: %d ms%n", elapsedTimeMillis));
        if (bestSolution != null) {
            sb.append(String.format("Best Solution: %s%n",
                Arrays.toString(bestSolution.getTaskAssignment())));
        }
        return sb.toString();
    }

    /**
     * Record class for storing generation history.
     */
    public static class GenerationRecord {
        public final int generation;
        public final double bestFitness;
        public final double averageFitness;
        public final double worstFitness;
        public final double standardDeviation;
        public final boolean improved;
        public final long elapsedTimeMillis;

        public GenerationRecord(int generation, double bestFitness, double averageFitness,
                                double worstFitness, double standardDeviation,
                                boolean improved, long elapsedTimeMillis) {
            this.generation = generation;
            this.bestFitness = bestFitness;
            this.averageFitness = averageFitness;
            this.worstFitness = worstFitness;
            this.standardDeviation = standardDeviation;
            this.improved = improved;
            this.elapsedTimeMillis = elapsedTimeMillis;
        }

        @Override
        public String toString() {
            return String.format("Gen %d: best=%.4f, avg=%.4f, worst=%.4f, std=%.4f%s",
                generation, bestFitness, averageFitness, worstFitness, standardDeviation,
                improved ? " *" : "");
        }
    }
}
