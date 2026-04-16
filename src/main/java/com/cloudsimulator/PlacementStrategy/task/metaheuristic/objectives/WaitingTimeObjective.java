package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.List;

/**
 * Waiting Time objective: Minimizes average task waiting time.
 *
 * Waiting time for a task is the time it spends in the VM's queue before
 * it begins executing. The first task on each VM has 0 waiting time;
 * subsequent tasks wait for all preceding tasks on the same VM to complete.
 *
 * Calculation (Discrete Simulation Model):
 * For each VM j, process tasks in their assigned order:
 *   currentTime = 0
 *   For each task i assigned to VM j (in order):
 *     waitingTime[i] = currentTime
 *     executionTicks = ceil(task.instructionLength / vm.totalIps)
 *     currentTime += executionTicks
 *
 * Average Waiting Time = sum(waitingTime[i]) / numTasks
 *
 * This matches the simulation's Task.getWaitingTime() semantics
 * (taskExecStartTime - taskCreationTime), since in the simulation all
 * tasks are created and assigned at time 0 and a task's waiting time
 * equals the cumulative execution time of all tasks ahead of it in
 * the same VM's queue.
 *
 * This objective encourages:
 * - Distributing tasks across more VMs (shorter queues)
 * - Placing shorter tasks earlier in each VM's queue
 * - Using faster VMs to reduce per-task execution time
 *
 * Trade-off with Energy:
 * Energy minimization favors consolidating tasks on fewer VMs (less idle power).
 * Consolidation creates longer queues, increasing waiting time.
 * Waiting time minimization favors spreading tasks across more VMs.
 * These are opposing pressures, producing a genuine Pareto front.
 */
public class WaitingTimeObjective implements SchedulingObjective {

    @Override
    public String getName() {
        return "WaitingTime";
    }

    @Override
    public double evaluate(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return 0.0;
        }

        double totalWaitingTime = 0.0;

        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder.isEmpty()) {
                continue;
            }

            long vmIps = vm.getTotalRequestedIps();
            if (vmIps == 0) {
                continue; // Invalid VM
            }

            // Process tasks in execution order on this VM
            long currentTime = 0;
            for (int taskIdx : taskOrder) {
                Task task = tasks.get(taskIdx);

                // This task waits until all prior tasks on this VM finish
                totalWaitingTime += currentTime;

                // Calculate execution ticks using ceiling division
                long instrLen = task.getInstructionLength();
                long ticksForTask = (instrLen + vmIps - 1) / vmIps;
                currentTime += ticksForTask;
            }
        }

        // Average across all tasks
        return totalWaitingTime / tasks.size();
    }

    @Override
    public boolean isMinimization() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Minimizes average task waiting time (time in queue before execution starts)";
    }

    @Override
    public String getUnit() {
        return "seconds";
    }
}
