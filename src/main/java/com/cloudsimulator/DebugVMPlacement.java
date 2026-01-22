package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

// Steps
import com.cloudsimulator.steps.InitializationStep;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.steps.UserDatacenterMappingStep;

// Placement strategies
import com.cloudsimulator.PlacementStrategy.hostPlacement.PowerAwareLoadBalancingHostPlacementStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug tool to analyze VM placement issue.
 * Shows which types of VMs are consuming resources on which types of hosts.
 */
public class DebugVMPlacement {

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

        // Analyze hosts and VMs
        System.out.println("========== HOST ANALYSIS ==========");
        Map<ComputeType, Integer> hostCounts = new HashMap<>();
        Map<ComputeType, Integer> totalCpuCores = new HashMap<>();

        for (CloudDatacenter dc : context.getDatacenters()) {
            for (Host host : dc.getHosts()) {
                ComputeType type = host.getComputeType();
                hostCounts.merge(type, 1, Integer::sum);
                totalCpuCores.merge(type, host.getNumberOfCpuCores(), Integer::sum);
            }
        }

        for (ComputeType type : ComputeType.values()) {
            int count = hostCounts.getOrDefault(type, 0);
            int cores = totalCpuCores.getOrDefault(type, 0);
            System.out.println(type + " hosts: " + count + " (total CPU cores: " + cores + ")");
        }

        System.out.println("\n========== VM ANALYSIS ==========");
        Map<ComputeType, Integer> vmCounts = new HashMap<>();
        Map<ComputeType, Integer> totalVcpus = new HashMap<>();

        for (VM vm : context.getVms()) {
            ComputeType type = vm.getComputeType();
            vmCounts.merge(type, 1, Integer::sum);
            totalVcpus.merge(type, vm.getRequestedVcpuCount(), Integer::sum);
        }

        for (ComputeType type : ComputeType.values()) {
            int count = vmCounts.getOrDefault(type, 0);
            int vcpus = totalVcpus.getOrDefault(type, 0);
            System.out.println(type + " VMs: " + count + " (total vCPUs needed: " + vcpus + ")");
        }

        System.out.println("\n========== COMPATIBILITY ANALYSIS ==========");
        System.out.println("CPU_ONLY VMs (" + vmCounts.getOrDefault(ComputeType.CPU_ONLY, 0) + ") can use:");
        System.out.println("  - CPU_ONLY hosts: " + hostCounts.getOrDefault(ComputeType.CPU_ONLY, 0));
        System.out.println("  - CPU_GPU_MIXED hosts: " + hostCounts.getOrDefault(ComputeType.CPU_GPU_MIXED, 0));

        System.out.println("\nGPU_ONLY VMs (" + vmCounts.getOrDefault(ComputeType.GPU_ONLY, 0) + ") can use:");
        System.out.println("  - GPU_ONLY hosts: " + hostCounts.getOrDefault(ComputeType.GPU_ONLY, 0));
        System.out.println("  - CPU_GPU_MIXED hosts: " + hostCounts.getOrDefault(ComputeType.CPU_GPU_MIXED, 0));

        System.out.println("\nCPU_GPU_MIXED VMs (" + vmCounts.getOrDefault(ComputeType.CPU_GPU_MIXED, 0) + ") can ONLY use:");
        System.out.println("  - CPU_GPU_MIXED hosts: " + hostCounts.getOrDefault(ComputeType.CPU_GPU_MIXED, 0));

        System.out.println("\n========== RESOURCE ANALYSIS ==========");
        int mixedHostCount = hostCounts.getOrDefault(ComputeType.CPU_GPU_MIXED, 0);
        int mixedHostCores = totalCpuCores.getOrDefault(ComputeType.CPU_GPU_MIXED, 0);

        int cpuOnlyVmVcpus = totalVcpus.getOrDefault(ComputeType.CPU_ONLY, 0);
        int gpuOnlyVmVcpus = totalVcpus.getOrDefault(ComputeType.GPU_ONLY, 0);
        int mixedVmVcpus = totalVcpus.getOrDefault(ComputeType.CPU_GPU_MIXED, 0);

        System.out.println("CPU_GPU_MIXED hosts have " + mixedHostCores + " total CPU cores");
        System.out.println("CPU_GPU_MIXED VMs need " + mixedVmVcpus + " vCPUs (MUST use MIXED hosts)");
        System.out.println("Remaining cores on MIXED hosts: " + (mixedHostCores - mixedVmVcpus));
        System.out.println("\nIf GPU_ONLY and CPU_ONLY VMs use up the remaining " +
                          (mixedHostCores - mixedVmVcpus) + " cores on MIXED hosts,");
        System.out.println("then CPU_GPU_MIXED VMs won't have enough resources!");

        System.out.println("\n========== DETAILED VM REQUIREMENTS ==========");
        for (int i = 0; i < Math.min(10, context.getVms().size()); i++) {
            VM vm = context.getVms().get(i);
            System.out.println("VM " + vm.getId() + ": " + vm.getComputeType() +
                              ", vCPUs=" + vm.getRequestedVcpuCount() +
                              ", RAM=" + vm.getRequestedRamMB() + "MB");
        }
        System.out.println("...");
        for (int i = Math.max(0, context.getVms().size() - 10); i < context.getVms().size(); i++) {
            VM vm = context.getVms().get(i);
            System.out.println("VM " + vm.getId() + ": " + vm.getComputeType() +
                              ", vCPUs=" + vm.getRequestedVcpuCount() +
                              ", RAM=" + vm.getRequestedRamMB() + "MB");
        }
    }
}
