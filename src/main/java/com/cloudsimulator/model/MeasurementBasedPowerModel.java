package com.cloudsimulator.model;

import com.cloudsimulator.enums.WorkloadType;

import java.util.EnumMap;
import java.util.Map;

/**
 * A power model based on empirical wall-plug measurements from real hardware.
 *
 * Reference System: Dell Precision 7920 Workstation with Nvidia 5080 GPU
 * Measurement Device: WellHise PM004 Power Meter
 * Measurement Date: October-November 2025
 *
 * This model uses actual measurements to calculate power consumption based on
 * the specific workload type being executed, providing more accurate power
 * estimates than generic utilization-based models.
 *
 * Key Concepts:
 * - Base Idle Power: System power when no workload is running (75.79W measured)
 * - Incremental Power: Additional power above idle for each workload type
 * - Hardware Scale Factor: Adjustment for different hardware configurations
 */
public class MeasurementBasedPowerModel {

    // Model identification
    private final String modelName;

    // Base system power (measured idle power)
    private final double baseIdlePowerWatts;

    // Hardware scaling factor (1.0 = reference system, >1.0 = more powerful, <1.0 = less powerful)
    private double hardwareScaleFactor;

    // Per-workload power profiles
    private final Map<WorkloadType, EmpiricalWorkloadProfile> workloadProfiles;

    // Reference system specifications (Dell Precision 7920 + Nvidia 5080)
    public static final double REFERENCE_IDLE_POWER = 75.79;  // Watts
    public static final String REFERENCE_SYSTEM = "Dell Precision 7920 + Nvidia 5080 GPU";

    /**
     * Creates a MeasurementBasedPowerModel with default reference system values.
     */
    public MeasurementBasedPowerModel() {
        this("MeasurementBasedPowerModel", REFERENCE_IDLE_POWER, 1.0);
    }

    /**
     * Creates a MeasurementBasedPowerModel with a custom hardware scale factor.
     *
     * @param hardwareScaleFactor Scale factor for different hardware (1.0 = reference system)
     */
    public MeasurementBasedPowerModel(double hardwareScaleFactor) {
        this("MeasurementBasedPowerModel", REFERENCE_IDLE_POWER, hardwareScaleFactor);
    }

    /**
     * Creates a MeasurementBasedPowerModel with custom parameters.
     *
     * @param modelName Name identifier for this model
     * @param baseIdlePowerWatts Base idle power consumption
     * @param hardwareScaleFactor Scale factor for different hardware
     */
    public MeasurementBasedPowerModel(String modelName, double baseIdlePowerWatts, double hardwareScaleFactor) {
        this.modelName = modelName;
        this.baseIdlePowerWatts = baseIdlePowerWatts;
        this.hardwareScaleFactor = hardwareScaleFactor;
        this.workloadProfiles = new EnumMap<>(WorkloadType.class);

        // Initialize with empirical measurements from power_log_template_v2.txt
        initializeDefaultProfiles();
    }

    /**
     * Initializes workload profiles with empirical measurements.
     * Data source: power_log_template_v2.txt
     * Reference System: Dell Precision 7920 + Nvidia 5080 GPU
     */
    private void initializeDefaultProfiles() {
        // IDLE - Baseline (no additional power)
        workloadProfiles.put(WorkloadType.IDLE, new EmpiricalWorkloadProfile(
            WorkloadType.IDLE,
            0.0,        // incrementalPower (no additional power above idle)
            126.7,      // peakPower
            70.5,       // lowestPower
            75.79,      // averagePower
            0.0,        // typicalCpuUtilization
            0.0,        // typicalGpuUtilization
            0.0,        // energyPerUnit
            "",         // throughput
            "IDLE Power draw of 1 day measurement"
        ));

        // VERACRYPT - Disk encryption (low CPU, disk-bound)
        workloadProfiles.put(WorkloadType.VERACRYPT, new EmpiricalWorkloadProfile(
            WorkloadType.VERACRYPT,
            19.25,      // incrementalPower
            138.7,      // peakPower
            90.8,       // lowestPower
            95.04,      // averagePower
            0.85,       // typicalCpuUtilization (from VM.java utilization profile)
            0.0,        // typicalGpuUtilization
            0.001185,   // energyPerUnit (J/Kbyte)
            "15.7 MiB/s",
            "Encrypting 32GB Sandisk Flash drive with Camellia Algorithm, Low CPU Usage, NO GPU"
        ));

        // DATABASE (HammerDB) - I/O bound, moderate CPU
        workloadProfiles.put(WorkloadType.DATABASE, new EmpiricalWorkloadProfile(
            WorkloadType.DATABASE,
            39.59,      // incrementalPower
            133.6,      // peakPower
            92.5,       // lowestPower
            115.38,     // averagePower
            0.6,        // typicalCpuUtilization (12% measured, but we use profile value)
            0.0,        // typicalGpuUtilization
            0.13495,    // energyPerUnit (J/transaction)
            "18861 TPM",
            "HammerDB 5.0 with MySQL 8.0 TPROC-Timed 100 Virtual Users, 12% CPU Usage, 100% IO"
        ));

        // SEVEN_ZIP - CPU-intensive compression
        workloadProfiles.put(WorkloadType.SEVEN_ZIP, new EmpiricalWorkloadProfile(
            WorkloadType.SEVEN_ZIP,
            130.29,     // incrementalPower
            215.8,      // peakPower
            99.3,       // lowestPower
            206.08,     // averagePower
            0.8,        // typicalCpuUtilization (100% measured, but profile uses 0.8)
            0.0,        // typicalGpuUtilization
            0.0,        // energyPerUnit (not applicable - continuous benchmark)
            "2569 GIPS",
            "Dictionary size = 96 MB, Nr of Threads = 40, CPU Usage 100%, 100 passes"
        ));

        // CINEBENCH - CPU rendering benchmark
        workloadProfiles.put(WorkloadType.CINEBENCH, new EmpiricalWorkloadProfile(
            WorkloadType.CINEBENCH,
            133.76,     // incrementalPower
            214.1,      // peakPower
            102.8,      // lowestPower
            209.55,     // averagePower
            1.0,        // typicalCpuUtilization
            0.0,        // typicalGpuUtilization
            59747.17,   // energyPerUnit (J/pass)
            "751 Points",
            "CPU Multicore bench, 60 Minutes render time, 40 Threads, High CPU Usage"
        ));

        // PRIME95SmallFFT - CPU stress test
        workloadProfiles.put(WorkloadType.PRIME95SmallFFT, new EmpiricalWorkloadProfile(
            WorkloadType.PRIME95SmallFFT,
            124.21,     // incrementalPower
            203.8,      // peakPower
            197.0,      // lowestPower
            200.0,      // averagePower
            1.0,        // typicalCpuUtilization
            0.0,        // typicalGpuUtilization
            7452.61,    // energyPerUnit (J/30min test)
            "30 min test",
            "SmallFFT - CPU saturating application"
        ));

        // LLM_CPU - Large Language Model inference on CPU
        workloadProfiles.put(WorkloadType.LLM_CPU, new EmpiricalWorkloadProfile(
            WorkloadType.LLM_CPU,
            104.21,     // incrementalPower
            214.1,      // peakPower
            95.6,       // lowestPower
            180.0,      // averagePower
            0.95,       // typicalCpuUtilization
            0.0,        // typicalGpuUtilization
            14693.62,   // energyPerUnit (J/prompt)
            "0.0071 prompts/s",
            "LMStudio OpenAI, 40 CPU Threads, 0/24 GPU memory, KV Cache = Off"
        ));

        // LLM_GPU - Large Language Model inference on GPU
        workloadProfiles.put(WorkloadType.LLM_GPU, new EmpiricalWorkloadProfile(
            WorkloadType.LLM_GPU,
            185.35,     // incrementalPower
            349.5,      // peakPower
            101.1,      // lowestPower
            261.14,     // averagePower
            0.3,        // typicalCpuUtilization
            0.95,       // typicalGpuUtilization
            1788.63,    // energyPerUnit (J/prompt)
            "0.1036 prompts/s",
            "LMStudio OpenAI, 40 CPU Threads, 24/24 GPU memory, KV Cache = On"
        ));

        // IMAGE_GEN_CPU - Image generation on CPU
        workloadProfiles.put(WorkloadType.IMAGE_GEN_CPU, new EmpiricalWorkloadProfile(
            WorkloadType.IMAGE_GEN_CPU,
            90.92,      // incrementalPower
            202.1,      // peakPower
            104.5,      // lowestPower
            166.71,     // averagePower
            0.9,        // typicalCpuUtilization
            0.0,        // typicalGpuUtilization
            172382.33,  // energyPerUnit (J/image)
            "0.0005 images/s",
            "ComfyUI 3.0.73, SDXL 1.0 base model, CPU only"
        ));

        // IMAGE_GEN_GPU - Image generation on GPU
        workloadProfiles.put(WorkloadType.IMAGE_GEN_GPU, new EmpiricalWorkloadProfile(
            WorkloadType.IMAGE_GEN_GPU,
            141.08,     // incrementalPower
            430.0,      // peakPower
            102.8,      // lowestPower
            216.87,     // averagePower
            0.2,        // typicalCpuUtilization
            0.9,        // typicalGpuUtilization
            2341.89,    // energyPerUnit (J/image)
            "0.0602 images/s",
            "ComfyUI 3.0.73, SDXL 1.0 base model, GPU accelerated"
        ));

        // FURMARK - GPU stress test
        workloadProfiles.put(WorkloadType.FURMARK, new EmpiricalWorkloadProfile(
            WorkloadType.FURMARK,
            352.18,     // incrementalPower
            452.3,      // peakPower
            164.4,      // lowestPower
            427.97,     // averagePower
            0.1,        // typicalCpuUtilization
            1.0,        // typicalGpuUtilization
            25181.02,   // energyPerUnit (J/test)
            "0.014 tests/s",
            "Furmark 2.9.0.0, VK + Knot VK, 7680x4320 Res. MSAA=8X"
        ));
    }

    /**
     * Calculates total power consumption for a specific workload.
     *
     * @param workloadType The type of workload being executed
     * @param cpuUtilization Current CPU utilization (0.0 to 1.0)
     * @param gpuUtilization Current GPU utilization (0.0 to 1.0)
     * @return Total power consumption in Watts
     */
    public double calculateTotalPower(WorkloadType workloadType, double cpuUtilization, double gpuUtilization) {
        double incrementalPower = calculateIncrementalPower(workloadType, cpuUtilization, gpuUtilization);
        return (baseIdlePowerWatts + incrementalPower) * hardwareScaleFactor;
    }

    /**
     * Calculates incremental power (above idle) for a specific workload.
     *
     * @param workloadType The type of workload being executed
     * @param cpuUtilization Current CPU utilization (0.0 to 1.0)
     * @param gpuUtilization Current GPU utilization (0.0 to 1.0)
     * @return Incremental power in Watts (above idle baseline)
     */
    public double calculateIncrementalPower(WorkloadType workloadType, double cpuUtilization, double gpuUtilization) {
        EmpiricalWorkloadProfile profile = workloadProfiles.get(workloadType);

        if (profile == null) {
            // Fallback: use a generic linear model if profile not found
            return calculateFallbackPower(cpuUtilization, gpuUtilization);
        }

        return profile.calculateIncrementalPower(cpuUtilization, gpuUtilization);
    }

    /**
     * Calculates power using the linear scaling method.
     * Power scales linearly with the maximum utilization component.
     *
     * @param workloadType The type of workload being executed
     * @param cpuUtilization Current CPU utilization (0.0 to 1.0)
     * @param gpuUtilization Current GPU utilization (0.0 to 1.0)
     * @return Total power consumption in Watts
     */
    public double calculateTotalPowerLinear(WorkloadType workloadType, double cpuUtilization, double gpuUtilization) {
        EmpiricalWorkloadProfile profile = workloadProfiles.get(workloadType);

        if (profile == null) {
            return (baseIdlePowerWatts + calculateFallbackPower(cpuUtilization, gpuUtilization)) * hardwareScaleFactor;
        }

        double incrementalPower = profile.calculateIncrementalPowerLinear(cpuUtilization, gpuUtilization);
        return (baseIdlePowerWatts + incrementalPower) * hardwareScaleFactor;
    }

    /**
     * Gets the raw incremental power for a workload at 100% typical utilization.
     * Useful for power estimation without current utilization values.
     *
     * @param workloadType The workload type
     * @return Incremental power at typical utilization, in Watts
     */
    public double getTypicalIncrementalPower(WorkloadType workloadType) {
        EmpiricalWorkloadProfile profile = workloadProfiles.get(workloadType);
        return profile != null ? profile.getIncrementalPowerWatts() : 0.0;
    }

    /**
     * Gets the typical total power for a workload.
     *
     * @param workloadType The workload type
     * @return Total power at typical utilization, in Watts
     */
    public double getTypicalTotalPower(WorkloadType workloadType) {
        return (baseIdlePowerWatts + getTypicalIncrementalPower(workloadType)) * hardwareScaleFactor;
    }

    /**
     * Gets the peak power observed for a workload.
     *
     * @param workloadType The workload type
     * @return Peak observed power in Watts
     */
    public double getPeakPower(WorkloadType workloadType) {
        EmpiricalWorkloadProfile profile = workloadProfiles.get(workloadType);
        return profile != null ? profile.getPeakPowerWatts() * hardwareScaleFactor : baseIdlePowerWatts * hardwareScaleFactor;
    }

    /**
     * Fallback power calculation when no profile exists.
     * Uses a simple linear model based on utilization.
     */
    private double calculateFallbackPower(double cpuUtilization, double gpuUtilization) {
        // Fallback: assume 130W max CPU incremental + 350W max GPU incremental
        double cpuPower = cpuUtilization * 130.0;
        double gpuPower = gpuUtilization * 350.0;
        return cpuPower + gpuPower;
    }

    /**
     * Gets the workload profile for a specific workload type.
     *
     * @param workloadType The workload type
     * @return The empirical profile, or null if not found
     */
    public EmpiricalWorkloadProfile getWorkloadProfile(WorkloadType workloadType) {
        return workloadProfiles.get(workloadType);
    }

    /**
     * Adds or updates a workload profile.
     *
     * @param profile The profile to add
     */
    public void addWorkloadProfile(EmpiricalWorkloadProfile profile) {
        workloadProfiles.put(profile.getWorkloadType(), profile);
    }

    /**
     * Checks if a profile exists for the given workload type.
     *
     * @param workloadType The workload type to check
     * @return true if a profile exists
     */
    public boolean hasProfile(WorkloadType workloadType) {
        return workloadProfiles.containsKey(workloadType);
    }

    // Getters and Setters

    public String getModelName() {
        return modelName;
    }

    public double getBaseIdlePowerWatts() {
        return baseIdlePowerWatts;
    }

    public double getHardwareScaleFactor() {
        return hardwareScaleFactor;
    }

    public void setHardwareScaleFactor(double hardwareScaleFactor) {
        this.hardwareScaleFactor = hardwareScaleFactor;
    }

    public Map<WorkloadType, EmpiricalWorkloadProfile> getWorkloadProfiles() {
        return workloadProfiles;
    }

    /**
     * Gets the idle power with hardware scaling applied.
     *
     * @return Scaled idle power in Watts
     */
    public double getScaledIdlePower() {
        return baseIdlePowerWatts * hardwareScaleFactor;
    }

    @Override
    public String toString() {
        return String.format("MeasurementBasedPowerModel{name='%s', idlePower=%.2fW, scaleFactor=%.2f, profiles=%d}",
                modelName, baseIdlePowerWatts, hardwareScaleFactor, workloadProfiles.size());
    }

    /**
     * Returns a summary of all workload profiles.
     */
    public String getProfilesSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Workload Power Profiles (Reference: ").append(REFERENCE_SYSTEM).append(")\n");
        sb.append(String.format("Base Idle Power: %.2f W\n", baseIdlePowerWatts));
        sb.append(String.format("Hardware Scale Factor: %.2f\n", hardwareScaleFactor));
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("%-20s %15s %15s %15s %15s\n",
                "Workload", "Incremental(W)", "Peak(W)", "CPU Util", "GPU Util"));
        sb.append("─".repeat(80)).append("\n");

        for (WorkloadType type : WorkloadType.values()) {
            EmpiricalWorkloadProfile profile = workloadProfiles.get(type);
            if (profile != null) {
                sb.append(String.format("%-20s %15.2f %15.2f %14.0f%% %14.0f%%\n",
                        type.name(),
                        profile.getIncrementalPowerWatts(),
                        profile.getPeakPowerWatts(),
                        profile.getTypicalCpuUtilization() * 100,
                        profile.getTypicalGpuUtilization() * 100));
            }
        }

        return sb.toString();
    }
}
