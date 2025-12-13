package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.Solution;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting between MOEA Framework solutions and
 * our simulator's solution representations.
 *
 * Handles:
 * - Converting NondominatedPopulation to ParetoFront
 * - Converting individual Solution to SchedulingSolution
 * - Solution selection from Pareto front
 */
public class SolutionConverter {

    private final TaskSchedulingProblem problem;

    /**
     * Creates a converter for the given problem.
     *
     * @param problem The task scheduling problem context
     */
    public SolutionConverter(TaskSchedulingProblem problem) {
        this.problem = problem;
    }

    /**
     * Converts MOEA Framework's NondominatedPopulation to our ParetoFront.
     *
     * @param population The MOEA Framework result
     * @return Our ParetoFront representation
     */
    public ParetoFront convertToParetoFront(NondominatedPopulation population) {
        List<SchedulingObjective> objectives = problem.getObjectives();

        // Build objective names and minimization flags
        List<String> objectiveNames = new ArrayList<>();
        boolean[] isMinimization = new boolean[objectives.size()];

        for (int i = 0; i < objectives.size(); i++) {
            objectiveNames.add(objectives.get(i).getName());
            isMinimization[i] = objectives.get(i).isMinimization();
        }

        // Convert solutions
        List<SchedulingSolution> solutions = new ArrayList<>();
        for (Solution moSolution : population) {
            // Skip infeasible solutions
            if (!moSolution.isFeasible()) {
                continue;
            }

            SchedulingSolution schedulingSolution = problem.convertToSchedulingSolution(moSolution);
            solutions.add(schedulingSolution);
        }

        return new ParetoFront(solutions, objectiveNames, isMinimization);
    }

    /**
     * Selects a single solution from the Pareto front based on the selection method.
     *
     * @param paretoFront     The Pareto front to select from
     * @param selectionMethod The selection method to use
     * @param weights         Weights for WEIGHTED_SUM selection (can be null for other methods)
     * @return The selected solution
     */
    public SchedulingSolution selectSolution(ParetoFront paretoFront,
                                              MOEAConfiguration.SolutionSelection selectionMethod,
                                              double[] weights) {
        if (paretoFront.isEmpty()) {
            return null;
        }

        switch (selectionMethod) {
            case KNEE_POINT:
                return paretoFront.getKneePoint();

            case BEST_FIRST_OBJECTIVE:
                return paretoFront.getBestForObjective(0);

            case BEST_SECOND_OBJECTIVE:
                return paretoFront.getBestForObjective(1);

            case WEIGHTED_SUM:
                if (weights == null) {
                    // Default to equal weights
                    int numObj = paretoFront.getObjectiveNames().size();
                    weights = new double[numObj];
                    for (int i = 0; i < numObj; i++) {
                        weights[i] = 1.0 / numObj;
                    }
                }
                return paretoFront.getByWeightedSum(weights);

            case FIRST:
            default:
                return paretoFront.getSolutions().get(0);
        }
    }

    /**
     * Finds the best feasible solution in the population.
     * If no feasible solution exists, returns the one with minimum constraint violation.
     *
     * @param population The MOEA Framework population
     * @return The best solution, or null if population is empty
     */
    public Solution getBestFeasibleSolution(NondominatedPopulation population) {
        Solution bestFeasible = null;
        Solution leastViolating = null;
        double minViolation = Double.MAX_VALUE;

        for (Solution solution : population) {
            if (solution.isFeasible()) {
                if (bestFeasible == null) {
                    bestFeasible = solution;
                }
                // Among feasible, any is acceptable (they're all non-dominated)
            } else {
                // Track least violating for fallback
                double violation = getTotalConstraintViolation(solution);
                if (violation < minViolation) {
                    minViolation = violation;
                    leastViolating = solution;
                }
            }
        }

        return bestFeasible != null ? bestFeasible : leastViolating;
    }

    /**
     * Calculates the total constraint violation for a solution.
     *
     * @param solution The MOEA Framework solution
     * @return Sum of all constraint violations
     */
    private double getTotalConstraintViolation(Solution solution) {
        double total = 0.0;
        for (int i = 0; i < solution.getNumberOfConstraints(); i++) {
            total += Math.abs(solution.getConstraint(i));
        }
        return total;
    }

    /**
     * Converts the entire population to a list of SchedulingSolutions.
     *
     * @param population  The MOEA Framework population
     * @param feasibleOnly If true, only includes feasible solutions
     * @return List of converted solutions
     */
    public List<SchedulingSolution> convertAll(NondominatedPopulation population, boolean feasibleOnly) {
        List<SchedulingSolution> result = new ArrayList<>();

        for (Solution moSolution : population) {
            if (feasibleOnly && !moSolution.isFeasible()) {
                continue;
            }
            result.add(problem.convertToSchedulingSolution(moSolution));
        }

        return result;
    }

    /**
     * Gets statistics about the population.
     *
     * @param population The MOEA Framework population
     * @return String describing population statistics
     */
    public String getPopulationStatistics(NondominatedPopulation population) {
        int total = population.size();
        int feasible = 0;
        double[] minObj = null;
        double[] maxObj = null;

        for (Solution solution : population) {
            if (solution.isFeasible()) {
                feasible++;
            }

            if (minObj == null) {
                minObj = new double[solution.getNumberOfObjectives()];
                maxObj = new double[solution.getNumberOfObjectives()];
                for (int i = 0; i < solution.getNumberOfObjectives(); i++) {
                    minObj[i] = Double.MAX_VALUE;
                    maxObj[i] = Double.MIN_VALUE;
                }
            }

            for (int i = 0; i < solution.getNumberOfObjectives(); i++) {
                double val = solution.getObjective(i);
                if (val < minObj[i]) minObj[i] = val;
                if (val > maxObj[i]) maxObj[i] = val;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Population: %d total, %d feasible (%.1f%%)\n",
            total, feasible, (total > 0 ? 100.0 * feasible / total : 0)));

        if (minObj != null) {
            List<SchedulingObjective> objectives = problem.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                sb.append(String.format("  %s: [%.4f - %.4f] %s\n",
                    objectives.get(i).getName(),
                    minObj[i], maxObj[i],
                    objectives.get(i).getUnit()));
            }
        }

        return sb.toString();
    }
}
