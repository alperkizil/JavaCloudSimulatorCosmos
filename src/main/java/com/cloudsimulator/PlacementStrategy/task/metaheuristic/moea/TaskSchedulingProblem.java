package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.Permutation;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.problem.AbstractProblem;

import java.util.List;

/**
 * Adapts the cloud task scheduling problem to MOEA Framework's Problem interface.
 *
 * Encoding (numTasks + 1 variables):
 * - Variables [0, numTasks): task i's VM assignment as a RealVariable in
 *   [0, numVMs-1], rounded to the nearest integer VM index on decode.
 * - Variable numTasks: a {@link Permutation} over the tasks acting as the
 *   global dispatch priority. A VM's intra-queue execution order is the
 *   permutation filtered down to the tasks assigned to that VM.
 *
 * The permutation makes intra-VM execution order part of the genotype: the
 * scheduling objectives (Makespan/WaitingTime/Energy via LaneSchedule) are
 * order-sensitive under the per-vCPU FIFO scheduler, and without order genes
 * every MOEA individual would be evaluated against the same canonical
 * ascending-index order — a strictly smaller search space than the native
 * GA/SA arms explore. See {@link TaskSchedulingVariation} for the
 * domain-operator variation that evolves both parts of the genotype.
 *
 * The static {@code decodeSolution}/{@code encodeInto}/{@code newShell}
 * helpers are the single source of truth for this encoding; the variation and
 * mutation adapters delegate to them rather than duplicating the mapping.
 */
public class TaskSchedulingProblem extends AbstractProblem {

    private final List<Task> tasks;
    private final List<VM> vms;
    private final List<SchedulingObjective> objectives;
    private final RepairOperator repairOperator;

    /**
     * Creates a task scheduling problem for MOEA Framework.
     *
     * @param tasks          List of tasks to schedule
     * @param vms            List of available VMs
     * @param objectives     Scheduling objectives to optimize
     * @param repairOperator Repair operator for fixing invalid assignments
     */
    public TaskSchedulingProblem(List<Task> tasks, List<VM> vms,
                                  List<SchedulingObjective> objectives,
                                  RepairOperator repairOperator) {
        // numTasks assignment variables + 1 dispatch-order permutation
        super(tasks.size() + 1, objectives.size(), 0);
        this.tasks = tasks;
        this.vms = vms;
        this.objectives = objectives;
        this.repairOperator = repairOperator;
    }

    @Override
    public String getName() {
        return "CloudTaskScheduling";
    }

    @Override
    public Solution newSolution() {
        return newShell(tasks.size(), vms.size(), numberOfObjectives, numberOfConstraints);
    }

    @Override
    public void evaluate(Solution solution) {
        // Decode MOEA solution to our SchedulingSolution
        SchedulingSolution schedulingSolution = decode(solution);

        // Apply repair to ensure valid assignments (rebuilds ordering only if
        // something was actually repaired)
        repairOperator.repair(schedulingSolution);

        // Evaluate each objective
        for (int i = 0; i < objectives.size(); i++) {
            double value = objectives.get(i).evaluate(schedulingSolution, tasks, vms);
            solution.setObjective(i, value);
        }

        // Store the repaired assignment and ordering back in the solution so
        // the genotype stays in sync with the evaluated phenotype
        encodeInto(solution, schedulingSolution);
    }

    /**
     * Decodes a MOEA Framework Solution to our SchedulingSolution.
     *
     * @param solution MOEA Framework solution
     * @return Decoded SchedulingSolution
     */
    public SchedulingSolution decode(Solution solution) {
        return decodeSolution(solution, tasks.size(), vms.size(), objectives.size());
    }

    /**
     * Encodes a SchedulingSolution to a new MOEA Framework Solution.
     *
     * @param schedulingSolution Our scheduling solution
     * @return MOEA Framework solution
     */
    public Solution encode(SchedulingSolution schedulingSolution) {
        Solution solution = newSolution();
        encodeInto(solution, schedulingSolution);
        return solution;
    }

    // ==================== Shared encoding helpers ====================

    /**
     * Creates an empty MOEA Solution with this problem's variable layout:
     * numTasks assignment RealVariables plus the dispatch-order Permutation.
     */
    static Solution newShell(int numTasks, int numVMs, int numObjectives, int numConstraints) {
        Solution solution = new Solution(numTasks + 1, numObjectives, numConstraints);
        for (int i = 0; i < numTasks; i++) {
            solution.setVariable(i, new RealVariable(0, numVMs - 1));
        }
        solution.setVariable(numTasks, new Permutation(numTasks));
        return solution;
    }

    /**
     * Decodes assignment genes + dispatch permutation into a SchedulingSolution
     * whose per-VM task order is the permutation filtered by assignment.
     */
    static SchedulingSolution decodeSolution(Solution solution, int numTasks, int numVMs,
                                             int numObjectives) {
        SchedulingSolution schedulingSolution = new SchedulingSolution(numTasks, numVMs, numObjectives);

        int[] assignment = new int[numTasks];
        for (int i = 0; i < numTasks; i++) {
            RealVariable var = (RealVariable) solution.getVariable(i);
            // Round to nearest integer VM index and clamp to valid range
            int vmIndex = (int) Math.round(var.getValue());
            vmIndex = Math.max(0, Math.min(numVMs - 1, vmIndex));
            assignment[i] = vmIndex;
        }
        schedulingSolution.setTaskAssignment(assignment);

        // Per-VM execution order = dispatch permutation filtered by assignment
        Permutation dispatchOrder = (Permutation) solution.getVariable(numTasks);
        List<List<Integer>> vmTaskOrder = schedulingSolution.getVmTaskOrder();
        for (int pos = 0; pos < numTasks; pos++) {
            int taskIdx = dispatchOrder.get(pos);
            vmTaskOrder.get(assignment[taskIdx]).add(taskIdx);
        }

        return schedulingSolution;
    }

    /**
     * Writes a SchedulingSolution's assignment and per-VM ordering into an
     * existing MOEA Solution's variables (the inverse of decodeSolution: the
     * dispatch permutation is the concatenation of the per-VM orders, which
     * round-trips to the identical per-VM orders).
     */
    static void encodeInto(Solution solution, SchedulingSolution schedulingSolution) {
        int[] assignment = schedulingSolution.getTaskAssignment();
        int numTasks = assignment.length;

        for (int i = 0; i < numTasks; i++) {
            ((RealVariable) solution.getVariable(i)).setValue(assignment[i]);
        }

        int[] dispatch = new int[numTasks];
        boolean[] placed = new boolean[numTasks];
        int pos = 0;
        for (List<Integer> order : schedulingSolution.getVmTaskOrder()) {
            for (int taskIdx : order) {
                if (taskIdx >= 0 && taskIdx < numTasks && !placed[taskIdx]) {
                    dispatch[pos++] = taskIdx;
                    placed[taskIdx] = true;
                }
            }
        }
        // Defensive completion: any task missing from the ordering lists (e.g.
        // a caller that set assignments without rebuilding order) is appended
        // in ascending index order so the variable is always a permutation.
        for (int taskIdx = 0; taskIdx < numTasks; taskIdx++) {
            if (!placed[taskIdx]) {
                dispatch[pos++] = taskIdx;
            }
        }

        ((Permutation) solution.getVariable(numTasks)).fromArray(dispatch);
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public List<VM> getVms() {
        return vms;
    }

    public List<SchedulingObjective> getObjectives() {
        return objectives;
    }

    public RepairOperator getRepairOperator() {
        return repairOperator;
    }
}
