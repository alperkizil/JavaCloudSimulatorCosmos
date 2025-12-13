package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.List;

/**
 * Energy objective: Minimizes total energy consumption.
 *
 * Energy is calculated as the sum of power consumption over time for each VM.
 * The power model considers:
 * - Base power (idle power for running VM)
 * - CPU utilization based on workload type
 * - GPU utilization based on workload type
 *
 * Energy (kWh) = Power (Watts) × Time (seconds) / 3,600,000
 *
 * Calculation:
 * For each VM j:
 *   For each task assigned to VM j:
 *     executionTime = task.instructionLength / vm.totalIPS
 *     power = calculatePowerForWorkload(task.workloadType, vm)
 *     energy += power × executionTime / 3,600,000
 *
 * Total Energy = sum of energy for all VMs (in kWh)
 *
 * This objective encourages:
 * - Assigning tasks to more power-efficient VMs
 * - Consolidating tasks on fewer VMs (reducing idle power waste)
 * - Matching workloads to appropriate hardware (GPU tasks on GPU VMs)
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

        double totalEnergyJoules = 0.0;

        // Calculate energy for each VM
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder.isEmpty()) {
                continue; // VM is not used, no energy consumed
            }

            long vmIps = vm.getTotalRequestedIps();
            if (vmIps == 0) {
                continue; // Invalid VM
            }

            // Calculate energy for each task on this VM
            for (int taskIdx : taskOrder) {
                Task task = tasks.get(taskIdx);

                // Calculate execution time for this task
                double executionTime = (double) task.getInstructionLength() / vmIps;

                // Calculate power draw based on workload type
                double power = calculatePowerForWorkload(task.getWorkloadType(), vm);

                // Energy (Joules) = Power (Watts) × Time (seconds)
                totalEnergyJoules += power * executionTime;
            }
        }

        // Convert Joules to kWh
        return totalEnergyJoules / JOULES_TO_KWH;
    }

    /**
     * Calculates power consumption for a specific workload type on a VM.
     * Uses MeasurementBasedPowerModel by default for accurate empirical power estimation.
     * Falls back to legacy linear model if configured.
     *
     * @param workloadType The workload being executed
     * @param vm           The VM executing the workload
     * @return Power consumption in Watts
     */
    private double calculatePowerForWorkload(WorkloadType workloadType, VM vm) {
        if (useMeasurementBasedModel) {
            return calculatePowerMeasurementBased(workloadType, vm);
        } else {
            return calculatePowerLegacy(workloadType, vm);
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
}
