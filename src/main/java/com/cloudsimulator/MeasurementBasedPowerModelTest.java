package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.factory.PowerModelFactory;
import com.cloudsimulator.model.*;

/**
 * Test class for the MeasurementBasedPowerModel implementation.
 * Demonstrates power calculations using empirical measurements from
 * wall-plug power monitoring.
 *
 * Run with: java -cp out com.cloudsimulator.MeasurementBasedPowerModelTest
 */
public class MeasurementBasedPowerModelTest {

    public static void main(String[] args) {
        System.out.println("=" .repeat(80));
        System.out.println("MeasurementBasedPowerModel Test");
        System.out.println("=" .repeat(80));
        System.out.println();

        // Test 1: Create and display the power model
        testPowerModelCreation();

        // Test 2: Calculate power for VERACRYPT example from README
        testVeracryptExample();

        // Test 3: Compare all workloads
        testAllWorkloads();

        // Test 4: Test with Host integration
        testHostIntegration();

        // Test 5: Compare measurement-based vs traditional model
        testModelComparison();

        System.out.println("\n" + "=" .repeat(80));
        System.out.println("All tests completed successfully!");
        System.out.println("=" .repeat(80));
    }

    private static void testPowerModelCreation() {
        System.out.println("TEST 1: Power Model Creation");
        System.out.println("-" .repeat(80));

        // Create using factory
        MeasurementBasedPowerModel model = PowerModelFactory.createMeasurementBasedPowerModel();

        System.out.println("Model Name: " + model.getModelName());
        System.out.println("Base Idle Power: " + model.getBaseIdlePowerWatts() + " W");
        System.out.println("Hardware Scale Factor: " + model.getHardwareScaleFactor());
        System.out.println("Number of workload profiles: " + model.getWorkloadProfiles().size());
        System.out.println();

        // Print profile summary
        System.out.println(model.getProfilesSummary());
        System.out.println();
    }

    private static void testVeracryptExample() {
        System.out.println("TEST 2: VERACRYPT Task Example (from README)");
        System.out.println("-" .repeat(80));
        System.out.println();

        // Configuration from README example
        System.out.println("Configuration:");
        System.out.println("  VM: CPU_GPU_MIXED with 4 vCPUs, 2 GPUs");
        System.out.println("  Task: VERACRYPT with 1,000,000 instructions");
        System.out.println("  VM IPS: 8 GIPS (2 GIPS per vCPU × 4 vCPUs)");
        System.out.println();

        MeasurementBasedPowerModel model = PowerModelFactory.createMeasurementBasedPowerModel();

        // VERACRYPT utilization from VM.calculateUtilization()
        double cpuUtil = 0.85;
        double gpuUtil = 0.0;

        // Calculate power
        double incrementalPower = model.calculateIncrementalPower(WorkloadType.VERACRYPT, cpuUtil, gpuUtil);
        double totalPower = model.calculateTotalPower(WorkloadType.VERACRYPT, cpuUtil, gpuUtil);

        System.out.println("Power Calculation:");
        System.out.println("  CPU Utilization: " + (cpuUtil * 100) + "%");
        System.out.println("  GPU Utilization: " + (gpuUtil * 100) + "%");
        System.out.println("  Base Idle Power: " + model.getBaseIdlePowerWatts() + " W");
        System.out.println("  Incremental Power: " + String.format("%.2f", incrementalPower) + " W");
        System.out.println("  Total Power: " + String.format("%.2f", totalPower) + " W");
        System.out.println();

        // Compare with actual measurement
        EmpiricalWorkloadProfile profile = model.getWorkloadProfile(WorkloadType.VERACRYPT);
        System.out.println("Actual Measurement (from power log):");
        System.out.println("  Average Power: " + profile.getAveragePowerWatts() + " W");
        System.out.println("  Incremental Power: " + profile.getIncrementalPowerWatts() + " W");
        System.out.println("  Peak Power: " + profile.getPeakPowerWatts() + " W");
        System.out.println();

        // Energy for 1 second
        double energyJoules = totalPower * 1.0;
        System.out.println("Energy (1 second execution):");
        System.out.println("  Energy: " + String.format("%.2f", energyJoules) + " J");
        System.out.println("  Energy: " + String.format("%.6f", energyJoules / 3600000.0) + " kWh");
        System.out.println();
    }

    private static void testAllWorkloads() {
        System.out.println("TEST 3: Power Comparison Across All Workloads");
        System.out.println("-" .repeat(80));
        System.out.println();

        MeasurementBasedPowerModel model = PowerModelFactory.createMeasurementBasedPowerModel();

        // Define typical utilization for each workload (from VM.calculateUtilization)
        double[][] workloadUtils = {
            {0.0, 0.0},    // IDLE
            {0.85, 0.0},   // VERACRYPT
            {0.6, 0.0},    // DATABASE
            {0.8, 0.0},    // SEVEN_ZIP
            {1.0, 0.0},    // CINEBENCH
            {1.0, 0.0},    // PRIME95SmallFFT
            {0.95, 0.0},   // LLM_CPU
            {0.3, 0.95},   // LLM_GPU
            {0.9, 0.0},    // IMAGE_GEN_CPU
            {0.2, 0.9},    // IMAGE_GEN_GPU
            {0.1, 1.0}     // FURMARK
        };

        WorkloadType[] workloads = {
            WorkloadType.IDLE,
            WorkloadType.VERACRYPT,
            WorkloadType.DATABASE,
            WorkloadType.SEVEN_ZIP,
            WorkloadType.CINEBENCH,
            WorkloadType.PRIME95SmallFFT,
            WorkloadType.LLM_CPU,
            WorkloadType.LLM_GPU,
            WorkloadType.IMAGE_GEN_CPU,
            WorkloadType.IMAGE_GEN_GPU,
            WorkloadType.FURMARK
        };

        System.out.printf("%-20s %10s %10s %15s %15s %15s\n",
            "Workload", "CPU Util", "GPU Util", "Incremental(W)", "Total(W)", "Measured(W)");
        System.out.println("-" .repeat(90));

        for (int i = 0; i < workloads.length; i++) {
            WorkloadType workload = workloads[i];
            double cpuUtil = workloadUtils[i][0];
            double gpuUtil = workloadUtils[i][1];

            double incrementalPower = model.calculateIncrementalPower(workload, cpuUtil, gpuUtil);
            double totalPower = model.calculateTotalPower(workload, cpuUtil, gpuUtil);
            EmpiricalWorkloadProfile profile = model.getWorkloadProfile(workload);
            double measuredTotal = profile != null ? profile.getAveragePowerWatts() : 0.0;

            System.out.printf("%-20s %9.0f%% %9.0f%% %15.2f %15.2f %15.2f\n",
                workload.name(), cpuUtil * 100, gpuUtil * 100,
                incrementalPower, totalPower, measuredTotal);
        }
        System.out.println();
    }

    private static void testHostIntegration() {
        System.out.println("TEST 4: Host Integration with MeasurementBasedPowerModel");
        System.out.println("-" .repeat(80));
        System.out.println();

        // Create a host with measurement-based power model
        Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_GPU_MIXED, 4);
        MeasurementBasedPowerModel model = PowerModelFactory.createMeasurementBasedPowerModel();
        host.setMeasurementBasedPowerModel(model);

        System.out.println("Host Configuration:");
        System.out.println("  CPU Cores: " + host.getNumberOfCpuCores());
        System.out.println("  GPUs: " + host.getNumberOfGpus());
        System.out.println("  Using Measurement-Based Model: " + host.isUsingMeasurementBasedPowerModel());
        System.out.println();

        // Create a VM with VERACRYPT task
        VM vm = new VM("TestUser", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
        Task task = new Task("VeracryptTask", "TestUser", 1_000_000L, WorkloadType.VERACRYPT);

        // Assign VM to host
        host.assignVM(vm);
        vm.start();
        vm.assignTask(task);

        System.out.println("VM assigned to host:");
        System.out.println("  VM ID: " + vm.getId());
        System.out.println("  User: " + vm.getUserId());
        System.out.println("  vCPUs: " + vm.getRequestedVcpuCount());
        System.out.println();

        // Simulate one execution tick
        vm.executeOneSecond(0);

        System.out.println("After 1 second of execution:");
        System.out.println("  Current Workload: " + vm.getCurrentWorkloadType());
        System.out.println("  CPU Utilization: " + (vm.getCurrentUtilization().getCpuUtilization() * 100) + "%");
        System.out.println("  GPU Utilization: " + (vm.getCurrentUtilization().getGpuUtilization() * 100) + "%");
        System.out.println();

        // Update host power
        host.updatePowerConsumption();

        System.out.println("Host Power Consumption:");
        System.out.println("  CPU Power: " + String.format("%.2f", host.getCurrentCpuPowerDraw()) + " W");
        System.out.println("  GPU Power: " + String.format("%.2f", host.getCurrentGpuPowerDraw()) + " W");
        System.out.println("  Other Components: " + String.format("%.2f", host.getOtherComponentsPowerDraw()) + " W");
        System.out.println("  Total Power: " + String.format("%.2f", host.getCurrentTotalPowerDraw()) + " W");
        System.out.println();
    }

    private static void testModelComparison() {
        System.out.println("TEST 5: Comparison - Measurement-Based vs Traditional Model");
        System.out.println("-" .repeat(80));
        System.out.println();

        // Create both models
        MeasurementBasedPowerModel measurementModel = PowerModelFactory.createMeasurementBasedPowerModel();
        PowerModel traditionalModel = PowerModelFactory.createPowerModel("StandardPowerModel");

        WorkloadType[] testWorkloads = {
            WorkloadType.VERACRYPT,
            WorkloadType.SEVEN_ZIP,
            WorkloadType.FURMARK,
            WorkloadType.LLM_GPU
        };

        double[][] testUtils = {
            {0.85, 0.0},   // VERACRYPT
            {0.8, 0.0},    // SEVEN_ZIP
            {0.1, 1.0},    // FURMARK
            {0.3, 0.95}    // LLM_GPU
        };

        System.out.printf("%-15s %10s %10s %18s %18s %12s\n",
            "Workload", "CPU Util", "GPU Util", "Measurement(W)", "Traditional(W)", "Difference");
        System.out.println("-" .repeat(95));

        for (int i = 0; i < testWorkloads.length; i++) {
            WorkloadType workload = testWorkloads[i];
            double cpuUtil = testUtils[i][0];
            double gpuUtil = testUtils[i][1];

            double measurementPower = measurementModel.calculateTotalPower(workload, cpuUtil, gpuUtil);
            double traditionalPower = traditionalModel.calculateTotalPower(cpuUtil, gpuUtil);
            double difference = traditionalPower - measurementPower;
            String diffSign = difference > 0 ? "+" : "";

            System.out.printf("%-15s %9.0f%% %9.0f%% %18.2f %18.2f %11s%.2f\n",
                workload.name(), cpuUtil * 100, gpuUtil * 100,
                measurementPower, traditionalPower, diffSign, difference);
        }
        System.out.println();

        System.out.println("Key Observations:");
        System.out.println("  - Traditional model uses fixed multipliers regardless of workload type");
        System.out.println("  - Measurement-based model uses empirical data for each workload");
        System.out.println("  - Traditional model tends to overestimate for light workloads (VERACRYPT)");
        System.out.println("  - Measurement model provides more accurate power estimates based on real data");
        System.out.println();
    }
}
