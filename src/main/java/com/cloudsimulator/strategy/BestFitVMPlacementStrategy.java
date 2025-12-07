package com.cloudsimulator.strategy;

import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.VM;

import java.util.List;
import java.util.Optional;

/**
 * Best Fit VM Placement Strategy.
 *
 * Algorithm:
 * 1. Scan ALL eligible hosts that have capacity for the VM
 * 2. Select the host that would have the SMALLEST remaining capacity after placement
 * 3. This minimizes resource fragmentation by tightly packing VMs
 *
 * "Best fit" score calculation:
 * - For each host, calculate what the remaining resources would be after placing the VM
 * - remaining_score = (remaining_cpus / total_cpus + remaining_ram / total_ram) / 2
 * - Lower score = tighter fit = preferred
 *
 * Characteristics:
 * - Time Complexity: O(n) where n = number of candidate hosts
 * - Reduces resource fragmentation
 * - Better packing efficiency than First Fit
 * - Slightly more computational overhead than First Fit
 *
 * Use Case: Maximize resource utilization, reduce wasted capacity
 */
public class BestFitVMPlacementStrategy implements VMPlacementStrategy {

    @Override
    public Optional<Host> selectHost(VM vm, List<Host> candidateHosts) {
        if (vm == null || candidateHosts == null || candidateHosts.isEmpty()) {
            return Optional.empty();
        }

        Host bestHost = null;
        double lowestRemainingScore = Double.MAX_VALUE;

        for (Host host : candidateHosts) {
            // Check if host has capacity for this VM
            if (!host.hasCapacityForVM(vm)) {
                continue;
            }

            // Calculate remaining resources after placement
            double remainingScore = calculateRemainingScore(host, vm);

            // Select host with lowest remaining score (tightest fit)
            if (remainingScore < lowestRemainingScore) {
                lowestRemainingScore = remainingScore;
                bestHost = host;
            }
        }

        return Optional.ofNullable(bestHost);
    }

    /**
     * Calculates the remaining resource score after placing the VM.
     * Lower score indicates a tighter fit (less wasted resources).
     *
     * @param host The host to evaluate
     * @param vm   The VM to place
     * @return Remaining resource score (0.0 to 1.0, lower is better fit)
     */
    private double calculateRemainingScore(Host host, VM vm) {
        // Calculate remaining CPU ratio after placement
        int remainingCpus = host.getAvailableCpuCores() - vm.getRequestedVcpuCount();
        double cpuRatio = (double) remainingCpus / host.getNumberOfCpuCores();

        // Calculate remaining RAM ratio after placement
        long remainingRam = host.getAvailableRamMB() - vm.getRequestedRamMB();
        double ramRatio = (double) remainingRam / host.getRamCapacityMB();

        // Calculate remaining GPU ratio (if host has GPUs)
        double gpuRatio = 0.0;
        if (host.getNumberOfGpus() > 0) {
            int remainingGpus = host.getAvailableGpus() - vm.getRequestedGpuCount();
            gpuRatio = (double) remainingGpus / host.getNumberOfGpus();
        }

        // Combine ratios (GPU only if applicable)
        if (host.getNumberOfGpus() > 0) {
            return (cpuRatio + ramRatio + gpuRatio) / 3.0;
        } else {
            return (cpuRatio + ramRatio) / 2.0;
        }
    }

    @Override
    public String getStrategyName() {
        return "Best Fit";
    }

    @Override
    public String getDescription() {
        return "Places each VM on the host that would have the smallest remaining capacity, " +
               "minimizing resource fragmentation and maximizing packing efficiency.";
    }
}
