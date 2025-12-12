package com.cloudsimulator.PlacementStrategy.VMPlacement;

import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.VM;

import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for VM placement algorithms.
 * Implementations define how VMs are assigned to hosts based on
 * different optimization criteria (resource utilization, load balancing, power efficiency, etc.).
 *
 * VM placement considers:
 * - Resource capacity: vCPUs, GPUs, RAM, storage, bandwidth
 * - Compute type compatibility: CPU_ONLY, GPU_ONLY, CPU_GPU_MIXED
 * - User datacenter preferences (handled by VMPlacementStep before calling strategy)
 */
public interface VMPlacementStrategy {

    /**
     * Selects a host for the given VM based on the strategy's algorithm.
     * The list of candidate hosts should already be filtered to only include
     * hosts that are in the user's preferred datacenters.
     *
     * @param vm             The VM to place
     * @param candidateHosts Available hosts to choose from (already filtered by datacenter preference)
     * @return Optional containing the selected host, or empty if no suitable host found
     */
    Optional<Host> selectHost(VM vm, List<Host> candidateHosts);

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
