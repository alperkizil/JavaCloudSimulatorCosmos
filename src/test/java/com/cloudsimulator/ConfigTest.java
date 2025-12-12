package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.engine.SimulationEngine;

/**
 * Test program to verify configuration loading from .cosc files.
 */
public class ConfigTest {

    public static void main(String[] args) {
        System.out.println("=== Configuration System Test ===\n");

        // Create engine
        SimulationEngine engine = new SimulationEngine();
        engine.setDebugEnabled(false);

        // Load configuration from file
        String configFile = "configs/sample-experiment.cosc";
        System.out.println("Loading configuration from: " + configFile);

        try {
            engine.configure(configFile);

            // Get configuration
            ExperimentConfiguration config = engine.getConfiguration();

            System.out.println("\n=== Configuration Loaded Successfully ===");
            System.out.println(config);

            System.out.println("\n=== Datacenters ===");
            config.getDatacenterConfigs().forEach(dc -> System.out.println("  " + dc));

            System.out.println("\n=== Hosts ===");
            config.getHostConfigs().forEach(host -> System.out.println("  " + host));

            System.out.println("\n=== Users ===");
            config.getUserConfigs().forEach(user -> System.out.println("  " + user));

            System.out.println("\n=== VMs ===");
            config.getVmConfigs().forEach(vm -> System.out.println("  " + vm));

            System.out.println("\n=== Tasks ===");
            config.getTaskConfigs().forEach(task -> System.out.println("  " + task));

            // Test deep copy
            System.out.println("\n=== Testing Deep Copy ===");
            ExperimentConfiguration copy = config.clone();
            System.out.println("Original seed: " + config.getRandomSeed());
            System.out.println("Copy seed: " + copy.getRandomSeed());

            ExperimentConfiguration copyWithNewSeed = config.cloneWithSeed(999);
            System.out.println("Copy with new seed: " + copyWithNewSeed.getRandomSeed());

            System.out.println("\n=== Configuration System Test Complete ===");

        } catch (Exception e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
