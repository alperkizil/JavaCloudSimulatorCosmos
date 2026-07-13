package com.cloudsimulator.newExperiments;

import com.cloudsimulator.observer.AlgorithmRunResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Derives power-cap thresholds dynamically from an <em>uncapped</em> run's observed
 * coincident-peak distribution, instead of hardcoding them.
 *
 * <p>The experiment runs all (metaheuristic) arms with no power ceiling, capturing
 * each solution's coincident Step-8 peak. This calibrator then sets each cap at the
 * peak percentile that corresponds to a target feasibility fraction: by definition,
 * a cap placed at the <em>t</em>-th percentile of the peaks makes ~<em>t%</em> of
 * solutions feasible. With {@link #DEFAULT_FEASIBILITY_TARGETS} this yields the
 * ~90 / 60 / 30% tiers, computed globally (pooled across all scenarios).</p>
 */
public final class PowerCapCalibrator {

    private PowerCapCalibrator() {}

    /** Default target feasibility fractions (%) for the three cap tiers, loose→tight. */
    public static final double[] DEFAULT_FEASIBILITY_TARGETS = {90.0, 60.0, 30.0};

    /**
     * Pools every non-null coincident peak across the given runs and returns the cap
     * (Watts) for each target feasibility percent, in the same order as
     * {@code targetFeasibilityPercents}. Returns an empty array if no peaks exist.
     */
    public static double[] deriveCaps(List<AlgorithmRunResult> runs, double[] targetFeasibilityPercents) {
        List<Double> peaks = poolPeaks(runs);
        return deriveCapsFromPeaks(peaks, targetFeasibilityPercents);
    }

    /** As {@link #deriveCaps} but from a pre-pooled peak list (Watts). */
    public static double[] deriveCapsFromPeaks(List<Double> peaksWatts, double[] targetFeasibilityPercents) {
        if (peaksWatts == null || peaksWatts.isEmpty() || targetFeasibilityPercents == null) {
            return new double[0];
        }
        List<Double> sorted = new ArrayList<>(peaksWatts);
        Collections.sort(sorted);
        double[] caps = new double[targetFeasibilityPercents.length];
        for (int i = 0; i < targetFeasibilityPercents.length; i++) {
            caps[i] = percentile(sorted, targetFeasibilityPercents[i]);
        }
        return caps;
    }

    /** Flattens every run's non-null {@code auxPeakPowerWatts} into one list. */
    public static List<Double> poolPeaks(List<AlgorithmRunResult> runs) {
        List<Double> peaks = new ArrayList<>();
        if (runs == null) {
            return peaks;
        }
        for (AlgorithmRunResult run : runs) {
            List<Double> p = run.getAuxPeakPowerWatts();
            if (p == null) {
                continue;
            }
            for (Double v : p) {
                if (v != null && Double.isFinite(v)) {
                    peaks.add(v);
                }
            }
        }
        return peaks;
    }

    /**
     * Linear-interpolated percentile of an already-sorted (ascending) list, using the
     * {@code (n-1)*p/100} index convention (matches numpy's default and the Python
     * calibration script).
     */
    static double percentile(List<Double> sortedAscending, double percent) {
        int n = sortedAscending.size();
        if (n == 0) {
            return Double.NaN;
        }
        if (n == 1) {
            return sortedAscending.get(0);
        }
        double clamped = Math.max(0.0, Math.min(100.0, percent));
        double k = (n - 1) * clamped / 100.0;
        int lo = (int) Math.floor(k);
        int hi = (int) Math.ceil(k);
        if (lo == hi) {
            return sortedAscending.get(lo);
        }
        double frac = k - lo;
        return sortedAscending.get(lo) * (1.0 - frac) + sortedAscending.get(hi) * frac;
    }
}
