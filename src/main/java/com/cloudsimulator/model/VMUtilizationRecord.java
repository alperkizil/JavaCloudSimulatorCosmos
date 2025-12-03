package com.cloudsimulator.model;

import com.cloudsimulator.enums.WorkloadType;

/**
 * Records the utilization history for a VM at a specific point in time.
 * Tracks which Tasks are running, what workloads they're executing,
 * and the power consumption at that moment.
 */
public class VMUtilizationRecord {
    private long timestamp;              // Simulation second when this record was created
    private long taskId;                 // Task that was running
    private WorkloadType workloadType;   // Type of workload being executed
    private double cpuUtilization;       // CPU utilization (0.0 to 1.0)
    private double gpuUtilization;       // GPU utilization (0.0 to 1.0)
    private double powerDraw;            // Power draw at this moment (Watts)
    private long durationSeconds;        // How many seconds this state lasted

    /**
     * Constructor for creating a utilization record.
     */
    public VMUtilizationRecord(long timestamp, long taskId, WorkloadType workloadType,
                               double cpuUtilization, double gpuUtilization,
                               double powerDraw, long durationSeconds) {
        this.timestamp = timestamp;
        this.taskId = taskId;
        this.workloadType = workloadType;
        this.cpuUtilization = cpuUtilization;
        this.gpuUtilization = gpuUtilization;
        this.powerDraw = powerDraw;
        this.durationSeconds = durationSeconds;
    }

    /**
     * Constructor with duration defaulting to 1 second.
     */
    public VMUtilizationRecord(long timestamp, long taskId, WorkloadType workloadType,
                               double cpuUtilization, double gpuUtilization,
                               double powerDraw) {
        this(timestamp, taskId, workloadType, cpuUtilization, gpuUtilization, powerDraw, 1);
    }

    // Getters and Setters

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTaskId() {
        return taskId;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
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
        return "VMUtilizationRecord{" +
                "timestamp=" + timestamp +
                ", taskId=" + taskId +
                ", workloadType=" + workloadType +
                ", cpuUtilization=" + cpuUtilization +
                ", gpuUtilization=" + gpuUtilization +
                ", powerDraw=" + powerDraw +
                ", durationSeconds=" + durationSeconds +
                '}';
    }
}
