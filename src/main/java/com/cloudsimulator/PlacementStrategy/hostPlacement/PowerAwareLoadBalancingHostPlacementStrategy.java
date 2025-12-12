package com.cloudsimulator.PlacementStrategy.hostPlacement;

import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Power-Aware Load Balancing Host Placement Strategy.
 *
 * Goal: Distribute hosts evenly across datacenters to balance power consumption
 * and prevent any single datacenter from being overloaded.
 *
 * Algorithm:
 * 1. Calculate the power utilization ratio for each datacenter
 *    (current power draw / max power draw)
 * 2. Select the datacenter with the LOWEST power utilization ratio
 * 3. This spreads hosts across datacenters to balance power load
 *
 * Characteristics:
 * - Time Complexity: O(n) where n = number of datacenters
 * - Distributes load evenly across all datacenters
 * - Prevents power hotspots and overloading
 * - May result in all datacenters running at partial capacity
 * - Good for fault tolerance (if one DC fails, others aren't at capacity)
 * - Ensures no datacenter approaches its power limit prematurely
 *
 * Example:
 * DC1: 80% power utilization (80kW/100kW)
 * DC2: 30% power utilization (22.5kW/75kW)
 * DC3: 50% power utilization (45kW/90kW)
 * Result: DC2 selected (lowest power utilization)
 */
public class PowerAwareLoadBalancingHostPlacementStrategy implements HostPlacementStrategy {

    @Override
    public Optional<CloudDatacenter> selectDatacenter(Host host, List<CloudDatacenter> datacenters) {
        if (host == null || datacenters == null || datacenters.isEmpty()) {
            return Optional.empty();
        }

        // Find the datacenter with lowest power utilization that can accommodate the host
        return datacenters.stream()
                .filter(dc -> dc.canAccommodateHost(host))
                .min(Comparator.comparingDouble(this::calculatePowerUtilization));
    }

    /**
     * Calculates the power utilization of a datacenter as a ratio of
     * current power draw to maximum power capacity.
     * A lower ratio means the datacenter has more power headroom.
     *
     * @param datacenter The datacenter to evaluate
     * @return Power utilization ratio between 0.0 and 1.0
     */
    private double calculatePowerUtilization(CloudDatacenter datacenter) {
        double maxPower = datacenter.getTotalMaxPowerDraw();
        if (maxPower <= 0) {
            return 1.0; // Treat as fully utilized if no power capacity defined
        }
        datacenter.updateTotalMomentaryPowerDraw();
        return datacenter.getTotalMomentaryPowerDraw() / maxPower;
    }

    @Override
    public String getStrategyName() {
        return "Power-Aware Load Balancing";
    }

    @Override
    public String getDescription() {
        return "Distributes hosts across datacenters by placing each host in the datacenter with " +
               "the lowest power utilization. Balances power consumption to prevent hotspots and " +
               "ensure fault tolerance. Good for maintaining headroom across all datacenters.";
    }
}
