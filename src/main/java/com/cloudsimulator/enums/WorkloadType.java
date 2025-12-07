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
    IDLE                // No Workload
}
