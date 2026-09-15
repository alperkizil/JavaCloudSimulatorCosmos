package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.core.Solution;
import org.moeaframework.core.operator.Mutation;

/**
 * Adapts the project's domain-specific MutationOperator to MOEA Framework's Mutation interface.
 *
 * This allows AMOSA to use constraint-aware mutations (reassign to valid VMs,
 * swap task ordering) instead of generic Polynomial Mutation, which is designed
 * for continuous optimization and unaware of task-VM compatibility constraints.
 * With the permutation-carrying encoding, SWAP_ORDER moves survive the
 * encode/decode round-trip, so order-mutating types (COMBINED) are effective.
 */
public class TaskSchedulingMutation implements Mutation {

    private final MutationOperator mutationOperator;
    private final RepairOperator repairOperator;
    private final double mutationRate;
    private final int numTasks;
    private final int numVMs;

    /**
     * Fraction of the configured per-task rate currently in force, in [0, 1].
     * 1.0 (default) leaves {@link #mutationRate} untouched. Lower values scale
     * the per-task rate linearly, so the expected number of mutated tasks falls
     * from {@code mutationRate * numTasks} toward zero; once that expectation is
     * at or below one task, {@link #mutate} takes exactly one
     * {@link MutationOperator#mutateSingle} step instead of a probabilistic pass,
     * so a cold annealer makes a genuine single-task move rather than
     * {@code max(Binomial(N, 1/N), 1)} of them (about 1.37 on average, and two or
     * more a quarter of the time). Mirrors the SA arms' temperature-scaled
     * perturbation; set per temperature step by {@link FixedAMOSAConstrained}.
     */
    private double rateScale = 1.0;

    public TaskSchedulingMutation(MutationOperator mutationOperator,
                                   RepairOperator repairOperator,
                                   double mutationRate,
                                   int numTasks,
                                   int numVMs) {
        this.mutationOperator = mutationOperator;
        this.repairOperator = repairOperator;
        this.mutationRate = mutationRate;
        this.numTasks = numTasks;
        this.numVMs = numVMs;
    }

    @Override
    public String getName() {
        return "TaskSchedulingMutation";
    }

    /** See {@link #rateScale}; clamped to [0, 1]. */
    public void setRateScale(double scale) {
        this.rateScale = Math.max(0.0, Math.min(1.0, scale));
    }

    public double getRateScale() {
        return rateScale;
    }

    /** Per-task rate after scaling: {@code rateScale * mutationRate}. */
    double effectiveMutationRate() {
        return rateScale >= 1.0 ? mutationRate : rateScale * mutationRate;
    }

    /**
     * True when the scaled rate would mutate at most one task in expectation, in
     * which case {@link #mutate} performs exactly one single-task step. Never true
     * at scale 1.0 unless the configured rate itself is that low.
     */
    boolean singleStepRegime() {
        return rateScale < 1.0 && effectiveMutationRate() * numTasks <= 1.0;
    }

    @Override
    public Solution mutate(Solution parent) {
        Solution child = parent.copy();

        // Decode MOEA Solution to SchedulingSolution
        SchedulingSolution schedulingSolution = decode(child);

        if (singleStepRegime()) {
            // Cold endpoint of the temperature-scaled schedule: exactly one
            // single-task step, not a probabilistic pass plus a fallback.
            mutationOperator.mutateSingle(schedulingSolution);
        } else {
            // Apply domain-specific mutation (reassign to valid VMs, swap ordering)
            boolean mutated = mutationOperator.mutate(schedulingSolution, effectiveMutationRate());

            // Guarantee at least one mutation. With rate=0.01 and 100 tasks,
            // P(0 mutations) = e^(-1) ≈ 37%. For SA-based search (AMOSA),
            // every neighbor must be distinct to avoid wasting evaluations.
            if (!mutated) {
                mutationOperator.mutateSingle(schedulingSolution);
            }
        }

        // Repair any constraint violations
        repairOperator.repair(schedulingSolution);

        // Encode back to MOEA Solution, preserving parent's constraint count
        // so constrained problems (e.g. PowerCeilingSchedulingProblem) can
        // still call setConstraint() during evaluation.
        return encode(schedulingSolution, child.getNumberOfObjectives(),
            child.getNumberOfConstraints());
    }

    private SchedulingSolution decode(Solution solution) {
        return TaskSchedulingProblem.decodeSolution(
            solution, numTasks, numVMs, solution.getNumberOfObjectives());
    }

    private Solution encode(SchedulingSolution schedulingSolution, int numObjectives,
                             int numConstraints) {
        Solution solution = TaskSchedulingProblem.newShell(
            numTasks, numVMs, numObjectives, numConstraints);
        TaskSchedulingProblem.encodeInto(solution, schedulingSolution);
        return solution;
    }
}
