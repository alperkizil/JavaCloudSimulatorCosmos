package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.List;

/**
 * Energy objective: Minimizes total energy consumption.
 *
 * Energy is calculated to match the simulation's tick-by-tick power model:
 * - Base idle power is consumed for the entire simulation duration (makespan)
 * - Incremental workload power is consumed for each task's execution ticks
 *
 * Energy (kWh) = (idlePower × makespan + sum(incrementalPower × taskTicks)) / 3,600,000
 *
 * Calculation (Discrete Simulation Model):
 * The simulation executes in 1-second ticks. Power calculation per tick:
 *   tickPower = baseIdlePower + sum(activeVM_incrementalPower)
 *
 * This is equivalent to:
 *   totalEnergy = baseIdlePower × makespan + sum(incrementalPower × taskTicks)
 *
 * This objective encourages:
 * - Assigning tasks to more power-efficient VMs
 * - Consolidating tasks on fewer VMs (reducing idle power waste)
 * - Matching workloads to appropriate hardware (GPU tasks on GPU VMs)
 * - Minimizing makespan (reduces idle power duration)
 */
public class EnergyObjective implements SchedulingObjective {

    // Measurement-based power model for accurate workload-specific power calculation
    private final MeasurementBasedPowerModel powerModel;

    // Legacy power model parameters (used as fallback)
    private double basePowerWatts = 50.0;      // Idle power per VM
    private double cpuPowerPerCoreWatts = 30.0; // Additional power per vCPU at 100% utilization
    private double gpuPowerPerUnitWatts = 200.0; // Additional power per GPU at 100% utilization

    // Flag to use measurement-based model
    private boolean useMeasurementBasedModel = true;

    // Flag to enable speed-based power scaling (creates makespan vs energy trade-off)
    private boolean useSpeedBasedScaling = true;

    // Additional hosts that consume idle power but have no VMs assigned
    // (e.g., hosts placed in datacenter but without VM assignments)
    private int additionalIdleHostCount = 0;

    // Hosts list for dynamic calculation of idle host count
    private java.util.List<Host> hosts = null;

    /**
     * Creates an EnergyObjective with MeasurementBasedPowerModel (default).
     * Uses empirical power measurements for accurate energy estimation.
     */
    public EnergyObjective() {
        this.powerModel = new MeasurementBasedPowerModel();
    }

    /**
     * Creates an EnergyObjective with a custom MeasurementBasedPowerModel.
     *
     * @param powerModel The measurement-based power model to use
     */
    public EnergyObjective(MeasurementBasedPowerModel powerModel) {
        this.powerModel = powerModel;
    }

    /**
     * Creates an EnergyObjective with a hardware scale factor for the MeasurementBasedPowerModel.
     *
     * @param hardwareScaleFactor Scale factor for different hardware (1.0 = reference system)
     */
    public EnergyObjective(double hardwareScaleFactor) {
        this.powerModel = new MeasurementBasedPowerModel(hardwareScaleFactor);
    }

    /**
     * Creates an EnergyObjective with legacy power model parameters.
     * Use this constructor for backward compatibility or custom linear power models.
     *
     * @param basePowerWatts        Base idle power per VM
     * @param cpuPowerPerCoreWatts  Power per vCPU core at 100% utilization
     * @param gpuPowerPerUnitWatts  Power per GPU at 100% utilization
     * @param useLegacyModel        Set to true to use legacy linear model instead of measurement-based
     */
    public EnergyObjective(double basePowerWatts, double cpuPowerPerCoreWatts, double gpuPowerPerUnitWatts, boolean useLegacyModel) {
        this.powerModel = new MeasurementBasedPowerModel();
        this.basePowerWatts = basePowerWatts;
        this.cpuPowerPerCoreWatts = cpuPowerPerCoreWatts;
        this.gpuPowerPerUnitWatts = gpuPowerPerUnitWatts;
        this.useMeasurementBasedModel = !useLegacyModel;
    }

    @Override
    public String getName() {
        return "Energy";
    }

    // Conversion factor: 1 kWh = 3,600,000 Joules (3600 seconds * 1000 watts)
    private static final double JOULES_TO_KWH = 3_600_000.0;

    @Override
    public double evaluate(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return 0.0;
        }

        double incrementalEnergyJoules = 0.0;
        long makespan = 0;
        int activeVmCount = 0;
        long totalVmTicks = 0;  // Sum of all VM completion times (for idle calculation)

        // Count unique hosts that have VMs assigned (these will consume idle power)
        // This matches simulation behavior where ALL hosts with VMs consume idle power
        java.util.Set<Long> hostsWithVMs = new java.util.HashSet<>();
        for (VM vm : vms) {
            if (vm.getAssignedHostId() != null) {
                hostsWithVMs.add(vm.getAssignedHostId());
            }
        }
        int activeHostCount = Math.max(1, hostsWithVMs.size());

        // Dynamically calculate additional idle hosts if hosts list is provided
        int dynamicAdditionalHosts = additionalIdleHostCount;
        if (hosts != null && !hosts.isEmpty()) {
            // Count hosts that are in datacenters (have assignedDatacenterId)
            int hostsInDatacenter = (int) hosts.stream()
                .filter(h -> h.getAssignedDatacenterId() != null)
                .count();
            // Additional idle hosts = hosts in datacenter without VMs
            dynamicAdditionalHosts = Math.max(0, hostsInDatacenter - hostsWithVMs.size());
        }

        // First pass: calculate makespan and incremental energy
        long[] vmCompletionTicks = new long[vms.size()];

        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder.isEmpty()) {
                continue; // VM is not used
            }

            activeVmCount++;
            long vmIps = vm.getTotalRequestedIps();
            if (vmIps == 0) {
                continue; // Invalid VM
            }

            // Calculate incremental energy for each task on this VM
            for (int taskIdx : taskOrder) {
                Task task = tasks.get(taskIdx);

                // Calculate execution ticks using ceiling division
                // This models the discrete 1-second time steps in simulation
                long executionTicks = (task.getInstructionLength() + vmIps - 1) / vmIps;
                vmCompletionTicks[vmIdx] += executionTicks;

                // Calculate INCREMENTAL power (above idle) for this workload
                double incrementalPower = calculateIncrementalPowerForWorkload(task.getWorkloadType(), vm);

                // Incremental Energy (Joules) = Incremental Power (Watts) × Time (seconds)
                incrementalEnergyJoules += incrementalPower * executionTicks;
            }

            totalVmTicks += vmCompletionTicks[vmIdx];

            // Track makespan (max completion time across all VMs)
            if (vmCompletionTicks[vmIdx] > makespan) {
                makespan = vmCompletionTicks[vmIdx];
            }
        }

        // Add base idle energy for the entire simulation duration (host idle power)
        // Each host with VMs consumes idle power for the entire makespan duration
        // Also include any additional hosts that are powered on but have no VMs
        // This matches simulation's tick-by-tick calculation where each host adds idlePower per tick
        double baseIdlePower = powerModel.getScaledIdlePower();
        int totalActiveHosts = activeHostCount + dynamicAdditionalHosts;
        double hostIdleEnergyJoules = baseIdlePower * makespan * totalActiveHosts;

        // Add VM idle energy: when VMs finish before makespan, they still consume some power
        // Each active VM runs for 'makespan' ticks total, but only executes tasks for vmCompletionTicks
        // The idle time (makespan - vmCompletionTicks) still consumes base VM power
        // Approximation: use IDLE workload incremental power (which is 0 for measurement-based model)
        // The simulation counts host idle during VM idle, which we already count in hostIdleEnergyJoules

        double totalEnergyJoules = hostIdleEnergyJoules + incrementalEnergyJoules;

        // Convert Joules to kWh
        return totalEnergyJoules / JOULES_TO_KWH;
    }

    /**
     * Calculates total power consumption for a specific workload type on a VM.
     * Uses MeasurementBasedPowerModel by default for accurate empirical power estimation.
     * Falls back to legacy linear model if configured.
     *
     * @param workloadType The workload being executed
     * @param vm           The VM executing the workload
     * @return Total power consumption in Watts (including idle)
     */
    private double calculatePowerForWorkload(WorkloadType workloadType, VM vm) {
        if (useMeasurementBasedModel) {
            return calculatePowerMeasurementBased(workloadType, vm);
        } else {
            return calculatePowerLegacy(workloadType, vm);
        }
    }

    /**
     * Calculates INCREMENTAL power (above idle) for a specific workload type.
     * This is the additional power consumed by the workload beyond base idle power.
     *
     * When speed-based scaling is enabled, faster VMs consume more power per second,
     * creating a trade-off between execution speed (makespan) and energy consumption.
     *
     * @param workloadType The workload being executed
     * @param vm           The VM executing the workload
     * @return Incremental power consumption in Watts (NOT including idle)
     */
    private double calculateIncrementalPowerForWorkload(WorkloadType workloadType, VM vm) {
        if (useMeasurementBasedModel) {
            double[] utilization = getUtilizationProfile(workloadType);

            if (useSpeedBasedScaling) {
                // Apply speed-based scaling: faster VMs use more power per second
                return powerModel.calculateIncrementalPowerWithSpeedScaling(
                        workloadType,
                        utilization[0],
                        utilization[1],
                        vm.getTotalRequestedIps()
                );
            } else {
                // Original behavior: no speed scaling
                return powerModel.calculateIncrementalPower(workloadType, utilization[0], utilization[1]);
            }
        } else {
            // Legacy model: total power minus base power
            return calculatePowerLegacy(workloadType, vm) - basePowerWatts;
        }
    }

    /**
     * Calculates power using the MeasurementBasedPowerModel.
     * Uses empirical measurements for accurate workload-specific power estimation.
     *
     * @param workloadType The workload being executed
     * @param vm           The VM executing the workload
     * @return Power consumption in Watts
     */
    private double calculatePowerMeasurementBased(WorkloadType workloadType, VM vm) {
        // Get typical utilization for this workload from the empirical profile
        double[] utilization = getUtilizationProfile(workloadType);
        double cpuUtil = utilization[0];
        double gpuUtil = utilization[1];

        // Use the measurement-based power model for accurate calculation
        return powerModel.calculateTotalPower(workloadType, cpuUtil, gpuUtil);
    }

    /**
     * Calculates power using the legacy linear model.
     * Provided for backward compatibility.
     *
     * @param workloadType The workload being executed
     * @param vm           The VM executing the workload
     * @return Power consumption in Watts
     */
    private double calculatePowerLegacy(WorkloadType workloadType, VM vm) {
        // Get utilization profile for workload type
        double[] utilization = getUtilizationProfile(workloadType);
        double cpuUtil = utilization[0];
        double gpuUtil = utilization[1];

        // Calculate power components using legacy linear model
        double cpuPower = cpuUtil * vm.getRequestedVcpuCount() * cpuPowerPerCoreWatts;
        double gpuPower = gpuUtil * vm.getRequestedGpuCount() * gpuPowerPerUnitWatts;

        return basePowerWatts + cpuPower + gpuPower;
    }

    /**
     * Returns the CPU and GPU utilization profile for a workload type.
     * These values are consistent with the VM.calculateUtilization() method
     * and the empirical measurements in MeasurementBasedPowerModel.
     *
     * @param workloadType The workload type
     * @return Array of [cpuUtilization, gpuUtilization] (0.0 to 1.0)
     */
    private double[] getUtilizationProfile(WorkloadType workloadType) {
        // First try to get utilization from the measurement-based model
        if (powerModel != null && powerModel.hasProfile(workloadType)) {
            var profile = powerModel.getWorkloadProfile(workloadType);
            return new double[]{profile.getTypicalCpuUtilization(), profile.getTypicalGpuUtilization()};
        }

        // Fallback to hardcoded values
        switch (workloadType) {
            case SEVEN_ZIP:
                return new double[]{1.0, 0.0};  // 100% CPU from empirical data
            case DATABASE:
                return new double[]{0.12, 0.0}; // 12% CPU from empirical data
            case FURMARK:
                return new double[]{0.08, 1.0}; // 8% CPU, 100% GPU from empirical data
            case IMAGE_GEN_CPU:
                return new double[]{0.80, 0.0}; // 80% CPU from empirical data
            case IMAGE_GEN_GPU:
                return new double[]{0.30, 0.10}; // 30% CPU, 10% GPU from empirical data
            case LLM_CPU:
                return new double[]{0.55, 0.0}; // 55% CPU from empirical data
            case LLM_GPU:
                return new double[]{0.12, 0.12}; // 12% CPU, 12% GPU from empirical data
            case CINEBENCH:
                return new double[]{1.0, 0.0};  // 100% CPU from empirical data
            case PRIME95SmallFFT:
                return new double[]{1.0, 0.0};  // 100% CPU from empirical data
            case VERACRYPT:
                return new double[]{0.03, 0.0}; // 3% CPU (disk-bound) from empirical data
            case IDLE:
            default:
                return new double[]{0.0, 0.0};
        }
    }

    @Override
    public boolean isMinimization() {
        return true;
    }

    @Override
    public String getDescription() {
        String modelType = useMeasurementBasedModel ? "MeasurementBasedPowerModel" : "Legacy Linear Model";
        return "Minimizes total energy consumption across all VMs executing tasks (using " + modelType + ")";
    }

    @Override
    public String getUnit() {
        return "kWh";
    }

    // Getters and setters for power model parameters

    public double getBasePowerWatts() {
        return basePowerWatts;
    }

    public void setBasePowerWatts(double basePowerWatts) {
        this.basePowerWatts = basePowerWatts;
    }

    public double getCpuPowerPerCoreWatts() {
        return cpuPowerPerCoreWatts;
    }

    public void setCpuPowerPerCoreWatts(double cpuPowerPerCoreWatts) {
        this.cpuPowerPerCoreWatts = cpuPowerPerCoreWatts;
    }

    public double getGpuPowerPerUnitWatts() {
        return gpuPowerPerUnitWatts;
    }

    public void setGpuPowerPerUnitWatts(double gpuPowerPerUnitWatts) {
        this.gpuPowerPerUnitWatts = gpuPowerPerUnitWatts;
    }

    /**
     * Gets the MeasurementBasedPowerModel used for energy calculation.
     *
     * @return The power model
     */
    public MeasurementBasedPowerModel getPowerModel() {
        return powerModel;
    }

    /**
     * Checks if the measurement-based power model is being used.
     *
     * @return true if using MeasurementBasedPowerModel, false if using legacy model
     */
    public boolean isUsingMeasurementBasedModel() {
        return useMeasurementBasedModel;
    }

    /**
     * Sets whether to use the measurement-based power model.
     *
     * @param useMeasurementBasedModel true to use MeasurementBasedPowerModel, false for legacy
     */
    public void setUseMeasurementBasedModel(boolean useMeasurementBasedModel) {
        this.useMeasurementBasedModel = useMeasurementBasedModel;
    }

    /**
     * Gets the number of additional idle hosts (hosts without VMs but consuming power).
     *
     * @return The additional idle host count
     */
    public int getAdditionalIdleHostCount() {
        return additionalIdleHostCount;
    }

    /**
     * Sets the number of additional hosts that consume idle power but have no VMs assigned.
     * This accounts for hosts that are placed in datacenters but not assigned any VMs.
     * Set this value based on (total hosts in datacenter) - (hosts with VMs) for accurate
     * energy prediction that matches simulation.
     *
     * NOTE: If setHosts() is called, this value is calculated automatically and this
     * manual setting is ignored.
     *
     * @param additionalIdleHostCount Number of additional idle hosts
     */
    public void setAdditionalIdleHostCount(int additionalIdleHostCount) {
        this.additionalIdleHostCount = Math.max(0, additionalIdleHostCount);
    }

    /**
     * Sets the hosts list for dynamic calculation of idle host count and reference IPS.
     * When set, the additional idle hosts will be automatically calculated as:
     * (hosts in datacenter) - (hosts with VMs)
     *
     * Also automatically initializes the reference IPS for speed-based power scaling
     * using the median IPS of all hosts.
     *
     * This eliminates the need to manually call setAdditionalIdleHostCount() or
     * initializeReferenceIpsFromHosts().
     *
     * @param hosts List of all hosts in the simulation
     */
    public void setHosts(java.util.List<Host> hosts) {
        this.hosts = hosts;
        // Automatically initialize reference IPS for speed-based scaling
        if (powerModel != null && hosts != null && !hosts.isEmpty()) {
            powerModel.calculateReferenceIpsFromHosts(hosts);
        }
    }

    /**
     * Gets the hosts list used for dynamic idle host calculation.
     *
     * @return The hosts list, or null if not set
     */
    public java.util.List<Host> getHosts() {
        return hosts;
    }

    // ==================== Speed-Based Power Scaling ====================

    /**
     * Checks if speed-based power scaling is enabled.
     * When enabled, faster VMs consume more power per second, creating
     * a trade-off between execution speed (makespan) and energy consumption.
     *
     * @return true if speed-based scaling is enabled
     */
    public boolean isUsingSpeedBasedScaling() {
        return useSpeedBasedScaling;
    }

    /**
     * Enables or disables speed-based power scaling.
     *
     * When ENABLED (default):
     * - Faster VMs consume more power per second
     * - Creates trade-off: fast execution = high energy, slow execution = low energy
     * - Pareto front will have multiple solutions
     *
     * When DISABLED:
     * - Power consumption independent of VM speed
     * - Minimizing makespan also minimizes energy (no trade-off)
     * - Pareto front collapses to ~1 solution
     *
     * @param useSpeedBasedScaling true to enable speed-based scaling
     */
    public void setUseSpeedBasedScaling(boolean useSpeedBasedScaling) {
        this.useSpeedBasedScaling = useSpeedBasedScaling;
    }

    /**
     * Initializes the reference IPS for speed-based scaling from the VM list.
     * The reference IPS is set to the MEDIAN IPS of all VMs, creating a balanced
     * trade-off where roughly half the VMs are "power-efficient" and half are "power-hungry".
     *
     * This method should be called before optimization if speed-based scaling is enabled.
     *
     * @param vms List of VMs to analyze
     */
    public void initializeReferenceIpsFromVMs(java.util.List<VM> vms) {
        if (powerModel != null && vms != null && !vms.isEmpty()) {
            powerModel.calculateReferenceIpsFromVMs(vms);
        }
    }

    /**
     * Initializes the reference IPS for speed-based scaling from the host list.
     * The reference IPS is set to the MEDIAN IPS of all hosts.
     *
     * @param hosts List of hosts to analyze
     */
    public void initializeReferenceIpsFromHosts(java.util.List<Host> hosts) {
        if (powerModel != null && hosts != null && !hosts.isEmpty()) {
            powerModel.calculateReferenceIpsFromHosts(hosts);
        }
    }

    /**
     * Gets the current reference IPS used for speed-based power scaling.
     *
     * @return Reference IPS value, or -1 if power model is not available
     */
    public long getReferenceIps() {
        return powerModel != null ? powerModel.getReferenceVmIps() : -1;
    }

    /**
     * Manually sets the reference IPS for speed-based power scaling.
     *
     * @param referenceIps The reference IPS value
     */
    public void setReferenceIps(long referenceIps) {
        if (powerModel != null) {
            powerModel.setReferenceVmIps(referenceIps);
        }
    }
}
