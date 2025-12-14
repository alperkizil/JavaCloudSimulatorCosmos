package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a complete solution for the task scheduling problem.
 *
 * A solution encodes:
 * 1. Task-to-VM assignment: Which VM each task is assigned to
 * 2. Task ordering: The execution order of tasks within each VM
 *
 * This is the "chromosome" used by metaheuristic algorithms like NSGA-II.
 */
public class SchedulingSolution implements Comparable<SchedulingSolution>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Task assignment array.
     * taskAssignment[i] = index of VM that task i is assigned to.
     * Length = number of tasks.
     */
    private int[] taskAssignment;

    /**
     * Task ordering within each VM.
     * vmTaskOrder.get(j) = list of task indices in execution order for VM j.
     * This allows different orderings of tasks on each VM.
     */
    private List<List<Integer>> vmTaskOrder;

    /**
     * Cached objective values after evaluation.
     * Index corresponds to objective index in the configuration.
     */
    private double[] objectiveValues;

    /**
     * Pareto rank (front number) - 0 is best.
     * Used by NSGA-II for non-dominated sorting.
     */
    private int rank;

    /**
     * Crowding distance for diversity preservation.
     * Higher values indicate more isolated solutions (better for diversity).
     */
    private double crowdingDistance;

    /**
     * Number of solutions that dominate this one.
     * Used during non-dominated sorting.
     */
    private int dominationCount;

    /**
     * Set of solutions that this solution dominates.
     * Used during non-dominated sorting.
     */
    private List<SchedulingSolution> dominatedSolutions;

    /**
     * Whether this solution has been evaluated.
     */
    private boolean evaluated;

    /**
     * Creates a new empty solution with specified dimensions.
     *
     * @param numTasks Number of tasks
     * @param numVMs   Number of VMs
     * @param numObjectives Number of objectives
     */
    public SchedulingSolution(int numTasks, int numVMs, int numObjectives) {
        this.taskAssignment = new int[numTasks];
        this.vmTaskOrder = new ArrayList<>(numVMs);
        for (int i = 0; i < numVMs; i++) {
            vmTaskOrder.add(new ArrayList<>());
        }
        this.objectiveValues = new double[numObjectives];
        this.rank = Integer.MAX_VALUE;
        this.crowdingDistance = 0.0;
        this.dominationCount = 0;
        this.dominatedSolutions = new ArrayList<>();
        this.evaluated = false;
    }

    /**
     * Creates a deep copy of this solution.
     *
     * @return A new SchedulingSolution with copied data
     */
    public SchedulingSolution copy() {
        SchedulingSolution copy = new SchedulingSolution(
            taskAssignment.length,
            vmTaskOrder.size(),
            objectiveValues.length
        );

        // Copy assignment array
        System.arraycopy(taskAssignment, 0, copy.taskAssignment, 0, taskAssignment.length);

        // Copy task ordering
        for (int i = 0; i < vmTaskOrder.size(); i++) {
            copy.vmTaskOrder.get(i).addAll(vmTaskOrder.get(i));
        }

        // Copy objective values
        System.arraycopy(objectiveValues, 0, copy.objectiveValues, 0, objectiveValues.length);

        // Copy metadata
        copy.rank = this.rank;
        copy.crowdingDistance = this.crowdingDistance;
        copy.evaluated = this.evaluated;

        return copy;
    }

    /**
     * Rebuilds the task ordering lists based on current assignment.
     * Should be called after modifying taskAssignment.
     */
    public void rebuildTaskOrdering() {
        // Clear existing orderings
        for (List<Integer> order : vmTaskOrder) {
            order.clear();
        }

        // Rebuild based on assignment
        for (int taskIdx = 0; taskIdx < taskAssignment.length; taskIdx++) {
            int vmIdx = taskAssignment[taskIdx];
            if (vmIdx >= 0 && vmIdx < vmTaskOrder.size()) {
                vmTaskOrder.get(vmIdx).add(taskIdx);
            }
        }
    }

    /**
     * Checks if this solution dominates another solution.
     * Solution A dominates B if A is at least as good in all objectives
     * and strictly better in at least one.
     *
     * @param other The other solution to compare
     * @param minimization Array indicating which objectives to minimize
     * @return true if this solution dominates the other
     */
    public boolean dominates(SchedulingSolution other, boolean[] minimization) {
        boolean atLeastOneBetter = false;

        for (int i = 0; i < objectiveValues.length; i++) {
            double thisVal = objectiveValues[i];
            double otherVal = other.objectiveValues[i];

            if (minimization[i]) {
                // Minimization: lower is better
                if (thisVal > otherVal) {
                    return false; // This is worse in at least one objective
                }
                if (thisVal < otherVal) {
                    atLeastOneBetter = true;
                }
            } else {
                // Maximization: higher is better
                if (thisVal < otherVal) {
                    return false; // This is worse in at least one objective
                }
                if (thisVal > otherVal) {
                    atLeastOneBetter = true;
                }
            }
        }

        return atLeastOneBetter;
    }

    /**
     * Resets domination-related fields for a new non-dominated sorting pass.
     */
    public void resetDominationData() {
        this.dominationCount = 0;
        this.dominatedSolutions.clear();
        this.rank = Integer.MAX_VALUE;
    }

    /**
     * Marks this solution as needing re-evaluation.
     */
    public void invalidate() {
        this.evaluated = false;
    }

    // Comparison for sorting (by rank, then by crowding distance descending)
    @Override
    public int compareTo(SchedulingSolution other) {
        if (this.rank != other.rank) {
            return Integer.compare(this.rank, other.rank);
        }
        // Higher crowding distance is better (more diversity)
        return Double.compare(other.crowdingDistance, this.crowdingDistance);
    }

    // Getters and Setters

    public int[] getTaskAssignment() {
        return taskAssignment;
    }

    public void setTaskAssignment(int[] taskAssignment) {
        this.taskAssignment = taskAssignment;
        this.evaluated = false;
    }

    public int getAssignedVM(int taskIndex) {
        return taskAssignment[taskIndex];
    }

    public void setAssignedVM(int taskIndex, int vmIndex) {
        taskAssignment[taskIndex] = vmIndex;
        this.evaluated = false;
    }

    public List<List<Integer>> getVmTaskOrder() {
        return vmTaskOrder;
    }

    public List<Integer> getTaskOrderForVM(int vmIndex) {
        return vmTaskOrder.get(vmIndex);
    }

    public double[] getObjectiveValues() {
        return objectiveValues;
    }

    public double getObjectiveValue(int index) {
        return objectiveValues[index];
    }

    public void setObjectiveValue(int index, double value) {
        objectiveValues[index] = value;
    }

    public void setObjectiveValues(double[] values) {
        System.arraycopy(values, 0, objectiveValues, 0,
            Math.min(values.length, objectiveValues.length));
        this.evaluated = true;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public double getCrowdingDistance() {
        return crowdingDistance;
    }

    public void setCrowdingDistance(double crowdingDistance) {
        this.crowdingDistance = crowdingDistance;
    }

    public int getDominationCount() {
        return dominationCount;
    }

    public void setDominationCount(int dominationCount) {
        this.dominationCount = dominationCount;
    }

    public void incrementDominationCount() {
        this.dominationCount++;
    }

    public void decrementDominationCount() {
        this.dominationCount--;
    }

    public List<SchedulingSolution> getDominatedSolutions() {
        return dominatedSolutions;
    }

    public void addDominatedSolution(SchedulingSolution solution) {
        this.dominatedSolutions.add(solution);
    }

    public boolean isEvaluated() {
        return evaluated;
    }

    public void setEvaluated(boolean evaluated) {
        this.evaluated = evaluated;
    }

    public int getNumTasks() {
        return taskAssignment.length;
    }

    public int getNumVMs() {
        return vmTaskOrder.size();
    }

    public int getNumObjectives() {
        return objectiveValues.length;
    }

    @Override
    public String toString() {
        return "SchedulingSolution{" +
            "rank=" + rank +
            ", crowdingDistance=" + String.format("%.4f", crowdingDistance) +
            ", objectives=" + Arrays.toString(objectiveValues) +
            ", assignment=" + Arrays.toString(taskAssignment) +
            '}';
    }
}
