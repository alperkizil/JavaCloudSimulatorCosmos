package com.cloudsimulator.PlacementStrategy.hostPlacement;

import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;

import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for host placement algorithms.
 * Implementations define how hosts are assigned to datacenters based on
 * different optimization criteria (capacity, power, load balancing, etc.).
 */
public interface HostPlacementStrategy {

    /**
     * Selects a datacenter for the given host based on the strategy's algorithm.
     *
     * @param host        The host to place
     * @param datacenters Available datacenters to choose from
     * @return Optional containing the selected datacenter, or empty if no suitable datacenter found
     */
    Optional<CloudDatacenter> selectDatacenter(Host host, List<CloudDatacenter> datacenters);

    /**
     * Gets the name of this strategy for logging and reporting purposes.
     *
     * @return Strategy name
     */
    String getStrategyName();

    /**
     * Gets a description of how this strategy works.
     *
     * @return Strategy description
     */
    String getDescription();
}
