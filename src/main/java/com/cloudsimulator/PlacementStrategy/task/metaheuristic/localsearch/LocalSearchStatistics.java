package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Statistics tracking for the Local Search algorithm.
 *
 * Tracks per-iteration metrics including:
 * - Current and best fitness values
 * - Neighbors evaluated per iteration
 * - Improvement tracking
 * - Best solution found
 * - Termination reason
 *
 * Follows the same patterns as GAStatistics and SAStatistics.
 */
public class LocalSearchStatistics {

    /**
     * Output format for iteration logging.
     */
    public enum OutputFormat {
        MINIMAL,
        DEFAULT,
        DETAILED,
        CSV
    }

    /**
     * Reason why the algorithm terminated.
     */
    public enum TerminationReason {
        LOCAL_OPTIMUM("Local optimum reached (no improving neighbor)"),
        MAX_ITERATIONS("Maximum iterations reached"),
        MAX_NO_IMPROVEMENT("Maximum iterations without improvement reached"),
        TERMINATION_CONDITION("Termination condition satisfied"),
        UNKNOWN("Unknown");

        private final String description;

        TerminationReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Current iteration data
    private int currentIteration;
    private double currentFitness;
    private double bestFitness;
    private int neighborsEvaluatedThisIteration;

    // Best solution tracking
    private SchedulingSolution bestSolution;
    private int bestSolutionIteration;

    // Cumulative tracking
    private int totalNeighborsEvaluated;
    private int totalImprovingMoves;
    private int noImprovementIterations;

    // Execution tracking
    private int totalFitnessEvaluations;
    private long startTimeMillis;
    private long elapsedTimeMillis;

    // Termination info
    private TerminationReason terminationReason;

    // History tracking (optional)
    private final List<IterationRecord> history;
    private final boolean trackHistory;

    // Output configuration
    private OutputFormat outputFormat;

    /**
     * Creates a new statistics tracker.
     */
    public LocalSearchStatistics() {
        this(false);
    }

    /**
     * Creates a new statistics tracker with optional history tracking.
     *
     * @param trackHistory true to maintain history of all iterations
     */
    public LocalSearchStatistics(boolean trackHistory) {
        this.trackHistory = trackHistory;
        this.history = new ArrayList<>();
        this.outputFormat = OutputFormat.DEFAULT;
        reset();
    }

    /**
     * Resets all statistics to initial state.
     */
    public void reset() {
        this.currentIteration = 0;
        this.currentFitness = Double.MAX_VALUE;
        this.bestFitness = Double.MAX_VALUE;
        this.neighborsEvaluatedThisIteration = 0;
        this.bestSolution = null;
        this.bestSolutionIteration = 0;
        this.totalNeighborsEvaluated = 0;
        this.totalImprovingMoves = 0;
        this.noImprovementIterations = 0;
        this.totalFitnessEvaluations = 0;
        this.startTimeMillis = System.currentTimeMillis();
        this.elapsedTimeMillis = 0;
        this.terminationReason = TerminationReason.UNKNOWN;
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
     * Updates statistics for a new iteration.
     *
     * @param iteration Current iteration number
     * @param currentSolution Current solution
     * @param currentFitness Current solution fitness
     * @param neighborsEvaluated Number of neighbors evaluated this iteration
     * @param improved Whether the solution improved this iteration
     * @param isMinimization true if lower fitness is better
     */
    public void updateIteration(int iteration, SchedulingSolution currentSolution,
                                 double currentFitness, int neighborsEvaluated,
                                 boolean improved, boolean isMinimization) {
        this.currentIteration = iteration;
        this.currentFitness = currentFitness;
        this.neighborsEvaluatedThisIteration = neighborsEvaluated;

        // Update cumulative counters
        this.totalNeighborsEvaluated += neighborsEvaluated;
        this.totalFitnessEvaluations += neighborsEvaluated + 1; // +1 for current solution

        updateElapsedTime();

        // Update best solution tracking
        if (improved) {
            this.totalImprovingMoves++;
            this.noImprovementIterations = 0;

            boolean isBetter;
            if (isMinimization) {
                isBetter = currentFitness < bestFitness;
            } else {
                isBetter = currentFitness > bestFitness;
            }

            if (isBetter || bestSolution == null) {
                this.bestFitness = currentFitness;
                this.bestSolution = currentSolution.copy();
                this.bestSolutionIteration = iteration;
            }
        } else {
            this.noImprovementIterations++;
        }

        // Record history if enabled
        if (trackHistory) {
            history.add(new IterationRecord(
                iteration, currentFitness, bestFitness,
                neighborsEvaluated, improved, elapsedTimeMillis
            ));
        }
    }

    /**
     * Sets the initial solution statistics.
     *
     * @param initialSolution The initial solution
     * @param initialFitness Fitness of initial solution
     */
    public void setInitialSolution(SchedulingSolution initialSolution, double initialFitness) {
        this.currentFitness = initialFitness;
        this.bestFitness = initialFitness;
        this.bestSolution = initialSolution.copy();
        this.bestSolutionIteration = 0;
        this.totalFitnessEvaluations = 1;
    }

    /**
     * Sets the termination reason.
     *
     * @param reason Why the algorithm terminated
     */
    public void setTerminationReason(TerminationReason reason) {
        this.terminationReason = reason;
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
     * Sets the output format for iteration logging.
     *
     * @param format Output format
     */
    public void setOutputFormat(OutputFormat format) {
        this.outputFormat = format;
    }

    /**
     * Formats the current iteration statistics as a string.
     *
     * @return Formatted statistics string
     */
    public String formatCurrentIteration() {
        return formatIteration(currentIteration, currentFitness, bestFitness,
            neighborsEvaluatedThisIteration, noImprovementIterations);
    }

    /**
     * Formats iteration statistics as a string.
     */
    private String formatIteration(int iteration, double currentFit, double bestFit,
                                   int neighborsEval, int noImprove) {
        switch (outputFormat) {
            case MINIMAL:
                return String.format("Iter: %d, Best: %.6f", iteration, bestFit);

            case CSV:
                return String.format("%d,%.6f,%.6f,%d,%d",
                    iteration, currentFit, bestFit, neighborsEval, noImprove);

            case DETAILED:
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Iteration: %d%n", iteration));
                sb.append(String.format("  Current Fitness: %.6f%n", currentFit));
                sb.append(String.format("  Best Fitness: %.6f%n", bestFit));
                sb.append(String.format("  Neighbors Evaluated: %d%n", neighborsEval));
                sb.append(String.format("  No Improvement Iterations: %d%n", noImprove));
                sb.append(String.format("  Total Evaluations: %d%n", totalFitnessEvaluations));
                sb.append(String.format("  Elapsed Time: %d ms", elapsedTimeMillis));
                return sb.toString();

            case DEFAULT:
            default:
                return String.format(
                    "Iter: %d, Current: %.6f, Best: %.6f, Neighbors: %d",
                    iteration, currentFit, bestFit, neighborsEval);
        }
    }

    /**
     * Gets the CSV header for CSV output format.
     *
     * @return CSV header string
     */
    public static String getCsvHeader() {
        return "iteration,current_fitness,best_fitness,neighbors_evaluated,no_improvement_iterations";
    }

    // Getters

    public int getCurrentIteration() {
        return currentIteration;
    }

    public double getCurrentFitness() {
        return currentFitness;
    }

    public double getBestFitness() {
        return bestFitness;
    }

    public int getNeighborsEvaluatedThisIteration() {
        return neighborsEvaluatedThisIteration;
    }

    public SchedulingSolution getBestSolution() {
        return bestSolution;
    }

    public int getBestSolutionIteration() {
        return bestSolutionIteration;
    }

    public int getTotalNeighborsEvaluated() {
        return totalNeighborsEvaluated;
    }

    public int getTotalImprovingMoves() {
        return totalImprovingMoves;
    }

    public int getNoImprovementIterations() {
        return noImprovementIterations;
    }

    public int getTotalFitnessEvaluations() {
        return totalFitnessEvaluations;
    }

    public long getElapsedTimeMillis() {
        return elapsedTimeMillis;
    }

    public TerminationReason getTerminationReason() {
        return terminationReason;
    }

    public List<IterationRecord> getHistory() {
        return history;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    /**
     * Gets average neighbors evaluated per iteration.
     *
     * @return Average neighbors per iteration
     */
    public double getAverageNeighborsPerIteration() {
        return currentIteration > 0 ? (double) totalNeighborsEvaluated / currentIteration : 0.0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Local Search Statistics Summary ===\n");
        sb.append(String.format("Iterations: %d%n", currentIteration));
        sb.append(String.format("Best Fitness: %.6f (found at iteration %d)%n",
            bestFitness, bestSolutionIteration));
        sb.append(String.format("Total Improving Moves: %d%n", totalImprovingMoves));
        sb.append(String.format("Total Neighbors Evaluated: %d%n", totalNeighborsEvaluated));
        sb.append(String.format("Average Neighbors/Iteration: %.2f%n", getAverageNeighborsPerIteration()));
        sb.append(String.format("Total Fitness Evaluations: %d%n", totalFitnessEvaluations));
        sb.append(String.format("Elapsed Time: %d ms%n", elapsedTimeMillis));
        sb.append(String.format("Termination Reason: %s%n", terminationReason.getDescription()));
        if (bestSolution != null) {
            sb.append(String.format("Best Solution: %s%n",
                Arrays.toString(bestSolution.getTaskAssignment())));
        }
        return sb.toString();
    }

    /**
     * Record class for storing iteration history.
     */
    public static class IterationRecord {
        public final int iteration;
        public final double currentFitness;
        public final double bestFitness;
        public final int neighborsEvaluated;
        public final boolean improved;
        public final long elapsedTimeMillis;

        public IterationRecord(int iteration, double currentFitness, double bestFitness,
                               int neighborsEvaluated, boolean improved, long elapsedTimeMillis) {
            this.iteration = iteration;
            this.currentFitness = currentFitness;
            this.bestFitness = bestFitness;
            this.neighborsEvaluated = neighborsEvaluated;
            this.improved = improved;
            this.elapsedTimeMillis = elapsedTimeMillis;
        }

        @Override
        public String toString() {
            return String.format("Iter %d: current=%.4f, best=%.4f, neighbors=%d%s",
                iteration, currentFitness, bestFitness, neighborsEvaluated,
                improved ? " *" : "");
        }
    }
}
