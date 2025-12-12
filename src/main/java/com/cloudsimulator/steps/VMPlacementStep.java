package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.User;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.VMPlacement.VMPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.VMPlacement.FirstFitVMPlacementStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * VMPlacementStep assigns VMs to hosts using a configurable placement strategy.
 *
 * This is the fourth step in the simulation pipeline, executed after UserDatacenterMappingStep.
 * It takes all unassigned VMs from users and assigns them to appropriate hosts based on
 * the selected placement strategy.
 *
 * Placement constraints enforced:
 * 1. User Datacenter Preferences: VMs are only placed on hosts in datacenters the user has selected
 * 2. Compute Type Compatibility: VM compute type must be compatible with host compute type
 * 3. Resource Capacity: Host must have sufficient vCPUs, GPUs, RAM, storage, and bandwidth
 *
 * Supported Strategies:
 * - FirstFitVMPlacementStrategy: Places VMs on the first host with capacity
 * - BestFitVMPlacementStrategy: Minimizes remaining capacity (tightest fit)
 * - LoadBalancingVMPlacementStrategy: Distributes VMs to least utilized hosts
 * - PowerAwareVMPlacementStrategy: Consolidates VMs to minimize active hosts
 *
 * Usage:
 * <pre>
 * // Using default FirstFit strategy
 * VMPlacementStep step = new VMPlacementStep();
 *
 * // Using custom strategy
 * VMPlacementStep step = new VMPlacementStep(new PowerAwareVMPlacementStrategy());
 * </pre>
 */
public class VMPlacementStep implements SimulationStep {

    private final VMPlacementStrategy strategy;
    private int vmsPlaced;
    private int vmsFailed;
    private Map<Long, Integer> vmsPerHost;
    private Map<String, Integer> vmsPerDatacenter;
    private List<String> failedVMReasons;

    /**
     * Creates a VMPlacementStep with the default FirstFit strategy.
     */
    public VMPlacementStep() {
        this(new FirstFitVMPlacementStrategy());
    }

    /**
     * Creates a VMPlacementStep with a custom placement strategy.
     *
     * @param strategy The VM placement strategy to use
     */
    public VMPlacementStep(VMPlacementStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("VMPlacementStrategy cannot be null");
        }
        this.strategy = strategy;
        this.vmsPlaced = 0;
        this.vmsFailed = 0;
        this.vmsPerHost = new HashMap<>();
        this.vmsPerDatacenter = new HashMap<>();
        this.failedVMReasons = new ArrayList<>();
    }

    @Override
    public void execute(SimulationContext context) {
        List<VM> allVMs = context.getVms();
        List<User> users = context.getUsers();
        List<CloudDatacenter> datacenters = context.getDatacenters();

        if (allVMs == null || allVMs.isEmpty()) {
            recordMetrics(context, datacenters);
            return;
        }

        // Build lookup maps
        Map<String, User> usersByName = new HashMap<>();
        for (User user : users) {
            usersByName.put(user.getName(), user);
        }

        Map<Long, CloudDatacenter> datacentersById = new HashMap<>();
        for (CloudDatacenter dc : datacenters) {
            datacentersById.put(dc.getId(), dc);
            vmsPerDatacenter.put(dc.getName(), 0);
        }

        // Process each unassigned VM
        for (VM vm : allVMs) {
            // Skip VMs that are already assigned to a host
            if (vm.isAssignedToHost()) {
                vmsPlaced++;
                continue;
            }

            // Find the owner user
            User owner = usersByName.get(vm.getUserId());
            if (owner == null) {
                vmsFailed++;
                failedVMReasons.add("VM " + vm.getId() + ": Owner user '" + vm.getUserId() + "' not found");
                logWarning("VM " + vm.getId() + " has no valid owner user: " + vm.getUserId());
                continue;
            }

            // Get candidate hosts from user's preferred datacenters
            List<Host> candidateHosts = getCandidateHosts(vm, owner, datacentersById);

            if (candidateHosts.isEmpty()) {
                vmsFailed++;
                failedVMReasons.add("VM " + vm.getId() + ": No compatible hosts in user's preferred datacenters");
                logWarning("VM " + vm.getId() + " has no candidate hosts in user " + owner.getName() + "'s datacenters");
                continue;
            }

            // Use strategy to select a host
            Optional<Host> selectedHost = strategy.selectHost(vm, candidateHosts);

            if (selectedHost.isPresent()) {
                Host host = selectedHost.get();
                try {
                    host.assignVM(vm);
                    vm.start(); // Set VM state to RUNNING
                    vmsPlaced++;

                    // Update tracking
                    vmsPerHost.merge(host.getId(), 1, Integer::sum);

                    // Find datacenter for this host and update count
                    Long dcId = host.getAssignedDatacenterId();
                    if (dcId != null && datacentersById.containsKey(dcId)) {
                        CloudDatacenter dc = datacentersById.get(dcId);
                        vmsPerDatacenter.merge(dc.getName(), 1, Integer::sum);
                    }
                } catch (IllegalStateException e) {
                    // Host ran out of capacity during placement (race condition)
                    vmsFailed++;
                    failedVMReasons.add("VM " + vm.getId() + ": Host " + host.getId() + " ran out of capacity");
                    logWarning("VM " + vm.getId() + " failed to place on host " + host.getId() + ": " + e.getMessage());
                }
            } else {
                vmsFailed++;
                failedVMReasons.add("VM " + vm.getId() + ": No host with sufficient capacity found");
                logWarning("VM " + vm.getId() + " could not find a suitable host");
            }
        }

        // Record metrics
        recordMetrics(context, datacenters);
    }

    /**
     * Gets candidate hosts for a VM from the user's preferred datacenters.
     * Filters by compute type compatibility.
     *
     * @param vm               The VM to place
     * @param owner            The user who owns the VM
     * @param datacentersById  Map of datacenter ID to CloudDatacenter
     * @return List of compatible hosts from user's preferred datacenters
     */
    private List<Host> getCandidateHosts(VM vm, User owner, Map<Long, CloudDatacenter> datacentersById) {
        List<Host> candidates = new ArrayList<>();

        for (Long dcId : owner.getUserSelectedDatacenters()) {
            CloudDatacenter dc = datacentersById.get(dcId);
            if (dc == null) {
                continue;
            }

            for (Host host : dc.getHosts()) {
                if (isComputeTypeCompatible(vm.getComputeType(), host.getComputeType())) {
                    candidates.add(host);
                }
            }
        }

        return candidates;
    }

    /**
     * Checks if a VM compute type is compatible with a host compute type.
     *
     * Compatibility rules:
     * - CPU_ONLY VMs can run on CPU_ONLY or CPU_GPU_MIXED hosts
     * - GPU_ONLY VMs can run on GPU_ONLY or CPU_GPU_MIXED hosts
     * - CPU_GPU_MIXED VMs can only run on CPU_GPU_MIXED hosts
     *
     * @param vmType   The VM's compute type
     * @param hostType The host's compute type
     * @return true if compatible, false otherwise
     */
    private boolean isComputeTypeCompatible(ComputeType vmType, ComputeType hostType) {
        if (hostType == ComputeType.CPU_GPU_MIXED) {
            // Mixed hosts can run any VM type
            return true;
        }

        if (vmType == ComputeType.CPU_GPU_MIXED) {
            // Mixed VMs require mixed hosts
            return hostType == ComputeType.CPU_GPU_MIXED;
        }

        // CPU_ONLY VMs on CPU_ONLY hosts, GPU_ONLY VMs on GPU_ONLY hosts
        return vmType == hostType;
    }

    /**
     * Logs a warning message about VM placement failure.
     */
    private void logWarning(String message) {
        System.out.println("[WARN] VMPlacementStep: " + message);
    }

    /**
     * Records metrics about VM placement.
     */
    private void recordMetrics(SimulationContext context, List<CloudDatacenter> datacenters) {
        context.recordMetric("vmPlacement.vmsPlaced", vmsPlaced);
        context.recordMetric("vmPlacement.vmsFailed", vmsFailed);
        context.recordMetric("vmPlacement.strategy", strategy.getStrategyName());

        // Record VMs per datacenter
        for (Map.Entry<String, Integer> entry : vmsPerDatacenter.entrySet()) {
            context.recordMetric("vmPlacement.datacenter." + entry.getKey() + ".vmCount", entry.getValue());
        }

        // Record VMs per host
        for (Map.Entry<Long, Integer> entry : vmsPerHost.entrySet()) {
            context.recordMetric("vmPlacement.host." + entry.getKey() + ".vmCount", entry.getValue());
        }

        // Calculate active hosts (hosts with at least 1 VM)
        int activeHosts = vmsPerHost.size();
        context.recordMetric("vmPlacement.activeHosts", activeHosts);
    }

    @Override
    public String getStepName() {
        return "VM Placement (" + strategy.getStrategyName() + ")";
    }

    /**
     * Gets the placement strategy being used.
     *
     * @return The VM placement strategy
     */
    public VMPlacementStrategy getStrategy() {
        return strategy;
    }

    /**
     * Gets the number of VMs successfully placed.
     *
     * @return Number of VMs placed
     */
    public int getVmsPlaced() {
        return vmsPlaced;
    }

    /**
     * Gets the number of VMs that failed to be placed.
     *
     * @return Number of VMs that failed placement
     */
    public int getVmsFailed() {
        return vmsFailed;
    }

    /**
     * Gets the number of VMs placed on each host.
     *
     * @return Map of host ID to VM count
     */
    public Map<Long, Integer> getVmsPerHost() {
        return vmsPerHost;
    }

    /**
     * Gets the number of VMs placed in each datacenter.
     *
     * @return Map of datacenter name to VM count
     */
    public Map<String, Integer> getVmsPerDatacenter() {
        return vmsPerDatacenter;
    }

    /**
     * Gets the reasons for failed VM placements.
     *
     * @return List of failure reason strings
     */
    public List<String> getFailedVMReasons() {
        return failedVMReasons;
    }

    /**
     * Gets the number of active hosts (hosts with at least 1 VM).
     *
     * @return Number of active hosts
     */
    public int getActiveHostCount() {
        return vmsPerHost.size();
    }
}
