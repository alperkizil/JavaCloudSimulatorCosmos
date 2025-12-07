package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.InitializationStep;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.strategy.*;

/**
 * Test for the HostPlacementStep implementation.
 * Verifies that hosts are correctly assigned to datacenters using different strategies.
 */
public class HostPlacementStepTest {

    public static void main(String[] args) {
        System.out.println("=== HostPlacementStep Test ===\n");

        // Run tests for each strategy
        testFirstFitStrategy();
        testPowerBasedBestFitStrategy();
        testSlotBasedBestFitStrategy();
        testPowerAwareConsolidatingStrategy();
        testPowerAwareLoadBalancingStrategy();
        testEdgeCases();

        System.out.println("\n=== All Tests Completed ===");
    }

    private static SimulationContext createTestContext() {
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse("configs/sample-experiment.cosc");
        SimulationContext context = new SimulationContext();
        InitializationStep initStep = new InitializationStep(config);
        initStep.execute(context);
        return context;
    }

    private static void testFirstFitStrategy() {
        System.out.println("Test 1: First Fit Strategy");
        System.out.println("-".repeat(50));

        SimulationContext context = createTestContext();
        HostPlacementStep step = new HostPlacementStep(new FirstFitHostPlacementStrategy());

        // Verify hosts are unassigned before placement
        long unassignedBefore = context.getHosts().stream()
                .filter(h -> h.getAssignedDatacenterId() == null)
                .count();
        System.out.println("  Hosts unassigned before: " + unassignedBefore);

        // Execute placement
        step.execute(context);

        // Verify results
        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  Hosts placed: " + step.getHostsPlaced());
        System.out.println("  Hosts failed: " + step.getHostsFailed());

        // Show distribution
        System.out.println("  Distribution:");
        for (CloudDatacenter dc : context.getDatacenters()) {
            System.out.println("    " + dc.getName() + ": " + dc.getHosts().size() + " hosts");
        }

        // Verify all hosts assigned
        long unassignedAfter = context.getHosts().stream()
                .filter(h -> h.getAssignedDatacenterId() == null)
                .count();

        boolean passed = step.getHostsPlaced() == 5 && step.getHostsFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testPowerBasedBestFitStrategy() {
        System.out.println("Test 2: Power-Based Best Fit Strategy");
        System.out.println("-".repeat(50));

        SimulationContext context = createTestContext();
        HostPlacementStep step = new HostPlacementStep(new PowerBasedBestFitHostPlacementStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  Hosts placed: " + step.getHostsPlaced());
        System.out.println("  Hosts failed: " + step.getHostsFailed());

        // Show distribution with power info
        System.out.println("  Distribution (with power):");
        for (CloudDatacenter dc : context.getDatacenters()) {
            dc.updateTotalMomentaryPowerDraw();
            System.out.printf("    %s: %d hosts, %.1fW / %.1fW (%.1f%% used)%n",
                    dc.getName(),
                    dc.getHosts().size(),
                    dc.getTotalMomentaryPowerDraw(),
                    dc.getTotalMaxPowerDraw(),
                    (dc.getTotalMomentaryPowerDraw() / dc.getTotalMaxPowerDraw()) * 100);
        }

        boolean passed = step.getHostsPlaced() == 5 && step.getHostsFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testSlotBasedBestFitStrategy() {
        System.out.println("Test 3: Slot-Based Best Fit Strategy");
        System.out.println("-".repeat(50));

        SimulationContext context = createTestContext();
        HostPlacementStep step = new HostPlacementStep(new SlotBasedBestFitHostPlacementStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  Hosts placed: " + step.getHostsPlaced());
        System.out.println("  Hosts failed: " + step.getHostsFailed());

        // Show distribution with slot info
        System.out.println("  Distribution (with slots):");
        for (CloudDatacenter dc : context.getDatacenters()) {
            int remaining = dc.getMaxHostCapacity() - dc.getHosts().size();
            System.out.printf("    %s: %d/%d hosts (%d slots remaining)%n",
                    dc.getName(),
                    dc.getHosts().size(),
                    dc.getMaxHostCapacity(),
                    remaining);
        }

        boolean passed = step.getHostsPlaced() == 5 && step.getHostsFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testPowerAwareConsolidatingStrategy() {
        System.out.println("Test 4: Power-Aware Consolidating Strategy");
        System.out.println("-".repeat(50));

        SimulationContext context = createTestContext();
        HostPlacementStep step = new HostPlacementStep(new PowerAwareConsolidatingHostPlacementStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  Hosts placed: " + step.getHostsPlaced());
        System.out.println("  Hosts failed: " + step.getHostsFailed());

        // Show distribution - expect consolidation into fewer datacenters
        System.out.println("  Distribution (consolidating behavior):");
        int activeDatacenters = 0;
        for (CloudDatacenter dc : context.getDatacenters()) {
            double utilization = (double) dc.getHosts().size() / dc.getMaxHostCapacity() * 100;
            System.out.printf("    %s: %d hosts (%.1f%% utilization)%n",
                    dc.getName(),
                    dc.getHosts().size(),
                    utilization);
            if (dc.getHosts().size() > 0) activeDatacenters++;
        }
        System.out.println("  Active datacenters: " + activeDatacenters);

        boolean passed = step.getHostsPlaced() == 5 && step.getHostsFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testPowerAwareLoadBalancingStrategy() {
        System.out.println("Test 5: Power-Aware Load Balancing Strategy");
        System.out.println("-".repeat(50));

        SimulationContext context = createTestContext();
        HostPlacementStep step = new HostPlacementStep(new PowerAwareLoadBalancingHostPlacementStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  Hosts placed: " + step.getHostsPlaced());
        System.out.println("  Hosts failed: " + step.getHostsFailed());

        // Show distribution - expect balanced distribution
        System.out.println("  Distribution (load balancing behavior):");
        double totalPowerUtil = 0;
        int dcCount = 0;
        for (CloudDatacenter dc : context.getDatacenters()) {
            dc.updateTotalMomentaryPowerDraw();
            double powerUtil = (dc.getTotalMomentaryPowerDraw() / dc.getTotalMaxPowerDraw()) * 100;
            totalPowerUtil += powerUtil;
            dcCount++;
            System.out.printf("    %s: %d hosts, %.1f%% power utilization%n",
                    dc.getName(),
                    dc.getHosts().size(),
                    powerUtil);
        }
        System.out.printf("  Average power utilization: %.1f%%%n", totalPowerUtil / dcCount);

        boolean passed = step.getHostsPlaced() == 5 && step.getHostsFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testEdgeCases() {
        System.out.println("Test 6: Edge Cases");
        System.out.println("-".repeat(50));

        // Test 6a: Empty hosts list
        System.out.println("  Test 6a: Empty hosts list");
        SimulationContext emptyContext = new SimulationContext();
        HostPlacementStep step = new HostPlacementStep();
        step.execute(emptyContext);
        boolean test6a = step.getHostsPlaced() == 0 && step.getHostsFailed() == 0;
        System.out.println("    Result: " + (test6a ? "PASSED" : "FAILED"));

        // Test 6b: Default strategy (FirstFit)
        System.out.println("  Test 6b: Default strategy is FirstFit");
        HostPlacementStep defaultStep = new HostPlacementStep();
        boolean test6b = defaultStep.getStrategy() instanceof FirstFitHostPlacementStrategy;
        System.out.println("    Strategy: " + defaultStep.getStrategy().getStrategyName());
        System.out.println("    Result: " + (test6b ? "PASSED" : "FAILED"));

        // Test 6c: Strategy descriptions
        System.out.println("  Test 6c: Strategy descriptions available");
        HostPlacementStrategy[] strategies = {
            new FirstFitHostPlacementStrategy(),
            new PowerBasedBestFitHostPlacementStrategy(),
            new SlotBasedBestFitHostPlacementStrategy(),
            new PowerAwareConsolidatingHostPlacementStrategy(),
            new PowerAwareLoadBalancingHostPlacementStrategy()
        };
        boolean test6c = true;
        for (HostPlacementStrategy s : strategies) {
            if (s.getDescription() == null || s.getDescription().isEmpty()) {
                test6c = false;
            }
            System.out.println("    " + s.getStrategyName() + ": " +
                    (s.getDescription().length() > 50 ? s.getDescription().substring(0, 50) + "..." : s.getDescription()));
        }
        System.out.println("    Result: " + (test6c ? "PASSED" : "FAILED"));

        // Test 6d: Metrics recorded
        System.out.println("  Test 6d: Metrics recorded correctly");
        SimulationContext context = createTestContext();
        HostPlacementStep metricStep = new HostPlacementStep(new FirstFitHostPlacementStrategy());
        metricStep.execute(context);
        boolean test6d = context.getMetric("hostPlacement.hostsPlaced") != null &&
                        context.getMetric("hostPlacement.hostsFailed") != null &&
                        context.getMetric("hostPlacement.strategy") != null;
        System.out.println("    hostPlacement.hostsPlaced: " + context.getMetric("hostPlacement.hostsPlaced"));
        System.out.println("    hostPlacement.hostsFailed: " + context.getMetric("hostPlacement.hostsFailed"));
        System.out.println("    hostPlacement.strategy: " + context.getMetric("hostPlacement.strategy"));
        System.out.println("    Result: " + (test6d ? "PASSED" : "FAILED"));

        System.out.println();
    }
}
