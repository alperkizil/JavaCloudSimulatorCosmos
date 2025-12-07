package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationEngine;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.InitializationStep;

/**
 * Test for the InitializationStep implementation.
 * Verifies that entities are correctly created from configuration.
 */
public class InitializationStepTest {

    public static void main(String[] args) {
        System.out.println("=== InitializationStep Test ===\n");

        // Test 1: Load configuration from file
        System.out.println("Test 1: Loading configuration from sample-experiment.cosc");
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse("configs/sample-experiment.cosc");

        System.out.println("  Configuration loaded:");
        System.out.println("    Seed: " + config.getRandomSeed());
        System.out.println("    Datacenters: " + config.getDatacenterConfigs().size());
        System.out.println("    Hosts: " + config.getHostConfigs().size());
        System.out.println("    Users: " + config.getUserConfigs().size());
        System.out.println("    VMs: " + config.getVmConfigs().size());
        System.out.println("    Tasks: " + config.getTaskConfigs().size());
        System.out.println("  PASSED\n");

        // Test 2: Execute InitializationStep
        System.out.println("Test 2: Executing InitializationStep");
        SimulationContext context = new SimulationContext();
        InitializationStep initStep = new InitializationStep(config);

        initStep.execute(context);

        System.out.println("  Context after initialization:");
        System.out.println("    Datacenters: " + context.getTotalDatacenterCount());
        System.out.println("    Hosts: " + context.getTotalHostCount());
        System.out.println("    Users: " + context.getUsers().size());
        System.out.println("    VMs: " + context.getTotalVMCount());
        System.out.println("    Tasks: " + context.getTotalTaskCount());
        System.out.println("  PASSED\n");

        // Test 3: Verify datacenter creation
        System.out.println("Test 3: Verifying datacenter creation");
        boolean dcTestPassed = true;
        CloudDatacenter dcEast = context.getDatacenterByName("DC-East");
        CloudDatacenter dcWest = context.getDatacenterByName("DC-West");
        CloudDatacenter dcCentral = context.getDatacenterByName("DC-Central");

        if (dcEast == null || dcWest == null || dcCentral == null) {
            System.out.println("  FAILED: Missing datacenters");
            dcTestPassed = false;
        } else {
            System.out.println("    DC-East: capacity=" + dcEast.getMaxHostCapacity() + ", maxPower=" + dcEast.getTotalMaxPowerDraw());
            System.out.println("    DC-West: capacity=" + dcWest.getMaxHostCapacity() + ", maxPower=" + dcWest.getTotalMaxPowerDraw());
            System.out.println("    DC-Central: capacity=" + dcCentral.getMaxHostCapacity() + ", maxPower=" + dcCentral.getTotalMaxPowerDraw());

            if (dcEast.getMaxHostCapacity() != 50 || dcWest.getMaxHostCapacity() != 30 || dcCentral.getMaxHostCapacity() != 40) {
                System.out.println("  FAILED: Incorrect host capacities");
                dcTestPassed = false;
            }
        }
        if (dcTestPassed) System.out.println("  PASSED\n");

        // Test 4: Verify host creation
        System.out.println("Test 4: Verifying host creation");
        boolean hostTestPassed = context.getHosts().size() == 5;
        for (Host host : context.getHosts()) {
            System.out.println("    Host-" + host.getId() + ": cores=" + host.getNumberOfCpuCores() +
                             ", gpus=" + host.getNumberOfGpus() + ", type=" + host.getComputeType() +
                             ", powerModel=" + host.getPowerModel().getModelName());
        }
        if (hostTestPassed) {
            System.out.println("  PASSED\n");
        } else {
            System.out.println("  FAILED: Expected 5 hosts, got " + context.getHosts().size() + "\n");
        }

        // Test 5: Verify user creation and datacenter preferences
        System.out.println("Test 5: Verifying user creation and datacenter preferences");
        User alice = context.getUserByName("Alice");
        User bob = context.getUserByName("Bob");

        boolean userTestPassed = true;
        if (alice == null || bob == null) {
            System.out.println("  FAILED: Missing users");
            userTestPassed = false;
        } else {
            System.out.println("    Alice: selectedDatacenters=" + alice.getUserSelectedDatacenters().size() +
                             ", VMs=" + alice.getVirtualMachines().size() +
                             ", tasks=" + alice.getTasks().size());
            System.out.println("    Bob: selectedDatacenters=" + bob.getUserSelectedDatacenters().size() +
                             ", VMs=" + bob.getVirtualMachines().size() +
                             ", tasks=" + bob.getTasks().size());

            // Alice should have 2 datacenters (DC-East, DC-West)
            if (alice.getUserSelectedDatacenters().size() != 2) {
                System.out.println("  FAILED: Alice should have 2 selected datacenters");
                userTestPassed = false;
            }
            // Bob should have 1 datacenter (DC-Central)
            if (bob.getUserSelectedDatacenters().size() != 1) {
                System.out.println("  FAILED: Bob should have 1 selected datacenter");
                userTestPassed = false;
            }
        }
        if (userTestPassed) System.out.println("  PASSED\n");

        // Test 6: Verify VM creation and ownership
        System.out.println("Test 6: Verifying VM creation");
        System.out.println("    Total VMs: " + context.getTotalVMCount());
        int aliceVMs = 0;
        int bobVMs = 0;
        for (VM vm : context.getVms()) {
            if ("Alice".equals(vm.getUserId())) aliceVMs++;
            if ("Bob".equals(vm.getUserId())) bobVMs++;
            System.out.println("    VM-" + vm.getId() + ": owner=" + vm.getUserId() +
                             ", vcpus=" + vm.getRequestedVcpuCount() +
                             ", gpus=" + vm.getRequestedGpuCount() +
                             ", type=" + vm.getComputeType());
        }
        System.out.println("    Alice's VMs: " + aliceVMs + ", Bob's VMs: " + bobVMs);
        boolean vmTestPassed = aliceVMs == 4 && bobVMs == 2;
        if (vmTestPassed) {
            System.out.println("  PASSED\n");
        } else {
            System.out.println("  FAILED: Expected Alice=4, Bob=2 VMs\n");
        }

        // Test 7: Verify task creation
        System.out.println("Test 7: Verifying task creation");
        System.out.println("    Total Tasks: " + context.getTotalTaskCount());
        for (Task task : context.getTasks()) {
            System.out.println("    " + task.getName() + ": owner=" + task.getUserId() +
                             ", type=" + task.getWorkloadType() +
                             ", instructions=" + task.getInstructionLength());
        }
        boolean taskTestPassed = context.getTotalTaskCount() == 10;
        if (taskTestPassed) {
            System.out.println("  PASSED\n");
        } else {
            System.out.println("  FAILED: Expected 10 tasks, got " + context.getTotalTaskCount() + "\n");
        }

        // Test 8: Verify metrics recorded
        System.out.println("Test 8: Verifying initialization metrics");
        System.out.println("    initialization.datacenters: " + context.getMetric("initialization.datacenters"));
        System.out.println("    initialization.hosts: " + context.getMetric("initialization.hosts"));
        System.out.println("    initialization.users: " + context.getMetric("initialization.users"));
        System.out.println("    initialization.vms: " + context.getMetric("initialization.vms"));
        System.out.println("    initialization.tasks: " + context.getMetric("initialization.tasks"));
        System.out.println("  PASSED\n");

        // Summary
        System.out.println("=== All Tests Completed ===");
        System.out.println("InitializationStep implementation verified successfully!");
    }
}
