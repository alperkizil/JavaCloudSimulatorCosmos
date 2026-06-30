package com.cloudsimulator.observer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes per-(algorithm, power-cap) feasibility rates from the per-solution
 * auxiliary peak-power values carried by PowerCeiling runs.
 *
 * <p>A solution is <em>feasible</em> at a cap when its coincident peak power is
 * at or below the cap. The feasibility rate for an algorithm at a cap is the
 * fraction of that algorithm's solutions (pooled across its seeds/runs) that are
 * feasible.</p>
 *
 * <p>Used by the PowerCeiling study; ships now so the later migration is
 * mechanical. Runs without auxiliary peak data are ignored.</p>
 */
public final class PowerCapFeasibility {

    private PowerCapFeasibility() {
    }

    /**
     * Computes feasibility rates using {@link ExperimentSpec#DEFAULT_CAP_THRESHOLDS_WATTS}.
     *
     * @param runs the runs to analyse (only those with aux peak data contribute)
     * @return algorithm label &rarr; (cap Watts &rarr; feasibility rate in [0,1])
     */
    public static Map<String, Map<Double, Double>> feasibilityRates(List<AlgorithmRunResult> runs) {
        return feasibilityRates(runs, ExperimentSpec.DEFAULT_CAP_THRESHOLDS_WATTS);
    }

    /**
     * Computes per-(algorithm, cap) feasibility rates.
     *
     * @param runs           the runs to analyse
     * @param capsWatts      cap thresholds in Watts
     * @return algorithm label &rarr; (cap Watts &rarr; feasibility rate in [0,1]).
     *         A cap entry is {@code NaN} when the algorithm has no aux peak values.
     */
    public static Map<String, Map<Double, Double>> feasibilityRates(
            List<AlgorithmRunResult> runs, double[] capsWatts) {

        if (runs == null || capsWatts == null) {
            throw new IllegalArgumentException("runs and capsWatts must not be null");
        }

        // Accumulate per-label: total peak count and per-cap feasible counts.
        Map<String, long[]> feasibleByLabel = new LinkedHashMap<>(); // label -> count per cap
        Map<String, Long> totalByLabel = new LinkedHashMap<>();

        for (AlgorithmRunResult run : runs) {
            List<Double> peaks = run.getAuxPeakPowerWatts();
            if (peaks == null) {
                continue;
            }
            String label = run.getLabel();
            long[] feasible = feasibleByLabel.computeIfAbsent(label, k -> new long[capsWatts.length]);
            long total = totalByLabel.getOrDefault(label, 0L);
            for (Double peakObj : peaks) {
                if (peakObj == null) {
                    continue;
                }
                double peak = peakObj;
                total++;
                for (int c = 0; c < capsWatts.length; c++) {
                    if (peak <= capsWatts[c]) {
                        feasible[c]++;
                    }
                }
            }
            totalByLabel.put(label, total);
        }

        Map<String, Map<Double, Double>> rates = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> entry : feasibleByLabel.entrySet()) {
            String label = entry.getKey();
            long[] feasible = entry.getValue();
            long total = totalByLabel.getOrDefault(label, 0L);
            Map<Double, Double> perCap = new LinkedHashMap<>();
            for (int c = 0; c < capsWatts.length; c++) {
                perCap.put(capsWatts[c], total > 0 ? (double) feasible[c] / total : Double.NaN);
            }
            rates.put(label, perCap);
        }
        return rates;
    }
}
