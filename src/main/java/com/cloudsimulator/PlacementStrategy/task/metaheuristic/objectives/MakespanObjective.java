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
 * Calculation (Discrete Simulation Model):
 * The simulation executes in 1-second ticks. When a task completes mid-tick,
 * the remaining IPS for that tick is wasted (not used for the next task).
 * This uses ceiling division to accurately model discrete time-step behavior:
 *
 * For each VM j:
 *   For each task assigned to VM j:
 *     ticksForTask = ceil(task.instructionLength / vm.totalIPS)
 *   completionTime[j] = sum of ticksForTask
 *
 * Makespan = max(completionTime[j]) for all VMs j
 *
 * This objective is commonly optimized in scheduling problems because
 * minimizing makespan maximizes resource utilization and throughput.
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

        long maxCompletionTicks = 0;

        // Calculate completion time for each VM using discrete simulation model
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder.isEmpty()) {
                continue;
            }

            long vmIps = vm.getTotalRequestedIps();

            if (vmIps == 0) {
                // VM has no processing power - assign very high penalty
                return Double.MAX_VALUE / 2;
            }

            // Calculate total ticks for this VM using ceiling division
            // This models the discrete 1-second time steps in simulation
            long vmCompletionTicks = 0;
            for (int taskIdx : taskOrder) {
                Task task = tasks.get(taskIdx);
                // Ceiling division: ceil(instructionLength / vmIps)
                // When a task finishes mid-tick, remaining IPS is wasted
                long ticksForTask = (task.getInstructionLength() + vmIps - 1) / vmIps;
                vmCompletionTicks += ticksForTask;
            }

            if (vmCompletionTicks > maxCompletionTicks) {
                maxCompletionTicks = vmCompletionTicks;
            }
        }

        return (double) maxCompletionTicks;
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
