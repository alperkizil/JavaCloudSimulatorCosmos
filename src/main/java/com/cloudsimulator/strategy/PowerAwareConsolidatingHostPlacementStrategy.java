package com.cloudsimulator.strategy;

import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Power-Aware Consolidating Host Placement Strategy.
 *
 * Goal: Minimize the number of active datacenters by consolidating hosts into fewer datacenters.
 * This allows unused datacenters to remain powered off, resulting in significant energy savings.
 *
 * Algorithm:
 * 1. Sort datacenters by current utilization (number of hosts / max capacity) in DESCENDING order
 * 2. Try to place host in the most utilized datacenter first (pack tightly)
 * 3. If that datacenter can't accommodate the host, try the next most utilized
 * 4. Continue until a suitable datacenter is found
 *
 * Characteristics:
 * - Time Complexity: O(n log n) due to sorting, where n = number of datacenters
 * - Maximizes consolidation - packs hosts into minimum number of datacenters
 * - Ideal for green cloud computing - unused datacenters can be shut down
 * - May lead to higher utilization in active datacenters
 * - Reduces overall infrastructure power consumption
 *
 * Example:
 * DC1: 80% utilized (8/10 hosts), has power capacity
 * DC2: 20% utilized (10/50 hosts), has power capacity
 * DC3: 0% utilized (0/40 hosts), has power capacity
 * Result: DC1 selected (most utilized, consolidate there first)
 */
public class PowerAwareConsolidatingHostPlacementStrategy implements HostPlacementStrategy {

    @Override
    public Optional<CloudDatacenter> selectDatacenter(Host host, List<CloudDatacenter> datacenters) {
        if (host == null || datacenters == null || datacenters.isEmpty()) {
            return Optional.empty();
        }

        // Sort datacenters by utilization (descending) - most utilized first
        // Using a copy to avoid modifying the original list
        return datacenters.stream()
                .filter(dc -> dc.canAccommodateHost(host))
                .max(Comparator.comparingDouble(this::calculateUtilization));
    }

    /**
     * Calculates the utilization of a datacenter as a ratio of current hosts to max capacity.
     * A higher ratio means the datacenter is more utilized.
     *
     * @param datacenter The datacenter to evaluate
     * @return Utilization ratio between 0.0 and 1.0
     */
    private double calculateUtilization(CloudDatacenter datacenter) {
        int maxCapacity = datacenter.getMaxHostCapacity();
        if (maxCapacity == 0) {
            return 0.0;
        }
        return (double) datacenter.getHosts().size() / maxCapacity;
    }

    @Override
    public String getStrategyName() {
        return "Power-Aware Consolidating";
    }

    @Override
    public String getDescription() {
        return "Consolidates hosts into the minimum number of datacenters by placing hosts in the " +
               "most utilized datacenter first. Allows unused datacenters to be powered off for " +
               "significant energy savings. Ideal for green cloud computing.";
    }
}
