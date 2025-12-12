package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.TaskExecutionStatus;
import com.cloudsimulator.enums.VmState;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

/**
 * Unit tests for the VM class.
 * Tests all major functionality including:
 * - Constructor variants
 * - Task assignment and execution
 * - State management
 * - Utilization tracking
 * - Compute type compatibility
 * - Resource calculations
 * - Completion tracking
 */
public class VMTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=== VM Unit Tests ===\n");

        // Constructor tests
        testDefaultConstructor();
        testCustomConstructor();

        // Task assignment tests
        testAssignTask();
        testFinishTask();
        testGetNextTask();
        testMultipleTaskAssignments();

        // Compute type compatibility tests
        testCanAcceptTaskCPUOnly();
        testCanAcceptTaskGPUOnly();
        testCanAcceptTaskMixed();

        // Execution tests
        testExecuteOneSecondNoTasks();
        testExecuteOneSecondWithTask();
        testExecuteOneSecondTaskCompletion();
        testExecuteMultipleTasks();

        // State management tests
        testActivate();
        testStart();
        testUpdateState();
        testHostAssignment();

        // Utilization tests
        testRecordUtilization();
        testTaskRunningSecondsTracking();
        testTaskWorkloadSecondsTracking();
        testCalculateTaskTotalPowerDraw();

        // Completion tracking tests
        testGetCompletionPercentage();
        testHasFinishedAllTasks();
        testHasEverHadTasks();

        // Workload type tests
        testGetCurrentWorkloadType();
        testGetCurrentExecutingTask();

        // Resource calculation tests
        testGetTotalRequestedIps();

        // Getters and setters tests
        testGettersAndSetters();
        testToString();

        // Summary
        System.out.println("\n=== Test Summary ===");
        System.out.println("Tests Passed: " + testsPassed);
        System.out.println("Tests Failed: " + testsFailed);
        System.out.println("Total Tests: " + (testsPassed + testsFailed));

        if (testsFailed == 0) {
            System.out.println("\nAll VM tests PASSED!");
        } else {
            System.out.println("\nSome tests FAILED. Please review the output above.");
            System.exit(1);
        }
    }

    // ==================== Constructor Tests ====================

    private static void testDefaultConstructor() {
        System.out.println("Test: Default Constructor");
        try {
            VM vm = new VM("user1");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!"user1".equals(vm.getUserId())) {
                passed = false;
                errors.append("    - User ID should be 'user1'\n");
            }
            if (vm.getRequestedIpsPerVcpu() != 2_000_000_000L) {
                passed = false;
                errors.append("    - Default IPS per vCPU should be 2,000,000,000\n");
            }
            if (vm.getRequestedVcpuCount() != 4) {
                passed = false;
                errors.append("    - Default vCPU count should be 4\n");
            }
            if (vm.getRequestedGpuCount() != 0) {
                passed = false;
                errors.append("    - Default GPU count should be 0\n");
            }
            if (vm.getRequestedRamMB() != 8192) {
                passed = false;
                errors.append("    - Default RAM should be 8192 MB\n");
            }
            if (vm.getComputeType() != ComputeType.CPU_ONLY) {
                passed = false;
                errors.append("    - Default compute type should be CPU_ONLY\n");
            }
            if (vm.getVmState() != VmState.CREATED) {
                passed = false;
                errors.append("    - Initial state should be CREATED\n");
            }
            if (vm.getActiveSeconds() != 0) {
                passed = false;
                errors.append("    - Initial active seconds should be 0\n");
            }
            if (!vm.getAssignedTasks().isEmpty()) {
                passed = false;
                errors.append("    - Initial assigned tasks should be empty\n");
            }
            if (!vm.getFinishedTasks().isEmpty()) {
                passed = false;
                errors.append("    - Initial finished tasks should be empty\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testCustomConstructor() {
        System.out.println("Test: Custom Constructor");
        try {
            VM vm = new VM("user2", 3_000_000_000L, 8, 2, 16384, 204800, 2000, ComputeType.CPU_GPU_MIXED);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!"user2".equals(vm.getUserId())) {
                passed = false;
                errors.append("    - User ID mismatch\n");
            }
            if (vm.getRequestedIpsPerVcpu() != 3_000_000_000L) {
                passed = false;
                errors.append("    - IPS per vCPU mismatch\n");
            }
            if (vm.getRequestedVcpuCount() != 8) {
                passed = false;
                errors.append("    - vCPU count mismatch\n");
            }
            if (vm.getRequestedGpuCount() != 2) {
                passed = false;
                errors.append("    - GPU count mismatch\n");
            }
            if (vm.getRequestedRamMB() != 16384) {
                passed = false;
                errors.append("    - RAM mismatch\n");
            }
            if (vm.getComputeType() != ComputeType.CPU_GPU_MIXED) {
                passed = false;
                errors.append("    - Compute type mismatch\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Task Assignment Tests ====================

    private static void testAssignTask() {
        System.out.println("Test: assignTask()");
        try {
            VM vm = new VM("user1");
            Task task = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task);

            if (vm.getAssignedTasks().size() != 1) {
                passed = false;
                errors.append("    - Assigned tasks count should be 1\n");
            }
            if (vm.getNextTask() != task) {
                passed = false;
                errors.append("    - Next task should be the assigned task\n");
            }
            if (vm.getTaskRunningSeconds(task.getId()) != 0L) {
                passed = false;
                errors.append("    - Task running seconds should be initialized to 0\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testFinishTask() {
        System.out.println("Test: finishTask()");
        try {
            VM vm = new VM("user1");
            Task task = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task);
            vm.finishTask(task);

            if (vm.getAssignedTasks().size() != 0) {
                passed = false;
                errors.append("    - Assigned tasks should be empty after finishing\n");
            }
            if (vm.getFinishedTasks().size() != 1) {
                passed = false;
                errors.append("    - Finished tasks count should be 1\n");
            }
            if (!vm.getFinishedTasks().contains(task)) {
                passed = false;
                errors.append("    - Task should be in finished list\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testGetNextTask() {
        System.out.println("Test: getNextTask()");
        try {
            VM vm = new VM("user1");
            Task task1 = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "user1", 2_000_000_000L, WorkloadType.DATABASE);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task1);
            vm.assignTask(task2);

            if (vm.getNextTask() != task1) {
                passed = false;
                errors.append("    - Next task should be task1 (FIFO order)\n");
            }

            vm.finishTask(task1);

            if (vm.getNextTask() != task2) {
                passed = false;
                errors.append("    - Next task should be task2 after task1 is finished\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testMultipleTaskAssignments() {
        System.out.println("Test: Multiple Task Assignments");
        try {
            VM vm = new VM("user1");
            Task task1 = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "user1", 2_000_000_000L, WorkloadType.DATABASE);
            Task task3 = new Task("Task3", "user1", 3_000_000_000L, WorkloadType.CINEBENCH);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task1);
            vm.assignTask(task2);
            vm.assignTask(task3);

            if (vm.getAssignedTasks().size() != 3) {
                passed = false;
                errors.append("    - Should have 3 assigned tasks\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Compute Type Compatibility Tests ====================

    private static void testCanAcceptTaskCPUOnly() {
        System.out.println("Test: canAcceptTask() - CPU_ONLY VM");
        try {
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // CPU tasks should be accepted
            Task cpuTask = new Task("CPUTask", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            if (!vm.canAcceptTask(cpuTask)) {
                passed = false;
                errors.append("    - CPU_ONLY VM should accept CPU workload\n");
            }

            // GPU tasks should NOT be accepted
            Task gpuTask = new Task("GPUTask", "user1", 1_000_000_000L, WorkloadType.FURMARK);
            if (vm.canAcceptTask(gpuTask)) {
                passed = false;
                errors.append("    - CPU_ONLY VM should NOT accept GPU workload\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testCanAcceptTaskGPUOnly() {
        System.out.println("Test: canAcceptTask() - GPU_ONLY VM");
        try {
            VM vm = new VM("user1", 2_000_000_000L, 0, 4, 8192, 102400, 1000, ComputeType.GPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // GPU tasks should be accepted
            Task gpuTask = new Task("GPUTask", "user1", 1_000_000_000L, WorkloadType.FURMARK);
            if (!vm.canAcceptTask(gpuTask)) {
                passed = false;
                errors.append("    - GPU_ONLY VM should accept GPU workload\n");
            }

            // CPU tasks should NOT be accepted
            Task cpuTask = new Task("CPUTask", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            if (vm.canAcceptTask(cpuTask)) {
                passed = false;
                errors.append("    - GPU_ONLY VM should NOT accept CPU workload\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testCanAcceptTaskMixed() {
        System.out.println("Test: canAcceptTask() - MIXED VM");
        try {
            VM vm = new VM("user1", 2_000_000_000L, 4, 2, 8192, 102400, 1000, ComputeType.CPU_GPU_MIXED);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // Both CPU and GPU tasks should be accepted
            Task cpuTask = new Task("CPUTask", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task gpuTask = new Task("GPUTask", "user1", 1_000_000_000L, WorkloadType.FURMARK);
            Task mixedTask = new Task("MixedTask", "user1", 1_000_000_000L, WorkloadType.IMAGE_GEN_GPU);

            if (!vm.canAcceptTask(cpuTask)) {
                passed = false;
                errors.append("    - MIXED VM should accept CPU workload\n");
            }
            if (!vm.canAcceptTask(gpuTask)) {
                passed = false;
                errors.append("    - MIXED VM should accept GPU workload\n");
            }
            if (!vm.canAcceptTask(mixedTask)) {
                passed = false;
                errors.append("    - MIXED VM should accept mixed workload\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Execution Tests ====================

    private static void testExecuteOneSecondNoTasks() {
        System.out.println("Test: executeOneSecond() - No Tasks");
        try {
            VM vm = new VM("user1");
            vm.setVmState(VmState.RUNNING);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.executeOneSecond(1000L);

            // Should complete without errors
            if (vm.getCurrentExecutingTask() != null) {
                passed = false;
                errors.append("    - Should have no executing task\n");
            }
            if (vm.getCurrentWorkloadType() != WorkloadType.IDLE) {
                passed = false;
                errors.append("    - Workload type should be IDLE\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testExecuteOneSecondWithTask() {
        System.out.println("Test: executeOneSecond() - With Task");
        try {
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            vm.setVmState(VmState.RUNNING);
            Task task = new Task("Task1", "user1", 10_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task);
            vm.executeOneSecond(1000L);

            if (vm.getCurrentExecutingTask() != task) {
                passed = false;
                errors.append("    - Current executing task should be task\n");
            }
            if (task.getInstructionsExecuted() != vm.getTotalRequestedIps()) {
                passed = false;
                errors.append("    - Instructions executed should equal VM IPS\n");
            }
            if (!task.isExecuting()) {
                passed = false;
                errors.append("    - Task should be in EXECUTING state\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testExecuteOneSecondTaskCompletion() {
        System.out.println("Test: executeOneSecond() - Task Completion");
        try {
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            vm.setVmState(VmState.RUNNING);
            // Task that will complete in one second (8B IPS * 1s = 8B instructions)
            Task task = new Task("Task1", "user1", 8_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task);
            vm.executeOneSecond(1000L);

            if (!task.isCompleted()) {
                passed = false;
                errors.append("    - Task should be completed\n");
            }
            if (vm.getFinishedTasks().size() != 1) {
                passed = false;
                errors.append("    - Finished tasks should have 1 task\n");
            }
            if (vm.getAssignedTasks().size() != 0) {
                passed = false;
                errors.append("    - Assigned tasks should be empty\n");
            }
            if (vm.getCurrentExecutingTask() != null) {
                passed = false;
                errors.append("    - Current executing task should be null after completion\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testExecuteMultipleTasks() {
        System.out.println("Test: Execute Multiple Tasks");
        try {
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            vm.setVmState(VmState.RUNNING);
            // Each task completes in 1 second
            Task task1 = new Task("Task1", "user1", 8_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "user1", 8_000_000_000L, WorkloadType.DATABASE);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task1);
            vm.assignTask(task2);

            vm.executeOneSecond(1000L); // Complete task1
            vm.executeOneSecond(1001L); // Complete task2

            if (vm.getFinishedTasks().size() != 2) {
                passed = false;
                errors.append("    - Should have 2 finished tasks, got " + vm.getFinishedTasks().size() + "\n");
            }
            if (vm.getAssignedTasks().size() != 0) {
                passed = false;
                errors.append("    - Should have 0 assigned tasks\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== State Management Tests ====================

    private static void testActivate() {
        System.out.println("Test: activate()");
        try {
            VM vm = new VM("user1");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (vm.getAssignedHostId() != null) {
                passed = false;
                errors.append("    - Initial host ID should be null\n");
            }

            vm.activate(1000L, 5L);

            if (vm.getAssignedHostId() == null || vm.getAssignedHostId() != 5L) {
                passed = false;
                errors.append("    - Host ID should be 5\n");
            }
            if (vm.getVmState() != VmState.QUEUED) {
                passed = false;
                errors.append("    - State should be QUEUED after activation\n");
            }
            if (vm.getActiveSeconds() != 0) {
                passed = false;
                errors.append("    - Active seconds should be reset to 0\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testStart() {
        System.out.println("Test: start()");
        try {
            VM vm = new VM("user1");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.start();

            if (vm.getVmState() != VmState.RUNNING) {
                passed = false;
                errors.append("    - State should be RUNNING after start\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testUpdateState() {
        System.out.println("Test: updateState()");
        try {
            VM vm = new VM("user1");
            vm.setVmState(VmState.RUNNING);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.updateState();

            if (vm.getActiveSeconds() != 1) {
                passed = false;
                errors.append("    - Active seconds should be 1\n");
            }
            if (vm.getTotalOpenSeconds() != 1) {
                passed = false;
                errors.append("    - Total open seconds should be 1\n");
            }
            // With no tasks, should increment idle time
            if (vm.getSecondsIDLE() != 1) {
                passed = false;
                errors.append("    - Idle seconds should be 1 (no tasks)\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testHostAssignment() {
        System.out.println("Test: Host Assignment");
        try {
            VM vm = new VM("user1");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (vm.isAssignedToHost()) {
                passed = false;
                errors.append("    - VM should not be assigned to host initially\n");
            }

            vm.setAssignedHostId(10L);

            if (!vm.isAssignedToHost()) {
                passed = false;
                errors.append("    - VM should be assigned to host after setAssignedHostId\n");
            }
            if (vm.getAssignedHostId() != 10L) {
                passed = false;
                errors.append("    - Host ID should be 10\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Utilization Tests ====================

    private static void testRecordUtilization() {
        System.out.println("Test: recordUtilization()");
        try {
            VM vm = new VM("user1");
            Task task = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task);
            vm.recordUtilization(1000L, task.getId(), WorkloadType.SEVEN_ZIP, 0.8, 0.0, 100.0);
            vm.recordUtilization(1001L, task.getId(), WorkloadType.SEVEN_ZIP, 0.9, 0.0, 110.0);

            if (vm.getUtilizationHistory().size() != 2) {
                passed = false;
                errors.append("    - Utilization history should have 2 records\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testTaskRunningSecondsTracking() {
        System.out.println("Test: Task Running Seconds Tracking");
        try {
            VM vm = new VM("user1");
            Task task = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task);

            // Record utilization 3 times
            vm.recordUtilization(1000L, task.getId(), WorkloadType.SEVEN_ZIP, 0.8, 0.0, 100.0);
            vm.recordUtilization(1001L, task.getId(), WorkloadType.SEVEN_ZIP, 0.9, 0.0, 110.0);
            vm.recordUtilization(1002L, task.getId(), WorkloadType.SEVEN_ZIP, 0.7, 0.0, 90.0);

            long runningSeconds = vm.getTaskRunningSeconds(task.getId());
            if (runningSeconds != 3) {
                passed = false;
                errors.append("    - Task running seconds should be 3, got " + runningSeconds + "\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testTaskWorkloadSecondsTracking() {
        System.out.println("Test: Task Workload Seconds Tracking");
        try {
            VM vm = new VM("user1");
            Task task = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task);

            vm.recordUtilization(1000L, task.getId(), WorkloadType.SEVEN_ZIP, 0.8, 0.0, 100.0);
            vm.recordUtilization(1001L, task.getId(), WorkloadType.SEVEN_ZIP, 0.9, 0.0, 110.0);
            vm.recordUtilization(1002L, task.getId(), WorkloadType.DATABASE, 0.5, 0.0, 80.0);

            long sevenZipSeconds = vm.getTaskWorkloadSeconds(task.getId(), WorkloadType.SEVEN_ZIP);
            long dbSeconds = vm.getTaskWorkloadSeconds(task.getId(), WorkloadType.DATABASE);

            if (sevenZipSeconds != 2) {
                passed = false;
                errors.append("    - SEVEN_ZIP workload seconds should be 2, got " + sevenZipSeconds + "\n");
            }
            if (dbSeconds != 1) {
                passed = false;
                errors.append("    - DATABASE workload seconds should be 1, got " + dbSeconds + "\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testCalculateTaskTotalPowerDraw() {
        System.out.println("Test: calculateTaskTotalPowerDraw()");
        try {
            VM vm = new VM("user1");
            Task task = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task);

            vm.recordUtilization(1000L, task.getId(), WorkloadType.SEVEN_ZIP, 1.0, 0.0, 100.0);
            vm.recordUtilization(1001L, task.getId(), WorkloadType.SEVEN_ZIP, 1.0, 0.0, 100.0);

            double totalPower = vm.calculateTaskTotalPowerDraw(task.getId());

            if (Math.abs(totalPower - 200.0) > 0.001) {
                passed = false;
                errors.append("    - Total power draw should be 200.0, got " + totalPower + "\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Completion Tracking Tests ====================

    private static void testGetCompletionPercentage() {
        System.out.println("Test: getCompletionPercentage()");
        try {
            VM vm = new VM("user1");
            Task task1 = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "user1", 2_000_000_000L, WorkloadType.DATABASE);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            vm.assignTask(task1);
            vm.assignTask(task2);

            // 0% completed
            if (Math.abs(vm.getCompletionPercentage() - 0.0) > 0.001) {
                passed = false;
                errors.append("    - Initial completion should be 0%\n");
            }

            vm.finishTask(task1);

            // 50% completed
            if (Math.abs(vm.getCompletionPercentage() - 50.0) > 0.001) {
                passed = false;
                errors.append("    - Completion should be 50% after one task\n");
            }

            vm.finishTask(task2);

            // 100% completed
            if (Math.abs(vm.getCompletionPercentage() - 100.0) > 0.001) {
                passed = false;
                errors.append("    - Completion should be 100% after all tasks\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testHasFinishedAllTasks() {
        System.out.println("Test: hasFinishedAllTasks()");
        try {
            VM vm = new VM("user1");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // No tasks assigned - should return true
            if (!vm.hasFinishedAllTasks()) {
                passed = false;
                errors.append("    - Should return true when no tasks assigned\n");
            }

            Task task = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            vm.assignTask(task);

            // Tasks pending - should return false
            if (vm.hasFinishedAllTasks()) {
                passed = false;
                errors.append("    - Should return false when tasks are pending\n");
            }

            vm.finishTask(task);

            // All tasks finished - should return true
            if (!vm.hasFinishedAllTasks()) {
                passed = false;
                errors.append("    - Should return true when all tasks finished\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testHasEverHadTasks() {
        System.out.println("Test: hasEverHadTasks()");
        try {
            VM vm = new VM("user1");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // No tasks - should return false
            if (vm.hasEverHadTasks()) {
                passed = false;
                errors.append("    - Should return false when no tasks assigned\n");
            }

            Task task = new Task("Task1", "user1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            vm.assignTask(task);

            // Has tasks - should return true
            if (!vm.hasEverHadTasks()) {
                passed = false;
                errors.append("    - Should return true when tasks are assigned\n");
            }

            vm.finishTask(task);

            // Tasks finished - should still return true
            if (!vm.hasEverHadTasks()) {
                passed = false;
                errors.append("    - Should return true even after tasks are finished\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Workload Type Tests ====================

    private static void testGetCurrentWorkloadType() {
        System.out.println("Test: getCurrentWorkloadType()");
        try {
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            vm.setVmState(VmState.RUNNING);
            Task task = new Task("Task1", "user1", 10_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // No task executing - should return IDLE
            if (vm.getCurrentWorkloadType() != WorkloadType.IDLE) {
                passed = false;
                errors.append("    - Should return IDLE when no task executing\n");
            }

            vm.assignTask(task);
            vm.executeOneSecond(1000L);

            // Task executing - should return task's workload type
            if (vm.getCurrentWorkloadType() != WorkloadType.SEVEN_ZIP) {
                passed = false;
                errors.append("    - Should return SEVEN_ZIP when task is executing\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testGetCurrentExecutingTask() {
        System.out.println("Test: getCurrentExecutingTask()");
        try {
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            vm.setVmState(VmState.RUNNING);
            Task task = new Task("Task1", "user1", 10_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // No task executing
            if (vm.getCurrentExecutingTask() != null) {
                passed = false;
                errors.append("    - Should return null when no task executing\n");
            }

            vm.assignTask(task);
            vm.executeOneSecond(1000L);

            // Task executing
            if (vm.getCurrentExecutingTask() != task) {
                passed = false;
                errors.append("    - Should return the executing task\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Resource Calculation Tests ====================

    private static void testGetTotalRequestedIps() {
        System.out.println("Test: getTotalRequestedIps()");
        try {
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            long totalIps = vm.getTotalRequestedIps();
            long expected = 2_000_000_000L * 4; // 8 billion IPS

            if (totalIps != expected) {
                passed = false;
                errors.append("    - Total IPS should be " + expected + ", got " + totalIps + "\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Getters and Setters Tests ====================

    private static void testGettersAndSetters() {
        System.out.println("Test: Getters and Setters");
        try {
            VM vm = new VM("user1");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // Test setters
            vm.setUserId("user2");
            vm.setRequestedIpsPerVcpu(3_000_000_000L);
            vm.setRequestedVcpuCount(8);
            vm.setRequestedGpuCount(4);
            vm.setRequestedRamMB(16384);
            vm.setRequestedStorageMB(204800);
            vm.setRequestedBandwidthMbps(2000);
            vm.setVmState(VmState.RUNNING);
            vm.setComputeType(ComputeType.CPU_GPU_MIXED);
            vm.setActiveSeconds(500L);
            vm.setSecondsIDLE(200L);
            vm.setSecondsExecuting(300L);

            // Test getters
            if (!"user2".equals(vm.getUserId())) {
                passed = false;
                errors.append("    - User ID getter/setter mismatch\n");
            }
            if (vm.getRequestedIpsPerVcpu() != 3_000_000_000L) {
                passed = false;
                errors.append("    - IPS per vCPU getter/setter mismatch\n");
            }
            if (vm.getRequestedVcpuCount() != 8) {
                passed = false;
                errors.append("    - vCPU count getter/setter mismatch\n");
            }
            if (vm.getRequestedGpuCount() != 4) {
                passed = false;
                errors.append("    - GPU count getter/setter mismatch\n");
            }
            if (vm.getVmState() != VmState.RUNNING) {
                passed = false;
                errors.append("    - VM state getter/setter mismatch\n");
            }
            if (vm.getComputeType() != ComputeType.CPU_GPU_MIXED) {
                passed = false;
                errors.append("    - Compute type getter/setter mismatch\n");
            }
            if (vm.getActiveSeconds() != 500L) {
                passed = false;
                errors.append("    - Active seconds getter/setter mismatch\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testToString() {
        System.out.println("Test: toString()");
        try {
            VM vm = new VM("user1");
            String str = vm.toString();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!str.contains("VM{")) {
                passed = false;
                errors.append("    - toString should start with 'VM{'\n");
            }
            if (!str.contains("id=")) {
                passed = false;
                errors.append("    - toString should contain 'id='\n");
            }
            if (!str.contains("userId=")) {
                passed = false;
                errors.append("    - toString should contain 'userId='\n");
            }
            if (!str.contains("vmState=")) {
                passed = false;
                errors.append("    - toString should contain 'vmState='\n");
            }
            if (!str.contains("computeType=")) {
                passed = false;
                errors.append("    - toString should contain 'computeType='\n");
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED:\n" + errors);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }
}
