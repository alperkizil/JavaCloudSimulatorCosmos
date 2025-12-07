package com.cloudsimulator.strategy.task;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.List;
import java.util.Optional;

/**
 * Simple task assignment strategy that assigns each task to the first available compatible VM.
 *
 * Algorithm:
 * For each task, iterate through candidate VMs and select the first one found.
 *
 * Characteristics:
 * - Time complexity: O(1) per task (returns first match)
 * - Simple and fast
 * - Deterministic (same order produces same results)
 * - May create imbalanced queues (first VMs get more tasks)
 * - Does not consider task size or VM processing power
 *
 * Use case:
 * - Simple baseline for comparison
 * - When assignment speed is critical
 * - When task distribution doesn't matter
 */
public class FirstAvailableTaskAssignmentStrategy implements TaskAssignmentStrategy {

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        if (candidateVMs == null || candidateVMs.isEmpty()) {
            return Optional.empty();
        }

        // Return the first VM in the list
        return Optional.of(candidateVMs.get(0));
    }

    @Override
    public String getStrategyName() {
        return "FirstAvailable";
    }

    @Override
    public String getDescription() {
        return "Assigns each task to the first compatible VM found. " +
               "Simple and fast but may create imbalanced task queues.";
    }
}
