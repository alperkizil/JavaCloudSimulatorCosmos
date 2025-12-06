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

    // Host assignment tracking
    private Long assignedHostId;

    // Task execution tracking
    private Task currentExecutingTask;
    private long currentTaskProgress;

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

        // Initialize host assignment and task execution
        this.assignedHostId = null;
        this.currentExecutingTask = null;
        this.currentTaskProgress = 0;
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

    // Host assignment methods
    public Long getAssignedHostId() {
        return assignedHostId;
    }

    public void setAssignedHostId(Long hostId) {
        this.assignedHostId = hostId;
    }

    public boolean isAssignedToHost() {
        return assignedHostId != null;
    }

    /**
     * Activates the VM by assigning it to a host.
     */
    public void activate(long timestamp, Long hostId) {
        if (this.assignedHostId == null) {
            this.assignedHostId = hostId;
            this.vmState = VmState.QUEUED;
            this.activeSeconds = 0;
        }
    }

    /**
     * Starts the VM (changes state to RUNNING).
     */
    public void start() {
        this.vmState = VmState.RUNNING;
    }

    /**
     * Checks if VM can accept a task based on compute type compatibility.
     */
    public boolean canAcceptTask(Task task) {
        if (task.getWorkloadType() == WorkloadType.IDLE) return true;

        boolean requiresGpu = isGpuWorkload(task.getWorkloadType());
        if (requiresGpu && computeType == ComputeType.CPU_ONLY) {
            return false;
        }

        boolean requiresCpu = isCpuWorkload(task.getWorkloadType());
        if (requiresCpu && computeType == ComputeType.GPU_ONLY) {
            return false;
        }

        return true;
    }

    private boolean isGpuWorkload(WorkloadType type) {
        return type == WorkloadType.FURMARK ||
               type == WorkloadType.IMAGE_GEN_GPU ||
               type == WorkloadType.LLM_GPU;
    }

    private boolean isCpuWorkload(WorkloadType type) {
        return type == WorkloadType.SEVEN_ZIP ||
               type == WorkloadType.DATABASE ||
               type == WorkloadType.IMAGE_GEN_CPU ||
               type == WorkloadType.LLM_CPU ||
               type == WorkloadType.CINEBENCH ||
               type == WorkloadType.PRIME95SmallFFT;
    }

    /**
     * Executes tasks for one simulation second.
     */
    public void executeOneSecond(long currentTime) {
        if (vmState != VmState.RUNNING) {
            return;
        }

        // Get or start next task
        if (currentExecutingTask == null && !assignedTasks.isEmpty()) {
            currentExecutingTask = assignedTasks.peek();
            currentExecutingTask.startExecution(currentTime);
            currentTaskProgress = 0;
        }

        if (currentExecutingTask != null) {
            // Calculate IPS available for this task
            long availableIps = getTotalRequestedIps();

            // Execute instructions
            currentTaskProgress += availableIps;
            currentExecutingTask.executeInstructions(availableIps);

            // Calculate utilization based on workload type
            double[] utilization = calculateUtilization(currentExecutingTask.getWorkloadType());
            double cpuUtil = utilization[0];
            double gpuUtil = utilization[1];

            // Update current utilization
            currentUtilization.setActiveWorkloadType(currentExecutingTask.getWorkloadType());
            currentUtilization.setCpuUtilization(cpuUtil);
            currentUtilization.setGpuUtilization(gpuUtil);
            currentUtilization.incrementProgress();

            // Record utilization
            double powerDraw = calculatePowerDraw(cpuUtil, gpuUtil);
            recordUtilization(currentTime, currentExecutingTask.getId(),
                             currentExecutingTask.getWorkloadType(),
                             cpuUtil, gpuUtil, powerDraw);

            // Check if task is complete
            if (currentTaskProgress >= currentExecutingTask.getInstructionLength()) {
                currentExecutingTask.finishExecution(currentTime);
                finishTask(currentExecutingTask);
                currentExecutingTask = null;
                currentTaskProgress = 0;
                currentUtilization.resetToIdle();
            }
        } else {
            // VM is idle
            currentUtilization.resetToIdle();
        }
    }

    /**
     * Calculate utilization based on workload type.
     * Returns [cpuUtilization, gpuUtilization]
     */
    private double[] calculateUtilization(WorkloadType workloadType) {
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
            case IDLE:
            default:
                return new double[]{0.0, 0.0};
        }
    }

    /**
     * Calculate power draw (simplified).
     */
    private double calculatePowerDraw(double cpuUtil, double gpuUtil) {
        double basePower = 50.0;
        double cpuPower = cpuUtil * requestedVcpuCount * 30.0;
        double gpuPower = gpuUtil * requestedGpuCount * 200.0;
        return basePower + cpuPower + gpuPower;
    }

    /**
     * Gets completion percentage.
     */
    public double getCompletionPercentage() {
        if (assignedTasks.isEmpty() && finishedTasks.isEmpty()) {
            return 0.0;
        }
        int total = assignedTasks.size() + finishedTasks.size();
        return (double) finishedTasks.size() / total * 100.0;
    }

    /**
     * Checks if all tasks are finished.
     * Returns true if:
     * - All assigned tasks have completed (queue is empty and finished list is not empty), OR
     * - No tasks were ever assigned (both queue and finished list are empty)
     */
    public boolean hasFinishedAllTasks() {
        // If no tasks were ever assigned, consider it as "finished" (nothing to do)
        if (assignedTasks.isEmpty() && finishedTasks.isEmpty()) {
            return true;
        }
        // If there are pending tasks, not finished
        return assignedTasks.isEmpty();
    }

    /**
     * Checks if this VM has ever been assigned any tasks.
     */
    public boolean hasEverHadTasks() {
        return !assignedTasks.isEmpty() || !finishedTasks.isEmpty();
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
