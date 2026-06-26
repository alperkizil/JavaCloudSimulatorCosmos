package com.cloudsimulator;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationEngine;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.steps.*;
import com.cloudsimulator.steps.EnergyCalculationStep.CarbonIntensityRegion;
import com.cloudsimulator.PlacementStrategy.hostPlacement.*;
import com.cloudsimulator.PlacementStrategy.VMPlacement.*;
import com.cloudsimulator.PlacementStrategy.task.*;

/**
 * Runner for executing a single simulation experiment from a .cosc config file.
 *
 * This runner executes all 10 simulation steps:
 * 1. Initialization - Create entities from config
 * 2. Host Placement - Assign hosts to datacenters
 * 3. User-DC Mapping - Validate user preferences
 * 4. VM Placement - Assign VMs to hosts
 * 5. Task Assignment - Assign tasks to VMs
 * 6. VM Execution - Run time-stepped simulation
 * 7. Task Execution - Post-simulation analysis
 * 8. Energy Calculation - Compute energy/carbon/cost
 * 9. Metrics Collection - SLA and percentiles
 * 10. Reporting - Generate CSV reports
 *
 * Usage:
 *   java com.cloudsimulator.SampleScenarioRunner [configPath] [reportDir]
 *
 * Example:
 *   java com.cloudsimulator.SampleScenarioRunner configs/sampleScenario/100_20251210_202825_001.cosc ./reports
 */
public class SampleScenarioRunner {

    // Configuration options (can be customized)
    private String configPath;
    private String reportOutputDir = "./reports";

    // Strategy choices (defaults)
    private HostPlacementStrategy hostPlacementStrategy = new FirstFitHostPlacementStrategy();
    private VMPlacementStrategy vmPlacementStrategy = new FirstFitVMPlacementStrategy();
    private TaskAssignmentStrategy taskAssignmentStrategy = new FirstAvailableTaskAssignmentStrategy();

    // Energy configuration
    private double pue = 1.5;
    private CarbonIntensityRegion carbonRegion = CarbonIntensityRegion.US_AVERAGE;
    private double electricityCostPerKWh = 0.10;

    // SLA configuration
    private long primarySLAThreshold = 3600; // 1 hour

    // Results
    private SimulationSummary summary;
    private long executionTimeMs;

    public SampleScenarioRunner(String configPath) {
        this.configPath = configPath;
    }

    /**
     * Runs the complete simulation pipeline.
     *
     * @return SimulationSummary containing all results
     */
    public SimulationSummary run() {
        long startTime = System.currentTimeMillis();

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           JavaCloudSimulatorCosmos - Experiment Runner       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Config file: " + configPath);
        System.out.println("Report output: " + reportOutputDir);
        System.out.println();

        // Load configuration
        FileConfigParser parser = new FileConfigParser();
        ExperimentConfiguration config = parser.parse(configPath);

        System.out.println("Configuration loaded:");
        System.out.println("  Seed: " + config.getRandomSeed());
        System.out.println("  Datacenters: " + config.getDatacenterConfigs().size());
        System.out.println("  Hosts: " + config.getHostConfigs().size());
        System.out.println("  Users: " + config.getUserConfigs().size());
        System.out.println("  VMs: " + config.getVmConfigs().size());
        System.out.println("  Tasks: " + config.getTaskConfigs().size());
        System.out.println();

        // Create simulation engine
        SimulationEngine engine = new SimulationEngine();
        engine.configure(config);

        // Add all 10 simulation steps
        System.out.println("Adding simulation steps...");

        // Step 1: Initialization
        InitializationStep initStep = new InitializationStep(config);
        engine.addStep(initStep);
        System.out.println("  [1/10] InitializationStep");

        // Step 2: Host Placement
        HostPlacementStep hostPlacementStep = new HostPlacementStep(hostPlacementStrategy);
        engine.addStep(hostPlacementStep);
        System.out.println("  [2/10] HostPlacementStep (" + hostPlacementStrategy.getStrategyName() + ")");

        // Step 3: User-Datacenter Mapping
        UserDatacenterMappingStep userMappingStep = new UserDatacenterMappingStep();
        engine.addStep(userMappingStep);
        System.out.println("  [3/10] UserDatacenterMappingStep");

        // Step 4: VM Placement
        VMPlacementStep vmPlacementStep = new VMPlacementStep(vmPlacementStrategy);
        engine.addStep(vmPlacementStep);
        System.out.println("  [4/10] VMPlacementStep (" + vmPlacementStrategy.getStrategyName() + ")");

        // Step 5: Task Assignment
        TaskAssignmentStep taskAssignmentStep = new TaskAssignmentStep(taskAssignmentStrategy);
        engine.addStep(taskAssignmentStep);
        System.out.println("  [5/10] TaskAssignmentStep (" + taskAssignmentStrategy.getStrategyName() + ")");

        // Step 6: VM Execution
        VMExecutionStep vmExecutionStep = new VMExecutionStep();
        engine.addStep(vmExecutionStep);
        System.out.println("  [6/10] VMExecutionStep");

        // Step 7: Task Execution Analysis
        TaskExecutionStep taskExecutionStep = new TaskExecutionStep();
        engine.addStep(taskExecutionStep);
        System.out.println("  [7/10] TaskExecutionStep");

        // Step 8: Energy Calculation
        EnergyCalculationStep energyStep = new EnergyCalculationStep();
        energyStep.setPUE(pue);
        energyStep.setCarbonIntensity(carbonRegion);
        energyStep.setElectricityCostPerKWh(electricityCostPerKWh);
        engine.addStep(energyStep);
        System.out.println("  [8/10] EnergyCalculationStep (PUE=" + pue + ", Region=" + carbonRegion + ")");

        // Step 9: Metrics Collection
        MetricsCollectionStep metricsStep = new MetricsCollectionStep();
        metricsStep.setPrimarySLAThreshold(primarySLAThreshold);
        engine.addStep(metricsStep);
        System.out.println("  [9/10] MetricsCollectionStep (SLA=" + primarySLAThreshold + "s)");

        // Step 10: Reporting
        ReportingStep reportingStep = new ReportingStep();
        reportingStep.setBaseOutputDirectory(reportOutputDir);
        reportingStep.setCustomPrefix("seed_" + config.getRandomSeed());
        engine.addStep(reportingStep);
        System.out.println("  [10/10] ReportingStep");

        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("Starting simulation...");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();

        // Run the simulation
        engine.run();

        // Get results
        SimulationContext context = engine.getContext();
        this.summary = metricsStep.getSummary();

        executionTimeMs = System.currentTimeMillis() - startTime;

        // Print summary
        printSummary(config, context, reportingStep);

        return summary;
    }

    /**
     * Prints a summary of the simulation results.
     */
    private void printSummary(ExperimentConfiguration config, SimulationContext context,
                              ReportingStep reportingStep) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    SIMULATION COMPLETE                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        if (summary != null) {
            System.out.println("═══ INFRASTRUCTURE ═══");
            System.out.printf("  Datacenters: %d%n", summary.getInfrastructure().datacenterCount);
            System.out.printf("  Hosts: %d (Active: %d, Idle: %d)%n",
                summary.getInfrastructure().hostCount,
                summary.getInfrastructure().activeHostCount,
                summary.getInfrastructure().idleHostCount);
            System.out.printf("  VMs: %d%n", summary.getInfrastructure().vmCount);
            System.out.printf("  Avg CPU Utilization: %.1f%%%n",
                summary.getInfrastructure().avgCpuUtilization * 100);
            System.out.println();

            System.out.println("═══ TASKS ═══");
            System.out.printf("  Total: %d%n", summary.getTasks().totalTasks);
            System.out.printf("  Completed: %d (%.1f%%)%n",
                summary.getTasks().completedTasks,
                summary.getTasks().completionRate * 100);
            System.out.printf("  Failed: %d%n", summary.getTasks().failedTasks);
            System.out.printf("  Unassigned: %d%n", summary.getTasks().unassignedTasks);
            System.out.println();

            System.out.println("═══ PERFORMANCE ═══");
            System.out.printf("  Makespan: %d seconds (%.1f minutes)%n",
                summary.getPerformance().makespanSeconds,
                summary.getPerformance().makespanSeconds / 60.0);
            System.out.printf("  Throughput: %.4f tasks/sec%n", summary.getPerformance().throughputTasksPerSecond);
            System.out.printf("  Avg Turnaround: %.1f seconds%n", summary.getPerformance().avgTurnaroundTimeSeconds);
            System.out.printf("  P50 Turnaround: %.1f seconds%n", summary.getPerformance().p50TurnaroundTimeSeconds);
            System.out.printf("  P90 Turnaround: %.1f seconds%n", summary.getPerformance().p90TurnaroundTimeSeconds);
            System.out.printf("  P99 Turnaround: %.1f seconds%n", summary.getPerformance().p99TurnaroundTimeSeconds);
            System.out.println();

            System.out.println("═══ ENERGY & CARBON ═══");
            System.out.printf("  IT Energy: %.4f kWh%n", summary.getEnergy().totalITEnergyKWh);
            System.out.printf("  Facility Energy (PUE=%.2f): %.4f kWh%n",
                summary.getEnergy().pue, summary.getEnergy().totalFacilityEnergyKWh);
            System.out.printf("  Carbon Footprint: %.4f kg CO2%n", summary.getEnergy().carbonFootprintKg);
            System.out.printf("  Estimated Cost: $%.4f%n", summary.getEnergy().estimatedCostDollars);
            System.out.println();

            System.out.println("═══ SLA COMPLIANCE ═══");
            System.out.printf("  Primary Threshold: %d seconds%n", summary.getSla().slaThresholdSeconds);
            System.out.printf("  Compliance: %.1f%%%n", summary.getSla().slaCompliancePercent);
            System.out.println();
        }

        System.out.println("═══ OUTPUT ═══");
        System.out.printf("  Reports Directory: %s%n", reportingStep.getOutputDirectory());
        System.out.printf("  Files Generated: %d%n", reportingStep.getTotalFilesGenerated());
        System.out.printf("  Total Size: %.2f KB%n", reportingStep.getTotalBytesWritten() / 1024.0);
        System.out.println();

        System.out.printf("Total wall-clock execution time: %.2f seconds%n", executionTimeMs / 1000.0);
        System.out.println();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Configuration Setters
    // ═══════════════════════════════════════════════════════════════════════

    public void setReportOutputDir(String dir) {
        this.reportOutputDir = dir;
    }

    public void setHostPlacementStrategy(HostPlacementStrategy strategy) {
        this.hostPlacementStrategy = strategy;
    }

    public void setVmPlacementStrategy(VMPlacementStrategy strategy) {
        this.vmPlacementStrategy = strategy;
    }

    public void setTaskAssignmentStrategy(TaskAssignmentStrategy strategy) {
        this.taskAssignmentStrategy = strategy;
    }

    public void setPue(double pue) {
        this.pue = pue;
    }

    public void setCarbonRegion(CarbonIntensityRegion region) {
        this.carbonRegion = region;
    }

    public void setElectricityCostPerKWh(double cost) {
        this.electricityCostPerKWh = cost;
    }

    public void setPrimarySLAThreshold(long seconds) {
        this.primarySLAThreshold = seconds;
    }

    // Getters
    public SimulationSummary getSummary() {
        return summary;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Main Method
    // ═══════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        String configPath = "configs/sampleScenario/100_20251210_202825_001.cosc";
        String reportDir = "./reports";

        if (args.length >= 1) {
            configPath = args[0];
        }
        if (args.length >= 2) {
            reportDir = args[1];
        }

        SampleScenarioRunner runner = new SampleScenarioRunner(configPath);
        runner.setReportOutputDir(reportDir);

        // You can customize strategies here:
        // runner.setHostPlacementStrategy(new PowerAwareConsolidatingHostPlacementStrategy());
        // runner.setVmPlacementStrategy(new PowerAwareVMPlacementStrategy());
        // runner.setTaskAssignmentStrategy(new WorkloadAwareTaskAssignmentStrategy());

        runner.run();
    }
}
