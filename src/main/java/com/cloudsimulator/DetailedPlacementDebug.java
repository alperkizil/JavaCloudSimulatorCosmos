package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.User;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

// Steps
import com.cloudsimulator.steps.InitializationStep;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.steps.UserDatacenterMappingStep;

// Placement strategies
import com.cloudsimulator.PlacementStrategy.hostPlacement.PowerAwareLoadBalancingHostPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.VMPlacement.BestFitVMPlacementStrategy;

import java.util.*;

/**
 * Detailed debug tool to trace VM placement step by step.
 */
public class DetailedPlacementDebug {

    public static void main(String[] args) throws Exception {
        String configFile = "configs/experiment1new/1_20260122_230550_001.cosc";

        // Parse configuration
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse(configFile);

        // Initialize random generator
        RandomGenerator.initialize(config.getRandomSeed());

        // Create simulation context
        SimulationContext context = new SimulationContext();

        // Execute initialization steps
        InitializationStep initStep = new InitializationStep(config);
        initStep.execute(context);

        HostPlacementStep hostPlacementStep = new HostPlacementStep(
            new PowerAwareLoadBalancingHostPlacementStrategy()
        );
        hostPlacementStep.execute(context);

        UserDatacenterMappingStep userMappingStep = new UserDatacenterMappingStep();
        userMappingStep.execute(context);

        // Manual VM placement with detailed tracking
        List<VM> allVMs = context.getVms();
        List<User> users = context.getUsers();
        List<CloudDatacenter> datacenters = context.getDatacenters();

        Map<String, User> usersByName = new HashMap<>();
        for (User user : users) {
            usersByName.put(user.getName(), user);
        }

        Map<Long, CloudDatacenter> datacentersById = new HashMap<>();
        for (CloudDatacenter dc : datacenters) {
            datacentersById.put(dc.getId(), dc);
        }

        BestFitVMPlacementStrategy strategy = new BestFitVMPlacementStrategy();

        // Track resource usage by host type
        Map<ComputeType, Integer> usedCores = new HashMap<>();
        for (ComputeType type : ComputeType.values()) {
            usedCores.put(type, 0);
        }

        int placedCount = 0;
        int failedCount = 0;

        System.out.println("========== SIMULATING VM PLACEMENT ==========\n");

        for (VM vm : allVMs) {
            User owner = usersByName.get(vm.getUserId());
            if (owner == null) {
                System.out.println("VM " + vm.getId() + ": FAILED (no owner)");
                failedCount++;
                continue;
            }

            // Get candidate hosts
            List<Host> candidateHosts = getCandidateHosts(vm, owner, datacentersById);

            if (candidateHosts.isEmpty()) {
                System.out.println("VM " + vm.getId() + " (" + vm.getComputeType() + ", " +
                                  vm.getRequestedVcpuCount() + " vCPUs): FAILED (no candidates)");
                failedCount++;
                continue;
            }

            // Use strategy to select a host
            Optional<Host> selectedHost = strategy.selectHost(vm, candidateHosts);

            if (selectedHost.isPresent()) {
                Host host = selectedHost.get();
                try {
                    host.assignVM(vm);
                    vm.start();
                    placedCount++;
                    usedCores.merge(host.getComputeType(), vm.getRequestedVcpuCount(), Integer::sum);

                    // Only print placements where VM type != Host type (cross-type placement)
                    if (vm.getComputeType() != host.getComputeType()) {
                        System.out.println("VM " + vm.getId() + " (" + vm.getComputeType() + ", " +
                                          vm.getRequestedVcpuCount() + " vCPUs) -> " +
                                          host.getComputeType() + " host " + host.getId() +
                                          " [CROSS-TYPE PLACEMENT]");
                    }
                } catch (IllegalStateException e) {
                    System.out.println("VM " + vm.getId() + " (" + vm.getComputeType() + ", " +
                                      vm.getRequestedVcpuCount() + " vCPUs): FAILED (capacity error)");
                    failedCount++;
                }
            } else {
                System.out.println("VM " + vm.getId() + " (" + vm.getComputeType() + ", " +
                                  vm.getRequestedVcpuCount() + " vCPUs): FAILED (no suitable host)");
                failedCount++;

                // Show remaining capacity on compatible hosts
                if (vm.getComputeType() == ComputeType.CPU_GPU_MIXED) {
                    System.out.println("  -> VM requires CPU_GPU_MIXED host");
                    int mixedHostsAvailable = 0;
                    int totalMixedCoresAvailable = 0;
                    for (Host h : candidateHosts) {
                        if (h.getComputeType() == ComputeType.CPU_GPU_MIXED) {
                            int available = h.getAvailableCpuCores();
                            if (available >= vm.getRequestedVcpuCount()) {
                                mixedHostsAvailable++;
                                totalMixedCoresAvailable += available;
                            }
                        }
                    }
                    System.out.println("  -> CPU_GPU_MIXED hosts with capacity: " + mixedHostsAvailable);
                    System.out.println("  -> Total available cores on MIXED hosts: " + totalMixedCoresAvailable);
                }
            }
        }

        System.out.println("\n========== PLACEMENT SUMMARY ==========");
        System.out.println("VMs Placed: " + placedCount);
        System.out.println("VMs Failed: " + failedCount);
        System.out.println("\n========== CORES USED BY HOST TYPE ==========");
        for (ComputeType type : ComputeType.values()) {
            System.out.println(type + ": " + usedCores.get(type) + " cores used");
        }
    }

    private static List<Host> getCandidateHosts(VM vm, User owner, Map<Long, CloudDatacenter> datacentersById) {
        List<Host> candidates = new ArrayList<>();

        for (Long dcId : owner.getUserSelectedDatacenters()) {
            CloudDatacenter dc = datacentersById.get(dcId);
            if (dc == null) continue;

            for (Host host : dc.getHosts()) {
                if (isComputeTypeCompatible(vm.getComputeType(), host.getComputeType())) {
                    candidates.add(host);
                }
            }
        }

        return candidates;
    }

    private static boolean isComputeTypeCompatible(ComputeType vmType, ComputeType hostType) {
        if (hostType == ComputeType.CPU_GPU_MIXED) {
            return true;
        }

        if (vmType == ComputeType.CPU_GPU_MIXED) {
            return hostType == ComputeType.CPU_GPU_MIXED;
        }

        return vmType == hostType;
    }
}
