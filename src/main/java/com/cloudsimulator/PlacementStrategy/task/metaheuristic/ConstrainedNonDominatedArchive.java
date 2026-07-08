package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Constraint-aware non-dominated archive implementing Deb's constrained-
 * domination rules (Deb et al., 2002):
 *   feasible ≻ infeasible
 *   both infeasible → smaller violation dominates
 *   both feasible   → standard Pareto on raw objectives
 *
 * Mirrors the storage/admission contract of {@link NonDominatedArchive} but
 * associates every member with its constraint violation magnitude so the
 * rules above decide admission/eviction.
 *
 * Additive: the unconstrained {@link NonDominatedArchive} is untouched so
 * baseline strategies keep their current behavior.
 */
public class ConstrainedNonDominatedArchive {

    private final boolean[] minimization;
    private final List<Member> members;

    private static class Member {
        final SchedulingSolution solution;
        final double violation;
        Member(SchedulingSolution s, double v) { this.solution = s; this.violation = v; }
    }

    private final double epsilonFraction;

    public ConstrainedNonDominatedArchive(boolean[] minimization) {
        this(minimization, 0.0);
    }

    /**
     * @param epsilonFraction epsilon-resolution as a fraction of the archive's
     *        current per-objective range over FEASIBLE members (see
     *        {@link NonDominatedArchive}). Applied only between a feasible
     *        candidate and feasible members — infeasible members are ranked by
     *        violation, not objective proximity. 0 disables the filter.
     */
    public ConstrainedNonDominatedArchive(boolean[] minimization, double epsilonFraction) {
        this.minimization = minimization.clone();
        this.members = new ArrayList<>();
        this.epsilonFraction = Math.max(0.0, epsilonFraction);
    }

    /**
     * Offer a candidate along with its constraint violation magnitude
     * (0.0 means feasible; positive means infeasible).
     */
    public boolean offer(SchedulingSolution candidate, double violation) {
        if (candidate == null) return false;
        double cViol = Math.max(0.0, violation);
        double[] candObj = candidate.getObjectiveValues();

        Iterator<Member> it = members.iterator();
        while (it.hasNext()) {
            Member m = it.next();
            int cmp = constrainedDominance(candObj, cViol, m.solution.getObjectiveValues(), m.violation);
            if (cmp > 0) {
                // Existing member dominates the candidate under Deb's rules.
                return false;
            } else if (cmp < 0) {
                // Candidate dominates the existing member -> evict.
                it.remove();
            } else if (cViol == m.violation && objectivesEqual(candObj, m.solution.getObjectiveValues())) {
                return false;
            }
        }

        if (epsilonFraction > 0.0 && cViol <= 0.0 && withinEpsilonOfFeasibleMember(candObj)) {
            return false;
        }

        members.add(new Member(candidate.copy(), cViol));
        return true;
    }

    /**
     * True if the feasible candidate lies within the epsilon box of any
     * feasible member; the box is epsilonFraction of the per-objective range
     * over the feasible members plus the candidate. Called only for
     * candidates that are mutually non-dominated with every remaining member.
     */
    private boolean withinEpsilonOfFeasibleMember(double[] candObj) {
        int n = candObj.length;
        double[] min = candObj.clone();
        double[] max = candObj.clone();
        boolean anyFeasible = false;
        for (Member m : members) {
            if (m.violation > 0.0) continue;
            anyFeasible = true;
            double[] o = m.solution.getObjectiveValues();
            for (int i = 0; i < n; i++) {
                if (o[i] < min[i]) min[i] = o[i];
                if (o[i] > max[i]) max[i] = o[i];
            }
        }
        if (!anyFeasible) return false;
        double[] eps = new double[n];
        for (int i = 0; i < n; i++) {
            eps[i] = epsilonFraction * (max[i] - min[i]);
        }
        for (Member m : members) {
            if (m.violation > 0.0) continue;
            double[] o = m.solution.getObjectiveValues();
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
        List<SchedulingSolution> out = new ArrayList<>(members.size());
        for (Member m : members) out.add(m.solution);
        return out;
    }

    public List<SchedulingSolution> getFeasibleMembers() {
        List<SchedulingSolution> out = new ArrayList<>();
        for (Member m : members) if (m.violation <= 0.0) out.add(m.solution);
        return out;
    }

    public int size() { return members.size(); }

    /**
     * Deb's constrained-domination:
     *   -1 if (a, aViol) dominates (b, bViol)
     *   +1 if (b, bViol) dominates (a, aViol)
     *    0 non-dominated
     */
    private int constrainedDominance(double[] a, double aViol, double[] b, double bViol) {
        boolean aFeasible = aViol <= 0.0;
        boolean bFeasible = bViol <= 0.0;
        if (aFeasible && !bFeasible) return -1;
        if (bFeasible && !aFeasible) return 1;
        if (!aFeasible && !bFeasible) {
            if (aViol < bViol) return -1;
            if (bViol < aViol) return 1;
            return 0; // equal violation, both infeasible -> non-dominated
        }
        // both feasible: standard Pareto on objectives
        return paretoDominance(a, b);
    }

    private int paretoDominance(double[] a, double[] b) {
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
