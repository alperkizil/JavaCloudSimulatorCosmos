package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationEngine;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.steps.*;
import com.cloudsimulator.PlacementStrategy.hostPlacement.*;
import com.cloudsimulator.PlacementStrategy.VMPlacement.*;
import com.cloudsimulator.PlacementStrategy.task.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.*;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.List;

/**
 * Test to investigate the 1-second makespan discrepancy between
 * GenerationalGA prediction and actual simulation.
 */
public class MakespanDiscrepancyTest {

    public static void main(String[] args) {
        String configPath = "configs/sampleScenario_single_user/1_20251213_194237_001.cosc";

        if (args.length >= 1) {
            configPath = args[0];
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     Makespan Discrepancy Investigation Test                   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Config: " + configPath);
        System.out.println();

        // Initialize random generator
        RandomGenerator.initialize(42L);

        // Load configuration
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse(configPath);

        System.out.println("Configuration loaded:");
        System.out.println("  Seed: " + config.getRandomSeed());
        System.out.println("  VMs: " + config.getVmConfigs().size());
        System.out.println("  Tasks: " + config.getTaskConfigs().size());
        System.out.println();

        // Configure GenerationalGA
        GAConfiguration gaConfig = GAConfiguration.builder()
            .populationSize(100)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .elitePercentage(0.1)
            .tournamentSize(3)
            .objective(new MakespanObjective())
            .terminationCondition(new GenerationCountTermination(100))
            .verboseLogging(false)
            .build();

        GenerationalGATaskSchedulingStrategy gaStrategy =
            new GenerationalGATaskSchedulingStrategy(gaConfig);

        // Run simulation with GenerationalGA strategy
        SampleScenarioRunner runner = new SampleScenarioRunner(configPath);
        runner.setTaskAssignmentStrategy(gaStrategy);
        runner.setReportOutputDir("./reports/discrepancy_test");

        System.out.println("Running simulation with GenerationalGA...");
        System.out.println();

        SimulationSummary summary = runner.run();

        // Get results
        long actualMakespan = summary.getPerformance().makespanSeconds;

        // Get the predicted makespan from the GA solution
        SchedulingSolution gaSolution = gaStrategy.getLastSolution();
        double predictedMakespan = gaSolution != null ? gaSolution.getObjectiveValue(0) : -1;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                 MAKESPAN COMPARISON                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.printf("  Predicted (GA):   %.1f seconds%n", predictedMakespan);
        System.out.printf("  Actual (Sim):     %d seconds%n", actualMakespan);
        System.out.printf("  Discrepancy:      %.1f seconds%n", predictedMakespan - actualMakespan);
        System.out.println();

        if (Math.abs(predictedMakespan - actualMakespan) < 0.001) {
            System.out.println("✓ PERFECT MATCH - No discrepancy detected!");
        } else if (predictedMakespan > actualMakespan) {
            System.out.println("! GA OVER-ESTIMATES by " + (predictedMakespan - actualMakespan) + " second(s)");
        } else {
            System.out.println("! GA UNDER-ESTIMATES by " + (actualMakespan - predictedMakespan) + " second(s)");
        }
        System.out.println();
    }
}
