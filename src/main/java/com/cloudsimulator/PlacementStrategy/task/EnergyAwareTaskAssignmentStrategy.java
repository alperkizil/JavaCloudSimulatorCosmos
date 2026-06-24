package com.cloudsimulator.PlacementStrategy.task;

import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.EmpiricalWorkloadProfile;
import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/**
 * Energy-aware task assignment: per-task greedy minimisation of marginal energy cost.
 *
 * For each task, pick the VM v that minimises the incremental energy introduced
 * by adding the task to v under the per-vCPU FIFO scheduler:
 *
 *   execTicks   = ceil(task.instructions / v.effectiveIpsPerVcpu)   // one lane
 *   execEnergy  = incrementalPower(task.workloadType, v) * execTicks
 *   newFinish   = max(v.busiestLane, v.leastLoadedLane + execTicks) // task joins a lane
 *   idleDelta   = max(0, newFinish - currentGlobalMakespan) * activeHostCount * idleHostPower
 *   ΔEnergy     = execEnergy + idleDelta
 *
 * The two terms capture the two drivers of energy in the simulator's
 * EnergyObjective (see EnergyObjective.evaluate):
 *   1. Task execution energy — workload-specific incremental power × run time.
 *      Run time is the single-lane time (per-vCPU IPS); speed-based scaling
 *      (POWER_SCALING_EXPONENT = 2.0) uses the same per-core speed, so faster
 *      cores cost quadratically more power.
 *   2. Host-idle energy — extending the global makespan multiplies the idle
 *      cost across every active host. A task that joins a free lane on a VM does
 *      not extend that VM's completion, so consolidating onto wide VMs is cheap.
 *
 * Greedy, online, O(m·lanes) per task; respects user ownership and compute-type
 * compatibility. The speed-scaling reference is the median effective per-vCPU
 * (per-core) IPS of the fleet, keeping power estimates dimensionally consistent
 * with the host power model and EnergyObjective.
 */
public class EnergyAwareTaskAssignmentStrategy implements TaskAssignmentStrategy {

    private final MeasurementBasedPowerModel powerModel = new MeasurementBasedPowerModel();

    // Populated by assignAll; used by selectVM for global context.
    private double idleHostPower;
    private int activeHostCount = 1;
    private long globalMakespanTicks = 0;
    // Per-VM vCPU lane loads (ticks), mirroring the per-vCPU FIFO scheduler.
    private Map<VM, long[]> laneLoadsByVm = new IdentityHashMap<>();
    // Per-VM GPU-slot loads (ticks): GPU-workload concurrency is capped by bound
    // GPU count, so a GPU task waits for a free vCPU lane AND a free GPU slot.
    private Map<VM, long[]> gpuLoadsByVm = new IdentityHashMap<>();
    private boolean contextInitialized = false;

    @Override
    public Map<Task, VM> assignAll(List<Task> tasks, List<VM> vms, long currentTime) {
        initializeContext(vms);

        LinkedHashMap<Task, VM> assignments = new LinkedHashMap<>();
        for (Task task : tasks) {
            VM chosen = pickBestVm(task, vms);
            if (chosen == null) {
                continue;
            }

            placeOnLane(chosen, task);

            task.assignToVM(chosen.getId(), currentTime);
            chosen.assignTask(task);
            assignments.put(task, chosen);
        }
        return assignments;
    }

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        if (candidateVMs == null || candidateVMs.isEmpty()) {
            return Optional.empty();
        }

        // If assignAll wasn't invoked first (direct per-task use), initialise
        // against the candidate set. The idle-extension term will be
        // approximate since we only see the subset passed in.
        if (!contextInitialized) {
            initializeContext(candidateVMs);
        }

        VM best = pickBestVm(task, candidateVMs);
        if (best != null) {
            placeOnLane(best, task);
        }
        return Optional.ofNullable(best);
    }

    private void initializeContext(List<VM> vms) {
        powerModel.setReferenceVmIps(perCoreReferenceIps(vms));
        idleHostPower = powerModel.getScaledIdlePower();

        Set<Long> hostIds = new HashSet<>();
        for (VM vm : vms) {
            if (vm.getAssignedHostId() != null) {
                hostIds.add(vm.getAssignedHostId());
            }
        }
        activeHostCount = Math.max(1, hostIds.size());

        laneLoadsByVm = new IdentityHashMap<>();
        gpuLoadsByVm = new IdentityHashMap<>();
        globalMakespanTicks = 0;
        for (VM vm : vms) {
            long effIps = vm.getEffectiveIpsPerVcpu();
            long[] lanes = new long[Math.max(1, vm.getRequestedVcpuCount())];
            long[] gpuLanes = new long[Math.max(0, vm.getBoundGpuCount())];
            if (effIps > 0) {
                for (Task queued : vm.getAssignedTasks()) {
                    long ticks = (queued.getRemainingInstructions() + effIps - 1) / effIps;
                    int laneIdx = leastLoadedLane(lanes);
                    long start = lanes[laneIdx];
                    if (queued.getWorkloadType().isGpuWorkload() && gpuLanes.length > 0) {
                        int gpuIdx = leastLoadedLane(gpuLanes);
                        start = Math.max(start, gpuLanes[gpuIdx]);
                        gpuLanes[gpuIdx] = start + ticks;
                    }
                    lanes[laneIdx] = start + ticks;
                }
            }
            laneLoadsByVm.put(vm, lanes);
            gpuLoadsByVm.put(vm, gpuLanes);
            long vmMax = maxLoad(lanes);
            if (vmMax > globalMakespanTicks) {
                globalMakespanTicks = vmMax;
            }
        }
        contextInitialized = true;
    }

    /** Commits the task to the chosen VM's least-loaded lane and updates makespan. */
    private void placeOnLane(VM vm, Task task) {
        long effIps = vm.getEffectiveIpsPerVcpu();
        if (effIps <= 0) {
            return;
        }
        long execTicks = (task.getInstructionLength() + effIps - 1) / effIps;
        long[] lanes = laneLoadsByVm.computeIfAbsent(vm,
            v -> new long[Math.max(1, v.getRequestedVcpuCount())]);
        int laneIdx = leastLoadedLane(lanes);
        long start = lanes[laneIdx];
        // A GPU task occupies a GPU slot too; serialize across bound GPUs.
        if (task.getWorkloadType().isGpuWorkload() && vm.getBoundGpuCount() > 0) {
            long[] gpuLanes = gpuLoadsByVm.computeIfAbsent(vm,
                v -> new long[v.getBoundGpuCount()]);
            int gpuIdx = leastLoadedLane(gpuLanes);
            start = Math.max(start, gpuLanes[gpuIdx]);
            gpuLanes[gpuIdx] = start + execTicks;
        }
        lanes[laneIdx] = start + execTicks;
        long vmMax = maxLoad(lanes);
        if (vmMax > globalMakespanTicks) {
            globalMakespanTicks = vmMax;
        }
    }

    private VM pickBestVm(Task task, List<VM> candidates) {
        VM best = null;
        double bestDeltaEnergy = Double.MAX_VALUE;

        for (VM vm : candidates) {
            if (!vm.getUserId().equals(task.getUserId())) {
                continue;
            }
            if (!vm.canAcceptTask(task)) {
                continue;
            }
            long effIps = vm.getEffectiveIpsPerVcpu();
            if (effIps <= 0) {
                continue;
            }

            long execTicks = (task.getInstructionLength() + effIps - 1) / effIps;
            double[] util = getUtilizationProfile(task.getWorkloadType());
            double incrPower = powerModel.calculateIncrementalPowerWithSpeedScaling(
                    task.getWorkloadType(), util[0], util[1], effIps);

            double execEnergyJoules = incrPower * execTicks;

            long[] lanes = laneLoadsByVm.get(vm);
            long laneStart = (lanes == null ? 0L : lanes[leastLoadedLane(lanes)]);
            // A GPU task also waits for a free GPU slot (concurrency capped by bound GPUs).
            if (task.getWorkloadType().isGpuWorkload() && vm.getBoundGpuCount() > 0) {
                long[] gpuLanes = gpuLoadsByVm.get(vm);
                long gpuStart = (gpuLanes == null ? 0L : gpuLanes[leastLoadedLane(gpuLanes)]);
                laneStart = Math.max(laneStart, gpuStart);
            }
            long laneFinish = laneStart + execTicks;
            long vmCurrentMax = (lanes == null ? 0L : maxLoad(lanes));
            long newFinish = Math.max(vmCurrentMax, laneFinish);
            long extension = Math.max(0L, newFinish - globalMakespanTicks);
            double idleEnergyJoules = (double) extension * activeHostCount * idleHostPower;

            double deltaEnergy = execEnergyJoules + idleEnergyJoules;
            if (deltaEnergy < bestDeltaEnergy) {
                bestDeltaEnergy = deltaEnergy;
                best = vm;
            }
        }
        return best;
    }

    /** Median effective per-vCPU (per-core) IPS of the fleet, for power scaling. */
    private long perCoreReferenceIps(List<VM> vms) {
        long[] vals = vms.stream()
                .mapToLong(VM::getEffectiveIpsPerVcpu)
                .filter(v -> v > 0)
                .sorted()
                .toArray();
        if (vals.length == 0) {
            return MeasurementBasedPowerModel.DEFAULT_REFERENCE_IPS;
        }
        int mid = vals.length / 2;
        return (vals.length % 2 == 0) ? (vals[mid - 1] + vals[mid]) / 2 : vals[mid];
    }

    private static int leastLoadedLane(long[] lanes) {
        int idx = 0;
        for (int i = 1; i < lanes.length; i++) {
            if (lanes[i] < lanes[idx]) {
                idx = i;
            }
        }
        return idx;
    }

    private static long maxLoad(long[] lanes) {
        long max = 0L;
        for (long load : lanes) {
            if (load > max) {
                max = load;
            }
        }
        return max;
    }

    private double[] getUtilizationProfile(WorkloadType workloadType) {
        if (powerModel.hasProfile(workloadType)) {
            EmpiricalWorkloadProfile profile = powerModel.getWorkloadProfile(workloadType);
            return new double[]{
                    profile.getTypicalCpuUtilization(),
                    profile.getTypicalGpuUtilization()
            };
        }
        switch (workloadType) {
            case SEVEN_ZIP:      return new double[]{1.0, 0.0};
            case DATABASE:       return new double[]{0.12, 0.0};
            case FURMARK:        return new double[]{0.08, 1.0};
            case IMAGE_GEN_CPU:  return new double[]{0.80, 0.0};
            case IMAGE_GEN_GPU:  return new double[]{0.30, 0.10};
            case LLM_CPU:        return new double[]{0.55, 0.0};
            case LLM_GPU:        return new double[]{0.12, 0.12};
            case CINEBENCH:      return new double[]{1.0, 0.0};
            case PRIME95SmallFFT:return new double[]{1.0, 0.0};
            case VERACRYPT:      return new double[]{0.03, 0.0};
            case IDLE:
            default:             return new double[]{0.0, 0.0};
        }
    }

    @Override
    public String getStrategyName() {
        return "EnergyAware";
    }

    @Override
    public String getDescription() {
        return "Assigns each task to minimise marginal energy: workload-aware "
             + "execution energy plus idle-power cost from any makespan extension, "
             + "using the per-vCPU FIFO scheduler.";
    }
}
