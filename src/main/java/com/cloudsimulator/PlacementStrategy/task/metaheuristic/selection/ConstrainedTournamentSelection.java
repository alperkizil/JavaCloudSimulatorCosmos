package com.cloudsimulator.PlacementStrategy.task.metaheuristic.selection;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.List;

/**
 * Tournament selection under Deb's constrained-domination rules.
 *
 * <p>Constraint-aware counterpart of {@link TournamentSelection}: the tournament is
 * drawn identically, but the winner is decided by
 * {@link #constrainedCompare(double, double, double, double, boolean)} rather than by
 * fitness alone:</p>
 *
 * <pre>
 *   one feasible, one not  -&gt; the feasible one wins
 *   both infeasible        -&gt; the smaller violation wins
 *   both feasible          -&gt; the better fitness wins
 * </pre>
 *
 * <p>This is the same ordering {@code ConstrainedNonDominatedArchive} applies when
 * publishing, so the search is driven toward feasibility by the criterion the archive
 * is later judged on, instead of stumbling into it.</p>
 *
 * <p>The plain {@link SelectionOperator#select} overload ignores constraints and
 * behaves exactly like {@link TournamentSelection}, so this class is a drop-in
 * replacement wherever violations are not available. Both overloads draw the same
 * number of values from the shared {@link RandomGenerator} as
 * {@link TournamentSelection} does, keeping the random stream aligned.</p>
 */
public class ConstrainedTournamentSelection implements SelectionOperator {

    private final int tournamentSize;
    private final RandomGenerator random;

    public ConstrainedTournamentSelection(int tournamentSize) {
        if (tournamentSize < 2) {
            throw new IllegalArgumentException("Tournament size must be at least 2");
        }
        this.tournamentSize = tournamentSize;
        this.random = RandomGenerator.getInstance();
    }

    public ConstrainedTournamentSelection() {
        this(2);
    }

    /** Unconstrained selection — every candidate is treated as feasible. */
    @Override
    public SchedulingSolution select(List<SchedulingSolution> population,
                                     double[] fitnessValues,
                                     boolean isMinimization) {
        return select(population, fitnessValues, null, isMinimization);
    }

    /**
     * Selects one parent under Deb's rules.
     *
     * @param violations constraint-violation magnitudes parallel to {@code population}
     *                   (0 means feasible). {@code null} selects on fitness alone.
     */
    public SchedulingSolution select(List<SchedulingSolution> population,
                                     double[] fitnessValues,
                                     double[] violations,
                                     boolean isMinimization) {
        if (population.isEmpty()) {
            throw new IllegalArgumentException("Population cannot be empty");
        }
        if (population.size() != fitnessValues.length) {
            throw new IllegalArgumentException("Population size must match fitness values length");
        }
        if (violations != null && violations.length != population.size()) {
            throw new IllegalArgumentException("Population size must match violations length");
        }

        int popSize = population.size();
        int actualTournamentSize = Math.min(tournamentSize, popSize);

        int bestIdx = random.nextInt(popSize);
        for (int i = 1; i < actualTournamentSize; i++) {
            int candidateIdx = random.nextInt(popSize);
            double candViol = (violations == null) ? 0.0 : violations[candidateIdx];
            double bestViol = (violations == null) ? 0.0 : violations[bestIdx];
            if (constrainedCompare(fitnessValues[candidateIdx], candViol,
                                   fitnessValues[bestIdx], bestViol, isMinimization) < 0) {
                bestIdx = candidateIdx;
            }
        }
        return population.get(bestIdx).copy();
    }

    /**
     * Deb's constrained comparison. Returns a negative number when {@code (fitnessA,
     * violationA)} is the better of the two, positive when it is worse, 0 when neither
     * is preferred.
     */
    public static int constrainedCompare(double fitnessA, double violationA,
                                         double fitnessB, double violationB,
                                         boolean isMinimization) {
        boolean feasibleA = violationA <= 0.0;
        boolean feasibleB = violationB <= 0.0;

        if (feasibleA != feasibleB) {
            return feasibleA ? -1 : 1;              // feasible beats infeasible
        }
        if (!feasibleA) {
            return Double.compare(violationA, violationB);   // both infeasible: less violation
        }
        return isMinimization                        // both feasible: better fitness
            ? Double.compare(fitnessA, fitnessB)
            : Double.compare(fitnessB, fitnessA);
    }

    public int getTournamentSize() {
        return tournamentSize;
    }

    @Override
    public String getName() {
        return "Constrained Tournament Selection (k=" + tournamentSize + ", Deb rules)";
    }

    @Override
    public String getDescription() {
        return "Selects the best of " + tournamentSize + " randomly chosen candidates under "
            + "Deb's constrained-domination rules: feasible beats infeasible, smaller "
            + "violation breaks ties among infeasibles, fitness decides among feasibles.";
    }
}
