package com.cloudsimulator;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.strategy.*;

/**
 * Test for HostPlacementStep with constrained power budgets.
 * This test creates a scenario where datacenters have limited power,
 * forcing strategies to distribute hosts across multiple datacenters.
 */
public class HostPlacementConstrainedTest {

    public static void main(String[] args) {
        System.out.println("=== HostPlacementStep Constrained Power Test ===\n");

        // Show power model info
        System.out.println("Power Model Analysis:");
        PowerModel standard = new PowerModel();  // Default: 50W idle CPU + 30W idle GPU + 100W other = 180W idle
        System.out.printf("  StandardPowerModel idle power: %.1fW%n",
            standard.calculateTotalPower(0.0, 0.0));
        System.out.printf("  StandardPowerModel max power: %.1fW%n",
            standard.calculateTotalPower(1.0, 1.0));
        System.out.println();

        // Run tests
        testFirstFitConstrained();
        testPowerBasedBestFitConstrained();
        testSlotBasedBestFitConstrained();
        testPowerAwareConsolidatingConstrained();
        testPowerAwareLoadBalancingConstrained();

        System.out.println("\n=== All Constrained Tests Completed ===");
    }

    /**
     * Creates a test context with constrained power budgets.
     *
     * Setup:
     * - 3 datacenters with limited power budgets (400W, 500W, 600W)
     * - 5 hosts with ~180W idle power each (total ~900W needed)
     * - This forces distribution across datacenters
     */
    private static SimulationContext createConstrainedContext() {
        SimulationContext context = new SimulationContext();

        // Create datacenters with CONSTRAINED power budgets
        // Total host idle power will be ~900W, so we need to spread across DCs
        CloudDatacenter dc1 = new CloudDatacenter("DC-Small", 10, 400.0);   // Can fit ~2 hosts
        CloudDatacenter dc2 = new CloudDatacenter("DC-Medium", 10, 500.0);  // Can fit ~2-3 hosts
        CloudDatacenter dc3 = new CloudDatacenter("DC-Large", 10, 600.0);   // Can fit ~3 hosts

        context.addDatacenter(dc1);
        context.addDatacenter(dc2);
        context.addDatacenter(dc3);

        // Create 5 hosts with StandardPowerModel (~180W idle each)
        for (int i = 1; i <= 5; i++) {
            Host host = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            PowerModel pm = new PowerModel();  // 180W idle
            host.setPowerModel(pm);

            // IMPORTANT: Initialize the host's power draw to idle power
            // This simulates a host that's powered on but not running VMs
            double idlePower = pm.calculateTotalPower(0.0, 0.0);
            host.setCurrentTotalPowerDraw(idlePower);

            context.addHost(host);
        }

        System.out.printf("  Created %d datacenters with power budgets: 400W, 500W, 600W%n",
            context.getDatacenters().size());
        System.out.printf("  Created %d hosts with ~180W idle power each (total ~900W)%n",
            context.getHosts().size());
        System.out.println();

        return context;
    }

    private static void printDistribution(SimulationContext context, HostPlacementStep step) {
        System.out.println("  Distribution:");
        for (CloudDatacenter dc : context.getDatacenters()) {
            dc.updateTotalMomentaryPowerDraw();
            double used = dc.getTotalMomentaryPowerDraw();
            double max = dc.getTotalMaxPowerDraw();
            double remaining = max - used;
            System.out.printf("    %s: %d hosts, %.1fW / %.1fW (%.1fW remaining)%n",
                dc.getName(),
                dc.getHosts().size(),
                used,
                max,
                remaining);
        }
        System.out.printf("  Hosts placed: %d, Failed: %d%n",
            step.getHostsPlaced(), step.getHostsFailed());
    }

    private static void testFirstFitConstrained() {
        System.out.println("Test 1: First Fit Strategy (Constrained)");
        System.out.println("-".repeat(55));

        SimulationContext context = createConstrainedContext();
        HostPlacementStep step = new HostPlacementStep(new FirstFitHostPlacementStrategy());
        step.execute(context);

        printDistribution(context, step);

        // First Fit should fill DC-Small first (2 hosts), then DC-Medium (2 hosts), then DC-Large (1 host)
        boolean distributed = context.getDatacenters().stream()
            .filter(dc -> dc.getHosts().size() > 0)
            .count() > 1;
        System.out.println("  Distributed across multiple DCs: " + (distributed ? "YES" : "NO"));
        System.out.println("  Result: " + (step.getHostsPlaced() == 5 ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testPowerBasedBestFitConstrained() {
        System.out.println("Test 2: Power-Based Best Fit Strategy (Constrained)");
        System.out.println("-".repeat(55));

        SimulationContext context = createConstrainedContext();
        HostPlacementStep step = new HostPlacementStep(new PowerBasedBestFitHostPlacementStrategy());
        step.execute(context);

        printDistribution(context, step);

        // Power-Based Best Fit should prefer DC-Small first (tightest fit)
        System.out.println("  Expected behavior: Prefer datacenters with tightest power fit");
        System.out.println("  Result: " + (step.getHostsPlaced() == 5 ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testSlotBasedBestFitConstrained() {
        System.out.println("Test 3: Slot-Based Best Fit Strategy (Constrained)");
        System.out.println("-".repeat(55));

        // For slot-based test, use different slot capacities
        SimulationContext context = new SimulationContext();

        // Same power budgets but different slot capacities
        CloudDatacenter dc1 = new CloudDatacenter("DC-FewSlots", 3, 600.0);   // 3 slots
        CloudDatacenter dc2 = new CloudDatacenter("DC-MedSlots", 5, 600.0);   // 5 slots
        CloudDatacenter dc3 = new CloudDatacenter("DC-ManySlots", 10, 600.0); // 10 slots

        context.addDatacenter(dc1);
        context.addDatacenter(dc2);
        context.addDatacenter(dc3);

        for (int i = 1; i <= 5; i++) {
            Host host = new Host(2500000000L, 16, ComputeType.CPU_ONLY, 0);
            PowerModel pm = new PowerModel();
            host.setPowerModel(pm);
            host.setCurrentTotalPowerDraw(pm.calculateTotalPower(0.0, 0.0));
            context.addHost(host);
        }

        System.out.printf("  Created 3 DCs with slot capacities: 3, 5, 10 (power: 600W each)%n");
        System.out.printf("  Created 5 hosts%n%n");

        HostPlacementStep step = new HostPlacementStep(new SlotBasedBestFitHostPlacementStrategy());
        step.execute(context);

        System.out.println("  Distribution:");
        for (CloudDatacenter dc : context.getDatacenters()) {
            int remaining = dc.getMaxHostCapacity() - dc.getHosts().size();
            System.out.printf("    %s: %d/%d hosts (%d slots remaining)%n",
                dc.getName(),
                dc.getHosts().size(),
                dc.getMaxHostCapacity(),
                remaining);
        }
        System.out.printf("  Hosts placed: %d, Failed: %d%n",
            step.getHostsPlaced(), step.getHostsFailed());

        // Slot-Based Best Fit should fill DC-FewSlots first (3), then DC-MedSlots (2)
        System.out.println("  Expected behavior: Fill smallest capacity DC first");
        System.out.println("  Result: " + (step.getHostsPlaced() == 5 ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testPowerAwareConsolidatingConstrained() {
        System.out.println("Test 4: Power-Aware Consolidating Strategy (Constrained)");
        System.out.println("-".repeat(55));

        SimulationContext context = createConstrainedContext();
        HostPlacementStep step = new HostPlacementStep(new PowerAwareConsolidatingHostPlacementStrategy());
        step.execute(context);

        printDistribution(context, step);

        // Consolidating should try to pack hosts into fewest DCs possible
        long activeDatacenters = context.getDatacenters().stream()
            .filter(dc -> dc.getHosts().size() > 0)
            .count();
        System.out.println("  Active datacenters: " + activeDatacenters);
        System.out.println("  Expected behavior: Minimize number of active datacenters");
        System.out.println("  Result: " + (step.getHostsPlaced() == 5 ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testPowerAwareLoadBalancingConstrained() {
        System.out.println("Test 5: Power-Aware Load Balancing Strategy (Constrained)");
        System.out.println("-".repeat(55));

        SimulationContext context = createConstrainedContext();
        HostPlacementStep step = new HostPlacementStep(new PowerAwareLoadBalancingHostPlacementStrategy());
        step.execute(context);

        printDistribution(context, step);

        // Calculate power utilization variance (lower = more balanced)
        double[] utilizations = context.getDatacenters().stream()
            .mapToDouble(dc -> {
                dc.updateTotalMomentaryPowerDraw();
                return dc.getTotalMomentaryPowerDraw() / dc.getTotalMaxPowerDraw();
            })
            .toArray();

        double avgUtil = 0;
        for (double u : utilizations) avgUtil += u;
        avgUtil /= utilizations.length;

        double variance = 0;
        for (double u : utilizations) variance += Math.pow(u - avgUtil, 2);
        variance /= utilizations.length;

        System.out.printf("  Power utilization: DC1=%.1f%%, DC2=%.1f%%, DC3=%.1f%%%n",
            utilizations[0] * 100, utilizations[1] * 100, utilizations[2] * 100);
        System.out.printf("  Average utilization: %.1f%%, Variance: %.4f%n", avgUtil * 100, variance);
        System.out.println("  Expected behavior: Balance load across datacenters");
        System.out.println("  Result: " + (step.getHostsPlaced() == 5 ? "PASSED" : "FAILED"));
        System.out.println();
    }
}
