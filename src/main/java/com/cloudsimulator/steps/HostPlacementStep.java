package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.strategy.HostPlacementStrategy;
import com.cloudsimulator.strategy.FirstFitHostPlacementStrategy;

import java.util.List;
import java.util.Optional;

/**
 * HostPlacementStep assigns hosts to datacenters using a configurable placement strategy.
 *
 * This is the second step in the simulation pipeline, executed after InitializationStep.
 * It takes all hosts from the context and assigns them to appropriate datacenters based on
 * the selected placement strategy.
 *
 * Supported Strategies:
 * - FirstFitHostPlacementStrategy: Places hosts in the first available datacenter
 * - PowerBasedBestFitHostPlacementStrategy: Minimizes remaining power budget
 * - SlotBasedBestFitHostPlacementStrategy: Minimizes remaining host slots
 * - PowerAwareConsolidatingHostPlacementStrategy: Consolidates hosts into fewer datacenters
 * - PowerAwareLoadBalancingHostPlacementStrategy: Balances power load across datacenters
 *
 * Usage:
 * <pre>
 * // Using default FirstFit strategy
 * HostPlacementStep step = new HostPlacementStep();
 *
 * // Using custom strategy
 * HostPlacementStep step = new HostPlacementStep(new PowerAwareConsolidatingHostPlacementStrategy());
 * </pre>
 */
public class HostPlacementStep implements SimulationStep {

    private final HostPlacementStrategy strategy;
    private int hostsPlaced;
    private int hostsFailed;

    /**
     * Creates a HostPlacementStep with the default FirstFit strategy.
     */
    public HostPlacementStep() {
        this(new FirstFitHostPlacementStrategy());
    }

    /**
     * Creates a HostPlacementStep with a custom placement strategy.
     *
     * @param strategy The host placement strategy to use
     */
    public HostPlacementStep(HostPlacementStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("HostPlacementStrategy cannot be null");
        }
        this.strategy = strategy;
        this.hostsPlaced = 0;
        this.hostsFailed = 0;
    }

    @Override
    public void execute(SimulationContext context) {
        List<Host> hosts = context.getHosts();
        List<CloudDatacenter> datacenters = context.getDatacenters();

        if (hosts == null || hosts.isEmpty()) {
            context.recordMetric("hostPlacement.hostsPlaced", 0);
            context.recordMetric("hostPlacement.hostsFailed", 0);
            context.recordMetric("hostPlacement.strategy", strategy.getStrategyName());
            return;
        }

        if (datacenters == null || datacenters.isEmpty()) {
            hostsFailed = hosts.size();
            context.recordMetric("hostPlacement.hostsPlaced", 0);
            context.recordMetric("hostPlacement.hostsFailed", hostsFailed);
            context.recordMetric("hostPlacement.strategy", strategy.getStrategyName());
            return;
        }

        // Process each unassigned host
        for (Host host : hosts) {
            // Skip hosts that are already assigned to a datacenter
            if (host.getAssignedDatacenterId() != null) {
                hostsPlaced++;
                continue;
            }

            // Use strategy to select a datacenter
            Optional<CloudDatacenter> selectedDatacenter = strategy.selectDatacenter(host, datacenters);

            if (selectedDatacenter.isPresent()) {
                CloudDatacenter datacenter = selectedDatacenter.get();
                try {
                    datacenter.addHost(host);
                    hostsPlaced++;
                } catch (IllegalStateException e) {
                    // Datacenter reached capacity during placement (shouldn't happen if strategy is correct)
                    hostsFailed++;
                }
            } else {
                // No suitable datacenter found for this host
                hostsFailed++;
            }
        }

        // Record metrics
        context.recordMetric("hostPlacement.hostsPlaced", hostsPlaced);
        context.recordMetric("hostPlacement.hostsFailed", hostsFailed);
        context.recordMetric("hostPlacement.strategy", strategy.getStrategyName());

        // Record datacenter distribution metrics
        for (CloudDatacenter datacenter : datacenters) {
            String metricKey = "hostPlacement.datacenter." + datacenter.getName() + ".hostCount";
            context.recordMetric(metricKey, datacenter.getHosts().size());
        }
    }

    @Override
    public String getStepName() {
        return "Host Placement (" + strategy.getStrategyName() + ")";
    }

    /**
     * Gets the placement strategy being used.
     *
     * @return The host placement strategy
     */
    public HostPlacementStrategy getStrategy() {
        return strategy;
    }

    /**
     * Gets the number of hosts successfully placed.
     *
     * @return Number of hosts placed
     */
    public int getHostsPlaced() {
        return hostsPlaced;
    }

    /**
     * Gets the number of hosts that failed to be placed.
     *
     * @return Number of hosts that failed placement
     */
    public int getHostsFailed() {
        return hostsFailed;
    }
}
