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
 * by adding the task to v:
 *
 *   execTicks   = ceil(task.instructions / v.totalIPS)
 *   execEnergy  = incrementalPower(task.workloadType, v) * execTicks
 *   newFinish   = queueTicks(v) + execTicks
 *   idleDelta   = max(0, newFinish - currentGlobalMakespan) * activeHostCount * idleHostPower
 *   ΔEnergy     = execEnergy + idleDelta
 *
 * The two terms capture the two drivers of energy in the simulator's
 * EnergyObjective (see EnergyObjective.evaluate):
 *   1. Task execution energy — workload-specific incremental power × run time.
 *      Under speed-based scaling (POWER_SCALING_EXPONENT = 2.0) this term
 *      tends to prefer slower, more power-efficient VMs.
 *   2. Host-idle energy — extending the global makespan multiplies the idle
 *      cost across every active host. This term keeps the heuristic from
 *      piling work onto slow VMs once doing so would push out the makespan.
 *
 * Mirrors WorkloadAwareTaskAssignmentStrategy in structure: O(m·k) per task,
 * greedy, online, respects user ownership and compute-type compatibility.
 */
public class EnergyAwareTaskAssignmentStrategy implements TaskAssignmentStrategy {

    private final MeasurementBasedPowerModel powerModel = new MeasurementBasedPowerModel();

    // Populated by assignAll; used by selectVM for global context.
    private double idleHostPower;
    private int activeHostCount = 1;
    private long globalMakespanTicks = 0;
    private Map<VM, Long> completionTicksByVm = new IdentityHashMap<>();
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

            long vmIps = chosen.getTotalRequestedIps();
            long execTicks = (task.getInstructionLength() + vmIps - 1) / vmIps;
            long newFinish = completionTicksByVm.get(chosen) + execTicks;
            completionTicksByVm.put(chosen, newFinish);
            if (newFinish > globalMakespanTicks) {
                globalMakespanTicks = newFinish;
            }

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
            long vmIps = best.getTotalRequestedIps();
            long execTicks = (task.getInstructionLength() + vmIps - 1) / vmIps;
            long newFinish = completionTicksByVm.getOrDefault(best, 0L) + execTicks;
            completionTicksByVm.put(best, newFinish);
            if (newFinish > globalMakespanTicks) {
                globalMakespanTicks = newFinish;
            }
        }
        return Optional.ofNullable(best);
    }

    private void initializeContext(List<VM> vms) {
        powerModel.calculateReferenceIpsFromVMs(vms);
        idleHostPower = powerModel.getScaledIdlePower();

        Set<Long> hostIds = new HashSet<>();
        for (VM vm : vms) {
            if (vm.getAssignedHostId() != null) {
                hostIds.add(vm.getAssignedHostId());
            }
        }
        activeHostCount = Math.max(1, hostIds.size());

        completionTicksByVm = new IdentityHashMap<>();
        globalMakespanTicks = 0;
        for (VM vm : vms) {
            long vmIps = vm.getTotalRequestedIps();
            long ticks = 0;
            if (vmIps > 0) {
                for (Task queued : vm.getAssignedTasks()) {
                    ticks += (queued.getRemainingInstructions() + vmIps - 1) / vmIps;
                }
            }
            completionTicksByVm.put(vm, ticks);
            if (ticks > globalMakespanTicks) {
                globalMakespanTicks = ticks;
            }
        }
        contextInitialized = true;
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
            long vmIps = vm.getTotalRequestedIps();
            if (vmIps <= 0) {
                continue;
            }

            long execTicks = (task.getInstructionLength() + vmIps - 1) / vmIps;
            double[] util = getUtilizationProfile(task.getWorkloadType());
            double incrPower = powerModel.calculateIncrementalPowerWithSpeedScaling(
                    task.getWorkloadType(), util[0], util[1], vmIps);

            double execEnergyJoules = incrPower * execTicks;
            long currentTicks = completionTicksByVm.getOrDefault(vm, 0L);
            long newFinish = currentTicks + execTicks;
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
             + "execution energy plus idle-power cost from any makespan extension.";
    }
}
