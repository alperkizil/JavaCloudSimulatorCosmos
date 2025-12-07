package com.cloudsimulator.strategy.task.metaheuristic.objectives;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.strategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.strategy.task.metaheuristic.SchedulingSolution;

import java.util.List;

/**
 * Makespan objective: Minimizes the total time to complete all tasks.
 *
 * Makespan is defined as the time when the last task finishes execution.
 * This is calculated by finding the VM that takes the longest to complete
 * all its assigned tasks.
 *
 * Calculation:
 * For each VM j:
 *   completionTime[j] = sum of (task.instructionLength / vm.totalIPS) for all tasks assigned to VM j
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

        double maxCompletionTime = 0.0;

        // Calculate completion time for each VM
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder.isEmpty()) {
                continue;
            }

            // Calculate total execution time for this VM
            double vmCompletionTime = 0.0;
            long vmIps = vm.getTotalRequestedIps();

            if (vmIps == 0) {
                // VM has no processing power - assign very high penalty
                vmCompletionTime = Double.MAX_VALUE / 2;
            } else {
                for (int taskIdx : taskOrder) {
                    Task task = tasks.get(taskIdx);
                    // Time = instructions / IPS
                    vmCompletionTime += (double) task.getInstructionLength() / vmIps;
                }
            }

            if (vmCompletionTime > maxCompletionTime) {
                maxCompletionTime = vmCompletionTime;
            }
        }

        return maxCompletionTime;
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
