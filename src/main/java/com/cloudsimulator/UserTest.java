package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.VmState;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.User;
import com.cloudsimulator.model.VM;

import java.util.List;

/**
 * Unit tests for the User class.
 * Tests all major functionality including:
 * - Constructor variants (default and with name)
 * - Datacenter selection management
 * - VM management (add, remove, finish)
 * - Task management (add, remove, finish)
 * - Session timing (start, finish)
 * - Completion tracking
 * - Statistics (task count, VM count, execution time)
 * - Edge cases and null handling
 */
public class UserTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=== User Unit Tests ===\n");

        // Constructor tests
        testDefaultConstructor();
        testNamedConstructor();
        testUniqueIdGeneration();

        // Datacenter selection tests
        testAddSelectedDatacenter();
        testAddDuplicateDatacenter();
        testRemoveSelectedDatacenter();
        testHasSelectedDatacenter();

        // VM management tests
        testAddVirtualMachine();
        testAddDuplicateVM();
        testRemoveVirtualMachine();
        testFinishVM();
        testGetTotalVMCount();

        // Task management tests
        testAddTask();
        testAddDuplicateTask();
        testRemoveTask();
        testFinishTask();
        testGetTotalTaskCount();
        testGetActiveTaskCount();
        testGetCompletedTaskCount();

        // Session timing tests
        testStartSession();
        testFinishSession();
        testGetTotalExecutionTime();
        testGetTotalExecutionTimeWhenNotFinished();

        // Completion tracking tests
        testAllTasksCompletedEmpty();
        testAllTasksCompletedWithPendingTasks();
        testAllTasksCompletedWithVMTasks();
        testAllTasksCompletedWhenAllDone();

        // Getters and setters tests
        testGettersAndSetters();
        testToString();

        // Summary
        System.out.println("\n=== Test Summary ===");
        System.out.println("Tests Passed: " + testsPassed);
        System.out.println("Tests Failed: " + testsFailed);
        System.out.println("Total Tests: " + (testsPassed + testsFailed));

        if (testsFailed == 0) {
            System.out.println("\nAll User tests PASSED!");
        } else {
            System.out.println("\nSome tests FAILED. Please review the output above.");
            System.exit(1);
        }
    }

    // ==================== Constructor Tests ====================

    private static void testDefaultConstructor() {
        System.out.println("Test: Default Constructor");
        try {
            User user = new User();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getId() <= 0) {
                passed = false;
                errors.append("    - User ID should be positive\n");
            }

            if (user.getName() == null || !user.getName().startsWith("User-")) {
                passed = false;
                errors.append("    - Default name should start with 'User-'\n");
            }

            if (user.getUserSelectedDatacenters() == null) {
                passed = false;
                errors.append("    - Selected datacenters list should be initialized\n");
            }

            if (user.getVirtualMachines() == null) {
                passed = false;
                errors.append("    - Virtual machines list should be initialized\n");
            }

            if (user.getTasks() == null) {
                passed = false;
                errors.append("    - Tasks list should be initialized\n");
            }

            if (user.getStartTimestamp() != null) {
                passed = false;
                errors.append("    - Start timestamp should be null initially\n");
            }

            if (user.getFinishTimestamp() != null) {
                passed = false;
                errors.append("    - Finish timestamp should be null initially\n");
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

    private static void testNamedConstructor() {
        System.out.println("Test: Named Constructor");
        try {
            User user = new User("Alice");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getId() <= 0) {
                passed = false;
                errors.append("    - User ID should be positive\n");
            }

            if (!"Alice".equals(user.getName())) {
                passed = false;
                errors.append("    - User name should be 'Alice'\n");
            }

            if (user.getUserSelectedDatacenters() == null || !user.getUserSelectedDatacenters().isEmpty()) {
                passed = false;
                errors.append("    - Selected datacenters should be empty list\n");
            }

            if (user.getVirtualMachines() == null || !user.getVirtualMachines().isEmpty()) {
                passed = false;
                errors.append("    - Virtual machines should be empty list\n");
            }

            if (user.getTasks() == null || !user.getTasks().isEmpty()) {
                passed = false;
                errors.append("    - Tasks should be empty list\n");
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
            User user1 = new User("User1");
            User user2 = new User("User2");
            User user3 = new User();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user1.getId() == user2.getId()) {
                passed = false;
                errors.append("    - User IDs should be unique\n");
            }

            if (user2.getId() == user3.getId()) {
                passed = false;
                errors.append("    - User IDs should be unique\n");
            }

            if (user1.getId() == user3.getId()) {
                passed = false;
                errors.append("    - User IDs should be unique\n");
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

    // ==================== Datacenter Selection Tests ====================

    private static void testAddSelectedDatacenter() {
        System.out.println("Test: Add Selected Datacenter");
        try {
            User user = new User("Bob");
            user.addSelectedDatacenter(1L);
            user.addSelectedDatacenter(2L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getUserSelectedDatacenters().size() != 2) {
                passed = false;
                errors.append("    - Should have 2 selected datacenters\n");
            }

            if (!user.getUserSelectedDatacenters().contains(1L)) {
                passed = false;
                errors.append("    - Should contain datacenter ID 1\n");
            }

            if (!user.getUserSelectedDatacenters().contains(2L)) {
                passed = false;
                errors.append("    - Should contain datacenter ID 2\n");
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

    private static void testAddDuplicateDatacenter() {
        System.out.println("Test: Add Duplicate Datacenter");
        try {
            User user = new User("Charlie");
            user.addSelectedDatacenter(1L);
            user.addSelectedDatacenter(1L);
            user.addSelectedDatacenter(1L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getUserSelectedDatacenters().size() != 1) {
                passed = false;
                errors.append("    - Should have only 1 datacenter (no duplicates)\n");
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

    private static void testRemoveSelectedDatacenter() {
        System.out.println("Test: Remove Selected Datacenter");
        try {
            User user = new User("David");
            user.addSelectedDatacenter(1L);
            user.addSelectedDatacenter(2L);
            user.addSelectedDatacenter(3L);

            user.removeSelectedDatacenter(2L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getUserSelectedDatacenters().size() != 2) {
                passed = false;
                errors.append("    - Should have 2 datacenters after removal\n");
            }

            if (user.getUserSelectedDatacenters().contains(2L)) {
                passed = false;
                errors.append("    - Should not contain removed datacenter ID 2\n");
            }

            if (!user.getUserSelectedDatacenters().contains(1L) || !user.getUserSelectedDatacenters().contains(3L)) {
                passed = false;
                errors.append("    - Should still contain datacenters 1 and 3\n");
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

    private static void testHasSelectedDatacenter() {
        System.out.println("Test: Has Selected Datacenter");
        try {
            User user = new User("Eve");
            user.addSelectedDatacenter(5L);
            user.addSelectedDatacenter(10L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!user.hasSelectedDatacenter(5L)) {
                passed = false;
                errors.append("    - Should have datacenter 5\n");
            }

            if (!user.hasSelectedDatacenter(10L)) {
                passed = false;
                errors.append("    - Should have datacenter 10\n");
            }

            if (user.hasSelectedDatacenter(99L)) {
                passed = false;
                errors.append("    - Should not have datacenter 99\n");
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

    // ==================== VM Management Tests ====================

    private static void testAddVirtualMachine() {
        System.out.println("Test: Add Virtual Machine");
        try {
            User user = new User("Frank");
            VM vm1 = new VM("Frank", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            VM vm2 = new VM("Frank", 3_000_000_000L, 8, 4, 16384, 204800, 2000, ComputeType.GPU_ONLY);

            user.addVirtualMachine(vm1);
            user.addVirtualMachine(vm2);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getVirtualMachines().size() != 2) {
                passed = false;
                errors.append("    - Should have 2 VMs\n");
            }

            if (!user.getVirtualMachines().contains(vm1)) {
                passed = false;
                errors.append("    - Should contain VM1\n");
            }

            if (!user.getVirtualMachines().contains(vm2)) {
                passed = false;
                errors.append("    - Should contain VM2\n");
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

    private static void testAddDuplicateVM() {
        System.out.println("Test: Add Duplicate VM");
        try {
            User user = new User("Grace");
            VM vm = new VM("Grace", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);

            user.addVirtualMachine(vm);
            user.addVirtualMachine(vm);
            user.addVirtualMachine(vm);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getVirtualMachines().size() != 1) {
                passed = false;
                errors.append("    - Should have only 1 VM (no duplicates)\n");
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

    private static void testRemoveVirtualMachine() {
        System.out.println("Test: Remove Virtual Machine");
        try {
            User user = new User("Henry");
            VM vm1 = new VM("Henry", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            VM vm2 = new VM("Henry", 3_000_000_000L, 8, 4, 16384, 204800, 2000, ComputeType.GPU_ONLY);

            user.addVirtualMachine(vm1);
            user.addVirtualMachine(vm2);
            user.removeVirtualMachine(vm1);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getVirtualMachines().size() != 1) {
                passed = false;
                errors.append("    - Should have 1 VM after removal\n");
            }

            if (user.getVirtualMachines().contains(vm1)) {
                passed = false;
                errors.append("    - Should not contain removed VM1\n");
            }

            if (!user.getVirtualMachines().contains(vm2)) {
                passed = false;
                errors.append("    - Should still contain VM2\n");
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

    private static void testFinishVM() {
        System.out.println("Test: Finish VM");
        try {
            User user = new User("Ivy");
            VM vm1 = new VM("Ivy", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            VM vm2 = new VM("Ivy", 3_000_000_000L, 8, 4, 16384, 204800, 2000, ComputeType.GPU_ONLY);

            user.addVirtualMachine(vm1);
            user.addVirtualMachine(vm2);
            user.finishVM(vm1);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getVirtualMachines().size() != 1) {
                passed = false;
                errors.append("    - Should have 1 active VM after finishing vm1\n");
            }

            if (user.getVmsFinishedExecuting().size() != 1) {
                passed = false;
                errors.append("    - Should have 1 finished VM\n");
            }

            if (!user.getVmsFinishedExecuting().contains(vm1)) {
                passed = false;
                errors.append("    - Finished VMs list should contain vm1\n");
            }

            if (user.getVirtualMachines().contains(vm1)) {
                passed = false;
                errors.append("    - Active VMs list should not contain vm1\n");
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

    private static void testGetTotalVMCount() {
        System.out.println("Test: Get Total VM Count");
        try {
            User user = new User("Jack");
            VM vm1 = new VM("Jack", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            VM vm2 = new VM("Jack", 3_000_000_000L, 8, 4, 16384, 204800, 2000, ComputeType.GPU_ONLY);
            VM vm3 = new VM("Jack", 2_500_000_000L, 2, 2, 12288, 153600, 1500, ComputeType.CPU_GPU_MIXED);

            user.addVirtualMachine(vm1);
            user.addVirtualMachine(vm2);
            user.addVirtualMachine(vm3);
            user.finishVM(vm1);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getTotalVMCount() != 3) {
                passed = false;
                errors.append("    - Total VM count should be 3 (active + finished)\n");
            }

            if (user.getVirtualMachines().size() != 2) {
                passed = false;
                errors.append("    - Active VM count should be 2\n");
            }

            if (user.getVmsFinishedExecuting().size() != 1) {
                passed = false;
                errors.append("    - Finished VM count should be 1\n");
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

    // ==================== Task Management Tests ====================

    private static void testAddTask() {
        System.out.println("Test: Add Task");
        try {
            User user = new User("Karen");
            Task task1 = new Task("Task1", "Karen", 5_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "Karen", 3_000_000_000L, WorkloadType.DATABASE);

            user.addTask(task1);
            user.addTask(task2);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getTasks().size() != 2) {
                passed = false;
                errors.append("    - Should have 2 tasks\n");
            }

            if (!user.getTasks().contains(task1)) {
                passed = false;
                errors.append("    - Should contain task1\n");
            }

            if (!user.getTasks().contains(task2)) {
                passed = false;
                errors.append("    - Should contain task2\n");
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

    private static void testAddDuplicateTask() {
        System.out.println("Test: Add Duplicate Task");
        try {
            User user = new User("Leo");
            Task task = new Task("Task1", "Leo", 5_000_000_000L, WorkloadType.SEVEN_ZIP);

            user.addTask(task);
            user.addTask(task);
            user.addTask(task);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getTasks().size() != 1) {
                passed = false;
                errors.append("    - Should have only 1 task (no duplicates)\n");
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

    private static void testRemoveTask() {
        System.out.println("Test: Remove Task");
        try {
            User user = new User("Maria");
            Task task1 = new Task("Task1", "Maria", 5_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "Maria", 3_000_000_000L, WorkloadType.DATABASE);

            user.addTask(task1);
            user.addTask(task2);
            user.removeTask(task1);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getTasks().size() != 1) {
                passed = false;
                errors.append("    - Should have 1 task after removal\n");
            }

            if (user.getTasks().contains(task1)) {
                passed = false;
                errors.append("    - Should not contain removed task1\n");
            }

            if (!user.getTasks().contains(task2)) {
                passed = false;
                errors.append("    - Should still contain task2\n");
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

    private static void testFinishTask() {
        System.out.println("Test: Finish Task");
        try {
            User user = new User("Nancy");
            Task task1 = new Task("Task1", "Nancy", 5_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "Nancy", 3_000_000_000L, WorkloadType.DATABASE);

            user.addTask(task1);
            user.addTask(task2);
            user.finishTask(task1);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getTasks().size() != 1) {
                passed = false;
                errors.append("    - Should have 1 active task after finishing task1\n");
            }

            if (user.getTasksFinishedExecuting().size() != 1) {
                passed = false;
                errors.append("    - Should have 1 finished task\n");
            }

            if (!user.getTasksFinishedExecuting().contains(task1)) {
                passed = false;
                errors.append("    - Finished tasks list should contain task1\n");
            }

            if (user.getTasks().contains(task1)) {
                passed = false;
                errors.append("    - Active tasks list should not contain task1\n");
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

    private static void testGetTotalTaskCount() {
        System.out.println("Test: Get Total Task Count");
        try {
            User user = new User("Oscar");
            Task task1 = new Task("Task1", "Oscar", 5_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "Oscar", 3_000_000_000L, WorkloadType.DATABASE);
            Task task3 = new Task("Task3", "Oscar", 2_000_000_000L, WorkloadType.CINEBENCH);

            user.addTask(task1);
            user.addTask(task2);
            user.addTask(task3);
            user.finishTask(task1);
            user.finishTask(task2);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getTotalTaskCount() != 3) {
                passed = false;
                errors.append("    - Total task count should be 3 (active + finished)\n");
            }

            if (user.getTasks().size() != 1) {
                passed = false;
                errors.append("    - Active task count should be 1\n");
            }

            if (user.getTasksFinishedExecuting().size() != 2) {
                passed = false;
                errors.append("    - Finished task count should be 2\n");
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

    private static void testGetActiveTaskCount() {
        System.out.println("Test: Get Active Task Count");
        try {
            User user = new User("Paula");
            Task task1 = new Task("Task1", "Paula", 5_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "Paula", 3_000_000_000L, WorkloadType.DATABASE);
            Task task3 = new Task("Task3", "Paula", 2_000_000_000L, WorkloadType.CINEBENCH);

            user.addTask(task1);
            user.addTask(task2);
            user.addTask(task3);
            user.finishTask(task1);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getActiveTaskCount() != 2) {
                passed = false;
                errors.append("    - Active task count should be 2\n");
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

    private static void testGetCompletedTaskCount() {
        System.out.println("Test: Get Completed Task Count");
        try {
            User user = new User("Quinn");
            Task task1 = new Task("Task1", "Quinn", 5_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "Quinn", 3_000_000_000L, WorkloadType.DATABASE);
            Task task3 = new Task("Task3", "Quinn", 2_000_000_000L, WorkloadType.CINEBENCH);

            user.addTask(task1);
            user.addTask(task2);
            user.addTask(task3);
            user.finishTask(task1);
            user.finishTask(task2);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getCompletedTaskCount() != 2) {
                passed = false;
                errors.append("    - Completed task count should be 2\n");
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

    // ==================== Session Timing Tests ====================

    private static void testStartSession() {
        System.out.println("Test: Start Session");
        try {
            User user = new User("Rachel");
            user.startSession(1000L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getStartTimestamp() == null) {
                passed = false;
                errors.append("    - Start timestamp should be set\n");
            }

            if (user.getStartTimestamp() != 1000L) {
                passed = false;
                errors.append("    - Start timestamp should be 1000\n");
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

    private static void testFinishSession() {
        System.out.println("Test: Finish Session");
        try {
            User user = new User("Sam");
            user.startSession(1000L);
            user.finishSession(5000L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.getFinishTimestamp() == null) {
                passed = false;
                errors.append("    - Finish timestamp should be set\n");
            }

            if (user.getFinishTimestamp() != 5000L) {
                passed = false;
                errors.append("    - Finish timestamp should be 5000\n");
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

    private static void testGetTotalExecutionTime() {
        System.out.println("Test: Get Total Execution Time");
        try {
            User user = new User("Tina");
            user.startSession(1000L);
            user.finishSession(5000L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            Long executionTime = user.getTotalExecutionTime();

            if (executionTime == null) {
                passed = false;
                errors.append("    - Execution time should not be null\n");
            }

            if (executionTime != 4000L) {
                passed = false;
                errors.append("    - Execution time should be 4000 (5000 - 1000)\n");
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

    private static void testGetTotalExecutionTimeWhenNotFinished() {
        System.out.println("Test: Get Total Execution Time (Not Finished)");
        try {
            User user = new User("Uma");
            user.startSession(1000L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            Long executionTime = user.getTotalExecutionTime();

            if (executionTime != null) {
                passed = false;
                errors.append("    - Execution time should be null when session not finished\n");
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

    // ==================== Completion Tracking Tests ====================

    private static void testAllTasksCompletedEmpty() {
        System.out.println("Test: All Tasks Completed (Empty)");
        try {
            User user = new User("Victor");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!user.allTasksCompleted()) {
                passed = false;
                errors.append("    - Empty user should have all tasks completed\n");
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

    private static void testAllTasksCompletedWithPendingTasks() {
        System.out.println("Test: All Tasks Completed (With Pending Tasks)");
        try {
            User user = new User("Wendy");
            Task task = new Task("Task1", "Wendy", 5_000_000_000L, WorkloadType.SEVEN_ZIP);
            user.addTask(task);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.allTasksCompleted()) {
                passed = false;
                errors.append("    - User with pending tasks should not have all tasks completed\n");
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

    private static void testAllTasksCompletedWithVMTasks() {
        System.out.println("Test: All Tasks Completed (With VM Tasks)");
        try {
            User user = new User("Xavier");
            VM vm = new VM("Xavier", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            Task task = new Task("Task1", "Xavier", 5_000_000_000L, WorkloadType.SEVEN_ZIP);

            vm.assignTask(task);
            user.addVirtualMachine(vm);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (user.allTasksCompleted()) {
                passed = false;
                errors.append("    - User with VM tasks should not have all tasks completed\n");
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

    private static void testAllTasksCompletedWhenAllDone() {
        System.out.println("Test: All Tasks Completed (All Done)");
        try {
            User user = new User("Yolanda");
            Task task1 = new Task("Task1", "Yolanda", 5_000_000_000L, WorkloadType.SEVEN_ZIP);
            Task task2 = new Task("Task2", "Yolanda", 3_000_000_000L, WorkloadType.DATABASE);

            user.addTask(task1);
            user.addTask(task2);
            user.finishTask(task1);
            user.finishTask(task2);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!user.allTasksCompleted()) {
                passed = false;
                errors.append("    - User with all tasks finished should have all tasks completed\n");
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
            User user = new User("Zack");

            user.setName("Zachary");

            List<Long> datacenters = List.of(1L, 2L, 3L);
            user.setUserSelectedDatacenters(datacenters);

            user.setStartTimestamp(1000L);
            user.setFinishTimestamp(5000L);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!"Zachary".equals(user.getName())) {
                passed = false;
                errors.append("    - Name should be 'Zachary'\n");
            }

            if (user.getStartTimestamp() != 1000L) {
                passed = false;
                errors.append("    - Start timestamp should be 1000\n");
            }

            if (user.getFinishTimestamp() != 5000L) {
                passed = false;
                errors.append("    - Finish timestamp should be 5000\n");
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
            User user = new User("TestUser");
            user.addSelectedDatacenter(1L);
            user.startSession(1000L);

            String result = user.toString();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (result == null || result.isEmpty()) {
                passed = false;
                errors.append("    - toString should return non-empty string\n");
            }

            if (!result.contains("TestUser")) {
                passed = false;
                errors.append("    - toString should contain user name\n");
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
