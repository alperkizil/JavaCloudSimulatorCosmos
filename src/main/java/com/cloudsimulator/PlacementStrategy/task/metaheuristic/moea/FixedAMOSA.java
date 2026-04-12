package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import org.moeaframework.algorithm.sa.AMOSA;
import org.moeaframework.core.Initialization;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Problem;
import org.moeaframework.core.Solution;
import org.moeaframework.core.comparator.DominanceComparator;
import org.moeaframework.core.comparator.ParetoDominanceComparator;
import org.moeaframework.core.operator.Mutation;
import org.moeaframework.util.clustering.Clustering;

/**
 * Extended AMOSA that fixes two bugs in the MOEA Framework's calculateDeltaDominance:
 *
 * <ol>
 *   <li><b>Product initialization:</b> The original initializes {@code deltaDominance = 0.0},
 *       but the AMOSA paper (Bandyopadhyay et al., 2008) defines it as a product:
 *       {@code ΔDom(a,b) = ∏(|f_i(a) - f_i(b)| / r_i)}. Since {@code 0 * x = 0},
 *       delta dominance always returns 0, making all acceptance probabilities = 0.5
 *       regardless of solution quality or temperature. Fixed to {@code 1.0}.</li>
 *   <li><b>Division by zero:</b> When an objective has identical values across all archive
 *       members (common with discrete makespan), {@code r[i] = 0} causes NaN/Infinity.
 *       Fixed by skipping zero-range dimensions.</li>
 * </ol>
 *
 * Since the buggy methods are {@code private} in AMOSA, this subclass overrides the
 * {@code protected iterate(double)} method with a corrected implementation.
 */
public class FixedAMOSA extends AMOSA {

    private final DominanceComparator comparator;
    private int maxEvaluations = Integer.MAX_VALUE;

    public FixedAMOSA(Problem problem, Initialization initialization, Mutation mutation,
                      double gamma, int softLimit, int hardLimit,
                      double stoppingTemperature, double initialTemperature, double alpha,
                      int numberOfIterationsPerTemperature,
                      int numberOfHillClimbingIterationsForRefinement) {
        super(problem, initialization, mutation, gamma, softLimit, hardLimit,
              stoppingTemperature, initialTemperature, alpha,
              numberOfIterationsPerTemperature, numberOfHillClimbingIterationsForRefinement);
        this.comparator = new ParetoDominanceComparator();
    }

    /**
     * Sets the maximum number of fitness evaluations. The iterate loop will
     * break early when this budget is reached, ensuring fair comparison
     * across algorithms.
     */
    public void setMaxEvaluations(int maxEvaluations) {
        this.maxEvaluations = maxEvaluations;
    }

    @Override
    protected void iterate(double temperature) {
        int iterationsPerTemp = getNumberOfIterationsPerTemperature();
        int sl = getSoftLimit();
        int hl = getHardLimit();

        for (int i = 0; i < iterationsPerTemp; i++) {
            if (getNumberOfEvaluations() >= maxEvaluations) break;
            Solution newPoint = mutation.mutate(currentPoint);
            evaluate(newPoint);

            double[] r = calculateR(newPoint);

            // Check domination status of currentPoint vs newPoint
            int comparisonResult = comparator.compare(currentPoint, newPoint);

            if (comparisonResult < 0) {
                // Case 1: currentPoint dominates newPoint
                double averageDeltaDominance = calculateAverageDeltaDominance(newPoint, r);
                double probability = 1.0 / (1.0 + Math.exp(averageDeltaDominance * temperature));

                if (PRNG.nextDouble() < probability) {
                    currentPoint = newPoint;
                }
            } else if (comparisonResult == 0) {
                // Case 2: currentPoint and newPoint are non-dominating
                DominationAmount dominationAmount = calculateDominationAmounts(newPoint);

                if (dominationAmount.dominatedAmount > 0) {
                    // Case 2(a): newPoint is dominated by k >= 1 archive points
                    double averageDeltaDominance = calculateAverageDeltaDominance(newPoint, r);
                    double probability = 1.0 / (1.0 + Math.exp(averageDeltaDominance * temperature));

                    if (PRNG.nextDouble() < probability) {
                        currentPoint = newPoint;
                    }
                } else if (dominationAmount.dominatedAmount == 0 && dominationAmount.dominatesAmount == 0) {
                    // Case 2(b): newPoint is non-dominating w.r.t all archive points
                    currentPoint = newPoint;
                    archive.add(currentPoint);

                    if (archive.size() > sl) {
                        Clustering.singleLinkage().truncate(hl, archive);
                    }
                } else if (dominationAmount.dominatesAmount > 0) {
                    // Case 2(c): newPoint dominates k >= 1 archive points
                    currentPoint = newPoint;
                    archive.add(currentPoint);
                }
            } else {
                // Case 3: newPoint dominates currentPoint
                DominationAmount dominationAmount = calculateDominationAmounts(newPoint);

                if (dominationAmount.dominatedAmount > 0) {
                    // Case 3(a): newPoint is dominated by k >= 1 archive points
                    MinimumDeltaDominance minDD = calculateMinimumDeltaDominance(newPoint, r);
                    double probability = 1.0 / (1.0 + Math.exp(-1.0 * minDD.minimumDeltaDominance));

                    if (PRNG.nextDouble() < probability) {
                        currentPoint = archive.get(minDD.minimumIndex);
                    } else {
                        currentPoint = newPoint;
                    }
                } else if (dominationAmount.dominatedAmount == 0 && dominationAmount.dominatesAmount == 0) {
                    // Case 3(b): newPoint is non-dominating w.r.t all archive points
                    currentPoint = newPoint;

                    if (!archive.add(currentPoint)) {
                        archive.remove(currentPoint);
                    } else if (archive.size() > sl) {
                        Clustering.singleLinkage().truncate(hl, archive);
                    }
                } else if (dominationAmount.dominatesAmount > 0) {
                    // Case 3(c): newPoint dominates k >= 1 archive points
                    currentPoint = newPoint;
                    archive.add(currentPoint);
                }
            }
        }
    }

    // ==================== Fixed helper methods ====================

    /**
     * Calculates the range of each objective across the archive and new point.
     */
    private double[] calculateR(Solution newPoint) {
        int nObj = newPoint.getNumberOfObjectives();
        double[] r = new double[nObj];
        double[] worsts = new double[nObj];
        double[] bests = new double[nObj];

        for (int i = 0; i < nObj; i++) {
            worsts[i] = newPoint.getObjective(i);
            bests[i] = newPoint.getObjective(i);
        }

        for (int i = 0; i < nObj; i++) {
            for (int j = 0; j < archive.size(); j++) {
                double val = archive.get(j).getObjective(i);
                if (val < bests[i]) {
                    bests[i] = val;
                } else if (val > worsts[i]) {
                    worsts[i] = val;
                }
            }
            r[i] = worsts[i] - bests[i];
        }

        return r;
    }

    /**
     * FIX: Product initialized at 1.0 (not 0.0), and division-by-zero guarded.
     *
     * Per the AMOSA paper: ΔDom(a,b) = ∏(|f_i(a) - f_i(b)| / r_i)
     */
    private double calculateDeltaDominance(Solution solutionA, Solution solutionB, double[] r) {
        double deltaDominance = 1.0;  // FIX: was 0.0 in original

        for (int i = 0; i < solutionA.getNumberOfObjectives(); i++) {
            if (r[i] > 1e-10) {  // FIX: guard against division by zero
                deltaDominance *= Math.abs(solutionA.getObjective(i) - solutionB.getObjective(i)) / r[i];
            }
        }

        return deltaDominance;
    }

    private double calculateAverageDeltaDominance(Solution newPoint, double[] r) {
        double totalDeltaDominance = 0.0;
        int k = 0;

        for (int i = 0; i < archive.size(); i++) {
            if (comparator.compare(archive.get(i), newPoint) < 0) {
                k++;
                totalDeltaDominance += calculateDeltaDominance(newPoint, archive.get(i), r);
            }
        }

        if (comparator.compare(currentPoint, newPoint) < 0) {
            k++;
            totalDeltaDominance += calculateDeltaDominance(newPoint, currentPoint, r);
        }

        return k > 0 ? totalDeltaDominance / k : 0.0;
    }

    private MinimumDeltaDominance calculateMinimumDeltaDominance(Solution newPoint, double[] r) {
        MinimumDeltaDominance minDD = new MinimumDeltaDominance();

        for (int i = 0; i < archive.size(); i++) {
            if (comparator.compare(newPoint, archive.get(i)) < 0) {
                double deltaDominance = calculateDeltaDominance(newPoint, archive.get(i), r);
                minDD.update(deltaDominance, i);
            }
        }

        return minDD;
    }

    private DominationAmount calculateDominationAmounts(Solution newPoint) {
        DominationAmount dominationAmount = new DominationAmount();

        for (int i = 0; i < archive.size(); i++) {
            int result = comparator.compare(newPoint, archive.get(i));
            if (result < 0) {
                dominationAmount.dominatesAmount++;
            } else if (result > 0) {
                dominationAmount.dominatedAmount++;
            }
        }

        return dominationAmount;
    }

    // ==================== Inner helper classes ====================

    private static class DominationAmount {
        int dominatedAmount;
        int dominatesAmount;
    }

    private static class MinimumDeltaDominance {
        double minimumDeltaDominance = Double.MAX_VALUE;
        int minimumIndex = 0;

        void update(double deltaDominance, int index) {
            if (deltaDominance < minimumDeltaDominance) {
                minimumDeltaDominance = deltaDominance;
                minimumIndex = index;
            }
        }
    }
}
