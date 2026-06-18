package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.model.Task;

import java.util.List;

/**
 * Analytic mirror of the per-vCPU FIFO scheduler in {@code VM.executeOneSecond},
 * used by the scheduling objectives so their predictions match the discrete
 * simulation exactly.
 *
 * A VM has {@code vcpuCount} identical lanes, each running one task at a time at
 * the effective per-vCPU IPS. Tasks are taken in the given execution order and
 * each is placed on the least-loaded lane (the lane that frees earliest, which is
 * the one the next FIFO task lands on in the simulation). A task of length L costs
 * {@code ceil(L / effIpsPerVcpu)} ticks. The VM's completion time is the maximum
 * lane load; a task's start tick is its lane's load before the task is placed.
 *
 * The per-lane loads and the resulting completion / start ticks are independent of
 * how ties between equally loaded lanes are broken, so the predicted makespan,
 * waiting time and energy match the simulation regardless of lane labelling.
 */
public final class LaneSchedule {

    private final long completionTicks;
    private final long[] startTicks;   // index-aligned to the input task order
    private final long[] taskTicks;    // index-aligned to the input task order

    private LaneSchedule(long completionTicks, long[] startTicks, long[] taskTicks) {
        this.completionTicks = completionTicks;
        this.startTicks = startTicks;
        this.taskTicks = taskTicks;
    }

    /** VM completion time in ticks (the busiest lane's load). */
    public long getCompletionTicks() {
        return completionTicks;
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
     * execution order) onto {@code vcpuCount} lanes each running at
     * {@code effIpsPerVcpu}.
     *
     * @param taskOrder      task indices (into {@code tasks}) in execution order
     * @param tasks          the global task list
     * @param effIpsPerVcpu  effective per-vCPU IPS (one lane's speed)
     * @param vcpuCount      number of lanes (vCPUs); treated as at least 1
     */
    public static LaneSchedule schedule(List<Integer> taskOrder, List<Task> tasks,
                                        long effIpsPerVcpu, int vcpuCount) {
        int n = taskOrder.size();
        long[] startTicks = new long[n];
        long[] taskTicks = new long[n];

        if (n == 0 || effIpsPerVcpu <= 0) {
            return new LaneSchedule(0, startTicks, taskTicks);
        }

        int lanes = Math.max(1, vcpuCount);
        long[] laneLoad = new long[lanes];
        long completion = 0;

        for (int pos = 0; pos < n; pos++) {
            long instrLen = tasks.get(taskOrder.get(pos)).getInstructionLength();
            // Ceiling division: a task finishing mid-tick wastes the remainder of
            // that tick, exactly as the discrete simulation does.
            long ticks = (instrLen + effIpsPerVcpu - 1) / effIpsPerVcpu;

            // Place on the least-loaded lane (lowest index on tie).
            int chosen = 0;
            for (int l = 1; l < lanes; l++) {
                if (laneLoad[l] < laneLoad[chosen]) {
                    chosen = l;
                }
            }

            startTicks[pos] = laneLoad[chosen];
            laneLoad[chosen] += ticks;
            taskTicks[pos] = ticks;
            if (laneLoad[chosen] > completion) {
                completion = laneLoad[chosen];
            }
        }

        return new LaneSchedule(completion, startTicks, taskTicks);
    }
}
