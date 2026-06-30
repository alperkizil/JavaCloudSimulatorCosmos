package com.cloudsimulator.observer;

import com.cloudsimulator.model.SimulationSummary;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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

    /** Timestamp format used in the resolved experiment id (e.g. {@code 30_06_2026_14_05_09}). */
    private static final DateTimeFormatter ID_TIMESTAMP = DateTimeFormatter.ofPattern("dd_MM_yyyy_HH_mm_ss");

    private final String name;                                          // nullable (unnamed experiment)
    private final ObjectiveSpec objectiveX;
    private final ObjectiveSpec objectiveY;
    private final ToDoubleFunction<SimulationSummary> auxPeakExtractor; // nullable
    private final double[] capThresholdsWatts;                          // nullable

    public ExperimentSpec(ObjectiveSpec objectiveX, ObjectiveSpec objectiveY) {
        this(null, objectiveX, objectiveY, null, null);
    }

    public ExperimentSpec(ObjectiveSpec objectiveX, ObjectiveSpec objectiveY,
                          ToDoubleFunction<SimulationSummary> auxPeakExtractor,
                          double[] capThresholdsWatts) {
        this(null, objectiveX, objectiveY, auxPeakExtractor, capThresholdsWatts);
    }

    public ExperimentSpec(String name, ObjectiveSpec objectiveX, ObjectiveSpec objectiveY) {
        this(name, objectiveX, objectiveY, null, null);
    }

    public ExperimentSpec(String name, ObjectiveSpec objectiveX, ObjectiveSpec objectiveY,
                          ToDoubleFunction<SimulationSummary> auxPeakExtractor,
                          double[] capThresholdsWatts) {
        if (objectiveX == null || objectiveY == null) {
            throw new IllegalArgumentException("both objectives must be specified");
        }
        this.name = name;
        this.objectiveX = objectiveX;
        this.objectiveY = objectiveY;
        this.auxPeakExtractor = auxPeakExtractor;
        this.capThresholdsWatts = capThresholdsWatts == null ? null : capThresholdsWatts.clone();
    }

    // ---- Factory methods for the three locked studies ----------------------

    public static ExperimentSpec scenarioComparison() {
        return scenarioComparison(null);
    }

    public static ExperimentSpec scenarioComparison(String name) {
        return new ExperimentSpec(name,
            new ObjectiveSpec("Makespan", MAKESPAN),
            new ObjectiveSpec("Energy", ENERGY));
    }

    public static ExperimentSpec waitingTime() {
        return waitingTime(null);
    }

    public static ExperimentSpec waitingTime(String name) {
        return new ExperimentSpec(name,
            new ObjectiveSpec("WaitingTime", WAITING_TIME),
            new ObjectiveSpec("Energy", ENERGY));
    }

    public static ExperimentSpec powerCeiling() {
        return powerCeiling(null);
    }

    public static ExperimentSpec powerCeiling(String name) {
        return new ExperimentSpec(name,
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

    // ---- Naming / identity --------------------------------------------------

    /** @return the experiment name, or {@code null} if unnamed. */
    public String getName() {
        return name;
    }

    /** @return {@code true} if a non-blank name was supplied. */
    public boolean hasName() {
        return name != null && !name.trim().isEmpty();
    }

    /** Returns a copy of this spec with the given experiment name (objectives unchanged). */
    public ExperimentSpec withName(String newName) {
        return new ExperimentSpec(newName, objectiveX, objectiveY, auxPeakExtractor, capThresholdsWatts);
    }

    /**
     * Pure, deterministic id builder: {@code "<base>_<timestamp>"}, where
     * {@code base} is the (trimmed) experiment name when present, otherwise
     * {@code fallbackId}. Useful for tests and when the caller owns the
     * timestamp / generated id.
     *
     * @param timestamp  the formatted timestamp component
     * @param fallbackId id to use when this experiment has no name
     */
    public String buildExperimentId(String timestamp, String fallbackId) {
        String base = hasName() ? name.trim() : fallbackId;
        return base + "_" + timestamp;
    }

    /**
     * Resolves the experiment id at the given time: {@code "<name>_<timestamp>"}
     * if named, otherwise {@code "<generatedId>_<timestamp>"}. Resolve <em>once</em>
     * per run and reuse the result (each unnamed call generates a fresh id).
     */
    public String resolveExperimentId(LocalDateTime when) {
        String timestamp = when.format(ID_TIMESTAMP);
        return buildExperimentId(timestamp, hasName() ? null : generateId());
    }

    /** Resolves the experiment id at the current time. See {@link #resolveExperimentId(LocalDateTime)}. */
    public String resolveExperimentId() {
        return resolveExperimentId(LocalDateTime.now());
    }

    /** Generates a short unique id for unnamed experiments, e.g. {@code exp-1a2b3c4d}. */
    private static String generateId() {
        return "exp-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
