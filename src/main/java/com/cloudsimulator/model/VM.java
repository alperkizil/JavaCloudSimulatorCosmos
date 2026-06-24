package com.cloudsimulator.model;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.VmState;
import com.cloudsimulator.enums.WorkloadType;
// Note: MeasurementBasedPowerModel is in the same package, no import needed

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
    private long effectiveIpsPerVcpu;        // Per-vCPU IPS after clamping to the assigned host's per-core speed (A2)
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

    // GPU binding: ids of the specific physical GPUs the host bound to this VM
    private List<Long> boundGpuIds;

    // CPU core binding: ids ("<processorId>-<coreIndex>") of the physical cores
    // the host bound 1:1 to this VM's vCPUs (identity bookkeeping)
    private List<String> boundCoreIds;

    // Per-vCPU FIFO scheduler state. Each vCPU is an execution lane that runs one
    // task at a time at the effective per-vCPU IPS. The VM's FIFO task queue
    // (assignedTasks) feeds the lanes: each tick, every free vCPU pulls the next
    // task from the head of the queue, so a VM runs up to requestedVcpuCount tasks
    // concurrently (single-threaded tasks, one task per vCPU lane).
    private final List<Task> runningTasks = new ArrayList<>();   // tasks occupying a vCPU lane (size <= requestedVcpuCount)

    /**
     * Constructor with custom specifications.
     */
    public VM(String userId, long requestedIpsPerVcpu, int requestedVcpuCount,
              int requestedGpuCount, long requestedRamMB, long requestedStorageMB,
              long requestedBandwidthMbps, ComputeType computeType) {
        this.id = idGenerator.incrementAndGet();
        this.userId = userId;
        this.requestedIpsPerVcpu = requestedIpsPerVcpu;
        // Until the VM is placed on a host, the effective per-vCPU speed equals the request.
        this.effectiveIpsPerVcpu = requestedIpsPerVcpu;
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
        this.boundGpuIds = new ArrayList<>();
        this.boundCoreIds = new ArrayList<>();
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

    /**
     * Records that the host bound a specific physical GPU to this VM.
     */
    public void addBoundGpuId(long gpuId) {
        this.boundGpuIds.add(gpuId);
    }

    /**
     * Clears this VM's GPU bindings (called when the host releases its GPUs).
     */
    public void clearBoundGpuIds() {
        this.boundGpuIds.clear();
    }

    /**
     * Gets the ids of the physical GPUs bound to this VM.
     */
    public List<Long> getBoundGpuIds() {
        return boundGpuIds;
    }

    /**
     * Gets the number of physical GPUs currently bound to this VM.
     */
    public int getBoundGpuCount() {
        return boundGpuIds.size();
    }

    /**
     * Records that the host bound a specific physical core to this VM.
     */
    public void addBoundCoreId(String coreId) {
        this.boundCoreIds.add(coreId);
    }

    /**
     * Clears this VM's core bindings (called when the host releases its cores).
     */
    public void clearBoundCoreIds() {
        this.boundCoreIds.clear();
    }

    /**
     * Gets the ids of the physical cores bound to this VM.
     */
    public List<String> getBoundCoreIds() {
        return boundCoreIds;
    }

    /**
     * Gets the number of physical cores currently bound to this VM.
     */
    public int getBoundCoreCount() {
        return boundCoreIds.size();
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
        return type.isGpuWorkload();
    }

    private boolean isCpuWorkload(WorkloadType type) {
        return type == WorkloadType.SEVEN_ZIP ||
               type == WorkloadType.DATABASE ||
               type == WorkloadType.IMAGE_GEN_CPU ||
               type == WorkloadType.LLM_CPU ||
               type == WorkloadType.CINEBENCH ||
               type == WorkloadType.PRIME95SmallFFT ||
               type == WorkloadType.VERACRYPT;
    }

    /**
     * Executes one simulation second under the per-vCPU FIFO scheduler.
     *
     * Each vCPU is an execution lane that runs a single task at the effective
     * per-vCPU IPS ({@link #getEffectiveIpsPerVcpu()}). The VM's FIFO task queue
     * feeds the lanes: any lane free at the start of the tick pulls the next
     * queued task. A lane that finishes a task mid-tick is only refilled on the
     * next tick, so the remainder of the finishing second is wasted — matching the
     * discrete ceiling-division timing the scheduling objectives assume.
     */
    public void executeOneSecond(long currentTime) {
        if (vmState != VmState.RUNNING) {
            return;
        }

        // Refill any vCPU lanes that are free at the start of this tick.
        fillLanes(currentTime);

        // Rebuild this tick's per-lane utilization snapshot from scratch.
        currentUtilization.resetToIdle();

        if (runningTasks.isEmpty()) {
            // No tasks to run: the VM is idle this tick.
            return;
        }

        long effIps = getEffectiveIpsPerVcpu();
        List<Task> completed = null;

        // Advance every busy vCPU lane by one second of work (one core's worth).
        for (Task task : runningTasks) {
            task.executeInstructions(effIps);

            double[] utilization = calculateUtilization(task.getWorkloadType());
            double cpuUtil = utilization[0];
            double gpuUtil = utilization[1];

            // Record this lane in the current-tick utilization (consumed by the
            // workload-aware host power model, which sums power across lanes).
            currentUtilization.addLane(task.getWorkloadType(), cpuUtil, gpuUtil);

            // Per-lane utilization history record (VM-level bookkeeping).
            double powerDraw = calculatePowerDraw(task.getWorkloadType(), cpuUtil, gpuUtil);
            recordUtilization(currentTime, task.getId(), task.getWorkloadType(),
                              cpuUtil, gpuUtil, powerDraw);

            // A task that reaches its instruction length finishes this tick; its
            // lane is freed below and refilled on the next tick.
            if (task.isComplete()) {
                task.finishExecution(currentTime);
                if (completed == null) {
                    completed = new ArrayList<>();
                }
                completed.add(task);
            }
        }

        // Retire completed tasks, freeing their lanes. Done after the loop so the
        // current-tick utilization above still reflects every lane that ran this
        // second (Host.updateState reads it after executeOneSecond).
        if (completed != null) {
            for (Task task : completed) {
                finishTask(task);
                runningTasks.remove(task);
            }
        }
    }

    /**
     * Fills idle vCPU lanes from the head of the FIFO task queue, up to
     * requestedVcpuCount concurrent tasks. Tasks already occupying a lane are
     * skipped; the next not-yet-started queued tasks are started in FIFO order.
     */
    private void fillLanes(long currentTime) {
        if (runningTasks.size() >= requestedVcpuCount) {
            return;
        }
        // GPU-workload concurrency is capped by the number of physical GPUs bound
        // to this VM: a GPU task needs one vCPU lane AND one GPU. CPU tasks are
        // bounded only by vCPU lanes. Head-of-line non-blocking: a GPU task that
        // cannot get a free GPU is skipped so later CPU tasks can still fill idle
        // lanes this tick. (gpuCap == 0 ⇒ unconstrained, avoids deadlock for a
        // misconfigured GPU-capable VM with no bound GPUs.)
        int gpuCap = getBoundGpuCount();
        int runningGpu = 0;
        for (Task running : runningTasks) {
            if (running.getWorkloadType().isGpuWorkload()) {
                runningGpu++;
            }
        }
        for (Task task : assignedTasks) {
            if (runningTasks.size() >= requestedVcpuCount) {
                break;
            }
            if (runningTasks.contains(task)) {
                continue; // already running on a lane
            }
            boolean gpu = task.getWorkloadType().isGpuWorkload();
            if (gpu && gpuCap > 0 && runningGpu >= gpuCap) {
                continue; // no free GPU: skip (head-of-line non-blocking)
            }
            task.startExecution(currentTime);
            runningTasks.add(task);
            if (gpu) {
                runningGpu++;
            }
        }
    }

    /**
     * Calculate utilization based on workload type.
     * Values are based on actual measurements from Dell Precision 7920 + Nvidia 5080 GPU.
     * Returns [cpuUtilization, gpuUtilization]
     */
    private double[] calculateUtilization(WorkloadType workloadType) {
        switch (workloadType) {
            case SEVEN_ZIP:
                return new double[]{1.0, 0.0};      // 100% CPU, 0% GPU
            case DATABASE:
                return new double[]{0.12, 0.0};     // 12% CPU, 0% GPU
            case FURMARK:
                return new double[]{0.08, 1.0};     // 8% CPU, 100% GPU
            case IMAGE_GEN_CPU:
                return new double[]{0.80, 0.0};     // 80% CPU, 0% GPU
            case IMAGE_GEN_GPU:
                return new double[]{0.30, 0.10};    // 30% CPU, 10% GPU
            case LLM_CPU:
                return new double[]{0.55, 0.0};     // 55% CPU, 0% GPU
            case LLM_GPU:
                return new double[]{0.12, 0.12};    // 12% CPU, 12% GPU
            case CINEBENCH:
                return new double[]{1.0, 0.0};      // 100% CPU, 0% GPU
            case PRIME95SmallFFT:
                return new double[]{1.0, 0.0};      // 100% CPU, 0% GPU
            case VERACRYPT:
                return new double[]{0.03, 0.0};     // 3% CPU, 0% GPU (disk-bound)
            case IDLE:
            default:
                return new double[]{0.0, 0.0};
        }
    }

    // Optional measurement-based power model for workload-aware calculations
    private MeasurementBasedPowerModel measurementBasedPowerModel;

    /**
     * Calculates the incremental power draw for a single vCPU lane running the
     * given workload. Uses the workload-aware empirical model when set, otherwise
     * the simplified utilization-based model.
     */
    private double calculatePowerDraw(WorkloadType workloadType, double cpuUtil, double gpuUtil) {
        if (measurementBasedPowerModel != null) {
            return measurementBasedPowerModel.calculateIncrementalPower(workloadType, cpuUtil, gpuUtil);
        }
        return calculatePowerDrawSimplified(cpuUtil, gpuUtil);
    }

    /**
     * Simplified power calculation based on utilization and VM resources.
     */
    private double calculatePowerDrawSimplified(double cpuUtil, double gpuUtil) {
        double basePower = 50.0;
        double cpuPower = cpuUtil * requestedVcpuCount * 30.0;
        double gpuPower = gpuUtil * requestedGpuCount * 200.0;
        return basePower + cpuPower + gpuPower;
    }

    /**
     * Sets the measurement-based power model for workload-aware power calculation.
     *
     * @param model The empirical power model to use
     */
    public void setMeasurementBasedPowerModel(MeasurementBasedPowerModel model) {
        this.measurementBasedPowerModel = model;
    }

    /**
     * Gets the measurement-based power model if set.
     *
     * @return The power model, or null if not set
     */
    public MeasurementBasedPowerModel getMeasurementBasedPowerModel() {
        return measurementBasedPowerModel;
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
     * Gets the workload type on the first busy vCPU lane (a representative for the
     * VM), or IDLE if no lane is busy. With the per-vCPU scheduler a VM may run
     * several workloads at once; use {@link #getActiveLaneUtilizations()} for the
     * full per-lane breakdown.
     *
     * @return The representative workload type
     */
    public WorkloadType getCurrentWorkloadType() {
        if (!runningTasks.isEmpty()) {
            return runningTasks.get(0).getWorkloadType();
        }
        return WorkloadType.IDLE;
    }

    /**
     * Gets the task on the first busy vCPU lane, or null if the VM is idle. With
     * the per-vCPU scheduler a VM may run up to requestedVcpuCount tasks at once;
     * this returns the head lane for callers expecting a single representative.
     *
     * @return The head-lane task, or null if idle
     */
    public Task getCurrentExecutingTask() {
        return runningTasks.isEmpty() ? null : runningTasks.get(0);
    }

    /**
     * Number of vCPU lanes currently busy (tasks running concurrently this tick).
     */
    public int getRunningTaskCount() {
        return runningTasks.size();
    }

    /**
     * Per-busy-lane utilizations for the current tick (one entry per running
     * task). Empty when the VM is idle. Consumed by the workload-aware host power
     * model to sum power across concurrently running workloads.
     */
    public List<VMUtilization.LaneUtilization> getActiveLaneUtilizations() {
        return currentUtilization.getLanes();
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

    /**
     * Per-vCPU IPS actually delivered after clamping to the assigned host's
     * per-core speed: a vCPU cannot run faster than a physical core (A2). Set
     * when the VM is placed on a host; defaults to the requested per-vCPU IPS
     * while the VM is unplaced.
     */
    public long getEffectiveIpsPerVcpu() {
        return effectiveIpsPerVcpu;
    }

    public void setEffectiveIpsPerVcpu(long effectiveIpsPerVcpu) {
        this.effectiveIpsPerVcpu = effectiveIpsPerVcpu;
    }

    /**
     * Aggregate effective IPS across all vCPU lanes (effective per-vCPU × vCPU
     * count). This is the VM's peak throughput when every lane is busy; a single
     * task runs at {@link #getEffectiveIpsPerVcpu()}, not this aggregate.
     */
    public long getEffectiveTotalIps() {
        return effectiveIpsPerVcpu * requestedVcpuCount;
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

    /**
     * Resets VM execution state for rescheduling scenarios (e.g., Pareto front evaluation).
     * Clears task queues, execution counters, and utilization history.
     * Does NOT reset host assignment or resource specifications.
     */
    public void resetForRescheduling() {
        // Clear task queues
        this.assignedTasks = new LinkedList<>();
        this.finishedTasks = new ArrayList<>();

        // Reset execution state
        this.runningTasks.clear();

        // Reset timing counters
        this.activeSeconds = 0;
        this.secondsIDLE = 0;
        this.secondsExecuting = 0;
        this.totalOpenSeconds = 0;
        this.totalIDLESeconds = 0;
        this.totalActiveWorkloadSeconds = 0;

        // Reset utilization tracking
        this.currentUtilization = new VMUtilization();
        this.utilizationHistory = new ArrayList<>();
        this.taskRunningSecondsMap = new HashMap<>();
        this.taskWorkloadSecondsMap = new HashMap<>();

        // Reset state to RUNNING (VM is already placed, ready for new tasks)
        // Keep assignedHostId - VM stays on its host
        if (this.assignedHostId != null) {
            this.vmState = VmState.RUNNING;
        } else {
            this.vmState = VmState.CREATED;
        }
    }
}
