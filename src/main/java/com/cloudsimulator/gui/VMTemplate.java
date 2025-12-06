package com.cloudsimulator.gui;

import com.cloudsimulator.enums.ComputeType;

/**
 * Template for VM configuration.
 */
public class VMTemplate {
    private long requestedIpsPerVcpu;
    private int requestedVcpuCount;
    private int requestedGpuCount;
    private long requestedRamMB;
    private long requestedStorageMB;
    private long requestedBandwidthMbps;
    private ComputeType computeType;

    public VMTemplate() {
        this.requestedIpsPerVcpu = 2000000000L;
        this.requestedVcpuCount = 4;
        this.requestedGpuCount = 0;
        this.requestedRamMB = 8192;
        this.requestedStorageMB = 102400;
        this.requestedBandwidthMbps = 1000;
        this.computeType = ComputeType.CPU_ONLY;
    }

    public VMTemplate(long requestedIpsPerVcpu, int requestedVcpuCount, int requestedGpuCount,
                      long requestedRamMB, long requestedStorageMB, long requestedBandwidthMbps,
                      ComputeType computeType) {
        this.requestedIpsPerVcpu = requestedIpsPerVcpu;
        this.requestedVcpuCount = requestedVcpuCount;
        this.requestedGpuCount = requestedGpuCount;
        this.requestedRamMB = requestedRamMB;
        this.requestedStorageMB = requestedStorageMB;
        this.requestedBandwidthMbps = requestedBandwidthMbps;
        this.computeType = computeType;
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
        return String.format("%s VM: %d vCPUs, %d GPUs, %d MB RAM",
            computeType.name(), requestedVcpuCount, requestedGpuCount, requestedRamMB);
    }
}
