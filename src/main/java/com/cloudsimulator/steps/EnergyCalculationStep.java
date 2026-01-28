package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EnergyCalculationStep aggregates and analyzes energy consumption data
 * from all hosts and datacenters after simulation completes.
 *
 * This is the eighth step in the simulation pipeline, executed after TaskExecutionStep.
 * It calculates total energy, PUE (Power Usage Effectiveness), carbon footprint,
 * and estimated electricity costs.
 *
 * Key metrics calculated:
 * - Total energy consumption (Joules, kWh)
 * - Per-datacenter energy breakdown
 * - Per-host energy consumption
 * - PUE (Power Usage Effectiveness)
 * - Carbon footprint (kg CO2)
 * - Electricity cost ($)
 * - Energy efficiency (instructions per Joule)
 *
 * Usage:
 * <pre>
 * EnergyCalculationStep step = new EnergyCalculationStep();
 * step.setPUE(1.5);
 * step.setCarbonIntensity(CarbonIntensityRegion.US_AVERAGE);
 * step.setElectricityCostPerKWh(0.12);
 * step.execute(context);
 *
 * System.out.println("Total energy: " + step.getTotalEnergyKWh() + " kWh");
 * System.out.println("Carbon footprint: " + step.getCarbonFootprintKg() + " kg CO2");
 * </pre>
 */
public class EnergyCalculationStep implements SimulationStep {

    /**
     * Region-specific carbon intensity values (kg CO2 per kWh).
     * Based on average grid carbon intensity by region.
     */
    public enum CarbonIntensityRegion {
        US_AVERAGE(0.42),           // US national average
        US_CALIFORNIA(0.22),        // California (high renewables)
        US_TEXAS(0.40),             // Texas (ERCOT grid)
        US_MIDWEST(0.55),           // Midwest (coal-heavy)
        EU_AVERAGE(0.30),           // European Union average
        EU_FRANCE(0.06),            // France (nuclear)
        EU_GERMANY(0.35),           // Germany
        EU_POLAND(0.70),            // Poland (coal-heavy)
        EU_NORDICS(0.05),           // Nordic countries (hydro)
        UK(0.25),                   // United Kingdom
        CHINA(0.58),                // China average
        INDIA(0.70),                // India average
        AUSTRALIA(0.65),            // Australia average
        JAPAN(0.45),                // Japan average
        CANADA(0.12),               // Canada (hydro)
        BRAZIL(0.08),               // Brazil (hydro)
        RENEWABLE_ONLY(0.0);        // 100% renewable datacenter

        private final double kgCO2PerKWh;

        CarbonIntensityRegion(double kgCO2PerKWh) {
            this.kgCO2PerKWh = kgCO2PerKWh;
        }

        public double getKgCO2PerKWh() {
            return kgCO2PerKWh;
        }
    }

    // Configuration
    private double pue = 1.5;                           // Default PUE (industry avg ~1.5-1.8)
    private double carbonIntensity = CarbonIntensityRegion.US_AVERAGE.getKgCO2PerKWh();
    private double electricityCostPerKWh = 0.10;        // Default $0.10 per kWh
    private CarbonIntensityRegion selectedRegion = CarbonIntensityRegion.US_AVERAGE;

    // Energy metrics (Joules)
    private double totalITEnergyJoules;                 // IT equipment energy (hosts)
    private double totalFacilityEnergyJoules;           // Total including PUE overhead
    private double totalCpuEnergyJoules;
    private double totalGpuEnergyJoules;
    private double totalOtherComponentsEnergyJoules;

    // Per-datacenter energy
    private Map<String, Double> datacenterEnergyJoules;
    private Map<String, Double> datacenterPeakPowerWatts;

    // Per-host energy
    private Map<Long, Double> hostEnergyJoules;
    private Map<Long, Double> hostPeakPowerWatts;

    // Power metrics
    private double peakTotalPowerWatts;
    private double averagePowerWatts;

    // Derived metrics
    private double carbonFootprintKg;
    private double estimatedCostDollars;
    private double energyPerTaskJoules;
    private double energyEfficiencyIpsPerWatt;

    // Simulation info
    private long simulationDurationSeconds;
    private long totalInstructionsExecuted;
    private int tasksCompleted;

    public EnergyCalculationStep() {
        this.datacenterEnergyJoules = new HashMap<>();
        this.datacenterPeakPowerWatts = new HashMap<>();
        this.hostEnergyJoules = new HashMap<>();
        this.hostPeakPowerWatts = new HashMap<>();
    }

    @Override
    public void execute(SimulationContext context) {
        resetState();

        List<CloudDatacenter> datacenters = context.getDatacenters();
        List<Host> hosts = context.getHosts();
        List<Task> tasks = context.getTasks();

        if (hosts == null || hosts.isEmpty()) {
            logInfo("No hosts to analyze. Skipping EnergyCalculationStep.");
            recordMetrics(context);
            return;
        }

        logInfo("Calculating energy consumption metrics...");

        // Get simulation duration from context metrics
        Object simDuration = context.getMetric("vmExecution.totalSimulationSeconds");
        simulationDurationSeconds = simDuration != null ? ((Number) simDuration).longValue() : 0;

        // Count completed tasks
        tasksCompleted = tasks != null ?
            (int) tasks.stream().filter(Task::isCompleted).count() : 0;

        // Calculate total instructions executed
        totalInstructionsExecuted = tasks != null ?
            tasks.stream()
                .filter(Task::isCompleted)
                .mapToLong(Task::getInstructionLength)
                .sum() : 0;

        // Aggregate energy from hosts
        calculateHostEnergy(hosts);

        // Aggregate energy from datacenters
        calculateDatacenterEnergy(datacenters);

        // Calculate facility energy (with PUE)
        totalFacilityEnergyJoules = totalITEnergyJoules * pue;

        // Calculate derived metrics
        calculateDerivedMetrics();

        // Log summary
        logSummary();

        // Record metrics to context
        recordMetrics(context);
    }

    /**
     * Resets calculated fields so the step can be reused safely across multiple runs
     * (e.g., for each solution in a Pareto front).
     */
    private void resetState() {
        totalITEnergyJoules = 0;
        totalFacilityEnergyJoules = 0;
        totalCpuEnergyJoules = 0;
        totalGpuEnergyJoules = 0;
        totalOtherComponentsEnergyJoules = 0;
        peakTotalPowerWatts = 0;
        averagePowerWatts = 0;
        carbonFootprintKg = 0;
        estimatedCostDollars = 0;
        energyPerTaskJoules = 0;
        energyEfficiencyIpsPerWatt = 0;
        simulationDurationSeconds = 0;
        totalInstructionsExecuted = 0;
        tasksCompleted = 0;

        datacenterEnergyJoules.clear();
        datacenterPeakPowerWatts.clear();
        hostEnergyJoules.clear();
        hostPeakPowerWatts.clear();
    }

    /**
     * Calculates energy metrics from all hosts.
     */
    private void calculateHostEnergy(List<Host> hosts) {
        totalITEnergyJoules = 0;
        totalCpuEnergyJoules = 0;
        totalGpuEnergyJoules = 0;
        totalOtherComponentsEnergyJoules = 0;
        peakTotalPowerWatts = 0;

        for (Host host : hosts) {
            double hostEnergy = host.getTotalEnergyConsumed();
            hostEnergyJoules.put(host.getId(), hostEnergy);
            totalITEnergyJoules += hostEnergy;

            // Track peak power (maximum observed during simulation, not current)
            double hostPeakPower = host.getPeakTotalPowerDraw();
            hostPeakPowerWatts.put(host.getId(), hostPeakPower);
            peakTotalPowerWatts += hostPeakPower;

            // Estimate component breakdown based on power model ratios
            if (host.getPowerModel() != null && hostEnergy > 0) {
                double totalMaxPower = host.getPowerModel().getMaxCpuPower() +
                                      host.getPowerModel().getMaxGpuPower() +
                                      host.getPowerModel().getOtherComponentsPower();

                if (totalMaxPower > 0) {
                    double cpuRatio = host.getPowerModel().getMaxCpuPower() / totalMaxPower;
                    double gpuRatio = host.getPowerModel().getMaxGpuPower() / totalMaxPower;
                    double otherRatio = host.getPowerModel().getOtherComponentsPower() / totalMaxPower;

                    totalCpuEnergyJoules += hostEnergy * cpuRatio;
                    totalGpuEnergyJoules += hostEnergy * gpuRatio;
                    totalOtherComponentsEnergyJoules += hostEnergy * otherRatio;
                }
            }
        }

        // Calculate average power
        if (simulationDurationSeconds > 0) {
            averagePowerWatts = totalITEnergyJoules / simulationDurationSeconds;
        }
    }

    /**
     * Calculates energy metrics for each datacenter.
     */
    private void calculateDatacenterEnergy(List<CloudDatacenter> datacenters) {
        for (CloudDatacenter dc : datacenters) {
            double dcEnergy = dc.getTotalEnergyConsumed();
            datacenterEnergyJoules.put(dc.getName(), dcEnergy);

            // Calculate peak power for datacenter (sum of host peaks)
            double dcPeakPower = dc.getHosts().stream()
                .mapToDouble(Host::getPeakTotalPowerDraw)
                .sum();
            datacenterPeakPowerWatts.put(dc.getName(), dcPeakPower);
        }
    }

    /**
     * Calculates derived metrics (carbon, cost, efficiency).
     */
    private void calculateDerivedMetrics() {
        // Convert to kWh for cost and carbon calculations
        double totalFacilityKWh = totalFacilityEnergyJoules / 3_600_000.0;

        // Carbon footprint
        carbonFootprintKg = totalFacilityKWh * carbonIntensity;

        // Electricity cost
        estimatedCostDollars = totalFacilityKWh * electricityCostPerKWh;

        // Energy per task
        if (tasksCompleted > 0) {
            energyPerTaskJoules = totalFacilityEnergyJoules / tasksCompleted;
        }

        // Energy efficiency (instructions per Watt)
        if (totalITEnergyJoules > 0 && simulationDurationSeconds > 0) {
            double avgPower = totalITEnergyJoules / simulationDurationSeconds;
            if (avgPower > 0) {
                energyEfficiencyIpsPerWatt = (double) totalInstructionsExecuted /
                    (avgPower * simulationDurationSeconds);
            }
        }
    }

    /**
     * Logs a summary of energy calculations.
     */
    private void logSummary() {
        logInfo("Energy Calculation Complete");
        logInfo(String.format("  Simulation duration: %d seconds", simulationDurationSeconds));
        logInfo(String.format("  IT Equipment Energy: %.2f kWh (%.0f Joules)",
            getTotalITEnergyKWh(), totalITEnergyJoules));
        logInfo(String.format("  Total Facility Energy (PUE=%.2f): %.2f kWh",
            pue, getTotalFacilityEnergyKWh()));
        logInfo(String.format("  Average Power Draw: %.2f W", averagePowerWatts));
        logInfo(String.format("  Peak Power Draw: %.2f W", peakTotalPowerWatts));
        logInfo(String.format("  Carbon Footprint (%s): %.4f kg CO2",
            selectedRegion.name(), carbonFootprintKg));
        logInfo(String.format("  Estimated Cost ($%.4f/kWh): $%.4f",
            electricityCostPerKWh, estimatedCostDollars));
        if (tasksCompleted > 0) {
            logInfo(String.format("  Energy per Task: %.2f Joules", energyPerTaskJoules));
        }
        if (energyEfficiencyIpsPerWatt > 0) {
            logInfo(String.format("  Energy Efficiency: %.2e IPS/Watt", energyEfficiencyIpsPerWatt));
        }

        // Per-datacenter breakdown
        if (!datacenterEnergyJoules.isEmpty()) {
            logInfo("  Per-Datacenter Energy:");
            for (Map.Entry<String, Double> entry : datacenterEnergyJoules.entrySet()) {
                double dcKWh = entry.getValue() / 3_600_000.0;
                logInfo(String.format("    %s: %.4f kWh", entry.getKey(), dcKWh));
            }
        }
    }

    /**
     * Records energy metrics to the simulation context.
     */
    private void recordMetrics(SimulationContext context) {
        // Total energy
        context.recordMetric("energy.totalITEnergyJoules", totalITEnergyJoules);
        context.recordMetric("energy.totalITEnergyKWh", getTotalITEnergyKWh());
        context.recordMetric("energy.totalFacilityEnergyJoules", totalFacilityEnergyJoules);
        context.recordMetric("energy.totalFacilityEnergyKWh", getTotalFacilityEnergyKWh());

        // Component breakdown
        context.recordMetric("energy.cpuEnergyJoules", totalCpuEnergyJoules);
        context.recordMetric("energy.gpuEnergyJoules", totalGpuEnergyJoules);
        context.recordMetric("energy.otherComponentsEnergyJoules", totalOtherComponentsEnergyJoules);

        // Power
        context.recordMetric("energy.averagePowerWatts", averagePowerWatts);
        context.recordMetric("energy.peakPowerWatts", peakTotalPowerWatts);

        // Configuration
        context.recordMetric("energy.pue", pue);
        context.recordMetric("energy.carbonIntensityKgPerKWh", carbonIntensity);
        context.recordMetric("energy.carbonRegion", selectedRegion.name());
        context.recordMetric("energy.electricityCostPerKWh", electricityCostPerKWh);

        // Derived metrics
        context.recordMetric("energy.carbonFootprintKg", carbonFootprintKg);
        context.recordMetric("energy.estimatedCostDollars", estimatedCostDollars);
        context.recordMetric("energy.energyPerTaskJoules", energyPerTaskJoules);
        context.recordMetric("energy.energyEfficiencyIpsPerWatt", energyEfficiencyIpsPerWatt);

        // Per-datacenter
        for (Map.Entry<String, Double> entry : datacenterEnergyJoules.entrySet()) {
            context.recordMetric("energy.datacenter." + entry.getKey() + ".energyJoules", entry.getValue());
            context.recordMetric("energy.datacenter." + entry.getKey() + ".energyKWh",
                entry.getValue() / 3_600_000.0);
        }
        for (Map.Entry<String, Double> entry : datacenterPeakPowerWatts.entrySet()) {
            context.recordMetric("energy.datacenter." + entry.getKey() + ".peakPowerWatts", entry.getValue());
        }

        // Per-host
        for (Map.Entry<Long, Double> entry : hostEnergyJoules.entrySet()) {
            context.recordMetric("energy.host." + entry.getKey() + ".energyJoules", entry.getValue());
        }
    }

    private void logInfo(String message) {
        System.out.println("[INFO] EnergyCalculationStep: " + message);
    }

    @Override
    public String getStepName() {
        return "Energy Calculation";
    }

    // Configuration setters

    /**
     * Sets the Power Usage Effectiveness (PUE) for the datacenter.
     * PUE = Total Facility Energy / IT Equipment Energy
     * Typical values: 1.2 (efficient) to 2.0 (inefficient)
     */
    public void setPUE(double pue) {
        if (pue < 1.0) {
            throw new IllegalArgumentException("PUE must be >= 1.0");
        }
        this.pue = pue;
    }

    /**
     * Sets the carbon intensity using a predefined region.
     */
    public void setCarbonIntensity(CarbonIntensityRegion region) {
        this.selectedRegion = region;
        this.carbonIntensity = region.getKgCO2PerKWh();
    }

    /**
     * Sets a custom carbon intensity value (kg CO2 per kWh).
     */
    public void setCarbonIntensityKgPerKWh(double kgCO2PerKWh) {
        this.carbonIntensity = kgCO2PerKWh;
    }

    /**
     * Sets the electricity cost per kWh in dollars.
     */
    public void setElectricityCostPerKWh(double costPerKWh) {
        this.electricityCostPerKWh = costPerKWh;
    }

    // Getters

    public double getPUE() {
        return pue;
    }

    public double getCarbonIntensity() {
        return carbonIntensity;
    }

    public CarbonIntensityRegion getSelectedRegion() {
        return selectedRegion;
    }

    public double getElectricityCostPerKWh() {
        return electricityCostPerKWh;
    }

    public double getTotalITEnergyJoules() {
        return totalITEnergyJoules;
    }

    public double getTotalITEnergyKWh() {
        return totalITEnergyJoules / 3_600_000.0;
    }

    public double getTotalFacilityEnergyJoules() {
        return totalFacilityEnergyJoules;
    }

    public double getTotalFacilityEnergyKWh() {
        return totalFacilityEnergyJoules / 3_600_000.0;
    }

    public double getTotalCpuEnergyJoules() {
        return totalCpuEnergyJoules;
    }

    public double getTotalGpuEnergyJoules() {
        return totalGpuEnergyJoules;
    }

    public double getAveragePowerWatts() {
        return averagePowerWatts;
    }

    public double getPeakTotalPowerWatts() {
        return peakTotalPowerWatts;
    }

    public double getCarbonFootprintKg() {
        return carbonFootprintKg;
    }

    public double getEstimatedCostDollars() {
        return estimatedCostDollars;
    }

    public double getEnergyPerTaskJoules() {
        return energyPerTaskJoules;
    }

    public double getEnergyEfficiencyIpsPerWatt() {
        return energyEfficiencyIpsPerWatt;
    }

    public Map<String, Double> getDatacenterEnergyJoules() {
        return datacenterEnergyJoules;
    }

    public Map<String, Double> getDatacenterPeakPowerWatts() {
        return datacenterPeakPowerWatts;
    }

    public Map<Long, Double> getHostEnergyJoules() {
        return hostEnergyJoules;
    }

    public long getSimulationDurationSeconds() {
        return simulationDurationSeconds;
    }

    public long getTotalInstructionsExecuted() {
        return totalInstructionsExecuted;
    }

    public int getTasksCompleted() {
        return tasksCompleted;
    }
}
