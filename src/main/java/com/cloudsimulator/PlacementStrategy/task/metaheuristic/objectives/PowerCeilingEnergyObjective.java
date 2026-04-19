package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Energy objective extended with instantaneous aggregate-power accounting.
 *
 * Additive wrapper over EnergyObjective: evaluate() still returns energy in kWh
 * (identical formulation to the parent), but internally runs a sweep-line over
 * task start/end events to expose:
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

    private void computePowerProfile(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        resetMetrics();
        if (tasks.isEmpty() || vms.isEmpty()) {
            return;
        }

        // Identify which hosts are active (hold at least one VM with tasks)
        Set<Long> hostsWithVMs = new HashSet<>();
        for (VM vm : vms) {
            if (vm.getAssignedHostId() != null) {
                hostsWithVMs.add(vm.getAssignedHostId());
            }
        }
        int activeHostCount = Math.max(1, hostsWithVMs.size());

        int dynamicAdditionalHosts = getAdditionalIdleHostCount();
        List<Host> hosts = getHosts();
        if (hosts != null && !hosts.isEmpty()) {
            int hostsInDatacenter = (int) hosts.stream()
                .filter(h -> h.getAssignedDatacenterId() != null)
                .count();
            dynamicAdditionalHosts = Math.max(0, hostsInDatacenter - hostsWithVMs.size());
        }
        int totalActiveHosts = activeHostCount + dynamicAdditionalHosts;

        double baseIdlePerHost = getPowerModel() != null
            ? getPowerModel().getScaledIdlePower()
            : 0.0;
        double baselineDcPower = baseIdlePerHost * totalActiveHosts;

        // Build (time, deltaPower) events from each VM's sequential schedule.
        // 2N events for N tasks: +inc at task start, -inc at task end.
        List<double[]> events = new ArrayList<>();
        long makespan = 0L;

        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);
            if (taskOrder.isEmpty()) continue;

            long vmIps = vm.getTotalRequestedIps();
            if (vmIps <= 0) continue;

            long cursor = 0L;
            for (int taskIdx : taskOrder) {
                Task task = tasks.get(taskIdx);
                long executionTicks = (task.getInstructionLength() + vmIps - 1) / vmIps;
                if (executionTicks <= 0) continue;

                double incPower = incrementalPowerForWorkloadOnVM(task.getWorkloadType(), vm);

                double start = (double) cursor;
                double end   = (double) (cursor + executionTicks);

                events.add(new double[]{ start, +incPower });
                events.add(new double[]{ end,   -incPower });

                cursor += executionTicks;
            }
            if (cursor > makespan) makespan = cursor;
        }

        lastMakespanTicks = makespan;

        if (makespan == 0L) {
            // No work scheduled. Peak equals idle baseline.
            lastPeakPower = baselineDcPower;
            lastAverageActivePower = baselineDcPower;
            lastOverflowSeconds = baselineDcPower > powerCapWatts ? 0.0 : 0.0;
            return;
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

        double runningIncremental = 0.0;
        double peak = baselineDcPower;
        double overflow = 0.0;
        double energyWattSeconds = baselineDcPower * makespan; // idle contribution
        double prevTime = 0.0;

        // Sweep-line: between consecutive event times the power is constant.
        int i = 0;
        while (i < events.size()) {
            double t = events.get(i)[0];
            // Interval [prevTime, t) has power = baselineDcPower + runningIncremental
            double instantPower = baselineDcPower + runningIncremental;
            double dt = t - prevTime;
            if (dt > 0) {
                if (instantPower > peak) peak = instantPower;
                if (instantPower > powerCapWatts) {
                    overflow += dt;
                }
                // Accumulate incremental contribution to average power
                energyWattSeconds += runningIncremental * dt;
            }
            // Apply all events at time t
            while (i < events.size() && events.get(i)[0] == t) {
                runningIncremental += events.get(i)[1];
                i++;
            }
            prevTime = t;
        }
        // Tail interval [prevTime, makespan] — all tasks have ended so running=0.
        // Nothing to add; peak already captured by last interval.

        // Guard against floating-point underflow producing a tiny negative.
        if (runningIncremental < 1e-9) runningIncremental = 0.0;

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
                    workloadType, util[0], util[1], vm.getTotalRequestedIps());
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
