package com.cloudsimulator.PlacementStrategy.task;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LPT (Longest-Processing-Time-first) task assignment strategy: the classical
 * list-scheduling heuristic for parallel-machine makespan minimization.
 *
 * Algorithm:
 *   1. Sort all tasks by descending instruction length (ties broken by task
 *      id ascending, for determinism).
 *   2. Walk the sorted list; for each task, delegate VM selection to
 *      {@link WorkloadAwareTaskAssignmentStrategy#selectVM}, which projects
 *      each candidate VM's completion tick via {@code LaneSchedule} (the same
 *      analytic mirror of the per-vCPU FIFO scheduler the scheduling
 *      objectives use), including the GPU concurrency cap.
 *   3. Commit each assignment immediately (mirrors
 *      {@link EnergyAwareTaskAssignmentStrategy#assignAll}'s per-task
 *      commit-then-continue pattern) so later tasks in the sorted order see
 *      the updated queue state.
 *
 * Must override {@link #assignAll} rather than relying on the interface
 * default: the default dispatches tasks in caller-supplied (arrival) order,
 * which is not LPT — only {@code assignAll} controls processing order;
 * {@code selectVM} alone cannot reorder tasks.
 *
 * Deterministic: no RNG is used anywhere in this class.
 */
public class LPTTaskAssignmentStrategy implements TaskAssignmentStrategy {

    private final WorkloadAwareTaskAssignmentStrategy vmSelector =
        new WorkloadAwareTaskAssignmentStrategy();

    @Override
    public Map<Task, VM> assignAll(List<Task> tasks, List<VM> vms, long currentTime) {
        List<Task> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator
            .comparingLong(Task::getInstructionLength).reversed()
            .thenComparingLong(Task::getId));

        Map<Task, VM> assignments = new LinkedHashMap<>();
        for (Task task : ordered) {
            List<VM> candidates = filterCandidates(task, vms);
            if (candidates.isEmpty()) continue;
            Optional<VM> chosen = vmSelector.selectVM(task, candidates);
            if (!chosen.isPresent()) continue;
            VM vm = chosen.get();
            task.assignToVM(vm.getId(), currentTime);
            vm.assignTask(task);
            assignments.put(task, vm);
        }
        return assignments;
    }

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        // Single-task use (no ordering to apply): same completion-time
        // projection used per task inside assignAll.
        return vmSelector.selectVM(task, candidateVMs);
    }

    private List<VM> filterCandidates(Task task, List<VM> vms) {
        List<VM> out = new ArrayList<>();
        for (VM vm : vms) {
            if (!vm.getUserId().equals(task.getUserId())) continue;
            if (!vm.canAcceptTask(task)) continue;
            out.add(vm);
        }
        return out;
    }

    @Override
    public String getStrategyName() {
        return "LPT";
    }

    @Override
    public String getDescription() {
        return "Longest-Processing-Time-first: sorts tasks by descending "
             + "instruction length, then greedily assigns each to the VM with "
             + "the lowest projected completion tick under the per-vCPU FIFO "
             + "scheduler (LaneSchedule), including the GPU concurrency cap.";
    }
}
