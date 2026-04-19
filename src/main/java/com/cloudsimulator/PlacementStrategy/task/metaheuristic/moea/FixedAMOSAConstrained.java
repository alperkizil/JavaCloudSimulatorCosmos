package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import org.moeaframework.algorithm.sa.AMOSA;
import org.moeaframework.core.Initialization;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Problem;
import org.moeaframework.core.Solution;
import org.moeaframework.core.comparator.AggregateConstraintComparator;
import org.moeaframework.core.comparator.ChainedComparator;
import org.moeaframework.core.comparator.DominanceComparator;
import org.moeaframework.core.comparator.ParetoDominanceComparator;
import org.moeaframework.core.operator.Mutation;
import org.moeaframework.util.clustering.Clustering;

/**
 * Constraint-aware duplicate of {@link FixedAMOSA}. Identical delta-dominance
 * fixes and iterate logic; differs only in the dominance comparator — chains
 * {@link AggregateConstraintComparator} ahead of {@link ParetoDominanceComparator}
 * so Deb's constrained-domination rules apply:
 *   feasible ≻ infeasible
 *   both infeasible → smaller aggregate violation wins
 *   both feasible   → Pareto dominance on objectives
 *
 * Additive: the base {@link FixedAMOSA} is untouched.
 */
public class FixedAMOSAConstrained extends AMOSA {

    private final DominanceComparator comparator;
    private int maxEvaluations = Integer.MAX_VALUE;

    public FixedAMOSAConstrained(Problem problem, Initialization initialization, Mutation mutation,
                                 double gamma, int softLimit, int hardLimit,
                                 double stoppingTemperature, double initialTemperature, double alpha,
                                 int numberOfIterationsPerTemperature,
                                 int numberOfHillClimbingIterationsForRefinement) {
        super(problem, initialization, mutation, gamma, softLimit, hardLimit,
              stoppingTemperature, initialTemperature, alpha,
              numberOfIterationsPerTemperature, numberOfHillClimbingIterationsForRefinement);
        this.comparator = new ChainedComparator(
            new AggregateConstraintComparator(),
            new ParetoDominanceComparator()
        );
    }

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

            int comparisonResult = comparator.compare(currentPoint, newPoint);

            if (comparisonResult < 0) {
                double averageDeltaDominance = calculateAverageDeltaDominance(newPoint, r);
                double probability = 1.0 / (1.0 + Math.exp(averageDeltaDominance * temperature));

                if (PRNG.nextDouble() < probability) {
                    currentPoint = newPoint;
                }
            } else if (comparisonResult == 0) {
                DominationAmount dominationAmount = calculateDominationAmounts(newPoint);

                if (dominationAmount.dominatedAmount > 0) {
                    double averageDeltaDominance = calculateAverageDeltaDominance(newPoint, r);
                    double probability = 1.0 / (1.0 + Math.exp(averageDeltaDominance * temperature));

                    if (PRNG.nextDouble() < probability) {
                        currentPoint = newPoint;
                    }
                } else if (dominationAmount.dominatedAmount == 0 && dominationAmount.dominatesAmount == 0) {
                    currentPoint = newPoint;
                    archive.add(currentPoint);

                    if (archive.size() > sl) {
                        Clustering.singleLinkage().truncate(hl, archive);
                    }
                } else if (dominationAmount.dominatesAmount > 0) {
                    currentPoint = newPoint;
                    archive.add(currentPoint);
                }
            } else {
                DominationAmount dominationAmount = calculateDominationAmounts(newPoint);

                if (dominationAmount.dominatedAmount > 0) {
                    MinimumDeltaDominance minDD = calculateMinimumDeltaDominance(newPoint, r);
                    double probability = 1.0 / (1.0 + Math.exp(-1.0 * minDD.minimumDeltaDominance));

                    if (PRNG.nextDouble() < probability) {
                        currentPoint = archive.get(minDD.minimumIndex);
                    } else {
                        currentPoint = newPoint;
                    }
                } else if (dominationAmount.dominatedAmount == 0 && dominationAmount.dominatesAmount == 0) {
                    currentPoint = newPoint;

                    if (!archive.add(currentPoint)) {
                        archive.remove(currentPoint);
                    } else if (archive.size() > sl) {
                        Clustering.singleLinkage().truncate(hl, archive);
                    }
                } else if (dominationAmount.dominatesAmount > 0) {
                    currentPoint = newPoint;
                    archive.add(currentPoint);
                }
            }
        }
    }

    // ==================== Helper methods (mirror FixedAMOSA) ====================

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

    private double calculateDeltaDominance(Solution solutionA, Solution solutionB, double[] r) {
        double deltaDominance = 1.0;
        int effectiveObjectives = 0;

        for (int i = 0; i < solutionA.getNumberOfObjectives(); i++) {
            if (r[i] > 1e-10) {
                deltaDominance *= Math.abs(solutionA.getObjective(i) - solutionB.getObjective(i)) / r[i];
                effectiveObjectives++;
            }
        }

        if (effectiveObjectives > 1) {
            deltaDominance = Math.pow(deltaDominance, 1.0 / effectiveObjectives);
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
