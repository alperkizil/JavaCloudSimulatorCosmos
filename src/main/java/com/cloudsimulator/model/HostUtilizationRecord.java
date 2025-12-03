package com.cloudsimulator.model;

import com.cloudsimulator.enums.WorkloadType;

/**
 * Records the utilization history for a Host at a specific point in time.
 * This tracks which VMs are running, what workloads they're executing,
 * and the power consumption at that moment.
 */
public class HostUtilizationRecord {
    private long timestamp;              // Simulation second when this record was created
    private long vmId;                   // VM that was running
    private WorkloadType workloadType;   // Type of workload being executed
    private double cpuUtilization;       // CPU utilization (0.0 to 1.0)
    private double gpuUtilization;       // GPU utilization (0.0 to 1.0)
    private double powerDraw;            // Power draw at this moment (Watts)
    private long durationSeconds;        // How many seconds this state lasted

    /**
     * Constructor for creating a utilization record.
     */
    public HostUtilizationRecord(long timestamp, long vmId, WorkloadType workloadType,
                                 double cpuUtilization, double gpuUtilization,
                                 double powerDraw, long durationSeconds) {
        this.timestamp = timestamp;
        this.vmId = vmId;
        this.workloadType = workloadType;
        this.cpuUtilization = cpuUtilization;
        this.gpuUtilization = gpuUtilization;
        this.powerDraw = powerDraw;
        this.durationSeconds = durationSeconds;
    }

    /**
     * Constructor with duration defaulting to 1 second.
     */
    public HostUtilizationRecord(long timestamp, long vmId, WorkloadType workloadType,
                                 double cpuUtilization, double gpuUtilization,
                                 double powerDraw) {
        this(timestamp, vmId, workloadType, cpuUtilization, gpuUtilization, powerDraw, 1);
    }

    // Getters and Setters

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getVmId() {
        return vmId;
    }

    public void setVmId(long vmId) {
        this.vmId = vmId;
    }

    public WorkloadType getWorkloadType() {
        return workloadType;
    }

    public void setWorkloadType(WorkloadType workloadType) {
        this.workloadType = workloadType;
    }

    public double getCpuUtilization() {
        return cpuUtilization;
    }

    public void setCpuUtilization(double cpuUtilization) {
        this.cpuUtilization = cpuUtilization;
    }

    public double getGpuUtilization() {
        return gpuUtilization;
    }

    public void setGpuUtilization(double gpuUtilization) {
        this.gpuUtilization = gpuUtilization;
    }

    public double getPowerDraw() {
        return powerDraw;
    }

    public void setPowerDraw(double powerDraw) {
        this.powerDraw = powerDraw;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    @Override
    public String toString() {
        return "HostUtilizationRecord{" +
                "timestamp=" + timestamp +
                ", vmId=" + vmId +
                ", workloadType=" + workloadType +
                ", cpuUtilization=" + cpuUtilization +
                ", gpuUtilization=" + gpuUtilization +
                ", powerDraw=" + powerDraw +
                ", durationSeconds=" + durationSeconds +
                '}';
    }
}
