package com.cloudsimulator.config;

import com.cloudsimulator.enums.ComputeType;

import java.io.Serializable;

/**
 * Configuration for a single VM.
 * Supports deep-copy for experiment variations.
 */
public class VMConfig implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    private String userName;
    private long requestedIpsPerVcpu;
    private int requestedVcpuCount;
    private int requestedGpuCount;
    private long requestedRamMB;
    private long requestedStorageMB;
    private long requestedBandwidthMbps;
    private ComputeType computeType;

    public VMConfig() {}

    public VMConfig(String userName, long requestedIpsPerVcpu, int requestedVcpuCount,
                   int requestedGpuCount, long requestedRamMB, long requestedStorageMB,
                   long requestedBandwidthMbps, ComputeType computeType) {
        this.userName = userName;
        this.requestedIpsPerVcpu = requestedIpsPerVcpu;
        this.requestedVcpuCount = requestedVcpuCount;
        this.requestedGpuCount = requestedGpuCount;
        this.requestedRamMB = requestedRamMB;
        this.requestedStorageMB = requestedStorageMB;
        this.requestedBandwidthMbps = requestedBandwidthMbps;
        this.computeType = computeType;
    }

    // Copy constructor
    public VMConfig(VMConfig other) {
        this.userName = other.userName;
        this.requestedIpsPerVcpu = other.requestedIpsPerVcpu;
        this.requestedVcpuCount = other.requestedVcpuCount;
        this.requestedGpuCount = other.requestedGpuCount;
        this.requestedRamMB = other.requestedRamMB;
        this.requestedStorageMB = other.requestedStorageMB;
        this.requestedBandwidthMbps = other.requestedBandwidthMbps;
        this.computeType = other.computeType;
    }

    @Override
    public VMConfig clone() {
        return new VMConfig(this);
    }

    // Getters and Setters
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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

    public ComputeType getComputeType() {
        return computeType;
    }

    public void setComputeType(ComputeType computeType) {
        this.computeType = computeType;
    }

    @Override
    public String toString() {
        return "VMConfig{" +
                "user='" + userName + '\'' +
                ", vcpus=" + requestedVcpuCount +
                ", gpus=" + requestedGpuCount +
                ", type=" + computeType +
                '}';
    }
}
