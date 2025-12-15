package com.cloudsimulator.model;

import com.cloudsimulator.enums.TaskExecutionStatus;
import com.cloudsimulator.enums.WorkloadType;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a Task that is executed by a VM.
 * Tasks have a specific workload type and instruction length.
 */
public class Task {
    private static final AtomicLong idGenerator = new AtomicLong(0);

    // Identity
    private final long id;
    private String name;
    private String userId;                    // Which user created the task

    // Execution details
    private long instructionLength;           // How many instructions to execute
    private Long assignedVmId;                // Which VM this task is assigned to (null if not assigned)
    private WorkloadType workloadType;        // Type of workload to execute

    // Timing information
    private long taskCreationTime;            // Seconds when task was created in the system
    private Long taskAssignmentTime;          // Seconds when task was assigned to VM (null if not assigned)
    private Long taskExecStartTime;           // Seconds when task started executing (null if not started)
    private Long taskExecEndTime;             // Seconds when task finished executing (null if not finished)

    // Execution status
    private TaskExecutionStatus taskExecutionStatus;
    private long taskCpuExecTime;             // How many seconds spent executing

    // Execution progress tracking
    private long instructionsExecuted;

    /**
     * Full constructor.
     */
    public Task(String name, String userId, long instructionLength,
                WorkloadType workloadType, long taskCreationTime) {
        this.id = idGenerator.incrementAndGet();
        this.name = name;
        this.userId = userId;
        this.instructionLength = instructionLength;
        this.workloadType = workloadType;
        this.taskCreationTime = taskCreationTime;

        // Initialize nullable fields
        this.assignedVmId = null;
        this.taskAssignmentTime = null;
        this.taskExecStartTime = null;
        this.taskExecEndTime = null;

        // Initialize execution status
        this.taskExecutionStatus = TaskExecutionStatus.NOT_EXECUTED;
        this.taskCpuExecTime = 0;
        this.instructionsExecuted = 0;
    }

    /**
     * Simplified constructor without creation time (will be set to 0).
     */
    public Task(String name, String userId, long instructionLength, WorkloadType workloadType) {
        this(name, userId, instructionLength, workloadType, 0);
    }

    /**
     * Assigns this task to a VM.
     */
    public void assignToVM(long vmId, long assignmentTime) {
        this.assignedVmId = vmId;
        this.taskAssignmentTime = assignmentTime;
    }

    /**
     * Starts execution of this task.
     */
    public void startExecution(long startTime) {
        this.taskExecStartTime = startTime;
        this.taskExecutionStatus = TaskExecutionStatus.EXECUTING;
    }

    /**
     * Finishes execution of this task.
     */
    public void finishExecution(long endTime) {
        this.taskExecEndTime = endTime;
        this.taskExecutionStatus = TaskExecutionStatus.EXECUTED;

        // Calculate total execution time
        if (taskExecStartTime != null) {
            this.taskCpuExecTime = endTime - taskExecStartTime;
        }
    }

    /**
     * Increments the CPU execution time by one second.
     */
    public void incrementExecTime() {
        this.taskCpuExecTime++;
    }

    /**
     * Checks if the task is assigned to a VM.
     */
    public boolean isAssigned() {
        return assignedVmId != null;
    }

    /**
     * Checks if the task has started execution.
     */
    public boolean isExecuting() {
        return taskExecutionStatus == TaskExecutionStatus.EXECUTING;
    }

    /**
     * Checks if the task has completed execution.
     */
    public boolean isCompleted() {
        return taskExecutionStatus == TaskExecutionStatus.EXECUTED;
    }

    /**
     * Executes the specified number of instructions.
     */
    public void executeInstructions(long instructions) {
        instructionsExecuted += instructions;
        if (instructionsExecuted >= instructionLength) {
            instructionsExecuted = instructionLength;
        }
    }

    /**
     * Gets instructions executed so far.
     */
    public long getInstructionsExecuted() {
        return instructionsExecuted;
    }

    /**
     * Gets progress percentage (0.0 to 100.0).
     */
    public double getProgressPercentage() {
        if (instructionLength == 0) {
            return 100.0; // Task with no instructions is considered complete
        }
        return (double) instructionsExecuted / instructionLength * 100.0;
    }

    /**
     * Gets remaining instructions to complete.
     */
    public long getRemainingInstructions() {
        return Math.max(0, instructionLength - instructionsExecuted);
    }

    /**
     * Checks if task is complete.
     */
    public boolean isComplete() {
        return instructionsExecuted >= instructionLength;
    }

    /**
     * Gets estimated remaining time given available IPS.
     */
    public long getEstimatedRemainingTime(long ipsAvailable) {
        if (ipsAvailable == 0) return Long.MAX_VALUE;
        // Use ceiling division to ensure we don't underestimate remaining time
        long remaining = getRemainingInstructions();
        return (remaining + ipsAvailable - 1) / ipsAvailable;
    }

    /**
     * Resets task execution state for retry scenarios.
     * Does NOT reset assignment (use resetForRescheduling for that).
     */
    public void reset() {
        instructionsExecuted = 0;
        taskExecutionStatus = TaskExecutionStatus.NOT_EXECUTED;
        taskExecStartTime = null;
        taskExecEndTime = null;
        taskCpuExecTime = 0;
    }

    /**
     * Fully resets task for rescheduling scenarios (e.g., Pareto front evaluation).
     * Resets both assignment and execution state.
     */
    public void resetForRescheduling() {
        // Reset assignment
        assignedVmId = null;
        taskAssignmentTime = null;

        // Reset execution state
        instructionsExecuted = 0;
        taskExecutionStatus = TaskExecutionStatus.NOT_EXECUTED;
        taskExecStartTime = null;
        taskExecEndTime = null;
        taskCpuExecTime = 0;
    }

    /**
     * Gets the waiting time (time between creation and start of execution).
     */
    public Long getWaitingTime() {
        if (taskExecStartTime != null) {
            return taskExecStartTime - taskCreationTime;
        }
        return null;
    }

    /**
     * Gets the turnaround time (time between creation and completion).
     */
    public Long getTurnaroundTime() {
        if (taskExecEndTime != null) {
            return taskExecEndTime - taskCreationTime;
        }
        return null;
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getInstructionLength() {
        return instructionLength;
    }

    public void setInstructionLength(long instructionLength) {
        this.instructionLength = instructionLength;
    }

    public Long getAssignedVmId() {
        return assignedVmId;
    }

    public void setAssignedVmId(Long assignedVmId) {
        this.assignedVmId = assignedVmId;
    }

    public WorkloadType getWorkloadType() {
        return workloadType;
    }

    public void setWorkloadType(WorkloadType workloadType) {
        this.workloadType = workloadType;
    }

    public long getTaskCreationTime() {
        return taskCreationTime;
    }

    public void setTaskCreationTime(long taskCreationTime) {
        this.taskCreationTime = taskCreationTime;
    }

    public Long getTaskAssignmentTime() {
        return taskAssignmentTime;
    }

    public void setTaskAssignmentTime(Long taskAssignmentTime) {
        this.taskAssignmentTime = taskAssignmentTime;
    }

    public Long getTaskExecStartTime() {
        return taskExecStartTime;
    }

    public void setTaskExecStartTime(Long taskExecStartTime) {
        this.taskExecStartTime = taskExecStartTime;
    }

    public Long getTaskExecEndTime() {
        return taskExecEndTime;
    }

    public void setTaskExecEndTime(Long taskExecEndTime) {
        this.taskExecEndTime = taskExecEndTime;
    }

    public TaskExecutionStatus getTaskExecutionStatus() {
        return taskExecutionStatus;
    }

    public void setTaskExecutionStatus(TaskExecutionStatus taskExecutionStatus) {
        this.taskExecutionStatus = taskExecutionStatus;
    }

    public long getTaskCpuExecTime() {
        return taskCpuExecTime;
    }

    public void setTaskCpuExecTime(long taskCpuExecTime) {
        this.taskCpuExecTime = taskCpuExecTime;
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", userId='" + userId + '\'' +
                ", instructionLength=" + instructionLength +
                ", workloadType=" + workloadType +
                ", assignedVmId=" + assignedVmId +
                ", taskExecutionStatus=" + taskExecutionStatus +
                ", taskCreationTime=" + taskCreationTime +
                ", taskExecStartTime=" + taskExecStartTime +
                ", taskExecEndTime=" + taskExecEndTime +
                ", taskCpuExecTime=" + taskCpuExecTime +
                '}';
    }
}
