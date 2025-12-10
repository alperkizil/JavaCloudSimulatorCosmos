package com.cloudsimulator.model;

import com.cloudsimulator.enums.WorkloadType;

/**
 * Represents empirical power consumption measurements for a specific workload type.
 * Data is derived from actual wall-plug measurements on real hardware.
 *
 * Reference System: Dell Precision 7920 Workstation with Nvidia 5080 GPU
 * Measurement Device: WellHise PM004 Power Meter
 *
 * The incremental power represents the additional power draw above the system's
 * idle baseline when running the workload at typical utilization levels.
 */
public class EmpiricalWorkloadProfile {

    private final WorkloadType workloadType;

    // Power measurements (in Watts)
    private final double incrementalPowerWatts;    // Power above idle baseline
    private final double peakPowerWatts;           // Maximum observed power
    private final double lowestPowerWatts;         // Minimum observed power during workload
    private final double averagePowerWatts;        // Average total power (including idle)

    // Typical utilization during measurement
    private final double typicalCpuUtilization;    // 0.0 to 1.0
    private final double typicalGpuUtilization;    // 0.0 to 1.0

    // Throughput and efficiency metrics
    private final double energyPerUnitJoules;      // Energy per work unit (J/unit)
    private final String throughputDescription;    // Human-readable throughput

    // Measurement metadata
    private final String measurementNotes;

    /**
     * Full constructor with all measurement parameters.
     */
    public EmpiricalWorkloadProfile(
            WorkloadType workloadType,
            double incrementalPowerWatts,
            double peakPowerWatts,
            double lowestPowerWatts,
            double averagePowerWatts,
            double typicalCpuUtilization,
            double typicalGpuUtilization,
            double energyPerUnitJoules,
            String throughputDescription,
            String measurementNotes) {
        this.workloadType = workloadType;
        this.incrementalPowerWatts = incrementalPowerWatts;
        this.peakPowerWatts = peakPowerWatts;
        this.lowestPowerWatts = lowestPowerWatts;
        this.averagePowerWatts = averagePowerWatts;
        this.typicalCpuUtilization = typicalCpuUtilization;
        this.typicalGpuUtilization = typicalGpuUtilization;
        this.energyPerUnitJoules = energyPerUnitJoules;
        this.throughputDescription = throughputDescription;
        this.measurementNotes = measurementNotes;
    }

    /**
     * Simplified constructor for basic power measurements.
     */
    public EmpiricalWorkloadProfile(
            WorkloadType workloadType,
            double incrementalPowerWatts,
            double peakPowerWatts,
            double typicalCpuUtilization,
            double typicalGpuUtilization) {
        this(workloadType, incrementalPowerWatts, peakPowerWatts, 0.0, 0.0,
             typicalCpuUtilization, typicalGpuUtilization, 0.0, "", "");
    }

    /**
     * Calculates power draw at a given utilization level.
     * Scales the incremental power based on the utilization ratio compared to typical.
     *
     * @param cpuUtilization Current CPU utilization (0.0 to 1.0)
     * @param gpuUtilization Current GPU utilization (0.0 to 1.0)
     * @return Incremental power in Watts (above idle baseline)
     */
    public double calculateIncrementalPower(double cpuUtilization, double gpuUtilization) {
        // Calculate utilization ratio compared to typical measurement conditions
        double cpuRatio = typicalCpuUtilization > 0 ? cpuUtilization / typicalCpuUtilization : 0.0;
        double gpuRatio = typicalGpuUtilization > 0 ? gpuUtilization / typicalGpuUtilization : 0.0;

        // Determine the dominant component based on workload type
        double utilizationRatio;
        if (typicalGpuUtilization > typicalCpuUtilization) {
            // GPU-dominant workload
            utilizationRatio = gpuRatio;
        } else if (typicalCpuUtilization > 0) {
            // CPU-dominant workload
            utilizationRatio = cpuRatio;
        } else {
            // IDLE workload
            utilizationRatio = 0.0;
        }

        // Clamp ratio to reasonable bounds (0.0 to 1.5 for overutilization)
        utilizationRatio = Math.max(0.0, Math.min(1.5, utilizationRatio));

        return incrementalPowerWatts * utilizationRatio;
    }

    /**
     * Calculates power draw using a simpler linear model.
     * Power scales linearly with combined utilization.
     *
     * @param cpuUtilization Current CPU utilization (0.0 to 1.0)
     * @param gpuUtilization Current GPU utilization (0.0 to 1.0)
     * @return Incremental power in Watts
     */
    public double calculateIncrementalPowerLinear(double cpuUtilization, double gpuUtilization) {
        // Use max utilization for scaling (whichever component is more active)
        double effectiveUtilization = Math.max(cpuUtilization, gpuUtilization);
        return incrementalPowerWatts * effectiveUtilization;
    }

    // Getters

    public WorkloadType getWorkloadType() {
        return workloadType;
    }

    public double getIncrementalPowerWatts() {
        return incrementalPowerWatts;
    }

    public double getPeakPowerWatts() {
        return peakPowerWatts;
    }

    public double getLowestPowerWatts() {
        return lowestPowerWatts;
    }

    public double getAveragePowerWatts() {
        return averagePowerWatts;
    }

    public double getTypicalCpuUtilization() {
        return typicalCpuUtilization;
    }

    public double getTypicalGpuUtilization() {
        return typicalGpuUtilization;
    }

    public double getEnergyPerUnitJoules() {
        return energyPerUnitJoules;
    }

    public String getThroughputDescription() {
        return throughputDescription;
    }

    public String getMeasurementNotes() {
        return measurementNotes;
    }

    /**
     * Returns whether this is a GPU-intensive workload.
     */
    public boolean isGpuIntensive() {
        return typicalGpuUtilization > typicalCpuUtilization;
    }

    /**
     * Returns whether this is a CPU-intensive workload.
     */
    public boolean isCpuIntensive() {
        return typicalCpuUtilization > typicalGpuUtilization;
    }

    @Override
    public String toString() {
        return String.format("EmpiricalWorkloadProfile{workload=%s, incrementalPower=%.2fW, " +
                "peakPower=%.2fW, cpuUtil=%.0f%%, gpuUtil=%.0f%%}",
                workloadType, incrementalPowerWatts, peakPowerWatts,
                typicalCpuUtilization * 100, typicalGpuUtilization * 100);
    }
}
