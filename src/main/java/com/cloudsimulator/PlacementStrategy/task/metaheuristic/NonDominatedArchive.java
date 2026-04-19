package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Non-dominated archive for single-objective metaheuristics (GA, SA) running
 * under a weighted-sum fitness in a multi-objective evaluation space.
 *
 * The archive stores copies of solutions whose raw objective vector is not
 * dominated by any other stored solution. Offer a solution and it is admitted
 * iff it is non-dominated; any archive members it dominates are evicted.
 *
 * This lets a single-objective search (which returns one fitness-best point)
 * also surface the Pareto-useful trajectory points it passed through — e.g.
 * an injected heuristic seed that starts on the universal front stays in the
 * archive even after the search drifts to a better fitness with worse trade-off.
 */
public class NonDominatedArchive {

    private final boolean[] minimization;
    private final List<SchedulingSolution> members;

    public NonDominatedArchive(boolean[] minimization) {
        this.minimization = minimization.clone();
        this.members = new ArrayList<>();
    }

    /**
     * Offers a candidate to the archive. A copy is stored if accepted.
     * Duplicates (exact objective-vector equality) are not re-added.
     *
     * @return true if the candidate was added
     */
    public boolean offer(SchedulingSolution candidate) {
        if (candidate == null) return false;

        double[] candObj = candidate.getObjectiveValues();

        Iterator<SchedulingSolution> it = members.iterator();
        while (it.hasNext()) {
            SchedulingSolution m = it.next();
            double[] mObj = m.getObjectiveValues();
            int cmp = dominanceCompare(candObj, mObj);
            if (cmp > 0) {
                // Existing member dominates the candidate.
                return false;
            } else if (cmp < 0) {
                // Candidate dominates the existing member -> evict.
                it.remove();
            } else if (objectivesEqual(candObj, mObj)) {
                return false;
            }
        }

        members.add(candidate.copy());
        return true;
    }

    public List<SchedulingSolution> getMembers() {
        return new ArrayList<>(members);
    }

    public int size() {
        return members.size();
    }

    /**
     * @return -1 if a dominates b, +1 if b dominates a, 0 if non-dominated pair
     */
    private int dominanceCompare(double[] a, double[] b) {
        boolean aBetterAny = false;
        boolean bBetterAny = false;
        for (int i = 0; i < a.length; i++) {
            double av = a[i];
            double bv = b[i];
            if (av == bv) continue;
            boolean aBetter = minimization[i] ? av < bv : av > bv;
            if (aBetter) aBetterAny = true;
            else bBetterAny = true;
            if (aBetterAny && bBetterAny) return 0;
        }
        if (aBetterAny && !bBetterAny) return -1;
        if (bBetterAny && !aBetterAny) return 1;
        return 0;
    }

    private boolean objectivesEqual(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1e-12) return false;
        }
        return true;
    }
}
