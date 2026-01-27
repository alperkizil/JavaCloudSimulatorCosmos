package com.cloudsimulator.multiobjectivePerformance.PerfMet;

import java.util.ArrayList;

/**
 * Pareto dominance comparison for multi-objective optimization.
 * Uses standard Pareto dominance: a dominates b if a is at least as good
 * in ALL objectives AND strictly better in AT LEAST ONE objective.
 *
 * @author kazim.erdogdu
 */
public class Dominance {
    /**
     * Compares two solutions using Pareto dominance (minimization assumed).
     *
     * @param f1 First solution's objective values
     * @param f2 Second solution's objective values
     * @return -1 if f1 dominates f2, 1 if f2 dominates f1, 0 if neither dominates
     */
    public static int compare(ArrayList<Double> f1, ArrayList<Double> f2) {
        boolean f1BetterInAtLeastOne = false;
        boolean f2BetterInAtLeastOne = false;

        for (int i = 0; i < f1.size(); i++) {
            double v1 = f1.get(i);
            double v2 = f2.get(i);

            if (v1 < v2) {
                f1BetterInAtLeastOne = true;
            } else if (v2 < v1) {
                f2BetterInAtLeastOne = true;
            }
        }

        // f1 dominates f2: f1 is better in at least one AND f2 is not better in any
        if (f1BetterInAtLeastOne && !f2BetterInAtLeastOne) {
            return -1;
        }
        // f2 dominates f1: f2 is better in at least one AND f1 is not better in any
        if (f2BetterInAtLeastOne && !f1BetterInAtLeastOne) {
            return 1;
        }
        // Neither dominates (both are better in some objectives, or all equal)
        return 0;
    }
}
