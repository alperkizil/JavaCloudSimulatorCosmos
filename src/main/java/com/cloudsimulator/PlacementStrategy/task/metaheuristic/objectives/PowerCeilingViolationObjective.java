package com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives;

import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.List;

/**
 * Minimizable scheduling objective that penalizes schedules whose instantaneous
 * aggregate DC power exceeds a configured ceiling.
 *
 * Designed to be used as a third objective alongside waiting-time and energy,
 * turning the 2D problem into a 3D constrained-style MOO without requiring
 * Deb's constrained-domination rules.
 *
 * Three penalty modes (see {@link Mode}) expose the violation in different
 * units so experiments can compare formulations:
 *
 *   WATTS_OVER_CAP      — max(0, peakPower − cap), unit: W
 *   OVERFLOW_SECONDS    — tick-seconds strictly above cap, unit: s
 *   OVERFLOW_JOULES     — integrated (power − cap)⁺ · dt over [0, makespan], unit: J
 *
 * Internally this class reuses {@link PowerCeilingEnergyObjective} for the
 * sweep-line. No state from {@link EnergyObjective} is mutated.
 */
public class PowerCeilingViolationObjective implements SchedulingObjective {

    public enum Mode {
        WATTS_OVER_CAP,
        OVERFLOW_SECONDS,
        OVERFLOW_JOULES
    }

    private final PowerCeilingEnergyObjective inner;
    private final Mode mode;

    public PowerCeilingViolationObjective(double powerCapWatts) {
        this(powerCapWatts, Mode.WATTS_OVER_CAP);
    }

    public PowerCeilingViolationObjective(double powerCapWatts, Mode mode) {
        this.inner = new PowerCeilingEnergyObjective(powerCapWatts);
        this.mode = mode;
    }

    public PowerCeilingViolationObjective(MeasurementBasedPowerModel powerModel,
                                          double powerCapWatts,
                                          Mode mode) {
        this.inner = new PowerCeilingEnergyObjective(powerModel, powerCapWatts);
        this.mode = mode;
    }

    @Override
    public String getName() {
        return "PowerCeilingViolation_" + mode.name();
    }

    @Override
    public String getDescription() {
        return "Minimizes aggregate DC power excess above the configured ceiling ("
            + mode.name() + ")";
    }

    @Override
    public String getUnit() {
        switch (mode) {
            case WATTS_OVER_CAP:   return "W";
            case OVERFLOW_SECONDS: return "s";
            case OVERFLOW_JOULES:  return "J";
            default:               return "";
        }
    }

    @Override
    public boolean isMinimization() {
        return true;
    }

    @Override
    public double evaluate(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        inner.evaluate(solution, tasks, vms);
        switch (mode) {
            case WATTS_OVER_CAP:
                return inner.getLastViolationWatts();
            case OVERFLOW_SECONDS:
                return inner.getLastOverflowSeconds();
            case OVERFLOW_JOULES:
                // Approximation: violationWatts × overflowSeconds. This is an
                // upper bound on the true integral when the excess profile is
                // convex (peak-driven), exact when excess is constant across
                // the overflow interval. Tight enough for ranking purposes.
                return inner.getLastViolationWatts() * inner.getLastOverflowSeconds();
            default:
                return 0.0;
        }
    }

    // ---- Configuration pass-through ----

    public double getPowerCapWatts() { return inner.getPowerCapWatts(); }

    public void setPowerCapWatts(double watts) { inner.setPowerCapWatts(watts); }

    public Mode getMode() { return mode; }

    public double getLastPeakPower() { return inner.getLastPeakPower(); }

    public double getLastOverflowSeconds() { return inner.getLastOverflowSeconds(); }

    public void setHosts(List<Host> hosts) { inner.setHosts(hosts); }
}
