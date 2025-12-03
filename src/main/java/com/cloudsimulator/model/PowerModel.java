package com.cloudsimulator.model;

import com.cloudsimulator.enums.WorkloadType;

/**
 * PowerModel calculates the power consumption of a Host based on its utilization.
 * It considers CPU utilization, GPU utilization, and other components.
 */
public class PowerModel {
    // Power consumption parameters
    private double maxCpuPower;           // Max power for CPU at 100% utilization (Watts)
    private double maxGpuPower;           // Max power for GPU at 100% utilization (Watts)
    private double idleCpuPower;          // Idle power for CPU at 0% utilization (Watts)
    private double idleGpuPower;          // Idle power for GPU at 0% utilization (Watts)
    private double otherComponentsPower;  // Power for other components (RAM, storage, etc.) (Watts)

    /**
     * Constructor with custom power parameters.
     */
    public PowerModel(double maxCpuPower, double maxGpuPower,
                     double idleCpuPower, double idleGpuPower,
                     double otherComponentsPower) {
        this.maxCpuPower = maxCpuPower;
        this.maxGpuPower = maxGpuPower;
        this.idleCpuPower = idleCpuPower;
        this.idleGpuPower = idleGpuPower;
        this.otherComponentsPower = otherComponentsPower;
    }

    /**
     * Default constructor with typical server values.
     * Assumes a typical server with moderate power consumption.
     */
    public PowerModel() {
        this(300.0, 250.0, 50.0, 30.0, 100.0);
    }

    /**
     * Calculates CPU power draw based on utilization percentage.
     * Uses a linear power model.
     *
     * @param cpuUtilization CPU utilization (0.0 to 1.0)
     * @return Power draw in Watts
     */
    public double calculateCpuPower(double cpuUtilization) {
        if (cpuUtilization < 0.0 || cpuUtilization > 1.0) {
            throw new IllegalArgumentException("CPU utilization must be between 0.0 and 1.0");
        }
        return idleCpuPower + (maxCpuPower - idleCpuPower) * cpuUtilization;
    }

    /**
     * Calculates GPU power draw based on utilization percentage.
     * Uses a linear power model.
     *
     * @param gpuUtilization GPU utilization (0.0 to 1.0)
     * @return Power draw in Watts
     */
    public double calculateGpuPower(double gpuUtilization) {
        if (gpuUtilization < 0.0 || gpuUtilization > 1.0) {
            throw new IllegalArgumentException("GPU utilization must be between 0.0 and 1.0");
        }
        return idleGpuPower + (maxGpuPower - idleGpuPower) * gpuUtilization;
    }

    /**
     * Calculates total power draw for a host given CPU and GPU utilization.
     *
     * @param cpuUtilization CPU utilization (0.0 to 1.0)
     * @param gpuUtilization GPU utilization (0.0 to 1.0)
     * @return Total power draw in Watts
     */
    public double calculateTotalPower(double cpuUtilization, double gpuUtilization) {
        double cpuPower = calculateCpuPower(cpuUtilization);
        double gpuPower = calculateGpuPower(gpuUtilization);
        return cpuPower + gpuPower + otherComponentsPower;
    }

    /**
     * Calculates power draw for a specific workload type.
     * Different workloads have different power characteristics.
     *
     * @param workloadType The type of workload being executed
     * @param cpuUtilization CPU utilization (0.0 to 1.0)
     * @param gpuUtilization GPU utilization (0.0 to 1.0)
     * @return Power draw in Watts
     */
    public double calculateWorkloadPower(WorkloadType workloadType,
                                         double cpuUtilization,
                                         double gpuUtilization) {
        return calculateTotalPower(cpuUtilization, gpuUtilization);
    }

    // Getters and Setters

    public double getMaxCpuPower() {
        return maxCpuPower;
    }

    public void setMaxCpuPower(double maxCpuPower) {
        this.maxCpuPower = maxCpuPower;
    }

    public double getMaxGpuPower() {
        return maxGpuPower;
    }

    public void setMaxGpuPower(double maxGpuPower) {
        this.maxGpuPower = maxGpuPower;
    }

    public double getIdleCpuPower() {
        return idleCpuPower;
    }

    public void setIdleCpuPower(double idleCpuPower) {
        this.idleCpuPower = idleCpuPower;
    }

    public double getIdleGpuPower() {
        return idleGpuPower;
    }

    public void setIdleGpuPower(double idleGpuPower) {
        this.idleGpuPower = idleGpuPower;
    }

    public double getOtherComponentsPower() {
        return otherComponentsPower;
    }

    public void setOtherComponentsPower(double otherComponentsPower) {
        this.otherComponentsPower = otherComponentsPower;
    }

    @Override
    public String toString() {
        return "PowerModel{" +
                "maxCpuPower=" + maxCpuPower +
                ", maxGpuPower=" + maxGpuPower +
                ", idleCpuPower=" + idleCpuPower +
                ", idleGpuPower=" + idleGpuPower +
                ", otherComponentsPower=" + otherComponentsPower +
                '}';
    }
}
