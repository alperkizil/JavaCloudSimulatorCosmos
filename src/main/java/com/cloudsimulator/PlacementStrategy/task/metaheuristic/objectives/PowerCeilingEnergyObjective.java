package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Energy objective extended with instantaneous aggregate-power accounting.
 *
 * Additive wrapper over EnergyObjective: evaluate() still returns energy in kWh
 * (identical formulation to the parent), but internally runs a sweep-line over
 * task start/end events plus per-host idle-window events (idle-host power
 * gating: a host draws idle power only during its own active window, exactly
 * as Host.updateState suspends drained hosts) to expose:
 *   - peakPower  (W)   : max aggregate DC power at any simulated tick
 *   - overflowSeconds  : integrated tick-seconds spent strictly above the cap
 *   - averageActivePower (W): time-weighted mean DC power over [0, makespan]
 *
 * This class does NOT mutate any state of EnergyObjective's parent behavior.
 * It can be used as a drop-in SchedulingObjective for experiments that want
 * to measure or penalize peak-power violations.
 */
public class PowerCeilingEnergyObjective extends EnergyObjective {

    /** Power cap in Watts. Defaults to +infinity (no cap). */
    private double powerCapWatts = Double.POSITIVE_INFINITY;

    // Per-evaluate() output metrics
    private double lastPeakPower = 0.0;
    private double lastOverflowSeconds = 0.0;
    private double lastAverageActivePower = 0.0;
    private long lastMakespanTicks = 0L;

    public PowerCeilingEnergyObjective() {
        super();
    }

    public PowerCeilingEnergyObjective(double powerCapWatts) {
        super();
        this.powerCapWatts = powerCapWatts;
    }

    public PowerCeilingEnergyObjective(MeasurementBasedPowerModel powerModel, double powerCapWatts) {
        super(powerModel);
        this.powerCapWatts = powerCapWatts;
    }

    @Override
    public String getName() {
        return "EnergyWithPowerCeiling";
    }

    @Override
    public String getDescription() {
        return super.getDescription() + " (also tracks peak aggregate DC power and cap-overflow seconds)";
    }

    @Override
    public double evaluate(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        double energyKwh = super.evaluate(solution, tasks, vms);
        computePowerProfile(solution, tasks, vms);
        return energyKwh;
    }

    /**
     * Runs only the sweep-line power profile (peak / overflow / average) and
     * skips the parent's energy integral. Use when the caller already ran
     * {@link EnergyObjective#evaluate} on the same solution (e.g. when energy
     * is in the objective list) and only needs the peak-power readings.
     */
    public void computePowerProfileOnly(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        computePowerProfile(solution, tasks, vms);
    }

    private void computePowerProfile(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        resetMetrics();
        if (tasks.isEmpty() || vms.isEmpty()) {
            return;
        }

        // Idle-host power gating (mirrors Host.updateState and EnergyObjective):
        // a host draws idle power only during its own active window
        // [0, max LaneSchedule completion of its VMs); a drained host suspends
        // at 0 W and a host with no scheduled work is never powered on. Each
        // occupied host contributes +idle/-idle step events to the same
        // sweep-line as the per-task incremental events, so peak, overflow and
        // average all see the gated fleet draw the simulator produces.
        double baseIdlePerHost = getPowerModel() != null
            ? getPowerModel().getScaledIdlePower()
            : 0.0;
        Map<Long, Long> hostActiveTicks = new HashMap<>();

        // Build (time, deltaPower) events from each VM's sequential schedule.
        // 2N events for N tasks: +inc at task start, -inc at task end.
        List<double[]> events = new ArrayList<>();
        long makespan = 0L;

        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);
            if (taskOrder.isEmpty()) continue;

            long effIps = vm.getEffectiveIpsPerVcpu();
            if (effIps <= 0) continue;

            // Per-vCPU FIFO scheduler: each task runs on a lane from its start tick
            // (its lane's load when dispatched) for its tick cost.
            LaneSchedule sched = LaneSchedule.schedule(taskOrder, tasks, effIps, vm.getRequestedVcpuCount(), vm.getBoundGpuCount());
            for (int pos = 0; pos < taskOrder.size(); pos++) {
                Task task = tasks.get(taskOrder.get(pos));
                long executionTicks = sched.getTaskTicks(pos);
                if (executionTicks <= 0) continue;

                double incPower = incrementalPowerForWorkloadOnVM(task.getWorkloadType(), vm);

                double start = (double) sched.getStartTick(pos);
                double end   = start + executionTicks;

                events.add(new double[]{ start, +incPower });
                events.add(new double[]{ end,   -incPower });
            }
            if (sched.getCompletionTicks() > makespan) makespan = sched.getCompletionTicks();

            // Extend the VM's host's powered-on window (max over its VMs).
            Long hostId = vm.getAssignedHostId();
            if (hostId != null) {
                hostActiveTicks.merge(hostId, sched.getCompletionTicks(), Math::max);
            }
        }

        lastMakespanTicks = makespan;

        if (makespan == 0L) {
            // No work scheduled: under idle-host gating no host is powered on,
            // so the fleet draws nothing (metrics already reset to zero).
            return;
        }

        // Per-host idle step events over each host's active window.
        for (Map.Entry<Long, Long> hostWindow : hostActiveTicks.entrySet()) {
            long windowTicks = hostWindow.getValue();
            if (windowTicks <= 0) continue;
            events.add(new double[]{ 0.0, +baseIdlePerHost });
            events.add(new double[]{ (double) windowTicks, -baseIdlePerHost });
        }

        // Sort events by time; ties: apply decrements before increments so we
        // don't double-count the instant a task finishes and another on the
        // same VM starts. For same-VM back-to-back tasks this means the sum
        // stays flat across the boundary rather than momentarily doubling.
        events.sort((a, b) -> {
            int c = Double.compare(a[0], b[0]);
            if (c != 0) return c;
            return Double.compare(a[1], b[1]); // negatives first
        });

        double runningPower = 0.0;   // gated fleet draw: per-host idle + task increments
        double peak = 0.0;
        double overflow = 0.0;
        double energyWattSeconds = 0.0;
        double prevTime = 0.0;

        // Sweep-line: between consecutive event times the power is constant.
        int i = 0;
        while (i < events.size()) {
            double t = events.get(i)[0];
            // Interval [prevTime, t) has power = runningPower
            double instantPower = runningPower;
            double dt = t - prevTime;
            if (dt > 0) {
                if (instantPower > peak) peak = instantPower;
                if (instantPower > powerCapWatts) {
                    overflow += dt;
                }
                energyWattSeconds += instantPower * dt;
            }
            // Apply all events at time t
            while (i < events.size() && events.get(i)[0] == t) {
                runningPower += events.get(i)[1];
                i++;
            }
            prevTime = t;
        }
        // Tail: the last event is the final host window's -idle at t == makespan,
        // so the intervals above cover [0, makespan] completely and running is 0.

        lastPeakPower = peak;
        lastOverflowSeconds = overflow;
        lastAverageActivePower = energyWattSeconds / makespan;
    }

    /**
     * Duplicates EnergyObjective's private incremental-power logic using only
     * public API of MeasurementBasedPowerModel + public getters of the parent.
     */
    private double incrementalPowerForWorkloadOnVM(WorkloadType workloadType, VM vm) {
        MeasurementBasedPowerModel powerModel = getPowerModel();
        if (isUsingMeasurementBasedModel() && powerModel != null) {
            double[] util = utilizationProfile(workloadType, powerModel);
            if (isUsingSpeedBasedScaling()) {
                return powerModel.calculateIncrementalPowerWithSpeedScaling(
                    workloadType, util[0], util[1], vm.getEffectiveIpsPerVcpu());
            }
            return powerModel.calculateIncrementalPower(workloadType, util[0], util[1]);
        }
        // Legacy linear model: incremental = total - base
        double[] util = utilizationProfile(workloadType, powerModel);
        double cpuPower = util[0] * vm.getRequestedVcpuCount() * getCpuPowerPerCoreWatts();
        double gpuPower = util[1] * vm.getRequestedGpuCount() * getGpuPowerPerUnitWatts();
        return cpuPower + gpuPower;
    }

    private static double[] utilizationProfile(WorkloadType wt, MeasurementBasedPowerModel pm) {
        if (pm != null && pm.hasProfile(wt)) {
            var profile = pm.getWorkloadProfile(wt);
            return new double[]{ profile.getTypicalCpuUtilization(), profile.getTypicalGpuUtilization() };
        }
        // Mirror EnergyObjective's hardcoded fallback table
        switch (wt) {
            case SEVEN_ZIP:     return new double[]{1.0, 0.0};
            case DATABASE:      return new double[]{0.12, 0.0};
            case FURMARK:       return new double[]{0.08, 1.0};
            case IMAGE_GEN_CPU: return new double[]{0.80, 0.0};
            case IMAGE_GEN_GPU: return new double[]{0.30, 0.10};
            case LLM_CPU:       return new double[]{0.55, 0.0};
            case LLM_GPU:       return new double[]{0.12, 0.12};
            case CINEBENCH:     return new double[]{1.0, 0.0};
            case PRIME95SmallFFT: return new double[]{1.0, 0.0};
            case VERACRYPT:     return new double[]{0.03, 0.0};
            case IDLE:
            default:            return new double[]{0.0, 0.0};
        }
    }

    private void resetMetrics() {
        lastPeakPower = 0.0;
        lastOverflowSeconds = 0.0;
        lastAverageActivePower = 0.0;
        lastMakespanTicks = 0L;
    }

    // ---- Power-ceiling API ----

    public double getPowerCapWatts() { return powerCapWatts; }

    public void setPowerCapWatts(double powerCapWatts) { this.powerCapWatts = powerCapWatts; }

    /** Peak aggregate DC power (Watts) observed during the last evaluate() call. */
    public double getLastPeakPower() { return lastPeakPower; }

    /** Seconds (ticks) spent strictly above the cap during the last evaluate() call. */
    public double getLastOverflowSeconds() { return lastOverflowSeconds; }

    /** Time-weighted mean DC power (Watts) during [0, makespan] for the last evaluate(). */
    public double getLastAverageActivePower() { return lastAverageActivePower; }

    public long getLastMakespanTicks() { return lastMakespanTicks; }

    /** Convenience: true iff the last evaluated solution stayed at or below the cap. */
    public boolean wasLastFeasible() {
        return lastPeakPower <= powerCapWatts;
    }

    /**
     * Violation magnitude in Watts above the cap for the last evaluate().
     * Zero when feasible. Useful as a constraint value for Deb's rules.
     */
    public double getLastViolationWatts() {
        return Math.max(0.0, lastPeakPower - powerCapWatts);
    }
}
