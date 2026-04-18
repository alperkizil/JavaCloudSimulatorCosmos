package com.cloudsimulator.PlacementStrategy.task;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Round-robin task assignment: rotates through compatible VMs in order.
 *
 * A separate rotation counter is maintained per distinct candidate-VM set
 * (keyed by sorted VM IDs) so CPU-task rotation and GPU-task rotation advance
 * independently. VMs that appear in multiple compatibility classes (e.g.
 * CPU_GPU_MIXED) participate in each relevant rotation.
 *
 * Characteristics:
 * - Deterministic (same order produces same results)
 * - Non-preemptive (matches the framework's FIFO VM execution model)
 * - Ignores task size and VM processing power
 * - Balances task COUNT per VM within each compatibility class
 */
public class RoundRobinTaskAssignmentStrategy implements TaskAssignmentStrategy {

    private final Map<List<Long>, Integer> counters = new HashMap<>();

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        if (candidateVMs == null || candidateVMs.isEmpty()) {
            return Optional.empty();
        }

        List<Long> sig = candidateVMs.stream()
            .map(VM::getId)
            .sorted()
            .collect(Collectors.toList());

        int idx = counters.getOrDefault(sig, 0);
        VM selected = candidateVMs.get(idx % candidateVMs.size());
        counters.put(sig, idx + 1);
        return Optional.of(selected);
    }

    @Override
    public String getStrategyName() {
        return "RoundRobin";
    }

    @Override
    public String getDescription() {
        return "Assigns tasks in rotating order across compatible VMs, with a "
             + "separate rotation per task-compatibility class. Non-preemptive, "
             + "deterministic, ignores task size and VM speed.";
    }
}
