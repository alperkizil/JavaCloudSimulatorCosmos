package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.PowerModel;

import java.util.List;

/**
 * Unit tests for the CloudDatacenter class.
 * Tests all major functionality including:
 * - Constructor variants
 * - Host management (add, remove, capacity checks)
 * - Customer management
 * - Power management and limits
 * - Energy tracking and calculations
 * - Datacenter activation
 * - Host filtering by compute type and capacity
 */
public class CloudDatacenterTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=== CloudDatacenter Unit Tests ===\n");

        // Constructor tests
        testDefaultConstructor();
        testConstructorWithCapacity();
        testConstructorWithAllParams();

        // Host management tests
        testAddHost();
        testAddHostExceedsCapacity();
        testRemoveHost();
        testCanAcceptHost();
        testCanAccommodateHostPowerLimit();

        // Customer management tests
        testAddCustomer();
        testAddDuplicateCustomer();
        testRemoveCustomer();

        // Power management tests
        testIsPowerLimitReached();
        testUpdateTotalMomentaryPowerDraw();

        // Energy tracking tests
        testGetTotalEnergyConsumed();
        testGetTotalEnergyConsumedKWh();

        // Activation tests
        testActivate();
        testIncrementActiveSeconds();

        // Host filtering tests
        testGetAvailableHostsByComputeType();
        testGetAvailableHostsMixedType();
        testGetHostsWithCapacity();

        // Miscellaneous tests
        testGetAveragePowerDraw();
        testToString();

        // Summary
        System.out.println("\n=== Test Summary ===");
        System.out.println("Tests Passed: " + testsPassed);
        System.out.println("Tests Failed: " + testsFailed);
        System.out.println("Total Tests: " + (testsPassed + testsFailed));

        if (testsFailed == 0) {
            System.out.println("\nAll CloudDatacenter tests PASSED!");
        } else {
            System.out.println("\nSome tests FAILED. Please review the output above.");
            System.exit(1);
        }
    }

    // ==================== Constructor Tests ====================

    private static void testDefaultConstructor() {
        System.out.println("Test: Default Constructor (name only)");
        try {
            CloudDatacenter dc = new CloudDatacenter("TestDC");

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!"TestDC".equals(dc.getName())) {
                passed = false;
                errors.append("    - Name mismatch: expected 'TestDC', got '" + dc.getName() + "'\n");
            }
            if (dc.getMaxHostCapacity() != 50) {
                passed = false;
                errors.append("    - Default capacity should be 50, got " + dc.getMaxHostCapacity() + "\n");
            }
            if (dc.getTotalMaxPowerDraw() != 0.0) {
                passed = false;
                errors.append("    - Default max power should be 0.0, got " + dc.getTotalMaxPowerDraw() + "\n");
            }
            if (dc.getActiveSeconds() != 0) {
                passed = false;
                errors.append("    - Initial active seconds should be 0, got " + dc.getActiveSeconds() + "\n");
            }
            if (!dc.getHosts().isEmpty()) {
                passed = false;
                errors.append("    - Initial hosts list should be empty\n");
            }
            if (!dc.getCustomers().isEmpty()) {
                passed = false;
                errors.append("    - Initial customers list should be empty\n");
            }
            if (dc.isActive()) {
                passed = false;
                errors.append("    - Datacenter should not be active initially\n");
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

    private static void testConstructorWithCapacity() {
        System.out.println("Test: Constructor with custom capacity");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Custom", 100);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!"DC-Custom".equals(dc.getName())) {
                passed = false;
                errors.append("    - Name mismatch\n");
            }
            if (dc.getMaxHostCapacity() != 100) {
                passed = false;
                errors.append("    - Capacity should be 100, got " + dc.getMaxHostCapacity() + "\n");
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

    private static void testConstructorWithAllParams() {
        System.out.println("Test: Constructor with all parameters");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Full", 75, 50000.0);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (!"DC-Full".equals(dc.getName())) {
                passed = false;
                errors.append("    - Name mismatch\n");
            }
            if (dc.getMaxHostCapacity() != 75) {
                passed = false;
                errors.append("    - Capacity should be 75, got " + dc.getMaxHostCapacity() + "\n");
            }
            if (dc.getTotalMaxPowerDraw() != 50000.0) {
                passed = false;
                errors.append("    - Max power should be 50000.0, got " + dc.getTotalMaxPowerDraw() + "\n");
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

    // ==================== Host Management Tests ====================

    private static void testAddHost() {
        System.out.println("Test: Add host to datacenter");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);
            Host host = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);

            dc.addHost(host);

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            if (dc.getHosts().size() != 1) {
                passed = false;
                errors.append("    - Hosts count should be 1, got " + dc.getHosts().size() + "\n");
            }
            if (host.getAssignedDatacenterId() == null || host.getAssignedDatacenterId() != dc.getId()) {
                passed = false;
                errors.append("    - Host should be assigned to datacenter ID " + dc.getId() + "\n");
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

    private static void testAddHostExceedsCapacity() {
        System.out.println("Test: Add host when capacity exceeded");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Small", 2, 100000.0);
            Host host1 = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            Host host2 = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            Host host3 = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);

            dc.addHost(host1);
            dc.addHost(host2);

            boolean exceptionThrown = false;
            try {
                dc.addHost(host3);
            } catch (IllegalStateException e) {
                exceptionThrown = true;
                System.out.println("    Expected exception caught: " + e.getMessage());
            }

            if (exceptionThrown && dc.getHosts().size() == 2) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Expected IllegalStateException when exceeding capacity\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Unexpected exception - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testRemoveHost() {
        System.out.println("Test: Remove host from datacenter");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);
            Host host = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);

            dc.addHost(host);
            dc.removeHost(host);

            boolean passed = dc.getHosts().isEmpty();

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Host was not removed\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testCanAcceptHost() {
        System.out.println("Test: canAcceptHost() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 2, 100000.0);
            Host host1 = new Host();
            Host host2 = new Host();

            boolean passed = true;
            StringBuilder errors = new StringBuilder();

            // Initially should accept hosts
            if (!dc.canAcceptHost()) {
                passed = false;
                errors.append("    - Should accept hosts when empty\n");
            }

            dc.addHost(host1);
            if (!dc.canAcceptHost()) {
                passed = false;
                errors.append("    - Should accept hosts when 1/2 capacity\n");
            }

            dc.addHost(host2);
            if (dc.canAcceptHost()) {
                passed = false;
                errors.append("    - Should NOT accept hosts when at max capacity\n");
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

    private static void testCanAccommodateHostPowerLimit() {
        System.out.println("Test: canAccommodateHost() with power limit");
        try {
            // Datacenter with low power limit (500W allows one 200W host but not two)
            CloudDatacenter dc = new CloudDatacenter("DC-LowPower", 10, 350.0);

            // Create a host with a power model that has ~200W idle power
            Host host = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            PowerModel highPowerModel = new PowerModel("HighPower", 500.0, 300.0, 100.0, 50.0, 50.0);
            host.setPowerModel(highPowerModel);

            // Host idle power = idleCpu(100) + idleGpu(50) + other(50) = 200W
            double hostIdlePower = highPowerModel.calculateTotalPower(0.0, 0.0);
            System.out.println("    Host idle power from model: " + hostIdlePower + "W");
            System.out.println("    Datacenter power limit: " + dc.getTotalMaxPowerDraw() + "W");

            // First host should fit (200W <= 350W)
            boolean canAccommodateFirst = dc.canAccommodateHost(host);
            System.out.println("    Can accommodate first host: " + canAccommodateFirst);

            if (canAccommodateFirst) {
                dc.addHost(host);
                // Important: Set the host's current power draw to simulate it running at idle
                host.setCurrentTotalPowerDraw(hostIdlePower);

                // Update datacenter's power tracking
                dc.updateTotalMomentaryPowerDraw();
                System.out.println("    Datacenter current power after first host: " + dc.getTotalMomentaryPowerDraw() + "W");

                // Now try to add another host - should not fit (200 + 200 = 400W > 350W)
                Host host2 = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
                host2.setPowerModel(highPowerModel);

                boolean canAccommodateSecond = dc.canAccommodateHost(host2);
                System.out.println("    Can accommodate second host: " + canAccommodateSecond);

                if (!canAccommodateSecond) {
                    System.out.println("  PASSED\n");
                    testsPassed++;
                } else {
                    System.out.println("  FAILED: Should not accommodate second high-power host\n");
                    testsFailed++;
                }
            } else {
                System.out.println("  FAILED: First host should have been accommodated\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Customer Management Tests ====================

    private static void testAddCustomer() {
        System.out.println("Test: Add customer to datacenter");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test");

            dc.addCustomer("Customer1");
            dc.addCustomer("Customer2");

            boolean passed = dc.getCustomers().size() == 2 &&
                           dc.getCustomers().contains("Customer1") &&
                           dc.getCustomers().contains("Customer2");

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Customers not added correctly\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testAddDuplicateCustomer() {
        System.out.println("Test: Add duplicate customer (should be ignored)");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test");

            dc.addCustomer("Customer1");
            dc.addCustomer("Customer1"); // Duplicate

            boolean passed = dc.getCustomers().size() == 1;

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Duplicate customer was added, count = " + dc.getCustomers().size() + "\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testRemoveCustomer() {
        System.out.println("Test: Remove customer from datacenter");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test");

            dc.addCustomer("Customer1");
            dc.addCustomer("Customer2");
            dc.removeCustomer("Customer1");

            boolean passed = dc.getCustomers().size() == 1 &&
                           !dc.getCustomers().contains("Customer1") &&
                           dc.getCustomers().contains("Customer2");

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Customer not removed correctly\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Power Management Tests ====================

    private static void testIsPowerLimitReached() {
        System.out.println("Test: isPowerLimitReached() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 1000.0);

            // Initially, power limit should not be reached (no hosts)
            boolean reachedInitially = dc.isPowerLimitReached();

            // Add host with power draw
            Host host = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            PowerModel powerModel = new PowerModel("Test", 300.0, 200.0, 50.0, 30.0, 100.0);
            host.setPowerModel(powerModel);
            host.setCurrentTotalPowerDraw(500.0);
            dc.addHost(host);

            // Check power limit after adding host with 500W draw
            boolean reachedAfterHost = dc.isPowerLimitReached();

            // Add another host to exceed limit
            Host host2 = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            host2.setPowerModel(powerModel);
            host2.setCurrentTotalPowerDraw(600.0);
            dc.addHost(host2);

            // Now total is 1100W, should exceed 1000W limit
            boolean reachedAfterExceed = dc.isPowerLimitReached();

            boolean passed = !reachedInitially && !reachedAfterHost && reachedAfterExceed;

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Power limit detection incorrect\n");
                System.out.println("    Initially (expected false): " + reachedInitially);
                System.out.println("    After 500W host (expected false): " + reachedAfterHost);
                System.out.println("    After 1100W total (expected true): " + reachedAfterExceed);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testUpdateTotalMomentaryPowerDraw() {
        System.out.println("Test: updateTotalMomentaryPowerDraw() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 10000.0);

            Host host1 = new Host();
            host1.setCurrentTotalPowerDraw(250.0);
            dc.addHost(host1);

            Host host2 = new Host();
            host2.setCurrentTotalPowerDraw(350.0);
            dc.addHost(host2);

            dc.updateTotalMomentaryPowerDraw();

            double expectedPower = 600.0;
            boolean passed = Math.abs(dc.getTotalMomentaryPowerDraw() - expectedPower) < 0.001;

            if (passed) {
                System.out.println("  PASSED (Total power: " + dc.getTotalMomentaryPowerDraw() + "W)\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Expected " + expectedPower + "W, got " + dc.getTotalMomentaryPowerDraw() + "W\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Energy Tracking Tests ====================

    private static void testGetTotalEnergyConsumed() {
        System.out.println("Test: getTotalEnergyConsumed() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);

            // Create hosts and simulate energy consumption
            Host host1 = new Host();
            Host host2 = new Host();

            dc.addHost(host1);
            dc.addHost(host2);

            // Simulate some time steps to accumulate energy
            for (int i = 0; i < 100; i++) {
                host1.setCurrentTotalPowerDraw(200.0);
                host2.setCurrentTotalPowerDraw(300.0);
                host1.updateEnergyConsumption();
                host2.updateEnergyConsumption();
            }

            // Expected: 200W * 100s = 20000J for host1, 300W * 100s = 30000J for host2
            double expectedEnergy = 50000.0;
            double actualEnergy = dc.getTotalEnergyConsumed();

            boolean passed = Math.abs(actualEnergy - expectedEnergy) < 0.001;

            if (passed) {
                System.out.println("  PASSED (Total energy: " + actualEnergy + " Joules)\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Expected " + expectedEnergy + "J, got " + actualEnergy + "J\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testGetTotalEnergyConsumedKWh() {
        System.out.println("Test: getTotalEnergyConsumedKWh() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);

            Host host = new Host();
            dc.addHost(host);

            // Simulate 3600 seconds (1 hour) at 1000W = 1 kWh
            for (int i = 0; i < 3600; i++) {
                host.setCurrentTotalPowerDraw(1000.0);
                host.updateEnergyConsumption();
            }

            double expectedKWh = 1.0;
            double actualKWh = dc.getTotalEnergyConsumedKWh();

            boolean passed = Math.abs(actualKWh - expectedKWh) < 0.001;

            if (passed) {
                System.out.println("  PASSED (Total energy: " + actualKWh + " kWh)\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Expected " + expectedKWh + " kWh, got " + actualKWh + " kWh\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Activation Tests ====================

    private static void testActivate() {
        System.out.println("Test: activate() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test");

            // Initially not active
            boolean wasActive = dc.isActive();

            // Activate
            dc.activate(1000L);
            boolean isActiveAfter = dc.isActive();
            long activeSecondsAfter = dc.getActiveSeconds();

            // Activating again should not reset
            dc.setActiveSeconds(50);
            dc.activate(2000L);
            long activeSecondsAfterSecondActivation = dc.getActiveSeconds();

            boolean passed = !wasActive && isActiveAfter &&
                           activeSecondsAfter == 0 &&
                           activeSecondsAfterSecondActivation == 50;

            if (passed) {
                System.out.println("  PASSED\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Activation state incorrect\n");
                System.out.println("    Initially active: " + wasActive);
                System.out.println("    Active after activation: " + isActiveAfter);
                System.out.println("    Active seconds reset to 0: " + (activeSecondsAfter == 0));
                System.out.println("    Active seconds preserved on re-activate: " + (activeSecondsAfterSecondActivation == 50));
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testIncrementActiveSeconds() {
        System.out.println("Test: incrementActiveSeconds() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test");

            dc.setActiveSeconds(0);
            dc.incrementActiveSeconds();
            dc.incrementActiveSeconds();
            dc.incrementActiveSeconds();

            boolean passed = dc.getActiveSeconds() == 3;

            if (passed) {
                System.out.println("  PASSED (Active seconds: " + dc.getActiveSeconds() + ")\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Expected 3, got " + dc.getActiveSeconds() + "\n");
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Host Filtering Tests ====================

    private static void testGetAvailableHostsByComputeType() {
        System.out.println("Test: getAvailableHosts() by compute type");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);

            // Add hosts of different types
            Host cpuHost1 = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            Host cpuHost2 = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            Host gpuHost = new Host(2500000000L, 16, ComputeType.GPU_ONLY, 4);

            dc.addHost(cpuHost1);
            dc.addHost(cpuHost2);
            dc.addHost(gpuHost);

            List<Host> cpuHosts = dc.getAvailableHosts(ComputeType.CPU_ONLY);
            List<Host> gpuHosts = dc.getAvailableHosts(ComputeType.GPU_ONLY);

            boolean passed = cpuHosts.size() == 2 && gpuHosts.size() == 1;

            if (passed) {
                System.out.println("  PASSED (CPU hosts: " + cpuHosts.size() + ", GPU hosts: " + gpuHosts.size() + ")\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Incorrect host filtering\n");
                System.out.println("    Expected CPU hosts: 2, got: " + cpuHosts.size());
                System.out.println("    Expected GPU hosts: 1, got: " + gpuHosts.size());
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testGetAvailableHostsMixedType() {
        System.out.println("Test: getAvailableHosts() includes MIXED type");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);

            Host cpuHost = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            Host mixedHost = new Host(2500000000L, 32, ComputeType.CPU_GPU_MIXED, 4);

            dc.addHost(cpuHost);
            dc.addHost(mixedHost);

            // CPU_ONLY query should return CPU_ONLY + MIXED
            List<Host> cpuHosts = dc.getAvailableHosts(ComputeType.CPU_ONLY);
            // GPU_ONLY query should return GPU_ONLY + MIXED
            List<Host> gpuHosts = dc.getAvailableHosts(ComputeType.GPU_ONLY);

            boolean passed = cpuHosts.size() == 2 && gpuHosts.size() == 1;

            if (passed) {
                System.out.println("  PASSED (CPU compatible: " + cpuHosts.size() + ", GPU compatible: " + gpuHosts.size() + ")\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: MIXED type not included correctly\n");
                System.out.println("    CPU compatible (expected 2): " + cpuHosts.size());
                System.out.println("    GPU compatible (expected 1): " + gpuHosts.size());
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testGetHostsWithCapacity() {
        System.out.println("Test: getHostsWithCapacity() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);

            // Create hosts with different capacities
            Host smallHost = new Host(2500000000L, 8, ComputeType.CPU_ONLY, 0);
            smallHost.setRamCapacityMB(8192); // 8GB RAM

            Host largeHost = new Host(3000000000L, 32, ComputeType.CPU_GPU_MIXED, 8);
            largeHost.setRamCapacityMB(65536); // 64GB RAM

            dc.addHost(smallHost);
            dc.addHost(largeHost);

            // Query for hosts with 16GB RAM, 16 cores, 0 GPUs
            List<Host> hostsForSmallVM = dc.getHostsWithCapacity(16384, 16, 0);
            // Query for hosts with 32GB RAM, 24 cores, 4 GPUs
            List<Host> hostsForLargeVM = dc.getHostsWithCapacity(32768, 24, 4);

            boolean passed = hostsForSmallVM.size() == 1 &&
                           hostsForSmallVM.contains(largeHost) &&
                           hostsForLargeVM.size() == 1 &&
                           hostsForLargeVM.contains(largeHost);

            if (passed) {
                System.out.println("  PASSED (Small VM hosts: " + hostsForSmallVM.size() + ", Large VM hosts: " + hostsForLargeVM.size() + ")\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Capacity filtering incorrect\n");
                System.out.println("    Hosts for small VM (expected 1): " + hostsForSmallVM.size());
                System.out.println("    Hosts for large VM (expected 1): " + hostsForLargeVM.size());
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    // ==================== Miscellaneous Tests ====================

    private static void testGetAveragePowerDraw() {
        System.out.println("Test: getAveragePowerDraw() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);

            // With no hosts and no active seconds, should return 0
            double avgPowerEmpty = dc.getAveragePowerDraw();

            // Add hosts and set active
            Host host1 = new Host();
            host1.setCurrentTotalPowerDraw(200.0);
            Host host2 = new Host();
            host2.setCurrentTotalPowerDraw(400.0);

            dc.addHost(host1);
            dc.addHost(host2);
            dc.setActiveSeconds(100);

            double avgPowerWithHosts = dc.getAveragePowerDraw();
            double expectedAvg = 300.0; // (200 + 400) / 2

            boolean passed = avgPowerEmpty == 0.0 &&
                           Math.abs(avgPowerWithHosts - expectedAvg) < 0.001;

            if (passed) {
                System.out.println("  PASSED (Average power: " + avgPowerWithHosts + "W)\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: Average power calculation incorrect\n");
                System.out.println("    Empty DC (expected 0): " + avgPowerEmpty);
                System.out.println("    With hosts (expected 300): " + avgPowerWithHosts);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }

    private static void testToString() {
        System.out.println("Test: toString() method");
        try {
            CloudDatacenter dc = new CloudDatacenter("DC-Test", 25, 75000.0);
            Host host = new Host();
            dc.addHost(host);
            dc.addCustomer("TestCustomer");
            dc.setActiveSeconds(1000);

            String result = dc.toString();

            boolean passed = result.contains("DC-Test") &&
                           result.contains("activeSeconds=1000") &&
                           result.contains("hosts=1") &&
                           result.contains("customers=1") &&
                           result.contains("maxHostCapacity=25");

            if (passed) {
                System.out.println("  PASSED");
                System.out.println("    " + result + "\n");
                testsPassed++;
            } else {
                System.out.println("  FAILED: toString() missing expected content\n");
                System.out.println("    Got: " + result);
                testsFailed++;
            }
        } catch (Exception e) {
            System.out.println("  FAILED: Exception thrown - " + e.getMessage() + "\n");
            testsFailed++;
        }
    }
}
