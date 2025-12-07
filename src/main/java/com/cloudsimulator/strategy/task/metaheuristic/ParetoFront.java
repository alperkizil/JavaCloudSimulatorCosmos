package com.cloudsimulator.strategy.task.metaheuristic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the Pareto front (set of non-dominated solutions) from multi-objective optimization.
 *
 * A solution is non-dominated if no other solution is better in all objectives.
 * The Pareto front represents the trade-off surface between objectives.
 */
public class ParetoFront {

    private final List<SchedulingSolution> solutions;
    private final List<String> objectiveNames;
    private final boolean[] isMinimization;

    /**
     * Creates an empty Pareto front.
     *
     * @param objectiveNames Names of the objectives
     * @param isMinimization Array indicating which objectives are minimization
     */
    public ParetoFront(List<String> objectiveNames, boolean[] isMinimization) {
        this.solutions = new ArrayList<>();
        this.objectiveNames = new ArrayList<>(objectiveNames);
        this.isMinimization = isMinimization.clone();
    }

    /**
     * Creates a Pareto front with initial solutions.
     *
     * @param solutions      Non-dominated solutions
     * @param objectiveNames Names of the objectives
     * @param isMinimization Array indicating which objectives are minimization
     */
    public ParetoFront(List<SchedulingSolution> solutions,
                       List<String> objectiveNames,
                       boolean[] isMinimization) {
        this.solutions = new ArrayList<>(solutions);
        this.objectiveNames = new ArrayList<>(objectiveNames);
        this.isMinimization = isMinimization.clone();
    }

    /**
     * Gets all solutions in the Pareto front.
     *
     * @return Unmodifiable list of non-dominated solutions
     */
    public List<SchedulingSolution> getSolutions() {
        return Collections.unmodifiableList(solutions);
    }

    /**
     * Gets the number of solutions in the Pareto front.
     *
     * @return Number of non-dominated solutions
     */
    public int size() {
        return solutions.size();
    }

    /**
     * Checks if the Pareto front is empty.
     *
     * @return true if no solutions in the front
     */
    public boolean isEmpty() {
        return solutions.isEmpty();
    }

    /**
     * Gets the solution with the best value for a specific objective.
     *
     * @param objectiveIndex Index of the objective
     * @return Best solution for that objective, or null if front is empty
     */
    public SchedulingSolution getBestForObjective(int objectiveIndex) {
        if (solutions.isEmpty()) {
            return null;
        }

        SchedulingSolution best = solutions.get(0);
        boolean minimize = isMinimization[objectiveIndex];

        for (SchedulingSolution sol : solutions) {
            double currentBest = best.getObjectiveValue(objectiveIndex);
            double candidate = sol.getObjectiveValue(objectiveIndex);

            if (minimize) {
                if (candidate < currentBest) {
                    best = sol;
                }
            } else {
                if (candidate > currentBest) {
                    best = sol;
                }
            }
        }

        return best;
    }

    /**
     * Gets the extreme values for each objective in the Pareto front.
     *
     * @return Array of [min, max] for each objective
     */
    public double[][] getObjectiveRanges() {
        if (solutions.isEmpty()) {
            return new double[objectiveNames.size()][2];
        }

        int numObjectives = objectiveNames.size();
        double[][] ranges = new double[numObjectives][2];

        for (int i = 0; i < numObjectives; i++) {
            ranges[i][0] = Double.MAX_VALUE;  // min
            ranges[i][1] = Double.MIN_VALUE;  // max

            for (SchedulingSolution sol : solutions) {
                double val = sol.getObjectiveValue(i);
                if (val < ranges[i][0]) ranges[i][0] = val;
                if (val > ranges[i][1]) ranges[i][1] = val;
            }
        }

        return ranges;
    }

    /**
     * Finds the solution closest to a target point in objective space.
     * Uses normalized Euclidean distance.
     *
     * @param targetValues Target values for each objective
     * @return Closest solution to the target
     */
    public SchedulingSolution getClosestToTarget(double[] targetValues) {
        if (solutions.isEmpty()) {
            return null;
        }

        double[][] ranges = getObjectiveRanges();
        SchedulingSolution closest = null;
        double minDistance = Double.MAX_VALUE;

        for (SchedulingSolution sol : solutions) {
            double distance = 0.0;
            for (int i = 0; i < targetValues.length; i++) {
                double range = ranges[i][1] - ranges[i][0];
                if (range > 0) {
                    double normalized = (sol.getObjectiveValue(i) - targetValues[i]) / range;
                    distance += normalized * normalized;
                }
            }
            distance = Math.sqrt(distance);

            if (distance < minDistance) {
                minDistance = distance;
                closest = sol;
            }
        }

        return closest;
    }

    /**
     * Gets a solution using weighted sum of objectives.
     * Useful for selecting a specific trade-off point.
     *
     * @param weights Weights for each objective (should sum to 1.0)
     * @return Solution with best weighted sum
     */
    public SchedulingSolution getByWeightedSum(double[] weights) {
        if (solutions.isEmpty()) {
            return null;
        }

        double[][] ranges = getObjectiveRanges();
        SchedulingSolution best = null;
        double bestScore = Double.MAX_VALUE;

        for (SchedulingSolution sol : solutions) {
            double score = 0.0;
            for (int i = 0; i < weights.length; i++) {
                // Normalize objective value
                double range = ranges[i][1] - ranges[i][0];
                double normalized = range > 0
                    ? (sol.getObjectiveValue(i) - ranges[i][0]) / range
                    : 0.0;

                // For maximization objectives, invert the normalized value
                if (!isMinimization[i]) {
                    normalized = 1.0 - normalized;
                }

                score += weights[i] * normalized;
            }

            if (score < bestScore) {
                bestScore = score;
                best = sol;
            }
        }

        return best;
    }

    /**
     * Gets the "knee point" solution - a good compromise solution.
     * Uses the maximum curvature heuristic for 2D fronts.
     *
     * @return Knee point solution
     */
    public SchedulingSolution getKneePoint() {
        if (solutions.size() <= 2) {
            return solutions.isEmpty() ? null : solutions.get(solutions.size() / 2);
        }

        // For 2 objectives, find point furthest from line connecting extremes
        if (objectiveNames.size() == 2) {
            return findKnee2D();
        }

        // For more objectives, use equal weights
        double[] weights = new double[objectiveNames.size()];
        java.util.Arrays.fill(weights, 1.0 / objectiveNames.size());
        return getByWeightedSum(weights);
    }

    private SchedulingSolution findKnee2D() {
        // Get extreme points
        SchedulingSolution p1 = getBestForObjective(0);
        SchedulingSolution p2 = getBestForObjective(1);

        if (p1 == null || p2 == null) {
            return solutions.get(0);
        }

        double x1 = p1.getObjectiveValue(0);
        double y1 = p1.getObjectiveValue(1);
        double x2 = p2.getObjectiveValue(0);
        double y2 = p2.getObjectiveValue(1);

        // Find point with maximum perpendicular distance from line p1-p2
        SchedulingSolution knee = p1;
        double maxDistance = 0.0;

        double lineLength = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        if (lineLength == 0) {
            return solutions.get(0);
        }

        for (SchedulingSolution sol : solutions) {
            double px = sol.getObjectiveValue(0);
            double py = sol.getObjectiveValue(1);

            // Perpendicular distance from point to line
            double distance = Math.abs((y2 - y1) * px - (x2 - x1) * py + x2 * y1 - y2 * x1) / lineLength;

            if (distance > maxDistance) {
                maxDistance = distance;
                knee = sol;
            }
        }

        return knee;
    }

    /**
     * Adds a solution to the Pareto front.
     * Internal method - solutions should be validated as non-dominated first.
     *
     * @param solution Solution to add
     */
    void addSolution(SchedulingSolution solution) {
        solutions.add(solution);
    }

    /**
     * Gets the names of the objectives.
     *
     * @return List of objective names
     */
    public List<String> getObjectiveNames() {
        return Collections.unmodifiableList(objectiveNames);
    }

    /**
     * Checks if an objective is minimization.
     *
     * @param index Objective index
     * @return true if objective should be minimized
     */
    public boolean isMinimization(int index) {
        return isMinimization[index];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ParetoFront with ").append(solutions.size()).append(" solutions:\n");

        // Header
        for (String name : objectiveNames) {
            sb.append(String.format("%15s", name));
        }
        sb.append("\n");

        // Values
        for (SchedulingSolution sol : solutions) {
            for (int i = 0; i < objectiveNames.size(); i++) {
                sb.append(String.format("%15.4f", sol.getObjectiveValue(i)));
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
