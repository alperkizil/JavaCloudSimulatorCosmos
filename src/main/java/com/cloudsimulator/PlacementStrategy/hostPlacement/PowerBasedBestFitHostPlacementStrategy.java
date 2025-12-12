package com.cloudsimulator.PlacementStrategy.hostPlacement;

import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;

import java.util.List;
import java.util.Optional;

/**
 * Power-Based Best Fit Host Placement Strategy.
 *
 * Algorithm:
 * 1. Scan ALL eligible datacenters that can accommodate the host
 * 2. Calculate the remaining power budget after placement for each
 * 3. Select the datacenter with the SMALLEST remaining power budget
 *    (tightest fit in terms of power)
 *
 * Characteristics:
 * - Time Complexity: O(n) where n = number of datacenters
 * - Better bin-packing for power consumption
 * - Reduces power budget fragmentation
 * - May concentrate hosts in datacenters with smaller power budgets
 * - Good for maximizing utilization of power resources
 *
 * Example:
 * Host requires 500W
 * DC1: 1000W max, 400W used -> 100W remaining after placement
 * DC2: 2000W max, 0W used -> 1500W remaining after placement
 * Result: DC1 selected (tighter fit: 100W < 1500W)
 */
public class PowerBasedBestFitHostPlacementStrategy implements HostPlacementStrategy {

    @Override
    public Optional<CloudDatacenter> selectDatacenter(Host host, List<CloudDatacenter> datacenters) {
        if (host == null || datacenters == null || datacenters.isEmpty()) {
            return Optional.empty();
        }

        CloudDatacenter bestFit = null;
        double smallestRemainingPower = Double.MAX_VALUE;

        double hostPowerDraw = host.getCurrentTotalPowerDraw();
        // If host has no power draw yet (idle), estimate using power model idle power
        if (hostPowerDraw <= 0 && host.getPowerModel() != null) {
            hostPowerDraw = host.getPowerModel().calculateTotalPower(0.0, 0.0);
        }

        for (CloudDatacenter datacenter : datacenters) {
            // Check if datacenter can accommodate this host
            if (!datacenter.canAccommodateHost(host)) {
                continue;
            }

            // Calculate remaining power after placing this host
            datacenter.updateTotalMomentaryPowerDraw();
            double currentPower = datacenter.getTotalMomentaryPowerDraw();
            double maxPower = datacenter.getTotalMaxPowerDraw();
            double remainingPowerAfterPlacement = maxPower - currentPower - hostPowerDraw;

            // Select datacenter with smallest remaining power (best fit)
            if (remainingPowerAfterPlacement < smallestRemainingPower) {
                smallestRemainingPower = remainingPowerAfterPlacement;
                bestFit = datacenter;
            }
        }

        return Optional.ofNullable(bestFit);
    }

    @Override
    public String getStrategyName() {
        return "Power-Based Best Fit";
    }

    @Override
    public String getDescription() {
        return "Places each host in the datacenter where it leaves the smallest remaining power budget. " +
               "Optimizes power resource utilization and reduces power budget fragmentation.";
    }
}
