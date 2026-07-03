package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.model.Task;

import java.util.List;

/**
 * Analytic mirror of the per-vCPU FIFO scheduler in {@code VM.executeOneSecond},
 * used by the scheduling objectives so their predictions match the discrete
 * simulation exactly.
 *
 * A VM has {@code vcpuCount} identical vCPU lanes, each running one task at a time
 * at the effective per-vCPU IPS, plus {@code gpuCount} physical GPUs. A CPU task
 * occupies one vCPU lane; a GPU task occupies one vCPU lane AND one GPU, so the
 * number of GPU tasks running at once is capped by {@code gpuCount}. Tasks are
 * dispatched in the given execution order; whenever a lane is free, the earliest
 * not-yet-started runnable task takes it (head-of-line non-blocking: a GPU task
 * with no free GPU is skipped so later CPU tasks can fill idle lanes). A task of
 * length L costs {@code ceil(L / effIpsPerVcpu)} ticks.
 *
 * This replays the same resource arbitration as {@code VM.fillLanes} at integer
 * tick granularity, so the predicted start ticks, completion (makespan), waiting
 * time and energy match the discrete simulation exactly. With no GPU tasks (or
 * {@code gpuCount} large enough) it reduces to the previous least-loaded-lane packing.
 */
public final class LaneSchedule {

    private final long completionTicks;
    private final double completionExact;
    private final long[] startTicks;   // index-aligned to the input task order
    private final long[] taskTicks;    // index-aligned to the input task order

    private LaneSchedule(long completionTicks, double completionExact,
                         long[] startTicks, long[] taskTicks) {
        this.completionTicks = completionTicks;
        this.completionExact = completionExact;
        this.startTicks = startTicks;
        this.taskTicks = taskTicks;
    }

    /** VM completion time in ticks (the busiest lane's load). */
    public long getCompletionTicks() {
        return completionTicks;
    }

    /**
     * VM completion time at instruction resolution: the latest exact finish
     * instant {@code startTick + length / (double) effIpsPerVcpu} over all
     * scheduled tasks. The lane still holds the resource until the tick
     * boundary ({@link #getCompletionTicks()}) — dispatch dynamics are
     * unchanged — this only stops rounding the finish line up to a whole tick,
     * so {@code completionTicks - 1 < completionExact <= completionTicks}.
     * Must stay the same IEEE-754 expression as the simulator-side
     * {@code TaskExecutionStep} fractional makespan so predictions match the
     * re-simulated value bit-for-bit.
     */
    public double getCompletionExact() {
        return completionExact;
    }

    /** Start tick of the task at the given position in the input order. */
    public long getStartTick(int orderPosition) {
        return startTicks[orderPosition];
    }

    /** Tick cost of the task at the given position in the input order. */
    public long getTaskTicks(int orderPosition) {
        return taskTicks[orderPosition];
    }

    /** Number of scheduled tasks. */
    public int size() {
        return taskTicks.length;
    }

    /**
     * Schedules a VM's tasks (given by their indices into {@code tasks}, in
     * execution order) onto {@code vcpuCount} vCPU lanes each running at
     * {@code effIpsPerVcpu}, with GPU-workload concurrency capped by
     * {@code gpuCount} (a GPU task occupies one lane AND one GPU).
     *
     * @param taskOrder      task indices (into {@code tasks}) in execution order
     * @param tasks          the global task list
     * @param effIpsPerVcpu  effective per-vCPU IPS (one lane's speed)
     * @param vcpuCount      number of vCPU lanes; treated as at least 1
     * @param gpuCount       number of GPUs (cap on concurrent GPU tasks); 0 ⇒
     *                       GPU workloads are not constrained (avoids deadlock)
     */
    public static LaneSchedule schedule(List<Integer> taskOrder, List<Task> tasks,
                                        long effIpsPerVcpu, int vcpuCount, int gpuCount) {
        int n = taskOrder.size();
        long[] startTicks = new long[n];
        long[] taskTicks = new long[n];

        if (n == 0 || effIpsPerVcpu <= 0) {
            return new LaneSchedule(0, 0.0, startTicks, taskTicks);
        }

        // Per-task tick cost (ceiling division — a task finishing mid-tick wastes
        // the remainder of that tick, exactly as the discrete simulation does) and
        // GPU flag.
        boolean[] isGpu = new boolean[n];
        long[] lengths = new long[n];
        for (int pos = 0; pos < n; pos++) {
            Task task = tasks.get(taskOrder.get(pos));
            lengths[pos] = task.getInstructionLength();
            taskTicks[pos] = (lengths[pos] + effIpsPerVcpu - 1) / effIpsPerVcpu;
            isGpu[pos] = task.getWorkloadType().isGpuWorkload();
        }

        int lanes = Math.max(1, vcpuCount);
        int gpus = Math.max(0, gpuCount);
        long[] laneFree = new long[lanes];   // tick each vCPU lane next becomes free
        long[] gpuFree = new long[gpus];     // tick each GPU next becomes free (gpus may be 0)
        boolean[] started = new boolean[n];
        int remaining = n;
        long completion = 0;
        double completionExact = 0.0;
        long t = 0;

        while (remaining > 0) {
            // Dispatch onto every vCPU lane free at time t, taking the earliest
            // not-yet-started runnable task each time (head-of-line non-blocking).
            boolean progressed = true;
            while (progressed) {
                progressed = false;

                int lane = indexOfMin(laneFree);
                if (laneFree[lane] > t) {
                    break; // no vCPU lane free at t
                }

                int pick = -1;
                int gpuIdx = -1;
                for (int i = 0; i < n; i++) {
                    if (started[i]) {
                        continue;
                    }
                    if (isGpu[i] && gpus > 0) {
                        int g = indexOfMin(gpuFree);
                        if (gpuFree[g] > t) {
                            continue; // GPU busy: skip this GPU task this tick
                        }
                        gpuIdx = g;
                    }
                    pick = i;
                    break;
                }
                if (pick < 0) {
                    break; // nothing startable on the free lane right now
                }

                long fin = t + taskTicks[pick];
                startTicks[pick] = t;
                started[pick] = true;
                remaining--;
                laneFree[lane] = fin;
                if (isGpu[pick] && gpus > 0) {
                    gpuFree[gpuIdx] = fin;
                }
                if (fin > completion) {
                    completion = fin;
                }
                double finExact = t + lengths[pick] / (double) effIpsPerVcpu;
                if (finExact > completionExact) {
                    completionExact = finExact;
                }
                progressed = true;
            }

            if (remaining == 0) {
                break;
            }

            // Advance to the next time a lane or GPU frees (which may unblock a
            // skipped GPU task or refill a busy lane).
            long nextT = Long.MAX_VALUE;
            for (long f : laneFree) {
                if (f > t && f < nextT) {
                    nextT = f;
                }
            }
            for (long f : gpuFree) {
                if (f > t && f < nextT) {
                    nextT = f;
                }
            }
            if (nextT == Long.MAX_VALUE) {
                // No resource will free but tasks remain — only with an infeasible
                // config (GPU tasks but gpuCount == 0 and lanes idle). Avoid an
                // infinite loop; leave the rest unscheduled.
                break;
            }
            t = nextT;
        }

        return new LaneSchedule(completion, completionExact, startTicks, taskTicks);
    }

    /** Index of the smallest entry (lowest index on tie); 0 for empty input. */
    private static int indexOfMin(long[] a) {
        int idx = 0;
        for (int i = 1; i < a.length; i++) {
            if (a[i] < a[idx]) {
                idx = i;
            }
        }
        return idx;
    }
}
