package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.InitializationStep;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.steps.UserDatacenterMappingStep;
import com.cloudsimulator.strategy.FirstFitHostPlacementStrategy;
import com.cloudsimulator.utils.RandomGenerator;

/**
 * Test for the UserDatacenterMappingStep implementation.
 * Verifies that users are correctly mapped to datacenters and
 * invalid datacenters are removed from preferences.
 */
public class UserDatacenterMappingStepTest {

    public static void main(String[] args) {
        System.out.println("=== UserDatacenterMappingStep Test ===\n");

        // Initialize RandomGenerator with a seed for repeatability
        RandomGenerator.initialize(42);

        testNormalFlow();
        testReassignmentWhenNoValidDCs();
        testResourceRequirementsCalculation();
        testEmptyDatacenterRemoval();
        testNoHostsError();
        testMetricsRecording();

        System.out.println("\n=== All Tests Completed ===");
    }

    /**
     * Test normal flow with sample configuration.
     */
    private static void testNormalFlow() {
        System.out.println("Test 1: Normal Flow with Sample Configuration");
        System.out.println("-".repeat(55));

        // Reset random generator
        RandomGenerator.getInstance().reset(42);

        // Load and run initialization + host placement
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse("configs/sample-experiment.cosc");
        SimulationContext context = new SimulationContext();

        InitializationStep initStep = new InitializationStep(config);
        initStep.execute(context);

        HostPlacementStep hostStep = new HostPlacementStep(new FirstFitHostPlacementStrategy());
        hostStep.execute(context);

        // Show state before user mapping
        System.out.println("  Before UserDatacenterMappingStep:");
        for (User user : context.getUsers()) {
            System.out.println("    " + user.getName() + ": " +
                user.getUserSelectedDatacenters().size() + " preferred DCs, " +
                "session started: " + (user.getStartTimestamp() != null));
        }

        // Execute user mapping
        UserDatacenterMappingStep mappingStep = new UserDatacenterMappingStep();
        mappingStep.execute(context);

        // Show state after user mapping
        System.out.println("  After UserDatacenterMappingStep:");
        for (User user : context.getUsers()) {
            System.out.println("    " + user.getName() + ": " +
                user.getUserSelectedDatacenters().size() + " valid DCs, " +
                "session started: " + (user.getStartTimestamp() != null));
        }

        System.out.println("  Users processed: " + mappingStep.getUsersProcessed());
        System.out.println("  Valid mappings: " + mappingStep.getValidMappings());
        System.out.println("  Reassigned users: " + mappingStep.getReassignedUsers());

        boolean passed = mappingStep.getUsersProcessed() == 2 &&
                        context.getUsers().stream().allMatch(u -> u.getStartTimestamp() != null);
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    /**
     * Test that users are reassigned when their preferred DCs have no hosts.
     */
    private static void testReassignmentWhenNoValidDCs() {
        System.out.println("Test 2: Reassignment When No Valid DCs");
        System.out.println("-".repeat(55));

        RandomGenerator.getInstance().reset(42);
        SimulationContext context = new SimulationContext();

        // Create datacenters - only DC2 will have hosts
        CloudDatacenter dc1 = new CloudDatacenter("DC-Empty", 10, 10000.0);
        CloudDatacenter dc2 = new CloudDatacenter("DC-WithHosts", 10, 10000.0);
        context.addDatacenter(dc1);
        context.addDatacenter(dc2);

        // Add host only to DC2
        Host host = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
        host.setPowerModel(new PowerModel());
        dc2.addHost(host);
        context.addHost(host);

        // Create user who only prefers DC-Empty (no hosts)
        User user = new User("TestUser");
        user.addSelectedDatacenter(dc1.getId());  // This DC has no hosts!
        dc1.addCustomer(user.getName());
        context.addUser(user);

        System.out.println("  Setup: User prefers DC-Empty (no hosts), DC-WithHosts exists");
        System.out.println("  User's initial preferences: " + user.getUserSelectedDatacenters());

        // Execute mapping
        UserDatacenterMappingStep mappingStep = new UserDatacenterMappingStep();
        mappingStep.execute(context);

        System.out.println("  User's final preferences: " + user.getUserSelectedDatacenters());
        System.out.println("  Reassigned users: " + mappingStep.getReassignedUsers());

        // User should be reassigned to DC-WithHosts
        boolean passed = mappingStep.getReassignedUsers() == 1 &&
                        user.getUserSelectedDatacenters().contains(dc2.getId());
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    /**
     * Test resource requirements calculation.
     */
    private static void testResourceRequirementsCalculation() {
        System.out.println("Test 3: Resource Requirements Calculation");
        System.out.println("-".repeat(55));

        RandomGenerator.getInstance().reset(42);
        SimulationContext context = new SimulationContext();

        // Create datacenter with host
        CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 10000.0);
        Host host = new Host(2500000000L, 32, ComputeType.CPU_GPU_MIXED, 8);
        host.setRamCapacityMB(64 * 1024); // 64GB
        host.setPowerModel(new PowerModel());
        dc.addHost(host);
        context.addDatacenter(dc);
        context.addHost(host);

        // Create user with VMs
        User user = new User("ResourceUser");
        user.addSelectedDatacenter(dc.getId());

        // Add VMs with specific resource requirements
        VM vm1 = new VM("ResourceUser", 2000000000L, 4, 2, 8192, 102400, 1000, ComputeType.CPU_GPU_MIXED);
        VM vm2 = new VM("ResourceUser", 2000000000L, 8, 0, 16384, 204800, 2000, ComputeType.CPU_ONLY);
        user.addVirtualMachine(vm1);
        user.addVirtualMachine(vm2);
        context.addUser(user);
        context.addVM(vm1);
        context.addVM(vm2);

        // Execute mapping
        UserDatacenterMappingStep mappingStep = new UserDatacenterMappingStep();
        mappingStep.execute(context);

        // Check resource requirements
        UserDatacenterMappingStep.UserResourceRequirements req =
            mappingStep.getUserResourceRequirements().get("ResourceUser");

        System.out.println("  VMs: " + req.vmCount);
        System.out.println("  Total vCPUs: " + req.totalVcpus + " (expected: 12)");
        System.out.println("  Total GPUs: " + req.totalGpus + " (expected: 2)");
        System.out.println("  Total RAM: " + req.totalRamMB + "MB (expected: 24576)");

        boolean passed = req.vmCount == 2 &&
                        req.totalVcpus == 12 &&
                        req.totalGpus == 2 &&
                        req.totalRamMB == 24576;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    /**
     * Test that empty datacenters are removed from user preferences.
     */
    private static void testEmptyDatacenterRemoval() {
        System.out.println("Test 4: Empty Datacenter Removal from Preferences");
        System.out.println("-".repeat(55));

        RandomGenerator.getInstance().reset(42);
        SimulationContext context = new SimulationContext();

        // Create 3 datacenters - only 2 will have hosts
        CloudDatacenter dc1 = new CloudDatacenter("DC-HasHosts1", 10, 10000.0);
        CloudDatacenter dc2 = new CloudDatacenter("DC-Empty", 10, 10000.0);
        CloudDatacenter dc3 = new CloudDatacenter("DC-HasHosts2", 10, 10000.0);
        context.addDatacenter(dc1);
        context.addDatacenter(dc2);
        context.addDatacenter(dc3);

        // Add hosts to DC1 and DC3, not DC2
        Host host1 = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
        host1.setPowerModel(new PowerModel());
        dc1.addHost(host1);
        context.addHost(host1);

        Host host2 = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
        host2.setPowerModel(new PowerModel());
        dc3.addHost(host2);
        context.addHost(host2);

        // Create user who prefers all 3 DCs
        User user = new User("MultiDCUser");
        user.addSelectedDatacenter(dc1.getId());
        user.addSelectedDatacenter(dc2.getId());  // Empty - should be removed
        user.addSelectedDatacenter(dc3.getId());
        context.addUser(user);

        System.out.println("  User's initial preferences: " + user.getUserSelectedDatacenters().size() + " DCs");
        System.out.println("    IDs: " + user.getUserSelectedDatacenters());

        // Execute mapping
        UserDatacenterMappingStep mappingStep = new UserDatacenterMappingStep();
        mappingStep.execute(context);

        System.out.println("  User's final preferences: " + user.getUserSelectedDatacenters().size() + " DCs");
        System.out.println("    IDs: " + user.getUserSelectedDatacenters());

        // DC2 (empty) should be removed
        boolean passed = user.getUserSelectedDatacenters().size() == 2 &&
                        user.getUserSelectedDatacenters().contains(dc1.getId()) &&
                        !user.getUserSelectedDatacenters().contains(dc2.getId()) &&
                        user.getUserSelectedDatacenters().contains(dc3.getId());
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    /**
     * Test that simulation terminates if no datacenter has hosts.
     */
    private static void testNoHostsError() {
        System.out.println("Test 5: Error When No Datacenter Has Hosts");
        System.out.println("-".repeat(55));

        RandomGenerator.getInstance().reset(42);
        SimulationContext context = new SimulationContext();

        // Create datacenters with NO hosts
        CloudDatacenter dc1 = new CloudDatacenter("DC-Empty1", 10, 10000.0);
        CloudDatacenter dc2 = new CloudDatacenter("DC-Empty2", 10, 10000.0);
        context.addDatacenter(dc1);
        context.addDatacenter(dc2);

        User user = new User("StrandedUser");
        user.addSelectedDatacenter(dc1.getId());
        context.addUser(user);

        boolean errorThrown = false;
        try {
            UserDatacenterMappingStep mappingStep = new UserDatacenterMappingStep();
            mappingStep.execute(context);
        } catch (RuntimeException e) {
            errorThrown = true;
            System.out.println("  Expected error caught: " + e.getMessage().substring(0, 50) + "...");
        }

        System.out.println("  Result: " + (errorThrown ? "PASSED" : "FAILED"));
        System.out.println();
    }

    /**
     * Test that metrics are correctly recorded.
     */
    private static void testMetricsRecording() {
        System.out.println("Test 6: Metrics Recording");
        System.out.println("-".repeat(55));

        RandomGenerator.getInstance().reset(42);

        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse("configs/sample-experiment.cosc");
        SimulationContext context = new SimulationContext();

        InitializationStep initStep = new InitializationStep(config);
        initStep.execute(context);

        HostPlacementStep hostStep = new HostPlacementStep(new FirstFitHostPlacementStrategy());
        hostStep.execute(context);

        UserDatacenterMappingStep mappingStep = new UserDatacenterMappingStep();
        mappingStep.execute(context);

        // Check metrics
        System.out.println("  Recorded metrics:");
        System.out.println("    userMapping.usersProcessed: " +
            context.getMetric("userMapping.usersProcessed"));
        System.out.println("    userMapping.validMappings: " +
            context.getMetric("userMapping.validMappings"));
        System.out.println("    userMapping.reassignedUsers: " +
            context.getMetric("userMapping.reassignedUsers"));
        System.out.println("    userMapping.totalRequiredVcpus: " +
            context.getMetric("userMapping.totalRequiredVcpus"));
        System.out.println("    userMapping.totalRequiredGpus: " +
            context.getMetric("userMapping.totalRequiredGpus"));
        System.out.println("    userMapping.totalRequiredRamMB: " +
            context.getMetric("userMapping.totalRequiredRamMB"));

        boolean passed = context.getMetric("userMapping.usersProcessed") != null &&
                        context.getMetric("userMapping.totalRequiredVcpus") != null;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }
}
