package com.cloudsimulator.strategy;

import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.VM;

import java.util.List;
import java.util.Optional;

/**
 * Load Balancing VM Placement Strategy.
 *
 * Algorithm:
 * 1. Scan all eligible hosts that have capacity for the VM
 * 2. Select the host with the LOWEST current utilization
 * 3. CPU and GPU utilization are considered SEPARATELY
 *
 * Utilization calculation:
 * - CPU utilization = allocated_cpus / total_cpus
 * - GPU utilization = allocated_gpus / total_gpus (if host has GPUs)
 * - RAM utilization = allocated_ram / total_ram
 *
 * Selection logic:
 * - For CPU-only VMs: prefer hosts with lowest CPU utilization
 * - For GPU-only VMs: prefer hosts with lowest GPU utilization
 * - For Mixed VMs: prefer hosts with lowest combined (CPU + GPU) utilization
 * - RAM utilization is used as a tiebreaker
 *
 * Characteristics:
 * - Time Complexity: O(n) where n = number of candidate hosts
 * - Distributes VMs evenly across hosts
 * - Prevents hotspots and overloading
 * - May result in more active hosts than consolidation strategies
 *
 * Use Case: Fault tolerance, even workload distribution, prevent single point of failure
 */
public class LoadBalancingVMPlacementStrategy implements VMPlacementStrategy {

    @Override
    public Optional<Host> selectHost(VM vm, List<Host> candidateHosts) {
        if (vm == null || candidateHosts == null || candidateHosts.isEmpty()) {
            return Optional.empty();
        }

        Host bestHost = null;
        double lowestUtilization = Double.MAX_VALUE;

        boolean vmNeedsGpu = vm.getRequestedGpuCount() > 0;
        boolean vmNeedsCpu = vm.getRequestedVcpuCount() > 0;

        for (Host host : candidateHosts) {
            // Check if host has capacity for this VM
            if (!host.hasCapacityForVM(vm)) {
                continue;
            }

            // Calculate utilization based on VM requirements
            double utilization = calculateUtilization(host, vmNeedsCpu, vmNeedsGpu);

            // Select host with lowest utilization
            if (utilization < lowestUtilization) {
                lowestUtilization = utilization;
                bestHost = host;
            }
        }

        return Optional.ofNullable(bestHost);
    }

    /**
     * Calculates the current utilization of a host, considering CPU and GPU separately.
     *
     * @param host        The host to evaluate
     * @param needsCpu    Whether the VM requires CPU resources
     * @param needsGpu    Whether the VM requires GPU resources
     * @return Utilization score (0.0 to 1.0, lower means less loaded)
     */
    private double calculateUtilization(Host host, boolean needsCpu, boolean needsGpu) {
        // Calculate CPU utilization
        double cpuUtilization = 0.0;
        if (host.getNumberOfCpuCores() > 0) {
            int allocatedCpus = host.getNumberOfCpuCores() - host.getAvailableCpuCores();
            cpuUtilization = (double) allocatedCpus / host.getNumberOfCpuCores();
        }

        // Calculate GPU utilization
        double gpuUtilization = 0.0;
        if (host.getNumberOfGpus() > 0) {
            int allocatedGpus = host.getNumberOfGpus() - host.getAvailableGpus();
            gpuUtilization = (double) allocatedGpus / host.getNumberOfGpus();
        }

        // Calculate RAM utilization (used as tiebreaker)
        double ramUtilization = 0.0;
        if (host.getRamCapacityMB() > 0) {
            long allocatedRam = host.getRamCapacityMB() - host.getAvailableRamMB();
            ramUtilization = (double) allocatedRam / host.getRamCapacityMB();
        }

        // Select utilization based on VM requirements
        if (needsGpu && needsCpu) {
            // Mixed VM: consider both CPU and GPU equally, with RAM as minor factor
            return (cpuUtilization + gpuUtilization) / 2.0 + (ramUtilization * 0.1);
        } else if (needsGpu) {
            // GPU-only VM: prioritize GPU utilization, CPU and RAM as tiebreakers
            return gpuUtilization + (cpuUtilization * 0.1) + (ramUtilization * 0.05);
        } else {
            // CPU-only VM: prioritize CPU utilization, RAM as tiebreaker
            return cpuUtilization + (ramUtilization * 0.1);
        }
    }

    @Override
    public String getStrategyName() {
        return "Load Balancing";
    }

    @Override
    public String getDescription() {
        return "Places each VM on the host with the lowest current utilization. " +
               "CPU and GPU utilization are considered separately based on VM requirements. " +
               "Distributes load evenly to prevent hotspots and ensure fault tolerance.";
    }
}
