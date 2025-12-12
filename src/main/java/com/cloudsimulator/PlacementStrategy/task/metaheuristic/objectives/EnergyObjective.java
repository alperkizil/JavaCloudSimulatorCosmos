package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.enums.WorkloadType;
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

    // Default power model parameters (can be customized)
    private double basePowerWatts = 50.0;      // Idle power per VM
    private double cpuPowerPerCoreWatts = 30.0; // Additional power per vCPU at 100% utilization
    private double gpuPowerPerUnitWatts = 200.0; // Additional power per GPU at 100% utilization

    /**
     * Creates an EnergyObjective with default power model parameters.
     */
    public EnergyObjective() {
    }

    /**
     * Creates an EnergyObjective with custom power model parameters.
     *
     * @param basePowerWatts        Base idle power per VM
     * @param cpuPowerPerCoreWatts  Power per vCPU core at 100% utilization
     * @param gpuPowerPerUnitWatts  Power per GPU at 100% utilization
     */
    public EnergyObjective(double basePowerWatts, double cpuPowerPerCoreWatts, double gpuPowerPerUnitWatts) {
        this.basePowerWatts = basePowerWatts;
        this.cpuPowerPerCoreWatts = cpuPowerPerCoreWatts;
        this.gpuPowerPerUnitWatts = gpuPowerPerUnitWatts;
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
     * Uses workload-specific CPU/GPU utilization profiles.
     *
     * @param workloadType The workload being executed
     * @param vm           The VM executing the workload
     * @return Power consumption in Watts
     */
    private double calculatePowerForWorkload(WorkloadType workloadType, VM vm) {
        // Get utilization profile for workload type
        double[] utilization = getUtilizationProfile(workloadType);
        double cpuUtil = utilization[0];
        double gpuUtil = utilization[1];

        // Calculate power components
        double cpuPower = cpuUtil * vm.getRequestedVcpuCount() * cpuPowerPerCoreWatts;
        double gpuPower = gpuUtil * vm.getRequestedGpuCount() * gpuPowerPerUnitWatts;

        return basePowerWatts + cpuPower + gpuPower;
    }

    /**
     * Returns the CPU and GPU utilization profile for a workload type.
     * These values are consistent with the VM.calculateUtilization() method.
     *
     * @param workloadType The workload type
     * @return Array of [cpuUtilization, gpuUtilization] (0.0 to 1.0)
     */
    private double[] getUtilizationProfile(WorkloadType workloadType) {
        switch (workloadType) {
            case SEVEN_ZIP:
                return new double[]{0.8, 0.0};
            case DATABASE:
                return new double[]{0.6, 0.0};
            case FURMARK:
                return new double[]{0.1, 1.0};
            case IMAGE_GEN_CPU:
                return new double[]{0.9, 0.0};
            case IMAGE_GEN_GPU:
                return new double[]{0.2, 0.9};
            case LLM_CPU:
                return new double[]{0.95, 0.0};
            case LLM_GPU:
                return new double[]{0.3, 0.95};
            case CINEBENCH:
                return new double[]{1.0, 0.0};
            case PRIME95SmallFFT:
                return new double[]{1.0, 0.0};
            case VERACRYPT:
                return new double[]{0.85, 0.0};
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
        return "Minimizes total energy consumption across all VMs executing tasks";
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
}
