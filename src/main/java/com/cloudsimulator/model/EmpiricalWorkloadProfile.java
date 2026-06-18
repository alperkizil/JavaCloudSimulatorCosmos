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
     * Returns this workload's incremental power above idle, in Watts.
     *
     * The value is the empirically measured wall-plug delta at the workload's
     * typical utilization. The simulator always drives a workload at that fixed
     * typical utilization (see {@code VM.calculateUtilization}), so the result is
     * independent of the supplied utilization arguments — they are retained only
     * for the caller API. (The previous ratio/dominant-axis/clamp scaling reduced
     * to x1.0 for every actual call and has been removed; a future
     * variable-utilization or contention model would reintroduce scaling here.)
     *
     * @param cpuUtilization current CPU utilization (currently unused)
     * @param gpuUtilization current GPU utilization (currently unused)
     * @return incremental power in Watts (above idle baseline)
     */
    public double calculateIncrementalPower(double cpuUtilization, double gpuUtilization) {
        return incrementalPowerWatts;
    }

    // Getters

    public double getIncrementalPowerWatts() {
        return incrementalPowerWatts;
    }

    public double getPeakPowerWatts() {
        return peakPowerWatts;
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

    @Override
    public String toString() {
        return String.format("EmpiricalWorkloadProfile{workload=%s, incrementalPower=%.2fW, " +
                "peakPower=%.2fW, cpuUtil=%.0f%%, gpuUtil=%.0f%%}",
                workloadType, incrementalPowerWatts, peakPowerWatts,
                typicalCpuUtilization * 100, typicalGpuUtilization * 100);
    }
}
