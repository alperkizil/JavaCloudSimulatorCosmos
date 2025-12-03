package com.cloudsimulator.config;

import com.cloudsimulator.enums.WorkloadType;

import java.io.Serializable;

/**
 * Configuration for a single Task.
 * Supports deep-copy for experiment variations.
 */
public class TaskConfig implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String userName;
    private long instructionLength;
    private WorkloadType workloadType;

    public TaskConfig() {}

    public TaskConfig(String name, String userName, long instructionLength, WorkloadType workloadType) {
        this.name = name;
        this.userName = userName;
        this.instructionLength = instructionLength;
        this.workloadType = workloadType;
    }

    // Copy constructor
    public TaskConfig(TaskConfig other) {
        this.name = other.name;
        this.userName = other.userName;
        this.instructionLength = other.instructionLength;
        this.workloadType = other.workloadType;
    }

    @Override
    public TaskConfig clone() {
        return new TaskConfig(this);
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public long getInstructionLength() {
        return instructionLength;
    }

    public void setInstructionLength(long instructionLength) {
        this.instructionLength = instructionLength;
    }

    public WorkloadType getWorkloadType() {
        return workloadType;
    }

    public void setWorkloadType(WorkloadType workloadType) {
        this.workloadType = workloadType;
    }

    @Override
    public String toString() {
        return "TaskConfig{" +
                "name='" + name + '\'' +
                ", user='" + userName + '\'' +
                ", type=" + workloadType +
                ", instructions=" + instructionLength +
                '}';
    }
}
