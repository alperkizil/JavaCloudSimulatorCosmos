package com.cloudsimulator.gui;

import com.cloudsimulator.enums.WorkloadType;

/**
 * Template for task configuration with instruction length range.
 * When generating configs, actual instruction lengths are randomized within the range
 * based on the experiment seed.
 */
public class TaskTemplate {
    private String namePrefix;
    private WorkloadType workloadType;
    private long minInstructionLength;
    private long maxInstructionLength;
    private int count;

    public TaskTemplate() {
        this.namePrefix = "Task";
        this.workloadType = WorkloadType.SEVEN_ZIP;
        this.minInstructionLength = 1000000000L;
        this.maxInstructionLength = 10000000000L;
        this.count = 1;
    }

    public TaskTemplate(String namePrefix, WorkloadType workloadType,
                        long minInstructionLength, long maxInstructionLength, int count) {
        this.namePrefix = namePrefix;
        this.workloadType = workloadType;
        this.minInstructionLength = minInstructionLength;
        this.maxInstructionLength = maxInstructionLength;
        this.count = count;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public WorkloadType getWorkloadType() {
        return workloadType;
    }

    public void setWorkloadType(WorkloadType workloadType) {
        this.workloadType = workloadType;
    }

    public long getMinInstructionLength() {
        return minInstructionLength;
    }

    public void setMinInstructionLength(long minInstructionLength) {
        this.minInstructionLength = minInstructionLength;
    }

    public long getMaxInstructionLength() {
        return maxInstructionLength;
    }

    public void setMaxInstructionLength(long maxInstructionLength) {
        this.maxInstructionLength = maxInstructionLength;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) x%d [%d-%d]",
            namePrefix, workloadType.name(), count, minInstructionLength, maxInstructionLength);
    }
}
