package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.VmState;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.PowerModel;
import com.cloudsimulator.model.VM;

/**
 * Unit tests for the Host class.
 * Tests all major functionality including:
 * - Constructor variants
 * - Resource capacity management
 * - VM assignment and removal
 * - Resource allocation and deallocation
 * - Power consumption tracking
 * - Energy consumption tracking
 * - Utilization history
 * - State updates
 */
public class HostTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=== Host Unit Tests ===\n");

        // Constructor tests
        testDefaultConstructor();
        testCustomConstructor();

        // Capacity tests
        testHasCapacityFor();
        testHasCapacityForVM();
        testResourceAvailability();

        // VM assignment tests
        testAssignVM();
        testAssignVMExceedsCapacity();
        testRemoveVM();
        testMultipleVMAssignments();

        // Resource allocation tests
        testAllocateResources();
        testDeallocateResources();
        testResourceUtilization();

        // Power and energy tests
        testUpdatePowerConsumption();
        testUpdateEnergyConsumption();
        testGetTotalEnergyConsumed();
        testGetTotalEnergyConsumedKWh();

        // Activation tests
        testActivate();
        testActivateAlreadyActive();

        // State update tests
        testUpdateStateIdle();
        testUpdateStateExecuting();

        // Utilization tracking tests
        testRecordUtilization();
        testVmOpenSecondsTracking();
        testVmWorkloadSecondsTracking();
        testCalculateVmTotalPowerDraw();

        // Getters and setters tests
        testGettersAndSetters();
        testToString();

        // Summary
        System.out.println("\n=== Test Summary ===");
        System.out.println("Tests Passed: " + testsPassed);
        System.out.println("Tests Failed: " + testsFailed);
        System.out.println("Total Tests: " + (testsPassed + testsFailed));

        if (testsFailed == 0) {
            System.out.println("\nAll Host tests PASSED!");
        } else {
            System.out.println("\nSome tests FAILED. Please review the output above.");
            System.exit(1);
        }
    }

    // ==================== Constructor Tests ====================

    private static void testDefaultConstructor() {
        System.out.println("Test: Default Constructor");
        try {
            Host host = new Host();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (host.getInstructionsPerSecond() != 2_500_000_000L) {
                passed = false;
                errors.append("    - Default IPS should be 2,500,000,000\n");
            }
            if (host.getNumberOfCpuCores() != 16) {
                passed = false;
                errors.append("    - Default CPU cores should be 16\n");
            }
            if (host.getComputeType() != ComputeType.CPU_ONLY) {
                passed = false;
                errors.append("    - Default compute type should be CPU_ONLY\n");
            }
            if (host.getNumberOfGpus() != 0) {
                passed = false;
                errors.append("    - Default GPUs should be 0\n");
            }
            if (host.getRamCapacityMB() != 2L * 1024 * 1024) {
                passed = false;
                errors.append("    - Default RAM should be 2TB (2,097,152 MB)\n");
            }
            if (host.getNetworkCapacityMbps() != 2L * 1000 * 1000) {
                passed = false;
                errors.append("    - Default network should be 2Tbps (2,000,000 Mbps)\n");
            }
            if (host.getHardDriveCapacityMB() != 20L * 1024 * 1024) {
                passed = false;
                errors.append("    - Default storage should be 20TB (20,971,520 MB)\n");
            }
            if (host.getActiveSeconds() != 0) {
                passed = false;
                errors.append("    - Initial active seconds should be 0\n");
            }
            if (host.getAssignedVMs().size() != 0) {
                passed = false;
                errors.append("    - Initial VMs list should be empty\n");
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
            Host host = new Host(3_000_000_000L, 32, ComputeType.CPU_GPU_MIXED, 4);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (host.getInstructionsPerSecond() != 3_000_000_000L) {
                passed = false;
                errors.append("    - IPS mismatch\n");
            }
            if (host.getNumberOfCpuCores() != 32) {
                passed = false;
                errors.append("    - CPU cores mismatch\n");
            }
            if (host.getComputeType() != ComputeType.CPU_GPU_MIXED) {
                passed = false;
                errors.append("    - Compute type mismatch\n");
            }
            if (host.getNumberOfGpus() != 4) {
                passed = false;
                errors.append("    - GPU count mismatch\n");
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

    // ==================== Capacity Tests ====================

    private static void testHasCapacityFor() {
        System.out.println("Test: hasCapacityFor()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // Test with available capacity
            if (!host.hasCapacityFor(8192, 4, 0)) {
                passed = false;
                errors.append("    - Should have capacity for 4 CPUs, 8192 MB RAM\n");
            }

            // Allocate some resources
            host.allocateResources(new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY));

            // Test with reduced capacity
            if (!host.hasCapacityFor(8192, 12, 0)) {
                passed = false;
                errors.append("    - Should have capacity for 12 more CPUs\n");
            }

            // Test exceeding capacity
            if (host.hasCapacityFor(8192, 13, 0)) {
                passed = false;
                errors.append("    - Should NOT have capacity for 13 more CPUs (only 12 available)\n");
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

    private static void testHasCapacityForVM() {
        System.out.println("Test: hasCapacityForVM()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_GPU_MIXED, 4);

            VM vm1 = new VM("user1", 2_000_000_000L, 4, 1, 8192, 102400, 1000, ComputeType.GPU_ONLY);
            VM vm2 = new VM("user2", 2_000_000_000L, 8, 2, 16384, 204800, 2000, ComputeType.GPU_ONLY);
            VM vm3 = new VM("user3", 2_000_000_000L, 16, 5, 32768, 409600, 4000, ComputeType.GPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // Test VM that fits
            if (!host.hasCapacityForVM(vm1)) {
                passed = false;
                errors.append("    - Should have capacity for vm1\n");
            }

            // Assign vm1
            host.assignVM(vm1);

            // Test VM that still fits
            if (!host.hasCapacityForVM(vm2)) {
                passed = false;
                errors.append("    - Should have capacity for vm2\n");
            }

            // Test VM that doesn't fit (too many GPUs)
            if (host.hasCapacityForVM(vm3)) {
                passed = false;
                errors.append("    - Should NOT have capacity for vm3 (needs 5 GPUs, only 3 available)\n");
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

    private static void testResourceAvailability() {
        System.out.println("Test: Resource Availability Methods");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_GPU_MIXED, 4);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // Initial availability
            if (host.getAvailableRamMB() != 2L * 1024 * 1024) {
                passed = false;
                errors.append("    - Initial available RAM mismatch\n");
            }
            if (host.getAvailableCpuCores() != 16) {
                passed = false;
                errors.append("    - Initial available CPU cores mismatch\n");
            }
            if (host.getAvailableGpus() != 4) {
                passed = false;
                errors.append("    - Initial available GPUs mismatch\n");
            }

            // Allocate some resources
            VM vm = new VM("user1", 2_000_000_000L, 4, 2, 8192, 102400, 1000, ComputeType.CPU_GPU_MIXED);
            host.allocateResources(vm);

            // Check availability after allocation
            if (host.getAvailableRamMB() != (2L * 1024 * 1024 - 8192)) {
                passed = false;
                errors.append("    - Available RAM after allocation mismatch\n");
            }
            if (host.getAvailableCpuCores() != 12) {
                passed = false;
                errors.append("    - Available CPU cores should be 12\n");
            }
            if (host.getAvailableGpus() != 2) {
                passed = false;
                errors.append("    - Available GPUs should be 2\n");
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

    // ==================== VM Assignment Tests ====================

    private static void testAssignVM() {
        System.out.println("Test: assignVM()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_GPU_MIXED, 4);
            VM vm = new VM("user1", 2_000_000_000L, 4, 1, 8192, 102400, 1000, ComputeType.GPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.assignVM(vm);

            if (host.getAssignedVMs().size() != 1) {
                passed = false;
                errors.append("    - Assigned VMs count should be 1\n");
            }
            if (!host.getAssignedVMs().contains(vm)) {
                passed = false;
                errors.append("    - VM should be in assigned list\n");
            }
            if (vm.getAssignedHostId() == null || vm.getAssignedHostId() != host.getId()) {
                passed = false;
                errors.append("    - VM's assigned host ID should be set\n");
            }
            if (host.getVmOpenSeconds(vm.getId()) != 0L) {
                passed = false;
                errors.append("    - VM open seconds should be initialized to 0\n");
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

    private static void testAssignVMExceedsCapacity() {
        System.out.println("Test: assignVM() - Exceeds Capacity");
        try {
            Host host = new Host(2_500_000_000L, 4, ComputeType.CPU_ONLY, 0);
            VM vm = new VM("user1", 2_000_000_000L, 8, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);

            boolean passed = false;
            try {
                host.assignVM(vm);
            } catch (IllegalStateException e) {
                passed = true;
            }

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Should throw IllegalStateException when VM exceeds capacity\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Unexpected exception - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testRemoveVM() {
        System.out.println("Test: removeVM()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_GPU_MIXED, 4);
            VM vm = new VM("user1", 2_000_000_000L, 4, 1, 8192, 102400, 1000, ComputeType.GPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.assignVM(vm);
            host.removeVM(vm);

            if (host.getAssignedVMs().size() != 0) {
                passed = false;
                errors.append("    - Assigned VMs count should be 0 after removal\n");
            }
            if (host.getAssignedVMs().contains(vm)) {
                passed = false;
                errors.append("    - VM should not be in assigned list\n");
            }
            if (vm.getAssignedHostId() != null) {
                passed = false;
                errors.append("    - VM's assigned host ID should be null after removal\n");
            }
            // Resources should be deallocated
            if (host.getAvailableCpuCores() != 16) {
                passed = false;
                errors.append("    - CPU cores should be fully available after removal\n");
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

    private static void testMultipleVMAssignments() {
        System.out.println("Test: Multiple VM Assignments");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_GPU_MIXED, 4);
            VM vm1 = new VM("user1", 2_000_000_000L, 4, 1, 8192, 102400, 1000, ComputeType.GPU_ONLY);
            VM vm2 = new VM("user2", 2_000_000_000L, 4, 1, 8192, 102400, 1000, ComputeType.GPU_ONLY);
            VM vm3 = new VM("user3", 2_000_000_000L, 4, 1, 8192, 102400, 1000, ComputeType.GPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.assignVM(vm1);
            host.assignVM(vm2);
            host.assignVM(vm3);

            if (host.getAssignedVMs().size() != 3) {
                passed = false;
                errors.append("    - Should have 3 assigned VMs\n");
            }
            if (host.getAvailableCpuCores() != 4) {
                passed = false;
                errors.append("    - Should have 4 available CPU cores\n");
            }
            if (host.getAvailableGpus() != 1) {
                passed = false;
                errors.append("    - Should have 1 available GPU\n");
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

    // ==================== Resource Allocation Tests ====================

    private static void testAllocateResources() {
        System.out.println("Test: allocateResources()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_GPU_MIXED, 4);
            VM vm = new VM("user1", 2_000_000_000L, 4, 2, 8192, 102400, 1000, ComputeType.CPU_GPU_MIXED);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.allocateResources(vm);

            if (host.getAvailableCpuCores() != 12) {
                passed = false;
                errors.append("    - Available CPU cores should be 12\n");
            }
            if (host.getAvailableGpus() != 2) {
                passed = false;
                errors.append("    - Available GPUs should be 2\n");
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

    private static void testDeallocateResources() {
        System.out.println("Test: deallocateResources()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_GPU_MIXED, 4);
            VM vm = new VM("user1", 2_000_000_000L, 4, 2, 8192, 102400, 1000, ComputeType.CPU_GPU_MIXED);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.allocateResources(vm);
            host.deallocateResources(vm);

            if (host.getAvailableCpuCores() != 16) {
                passed = false;
                errors.append("    - Available CPU cores should be 16 after deallocation\n");
            }
            if (host.getAvailableGpus() != 4) {
                passed = false;
                errors.append("    - Available GPUs should be 4 after deallocation\n");
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

    private static void testResourceUtilization() {
        System.out.println("Test: getResourceUtilization()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);
            host.setRamCapacityMB(16384); // 16 GB for easier calculation

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // No allocation - 0% utilization
            double util1 = host.getResourceUtilization();
            if (Math.abs(util1 - 0.0) > 0.001) {
                passed = false;
                errors.append("    - Initial utilization should be 0.0, got " + util1 + "\n");
            }

            // Allocate 8 cores and 8GB RAM - 50% utilization
            VM vm = new VM("user1", 2_000_000_000L, 8, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            host.allocateResources(vm);

            double util2 = host.getResourceUtilization();
            // (8/16 + 8192/16384) / 2 = (0.5 + 0.5) / 2 = 0.5
            if (Math.abs(util2 - 0.5) > 0.001) {
                passed = false;
                errors.append("    - Utilization should be 0.5, got " + util2 + "\n");
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

    // ==================== Power and Energy Tests ====================

    private static void testUpdatePowerConsumption() {
        System.out.println("Test: updatePowerConsumption()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // Initial power should be 0
            if (host.getCurrentTotalPowerDraw() != 0.0) {
                passed = false;
                errors.append("    - Initial power draw should be 0.0\n");
            }

            // Update power consumption (should calculate based on VMs)
            host.updatePowerConsumption();

            // Power should still be low (no VMs running)
            if (host.getCurrentTotalPowerDraw() < 0.0) {
                passed = false;
                errors.append("    - Power draw should not be negative\n");
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

    private static void testUpdateEnergyConsumption() {
        System.out.println("Test: updateEnergyConsumption()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.setCurrentTotalPowerDraw(100.0); // 100 Watts
            double initialEnergy = host.getTotalEnergyConsumed();

            host.updateEnergyConsumption(); // 1 second at 100W

            double finalEnergy = host.getTotalEnergyConsumed();
            double energyDiff = finalEnergy - initialEnergy;

            // Energy increase should equal power draw (100 Joules for 100W * 1s)
            if (Math.abs(energyDiff - 100.0) > 0.001) {
                passed = false;
                errors.append("    - Energy increase should be 100 Joules, got " + energyDiff + "\n");
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

    private static void testGetTotalEnergyConsumed() {
        System.out.println("Test: getTotalEnergyConsumed()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.setCurrentTotalPowerDraw(200.0);
            host.updateEnergyConsumption();
            host.updateEnergyConsumption();
            host.updateEnergyConsumption();

            // 3 seconds at 200W = 600 Joules
            if (Math.abs(host.getTotalEnergyConsumed() - 600.0) > 0.001) {
                passed = false;
                errors.append("    - Total energy should be 600 Joules, got " + host.getTotalEnergyConsumed() + "\n");
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

    private static void testGetTotalEnergyConsumedKWh() {
        System.out.println("Test: getTotalEnergyConsumedKWh()");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.setCurrentTotalPowerDraw(1000.0); // 1 kW
            // Simulate 1 hour (3600 seconds)
            for (int i = 0; i < 3600; i++) {
                host.updateEnergyConsumption();
            }

            // 1 kW * 1 hour = 1 kWh
            // 3,600,000 Joules / 3,600,000 = 1 kWh
            double kWh = host.getTotalEnergyConsumedKWh();
            if (Math.abs(kWh - 1.0) > 0.001) {
                passed = false;
                errors.append("    - Energy should be 1 kWh, got " + kWh + "\n");
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

    // ==================== Activation Tests ====================

    private static void testActivate() {
        System.out.println("Test: activate()");
        try {
            Host host = new Host();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (host.getAssignedDatacenterId() != null) {
                passed = false;
                errors.append("    - Initial datacenter ID should be null\n");
            }

            host.activate(1000L, 5L);

            if (host.getAssignedDatacenterId() == null || host.getAssignedDatacenterId() != 5L) {
                passed = false;
                errors.append("    - Datacenter ID should be 5\n");
            }
            if (host.getActiveSeconds() != 0) {
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

    private static void testActivateAlreadyActive() {
        System.out.println("Test: activate() - Already Active");
        try {
            Host host = new Host();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.activate(1000L, 5L);
            host.setActiveSeconds(100L);
            host.activate(2000L, 10L); // Try to activate again

            // Should not change datacenter ID or reset active seconds
            if (host.getAssignedDatacenterId() != 5L) {
                passed = false;
                errors.append("    - Datacenter ID should remain 5\n");
            }
            if (host.getActiveSeconds() != 100L) {
                passed = false;
                errors.append("    - Active seconds should remain 100\n");
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

    // ==================== State Update Tests ====================

    private static void testUpdateStateIdle() {
        System.out.println("Test: updateState() - Idle");
        try {
            Host host = new Host();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.updateState();

            if (host.getActiveSeconds() != 1) {
                passed = false;
                errors.append("    - Active seconds should be 1\n");
            }
            if (host.getSecondsIDLE() != 1) {
                passed = false;
                errors.append("    - Idle seconds should be 1\n");
            }
            if (host.getSecondsExecuting() != 0) {
                passed = false;
                errors.append("    - Executing seconds should be 0\n");
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

    private static void testUpdateStateExecuting() {
        System.out.println("Test: updateState() - Executing");
        try {
            Host host = new Host(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
            vm.setVmState(VmState.RUNNING);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.assignVM(vm);
            host.updateState();

            if (host.getActiveSeconds() != 1) {
                passed = false;
                errors.append("    - Active seconds should be 1\n");
            }
            if (host.getSecondsIDLE() != 0) {
                passed = false;
                errors.append("    - Idle seconds should be 0\n");
            }
            if (host.getSecondsExecuting() != 1) {
                passed = false;
                errors.append("    - Executing seconds should be 1\n");
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

    // ==================== Utilization Tracking Tests ====================

    private static void testRecordUtilization() {
        System.out.println("Test: recordUtilization()");
        try {
            Host host = new Host();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.recordUtilization(1000L, 1L, WorkloadType.SEVEN_ZIP, 0.8, 0.0);
            host.recordUtilization(1001L, 1L, WorkloadType.SEVEN_ZIP, 0.9, 0.0);

            if (host.getUtilizationHistory().size() != 2) {
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

    private static void testVmOpenSecondsTracking() {
        System.out.println("Test: VM Open Seconds Tracking");
        try {
            Host host = new Host();
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.assignVM(vm);

            // Record utilization 3 times
            host.recordUtilization(1000L, vm.getId(), WorkloadType.SEVEN_ZIP, 0.8, 0.0);
            host.recordUtilization(1001L, vm.getId(), WorkloadType.SEVEN_ZIP, 0.9, 0.0);
            host.recordUtilization(1002L, vm.getId(), WorkloadType.SEVEN_ZIP, 0.7, 0.0);

            long openSeconds = host.getVmOpenSeconds(vm.getId());
            if (openSeconds != 3) {
                passed = false;
                errors.append("    - VM open seconds should be 3, got " + openSeconds + "\n");
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

    private static void testVmWorkloadSecondsTracking() {
        System.out.println("Test: VM Workload Seconds Tracking");
        try {
            Host host = new Host();
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.assignVM(vm);

            host.recordUtilization(1000L, vm.getId(), WorkloadType.SEVEN_ZIP, 0.8, 0.0);
            host.recordUtilization(1001L, vm.getId(), WorkloadType.SEVEN_ZIP, 0.9, 0.0);
            host.recordUtilization(1002L, vm.getId(), WorkloadType.DATABASE, 0.5, 0.0);

            long sevenZipSeconds = host.getVmWorkloadSeconds(vm.getId(), WorkloadType.SEVEN_ZIP);
            long dbSeconds = host.getVmWorkloadSeconds(vm.getId(), WorkloadType.DATABASE);

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

    private static void testCalculateVmTotalPowerDraw() {
        System.out.println("Test: calculateVmTotalPowerDraw()");
        try {
            Host host = new Host();
            VM vm = new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            host.assignVM(vm);

            // Power model will calculate power based on utilization
            host.recordUtilization(1000L, vm.getId(), WorkloadType.SEVEN_ZIP, 1.0, 0.0);
            host.recordUtilization(1001L, vm.getId(), WorkloadType.SEVEN_ZIP, 1.0, 0.0);

            double totalPower = host.calculateVmTotalPowerDraw(vm.getId());

            if (totalPower <= 0) {
                passed = false;
                errors.append("    - Total power draw should be positive, got " + totalPower + "\n");
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
            Host host = new Host();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // Test setters
            host.setInstructionsPerSecond(3_000_000_000L);
            host.setNumberOfCpuCores(32);
            host.setComputeType(ComputeType.GPU_ONLY);
            host.setNumberOfGpus(8);
            host.setRamCapacityMB(4L * 1024 * 1024);
            host.setNetworkCapacityMbps(10L * 1000 * 1000);
            host.setHardDriveCapacityMB(50L * 1024 * 1024);
            host.setActiveSeconds(500L);
            host.setSecondsIDLE(200L);
            host.setSecondsExecuting(300L);

            // Test getters
            if (host.getInstructionsPerSecond() != 3_000_000_000L) {
                passed = false;
                errors.append("    - IPS getter/setter mismatch\n");
            }
            if (host.getNumberOfCpuCores() != 32) {
                passed = false;
                errors.append("    - CPU cores getter/setter mismatch\n");
            }
            if (host.getComputeType() != ComputeType.GPU_ONLY) {
                passed = false;
                errors.append("    - Compute type getter/setter mismatch\n");
            }
            if (host.getNumberOfGpus() != 8) {
                passed = false;
                errors.append("    - GPU count getter/setter mismatch\n");
            }
            if (host.getActiveSeconds() != 500L) {
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
            Host host = new Host();
            String str = host.toString();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!str.contains("Host{")) {
                passed = false;
                errors.append("    - toString should start with 'Host{'\n");
            }
            if (!str.contains("id=")) {
                passed = false;
                errors.append("    - toString should contain 'id='\n");
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
