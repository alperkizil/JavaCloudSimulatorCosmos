package com.cloudsimulator.model;

import com.cloudsimulator.enums.WorkloadType;

import java.util.EnumMap;
import java.util.List;
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

    // Speed-based power scaling parameters
    // Power scales as (vmIPS / referenceIPS) ^ exponent
    // 1.0 = linear, 1.5 = super-linear (realistic for CPUs), 2.0 = quadratic
    public static final double POWER_SCALING_EXPONENT = 2.0;

    // Reference full-load incremental power per component (Watts; wall/AC, average
    // delta above idle), used to attribute a workload's single lumped incremental
    // power between CPU and GPU in proportion to (typical utilization x component
    // full-load power). Taken straight from the empirical table: CPU = CINEBENCH
    // (strongest sustained ~100% CPU benchmark); GPU = FURMARK (GPU-stress case).
    // Only their RATIO (~2.63) affects the split, so the choice of a common unit
    // (wall vs PSU-corrected DC) is irrelevant — it cancels.
    public static final double CPU_FULL_LOAD_WATTS = 133.76;
    public static final double GPU_FULL_LOAD_WATTS = 352.18;

    // Default reference IPS (used if not calculated from hosts)
    public static final long DEFAULT_REFERENCE_IPS = 3_000_000_000L;  // 3 billion IPS

    // Dynamic reference IPS calculated from host composition
    private long referenceVmIps = DEFAULT_REFERENCE_IPS;

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
            0.03,       // typicalCpuUtilization (3% - disk-bound workload)
            0.0,        // typicalGpuUtilization
            0.001185,   // energyPerUnit (J/Kbyte)
            "15.7 MiB/s",
            "Encrypting 32GB Sandisk Flash drive with Camellia Algorithm, Low CPU Usage, NO GPU"
        ));

        // DATABASE (HammerDB) - I/O bound, low CPU
        workloadProfiles.put(WorkloadType.DATABASE, new EmpiricalWorkloadProfile(
            WorkloadType.DATABASE,
            39.59,      // incrementalPower
            133.6,      // peakPower
            92.5,       // lowestPower
            115.38,     // averagePower
            0.12,       // typicalCpuUtilization (12% CPU)
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
            1.0,        // typicalCpuUtilization (100% CPU)
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
            1.0,        // typicalCpuUtilization (100% CPU)
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
            1.0,        // typicalCpuUtilization (100% CPU)
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
            0.55,       // typicalCpuUtilization (55% CPU)
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
            0.12,       // typicalCpuUtilization (12% CPU)
            0.12,       // typicalGpuUtilization (12% GPU)
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
            0.80,       // typicalCpuUtilization (80% CPU)
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
            0.30,       // typicalCpuUtilization (30% CPU)
            0.10,       // typicalGpuUtilization (10% GPU)
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
            0.08,       // typicalCpuUtilization (8% CPU)
            1.0,        // typicalGpuUtilization (100% GPU)
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
     * Splits a lane's incremental power between CPU and GPU in proportion to each
     * component's expected draw (typical utilization x component full-load power).
     * The two parts always sum to {@code lanePower}, so the host total power is
     * unchanged; this only refines the CPU/GPU attribution (replacing the old
     * winner-take-all boolean that dumped a workload's whole lumped wattage into a
     * single bucket and mislabeled mixed GPU workloads as CPU).
     *
     * A pure-CPU or pure-GPU workload collapses to 100% of that component; a
     * profile with no utilization (IDLE) or an unknown workload is attributed
     * entirely to CPU by convention (lanePower is typically 0 in that case).
     *
     * @param workloadType the workload running on the lane
     * @param lanePower    the lane's (speed-scaled) incremental power in Watts
     * @return {@code [cpuPower, gpuPower]} in Watts, summing to {@code lanePower}
     */
    public double[] splitIncrementalPower(WorkloadType workloadType, double lanePower) {
        EmpiricalWorkloadProfile profile = workloadProfiles.get(workloadType);
        if (profile == null) {
            return new double[]{ lanePower, 0.0 };
        }
        double cpuWeight = profile.getTypicalCpuUtilization() * CPU_FULL_LOAD_WATTS;
        double gpuWeight = profile.getTypicalGpuUtilization() * GPU_FULL_LOAD_WATTS;
        double total = cpuWeight + gpuWeight;
        double cpuFraction = total > 0 ? cpuWeight / total : 1.0;
        return new double[]{ lanePower * cpuFraction, lanePower * (1.0 - cpuFraction) };
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

    // ==================== Speed-Based Power Scaling ====================

    /**
     * Calculates and sets the reference IPS based on the composition of hosts.
     * The reference IPS is set to the MEDIAN IPS of all hosts, which ensures
     * that roughly half the hosts are "slow" (power-efficient) and half are
     * "fast" (power-hungry), creating a balanced trade-off.
     *
     * @param hosts List of hosts to analyze
     */
    public void calculateReferenceIpsFromHosts(List<Host> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            this.referenceVmIps = DEFAULT_REFERENCE_IPS;
            return;
        }

        // Extract IPS values and sort them
        long[] ipsValues = hosts.stream()
                .mapToLong(Host::getInstructionsPerSecond)
                .filter(ips -> ips > 0)
                .sorted()
                .toArray();

        if (ipsValues.length == 0) {
            this.referenceVmIps = DEFAULT_REFERENCE_IPS;
            return;
        }

        // Calculate median
        int mid = ipsValues.length / 2;
        if (ipsValues.length % 2 == 0) {
            // Even number of elements: average of two middle values
            this.referenceVmIps = (ipsValues[mid - 1] + ipsValues[mid]) / 2;
        } else {
            // Odd number of elements: middle value
            this.referenceVmIps = ipsValues[mid];
        }
    }

    /**
     * Calculates and sets the reference IPS based on VM speeds.
     * The reference IPS is set to the MEDIAN IPS of all VMs.
     *
     * @param vms List of VMs to analyze
     */
    public void calculateReferenceIpsFromVMs(List<VM> vms) {
        if (vms == null || vms.isEmpty()) {
            this.referenceVmIps = DEFAULT_REFERENCE_IPS;
            return;
        }

        // Extract total IPS values and sort them
        long[] ipsValues = vms.stream()
                .mapToLong(VM::getTotalRequestedIps)
                .filter(ips -> ips > 0)
                .sorted()
                .toArray();

        if (ipsValues.length == 0) {
            this.referenceVmIps = DEFAULT_REFERENCE_IPS;
            return;
        }

        // Calculate median
        int mid = ipsValues.length / 2;
        if (ipsValues.length % 2 == 0) {
            this.referenceVmIps = (ipsValues[mid - 1] + ipsValues[mid]) / 2;
        } else {
            this.referenceVmIps = ipsValues[mid];
        }
    }

    /**
     * Gets the current reference IPS used for power scaling.
     *
     * @return Reference IPS value
     */
    public long getReferenceVmIps() {
        return referenceVmIps;
    }

    /**
     * Manually sets the reference IPS for power scaling.
     *
     * @param referenceVmIps The reference IPS value
     */
    public void setReferenceVmIps(long referenceVmIps) {
        this.referenceVmIps = referenceVmIps > 0 ? referenceVmIps : DEFAULT_REFERENCE_IPS;
    }

    /**
     * Calculates the speed-based power scaling factor for a VM.
     * Faster VMs consume more power per unit time, creating a trade-off
     * between execution speed (makespan) and energy consumption.
     *
     * Formula: scaleFactor = (vmIPS / referenceIPS) ^ POWER_SCALING_EXPONENT
     *
     * Examples with exponent 1.5 and reference 3B IPS:
     * - VM at 2B IPS (0.67x reference): factor = 0.54 (46% power reduction)
     * - VM at 3B IPS (1x reference):    factor = 1.0  (baseline)
     * - VM at 4B IPS (1.33x reference): factor = 1.54 (54% more power)
     *
     * @param vmIps The VM's processing speed in instructions per second
     * @return Power scaling factor (1.0 = reference speed)
     */
    public double calculateSpeedPowerFactor(long vmIps) {
        if (vmIps <= 0 || referenceVmIps <= 0) {
            return 1.0;
        }
        double speedRatio = (double) vmIps / referenceVmIps;
        return Math.pow(speedRatio, POWER_SCALING_EXPONENT);
    }

    /**
     * Calculates incremental power with VM speed scaling applied.
     * This creates a trade-off: faster VMs draw more power per second.
     *
     * @param workloadType The type of workload being executed
     * @param cpuUtilization Current CPU utilization (0.0 to 1.0)
     * @param gpuUtilization Current GPU utilization (0.0 to 1.0)
     * @param vmIps The VM's processing speed in instructions per second
     * @return Speed-scaled incremental power in Watts
     */
    public double calculateIncrementalPowerWithSpeedScaling(
            WorkloadType workloadType,
            double cpuUtilization,
            double gpuUtilization,
            long vmIps) {
        double basePower = calculateIncrementalPower(workloadType, cpuUtilization, gpuUtilization);
        double speedFactor = calculateSpeedPowerFactor(vmIps);
        return basePower * speedFactor;
    }
}
