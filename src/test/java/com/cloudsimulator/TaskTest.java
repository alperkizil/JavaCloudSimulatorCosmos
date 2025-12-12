package com.cloudsimulator;

import com.cloudsimulator.enums.TaskExecutionStatus;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Task;

/**
 * Unit tests for the Task class.
 * Tests all major functionality including:
 * - Constructor variants (full and simplified)
 * - VM assignment
 * - Execution lifecycle (start, execute, finish)
 * - Instruction execution and progress tracking
 * - Status checking (isAssigned, isExecuting, isCompleted)
 * - Time calculations (waiting time, turnaround time)
 * - Reset functionality
 * - Edge cases and boundary conditions
 */
public class TaskTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=== Task Unit Tests ===\n");

        // Constructor tests
        testFullConstructor();
        testSimplifiedConstructor();
        testUniqueIdGeneration();

        // Assignment tests
        testAssignToVM();
        testIsAssigned();

        // Execution lifecycle tests
        testStartExecution();
        testFinishExecution();
        testIsExecuting();
        testIsCompleted();

        // Instruction execution tests
        testExecuteInstructions();
        testExecuteInstructionsPartial();
        testExecuteInstructionsOverflow();
        testGetProgressPercentage();
        testGetRemainingInstructions();
        testIsComplete();

        // Time calculation tests
        testGetWaitingTime();
        testGetWaitingTimeNotStarted();
        testGetTurnaroundTime();
        testGetTurnaroundTimeNotFinished();
        testGetEstimatedRemainingTime();
        testGetEstimatedRemainingTimeZeroIPS();

        // Increment tests
        testIncrementExecTime();

        // Reset tests
        testReset();

        // Edge case tests
        testZeroInstructionTask();
        testProgressPercentageZeroLength();

        // Getters and setters tests
        testGettersAndSetters();
        testToString();

        // Summary
        System.out.println("\n=== Test Summary ===");
        System.out.println("Tests Passed: " + testsPassed);
        System.out.println("Tests Failed: " + testsFailed);
        System.out.println("Total Tests: " + (testsPassed + testsFailed));

        if (testsFailed == 0) {
            System.out.println("\nAll Task tests PASSED!");
        } else {
            System.out.println("\nSome tests FAILED. Please review the output above.");
            System.exit(1);
        }
    }

    // ==================== Constructor Tests ====================

    private static void testFullConstructor() {
        System.out.println("Test: Full Constructor");
        try {
            Task task = new Task("CompressData", "Alice", 5_000_000_000L,
                    WorkloadType.SEVEN_ZIP, 100L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.getId() <= 0) {
                passed = false;
                errors.append("    - Task ID should be positive\n");
            }

            if (!"CompressData".equals(task.getName())) {
                passed = false;
                errors.append("    - Task name should be 'CompressData'\n");
            }

            if (!"Alice".equals(task.getUserId())) {
                passed = false;
                errors.append("    - User ID should be 'Alice'\n");
            }

            if (task.getInstructionLength() != 5_000_000_000L) {
                passed = false;
                errors.append("    - Instruction length should be 5,000,000,000\n");
            }

            if (task.getWorkloadType() != WorkloadType.SEVEN_ZIP) {
                passed = false;
                errors.append("    - Workload type should be SEVEN_ZIP\n");
            }

            if (task.getTaskCreationTime() != 100L) {
                passed = false;
                errors.append("    - Creation time should be 100\n");
            }

            if (task.getAssignedVmId() != null) {
                passed = false;
                errors.append("    - Assigned VM ID should be null initially\n");
            }

            if (task.getTaskExecutionStatus() != TaskExecutionStatus.NOT_EXECUTED) {
                passed = false;
                errors.append("    - Initial status should be NOT_EXECUTED\n");
            }

            if (task.getInstructionsExecuted() != 0) {
                passed = false;
                errors.append("    - Instructions executed should be 0 initially\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testSimplifiedConstructor() {
        System.out.println("Test: Simplified Constructor");
        try {
            Task task = new Task("QueryProcessing", "Bob", 3_000_000_000L,
                    WorkloadType.DATABASE);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!"QueryProcessing".equals(task.getName())) {
                passed = false;
                errors.append("    - Task name should be 'QueryProcessing'\n");
            }

            if (task.getTaskCreationTime() != 0L) {
                passed = false;
                errors.append("    - Creation time should default to 0\n");
            }

            if (task.getWorkloadType() != WorkloadType.DATABASE) {
                passed = false;
                errors.append("    - Workload type should be DATABASE\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testUniqueIdGeneration() {
        System.out.println("Test: Unique ID Generation");
        try {
            Task task1 = new Task("Task1", "User1", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "User2", 2_000_000_000L, WorkloadType.DATABASE);
            Task task3 = new Task("Task3", "User3", 3_000_000_000L, WorkloadType.CINEBENCH);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task1.getId() == task2.getId()) {
                passed = false;
                errors.append("    - Task IDs should be unique\n");
            }

            if (task2.getId() == task3.getId()) {
                passed = false;
                errors.append("    - Task IDs should be unique\n");
            }

            if (task1.getId() == task3.getId()) {
                passed = false;
                errors.append("    - Task IDs should be unique\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ==================== Assignment Tests ====================

    private static void testAssignToVM() {
        System.out.println("Test: Assign to VM");
        try {
            Task task = new Task("Task1", "Charlie", 5_000_000_000L, WorkloadType.SEVEN_ZIP, 100L);
            task.assignToVM(42L, 150L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.getAssignedVmId() == null) {
                passed = false;
                errors.append("    - Assigned VM ID should not be null\n");
            }

            if (task.getAssignedVmId() != 42L) {
                passed = false;
                errors.append("    - Assigned VM ID should be 42\n");
            }

            if (task.getTaskAssignmentTime() == null) {
                passed = false;
                errors.append("    - Assignment time should not be null\n");
            }

            if (task.getTaskAssignmentTime() != 150L) {
                passed = false;
                errors.append("    - Assignment time should be 150\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testIsAssigned() {
        System.out.println("Test: Is Assigned");
        try {
            Task task = new Task("Task1", "David", 5_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.isAssigned()) {
                passed = false;
                errors.append("    - Task should not be assigned initially\n");
            }

            task.assignToVM(1L, 100L);

            if (!task.isAssigned()) {
                passed = false;
                errors.append("    - Task should be assigned after assignToVM\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ==================== Execution Lifecycle Tests ====================

    private static void testStartExecution() {
        System.out.println("Test: Start Execution");
        try {
            Task task = new Task("Task1", "Eve", 5_000_000_000L, WorkloadType.SEVEN_ZIP, 100L);
            task.startExecution(200L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.getTaskExecStartTime() == null) {
                passed = false;
                errors.append("    - Execution start time should not be null\n");
            }

            if (task.getTaskExecStartTime() != 200L) {
                passed = false;
                errors.append("    - Execution start time should be 200\n");
            }

            if (task.getTaskExecutionStatus() != TaskExecutionStatus.EXECUTING) {
                passed = false;
                errors.append("    - Status should be EXECUTING after start\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testFinishExecution() {
        System.out.println("Test: Finish Execution");
        try {
            Task task = new Task("Task1", "Frank", 5_000_000_000L, WorkloadType.SEVEN_ZIP, 100L);
            task.startExecution(200L);
            task.finishExecution(500L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.getTaskExecEndTime() == null) {
                passed = false;
                errors.append("    - Execution end time should not be null\n");
            }

            if (task.getTaskExecEndTime() != 500L) {
                passed = false;
                errors.append("    - Execution end time should be 500\n");
            }

            if (task.getTaskExecutionStatus() != TaskExecutionStatus.EXECUTED) {
                passed = false;
                errors.append("    - Status should be EXECUTED after finish\n");
            }

            if (task.getTaskCpuExecTime() != 300L) {
                passed = false;
                errors.append("    - CPU execution time should be 300 (500 - 200)\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testIsExecuting() {
        System.out.println("Test: Is Executing");
        try {
            Task task = new Task("Task1", "Grace", 5_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.isExecuting()) {
                passed = false;
                errors.append("    - Task should not be executing initially\n");
            }

            task.startExecution(100L);

            if (!task.isExecuting()) {
                passed = false;
                errors.append("    - Task should be executing after start\n");
            }

            task.finishExecution(200L);

            if (task.isExecuting()) {
                passed = false;
                errors.append("    - Task should not be executing after finish\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testIsCompleted() {
        System.out.println("Test: Is Completed");
        try {
            Task task = new Task("Task1", "Henry", 5_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.isCompleted()) {
                passed = false;
                errors.append("    - Task should not be completed initially\n");
            }

            task.startExecution(100L);

            if (task.isCompleted()) {
                passed = false;
                errors.append("    - Task should not be completed while executing\n");
            }

            task.finishExecution(200L);

            if (!task.isCompleted()) {
                passed = false;
                errors.append("    - Task should be completed after finish\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ==================== Instruction Execution Tests ====================

    private static void testExecuteInstructions() {
        System.out.println("Test: Execute Instructions");
        try {
            Task task = new Task("Task1", "Ivy", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            task.executeInstructions(500_000_000L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.getInstructionsExecuted() != 500_000_000L) {
                passed = false;
                errors.append("    - Instructions executed should be 500,000,000\n");
            }

            task.executeInstructions(300_000_000L);

            if (task.getInstructionsExecuted() != 800_000_000L) {
                passed = false;
                errors.append("    - Instructions executed should be 800,000,000 after second execution\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testExecuteInstructionsPartial() {
        System.out.println("Test: Execute Instructions (Partial)");
        try {
            Task task = new Task("Task1", "Jack", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            task.executeInstructions(250_000_000L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.isComplete()) {
                passed = false;
                errors.append("    - Task should not be complete after partial execution\n");
            }

            if (task.getRemainingInstructions() != 750_000_000L) {
                passed = false;
                errors.append("    - Remaining instructions should be 750,000,000\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testExecuteInstructionsOverflow() {
        System.out.println("Test: Execute Instructions (Overflow)");
        try {
            Task task = new Task("Task1", "Karen", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            task.executeInstructions(1_500_000_000L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.getInstructionsExecuted() != 1_000_000_000L) {
                passed = false;
                errors.append("    - Instructions executed should be capped at instruction length\n");
            }

            if (!task.isComplete()) {
                passed = false;
                errors.append("    - Task should be complete after executing all instructions\n");
            }

            if (task.getRemainingInstructions() != 0L) {
                passed = false;
                errors.append("    - Remaining instructions should be 0\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testGetProgressPercentage() {
        System.out.println("Test: Get Progress Percentage");
        try {
            Task task = new Task("Task1", "Leo", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (Math.abs(task.getProgressPercentage() - 0.0) > 0.001) {
                passed = false;
                errors.append("    - Initial progress should be 0%\n");
            }

            task.executeInstructions(250_000_000L);

            if (Math.abs(task.getProgressPercentage() - 25.0) > 0.001) {
                passed = false;
                errors.append("    - Progress should be 25% after 250M instructions\n");
            }

            task.executeInstructions(500_000_000L);

            if (Math.abs(task.getProgressPercentage() - 75.0) > 0.001) {
                passed = false;
                errors.append("    - Progress should be 75% after 750M total instructions\n");
            }

            task.executeInstructions(250_000_000L);

            if (Math.abs(task.getProgressPercentage() - 100.0) > 0.001) {
                passed = false;
                errors.append("    - Progress should be 100% after all instructions\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testGetRemainingInstructions() {
        System.out.println("Test: Get Remaining Instructions");
        try {
            Task task = new Task("Task1", "Maria", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.getRemainingInstructions() != 1_000_000_000L) {
                passed = false;
                errors.append("    - Initial remaining should be 1,000,000,000\n");
            }

            task.executeInstructions(400_000_000L);

            if (task.getRemainingInstructions() != 600_000_000L) {
                passed = false;
                errors.append("    - Remaining should be 600,000,000 after 400M instructions\n");
            }

            task.executeInstructions(600_000_000L);

            if (task.getRemainingInstructions() != 0L) {
                passed = false;
                errors.append("    - Remaining should be 0 after completing all instructions\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testIsComplete() {
        System.out.println("Test: Is Complete");
        try {
            Task task = new Task("Task1", "Nancy", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.isComplete()) {
                passed = false;
                errors.append("    - Task should not be complete initially\n");
            }

            task.executeInstructions(999_999_999L);

            if (task.isComplete()) {
                passed = false;
                errors.append("    - Task should not be complete with 1 instruction remaining\n");
            }

            task.executeInstructions(1L);

            if (!task.isComplete()) {
                passed = false;
                errors.append("    - Task should be complete after executing all instructions\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ==================== Time Calculation Tests ====================

    private static void testGetWaitingTime() {
        System.out.println("Test: Get Waiting Time");
        try {
            Task task = new Task("Task1", "Oscar", 1_000_000_000L, WorkloadType.SEVEN_ZIP, 100L);
            task.startExecution(250L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            Long waitingTime = task.getWaitingTime();

            if (waitingTime == null) {
                passed = false;
                errors.append("    - Waiting time should not be null\n");
            }

            if (waitingTime != 150L) {
                passed = false;
                errors.append("    - Waiting time should be 150 (250 - 100)\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testGetWaitingTimeNotStarted() {
        System.out.println("Test: Get Waiting Time (Not Started)");
        try {
            Task task = new Task("Task1", "Paula", 1_000_000_000L, WorkloadType.SEVEN_ZIP, 100L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            Long waitingTime = task.getWaitingTime();

            if (waitingTime != null) {
                passed = false;
                errors.append("    - Waiting time should be null when not started\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testGetTurnaroundTime() {
        System.out.println("Test: Get Turnaround Time");
        try {
            Task task = new Task("Task1", "Quinn", 1_000_000_000L, WorkloadType.SEVEN_ZIP, 100L);
            task.startExecution(250L);
            task.finishExecution(550L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            Long turnaroundTime = task.getTurnaroundTime();

            if (turnaroundTime == null) {
                passed = false;
                errors.append("    - Turnaround time should not be null\n");
            }

            if (turnaroundTime != 450L) {
                passed = false;
                errors.append("    - Turnaround time should be 450 (550 - 100)\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testGetTurnaroundTimeNotFinished() {
        System.out.println("Test: Get Turnaround Time (Not Finished)");
        try {
            Task task = new Task("Task1", "Rachel", 1_000_000_000L, WorkloadType.SEVEN_ZIP, 100L);
            task.startExecution(250L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            Long turnaroundTime = task.getTurnaroundTime();

            if (turnaroundTime != null) {
                passed = false;
                errors.append("    - Turnaround time should be null when not finished\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testGetEstimatedRemainingTime() {
        System.out.println("Test: Get Estimated Remaining Time");
        try {
            Task task = new Task("Task1", "Sam", 1_000_000_000L, WorkloadType.SEVEN_ZIP);
            task.executeInstructions(600_000_000L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // Remaining: 400,000,000 instructions
            // IPS: 100,000,000
            // Expected time: 4 seconds
            long estimatedTime = task.getEstimatedRemainingTime(100_000_000L);

            if (estimatedTime != 4L) {
                passed = false;
                errors.append("    - Estimated remaining time should be 4 seconds\n");
            }

            // Test with different IPS
            estimatedTime = task.getEstimatedRemainingTime(200_000_000L);

            if (estimatedTime != 2L) {
                passed = false;
                errors.append("    - Estimated remaining time should be 2 seconds with 200M IPS\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testGetEstimatedRemainingTimeZeroIPS() {
        System.out.println("Test: Get Estimated Remaining Time (Zero IPS)");
        try {
            Task task = new Task("Task1", "Tina", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            long estimatedTime = task.getEstimatedRemainingTime(0L);

            if (estimatedTime != Long.MAX_VALUE) {
                passed = false;
                errors.append("    - Estimated remaining time should be Long.MAX_VALUE for zero IPS\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ==================== Increment Tests ====================

    private static void testIncrementExecTime() {
        System.out.println("Test: Increment Exec Time");
        try {
            Task task = new Task("Task1", "Uma", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.getTaskCpuExecTime() != 0L) {
                passed = false;
                errors.append("    - Initial exec time should be 0\n");
            }

            task.incrementExecTime();
            task.incrementExecTime();
            task.incrementExecTime();

            if (task.getTaskCpuExecTime() != 3L) {
                passed = false;
                errors.append("    - Exec time should be 3 after 3 increments\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ==================== Reset Tests ====================

    private static void testReset() {
        System.out.println("Test: Reset");
        try {
            Task task = new Task("Task1", "Victor", 1_000_000_000L, WorkloadType.SEVEN_ZIP, 100L);
            task.assignToVM(42L, 150L);
            task.startExecution(200L);
            task.executeInstructions(500_000_000L);
            task.incrementExecTime();
            task.incrementExecTime();

            task.reset();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (task.getInstructionsExecuted() != 0L) {
                passed = false;
                errors.append("    - Instructions executed should be 0 after reset\n");
            }

            if (task.getTaskExecutionStatus() != TaskExecutionStatus.NOT_EXECUTED) {
                passed = false;
                errors.append("    - Status should be NOT_EXECUTED after reset\n");
            }

            if (task.getTaskExecStartTime() != null) {
                passed = false;
                errors.append("    - Exec start time should be null after reset\n");
            }

            if (task.getTaskExecEndTime() != null) {
                passed = false;
                errors.append("    - Exec end time should be null after reset\n");
            }

            if (task.getTaskCpuExecTime() != 0L) {
                passed = false;
                errors.append("    - CPU exec time should be 0 after reset\n");
            }

            // Note: Reset does not clear assignment or creation time
            if (task.getAssignedVmId() == null) {
                passed = false;
                errors.append("    - Assignment should be preserved after reset\n");
            }

            if (task.getTaskCreationTime() != 100L) {
                passed = false;
                errors.append("    - Creation time should be preserved after reset\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ==================== Edge Case Tests ====================

    private static void testZeroInstructionTask() {
        System.out.println("Test: Zero Instruction Task");
        try {
            Task task = new Task("EmptyTask", "Wendy", 0L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!task.isComplete()) {
                passed = false;
                errors.append("    - Zero instruction task should be complete immediately\n");
            }

            if (task.getRemainingInstructions() != 0L) {
                passed = false;
                errors.append("    - Remaining instructions should be 0\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testProgressPercentageZeroLength() {
        System.out.println("Test: Progress Percentage (Zero Length)");
        try {
            Task task = new Task("EmptyTask", "Xavier", 0L, WorkloadType.SEVEN_ZIP);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (Math.abs(task.getProgressPercentage() - 100.0) > 0.001) {
                passed = false;
                errors.append("    - Progress should be 100% for zero-length task\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ==================== Getters and Setters Tests ====================

    private static void testGettersAndSetters() {
        System.out.println("Test: Getters and Setters");
        try {
            Task task = new Task("Task1", "Yolanda", 1_000_000_000L, WorkloadType.SEVEN_ZIP);

            task.setName("UpdatedTask");
            task.setUserId("NewUser");
            task.setInstructionLength(2_000_000_000L);
            task.setWorkloadType(WorkloadType.DATABASE);
            task.setAssignedVmId(99L);
            task.setTaskCreationTime(500L);
            task.setTaskAssignmentTime(600L);
            task.setTaskExecStartTime(700L);
            task.setTaskExecEndTime(800L);
            task.setTaskExecutionStatus(TaskExecutionStatus.EXECUTED);
            task.setTaskCpuExecTime(100L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!"UpdatedTask".equals(task.getName())) {
                passed = false;
                errors.append("    - Name should be 'UpdatedTask'\n");
            }

            if (!"NewUser".equals(task.getUserId())) {
                passed = false;
                errors.append("    - User ID should be 'NewUser'\n");
            }

            if (task.getInstructionLength() != 2_000_000_000L) {
                passed = false;
                errors.append("    - Instruction length should be 2,000,000,000\n");
            }

            if (task.getWorkloadType() != WorkloadType.DATABASE) {
                passed = false;
                errors.append("    - Workload type should be DATABASE\n");
            }

            if (task.getAssignedVmId() != 99L) {
                passed = false;
                errors.append("    - Assigned VM ID should be 99\n");
            }

            if (task.getTaskCreationTime() != 500L) {
                passed = false;
                errors.append("    - Creation time should be 500\n");
            }

            if (task.getTaskAssignmentTime() != 600L) {
                passed = false;
                errors.append("    - Assignment time should be 600\n");
            }

            if (task.getTaskExecStartTime() != 700L) {
                passed = false;
                errors.append("    - Exec start time should be 700\n");
            }

            if (task.getTaskExecEndTime() != 800L) {
                passed = false;
                errors.append("    - Exec end time should be 800\n");
            }

            if (task.getTaskExecutionStatus() != TaskExecutionStatus.EXECUTED) {
                passed = false;
                errors.append("    - Execution status should be EXECUTED\n");
            }

            if (task.getTaskCpuExecTime() != 100L) {
                passed = false;
                errors.append("    - CPU exec time should be 100\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private static void testToString() {
        System.out.println("Test: ToString");
        try {
            Task task = new Task("CompressData", "Zack", 5_000_000_000L,
                    WorkloadType.SEVEN_ZIP, 100L);
            task.assignToVM(42L, 150L);

            String result = task.toString();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (result == null || result.isEmpty()) {
                passed = false;
                errors.append("    - toString should return non-empty string\n");
            }

            if (!result.contains("CompressData")) {
                passed = false;
                errors.append("    - toString should contain task name\n");
            }

            if (!result.contains("Zack")) {
                passed = false;
                errors.append("    - toString should contain user ID\n");
            }

            if (!result.contains("SEVEN_ZIP")) {
                passed = false;
                errors.append("    - toString should contain workload type\n");
            }

            if (!result.contains("id=")) {
                passed = false;
                errors.append("    - toString should contain id field\n");
            }

            if (passed) {
                testsPassed++;
                System.out.println("  ✓ PASSED\n");
            } else {
                testsFailed++;
                System.out.println("  ✗ FAILED");
                System.out.print(errors.toString());
                System.out.println();
            }
        } catch (Exception e) {
            testsFailed++;
            System.out.println("  ✗ FAILED with exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}
