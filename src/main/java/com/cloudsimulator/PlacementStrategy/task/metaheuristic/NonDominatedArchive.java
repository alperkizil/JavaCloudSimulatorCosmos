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
    private final double epsilonFraction;

    public NonDominatedArchive(boolean[] minimization) {
        this(minimization, 0.0);
    }

    /**
     * @param epsilonFraction epsilon-resolution as a fraction of the archive's
     *        current per-objective range (e.g. 0.01 = 1%). A mutually
     *        non-dominated candidate lying within that box of an existing
     *        member is not admitted, pruning near-duplicate trajectory points.
     *        0 disables the filter (pure dominance archiving). Admission-time
     *        only: as the ranges widen during the run, points admitted earlier
     *        are not re-pruned.
     */
    public NonDominatedArchive(boolean[] minimization, double epsilonFraction) {
        this.minimization = minimization.clone();
        this.members = new ArrayList<>();
        this.epsilonFraction = Math.max(0.0, epsilonFraction);
    }

    /**
     * Offers a candidate to the archive. A copy is stored if accepted.
     * Duplicates (exact objective-vector equality) are not re-added, and with
     * a positive epsilon fraction neither are near-duplicates of members.
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

        if (epsilonFraction > 0.0 && withinEpsilonOfMember(candObj)) {
            return false;
        }

        members.add(candidate.copy());
        return true;
    }

    /**
     * True if the candidate lies within the epsilon box of any member, where
     * the box is epsilonFraction of the per-objective range over the current
     * members plus the candidate. Called only for candidates that are
     * mutually non-dominated with every remaining member.
     */
    private boolean withinEpsilonOfMember(double[] candObj) {
        if (members.isEmpty()) return false;
        int n = candObj.length;
        double[] min = candObj.clone();
        double[] max = candObj.clone();
        for (SchedulingSolution m : members) {
            double[] o = m.getObjectiveValues();
            for (int i = 0; i < n; i++) {
                if (o[i] < min[i]) min[i] = o[i];
                if (o[i] > max[i]) max[i] = o[i];
            }
        }
        double[] eps = new double[n];
        for (int i = 0; i < n; i++) {
            eps[i] = epsilonFraction * (max[i] - min[i]);
        }
        for (SchedulingSolution m : members) {
            double[] o = m.getObjectiveValues();
            boolean close = true;
            for (int i = 0; i < n; i++) {
                if (Math.abs(candObj[i] - o[i]) > eps[i]) {
                    close = false;
                    break;
                }
            }
            if (close) return true;
        }
        return false;
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
