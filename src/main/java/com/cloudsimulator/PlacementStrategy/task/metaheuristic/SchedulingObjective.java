package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.List;

/**
 * Interface for scheduling objectives used in multi-objective optimization.
 *
 * Each objective defines a metric to optimize (e.g., makespan, energy consumption).
 * Objectives can be either minimization or maximization targets.
 *
 * This interface is designed to be extensible - new objectives can be added
 * without modifying the NSGA-II algorithm.
 *
 * Built-in objectives:
 * - MakespanObjective: Minimizes total completion time
 * - EnergyObjective: Minimizes total energy consumption
 *
 * Future objectives (easily addable):
 * - WaitingTimeObjective: Minimizes average task waiting time
 * - LoadBalanceObjective: Minimizes variance in VM utilization
 * - ThroughputObjective: Maximizes tasks completed per time unit
 */
public interface SchedulingObjective {

    /**
     * Gets the unique name of this objective.
     *
     * @return Objective name (e.g., "Makespan", "Energy")
     */
    String getName();

    /**
     * Evaluates this objective for a given solution.
     *
     * @param solution The scheduling solution to evaluate
     * @param tasks    All tasks in the problem
     * @param vms      All VMs in the problem
     * @return The objective value (lower is better for minimization, higher for maximization)
     */
    double evaluate(SchedulingSolution solution, List<Task> tasks, List<VM> vms);

    /**
     * Indicates whether this objective should be minimized.
     *
     * @return true if lower values are better, false if higher values are better
     */
    boolean isMinimization();

    /**
     * Gets a description of what this objective measures.
     *
     * @return Human-readable description
     */
    String getDescription();

    /**
     * Gets the unit of measurement for this objective (optional).
     *
     * @return Unit string (e.g., "seconds", "joules", "percentage")
     */
    default String getUnit() {
        return "";
    }

    /**
     * Gets the ideal (best possible) value for this objective, if known.
     * Used for normalization in some algorithms.
     *
     * @return Ideal value, or Double.NaN if unknown
     */
    default double getIdealValue() {
        return Double.NaN;
    }

    /**
     * Gets the nadir (worst acceptable) value for this objective, if known.
     * Used for normalization in some algorithms.
     *
     * @return Nadir value, or Double.NaN if unknown
     */
    default double getNadirValue() {
        return Double.NaN;
    }
}
