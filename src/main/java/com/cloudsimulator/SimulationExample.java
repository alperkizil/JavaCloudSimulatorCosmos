package com.cloudsimulator;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationEngine;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.factory.PowerModelFactory;
import com.cloudsimulator.model.*;

/**
 * Example demonstrating the cloud simulation framework.
 * This creates a simple simulation with datacenters, hosts, users, VMs, and tasks.
 */
public class SimulationExample {

    public static void main(String[] args) {
        // Create simulation engine
        SimulationEngine engine = new SimulationEngine();
        engine.setRandomSeed(42);  // For repeatability
        engine.setDebugEnabled(true);

        // Add simulation steps
        engine.addStep(new InitializationStep());
        engine.addStep(new SimpleExecutionStep());
        engine.addStep(new ReportingStep());

        // Run simulation
        engine.run();

        // Print results
        SimulationContext context = engine.getContext();
        System.out.println("\n=== Simulation Results ===");
        System.out.println("Total Datacenters: " + context.getTotalDatacenterCount());
        System.out.println("Total Hosts: " + context.getTotalHostCount());
        System.out.println("Total Users: " + context.getUsers().size());
        System.out.println("Total VMs: " + context.getTotalVMCount());
        System.out.println("Total Tasks: " + context.getTotalTaskCount());
        System.out.println("Simulation Time: " + context.getCurrentTime() + " seconds");
    }

    /**
     * Step 1: Initialize the simulation with basic entities.
     */
    static class InitializationStep implements SimulationStep {
        @Override
        public void execute(SimulationContext context) {
            // Create datacenter
            CloudDatacenter dc1 = new CloudDatacenter("DC-East", 50, 100000.0);
            context.addDatacenter(dc1);

            // Create host
            Host host1 = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);
            host1.setPowerModel(PowerModelFactory.createPowerModel("StandardPowerModel"));
            context.addHost(host1);

            // Assign host to datacenter
            dc1.addHost(host1);
            host1.activate(context.getCurrentTime(), dc1.getId());
            dc1.activate(context.getCurrentTime());

            // Create user
            User user1 = new User("Alice");
            user1.addSelectedDatacenter(dc1.getId());
            user1.startSession(context.getCurrentTime());
            context.addUser(user1);

            // Create VM
            VM vm1 = new VM("Alice", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            context.addVM(vm1);
            user1.addVirtualMachine(vm1);

            // Assign VM to host
            host1.assignVM(vm1);
            vm1.activate(context.getCurrentTime(), host1.getId());
            vm1.start();

            // Create tasks
            Task task1 = new Task("Compress-1", "Alice", 10_000_000_000L, WorkloadType.SEVEN_ZIP, context.getCurrentTime());
            Task task2 = new Task("Query-1", "Alice", 5_000_000_000L, WorkloadType.DATABASE, context.getCurrentTime());

            context.addTask(task1);
            context.addTask(task2);
            user1.addTask(task1);
            user1.addTask(task2);

            // Assign tasks to VM
            vm1.assignTask(task1);
            vm1.assignTask(task2);

            System.out.println("Initialized: 1 datacenter, 1 host, 1 user, 1 VM, 2 tasks");
        }

        @Override
        public String getStepName() {
            return "Initialization";
        }
    }

    /**
     * Step 2: Execute the simulation for a fixed number of seconds.
     */
    static class SimpleExecutionStep implements SimulationStep {
        @Override
        public void execute(SimulationContext context) {
            int maxSimulationSeconds = 10; // Run for 10 seconds

            System.out.println("Starting execution for " + maxSimulationSeconds + " seconds...");

            for (int i = 0; i < maxSimulationSeconds; i++) {
                // Advance time
                context.advanceTime();
                long currentTime = context.getCurrentTime();

                // Execute VMs
                for (VM vm : context.getVms()) {
                    vm.executeOneSecond(currentTime);
                    vm.updateState();
                }

                // Update hosts
                for (Host host : context.getHosts()) {
                    host.updateState();
                }

                // Update datacenters
                for (CloudDatacenter dc : context.getDatacenters()) {
                    dc.incrementActiveSeconds();
                    dc.updateTotalMomentaryPowerDraw();
                }

                if (i % 2 == 0 || i == maxSimulationSeconds - 1) {
                    System.out.println("Time: " + currentTime + "s");
                }
            }

            System.out.println("Execution completed at time: " + context.getCurrentTime());
        }

        @Override
        public String getStepName() {
            return "Execution";
        }
    }

    /**
     * Step 3: Report results.
     */
    static class ReportingStep implements SimulationStep {
        @Override
        public void execute(SimulationContext context) {
            System.out.println("\n=== Energy Consumption Report ===");

            for (CloudDatacenter dc : context.getDatacenters()) {
                System.out.println("\nDatacenter: " + dc.getName());
                System.out.println("  Active Seconds: " + dc.getActiveSeconds());
                System.out.println("  Total Energy: " + String.format("%.2f", dc.getTotalEnergyConsumedKWh()) + " kWh");
                System.out.println("  Current Power: " + String.format("%.2f", dc.getTotalMomentaryPowerDraw()) + " W");

                for (Host host : dc.getHosts()) {
                    System.out.println("    Host-" + host.getId() +
                            ": Energy=" + String.format("%.2f", host.getTotalEnergyConsumedKWh()) + " kWh, " +
                            "Power=" + String.format("%.2f", host.getCurrentTotalPowerDraw()) + " W");
                }
            }

            System.out.println("\n=== Task Completion Report ===");
            for (Task task : context.getTasks()) {
                System.out.println("Task: " + task.getName() +
                        ", Status: " + task.getTaskExecutionStatus() +
                        ", Progress: " + String.format("%.1f", task.getProgressPercentage()) + "%");
            }
        }

        @Override
        public String getStepName() {
            return "Reporting";
        }
    }
}
