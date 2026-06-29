package com.cloudsimulator.observer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One algorithm's result for one (scenario, seed) run: the re-simulated Pareto
 * front plus the quality indicators computed by {@link ParetoAnalyzer}.
 *
 * <p>Unifies the per-runner {@code AlgorithmResult} inner classes. The front is
 * a list of objective vectors (size 2 for the pairwise studies; size 1 for
 * baselines / single-objective runs). {@link #getAuxPeakPowerWatts()} is
 * {@code null} for every study except PowerCeiling but is present in the API
 * from day one.</p>
 *
 * <p>Identity, front, aux and runtime are immutable (set at construction). The
 * indicator fields are populated later by {@link ParetoAnalyzer} and default to
 * {@code 0} / {@code NaN} until then.</p>
 */
public final class AlgorithmRunResult {

    // ---- Identity & captured data (immutable) ------------------------------
    private final String label;
    private final int scenarioNumber;
    private final String scenarioName;
    private final long seed;
    private final List<String> objectiveNames;
    private final List<double[]> front;
    private final List<Double> auxPeakPowerWatts; // nullable (PowerCeiling only)
    private final long runtimeMs;

    // ---- Indicators set by the analyzer ------------------------------------
    // Legacy (PerformanceMetrics-compatible) indicators, for the
    // performance_metrics / experiment_summary CSV columns.
    private double hv;
    private double gd;
    private double igd;
    private double spacing;

    // Fixed-reference indicators matching recompute_hv.py (the quality_indicators
    // oracle): HV against a scenario-fixed reference, additive epsilon+, and the
    // Pareto-contribution percentage.
    private double hvFixed = Double.NaN;
    private double epsilonPlus = Double.NaN;
    private double paretoContributionPct = Double.NaN;

    // Integer contribution count (distinct universal points matched) for the
    // performance_metrics ParetoContribution column.
    private int paretoContributionCount;

    private List<double[]> nonDominatedFront;
    private int nonDominatedCount;
    private int totalCount;

    public AlgorithmRunResult(String label, int scenarioNumber, String scenarioName, long seed,
                              List<String> objectiveNames, List<double[]> front,
                              List<Double> auxPeakPowerWatts, long runtimeMs) {
        this.label = label;
        this.scenarioNumber = scenarioNumber;
        this.scenarioName = scenarioName;
        this.seed = seed;
        this.objectiveNames = objectiveNames == null
            ? Collections.emptyList() : new ArrayList<>(objectiveNames);
        this.front = copyFront(front);
        this.auxPeakPowerWatts = auxPeakPowerWatts == null ? null : new ArrayList<>(auxPeakPowerWatts);
        this.runtimeMs = runtimeMs;
        this.totalCount = this.front.size();
    }

    private static List<double[]> copyFront(List<double[]> src) {
        List<double[]> copy = new ArrayList<>();
        if (src != null) {
            for (double[] v : src) {
                copy.add(v == null ? null : v.clone());
            }
        }
        return copy;
    }

    // ---- Identity / captured data accessors --------------------------------

    public String getLabel() { return label; }
    public int getScenarioNumber() { return scenarioNumber; }
    public String getScenarioName() { return scenarioName; }
    public long getSeed() { return seed; }
    public List<String> getObjectiveNames() { return Collections.unmodifiableList(objectiveNames); }

    /** @return the re-simulated front (defensive copy). */
    public List<double[]> getFront() { return copyFront(front); }

    /** @return per-solution auxiliary peak power (W), or {@code null} if none. */
    public List<Double> getAuxPeakPowerWatts() {
        return auxPeakPowerWatts == null ? null : Collections.unmodifiableList(auxPeakPowerWatts);
    }

    public long getRuntimeMs() { return runtimeMs; }

    /** @return {@code true} if this run produced no front points. */
    public boolean isEmpty() { return front.isEmpty(); }

    // ---- Indicator accessors -----------------------------------------------

    public double getHv() { return hv; }
    public void setHv(double hv) { this.hv = hv; }

    public double getGd() { return gd; }
    public void setGd(double gd) { this.gd = gd; }

    public double getIgd() { return igd; }
    public void setIgd(double igd) { this.igd = igd; }

    public double getSpacing() { return spacing; }
    public void setSpacing(double spacing) { this.spacing = spacing; }

    public double getHvFixed() { return hvFixed; }
    public void setHvFixed(double hvFixed) { this.hvFixed = hvFixed; }

    public double getEpsilonPlus() { return epsilonPlus; }
    public void setEpsilonPlus(double epsilonPlus) { this.epsilonPlus = epsilonPlus; }

    public double getParetoContributionPct() { return paretoContributionPct; }
    public void setParetoContributionPct(double pct) { this.paretoContributionPct = pct; }

    public int getParetoContributionCount() { return paretoContributionCount; }
    public void setParetoContributionCount(int count) { this.paretoContributionCount = count; }

    public List<double[]> getNonDominatedFront() {
        return nonDominatedFront == null ? null : copyFront(nonDominatedFront);
    }

    public void setNonDominatedFront(List<double[]> nonDominatedFront) {
        this.nonDominatedFront = copyFront(nonDominatedFront);
        this.nonDominatedCount = this.nonDominatedFront.size();
    }

    public int getNonDominatedCount() { return nonDominatedCount; }
    public int getTotalCount() { return totalCount; }

    @Override
    public String toString() {
        return "AlgorithmRunResult{" + label + ", scenario=" + scenarioNumber
            + ", seed=" + seed + ", frontSize=" + front.size()
            + ", hvFixed=" + hvFixed + ", epsPlus=" + epsilonPlus
            + ", contribPct=" + paretoContributionPct + "}";
    }
}
