package com.cloudsimulator.PlacementStrategy.VMPlacement;

import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.VM;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Power-Aware VM Placement Strategy (Consolidation Heuristic).
 *
 * This strategy minimizes power consumption by consolidating VMs into fewer hosts,
 * allowing unused hosts to remain powered off.
 *
 * Rationale:
 * - A host with 0 VMs can be powered off → 0 power consumption
 * - A host with 1+ VMs consumes idle power + utilization power
 * - Packing VMs into fewer hosts reduces total power consumption
 *
 * Algorithm:
 * 1. Categorize hosts into:
 *    - "Active" hosts: Already have ≥1 VM assigned
 *    - "Inactive" hosts: Have 0 VMs assigned
 *
 * 2. For each VM to place:
 *    a. First, try to place on ACTIVE hosts only
 *       - Among eligible active hosts, select the one with HIGHEST current utilization
 *       - This maximizes consolidation within already-running hosts
 *
 *    b. If no active host has capacity:
 *       - Select an INACTIVE host with the SMALLEST total capacity
 *       - Smaller capacity hosts typically have lower idle power consumption
 *
 * 3. Utilization score = (allocatedCpus / totalCpus) + (allocatedRam / totalRam)
 *    Higher score = more packed = preferred for consolidation
 *
 * Characteristics:
 * - Time Complexity: O(n) where n = number of candidate hosts
 * - Minimizes number of active hosts
 * - Reduces total power consumption
 * - May result in higher utilization on active hosts
 *
 * Use Case: Energy-efficient cloud computing, green computing, reducing carbon footprint
 */
public class PowerAwareVMPlacementStrategy implements VMPlacementStrategy {

    @Override
    public Optional<Host> selectHost(VM vm, List<Host> candidateHosts) {
        if (vm == null || candidateHosts == null || candidateHosts.isEmpty()) {
            return Optional.empty();
        }

        // Separate hosts into active (has VMs) and inactive (no VMs)
        List<Host> activeHosts = new ArrayList<>();
        List<Host> inactiveHosts = new ArrayList<>();

        for (Host host : candidateHosts) {
            if (host.getAssignedVMs().isEmpty()) {
                inactiveHosts.add(host);
            } else {
                activeHosts.add(host);
            }
        }

        // Step 1: Try to place on active hosts (consolidation)
        Optional<Host> activeHostResult = selectFromActiveHosts(vm, activeHosts);
        if (activeHostResult.isPresent()) {
            return activeHostResult;
        }

        // Step 2: No active host has capacity, select smallest inactive host
        return selectFromInactiveHosts(vm, inactiveHosts);
    }

    /**
     * Selects the best active host for the VM.
     * Prefers hosts with the HIGHEST utilization (most packed) to maximize consolidation.
     *
     * @param vm          The VM to place
     * @param activeHosts List of hosts that already have VMs
     * @return Optional containing the selected host, or empty if none suitable
     */
    private Optional<Host> selectFromActiveHosts(VM vm, List<Host> activeHosts) {
        Host bestHost = null;
        double highestUtilization = -1.0;

        for (Host host : activeHosts) {
            // Check if host has capacity for this VM
            if (!host.hasCapacityForVM(vm)) {
                continue;
            }

            // Calculate current utilization
            double utilization = calculateUtilization(host);

            // Select host with highest utilization (most packed)
            if (utilization > highestUtilization) {
                highestUtilization = utilization;
                bestHost = host;
            }
        }

        return Optional.ofNullable(bestHost);
    }

    /**
     * Selects the best inactive host for the VM.
     * Prefers hosts with the SMALLEST capacity (lower idle power consumption).
     *
     * @param vm            The VM to place
     * @param inactiveHosts List of hosts that have no VMs
     * @return Optional containing the selected host, or empty if none suitable
     */
    private Optional<Host> selectFromInactiveHosts(VM vm, List<Host> inactiveHosts) {
        Host bestHost = null;
        double smallestCapacity = Double.MAX_VALUE;

        for (Host host : inactiveHosts) {
            // Check if host has capacity for this VM
            if (!host.hasCapacityForVM(vm)) {
                continue;
            }

            // Calculate total capacity score (lower is better - smaller host)
            double capacityScore = calculateCapacityScore(host);

            // Select host with smallest capacity
            if (capacityScore < smallestCapacity) {
                smallestCapacity = capacityScore;
                bestHost = host;
            }
        }

        return Optional.ofNullable(bestHost);
    }

    /**
     * Calculates the current utilization of a host.
     * Higher utilization = more packed = preferred for consolidation.
     *
     * @param host The host to evaluate
     * @return Utilization score (0.0 to 2.0, higher means more utilized)
     */
    private double calculateUtilization(Host host) {
        // Calculate CPU utilization
        double cpuUtilization = 0.0;
        if (host.getNumberOfCpuCores() > 0) {
            int allocatedCpus = host.getNumberOfCpuCores() - host.getAvailableCpuCores();
            cpuUtilization = (double) allocatedCpus / host.getNumberOfCpuCores();
        }

        // Calculate RAM utilization
        double ramUtilization = 0.0;
        if (host.getRamCapacityMB() > 0) {
            long allocatedRam = host.getRamCapacityMB() - host.getAvailableRamMB();
            ramUtilization = (double) allocatedRam / host.getRamCapacityMB();
        }

        return cpuUtilization + ramUtilization;
    }

    /**
     * Calculates a capacity score for an inactive host.
     * Lower score = smaller host = preferred (lower idle power consumption).
     *
     * @param host The host to evaluate
     * @return Capacity score (lower is better)
     */
    private double calculateCapacityScore(Host host) {
        // Normalize CPU cores (assume max 128 cores)
        double cpuScore = (double) host.getNumberOfCpuCores() / 128.0;

        // Normalize RAM (assume max 4TB = 4,194,304 MB)
        double ramScore = (double) host.getRamCapacityMB() / 4_194_304.0;

        // Normalize GPUs (assume max 16 GPUs) - GPUs contribute significantly to power
        double gpuScore = (double) host.getNumberOfGpus() / 16.0;

        // Combined score with GPU weighted higher (GPUs consume more power)
        return cpuScore + ramScore + (gpuScore * 2.0);
    }

    @Override
    public String getStrategyName() {
        return "Power Aware";
    }

    @Override
    public String getDescription() {
        return "Consolidates VMs into fewer hosts to minimize power consumption. " +
               "Prioritizes placing VMs on already-active hosts with highest utilization. " +
               "When activating new hosts, selects the smallest capacity host to minimize idle power.";
    }
}
