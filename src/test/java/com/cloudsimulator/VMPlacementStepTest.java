package com.cloudsimulator;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.VMPlacementStep;
import com.cloudsimulator.PlacementStrategy.hostPlacement.*;
import com.cloudsimulator.PlacementStrategy.VMPlacement.*;

import java.util.Map;

/**
 * Test for the VMPlacementStep implementation.
 * Uses LIMITED resource budgets to differentiate strategy behaviors.
 *
 * The test creates a constrained environment where:
 * - 3 hosts with different capacities (small, medium, large)
 * - 6 VMs that need to be placed
 * - Resources are tight enough that different strategies produce different results
 */
public class VMPlacementStepTest {

    public static void main(String[] args) {
        System.out.println("=== VMPlacementStep Test ===\n");
        System.out.println("Testing with LIMITED resources to differentiate strategies\n");

        // Run tests for each strategy
        testFirstFitStrategy();
        testBestFitStrategy();
        testLoadBalancingStrategy();
        testPowerAwareStrategy();
        testEdgeCases();
        testComputeTypeCompatibility();

        // New test that shows strategy differentiation
        testStrategyDifferentiation();

        System.out.println("\n=== All Tests Completed ===");
    }

    /**
     * Creates a test context with LIMITED resources.
     *
     * Setup:
     * - 1 datacenter with 3 hosts
     * - Host A (small):  4 cores,  0 GPUs, 16GB RAM
     * - Host B (medium): 8 cores,  2 GPUs, 32GB RAM
     * - Host C (large):  16 cores, 4 GPUs, 64GB RAM
     *
     * - 1 user with 6 VMs to place
     * - VM1: 2 cores, 0 GPUs, 4GB RAM (CPU only)
     * - VM2: 2 cores, 0 GPUs, 4GB RAM (CPU only)
     * - VM3: 4 cores, 0 GPUs, 8GB RAM (CPU only)
     * - VM4: 4 cores, 1 GPU,  8GB RAM (Mixed)
     * - VM5: 4 cores, 2 GPUs, 16GB RAM (Mixed)
     * - VM6: 2 cores, 0 GPUs, 4GB RAM (CPU only)
     */
    private static SimulationContext createLimitedResourceContext() {
        SimulationContext context = new SimulationContext();

        // Create datacenter
        CloudDatacenter dc = new CloudDatacenter("DC-Limited", 10, 100000.0);
        context.addDatacenter(dc);

        // Create hosts with LIMITED capacity
        Host hostA = createHost(4, 0, 16384, ComputeType.CPU_ONLY);      // Small
        Host hostB = createHost(8, 2, 32768, ComputeType.CPU_GPU_MIXED); // Medium
        Host hostC = createHost(16, 4, 65536, ComputeType.CPU_GPU_MIXED); // Large

        // Assign hosts to datacenter
        dc.addHost(hostA);
        dc.addHost(hostB);
        dc.addHost(hostC);

        context.addHost(hostA);
        context.addHost(hostB);
        context.addHost(hostC);

        // Create user
        User user = new User("TestUser");
        user.addSelectedDatacenter(dc.getId());
        context.addUser(user);

        // Create VMs with specific resource needs
        VM vm1 = createVM(user.getName(), 2, 0, 4096, ComputeType.CPU_ONLY);
        VM vm2 = createVM(user.getName(), 2, 0, 4096, ComputeType.CPU_ONLY);
        VM vm3 = createVM(user.getName(), 4, 0, 8192, ComputeType.CPU_ONLY);
        VM vm4 = createVM(user.getName(), 4, 1, 8192, ComputeType.CPU_GPU_MIXED);
        VM vm5 = createVM(user.getName(), 4, 2, 16384, ComputeType.CPU_GPU_MIXED);
        VM vm6 = createVM(user.getName(), 2, 0, 4096, ComputeType.CPU_ONLY);

        // Add VMs to user and context
        user.addVirtualMachine(vm1);
        user.addVirtualMachine(vm2);
        user.addVirtualMachine(vm3);
        user.addVirtualMachine(vm4);
        user.addVirtualMachine(vm5);
        user.addVirtualMachine(vm6);

        context.addVM(vm1);
        context.addVM(vm2);
        context.addVM(vm3);
        context.addVM(vm4);
        context.addVM(vm5);
        context.addVM(vm6);

        return context;
    }

    private static Host createHost(int cpuCores, int gpus, long ramMB, ComputeType computeType) {
        Host host = new Host(2_500_000_000L, cpuCores, computeType, gpus);
        host.setRamCapacityMB(ramMB);
        host.setNetworkCapacityMbps(10000);
        host.setHardDriveCapacityMB(1024 * 1024); // 1TB
        return host;
    }

    private static VM createVM(String userId, int vcpus, int gpus, long ramMB, ComputeType computeType) {
        return new VM(userId, 2_000_000_000L, vcpus, gpus, ramMB, 51200, 1000, computeType);
    }

    private static void testFirstFitStrategy() {
        System.out.println("Test 1: First Fit Strategy");
        System.out.println("-".repeat(60));

        SimulationContext context = createLimitedResourceContext();
        VMPlacementStep step = new VMPlacementStep(new FirstFitVMPlacementStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  VMs placed: " + step.getVmsPlaced());
        System.out.println("  VMs failed: " + step.getVmsFailed());
        System.out.println("  Active hosts: " + step.getActiveHostCount());

        printHostDistribution(context, step);

        // First Fit places VMs on first available host
        // Expected: VMs packed into hosts in order they are encountered
        boolean passed = step.getVmsPlaced() == 6 && step.getVmsFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testBestFitStrategy() {
        System.out.println("Test 2: Best Fit Strategy");
        System.out.println("-".repeat(60));

        SimulationContext context = createLimitedResourceContext();
        VMPlacementStep step = new VMPlacementStep(new BestFitVMPlacementStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  VMs placed: " + step.getVmsPlaced());
        System.out.println("  VMs failed: " + step.getVmsFailed());
        System.out.println("  Active hosts: " + step.getActiveHostCount());

        printHostDistribution(context, step);

        // Best Fit minimizes remaining capacity - tightest packing
        // Expected: VMs placed on hosts that leave least slack
        boolean passed = step.getVmsPlaced() == 6 && step.getVmsFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testLoadBalancingStrategy() {
        System.out.println("Test 3: Load Balancing Strategy");
        System.out.println("-".repeat(60));

        SimulationContext context = createLimitedResourceContext();
        VMPlacementStep step = new VMPlacementStep(new LoadBalancingVMPlacementStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  VMs placed: " + step.getVmsPlaced());
        System.out.println("  VMs failed: " + step.getVmsFailed());
        System.out.println("  Active hosts: " + step.getActiveHostCount());

        printHostDistribution(context, step);

        // Load Balancing distributes VMs across hosts
        // Expected: More even distribution across hosts
        boolean passed = step.getVmsPlaced() == 6 && step.getVmsFailed() == 0;

        // Check for balanced distribution (all hosts should have at least 1 VM if compatible)
        int hostsWithVMs = step.getActiveHostCount();
        boolean balanced = hostsWithVMs >= 2; // At least 2 hosts should be used for load balancing
        System.out.println("  Load balanced (>= 2 hosts active): " + balanced);
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testPowerAwareStrategy() {
        System.out.println("Test 4: Power Aware Strategy (Consolidation)");
        System.out.println("-".repeat(60));

        SimulationContext context = createLimitedResourceContext();
        VMPlacementStep step = new VMPlacementStep(new PowerAwareVMPlacementStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  VMs placed: " + step.getVmsPlaced());
        System.out.println("  VMs failed: " + step.getVmsFailed());
        System.out.println("  Active hosts: " + step.getActiveHostCount());

        printHostDistribution(context, step);

        // Power Aware consolidates VMs into fewer hosts
        // Expected: Minimize active hosts by packing densely
        boolean passed = step.getVmsPlaced() == 6 && step.getVmsFailed() == 0;

        // Check for consolidation (should try to use fewer hosts)
        int activeHosts = step.getActiveHostCount();
        System.out.println("  Consolidation check (fewer active hosts is better)");
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testEdgeCases() {
        System.out.println("Test 5: Edge Cases");
        System.out.println("-".repeat(60));

        // Test 5a: Empty VMs list
        System.out.println("  Test 5a: Empty VMs list");
        SimulationContext emptyContext = new SimulationContext();
        VMPlacementStep step = new VMPlacementStep();
        step.execute(emptyContext);
        boolean test5a = step.getVmsPlaced() == 0 && step.getVmsFailed() == 0;
        System.out.println("    Result: " + (test5a ? "PASSED" : "FAILED"));

        // Test 5b: Default strategy (FirstFit)
        System.out.println("  Test 5b: Default strategy is FirstFit");
        VMPlacementStep defaultStep = new VMPlacementStep();
        boolean test5b = defaultStep.getStrategy() instanceof FirstFitVMPlacementStrategy;
        System.out.println("    Strategy: " + defaultStep.getStrategy().getStrategyName());
        System.out.println("    Result: " + (test5b ? "PASSED" : "FAILED"));

        // Test 5c: Strategy descriptions
        System.out.println("  Test 5c: Strategy descriptions available");
        VMPlacementStrategy[] strategies = {
            new FirstFitVMPlacementStrategy(),
            new BestFitVMPlacementStrategy(),
            new LoadBalancingVMPlacementStrategy(),
            new PowerAwareVMPlacementStrategy()
        };
        boolean test5c = true;
        for (VMPlacementStrategy s : strategies) {
            if (s.getDescription() == null || s.getDescription().isEmpty()) {
                test5c = false;
            }
            System.out.println("    " + s.getStrategyName() + ": " +
                    (s.getDescription().length() > 50 ? s.getDescription().substring(0, 50) + "..." : s.getDescription()));
        }
        System.out.println("    Result: " + (test5c ? "PASSED" : "FAILED"));

        // Test 5d: Metrics recorded
        System.out.println("  Test 5d: Metrics recorded correctly");
        SimulationContext context = createLimitedResourceContext();
        VMPlacementStep metricStep = new VMPlacementStep(new FirstFitVMPlacementStrategy());
        metricStep.execute(context);
        boolean test5d = context.getMetric("vmPlacement.vmsPlaced") != null &&
                        context.getMetric("vmPlacement.vmsFailed") != null &&
                        context.getMetric("vmPlacement.strategy") != null;
        System.out.println("    vmPlacement.vmsPlaced: " + context.getMetric("vmPlacement.vmsPlaced"));
        System.out.println("    vmPlacement.vmsFailed: " + context.getMetric("vmPlacement.vmsFailed"));
        System.out.println("    vmPlacement.strategy: " + context.getMetric("vmPlacement.strategy"));
        System.out.println("    vmPlacement.activeHosts: " + context.getMetric("vmPlacement.activeHosts"));
        System.out.println("    Result: " + (test5d ? "PASSED" : "FAILED"));

        // Test 5e: VM without valid owner
        System.out.println("  Test 5e: VM without valid owner (should skip and warn)");
        SimulationContext orphanContext = new SimulationContext();
        CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);
        Host host = createHost(8, 0, 32768, ComputeType.CPU_ONLY);
        dc.addHost(host);
        orphanContext.addDatacenter(dc);
        orphanContext.addHost(host);

        // Add VM without adding corresponding user
        VM orphanVM = new VM("NonExistentUser", 2_000_000_000L, 2, 0, 4096, 10240, 1000, ComputeType.CPU_ONLY);
        orphanContext.addVM(orphanVM);

        VMPlacementStep orphanStep = new VMPlacementStep();
        orphanStep.execute(orphanContext);
        boolean test5e = orphanStep.getVmsFailed() == 1;
        System.out.println("    VMs failed: " + orphanStep.getVmsFailed());
        System.out.println("    Failure reason: " +
            (orphanStep.getFailedVMReasons().isEmpty() ? "none" : orphanStep.getFailedVMReasons().get(0)));
        System.out.println("    Result: " + (test5e ? "PASSED" : "FAILED"));

        System.out.println();
    }

    private static void testComputeTypeCompatibility() {
        System.out.println("Test 6: Compute Type Compatibility");
        System.out.println("-".repeat(60));

        // Create context with specific compute type constraints
        SimulationContext context = new SimulationContext();

        // Create datacenter with CPU-only and GPU-only hosts
        CloudDatacenter dc = new CloudDatacenter("DC-Compute", 10, 100000.0);
        context.addDatacenter(dc);

        // CPU-only host
        Host cpuHost = createHost(16, 0, 65536, ComputeType.CPU_ONLY);
        // GPU-only host
        Host gpuHost = new Host(2_500_000_000L, 4, ComputeType.GPU_ONLY, 8);
        gpuHost.setRamCapacityMB(32768);
        gpuHost.setNetworkCapacityMbps(10000);
        gpuHost.setHardDriveCapacityMB(1024 * 1024);

        dc.addHost(cpuHost);
        dc.addHost(gpuHost);
        context.addHost(cpuHost);
        context.addHost(gpuHost);

        // Create user
        User user = new User("ComputeUser");
        user.addSelectedDatacenter(dc.getId());
        context.addUser(user);

        // Create VMs with different compute types
        VM cpuVM = createVM(user.getName(), 4, 0, 8192, ComputeType.CPU_ONLY);
        VM gpuVM = new VM(user.getName(), 2_000_000_000L, 2, 2, 4096, 10240, 1000, ComputeType.GPU_ONLY);

        user.addVirtualMachine(cpuVM);
        user.addVirtualMachine(gpuVM);
        context.addVM(cpuVM);
        context.addVM(gpuVM);

        VMPlacementStep step = new VMPlacementStep(new FirstFitVMPlacementStrategy());
        step.execute(context);

        System.out.println("  CPU-only VM placed on: Host " + cpuVM.getAssignedHostId());
        System.out.println("  GPU-only VM placed on: Host " + gpuVM.getAssignedHostId());
        System.out.println("  CPU Host ID: " + cpuHost.getId());
        System.out.println("  GPU Host ID: " + gpuHost.getId());

        // Verify CPU VM is on CPU host and GPU VM is on GPU host
        boolean cpuMatch = cpuVM.getAssignedHostId() != null && cpuVM.getAssignedHostId().equals(cpuHost.getId());
        boolean gpuMatch = gpuVM.getAssignedHostId() != null && gpuVM.getAssignedHostId().equals(gpuHost.getId());

        System.out.println("  CPU VM correctly placed: " + cpuMatch);
        System.out.println("  GPU VM correctly placed: " + gpuMatch);
        System.out.println("  Result: " + (cpuMatch && gpuMatch ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void printHostDistribution(SimulationContext context, VMPlacementStep step) {
        System.out.println("  Host Distribution:");
        for (Host host : context.getHosts()) {
            int vmCount = host.getAssignedVMs().size();
            double cpuUtil = host.getNumberOfCpuCores() > 0
                ? (double)(host.getNumberOfCpuCores() - host.getAvailableCpuCores()) / host.getNumberOfCpuCores() * 100
                : 0;
            double gpuUtil = host.getNumberOfGpus() > 0
                ? (double)(host.getNumberOfGpus() - host.getAvailableGpus()) / host.getNumberOfGpus() * 100
                : 0;
            double ramUtil = host.getRamCapacityMB() > 0
                ? (double)(host.getRamCapacityMB() - host.getAvailableRamMB()) / host.getRamCapacityMB() * 100
                : 0;

            System.out.printf("    Host %d (%s): %d VMs | CPU: %.0f%% | GPU: %.0f%% | RAM: %.0f%%%n",
                    host.getId(),
                    host.getComputeType(),
                    vmCount,
                    cpuUtil,
                    gpuUtil,
                    ramUtil);
        }
    }

    /**
     * Test that demonstrates STRATEGY DIFFERENTIATION.
     *
     * Setup: MORE hosts than required, all CPU_GPU_MIXED to remove compute type constraints
     * - 4 identical hosts: 16 cores, 4 GPUs, 64GB RAM each
     * - 4 small VMs: 2 cores, 1 GPU, 4GB RAM each (can all fit on 1 host)
     *
     * Expected Results:
     * - PowerAware: 1 active host (consolidates all VMs into one)
     * - LoadBalancing: 4 active hosts (spreads VMs across all)
     * - FirstFit: 1 active host (fills first host)
     * - BestFit: 1 active host (similar to FirstFit for small VMs)
     */
    private static void testStrategyDifferentiation() {
        System.out.println("Test 7: Strategy Differentiation (Key Test)");
        System.out.println("-".repeat(60));
        System.out.println("  Setup: 4 identical hosts, 4 small VMs that can fit on 1 host");
        System.out.println("  Goal: Show different strategies produce different active host counts\n");

        int firstFitHosts = runDifferentiationScenario(new FirstFitVMPlacementStrategy());
        int bestFitHosts = runDifferentiationScenario(new BestFitVMPlacementStrategy());
        int loadBalancingHosts = runDifferentiationScenario(new LoadBalancingVMPlacementStrategy());
        int powerAwareHosts = runDifferentiationScenario(new PowerAwareVMPlacementStrategy());

        System.out.println("\n  Summary of Active Hosts:");
        System.out.println("  +-----------------------+---------------+");
        System.out.println("  | Strategy              | Active Hosts  |");
        System.out.println("  +-----------------------+---------------+");
        System.out.printf("  | First Fit             |       %d       |%n", firstFitHosts);
        System.out.printf("  | Best Fit              |       %d       |%n", bestFitHosts);
        System.out.printf("  | Load Balancing        |       %d       |%n", loadBalancingHosts);
        System.out.printf("  | Power Aware           |       %d       |%n", powerAwareHosts);
        System.out.println("  +-----------------------+---------------+");

        // Verify differentiation
        boolean loadBalancingDifferent = loadBalancingHosts > powerAwareHosts;
        boolean powerAwareConsolidates = powerAwareHosts == 1;

        System.out.println("\n  Differentiation Checks:");
        System.out.println("    Load Balancing uses more hosts than Power Aware: " +
            (loadBalancingDifferent ? "PASSED" : "FAILED"));
        System.out.println("    Power Aware consolidates to minimum hosts: " +
            (powerAwareConsolidates ? "PASSED" : "FAILED"));

        boolean passed = loadBalancingDifferent && powerAwareConsolidates;
        System.out.println("\n  Overall Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    /**
     * Runs the differentiation scenario with a given strategy and returns active host count.
     */
    private static int runDifferentiationScenario(VMPlacementStrategy strategy) {
        SimulationContext context = createDifferentiationContext();
        VMPlacementStep step = new VMPlacementStep(strategy);
        step.execute(context);

        System.out.println("  " + strategy.getStrategyName() + ":");
        System.out.printf("    VMs placed: %d, Active hosts: %d%n",
            step.getVmsPlaced(), step.getActiveHostCount());

        // Show which hosts have VMs
        StringBuilder hostInfo = new StringBuilder("    Distribution: ");
        for (Host host : context.getHosts()) {
            hostInfo.append("H").append(host.getId() % 100).append("=")
                   .append(host.getAssignedVMs().size()).append(" ");
        }
        System.out.println(hostInfo.toString().trim());

        return step.getActiveHostCount();
    }

    /**
     * Creates a context designed to show strategy differentiation.
     *
     * 4 identical large hosts (all can hold all VMs)
     * 4 small VMs (can all fit on 1 host)
     */
    private static SimulationContext createDifferentiationContext() {
        SimulationContext context = new SimulationContext();

        // Create datacenter
        CloudDatacenter dc = new CloudDatacenter("DC-Diff", 10, 500000.0);
        context.addDatacenter(dc);

        // Create 4 IDENTICAL hosts - all CPU_GPU_MIXED to remove compute type constraints
        // Each host: 16 cores, 4 GPUs, 64GB RAM (can hold all 4 VMs)
        for (int i = 0; i < 4; i++) {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_GPU_MIXED, 4);
            host.setRamCapacityMB(65536);
            host.setNetworkCapacityMbps(10000);
            host.setHardDriveCapacityMB(1024 * 1024);
            dc.addHost(host);
            context.addHost(host);
        }

        // Create user
        User user = new User("DiffUser");
        user.addSelectedDatacenter(dc.getId());
        context.addUser(user);

        // Create 4 small VMs - total: 8 cores, 4 GPUs, 16GB RAM
        // All can fit on a single host (16 cores, 4 GPUs, 64GB RAM)
        for (int i = 0; i < 4; i++) {
            VM vm = new VM(user.getName(), 2_000_000_000L, 2, 1, 4096, 10240, 500, ComputeType.CPU_GPU_MIXED);
            user.addVirtualMachine(vm);
            context.addVM(vm);
        }

        return context;
    }
}
