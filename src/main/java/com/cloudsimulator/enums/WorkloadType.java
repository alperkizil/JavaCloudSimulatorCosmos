package com.cloudsimulator.enums;

/**
 * Enum representing different types of workloads that can be executed.
 */
public enum WorkloadType {
    SEVEN_ZIP,          // A 7zip compression operation
    DATABASE,           // A Relational Database transaction
    FURMARK,            // A GPU Full Power application
    IMAGE_GEN_CPU,      // AI Workload that taps into CPU
    IMAGE_GEN_GPU,      // AI Workload that taps into GPU
    LLM_CPU,            // AI Workload that taps into CPU
    LLM_GPU,            // AI Workload that taps into GPU
    CINEBENCH,          // Heavy CPU utilizing Application
    PRIME95SmallFFT,    // CPU saturating utilizing Application
    VERACRYPT,          // Disk encryption/decryption (CPU-intensive AES operations)
    IDLE;               // No Workload

    /**
     * GPU-accelerated workloads: they require a physical GPU and, in the
     * simulator, occupy one of the VM's bound GPUs while running (concurrency
     * capped by bound GPU count). Single source of truth for VM.canAcceptTask,
     * the per-vCPU lane scheduler (LaneSchedule), and the energy-aware heuristic.
     */
    public boolean isGpuWorkload() {
        return this == FURMARK || this == IMAGE_GEN_GPU || this == LLM_GPU;
    }
}
