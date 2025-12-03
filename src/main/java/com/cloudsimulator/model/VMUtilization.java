package com.cloudsimulator.model;

import com.cloudsimulator.enums.WorkloadType;

/**
 * Represents the utilization state of a VM at a specific point in time.
 * Tracks the workload being executed and resource utilization.
 */
public class VMUtilization {
    private long workloadLength;          // Total length/duration of workload (seconds)
    private long workloadProgress;        // Current progress in workload execution (seconds)
    private WorkloadType activeWorkloadType;
    private double cpuUtilization;        // CPU utilization (0.0 to 1.0)
    private double gpuUtilization;        // GPU utilization (0.0 to 1.0)
    private double powerDrawWatts;        // Power draw for this workload (Watts)

    /**
     * Full constructor.
     */
    public VMUtilization(long workloadLength, long workloadProgress,
                        WorkloadType activeWorkloadType,
                        double cpuUtilization, double gpuUtilization,
                        double powerDrawWatts) {
        this.workloadLength = workloadLength;
        this.workloadProgress = workloadProgress;
        this.activeWorkloadType = activeWorkloadType;
        this.cpuUtilization = cpuUtilization;
        this.gpuUtilization = gpuUtilization;
        this.powerDrawWatts = powerDrawWatts;
    }

    /**
     * Constructor for starting a new workload (progress = 0).
     */
    public VMUtilization(long workloadLength, WorkloadType activeWorkloadType,
                        double cpuUtilization, double gpuUtilization,
                        double powerDrawWatts) {
        this(workloadLength, 0, activeWorkloadType, cpuUtilization, gpuUtilization, powerDrawWatts);
    }

    /**
     * Constructor for IDLE state.
     */
    public VMUtilization() {
        this(0, 0, WorkloadType.IDLE, 0.0, 0.0, 0.0);
    }

    /**
     * Increments the workload progress by one second.
     */
    public void incrementProgress() {
        if (workloadProgress < workloadLength) {
            workloadProgress++;
        }
    }

    /**
     * Checks if the workload is complete.
     */
    public boolean isWorkloadComplete() {
        return workloadProgress >= workloadLength;
    }

    /**
     * Gets the remaining workload time in seconds.
     */
    public long getRemainingWorkloadTime() {
        return Math.max(0, workloadLength - workloadProgress);
    }

    /**
     * Resets the utilization to IDLE state.
     */
    public void resetToIdle() {
        this.workloadLength = 0;
        this.workloadProgress = 0;
        this.activeWorkloadType = WorkloadType.IDLE;
        this.cpuUtilization = 0.0;
        this.gpuUtilization = 0.0;
        this.powerDrawWatts = 0.0;
    }

    // Getters and Setters

    public long getWorkloadLength() {
        return workloadLength;
    }

    public void setWorkloadLength(long workloadLength) {
        this.workloadLength = workloadLength;
    }

    public long getWorkloadProgress() {
        return workloadProgress;
    }

    public void setWorkloadProgress(long workloadProgress) {
        this.workloadProgress = workloadProgress;
    }

    public WorkloadType getActiveWorkloadType() {
        return activeWorkloadType;
    }

    public void setActiveWorkloadType(WorkloadType activeWorkloadType) {
        this.activeWorkloadType = activeWorkloadType;
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

    public double getPowerDrawWatts() {
        return powerDrawWatts;
    }

    public void setPowerDrawWatts(double powerDrawWatts) {
        this.powerDrawWatts = powerDrawWatts;
    }

    @Override
    public String toString() {
        return "VMUtilization{" +
                "workloadLength=" + workloadLength +
                ", workloadProgress=" + workloadProgress +
                ", activeWorkloadType=" + activeWorkloadType +
                ", cpuUtilization=" + cpuUtilization +
                ", gpuUtilization=" + gpuUtilization +
                ", powerDrawWatts=" + powerDrawWatts +
                '}';
    }
}
