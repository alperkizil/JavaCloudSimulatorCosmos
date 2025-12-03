package com.cloudsimulator.model;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.VmState;
import com.cloudsimulator.enums.WorkloadType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a Virtual Machine that runs on a Host and executes Tasks.
 */
public class VM {
    private static final AtomicLong idGenerator = new AtomicLong(0);

    // Identity and timing
    private final long id;
    private long activeSeconds;              // Starts when assigned to a Host
    private long secondsIDLE;                // Seconds spent idle after assignment
    private long secondsExecuting;           // Seconds spent executing at least one task

    // Resource requests
    private long requestedIpsPerVcpu;        // Instructions per second per vCPU
    private int requestedVcpuCount;          // Number of virtual CPU cores
    private int requestedGpuCount;           // Number of GPUs (0 for CPU-only VMs)
    private long requestedRamMB;             // Requested RAM in megabytes
    private long requestedStorageMB;         // Requested storage in megabytes
    private long requestedBandwidthMbps;     // Requested bandwidth in Mbps

    // Task management
    private Queue<Task> assignedTasks;       // Runtime queue of tasks to execute
    private List<Task> finishedTasks;        // List of completed tasks

    // User and state
    private String userId;                   // Which user this VM belongs to
    private VmState vmState;                 // Current state of the VM
    private ComputeType computeType;         // Type of compute (CPU/GPU/MIXED)

    // Time tracking
    private long totalOpenSeconds;           // Total seconds in RUNNING, PAUSED, STOPPED (powered on)
    private long totalIDLESeconds;           // Total time spent doing nothing
    private long totalActiveWorkloadSeconds; // Total time spent RUNNING workloads

    // Utilization tracking
    private VMUtilization currentUtilization; // Current utilization state
    private List<VMUtilizationRecord> utilizationHistory;
    private Map<Long, Long> taskRunningSecondsMap;  // Task ID -> seconds running
    private Map<Long, Map<WorkloadType, Long>> taskWorkloadSecondsMap;  // Task ID -> Workload -> seconds

    /**
     * Constructor with custom specifications.
     */
    public VM(String userId, long requestedIpsPerVcpu, int requestedVcpuCount,
              int requestedGpuCount, long requestedRamMB, long requestedStorageMB,
              long requestedBandwidthMbps, ComputeType computeType) {
        this.id = idGenerator.incrementAndGet();
        this.userId = userId;
        this.requestedIpsPerVcpu = requestedIpsPerVcpu;
        this.requestedVcpuCount = requestedVcpuCount;
        this.requestedGpuCount = requestedGpuCount;
        this.requestedRamMB = requestedRamMB;
        this.requestedStorageMB = requestedStorageMB;
        this.requestedBandwidthMbps = requestedBandwidthMbps;
        this.computeType = computeType;

        // Initialize state
        this.vmState = VmState.CREATED;
        this.activeSeconds = 0;
        this.secondsIDLE = 0;
        this.secondsExecuting = 0;
        this.totalOpenSeconds = 0;
        this.totalIDLESeconds = 0;
        this.totalActiveWorkloadSeconds = 0;

        // Initialize task management
        this.assignedTasks = new LinkedList<>();
        this.finishedTasks = new ArrayList<>();

        // Initialize utilization tracking
        this.currentUtilization = new VMUtilization(); // Starts in IDLE state
        this.utilizationHistory = new ArrayList<>();
        this.taskRunningSecondsMap = new HashMap<>();
        this.taskWorkloadSecondsMap = new HashMap<>();
    }

    /**
     * Default constructor with typical VM specifications.
     * CPU-only VM with 4 vCPUs, 8GB RAM, 100GB storage, 1Gbps bandwidth.
     */
    public VM(String userId) {
        this(userId, 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY);
    }

    /**
     * Assigns a task to this VM's execution queue.
     */
    public void assignTask(Task task) {
        this.assignedTasks.add(task);
        this.taskRunningSecondsMap.put(task.getId(), 0L);
        this.taskWorkloadSecondsMap.put(task.getId(), new HashMap<>());
    }

    /**
     * Moves a task from the execution queue to finished tasks.
     */
    public void finishTask(Task task) {
        this.assignedTasks.remove(task);
        this.finishedTasks.add(task);
    }

    /**
     * Gets the next task to execute (peek at queue).
     */
    public Task getNextTask() {
        return assignedTasks.peek();
    }

    /**
     * Records utilization for a specific task at the current simulation time.
     */
    public void recordUtilization(long timestamp, long taskId, WorkloadType workloadType,
                                  double cpuUtilization, double gpuUtilization, double powerDraw) {
        // Create utilization record
        VMUtilizationRecord record = new VMUtilizationRecord(
            timestamp, taskId, workloadType, cpuUtilization, gpuUtilization, powerDraw
        );
        utilizationHistory.add(record);

        // Update task running seconds
        taskRunningSecondsMap.merge(taskId, 1L, Long::sum);

        // Update task workload seconds
        Map<WorkloadType, Long> workloadMap = taskWorkloadSecondsMap.get(taskId);
        if (workloadMap != null) {
            workloadMap.merge(workloadType, 1L, Long::sum);
        }
    }

    /**
     * Updates VM state for the current simulation second.
     * Should be called every simulation tick.
     */
    public void updateState() {
        activeSeconds++;

        // Update total open seconds if VM is powered on
        if (vmState == VmState.RUNNING || vmState == VmState.PAUSED || vmState == VmState.STOPPED) {
            totalOpenSeconds++;
        }

        // Update execution state
        if (vmState == VmState.RUNNING && !assignedTasks.isEmpty()) {
            secondsExecuting++;
            totalActiveWorkloadSeconds++;
        } else if (vmState == VmState.RUNNING && assignedTasks.isEmpty()) {
            secondsIDLE++;
            totalIDLESeconds++;
        }
    }

    /**
     * Gets the total seconds a specific task has been running on this VM.
     */
    public long getTaskRunningSeconds(long taskId) {
        return taskRunningSecondsMap.getOrDefault(taskId, 0L);
    }

    /**
     * Gets the seconds a task spent executing a specific workload type.
     */
    public long getTaskWorkloadSeconds(long taskId, WorkloadType workloadType) {
        Map<WorkloadType, Long> workloadMap = taskWorkloadSecondsMap.get(taskId);
        if (workloadMap != null) {
            return workloadMap.getOrDefault(workloadType, 0L);
        }
        return 0L;
    }

    /**
     * Calculates total power draw for a specific task based on utilization history.
     */
    public double calculateTaskTotalPowerDraw(long taskId) {
        return utilizationHistory.stream()
            .filter(record -> record.getTaskId() == taskId)
            .mapToDouble(VMUtilizationRecord::getPowerDraw)
            .sum();
    }

    /**
     * Calculates the total requested IPS for this VM.
     */
    public long getTotalRequestedIps() {
        return requestedIpsPerVcpu * requestedVcpuCount;
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

    public long getRequestedIpsPerVcpu() {
        return requestedIpsPerVcpu;
    }

    public void setRequestedIpsPerVcpu(long requestedIpsPerVcpu) {
        this.requestedIpsPerVcpu = requestedIpsPerVcpu;
    }

    public int getRequestedVcpuCount() {
        return requestedVcpuCount;
    }

    public void setRequestedVcpuCount(int requestedVcpuCount) {
        this.requestedVcpuCount = requestedVcpuCount;
    }

    public int getRequestedGpuCount() {
        return requestedGpuCount;
    }

    public void setRequestedGpuCount(int requestedGpuCount) {
        this.requestedGpuCount = requestedGpuCount;
    }

    public long getRequestedRamMB() {
        return requestedRamMB;
    }

    public void setRequestedRamMB(long requestedRamMB) {
        this.requestedRamMB = requestedRamMB;
    }

    public long getRequestedStorageMB() {
        return requestedStorageMB;
    }

    public void setRequestedStorageMB(long requestedStorageMB) {
        this.requestedStorageMB = requestedStorageMB;
    }

    public long getRequestedBandwidthMbps() {
        return requestedBandwidthMbps;
    }

    public void setRequestedBandwidthMbps(long requestedBandwidthMbps) {
        this.requestedBandwidthMbps = requestedBandwidthMbps;
    }

    public Queue<Task> getAssignedTasks() {
        return assignedTasks;
    }

    public List<Task> getFinishedTasks() {
        return finishedTasks;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public VmState getVmState() {
        return vmState;
    }

    public void setVmState(VmState vmState) {
        this.vmState = vmState;
    }

    public ComputeType getComputeType() {
        return computeType;
    }

    public void setComputeType(ComputeType computeType) {
        this.computeType = computeType;
    }

    public long getTotalOpenSeconds() {
        return totalOpenSeconds;
    }

    public void setTotalOpenSeconds(long totalOpenSeconds) {
        this.totalOpenSeconds = totalOpenSeconds;
    }

    public long getTotalIDLESeconds() {
        return totalIDLESeconds;
    }

    public void setTotalIDLESeconds(long totalIDLESeconds) {
        this.totalIDLESeconds = totalIDLESeconds;
    }

    public long getTotalActiveWorkloadSeconds() {
        return totalActiveWorkloadSeconds;
    }

    public void setTotalActiveWorkloadSeconds(long totalActiveWorkloadSeconds) {
        this.totalActiveWorkloadSeconds = totalActiveWorkloadSeconds;
    }

    public VMUtilization getCurrentUtilization() {
        return currentUtilization;
    }

    public void setCurrentUtilization(VMUtilization currentUtilization) {
        this.currentUtilization = currentUtilization;
    }

    public List<VMUtilizationRecord> getUtilizationHistory() {
        return utilizationHistory;
    }

    @Override
    public String toString() {
        return "VM{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", vmState=" + vmState +
                ", computeType=" + computeType +
                ", requestedVcpuCount=" + requestedVcpuCount +
                ", requestedGpuCount=" + requestedGpuCount +
                ", activeSeconds=" + activeSeconds +
                ", secondsIDLE=" + secondsIDLE +
                ", secondsExecuting=" + secondsExecuting +
                ", assignedTasks=" + assignedTasks.size() +
                ", finishedTasks=" + finishedTasks.size() +
                '}';
    }
}
