package com.cloudsimulator.PlacementStrategy.VMPlacement;

import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.VM;

import java.util.List;
import java.util.Optional;

/**
 * First Fit VM Placement Strategy.
 *
 * Algorithm:
 * 1. Iterate through candidate hosts in sequential order
 * 2. Place the VM on the FIRST host that has sufficient capacity
 * 3. Return immediately once a suitable host is found
 *
 * Capacity check includes:
 * - vCPUs: host has enough available CPU cores
 * - GPUs: host has enough available GPUs (if required)
 * - RAM: host has enough available memory
 * - Storage: host has enough available storage
 * - Bandwidth: host has enough available network bandwidth
 *
 * Characteristics:
 * - Time Complexity: O(n) where n = number of candidate hosts (best case O(1))
 * - Simple and fast
 * - May lead to uneven distribution (earlier hosts fill up first)
 * - Does not optimize for resource efficiency or load balancing
 *
 * Use Case: Quick placement when optimization isn't critical
 */
public class FirstFitVMPlacementStrategy implements VMPlacementStrategy {

    @Override
    public Optional<Host> selectHost(VM vm, List<Host> candidateHosts) {
        if (vm == null || candidateHosts == null || candidateHosts.isEmpty()) {
            return Optional.empty();
        }

        // Iterate through hosts in order
        for (Host host : candidateHosts) {
            // Check if host has capacity for this VM
            if (host.hasCapacityForVM(vm)) {
                return Optional.of(host);
            }
        }

        // No suitable host found
        return Optional.empty();
    }

    @Override
    public String getStrategyName() {
        return "First Fit";
    }

    @Override
    public String getDescription() {
        return "Places each VM on the first host that has sufficient capacity. " +
               "Simple and fast but may result in uneven load distribution.";
    }
}
