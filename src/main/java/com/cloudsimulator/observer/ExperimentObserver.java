package com.cloudsimulator.observer;

import com.cloudsimulator.engine.MultiObjectiveSimulationResult;
import com.cloudsimulator.engine.MultiObjectiveSimulationResult.SolutionSimulationResult;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationListener;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.utils.SimulationLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Long-lived collector that attaches to a {@code SimulationEngine} and captures
 * each run's re-simulated Pareto front as an {@link AlgorithmRunResult}.
 *
 * <p>Because the engine has no notion of an algorithm label, scenario or seed,
 * call {@link #beginRun(String, int, String, long)} immediately before each
 * {@code engine.run()} / {@code engine.runMultiObjective()} to stamp the
 * identity of the run that the next callback will capture.</p>
 *
 * <ul>
 *   <li>{@link #onMultiObjectiveComplete} &mdash; one front point per
 *       re-simulated Pareto solution, extracted from each solution's carried
 *       summary via the {@link ExperimentSpec}.</li>
 *   <li>{@link #onRunComplete} &mdash; a single-point front from the one summary
 *       (baselines / single-objective runs).</li>
 * </ul>
 *
 * <p>An empty Pareto front (or a missing summary) yields an empty
 * {@link AlgorithmRunResult} plus a logged warning, rather than fabricating a
 * point.</p>
 */
public final class ExperimentObserver implements SimulationListener {

    private final ExperimentSpec spec;
    private final SimulationLogger logger;
    private final List<AlgorithmRunResult> results = new ArrayList<>();

    // Identity of the run currently being captured.
    private boolean runStarted;
    private String currentLabel;
    private int currentScenarioNumber;
    private String currentScenarioName;
    private long currentSeed;
    private long currentStartMillis;

    public ExperimentObserver(ExperimentSpec spec) {
        this(spec, new SimulationLogger());
    }

    public ExperimentObserver(ExperimentSpec spec, SimulationLogger logger) {
        if (spec == null) {
            throw new IllegalArgumentException("ExperimentSpec must not be null");
        }
        this.spec = spec;
        this.logger = logger != null ? logger : new SimulationLogger();
    }

    /**
     * Stamps the identity of the next run to be captured. Must be called before
     * the corresponding {@code engine.run()} / {@code engine.runMultiObjective()}.
     *
     * @param label          algorithm label (e.g. {@code "NSGA-II"})
     * @param scenarioNumber 1-based scenario number
     * @param scenarioName   scenario name (e.g. {@code "Balanced"})
     * @param seed           random seed for this run
     */
    public void beginRun(String label, int scenarioNumber, String scenarioName, long seed) {
        this.runStarted = true;
        this.currentLabel = label;
        this.currentScenarioNumber = scenarioNumber;
        this.currentScenarioName = scenarioName;
        this.currentSeed = seed;
        this.currentStartMillis = System.currentTimeMillis();
    }

    @Override
    public void onMultiObjectiveComplete(MultiObjectiveSimulationResult result) {
        long runtimeMs = elapsedMs();
        List<double[]> front = new ArrayList<>();
        List<Double> auxPeaks = spec.hasAuxPeak() ? new ArrayList<>() : null;

        if (result != null) {
            for (SolutionSimulationResult sr : result.getSolutionResults()) {
                SimulationSummary summary = sr.getSimulationSummary();
                if (summary == null) {
                    continue;
                }
                front.add(spec.extractObjectives(summary));
                if (auxPeaks != null) {
                    auxPeaks.add(spec.extractAuxPeakWatts(summary));
                }
            }
        }

        if (front.isEmpty()) {
            logger.warn("ExperimentObserver: empty multi-objective front captured for "
                + describeCurrentRun() + " (no front point recorded).");
        }
        record(front, auxPeaks, runtimeMs);
    }

    @Override
    public void onRunComplete(SimulationContext context, SimulationSummary summary) {
        long runtimeMs = elapsedMs();
        List<double[]> front = new ArrayList<>();
        List<Double> auxPeaks = spec.hasAuxPeak() ? new ArrayList<>() : null;

        if (summary != null) {
            front.add(spec.extractObjectives(summary));
            if (auxPeaks != null) {
                auxPeaks.add(spec.extractAuxPeakWatts(summary));
            }
        } else {
            logger.warn("ExperimentObserver: null summary on run completion for "
                + describeCurrentRun() + " (no MetricsCollectionStep ran?); empty front recorded.");
        }
        record(front, auxPeaks, runtimeMs);
    }

    private void record(List<double[]> front, List<Double> auxPeaks, long runtimeMs) {
        AlgorithmRunResult run = new AlgorithmRunResult(
            currentLabel != null ? currentLabel : "unknown",
            currentScenarioNumber,
            currentScenarioName,
            currentSeed,
            spec.getObjectiveNames(),
            front,
            auxPeaks,
            runtimeMs);
        results.add(run);
    }

    private long elapsedMs() {
        return (runStarted && currentStartMillis > 0)
            ? Math.max(0L, System.currentTimeMillis() - currentStartMillis)
            : 0L;
    }

    private String describeCurrentRun() {
        return (currentLabel != null ? currentLabel : "unknown")
            + " [scenario " + currentScenarioNumber + " '" + currentScenarioName
            + "', seed " + currentSeed + "]";
    }

    // ---- Accessors ----------------------------------------------------------

    public ExperimentSpec getSpec() {
        return spec;
    }

    /** @return all captured runs in capture order (defensive copy). */
    public List<AlgorithmRunResult> getResults() {
        return new ArrayList<>(results);
    }

    /**
     * Groups captured runs by scenario name, preserving capture order within
     * each scenario.
     *
     * @return scenario name &rarr; runs for that scenario
     */
    public Map<String, List<AlgorithmRunResult>> resultsByScenario() {
        Map<String, List<AlgorithmRunResult>> byScenario = new LinkedHashMap<>();
        for (AlgorithmRunResult run : results) {
            byScenario.computeIfAbsent(run.getScenarioName(), k -> new ArrayList<>()).add(run);
        }
        return Collections.unmodifiableMap(byScenario);
    }
}
