package com.cloudsimulator.PlacementStrategy.hostPlacement;

import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;

import java.util.List;
import java.util.Optional;

/**
 * First Fit Host Placement Strategy.
 *
 * Algorithm:
 * 1. Iterate through datacenters in sequential order
 * 2. Place the host in the FIRST datacenter that satisfies:
 *    - Has capacity for more hosts (hosts.size() < maxHostCapacity)
 *    - Can accommodate the host's power draw without exceeding power limit
 * 3. Return immediately once a suitable datacenter is found
 *
 * Characteristics:
 * - Time Complexity: O(n) where n = number of datacenters (best case O(1))
 * - Simple and fast
 * - May lead to uneven distribution (earlier datacenters fill up first)
 * - Does not optimize for power efficiency or load balancing
 */
public class FirstFitHostPlacementStrategy implements HostPlacementStrategy {

    @Override
    public Optional<CloudDatacenter> selectDatacenter(Host host, List<CloudDatacenter> datacenters) {
        if (host == null || datacenters == null || datacenters.isEmpty()) {
            return Optional.empty();
        }

        // Iterate through datacenters in order
        for (CloudDatacenter datacenter : datacenters) {
            // Check both capacity and power constraints
            if (datacenter.canAccommodateHost(host)) {
                return Optional.of(datacenter);
            }
        }

        // No suitable datacenter found
        return Optional.empty();
    }

    @Override
    public String getStrategyName() {
        return "First Fit";
    }

    @Override
    public String getDescription() {
        return "Places each host in the first datacenter that has sufficient capacity and power budget. " +
               "Simple and fast but may result in uneven load distribution.";
    }
}
