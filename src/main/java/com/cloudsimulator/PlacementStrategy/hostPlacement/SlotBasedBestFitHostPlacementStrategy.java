package com.cloudsimulator.PlacementStrategy.hostPlacement;

import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;

import java.util.List;
import java.util.Optional;

/**
 * Slot-Based Best Fit Host Placement Strategy.
 *
 * Algorithm:
 * 1. Scan ALL eligible datacenters that can accommodate the host
 * 2. Calculate the remaining host slots after placement for each
 * 3. Select the datacenter with the FEWEST remaining slots
 *    (tightest fit in terms of capacity)
 *
 * Characteristics:
 * - Time Complexity: O(n) where n = number of datacenters
 * - Prioritizes filling datacenters to capacity
 * - Reduces capacity fragmentation
 * - Good for maximizing datacenter utilization
 * - May result in some datacenters being completely full while others are empty
 *
 * Example:
 * DC1: max 10 hosts, currently has 8 -> 1 slot remaining after placement
 * DC2: max 50 hosts, currently has 0 -> 49 slots remaining after placement
 * Result: DC1 selected (tighter fit: 1 < 49)
 */
public class SlotBasedBestFitHostPlacementStrategy implements HostPlacementStrategy {

    @Override
    public Optional<CloudDatacenter> selectDatacenter(Host host, List<CloudDatacenter> datacenters) {
        if (host == null || datacenters == null || datacenters.isEmpty()) {
            return Optional.empty();
        }

        CloudDatacenter bestFit = null;
        int smallestRemainingSlots = Integer.MAX_VALUE;

        for (CloudDatacenter datacenter : datacenters) {
            // Check if datacenter can accommodate this host
            if (!datacenter.canAccommodateHost(host)) {
                continue;
            }

            // Calculate remaining slots after placing this host
            int currentHosts = datacenter.getHosts().size();
            int maxHosts = datacenter.getMaxHostCapacity();
            int remainingSlotsAfterPlacement = maxHosts - currentHosts - 1;

            // Select datacenter with smallest remaining slots (best fit)
            if (remainingSlotsAfterPlacement < smallestRemainingSlots) {
                smallestRemainingSlots = remainingSlotsAfterPlacement;
                bestFit = datacenter;
            }
        }

        return Optional.ofNullable(bestFit);
    }

    @Override
    public String getStrategyName() {
        return "Slot-Based Best Fit";
    }

    @Override
    public String getDescription() {
        return "Places each host in the datacenter where it leaves the fewest remaining host slots. " +
               "Optimizes capacity utilization and reduces slot fragmentation.";
    }
}
