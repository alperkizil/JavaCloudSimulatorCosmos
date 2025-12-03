package com.cloudsimulator.config;

import com.cloudsimulator.enums.ComputeType;

import java.io.Serializable;

/**
 * Configuration for a single Host.
 * Supports deep-copy for experiment variations.
 */
public class HostConfig implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    private long instructionsPerSecond;
    private int numberOfCpuCores;
    private ComputeType computeType;
    private int numberOfGpus;
    private long ramCapacityMB;
    private long networkCapacityMbps;
    private long hardDriveCapacityMB;
    private String powerModelName;

    public HostConfig() {}

    public HostConfig(long instructionsPerSecond, int numberOfCpuCores,
                     ComputeType computeType, int numberOfGpus,
                     long ramCapacityMB, long networkCapacityMbps,
                     long hardDriveCapacityMB, String powerModelName) {
        this.instructionsPerSecond = instructionsPerSecond;
        this.numberOfCpuCores = numberOfCpuCores;
        this.computeType = computeType;
        this.numberOfGpus = numberOfGpus;
        this.ramCapacityMB = ramCapacityMB;
        this.networkCapacityMbps = networkCapacityMbps;
        this.hardDriveCapacityMB = hardDriveCapacityMB;
        this.powerModelName = powerModelName;
    }

    // Copy constructor
    public HostConfig(HostConfig other) {
        this.instructionsPerSecond = other.instructionsPerSecond;
        this.numberOfCpuCores = other.numberOfCpuCores;
        this.computeType = other.computeType;
        this.numberOfGpus = other.numberOfGpus;
        this.ramCapacityMB = other.ramCapacityMB;
        this.networkCapacityMbps = other.networkCapacityMbps;
        this.hardDriveCapacityMB = other.hardDriveCapacityMB;
        this.powerModelName = other.powerModelName;
    }

    @Override
    public HostConfig clone() {
        return new HostConfig(this);
    }

    // Getters and Setters
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

    public String getPowerModelName() {
        return powerModelName;
    }

    public void setPowerModelName(String powerModelName) {
        this.powerModelName = powerModelName;
    }

    @Override
    public String toString() {
        return "HostConfig{" +
                "ips=" + instructionsPerSecond +
                ", cores=" + numberOfCpuCores +
                ", type=" + computeType +
                ", gpus=" + numberOfGpus +
                ", powerModel='" + powerModelName + '\'' +
                '}';
    }
}
