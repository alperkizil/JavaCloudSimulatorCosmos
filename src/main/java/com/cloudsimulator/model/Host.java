package com.cloudsimulator.model;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a Host machine in a datacenter that can run multiple VMs.
 */
public class Host {
    private static final AtomicLong idGenerator = new AtomicLong(0);

    // Identity and timing
    private final long id;
    private long activeSeconds;              // Starts when assigned to datacenter
    private long secondsIDLE;                // Seconds with no VMs running
    private long secondsExecuting;           // Seconds with at least one VM running

    // Compute specifications
    private long instructionsPerSecond;      // IPS per core
    private int numberOfCpuCores;
    private ComputeType computeType;
    private int numberOfGpus;

    // Resources (defaults: RAM=2TB, Network=2Tbps, HDD=20TB)
    private long ramCapacityMB;              // RAM in MB (default 2TB = 2,097,152 MB)
    private long networkCapacityMbps;        // Network in Mbps (default 2Tbps = 2,000,000 Mbps)
    private long hardDriveCapacityMB;        // Storage in MB (default 20TB = 20,971,520 MB)

    // VM management
    private List<VM> assignedVMs;

    // Power consumption tracking
    private double currentTotalPowerDraw;    // Watts
    private double currentCpuPowerDraw;      // Watts
    private double currentGpuPowerDraw;      // Watts
    private double otherComponentsPowerDraw; // Watts
    private PowerModel powerModel;

    // Utilization history tracking
    private List<HostUtilizationRecord> utilizationHistory;
    private Map<Long, Long> vmOpenSecondsMap;  // VM ID -> seconds kept open
    private Map<Long, Map<WorkloadType, Long>> vmWorkloadSecondsMap;  // VM ID -> Workload -> seconds

    // Datacenter assignment
    private Long assignedDatacenterId;

    // Totals
    private long totalNumberOfSecondsWorking;
    private long totalNumberOfSecondsIdle;

    /**
     * Constructor with custom specifications.
     */
    public Host(long instructionsPerSecond, int numberOfCpuCores,
                ComputeType computeType, int numberOfGpus) {
        this.id = idGenerator.incrementAndGet();
        this.instructionsPerSecond = instructionsPerSecond;
        this.numberOfCpuCores = numberOfCpuCores;
        this.computeType = computeType;
        this.numberOfGpus = numberOfGpus;

        // Initialize defaults
        this.ramCapacityMB = 2L * 1024 * 1024;           // 2TB in MB
        this.networkCapacityMbps = 2L * 1000 * 1000;     // 2Tbps in Mbps
        this.hardDriveCapacityMB = 20L * 1024 * 1024;    // 20TB in MB

        this.activeSeconds = 0;
        this.secondsIDLE = 0;
        this.secondsExecuting = 0;
        this.assignedVMs = new ArrayList<>();

        this.currentTotalPowerDraw = 0.0;
        this.currentCpuPowerDraw = 0.0;
        this.currentGpuPowerDraw = 0.0;
        this.otherComponentsPowerDraw = 0.0;

        this.powerModel = new PowerModel();
        this.utilizationHistory = new ArrayList<>();
        this.vmOpenSecondsMap = new HashMap<>();
        this.vmWorkloadSecondsMap = new HashMap<>();

        this.assignedDatacenterId = null;
        this.totalNumberOfSecondsWorking = 0;
        this.totalNumberOfSecondsIdle = 0;
    }

    /**
     * Default constructor with typical server specifications.
     */
    public Host() {
        this(2_500_000_000L, 16, ComputeType.CPU_ONLY, 0);
    }

    /**
     * Assigns a VM to this host.
     */
    public void assignVM(VM vm) {
        this.assignedVMs.add(vm);
        this.vmOpenSecondsMap.put(vm.getId(), 0L);
        this.vmWorkloadSecondsMap.put(vm.getId(), new HashMap<>());
    }

    /**
     * Removes a VM from this host.
     */
    public void removeVM(VM vm) {
        this.assignedVMs.remove(vm);
    }

    /**
     * Records utilization for a specific VM at the current simulation time.
     */
    public void recordUtilization(long timestamp, long vmId, WorkloadType workloadType,
                                  double cpuUtilization, double gpuUtilization) {
        // Calculate power draw for this utilization
        double powerDraw = powerModel.calculateTotalPower(cpuUtilization, gpuUtilization);

        // Create utilization record
        HostUtilizationRecord record = new HostUtilizationRecord(
            timestamp, vmId, workloadType, cpuUtilization, gpuUtilization, powerDraw
        );
        utilizationHistory.add(record);

        // Update VM open seconds
        vmOpenSecondsMap.merge(vmId, 1L, Long::sum);

        // Update VM workload seconds
        Map<WorkloadType, Long> workloadMap = vmWorkloadSecondsMap.get(vmId);
        if (workloadMap != null) {
            workloadMap.merge(workloadType, 1L, Long::sum);
        }
    }

    /**
     * Updates power consumption based on current VM utilization.
     */
    public void updatePowerConsumption() {
        // Calculate current CPU and GPU utilization from all VMs
        double totalCpuUtil = 0.0;
        double totalGpuUtil = 0.0;

        for (VM vm : assignedVMs) {
            // Get VM's current utilization
            if (vm.getCurrentUtilization() != null) {
                totalCpuUtil += vm.getCurrentUtilization().getCpuUtilization();
                totalGpuUtil += vm.getCurrentUtilization().getGpuUtilization();
            }
        }

        // Normalize by number of cores/GPUs
        double avgCpuUtil = assignedVMs.isEmpty() ? 0.0 : Math.min(1.0, totalCpuUtil);
        double avgGpuUtil = assignedVMs.isEmpty() ? 0.0 : Math.min(1.0, totalGpuUtil);

        // Calculate power components
        this.currentCpuPowerDraw = powerModel.calculateCpuPower(avgCpuUtil);
        this.currentGpuPowerDraw = powerModel.calculateGpuPower(avgGpuUtil);
        this.otherComponentsPowerDraw = powerModel.getOtherComponentsPower();
        this.currentTotalPowerDraw = currentCpuPowerDraw + currentGpuPowerDraw + otherComponentsPowerDraw;
    }

    /**
     * Updates host state for the current simulation second.
     * Should be called every simulation tick.
     */
    public void updateState() {
        activeSeconds++;

        if (assignedVMs.isEmpty() || assignedVMs.stream().noneMatch(vm -> vm.getVmState() == com.cloudsimulator.enums.VmState.RUNNING)) {
            secondsIDLE++;
            totalNumberOfSecondsIdle++;
        } else {
            secondsExecuting++;
            totalNumberOfSecondsWorking++;
        }

        updatePowerConsumption();
    }

    /**
     * Gets the total seconds a specific VM has been open on this host.
     */
    public long getVmOpenSeconds(long vmId) {
        return vmOpenSecondsMap.getOrDefault(vmId, 0L);
    }

    /**
     * Gets the seconds a VM spent executing a specific workload type.
     */
    public long getVmWorkloadSeconds(long vmId, WorkloadType workloadType) {
        Map<WorkloadType, Long> workloadMap = vmWorkloadSecondsMap.get(vmId);
        if (workloadMap != null) {
            return workloadMap.getOrDefault(workloadType, 0L);
        }
        return 0L;
    }

    /**
     * Calculates total power draw for a specific VM based on utilization history.
     */
    public double calculateVmTotalPowerDraw(long vmId) {
        return utilizationHistory.stream()
            .filter(record -> record.getVmId() == vmId)
            .mapToDouble(HostUtilizationRecord::getPowerDraw)
            .sum();
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public long getActiveSeconds() {
        return activeSeconds;
    }

    public void setActiveSeconds(long activeSeconds) {
        this.activeSeconds = activeSeconds;
    }

    public long getSecondsIDLE() {
        return secondsIDLE;
    }

    public void setSecondsIDLE(long secondsIDLE) {
        this.secondsIDLE = secondsIDLE;
    }

    public long getSecondsExecuting() {
        return secondsExecuting;
    }

    public void setSecondsExecuting(long secondsExecuting) {
        this.secondsExecuting = secondsExecuting;
    }

    public long getInstructionsPerSecond() {
        return instructionsPerSecond;
    }

    public void setInstructionsPerSecond(long instructionsPerSecond) {
        this.instructionsPerSecond = instructionsPerSecond;
    }

    public int getNumberOfCpuCores() {
        return numberOfCpuCores;
    }

    public void setNumberOfCpuCores(int numberOfCpuCores) {
        this.numberOfCpuCores = numberOfCpuCores;
    }

    public ComputeType getComputeType() {
        return computeType;
    }

    public void setComputeType(ComputeType computeType) {
        this.computeType = computeType;
    }

    public int getNumberOfGpus() {
        return numberOfGpus;
    }

    public void setNumberOfGpus(int numberOfGpus) {
        this.numberOfGpus = numberOfGpus;
    }

    public long getRamCapacityMB() {
        return ramCapacityMB;
    }

    public void setRamCapacityMB(long ramCapacityMB) {
        this.ramCapacityMB = ramCapacityMB;
    }

    public long getNetworkCapacityMbps() {
        return networkCapacityMbps;
    }

    public void setNetworkCapacityMbps(long networkCapacityMbps) {
        this.networkCapacityMbps = networkCapacityMbps;
    }

    public long getHardDriveCapacityMB() {
        return hardDriveCapacityMB;
    }

    public void setHardDriveCapacityMB(long hardDriveCapacityMB) {
        this.hardDriveCapacityMB = hardDriveCapacityMB;
    }

    public List<VM> getAssignedVMs() {
        return assignedVMs;
    }

    public void setAssignedVMs(List<VM> assignedVMs) {
        this.assignedVMs = assignedVMs;
    }

    public double getCurrentTotalPowerDraw() {
        return currentTotalPowerDraw;
    }

    public void setCurrentTotalPowerDraw(double currentTotalPowerDraw) {
        this.currentTotalPowerDraw = currentTotalPowerDraw;
    }

    public double getCurrentCpuPowerDraw() {
        return currentCpuPowerDraw;
    }

    public void setCurrentCpuPowerDraw(double currentCpuPowerDraw) {
        this.currentCpuPowerDraw = currentCpuPowerDraw;
    }

    public double getCurrentGpuPowerDraw() {
        return currentGpuPowerDraw;
    }

    public void setCurrentGpuPowerDraw(double currentGpuPowerDraw) {
        this.currentGpuPowerDraw = currentGpuPowerDraw;
    }

    public double getOtherComponentsPowerDraw() {
        return otherComponentsPowerDraw;
    }

    public void setOtherComponentsPowerDraw(double otherComponentsPowerDraw) {
        this.otherComponentsPowerDraw = otherComponentsPowerDraw;
    }

    public PowerModel getPowerModel() {
        return powerModel;
    }

    public void setPowerModel(PowerModel powerModel) {
        this.powerModel = powerModel;
    }

    public List<HostUtilizationRecord> getUtilizationHistory() {
        return utilizationHistory;
    }

    public Long getAssignedDatacenterId() {
        return assignedDatacenterId;
    }

    public void setAssignedDatacenterId(Long assignedDatacenterId) {
        this.assignedDatacenterId = assignedDatacenterId;
    }

    public long getTotalNumberOfSecondsWorking() {
        return totalNumberOfSecondsWorking;
    }

    public long getTotalNumberOfSecondsIdle() {
        return totalNumberOfSecondsIdle;
    }

    @Override
    public String toString() {
        return "Host{" +
                "id=" + id +
                ", activeSeconds=" + activeSeconds +
                ", secondsIDLE=" + secondsIDLE +
                ", secondsExecuting=" + secondsExecuting +
                ", instructionsPerSecond=" + instructionsPerSecond +
                ", numberOfCpuCores=" + numberOfCpuCores +
                ", computeType=" + computeType +
                ", numberOfGpus=" + numberOfGpus +
                ", assignedVMs=" + assignedVMs.size() +
                ", currentTotalPowerDraw=" + currentTotalPowerDraw +
                ", assignedDatacenterId=" + assignedDatacenterId +
                '}';
    }
}
