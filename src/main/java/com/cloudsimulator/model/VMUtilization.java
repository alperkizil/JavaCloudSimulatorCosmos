package com.cloudsimulator.model;

import com.cloudsimulator.enums.WorkloadType;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of a VM's utilization for the current simulation tick under the
 * per-vCPU FIFO scheduler.
 *
 * A VM runs up to one task per vCPU lane concurrently, so its utilization this
 * tick is a set of per-lane entries — each carrying the workload type running on
 * that lane and that lane's CPU/GPU utilization. An empty lane list means the VM
 * is idle.
 *
 * The aggregate getters ({@link #getActiveWorkloadType()},
 * {@link #getCpuUtilization()}, {@link #getGpuUtilization()}) collapse the lanes
 * into a single VM-level figure for the legacy utilization-based power path and
 * for reporting. The workload-aware power model reads the per-lane breakdown via
 * {@link #getLanes()} instead.
 */
public class VMUtilization {

    /**
     * Utilization of a single busy vCPU lane during the current tick.
     */
    public static final class LaneUtilization {
        private final WorkloadType workloadType;
        private final double cpuUtilization;   // 0.0..1.0 for this one lane
        private final double gpuUtilization;   // 0.0..1.0 for this one lane

        public LaneUtilization(WorkloadType workloadType, double cpuUtilization, double gpuUtilization) {
            this.workloadType = workloadType;
            this.cpuUtilization = cpuUtilization;
            this.gpuUtilization = gpuUtilization;
        }

        public WorkloadType getWorkloadType() {
            return workloadType;
        }

        public double getCpuUtilization() {
            return cpuUtilization;
        }

        public double getGpuUtilization() {
            return gpuUtilization;
        }
    }

    // One entry per busy vCPU lane this tick; empty means the VM is idle.
    private final List<LaneUtilization> lanes = new ArrayList<>();

    /**
     * Creates an IDLE snapshot (no busy lanes).
     */
    public VMUtilization() {
    }

    /**
     * Records a busy vCPU lane running the given workload at the given utilization.
     */
    public void addLane(WorkloadType workloadType, double cpuUtilization, double gpuUtilization) {
        lanes.add(new LaneUtilization(workloadType, cpuUtilization, gpuUtilization));
    }

    /**
     * Clears all lanes; the VM is idle this tick.
     */
    public void resetToIdle() {
        lanes.clear();
    }

    /**
     * Per-lane utilizations for the current tick (one entry per concurrently
     * running task). Empty when the VM is idle.
     */
    public List<LaneUtilization> getLanes() {
        return lanes;
    }

    /**
     * Whether the VM has no busy lanes this tick.
     */
    public boolean isIdle() {
        return lanes.isEmpty();
    }

    /**
     * Number of busy vCPU lanes this tick (tasks running concurrently).
     */
    public int getActiveLaneCount() {
        return lanes.size();
    }

    // ---- Aggregate accessors (legacy utilization-based power path + reporting) ----

    /**
     * Representative workload for the VM: the workload on the first busy lane, or
     * IDLE when the VM has no busy lanes. With the per-vCPU scheduler a VM may run
     * several workloads at once; use {@link #getLanes()} for the full breakdown.
     */
    public WorkloadType getActiveWorkloadType() {
        return lanes.isEmpty() ? WorkloadType.IDLE : lanes.get(0).workloadType;
    }

    /**
     * Aggregate CPU utilization across all busy lanes, capped at 1.0 (a VM cannot
     * exceed 100% CPU). For the legacy utilization-based power path and reporting.
     */
    public double getCpuUtilization() {
        double sum = 0.0;
        for (LaneUtilization lane : lanes) {
            sum += lane.cpuUtilization;
        }
        return Math.min(1.0, sum);
    }

    /**
     * Aggregate GPU utilization across all busy lanes, capped at 1.0.
     */
    public double getGpuUtilization() {
        double sum = 0.0;
        for (LaneUtilization lane : lanes) {
            sum += lane.gpuUtilization;
        }
        return Math.min(1.0, sum);
    }

    @Override
    public String toString() {
        return "VMUtilization{" +
                "lanes=" + lanes.size() +
                ", workload=" + getActiveWorkloadType() +
                ", cpuUtilization=" + getCpuUtilization() +
                ", gpuUtilization=" + getGpuUtilization() +
                '}';
    }
}
