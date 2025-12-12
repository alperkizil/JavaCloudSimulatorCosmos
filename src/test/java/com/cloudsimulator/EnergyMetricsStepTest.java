package com.cloudsimulator;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.*;
import com.cloudsimulator.PlacementStrategy.task.FirstAvailableTaskAssignmentStrategy;

/**
 * Test for EnergyCalculationStep (Stage 8) and MetricsCollectionStep (Stage 9).
 *
 * Tests energy calculation, carbon footprint, SLA compliance, and metrics aggregation.
 */
public class EnergyMetricsStepTest {

    public static void main(String[] args) {
        System.out.println("=== EnergyCalculationStep & MetricsCollectionStep Test ===\n");

        // Energy calculation tests
        testBasicEnergyCalculation();
        testCarbonIntensityRegions();
        testPUECalculation();

        // Metrics collection tests
        testBasicMetricsCollection();
        testSLACompliance();
        testMultipleSLAThresholds();

        // Full pipeline test
        testFullPipelineWithEnergyAndMetrics();

        // JSON output test
        testJSONSerialization();

        System.out.println("\n=== All Tests Completed ===");
    }

    /**
     * Creates a test context with simulated execution results.
     */
    private static SimulationContext createTestContext() {
        SimulationContext context = new SimulationContext();

        // Create datacenter
        CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);
        context.addDatacenter(dc);

        // Create host with power model
        Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);
        host.setRamCapacityMB(65536);
        host.setNetworkCapacityMbps(10000);
        host.setHardDriveCapacityMB(1024 * 1024);
        dc.addHost(host);
        context.addHost(host);

        // Create user
        User user = new User("TestUser");
        user.addSelectedDatacenter(dc.getId());
        user.startSession(0);
        context.addUser(user);

        // Create VM
        VM vm = new VM(user.getName(), 2_000_000_000L, 4, 0, 8192, 51200, 1000, ComputeType.CPU_ONLY);
        host.assignVM(vm);
        vm.start();
        user.addVirtualMachine(vm);
        context.addVM(vm);

        // Create tasks
        Task task1 = new Task("Task1", user.getName(), 8_000_000_000L, WorkloadType.SEVEN_ZIP);
        Task task2 = new Task("Task2", user.getName(), 16_000_000_000L, WorkloadType.DATABASE);
        Task task3 = new Task("Task3", user.getName(), 4_000_000_000L, WorkloadType.CINEBENCH);

        user.addTask(task1);
        user.addTask(task2);
        user.addTask(task3);
        context.addTask(task1);
        context.addTask(task2);
        context.addTask(task3);

        // Assign tasks
        task1.assignToVM(vm.getId(), 0);
        vm.assignTask(task1);
        task2.assignToVM(vm.getId(), 0);
        vm.assignTask(task2);
        task3.assignToVM(vm.getId(), 0);
        vm.assignTask(task3);

        return context;
    }

    private static void testBasicEnergyCalculation() {
        System.out.println("Test 1: Basic Energy Calculation");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();

        // Run VMExecutionStep first to generate energy data
        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        // Run TaskExecutionStep
        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        // Run EnergyCalculationStep
        EnergyCalculationStep energyStep = new EnergyCalculationStep();
        energyStep.setPUE(1.5);
        energyStep.setCarbonIntensity(EnergyCalculationStep.CarbonIntensityRegion.US_AVERAGE);
        energyStep.setElectricityCostPerKWh(0.12);
        energyStep.execute(context);

        System.out.println("  Results:");
        System.out.println("    Simulation duration: " + energyStep.getSimulationDurationSeconds() + " seconds");
        System.out.println("    IT Energy: " + String.format("%.6f", energyStep.getTotalITEnergyKWh()) + " kWh");
        System.out.println("    Facility Energy (PUE=1.5): " + String.format("%.6f", energyStep.getTotalFacilityEnergyKWh()) + " kWh");
        System.out.println("    Average Power: " + String.format("%.2f", energyStep.getAveragePowerWatts()) + " W");
        System.out.println("    Carbon Footprint: " + String.format("%.6f", energyStep.getCarbonFootprintKg()) + " kg CO2");
        System.out.println("    Estimated Cost: $" + String.format("%.4f", energyStep.getEstimatedCostDollars()));
        System.out.println("    Tasks Completed: " + energyStep.getTasksCompleted());

        boolean passed = energyStep.getTotalITEnergyKWh() > 0 &&
                        energyStep.getTotalFacilityEnergyKWh() > energyStep.getTotalITEnergyKWh() &&
                        energyStep.getCarbonFootprintKg() > 0;

        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testCarbonIntensityRegions() {
        System.out.println("Test 2: Carbon Intensity Regions");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();

        // Run execution
        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);
        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        System.out.println("  Carbon footprint by region (for same workload):");

        EnergyCalculationStep.CarbonIntensityRegion[] regions = {
            EnergyCalculationStep.CarbonIntensityRegion.EU_FRANCE,
            EnergyCalculationStep.CarbonIntensityRegion.EU_NORDICS,
            EnergyCalculationStep.CarbonIntensityRegion.US_AVERAGE,
            EnergyCalculationStep.CarbonIntensityRegion.CHINA,
            EnergyCalculationStep.CarbonIntensityRegion.EU_POLAND
        };

        double previousCarbon = 0;
        boolean ordered = true;

        for (EnergyCalculationStep.CarbonIntensityRegion region : regions) {
            // Create fresh context for each region test
            SimulationContext testContext = createTestContext();
            new VMExecutionStep().execute(testContext);
            new TaskExecutionStep().execute(testContext);

            EnergyCalculationStep energyStep = new EnergyCalculationStep();
            energyStep.setCarbonIntensity(region);
            energyStep.execute(testContext);

            System.out.printf("    %-15s: %.6f kg CO2 (intensity: %.2f kg/kWh)%n",
                region.name(), energyStep.getCarbonFootprintKg(), region.getKgCO2PerKWh());

            if (energyStep.getCarbonFootprintKg() < previousCarbon) {
                ordered = false;
            }
            previousCarbon = energyStep.getCarbonFootprintKg();
        }

        System.out.println("  Result: " + (ordered ? "PASSED" : "FAILED (order check)"));
        System.out.println();
    }

    private static void testPUECalculation() {
        System.out.println("Test 3: PUE Calculation");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);
        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        EnergyCalculationStep energyStep = new EnergyCalculationStep();
        energyStep.setPUE(1.0); // Perfect PUE
        energyStep.execute(context);

        double itEnergy = energyStep.getTotalITEnergyKWh();
        double facilityEnergy10 = energyStep.getTotalFacilityEnergyKWh();

        System.out.println("  PUE=1.0 (perfect): IT=" + String.format("%.6f", itEnergy) +
            " kWh, Facility=" + String.format("%.6f", facilityEnergy10) + " kWh");

        // Test with PUE 2.0
        context = createTestContext();
        new VMExecutionStep().execute(context);
        new TaskExecutionStep().execute(context);

        energyStep = new EnergyCalculationStep();
        energyStep.setPUE(2.0);
        energyStep.execute(context);

        double facilityEnergy20 = energyStep.getTotalFacilityEnergyKWh();

        System.out.println("  PUE=2.0 (inefficient): IT=" + String.format("%.6f", energyStep.getTotalITEnergyKWh()) +
            " kWh, Facility=" + String.format("%.6f", facilityEnergy20) + " kWh");

        // Facility energy with PUE 2.0 should be ~2x PUE 1.0
        double ratio = facilityEnergy20 / facilityEnergy10;
        System.out.println("  Ratio (PUE 2.0 / PUE 1.0): " + String.format("%.2f", ratio));

        boolean passed = Math.abs(ratio - 2.0) < 0.01; // Should be approximately 2.0
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testBasicMetricsCollection() {
        System.out.println("Test 4: Basic Metrics Collection");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();

        // Run full pipeline
        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        EnergyCalculationStep energyStep = new EnergyCalculationStep();
        energyStep.execute(context);

        MetricsCollectionStep metricsStep = new MetricsCollectionStep();
        metricsStep.execute(context);

        SimulationSummary summary = metricsStep.getSummary();

        System.out.println("  Infrastructure:");
        System.out.println("    Datacenters: " + summary.getInfrastructure().datacenterCount);
        System.out.println("    Hosts: " + summary.getInfrastructure().hostCount);
        System.out.println("    Active Hosts: " + summary.getInfrastructure().activeHostCount);
        System.out.println("    VMs: " + summary.getInfrastructure().vmCount);

        System.out.println("  Tasks:");
        System.out.println("    Total: " + summary.getTasks().totalTasks);
        System.out.println("    Completed: " + summary.getTasks().completedTasks);
        System.out.println("    Completion Rate: " + String.format("%.1f%%", summary.getTasks().completionRate * 100));

        System.out.println("  Performance:");
        System.out.println("    Makespan: " + summary.getPerformance().makespanSeconds + " seconds");
        System.out.println("    P90 Turnaround: " + String.format("%.1f", summary.getPerformance().p90TurnaroundTimeSeconds) + " seconds");
        System.out.println("    Load Balance Index: " + String.format("%.4f", summary.getPerformance().loadBalanceIndex));

        boolean passed = summary.getInfrastructure().datacenterCount == 1 &&
                        summary.getTasks().completedTasks == 3 &&
                        summary.getPerformance().makespanSeconds > 0;

        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testSLACompliance() {
        System.out.println("Test 5: SLA Compliance");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        EnergyCalculationStep energyStep = new EnergyCalculationStep();
        energyStep.execute(context);

        // Get makespan to set appropriate SLA threshold
        long makespan = taskExecStep.getMakespan();

        MetricsCollectionStep metricsStep = new MetricsCollectionStep();
        // Set SLA threshold higher than makespan so all tasks pass
        metricsStep.setPrimarySLAThreshold(makespan * 2);
        metricsStep.execute(context);

        SimulationSummary.SLASummary sla = metricsStep.getSummary().getSla();

        System.out.println("  SLA Threshold: " + sla.slaThresholdSeconds + " seconds");
        System.out.println("  Tasks Within SLA: " + sla.tasksWithinSLA);
        System.out.println("  Tasks Beyond SLA: " + sla.tasksBeyondSLA);
        System.out.println("  SLA Compliance: " + String.format("%.1f%%", sla.slaCompliancePercent));

        // With threshold > makespan, all tasks should be within SLA
        boolean passed = sla.tasksWithinSLA == 3 && sla.tasksBeyondSLA == 0 &&
                        sla.slaCompliancePercent == 100.0;

        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testMultipleSLAThresholds() {
        System.out.println("Test 6: Multiple SLA Thresholds");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        EnergyCalculationStep energyStep = new EnergyCalculationStep();
        energyStep.execute(context);

        MetricsCollectionStep metricsStep = new MetricsCollectionStep();
        metricsStep.setSLAThresholds(1L, 5L, 10L, 60L, 300L, 3600L);
        metricsStep.execute(context);

        SimulationSummary.SLASummary sla = metricsStep.getSummary().getSla();

        System.out.println("  SLA Compliance by Threshold:");
        for (Long threshold : sla.complianceByThreshold.keySet()) {
            System.out.printf("    %5d seconds: %6.2f%%%n",
                threshold, sla.complianceByThreshold.get(threshold));
        }

        // Longer thresholds should have higher compliance
        boolean passed = sla.complianceByThreshold.size() == 6;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testFullPipelineWithEnergyAndMetrics() {
        System.out.println("Test 7: Full Pipeline Integration");
        System.out.println("-".repeat(60));

        SimulationContext context = new SimulationContext();

        // Create 2 datacenters
        CloudDatacenter dc1 = new CloudDatacenter("DC-East", 10, 100000.0);
        CloudDatacenter dc2 = new CloudDatacenter("DC-West", 10, 100000.0);
        context.addDatacenter(dc1);
        context.addDatacenter(dc2);

        // Create hosts
        Host host1 = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);
        host1.setRamCapacityMB(65536);
        dc1.addHost(host1);
        context.addHost(host1);

        Host host2 = new Host(3_000_000_000L, 32, ComputeType.CPU_GPU_MIXED, 4);
        host2.setRamCapacityMB(131072);
        host2.setNumberOfGpus(4);
        dc2.addHost(host2);
        context.addHost(host2);

        // Create users
        User user1 = new User("Alice");
        user1.addSelectedDatacenter(dc1.getId());
        user1.startSession(0);
        context.addUser(user1);

        User user2 = new User("Bob");
        user2.addSelectedDatacenter(dc2.getId());
        user2.startSession(0);
        context.addUser(user2);

        // Create VMs
        VM vm1 = new VM(user1.getName(), 2_000_000_000L, 4, 0, 8192, 51200, 1000, ComputeType.CPU_ONLY);
        host1.assignVM(vm1);
        vm1.start();
        user1.addVirtualMachine(vm1);
        context.addVM(vm1);

        VM vm2 = new VM(user2.getName(), 2_000_000_000L, 4, 2, 16384, 102400, 2000, ComputeType.CPU_GPU_MIXED);
        host2.assignVM(vm2);
        vm2.start();
        user2.addVirtualMachine(vm2);
        context.addVM(vm2);

        // Create diverse tasks
        Task task1 = new Task("Compress", user1.getName(), 8_000_000_000L, WorkloadType.SEVEN_ZIP);
        Task task2 = new Task("Query", user1.getName(), 4_000_000_000L, WorkloadType.DATABASE);
        Task task3 = new Task("GPUInfer", user2.getName(), 8_000_000_000L, WorkloadType.LLM_GPU);
        Task task4 = new Task("Render", user2.getName(), 16_000_000_000L, WorkloadType.CINEBENCH);

        for (Task t : new Task[]{task1, task2}) {
            user1.addTask(t);
            context.addTask(t);
            t.assignToVM(vm1.getId(), 0);
            vm1.assignTask(t);
        }

        for (Task t : new Task[]{task3, task4}) {
            user2.addTask(t);
            context.addTask(t);
            t.assignToVM(vm2.getId(), 0);
            vm2.assignTask(t);
        }

        System.out.println("  Setup:");
        System.out.println("    Datacenters: 2 (DC-East, DC-West)");
        System.out.println("    Hosts: 2 (CPU-only, GPU-Mixed)");
        System.out.println("    Users: 2 (Alice, Bob)");
        System.out.println("    VMs: 2");
        System.out.println("    Tasks: 4");

        // Execute all steps
        System.out.println("\n  Executing simulation pipeline...");

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        EnergyCalculationStep energyStep = new EnergyCalculationStep();
        energyStep.setPUE(1.4);
        energyStep.setCarbonIntensity(EnergyCalculationStep.CarbonIntensityRegion.EU_AVERAGE);
        energyStep.setElectricityCostPerKWh(0.15);
        energyStep.execute(context);

        MetricsCollectionStep metricsStep = new MetricsCollectionStep();
        metricsStep.addSLAThreshold(60L);
        metricsStep.addSLAThreshold(120L);
        metricsStep.execute(context);

        SimulationSummary summary = metricsStep.getSummary();

        System.out.println("\n  Results:");
        System.out.println("    Simulation Duration: " + summary.getSimulationDurationSeconds() + " seconds");
        System.out.println("    Tasks Completed: " + summary.getTasks().completedTasks + "/" + summary.getTasks().totalTasks);
        System.out.println("    Total Energy: " + String.format("%.6f", summary.getEnergy().totalFacilityEnergyKWh) + " kWh");
        System.out.println("    Carbon Footprint: " + String.format("%.6f", summary.getEnergy().carbonFootprintKg) + " kg CO2");
        System.out.println("    Estimated Cost: $" + String.format("%.4f", summary.getEnergy().estimatedCostDollars));
        System.out.println("    SLA Compliance: " + String.format("%.1f%%", summary.getSla().slaCompliancePercent));

        System.out.println("\n  Per-Datacenter Summary:");
        for (SimulationSummary.DatacenterSummary dc : summary.getDatacenters()) {
            System.out.printf("    %s: %d hosts, %d VMs, %.6f kWh%n",
                dc.name, dc.hostCount, dc.vmCount, dc.energyKWh);
        }

        System.out.println("\n  Per-User Summary:");
        for (SimulationSummary.UserSummary user : summary.getUsers()) {
            System.out.printf("    %s: %d tasks, %.1f%% completed%n",
                user.name, user.taskCount, user.completionRate * 100);
        }

        boolean passed = summary.getTasks().completedTasks == 4 &&
                        summary.getDatacenters().size() == 2 &&
                        summary.getUsers().size() == 2;

        System.out.println("\n  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testJSONSerialization() {
        System.out.println("Test 8: JSON Serialization");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        EnergyCalculationStep energyStep = new EnergyCalculationStep();
        energyStep.execute(context);

        MetricsCollectionStep metricsStep = new MetricsCollectionStep();
        metricsStep.execute(context);

        String json = metricsStep.getSummaryJson();

        System.out.println("  JSON Output Preview:");
        // Show first 500 chars
        String preview = json.length() > 500 ? json.substring(0, 500) + "..." : json;
        for (String line : preview.split("\n")) {
            System.out.println("    " + line);
        }

        boolean passed = json.contains("simulationId") &&
                        json.contains("infrastructure") &&
                        json.contains("tasks") &&
                        json.contains("energy") &&
                        json.contains("performance") &&
                        json.contains("sla");

        System.out.println("\n  JSON contains required sections: " + (passed ? "Yes" : "No"));
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }
}
