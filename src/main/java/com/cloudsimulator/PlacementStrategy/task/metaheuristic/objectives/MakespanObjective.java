package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.List;

/**
 * Makespan objective: Minimizes the total time to complete all tasks.
 *
 * Makespan is defined as the time when the last task finishes execution.
 * This is calculated by finding the VM that takes the longest to complete
 * all its assigned tasks.
 *
 * Calculation (Discrete Simulation Model, instruction-resolution finish):
 * The simulation executes in 1-second ticks; dispatch dynamics are tick-quantized
 * (a task finishing mid-tick frees its lane only at the next tick boundary —
 * ceiling division inside {@link LaneSchedule}). The <em>objective value</em>,
 * however, is the exact fractional instant the last instructions complete:
 *
 * For each VM j (per-vCPU FIFO lanes, GPU cap; see {@link LaneSchedule}):
 *   exactEnd(task) = startTick + instructionLength / (double) effIpsPerVcpu
 *   completionExact[j] = max over j's tasks of exactEnd(task)
 *
 * Makespan = max(completionExact[j]) for all VMs j
 *
 * Rationale: makespans land in a narrow band of whole seconds (~18-24 s under
 * the default fleet), so a tick-rounded makespan collapses the Pareto front to
 * at most a handful of points (equal-makespan solutions dominate each other on
 * energy alone). The fractional value restores a continuous objective axis.
 * It matches {@code TaskExecutionStep.getFractionalMakespan()} bit-for-bit
 * (same IEEE-754 expression on the same start tick, length, and effective IPS).
 */
public class MakespanObjective implements SchedulingObjective {

    @Override
    public String getName() {
        return "Makespan";
    }

    @Override
    public double evaluate(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return 0.0;
        }

        double maxCompletionExact = 0.0;

        // Calculate completion time for each VM using discrete simulation model
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder.isEmpty()) {
                continue;
            }

            long effIps = vm.getEffectiveIpsPerVcpu();

            if (effIps == 0) {
                // VM has no processing power - assign very high penalty
                return Double.MAX_VALUE / 2;
            }

            // Distribute the VM's tasks across its vCPU lanes (per-vCPU FIFO
            // scheduler); the VM's completion time is the busiest lane's last
            // exact task-finish instant (tick dynamics, fractional finish).
            double vmCompletionExact = LaneSchedule
                .schedule(taskOrder, tasks, effIps, vm.getRequestedVcpuCount(), vm.getBoundGpuCount())
                .getCompletionExact();

            if (vmCompletionExact > maxCompletionExact) {
                maxCompletionExact = vmCompletionExact;
            }
        }

        return maxCompletionExact;
    }

    @Override
    public boolean isMinimization() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Minimizes the total time to complete all tasks (time when last task finishes)";
    }

    @Override
    public String getUnit() {
        return "seconds";
    }
}
