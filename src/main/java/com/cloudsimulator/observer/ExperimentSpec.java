package com.cloudsimulator.observer;

import com.cloudsimulator.model.SimulationSummary;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Declarative description of a pairwise (2-objective) scheduling study, so a
 * study can be defined without per-runner campaign code.
 *
 * <p>A spec carries two {@link ObjectiveSpec}s (X and Y), an optional auxiliary
 * scalar extractor (the coincident peak power, in Watts, used by the
 * PowerCeiling study) and optional power-cap thresholds for feasibility
 * analysis. The auxiliary extractor is {@code null} for every study except
 * PowerCeiling, but the API carries it from day one so the later migration is
 * mechanical.</p>
 *
 * <p>Locked study definitions (confirmed with the user):</p>
 * <ul>
 *   <li>ScenarioComparison &mdash; {@code [Makespan, Energy]}</li>
 *   <li>WaitingTime &mdash; {@code [WaitingTime, Energy]}</li>
 *   <li>PowerCeiling &mdash; {@code [WaitingTime, Energy]} + aux coincident peak (W)
 *       + cap feasibility at {220, 190, 120} kW</li>
 * </ul>
 */
public final class ExperimentSpec {

    // -------------------------------------------------------------------------
    // Reusable extractors over existing summary fields.
    // makespanSeconds is a long; it widens to double for the extractor.
    // peakPowerWatts is the coincident (true) fleet peak (Step 8).
    // -------------------------------------------------------------------------

    /** Total completion time in seconds ({@code performance.makespanSeconds}). */
    public static final ToDoubleFunction<SimulationSummary> MAKESPAN =
        s -> s.getPerformance().makespanSeconds;

    /** Average task waiting time in seconds ({@code performance.avgWaitingTimeSeconds}). */
    public static final ToDoubleFunction<SimulationSummary> WAITING_TIME =
        s -> s.getPerformance().avgWaitingTimeSeconds;

    /** IT energy in kWh ({@code energy.totalITEnergyKWh}). */
    public static final ToDoubleFunction<SimulationSummary> ENERGY =
        s -> s.getEnergy().totalITEnergyKWh;

    /** Coincident fleet peak power in Watts ({@code energy.peakPowerWatts}). */
    public static final ToDoubleFunction<SimulationSummary> PEAK_POWER_WATTS =
        s -> s.getEnergy().peakPowerWatts;

    /** Default PowerCeiling cap thresholds in Watts: 220 kW, 190 kW, 120 kW. */
    public static final double[] DEFAULT_CAP_THRESHOLDS_WATTS = {220_000, 190_000, 120_000};

    private final ObjectiveSpec objectiveX;
    private final ObjectiveSpec objectiveY;
    private final ToDoubleFunction<SimulationSummary> auxPeakExtractor; // nullable
    private final double[] capThresholdsWatts;                          // nullable

    public ExperimentSpec(ObjectiveSpec objectiveX, ObjectiveSpec objectiveY) {
        this(objectiveX, objectiveY, null, null);
    }

    public ExperimentSpec(ObjectiveSpec objectiveX, ObjectiveSpec objectiveY,
                          ToDoubleFunction<SimulationSummary> auxPeakExtractor,
                          double[] capThresholdsWatts) {
        if (objectiveX == null || objectiveY == null) {
            throw new IllegalArgumentException("both objectives must be specified");
        }
        this.objectiveX = objectiveX;
        this.objectiveY = objectiveY;
        this.auxPeakExtractor = auxPeakExtractor;
        this.capThresholdsWatts = capThresholdsWatts == null ? null : capThresholdsWatts.clone();
    }

    // ---- Factory methods for the three locked studies ----------------------

    public static ExperimentSpec scenarioComparison() {
        return new ExperimentSpec(
            new ObjectiveSpec("Makespan", MAKESPAN),
            new ObjectiveSpec("Energy", ENERGY));
    }

    public static ExperimentSpec waitingTime() {
        return new ExperimentSpec(
            new ObjectiveSpec("WaitingTime", WAITING_TIME),
            new ObjectiveSpec("Energy", ENERGY));
    }

    public static ExperimentSpec powerCeiling() {
        return new ExperimentSpec(
            new ObjectiveSpec("WaitingTime", WAITING_TIME),
            new ObjectiveSpec("Energy", ENERGY),
            PEAK_POWER_WATTS,
            DEFAULT_CAP_THRESHOLDS_WATTS);
    }

    // ---- Accessors ----------------------------------------------------------

    /** Objective names in column order &mdash; this order is the Python contract. */
    public List<String> getObjectiveNames() {
        return Arrays.asList(objectiveX.getName(), objectiveY.getName());
    }

    /**
     * Extracts the 2-element objective vector {@code [x, y]} from a summary.
     */
    public double[] extractObjectives(SimulationSummary summary) {
        return new double[] {objectiveX.extract(summary), objectiveY.extract(summary)};
    }

    /** @return {@code true} if this study carries an auxiliary peak-power scalar. */
    public boolean hasAuxPeak() {
        return auxPeakExtractor != null;
    }

    /**
     * Extracts the auxiliary peak-power scalar (Watts), or {@code null} if this
     * study has none.
     */
    public Double extractAuxPeakWatts(SimulationSummary summary) {
        return auxPeakExtractor == null ? null : auxPeakExtractor.applyAsDouble(summary);
    }

    /** @return cap thresholds (Watts), or {@code null} if none are configured. */
    public double[] getCapThresholdsWatts() {
        return capThresholdsWatts == null ? null : capThresholdsWatts.clone();
    }
}
