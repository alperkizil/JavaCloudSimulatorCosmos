package com.cloudsimulator.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SimulationSummary is a comprehensive data container for all simulation results.
 * Designed to be easily serialized to JSON for external tools and reporting.
 *
 * This class aggregates metrics from all simulation steps into a structured format
 * suitable for analysis, visualization, and export.
 *
 * Usage:
 * <pre>
 * SimulationSummary summary = new SimulationSummary();
 * // ... populate summary ...
 *
 * // Convert to JSON (using any JSON library)
 * String json = new Gson().toJson(summary);
 * </pre>
 */
public class SimulationSummary {

    // Metadata
    private String simulationId;
    private String timestamp;
    private long randomSeed;
    private String configurationFile;

    // Simulation timing
    private long simulationDurationSeconds;
    private long wallClockDurationMs;

    // Infrastructure summary
    private InfrastructureSummary infrastructure;

    // Task summary
    private TaskSummary tasks;

    // Energy summary
    private EnergySummary energy;

    // Performance summary
    private PerformanceSummary performance;

    // SLA compliance
    private SLASummary sla;

    // Per-entity details
    private List<DatacenterSummary> datacenters;
    private List<HostSummary> hosts;
    private List<UserSummary> users;
    private List<WorkloadSummary> workloads;

    // Raw metrics map (for custom queries)
    private Map<String, Object> rawMetrics;

    public SimulationSummary() {
        this.timestamp = Instant.now().toString();
        this.infrastructure = new InfrastructureSummary();
        this.tasks = new TaskSummary();
        this.energy = new EnergySummary();
        this.performance = new PerformanceSummary();
        this.sla = new SLASummary();
        this.datacenters = new ArrayList<>();
        this.hosts = new ArrayList<>();
        this.users = new ArrayList<>();
        this.workloads = new ArrayList<>();
        this.rawMetrics = new HashMap<>();
    }

    /**
     * Converts this summary to a simple JSON string representation.
     * For production use, consider using a proper JSON library (Gson, Jackson).
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Metadata
        sb.append("  \"simulationId\": ").append(quote(simulationId)).append(",\n");
        sb.append("  \"timestamp\": ").append(quote(timestamp)).append(",\n");
        sb.append("  \"randomSeed\": ").append(randomSeed).append(",\n");
        sb.append("  \"configurationFile\": ").append(quote(configurationFile)).append(",\n");
        sb.append("  \"simulationDurationSeconds\": ").append(simulationDurationSeconds).append(",\n");
        sb.append("  \"wallClockDurationMs\": ").append(wallClockDurationMs).append(",\n");

        // Infrastructure
        sb.append("  \"infrastructure\": ").append(infrastructure.toJson()).append(",\n");

        // Tasks
        sb.append("  \"tasks\": ").append(tasks.toJson()).append(",\n");

        // Energy
        sb.append("  \"energy\": ").append(energy.toJson()).append(",\n");

        // Performance
        sb.append("  \"performance\": ").append(performance.toJson()).append(",\n");

        // SLA
        sb.append("  \"sla\": ").append(sla.toJson()).append(",\n");

        // Datacenters
        sb.append("  \"datacenters\": [\n");
        for (int i = 0; i < datacenters.size(); i++) {
            sb.append("    ").append(datacenters.get(i).toJson());
            if (i < datacenters.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Users (abbreviated)
        sb.append("  \"userCount\": ").append(users.size()).append(",\n");
        sb.append("  \"hostCount\": ").append(hosts.size()).append(",\n");
        sb.append("  \"workloadCount\": ").append(workloads.size()).append("\n");

        sb.append("}");
        return sb.toString();
    }

    private String quote(String s) {
        return s == null ? "null" : "\"" + s.replace("\"", "\\\"") + "\"";
    }

    // Nested classes for structured data

    /**
     * Infrastructure metrics summary.
     */
    public static class InfrastructureSummary {
        public int datacenterCount;
        public int hostCount;
        public int activeHostCount;
        public int idleHostCount;
        public int vmCount;
        public int userCount;

        // Resource totals
        public int totalCpuCores;
        public int totalGpus;
        public long totalRamMB;
        public long totalStorageMB;

        // Resource utilization
        public double avgCpuUtilization;
        public double avgGpuUtilization;
        public double avgRamUtilization;
        public double peakCpuUtilization;
        public double peakGpuUtilization;

        // Consolidation
        public double vmsPerActiveHost;
        public double consolidationRatio;

        public String toJson() {
            return String.format(
                "{\"datacenterCount\":%d,\"hostCount\":%d,\"activeHostCount\":%d," +
                "\"vmCount\":%d,\"userCount\":%d,\"totalCpuCores\":%d,\"totalGpus\":%d," +
                "\"avgCpuUtilization\":%.4f,\"avgGpuUtilization\":%.4f,\"vmsPerActiveHost\":%.2f}",
                datacenterCount, hostCount, activeHostCount, vmCount, userCount,
                totalCpuCores, totalGpus, avgCpuUtilization, avgGpuUtilization, vmsPerActiveHost
            );
        }
    }

    /**
     * Task metrics summary.
     */
    public static class TaskSummary {
        public int totalTasks;
        public int completedTasks;
        public int failedTasks;
        public int unassignedTasks;

        public double completionRate;
        public long totalInstructionsExecuted;

        public String toJson() {
            return String.format(
                "{\"totalTasks\":%d,\"completedTasks\":%d,\"failedTasks\":%d," +
                "\"unassignedTasks\":%d,\"completionRate\":%.4f,\"totalInstructionsExecuted\":%d}",
                totalTasks, completedTasks, failedTasks, unassignedTasks,
                completionRate, totalInstructionsExecuted
            );
        }
    }

    /**
     * Energy metrics summary.
     */
    public static class EnergySummary {
        public double totalITEnergyKWh;
        public double totalFacilityEnergyKWh;
        public double cpuEnergyKWh;
        public double gpuEnergyKWh;

        public double pue;
        public double averagePowerWatts;
        public double peakPowerWatts;

        public String carbonRegion;
        public double carbonIntensityKgPerKWh;
        public double carbonFootprintKg;

        public double electricityCostPerKWh;
        public double estimatedCostDollars;

        public double energyPerTaskJoules;
        public double energyEfficiencyIpsPerWatt;

        public String toJson() {
            return String.format(
                "{\"totalITEnergyKWh\":%.6f,\"totalFacilityEnergyKWh\":%.6f," +
                "\"pue\":%.2f,\"averagePowerWatts\":%.2f,\"peakPowerWatts\":%.2f," +
                "\"carbonRegion\":\"%s\",\"carbonFootprintKg\":%.6f," +
                "\"estimatedCostDollars\":%.4f,\"energyPerTaskJoules\":%.2f}",
                totalITEnergyKWh, totalFacilityEnergyKWh, pue, averagePowerWatts, peakPowerWatts,
                carbonRegion != null ? carbonRegion : "UNKNOWN", carbonFootprintKg,
                estimatedCostDollars, energyPerTaskJoules
            );
        }
    }

    /**
     * Performance metrics summary.
     */
    public static class PerformanceSummary {
        public long makespanSeconds;
        public double throughputTasksPerSecond;

        public double avgWaitingTimeSeconds;
        public double avgTurnaroundTimeSeconds;
        public double avgExecutionTimeSeconds;

        public double minWaitingTimeSeconds;
        public double maxWaitingTimeSeconds;
        public double minTurnaroundTimeSeconds;
        public double maxTurnaroundTimeSeconds;

        // Percentiles
        public double p50TurnaroundTimeSeconds;
        public double p90TurnaroundTimeSeconds;
        public double p99TurnaroundTimeSeconds;

        // Load balance
        public double loadBalanceIndex;
        public double taskDistributionStdDev;

        public String toJson() {
            return String.format(
                "{\"makespanSeconds\":%d,\"throughputTasksPerSecond\":%.6f," +
                "\"avgWaitingTimeSeconds\":%.2f,\"avgTurnaroundTimeSeconds\":%.2f," +
                "\"avgExecutionTimeSeconds\":%.2f,\"p90TurnaroundTimeSeconds\":%.2f," +
                "\"p99TurnaroundTimeSeconds\":%.2f,\"loadBalanceIndex\":%.4f}",
                makespanSeconds, throughputTasksPerSecond, avgWaitingTimeSeconds,
                avgTurnaroundTimeSeconds, avgExecutionTimeSeconds,
                p90TurnaroundTimeSeconds, p99TurnaroundTimeSeconds, loadBalanceIndex
            );
        }
    }

    /**
     * SLA compliance summary.
     */
    public static class SLASummary {
        public long slaThresholdSeconds;
        public int tasksWithinSLA;
        public int tasksBeyondSLA;
        public double slaCompliancePercent;

        // Multiple thresholds
        public Map<Long, Double> complianceByThreshold;

        public SLASummary() {
            this.complianceByThreshold = new HashMap<>();
        }

        public String toJson() {
            StringBuilder thresholds = new StringBuilder("[");
            int i = 0;
            for (Map.Entry<Long, Double> entry : complianceByThreshold.entrySet()) {
                if (i > 0) thresholds.append(",");
                thresholds.append(String.format("{\"threshold\":%d,\"compliance\":%.2f}",
                    entry.getKey(), entry.getValue()));
                i++;
            }
            thresholds.append("]");

            return String.format(
                "{\"slaThresholdSeconds\":%d,\"tasksWithinSLA\":%d,\"tasksBeyondSLA\":%d," +
                "\"slaCompliancePercent\":%.2f,\"complianceByThreshold\":%s}",
                slaThresholdSeconds, tasksWithinSLA, tasksBeyondSLA,
                slaCompliancePercent, thresholds.toString()
            );
        }
    }

    /**
     * Per-datacenter summary.
     */
    public static class DatacenterSummary {
        public String name;
        public int hostCount;
        public int vmCount;
        public int userCount;
        public double energyKWh;
        public double peakPowerWatts;
        public double avgUtilization;

        public String toJson() {
            return String.format(
                "{\"name\":\"%s\",\"hostCount\":%d,\"vmCount\":%d,\"userCount\":%d," +
                "\"energyKWh\":%.6f,\"peakPowerWatts\":%.2f,\"avgUtilization\":%.4f}",
                name, hostCount, vmCount, userCount, energyKWh, peakPowerWatts, avgUtilization
            );
        }
    }

    /**
     * Per-host summary.
     */
    public static class HostSummary {
        public long id;
        public String datacenterName;
        public int vmCount;
        public int cpuCores;
        public int gpus;
        public double cpuUtilization;
        public double gpuUtilization;
        public double energyKWh;
        public long activeSeconds;
        public long idleSeconds;

        public String toJson() {
            return String.format(
                "{\"id\":%d,\"datacenterName\":\"%s\",\"vmCount\":%d,\"cpuCores\":%d," +
                "\"cpuUtilization\":%.4f,\"energyKWh\":%.6f,\"activeSeconds\":%d}",
                id, datacenterName != null ? datacenterName : "", vmCount, cpuCores,
                cpuUtilization, energyKWh, activeSeconds
            );
        }
    }

    /**
     * Per-user summary.
     */
    public static class UserSummary {
        public String name;
        public int vmCount;
        public int taskCount;
        public int completedTasks;
        public double completionRate;
        public double avgTurnaroundTimeSeconds;
        public long sessionDurationSeconds;

        public String toJson() {
            return String.format(
                "{\"name\":\"%s\",\"vmCount\":%d,\"taskCount\":%d,\"completedTasks\":%d," +
                "\"completionRate\":%.4f,\"avgTurnaroundTimeSeconds\":%.2f}",
                name, vmCount, taskCount, completedTasks, completionRate, avgTurnaroundTimeSeconds
            );
        }
    }

    /**
     * Per-workload type summary.
     */
    public static class WorkloadSummary {
        public String workloadType;
        public int taskCount;
        public int completedTasks;
        public double avgExecutionTimeSeconds;
        public long totalInstructions;

        public String toJson() {
            return String.format(
                "{\"workloadType\":\"%s\",\"taskCount\":%d,\"completedTasks\":%d," +
                "\"avgExecutionTimeSeconds\":%.2f,\"totalInstructions\":%d}",
                workloadType, taskCount, completedTasks, avgExecutionTimeSeconds, totalInstructions
            );
        }
    }

    // Getters and setters

    public String getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public String getConfigurationFile() {
        return configurationFile;
    }

    public void setConfigurationFile(String configurationFile) {
        this.configurationFile = configurationFile;
    }

    public long getSimulationDurationSeconds() {
        return simulationDurationSeconds;
    }

    public void setSimulationDurationSeconds(long simulationDurationSeconds) {
        this.simulationDurationSeconds = simulationDurationSeconds;
    }

    public long getWallClockDurationMs() {
        return wallClockDurationMs;
    }

    public void setWallClockDurationMs(long wallClockDurationMs) {
        this.wallClockDurationMs = wallClockDurationMs;
    }

    public InfrastructureSummary getInfrastructure() {
        return infrastructure;
    }

    public void setInfrastructure(InfrastructureSummary infrastructure) {
        this.infrastructure = infrastructure;
    }

    public TaskSummary getTasks() {
        return tasks;
    }

    public void setTasks(TaskSummary tasks) {
        this.tasks = tasks;
    }

    public EnergySummary getEnergy() {
        return energy;
    }

    public void setEnergy(EnergySummary energy) {
        this.energy = energy;
    }

    public PerformanceSummary getPerformance() {
        return performance;
    }

    public void setPerformance(PerformanceSummary performance) {
        this.performance = performance;
    }

    public SLASummary getSla() {
        return sla;
    }

    public void setSla(SLASummary sla) {
        this.sla = sla;
    }

    public List<DatacenterSummary> getDatacenters() {
        return datacenters;
    }

    public void setDatacenters(List<DatacenterSummary> datacenters) {
        this.datacenters = datacenters;
    }

    public List<HostSummary> getHosts() {
        return hosts;
    }

    public void setHosts(List<HostSummary> hosts) {
        this.hosts = hosts;
    }

    public List<UserSummary> getUsers() {
        return users;
    }

    public void setUsers(List<UserSummary> users) {
        this.users = users;
    }

    public List<WorkloadSummary> getWorkloads() {
        return workloads;
    }

    public void setWorkloads(List<WorkloadSummary> workloads) {
        this.workloads = workloads;
    }

    public Map<String, Object> getRawMetrics() {
        return rawMetrics;
    }

    public void setRawMetrics(Map<String, Object> rawMetrics) {
        this.rawMetrics = rawMetrics;
    }

    public void addRawMetric(String key, Object value) {
        this.rawMetrics.put(key, value);
    }
}
