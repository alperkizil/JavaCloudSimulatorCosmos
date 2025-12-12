package com.cloudsimulator;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Test for ReportingStep implementation (Stage 10).
 *
 * Tests CSV report generation including:
 * - Summary report
 * - Datacenter report
 * - Host report
 * - VM report
 * - Task report
 * - User report
 */
public class ReportingStepTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=== ReportingStep Test (Stage 10) ===\n");

        // Basic tests
        testBasicReporting();
        testSelectiveReports();
        testCustomPrefix();
        testEmptyContext();

        // Full pipeline test
        testFullPipelineWithReporting();

        // Summary
        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Total:  " + (testsPassed + testsFailed));

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Tests basic reporting with all report types enabled.
     */
    private static void testBasicReporting() {
        System.out.println("--- Test: Basic Reporting ---");

        SimulationContext context = createTestContext();

        ReportingStep step = new ReportingStep();
        step.setBaseOutputDirectory("./test-reports");
        step.execute(context);

        // Verify all files were generated
        Path outputDir = step.getOutputDirectory();
        boolean allSuccess = step.getResults().values().stream()
            .allMatch(r -> r.success);

        if (allSuccess && step.getTotalFilesGenerated() == 6) {
            System.out.println("  [PASS] All 6 reports generated successfully");
            System.out.println("  Output: " + outputDir);
            testsPassed++;
        } else {
            System.out.println("  [FAIL] Expected 6 reports, got " + step.getTotalFilesGenerated());
            testsFailed++;
        }

        // Verify files exist
        for (ReportingStep.ReportResult result : step.getResults().values()) {
            if (result.success && Files.exists(result.path)) {
                System.out.println("    - " + result.filename + " (" + result.rowCount + " rows)");
            } else {
                System.out.println("    - " + result.filename + " MISSING");
            }
        }

        // Cleanup
        cleanupDirectory(outputDir);
    }

    /**
     * Tests selective report generation (only some reports enabled).
     */
    private static void testSelectiveReports() {
        System.out.println("\n--- Test: Selective Reports ---");

        SimulationContext context = createTestContext();

        ReportingStep step = new ReportingStep();
        step.setBaseOutputDirectory("./test-reports");
        step.disableAllReports();
        step.enableReport(ReportingStep.ReportType.SUMMARY);
        step.enableReport(ReportingStep.ReportType.TASKS);
        step.execute(context);

        if (step.getTotalFilesGenerated() == 2) {
            System.out.println("  [PASS] Generated exactly 2 reports (summary, tasks)");
            testsPassed++;
        } else {
            System.out.println("  [FAIL] Expected 2 reports, got " + step.getTotalFilesGenerated());
            testsFailed++;
        }

        // Cleanup
        cleanupDirectory(step.getOutputDirectory());
    }

    /**
     * Tests custom prefix for experiment folder.
     */
    private static void testCustomPrefix() {
        System.out.println("\n--- Test: Custom Prefix ---");

        SimulationContext context = createTestContext();

        ReportingStep step = new ReportingStep();
        step.setBaseOutputDirectory("./test-reports");
        step.setCustomPrefix("my_experiment");
        step.execute(context);

        String folderName = step.getOutputDirectory().getFileName().toString();
        if (folderName.startsWith("my_experiment_")) {
            System.out.println("  [PASS] Custom prefix applied: " + folderName);
            testsPassed++;
        } else {
            System.out.println("  [FAIL] Expected folder starting with 'my_experiment_', got: " + folderName);
            testsFailed++;
        }

        // Cleanup
        cleanupDirectory(step.getOutputDirectory());
    }

    /**
     * Tests reporting with empty context (no data).
     */
    private static void testEmptyContext() {
        System.out.println("\n--- Test: Empty Context ---");

        SimulationContext context = new SimulationContext();

        ReportingStep step = new ReportingStep();
        step.setBaseOutputDirectory("./test-reports");
        step.execute(context);

        // Should still generate files (with headers only)
        boolean allSuccess = step.getResults().values().stream()
            .allMatch(r -> r.success);

        if (allSuccess) {
            System.out.println("  [PASS] Reports generated for empty context");
            testsPassed++;
        } else {
            System.out.println("  [FAIL] Failed to generate reports for empty context");
            testsFailed++;
        }

        // Cleanup
        cleanupDirectory(step.getOutputDirectory());
    }

    /**
     * Tests full simulation pipeline with all 10 steps including reporting.
     */
    private static void testFullPipelineWithReporting() {
        System.out.println("\n--- Test: Full Pipeline With Reporting ---");

        try {
            // Create context with full setup
            SimulationContext context = createFullPipelineContext();

            // Execute VMExecutionStep
            VMExecutionStep vmExecStep = new VMExecutionStep();
            vmExecStep.execute(context);

            // Execute TaskExecutionStep
            TaskExecutionStep taskExecStep = new TaskExecutionStep();
            taskExecStep.execute(context);

            // Execute EnergyCalculationStep
            EnergyCalculationStep energyStep = new EnergyCalculationStep();
            energyStep.setPUE(1.5);
            energyStep.setCarbonIntensity(EnergyCalculationStep.CarbonIntensityRegion.US_AVERAGE);
            energyStep.execute(context);

            // Execute MetricsCollectionStep
            MetricsCollectionStep metricsStep = new MetricsCollectionStep();
            metricsStep.execute(context);

            // Execute ReportingStep
            ReportingStep reportingStep = new ReportingStep();
            reportingStep.setBaseOutputDirectory("./test-reports");
            reportingStep.setCustomPrefix("full_pipeline_test");
            reportingStep.execute(context);

            // Verify results
            boolean allSuccess = reportingStep.getResults().values().stream()
                .allMatch(r -> r.success);

            int totalRows = reportingStep.getResults().values().stream()
                .mapToInt(r -> r.rowCount)
                .sum();

            if (allSuccess && totalRows > 0) {
                System.out.println("  [PASS] Full pipeline completed successfully");
                System.out.println("    Total files: " + reportingStep.getTotalFilesGenerated());
                System.out.println("    Total rows: " + totalRows);
                System.out.println("    Total size: " + formatBytes(reportingStep.getTotalBytesWritten()));
                testsPassed++;
            } else {
                System.out.println("  [FAIL] Full pipeline reporting failed");
                testsFailed++;
            }

            // Print file details
            System.out.println("    Generated files:");
            for (ReportingStep.ReportResult result : reportingStep.getResults().values()) {
                System.out.println("      - " + result.filename + ": " + result.rowCount + " rows");
            }

            // Read and display task report sample
            Path tasksFile = reportingStep.getResult(ReportingStep.ReportType.TASKS).path;
            if (Files.exists(tasksFile)) {
                List<String> lines = Files.readAllLines(tasksFile);
                System.out.println("\n    Tasks report sample (first 3 lines):");
                for (int i = 0; i < Math.min(3, lines.size()); i++) {
                    System.out.println("      " + truncate(lines.get(i), 100));
                }
            }

            // Cleanup
            cleanupDirectory(reportingStep.getOutputDirectory());

        } catch (Exception e) {
            System.out.println("  [FAIL] Exception: " + e.getMessage());
            e.printStackTrace();
            testsFailed++;
        }
    }

    /**
     * Creates a simple test context.
     */
    private static SimulationContext createTestContext() {
        SimulationContext context = new SimulationContext();

        // Create datacenter
        CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);
        context.addDatacenter(dc);

        // Create host (using 4-arg constructor: IPS, cores, computeType, gpus)
        Host host = new Host(2_000_000_000L, 16, ComputeType.CPU_ONLY, 0);
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
        Task task1 = new Task("Task1", user.getName(), 8_000_000_000L, WorkloadType.SEVEN_ZIP, 0);
        Task task2 = new Task("Task2", user.getName(), 16_000_000_000L, WorkloadType.DATABASE, 0);

        user.addTask(task1);
        user.addTask(task2);
        context.addTask(task1);
        context.addTask(task2);

        // Assign tasks to VM
        task1.assignToVM(vm.getId(), 0);
        vm.assignTask(task1);
        task2.assignToVM(vm.getId(), 0);
        vm.assignTask(task2);

        return context;
    }

    /**
     * Creates a context for full pipeline testing.
     */
    private static SimulationContext createFullPipelineContext() {
        SimulationContext context = new SimulationContext();

        // Create 2 datacenters
        CloudDatacenter dc1 = new CloudDatacenter("DC-East", 10, 100000.0);
        CloudDatacenter dc2 = new CloudDatacenter("DC-West", 10, 100000.0);
        context.addDatacenter(dc1);
        context.addDatacenter(dc2);

        // Create hosts (using 4-arg constructor: IPS, cores, computeType, gpus)
        Host host1 = new Host(2_000_000_000L, 16, ComputeType.CPU_ONLY, 0);
        Host host2 = new Host(2_000_000_000L, 8, ComputeType.CPU_GPU_MIXED, 2);
        dc1.addHost(host1);
        dc2.addHost(host2);
        context.addHost(host1);
        context.addHost(host2);

        // Create 2 users
        User alice = new User("Alice");
        alice.addSelectedDatacenter(dc1.getId());
        alice.startSession(0);
        context.addUser(alice);

        User bob = new User("Bob");
        bob.addSelectedDatacenter(dc2.getId());
        bob.startSession(0);
        context.addUser(bob);

        // Create VMs for Alice
        VM vm1 = new VM(alice.getName(), 2_000_000_000L, 4, 0, 8192, 51200, 1000, ComputeType.CPU_ONLY);
        host1.assignVM(vm1);
        vm1.start();
        alice.addVirtualMachine(vm1);
        context.addVM(vm1);

        // Create VMs for Bob
        VM vm2 = new VM(bob.getName(), 2_000_000_000L, 4, 2, 8192, 51200, 1000, ComputeType.CPU_GPU_MIXED);
        host2.assignVM(vm2);
        vm2.start();
        bob.addVirtualMachine(vm2);
        context.addVM(vm2);

        // Create tasks for Alice
        Task task1 = new Task("Alice-Task1", alice.getName(), 8_000_000_000L, WorkloadType.SEVEN_ZIP, 0);
        Task task2 = new Task("Alice-Task2", alice.getName(), 16_000_000_000L, WorkloadType.DATABASE, 0);
        alice.addTask(task1);
        alice.addTask(task2);
        context.addTask(task1);
        context.addTask(task2);

        task1.assignToVM(vm1.getId(), 0);
        vm1.assignTask(task1);
        task2.assignToVM(vm1.getId(), 0);
        vm1.assignTask(task2);

        // Create tasks for Bob
        Task task3 = new Task("Bob-Task1", bob.getName(), 12_000_000_000L, WorkloadType.LLM_GPU, 0);
        Task task4 = new Task("Bob-Task2", bob.getName(), 20_000_000_000L, WorkloadType.IMAGE_GEN_GPU, 0);
        bob.addTask(task3);
        bob.addTask(task4);
        context.addTask(task3);
        context.addTask(task4);

        task3.assignToVM(vm2.getId(), 0);
        vm2.assignTask(task3);
        task4.assignToVM(vm2.getId(), 0);
        vm2.assignTask(task4);

        return context;
    }

    /**
     * Cleans up a test directory and its contents.
     */
    private static void cleanupDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }

        try {
            // Delete all files in directory
            Files.list(dir).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    // Ignore
                }
            });
            // Delete directory
            Files.deleteIfExists(dir);

            // Try to delete parent 'test-reports' if empty
            Path parent = dir.getParent();
            if (parent != null && parent.getFileName().toString().equals("test-reports")) {
                try {
                    if (Files.list(parent).count() == 0) {
                        Files.deleteIfExists(parent);
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Formats bytes into human-readable format.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Truncates a string to max length with ellipsis.
     */
    private static String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
    }
}
