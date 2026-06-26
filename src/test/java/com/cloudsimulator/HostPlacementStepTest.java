package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.InitializationStep;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.PlacementStrategy.hostPlacement.*;
import com.cloudsimulator.PlacementStrategy.VMPlacement.*;

/**
 * Test for the HostPlacementStep implementation.
 * Verifies that hosts are correctly assigned to datacenters using different strategies.
 */
public class HostPlacementStepTest {

    public static void main(String[] args) {
        System.out.println("=== HostPlacementStep Test ===\n");

        // Run tests for each strategy
        testFirstFitStrategy();
        testSlotBasedBestFitStrategy();
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

    private static void testSlotBasedBestFitStrategy() {
        System.out.println("Test 2: Slot-Based Best Fit Strategy");
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

    private static void testPowerAwareLoadBalancingStrategy() {
        System.out.println("Test 3: Power-Aware Load Balancing Strategy");
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
        System.out.println("Test 4: Edge Cases");
        System.out.println("-".repeat(50));

        // Test 4a: Empty hosts list
        System.out.println("  Test 4a: Empty hosts list");
        SimulationContext emptyContext = new SimulationContext();
        HostPlacementStep step = new HostPlacementStep();
        step.execute(emptyContext);
        boolean test4a = step.getHostsPlaced() == 0 && step.getHostsFailed() == 0;
        System.out.println("    Result: " + (test4a ? "PASSED" : "FAILED"));

        // Test 4b: Default strategy (FirstFit)
        System.out.println("  Test 4b: Default strategy is FirstFit");
        HostPlacementStep defaultStep = new HostPlacementStep();
        boolean test4b = defaultStep.getStrategy() instanceof FirstFitHostPlacementStrategy;
        System.out.println("    Strategy: " + defaultStep.getStrategy().getStrategyName());
        System.out.println("    Result: " + (test4b ? "PASSED" : "FAILED"));

        // Test 4c: Strategy descriptions
        System.out.println("  Test 4c: Strategy descriptions available");
        HostPlacementStrategy[] strategies = {
            new FirstFitHostPlacementStrategy(),
            new SlotBasedBestFitHostPlacementStrategy(),
            new PowerAwareLoadBalancingHostPlacementStrategy()
        };
        boolean test4c = true;
        for (HostPlacementStrategy s : strategies) {
            if (s.getDescription() == null || s.getDescription().isEmpty()) {
                test4c = false;
            }
            System.out.println("    " + s.getStrategyName() + ": " +
                    (s.getDescription().length() > 50 ? s.getDescription().substring(0, 50) + "..." : s.getDescription()));
        }
        System.out.println("    Result: " + (test4c ? "PASSED" : "FAILED"));

        // Test 4d: Metrics recorded
        System.out.println("  Test 4d: Metrics recorded correctly");
        SimulationContext context = createTestContext();
        HostPlacementStep metricStep = new HostPlacementStep(new FirstFitHostPlacementStrategy());
        metricStep.execute(context);
        boolean test4d = context.getMetric("hostPlacement.hostsPlaced") != null &&
                        context.getMetric("hostPlacement.hostsFailed") != null &&
                        context.getMetric("hostPlacement.strategy") != null;
        System.out.println("    hostPlacement.hostsPlaced: " + context.getMetric("hostPlacement.hostsPlaced"));
        System.out.println("    hostPlacement.hostsFailed: " + context.getMetric("hostPlacement.hostsFailed"));
        System.out.println("    hostPlacement.strategy: " + context.getMetric("hostPlacement.strategy"));
        System.out.println("    Result: " + (test4d ? "PASSED" : "FAILED"));

        System.out.println();
    }
}
