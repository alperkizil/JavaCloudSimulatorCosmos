package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.List;

/**
 * Load Balance objective: Minimizes workload imbalance across VMs.
 *
 * Measures the Coefficient of Variation (CV) of per-VM completion times.
 * CV = stdDev / mean, where completion time for each VM is the sum of
 * execution ticks for all tasks assigned to it.
 *
 * Calculation (Discrete Simulation Model):
 * For each active VM j (at least one task assigned):
 *   completionTime[j] = sum( ceil(task.instructionLength / vm.totalIps) )
 *
 * mean = average(completionTime[j]) across active VMs
 * stdDev = sqrt( sum((completionTime[j] - mean)^2) / activeVmCount )
 * CV = stdDev / mean
 *
 * CV is scale-invariant (normalized by the mean), so the objective value
 * is comparable across problem instances with different task counts or VM speeds.
 *
 * This aligns with the simulation's MetricsCollectionStep.calculateLoadBalanceIndex()
 * which also uses CV (stdDev / mean).
 *
 * This objective encourages:
 * - Distributing tasks evenly across VMs
 * - Matching task sizes to VM speeds for uniform completion times
 *
 * Trade-off with Energy:
 * Energy minimization favors task consolidation (fewer active hosts = less idle power).
 * Load balance favors task distribution (even workload = low CV).
 * These are directly opposing pressures, producing a genuine Pareto front.
 */
public class LoadBalanceObjective implements SchedulingObjective {

    @Override
    public String getName() {
        return "LoadBalance";
    }

    @Override
    public double evaluate(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return 0.0;
        }

        // Calculate completion time for each active VM
        int activeVmCount = 0;
        double sumCompletionTimes = 0.0;
        double[] completionTimes = new double[vms.size()];

        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder.isEmpty()) {
                continue; // VM has no tasks — not active
            }

            long vmIps = vm.getTotalRequestedIps();
            if (vmIps == 0) {
                continue; // Invalid VM
            }

            long vmCompletionTicks = 0;
            for (int taskIdx : taskOrder) {
                Task task = tasks.get(taskIdx);
                long instrLen = task.getInstructionLength();
                // Ceiling division: matches discrete 1-second simulation ticks
                long ticksForTask = (instrLen + vmIps - 1) / vmIps;
                vmCompletionTicks += ticksForTask;
            }

            completionTimes[vmIdx] = vmCompletionTicks;
            sumCompletionTimes += vmCompletionTicks;
            activeVmCount++;
        }

        // 0 or 1 active VMs — no imbalance possible
        if (activeVmCount <= 1) {
            return 0.0;
        }

        double mean = sumCompletionTimes / activeVmCount;
        if (mean == 0.0) {
            return 0.0;
        }

        // Calculate standard deviation
        double sumSquaredDiffs = 0.0;
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);
            if (taskOrder.isEmpty()) {
                continue;
            }
            double diff = completionTimes[vmIdx] - mean;
            sumSquaredDiffs += diff * diff;
        }

        double stdDev = Math.sqrt(sumSquaredDiffs / activeVmCount);

        // Coefficient of Variation
        return stdDev / mean;
    }

    @Override
    public boolean isMinimization() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Minimizes workload imbalance across VMs (Coefficient of Variation of VM completion times)";
    }

    @Override
    public String getUnit() {
        return "CV";
    }
}
