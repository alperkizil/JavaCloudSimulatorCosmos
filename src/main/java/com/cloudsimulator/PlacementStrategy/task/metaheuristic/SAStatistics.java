package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Statistics tracking for the Simulated Annealing algorithm.
 *
 * Tracks per-temperature-step metrics including:
 * - Current and best fitness values
 * - Temperature progression
 * - Acceptance statistics (accepted, rejected, improving moves)
 * - Best candidate solution
 * - Fitness evaluations count
 * - Elapsed time
 *
 * Supports configurable output format for temperature step logging,
 * following the same pattern as GAStatistics.
 */
public class SAStatistics {

    /**
     * Output format for temperature step logging.
     * Matches GAStatistics.OutputFormat for consistency.
     */
    public enum OutputFormat {
        /**
         * Minimal output: Temp Step: X, Temp: Y, Best: Z
         */
        MINIMAL,

        /**
         * Default output: Temp Step: X, Best Candidate: [...], Fitness Value: Y
         */
        DEFAULT,

        /**
         * Detailed output: All metrics including acceptance rate, etc.
         */
        DETAILED,

        /**
         * CSV format for easy parsing
         */
        CSV
    }

    // Current temperature step data
    private int currentTemperatureStep;
    private double currentTemperature;
    private double currentFitness;
    private double bestFitness;

    // Acceptance tracking (per temperature step)
    private int acceptedMoves;
    private int rejectedMoves;
    private int improvingMoves;
    private double acceptanceRate;

    // Best solution tracking
    private SchedulingSolution bestSolution;
    private int bestSolutionTemperatureStep;
    private double globalBestFitness;

    // Cumulative tracking
    private int totalIterations;
    private int totalAcceptedMoves;
    private int totalRejectedMoves;
    private int totalImprovingMoves;
    private int noImprovementSteps;

    // Execution tracking
    private int totalFitnessEvaluations;
    private long startTimeMillis;
    private long elapsedTimeMillis;

    // History tracking (optional)
    private final List<TemperatureStepRecord> history;
    private final boolean trackHistory;

    // Output configuration
    private OutputFormat outputFormat;

    /**
     * Creates a new statistics tracker.
     */
    public SAStatistics() {
        this(false);
    }

    /**
     * Creates a new statistics tracker with optional history tracking.
     *
     * @param trackHistory true to maintain history of all temperature steps
     */
    public SAStatistics(boolean trackHistory) {
        this.trackHistory = trackHistory;
        this.history = new ArrayList<>();
        this.outputFormat = OutputFormat.DEFAULT;
        reset();
    }

    /**
     * Resets all statistics to initial state.
     */
    public void reset() {
        this.currentTemperatureStep = 0;
        this.currentTemperature = 0.0;
        this.currentFitness = Double.MAX_VALUE;
        this.bestFitness = Double.MAX_VALUE;
        this.acceptedMoves = 0;
        this.rejectedMoves = 0;
        this.improvingMoves = 0;
        this.acceptanceRate = 0.0;
        this.bestSolution = null;
        this.bestSolutionTemperatureStep = 0;
        this.globalBestFitness = Double.MAX_VALUE;
        this.totalIterations = 0;
        this.totalAcceptedMoves = 0;
        this.totalRejectedMoves = 0;
        this.totalImprovingMoves = 0;
        this.noImprovementSteps = 0;
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
     * Updates statistics for a new temperature step.
     *
     * @param temperatureStep Current temperature step number
     * @param temperature Current temperature
     * @param currentSolution Current solution
     * @param currentFitness Current solution fitness
     * @param accepted Number of accepted moves in this step
     * @param rejected Number of rejected moves in this step
     * @param improving Number of improving moves in this step
     * @param isMinimization true if lower fitness is better
     */
    public void updateTemperatureStep(int temperatureStep, double temperature,
                                       SchedulingSolution currentSolution, double currentFitness,
                                       int accepted, int rejected, int improving,
                                       boolean isMinimization) {
        this.currentTemperatureStep = temperatureStep;
        this.currentTemperature = temperature;
        this.currentFitness = currentFitness;
        this.acceptedMoves = accepted;
        this.rejectedMoves = rejected;
        this.improvingMoves = improving;

        int totalMoves = accepted + rejected;
        this.acceptanceRate = totalMoves > 0 ? (double) accepted / totalMoves : 0.0;

        // Update cumulative counters
        this.totalAcceptedMoves += accepted;
        this.totalRejectedMoves += rejected;
        this.totalImprovingMoves += improving;
        this.totalIterations += totalMoves;

        updateElapsedTime();

        // Update best solution tracking
        boolean improved = false;
        if (isMinimization) {
            if (currentFitness < globalBestFitness) {
                globalBestFitness = currentFitness;
                bestSolution = currentSolution.copy();
                bestSolutionTemperatureStep = temperatureStep;
                noImprovementSteps = 0;
                improved = true;
            } else {
                noImprovementSteps++;
            }
        } else {
            if (currentFitness > globalBestFitness) {
                globalBestFitness = currentFitness;
                bestSolution = currentSolution.copy();
                bestSolutionTemperatureStep = temperatureStep;
                noImprovementSteps = 0;
                improved = true;
            } else {
                noImprovementSteps++;
            }
        }
        this.bestFitness = globalBestFitness;

        // Record history if enabled
        if (trackHistory) {
            history.add(new TemperatureStepRecord(
                temperatureStep, temperature, currentFitness, bestFitness,
                acceptanceRate, improved, elapsedTimeMillis
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
     * Sets the output format for temperature step logging.
     *
     * @param format Output format
     */
    public void setOutputFormat(OutputFormat format) {
        this.outputFormat = format;
    }

    /**
     * Formats the current temperature step statistics as a string.
     *
     * @return Formatted statistics string
     */
    public String formatCurrentTemperatureStep() {
        return formatTemperatureStep(currentTemperatureStep, currentTemperature,
            bestSolution, currentFitness, bestFitness, acceptanceRate, noImprovementSteps);
    }

    /**
     * Formats temperature step statistics as a string.
     */
    private String formatTemperatureStep(int step, double temp, SchedulingSolution best,
                                          double currentFit, double bestFit, double acceptRate,
                                          int noImprove) {
        switch (outputFormat) {
            case MINIMAL:
                return String.format("Temp Step: %d, Temp: %.6f, Best: %.6f",
                    step, temp, bestFit);

            case CSV:
                return String.format("%d,%.6f,%.6f,%.6f,%.4f,%d",
                    step, temp, currentFit, bestFit, acceptRate, noImprove);

            case DETAILED:
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Temperature Step: %d%n", step));
                sb.append(String.format("  Temperature: %.6f%n", temp));
                sb.append(String.format("  Best Candidate: %s%n",
                    best != null ? Arrays.toString(best.getTaskAssignment()) : "null"));
                sb.append(String.format("  Current Fitness: %.6f%n", currentFit));
                sb.append(String.format("  Best Fitness: %.6f%n", bestFit));
                sb.append(String.format("  Acceptance Rate: %.2f%%%n", acceptRate * 100));
                sb.append(String.format("  Accepted/Rejected: %d/%d%n", acceptedMoves, rejectedMoves));
                sb.append(String.format("  Improving Moves: %d%n", improvingMoves));
                sb.append(String.format("  No Improvement Steps: %d%n", noImprove));
                sb.append(String.format("  Elapsed Time: %d ms", elapsedTimeMillis));
                return sb.toString();

            case DEFAULT:
            default:
                return String.format(
                    "Temp Step: %d, Best Candidate: %s, Fitness Value: %.6f",
                    step,
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
        return "temp_step,temperature,current_fitness,best_fitness,acceptance_rate,no_improvement_steps";
    }

    // Getters

    public int getCurrentTemperatureStep() {
        return currentTemperatureStep;
    }

    public double getCurrentTemperature() {
        return currentTemperature;
    }

    public double getCurrentFitness() {
        return currentFitness;
    }

    public double getBestFitness() {
        return bestFitness;
    }

    public int getAcceptedMoves() {
        return acceptedMoves;
    }

    public int getRejectedMoves() {
        return rejectedMoves;
    }

    public int getImprovingMoves() {
        return improvingMoves;
    }

    public double getAcceptanceRate() {
        return acceptanceRate;
    }

    public SchedulingSolution getBestSolution() {
        return bestSolution;
    }

    public int getBestSolutionTemperatureStep() {
        return bestSolutionTemperatureStep;
    }

    public double getGlobalBestFitness() {
        return globalBestFitness;
    }

    public int getTotalIterations() {
        return totalIterations;
    }

    public int getTotalAcceptedMoves() {
        return totalAcceptedMoves;
    }

    public int getTotalRejectedMoves() {
        return totalRejectedMoves;
    }

    public int getTotalImprovingMoves() {
        return totalImprovingMoves;
    }

    public int getNoImprovementSteps() {
        return noImprovementSteps;
    }

    public int getTotalFitnessEvaluations() {
        return totalFitnessEvaluations;
    }

    public long getElapsedTimeMillis() {
        return elapsedTimeMillis;
    }

    public List<TemperatureStepRecord> getHistory() {
        return history;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    /**
     * Gets the overall acceptance rate across all iterations.
     *
     * @return Overall acceptance rate
     */
    public double getOverallAcceptanceRate() {
        int total = totalAcceptedMoves + totalRejectedMoves;
        return total > 0 ? (double) totalAcceptedMoves / total : 0.0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SA Statistics Summary ===\n");
        sb.append(String.format("Temperature Steps: %d%n", currentTemperatureStep));
        sb.append(String.format("Final Temperature: %.6f%n", currentTemperature));
        sb.append(String.format("Best Fitness: %.6f (found at step %d)%n",
            globalBestFitness, bestSolutionTemperatureStep));
        sb.append(String.format("Total Iterations: %d%n", totalIterations));
        sb.append(String.format("Total Accepted Moves: %d%n", totalAcceptedMoves));
        sb.append(String.format("Total Rejected Moves: %d%n", totalRejectedMoves));
        sb.append(String.format("Total Improving Moves: %d%n", totalImprovingMoves));
        sb.append(String.format("Overall Acceptance Rate: %.2f%%%n", getOverallAcceptanceRate() * 100));
        sb.append(String.format("No Improvement Steps: %d%n", noImprovementSteps));
        sb.append(String.format("Total Fitness Evaluations: %d%n", totalFitnessEvaluations));
        sb.append(String.format("Elapsed Time: %d ms%n", elapsedTimeMillis));
        if (bestSolution != null) {
            sb.append(String.format("Best Solution: %s%n",
                Arrays.toString(bestSolution.getTaskAssignment())));
        }
        return sb.toString();
    }

    /**
     * Record class for storing temperature step history.
     */
    public static class TemperatureStepRecord {
        public final int temperatureStep;
        public final double temperature;
        public final double currentFitness;
        public final double bestFitness;
        public final double acceptanceRate;
        public final boolean improved;
        public final long elapsedTimeMillis;

        public TemperatureStepRecord(int temperatureStep, double temperature,
                                     double currentFitness, double bestFitness,
                                     double acceptanceRate, boolean improved,
                                     long elapsedTimeMillis) {
            this.temperatureStep = temperatureStep;
            this.temperature = temperature;
            this.currentFitness = currentFitness;
            this.bestFitness = bestFitness;
            this.acceptanceRate = acceptanceRate;
            this.improved = improved;
            this.elapsedTimeMillis = elapsedTimeMillis;
        }

        @Override
        public String toString() {
            return String.format("Step %d: T=%.4f, current=%.4f, best=%.4f, accept=%.2f%%%s",
                temperatureStep, temperature, currentFitness, bestFitness,
                acceptanceRate * 100, improved ? " *" : "");
        }
    }
}
