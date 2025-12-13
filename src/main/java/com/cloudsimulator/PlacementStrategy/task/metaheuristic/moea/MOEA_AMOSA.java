package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;
import com.cloudsimulator.utils.SimulationLogger;

import org.moeaframework.core.Algorithm;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.EncodingUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MOEA Framework-compatible AMOSA implementation for task scheduling.
 *
 * AMOSA (Archived Multi-Objective Simulated Annealing) combines simulated
 * annealing with multi-objective optimization:
 * - Archive maintenance: Stores non-dominated solutions found during search
 * - Temperature schedule: Controls acceptance probability of worse solutions
 * - Multi-objective acceptance: Uses dominance relations for acceptance
 * - Clustering: Maintains diversity in the archive
 *
 * Key parameters:
 * - Initial temperature: Higher = more exploration initially
 * - Final temperature: Algorithm stops when reached
 * - Cooling rate: Temperature decrease factor (e.g., 0.9 = 10% reduction)
 * - Iterations per temperature: More = thorough search at each level
 *
 * Reference: Bandyopadhyay et al. (2008) "A Simulated Annealing-Based
 * Multiobjective Optimization Algorithm: AMOSA"
 *
 * Note: Since AMOSA is not included in standard MOEA Framework, this
 * implementation provides a custom AMOSA that integrates with our
 * simulator's RandomGenerator for experiment reproducibility.
 */
public class MOEA_AMOSA extends AbstractMOEAStrategy {

    /**
     * Creates a new MOEA_AMOSA strategy with the given configuration.
     *
     * @param config Configuration parameters
     */
    public MOEA_AMOSA(MOEAConfiguration config) {
        super(config);
    }

    /**
     * Creates a MOEA_AMOSA strategy with default configuration.
     */
    public MOEA_AMOSA() {
        super(MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.AMOSA)
            .initialTemperature(1000.0)
            .finalTemperature(0.01)
            .coolingRate(0.9)
            .archiveSize(100)
            .iterationsPerTemperature(100)
            .build());
    }

    @Override
    protected String getAlgorithmName() {
        return "MOEA_AMOSA";
    }

    @Override
    public String getStrategyName() {
        return "MOEA_AMOSA";
    }

    @Override
    protected Algorithm createAlgorithm(TaskSchedulingProblem problem) {
        // Return custom AMOSA implementation
        return new AMOSAAlgorithm(problem, config);
    }

    @Override
    public String getDescription() {
        return String.format(
            "MOEA Framework AMOSA: Archived Multi-Objective Simulated Annealing. " +
            "InitialTemp=%.1f, FinalTemp=%.4f, CoolingRate=%.2f, " +
            "ArchiveSize=%d, IterationsPerTemp=%d, Selection=%s",
            config.getInitialTemperature(),
            config.getFinalTemperature(),
            config.getCoolingRate(),
            config.getArchiveSize(),
            config.getIterationsPerTemperature(),
            config.getSolutionSelection());
    }

    /**
     * Custom AMOSA algorithm implementation compatible with MOEA Framework interface.
     */
    private static class AMOSAAlgorithm implements Algorithm {

        private final TaskSchedulingProblem problem;
        private final MOEAConfiguration config;
        private final NondominatedPopulation archive;
        private final RandomGenerator rng;

        private Solution currentSolution;
        private double temperature;
        private int evaluations;
        private boolean terminated;

        public AMOSAAlgorithm(TaskSchedulingProblem problem, MOEAConfiguration config) {
            this.problem = problem;
            this.config = config;
            this.archive = new NondominatedPopulation();
            this.rng = RandomGenerator.getInstance();
            this.temperature = config.getInitialTemperature();
            this.evaluations = 0;
            this.terminated = false;

            // Initialize with a random solution
            this.currentSolution = problem.newSolution();
            problem.evaluate(currentSolution);
            evaluations++;

            // Add to archive if feasible
            if (currentSolution.isFeasible()) {
                archive.add(currentSolution.copy());
            }
        }

        @Override
        public void step() {
            if (terminated) return;

            // Perform iterations at current temperature
            for (int iter = 0; iter < config.getIterationsPerTemperature(); iter++) {
                if (evaluations >= config.getMaxEvaluations()) {
                    terminated = true;
                    return;
                }

                // Generate neighbor solution
                Solution neighbor = generateNeighbor(currentSolution);
                problem.evaluate(neighbor);
                evaluations++;

                // Decide acceptance
                if (acceptNeighbor(currentSolution, neighbor)) {
                    currentSolution = neighbor;
                }

                // Update archive with current solution if feasible
                if (currentSolution.isFeasible()) {
                    addToArchive(currentSolution);
                }
            }

            // Cool down
            temperature *= config.getCoolingRate();

            // Check termination
            if (temperature < config.getFinalTemperature()) {
                terminated = true;
            }
        }

        /**
         * Generates a neighbor solution by perturbing the current solution.
         */
        private Solution generateNeighbor(Solution current) {
            Solution neighbor = current.copy();

            // Randomly select a task to reassign
            int taskIdx = rng.nextInt(problem.getNumberOfVariables());
            List<Integer> validVMs = problem.getValidVMsForTask(taskIdx);

            if (!validVMs.isEmpty()) {
                // Reassign to a different valid VM
                int currentVM = EncodingUtils.getInt(neighbor.getVariable(taskIdx));
                int newVM;

                if (validVMs.size() == 1) {
                    newVM = validVMs.get(0);
                } else {
                    // Select a different VM
                    do {
                        newVM = validVMs.get(rng.nextInt(validVMs.size()));
                    } while (newVM == currentVM && validVMs.size() > 1);
                }

                EncodingUtils.setInt(neighbor.getVariable(taskIdx), newVM);
            }

            return neighbor;
        }

        /**
         * Decides whether to accept the neighbor solution.
         * Uses AMOSA acceptance criteria based on dominance.
         */
        private boolean acceptNeighbor(Solution current, Solution neighbor) {
            // If neighbor is infeasible, reject
            if (!neighbor.isFeasible()) {
                return false;
            }

            // Check dominance relationship
            int dominance = compareDominance(current, neighbor);

            if (dominance > 0) {
                // Neighbor dominates current - always accept
                return true;
            } else if (dominance == 0) {
                // Non-dominated - accept with probability based on archive
                return acceptNonDominated(current, neighbor);
            } else {
                // Current dominates neighbor - accept with SA probability
                return acceptWorse(current, neighbor);
            }
        }

        /**
         * Compares two solutions for dominance.
         * @return >0 if b dominates a, <0 if a dominates b, 0 if non-dominated
         */
        private int compareDominance(Solution a, Solution b) {
            boolean aDominates = true;
            boolean bDominates = true;

            for (int i = 0; i < a.getNumberOfObjectives(); i++) {
                double aVal = a.getObjective(i);
                double bVal = b.getObjective(i);

                if (aVal < bVal) {
                    bDominates = false;
                } else if (bVal < aVal) {
                    aDominates = false;
                }
            }

            if (bDominates && !aDominates) return 1;
            if (aDominates && !bDominates) return -1;
            return 0;
        }

        /**
         * Acceptance for non-dominated solutions.
         */
        private boolean acceptNonDominated(Solution current, Solution neighbor) {
            // Count how many archive members dominate each solution
            int currentDominated = countDominatedBy(current, archive);
            int neighborDominated = countDominatedBy(neighbor, archive);

            if (neighborDominated < currentDominated) {
                // Neighbor is less dominated by archive - accept
                return true;
            } else if (neighborDominated == currentDominated) {
                // Equal - accept with probability 0.5
                return rng.nextDouble() < 0.5;
            } else {
                // Neighbor is more dominated - use SA probability
                double deltaE = neighborDominated - currentDominated;
                double prob = Math.exp(-deltaE / temperature);
                return rng.nextDouble() < prob;
            }
        }

        /**
         * SA acceptance for worse solutions.
         */
        private boolean acceptWorse(Solution current, Solution neighbor) {
            // Calculate energy difference (aggregate objective difference)
            double deltaE = 0;
            for (int i = 0; i < current.getNumberOfObjectives(); i++) {
                deltaE += neighbor.getObjective(i) - current.getObjective(i);
            }

            if (deltaE <= 0) {
                return true; // Actually better in aggregate
            }

            // Metropolis acceptance
            double prob = Math.exp(-deltaE / temperature);
            return rng.nextDouble() < prob;
        }

        /**
         * Counts how many solutions in the population dominate the given solution.
         */
        private int countDominatedBy(Solution solution, NondominatedPopulation population) {
            int count = 0;
            for (Solution other : population) {
                if (compareDominance(solution, other) > 0) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Adds a solution to the archive, maintaining non-dominance.
         */
        private void addToArchive(Solution solution) {
            // Only add if feasible
            if (!solution.isFeasible()) return;

            archive.add(solution.copy());

            // Truncate archive if needed using clustering
            while (archive.size() > config.getArchiveSize()) {
                truncateArchive();
            }
        }

        /**
         * Truncates the archive by removing the most crowded solution.
         */
        private void truncateArchive() {
            if (archive.size() <= 1) return;

            // Find solution with minimum crowding distance
            Solution mostCrowded = null;
            double minDistance = Double.MAX_VALUE;

            for (Solution sol : archive) {
                double distance = calculateCrowdingDistance(sol, archive);
                if (distance < minDistance) {
                    minDistance = distance;
                    mostCrowded = sol;
                }
            }

            if (mostCrowded != null) {
                archive.remove(mostCrowded);
            }
        }

        /**
         * Calculates crowding distance for a solution in the archive.
         */
        private double calculateCrowdingDistance(Solution solution, NondominatedPopulation population) {
            double totalDistance = 0;

            for (Solution other : population) {
                if (other == solution) continue;

                double distance = 0;
                for (int i = 0; i < solution.getNumberOfObjectives(); i++) {
                    double diff = solution.getObjective(i) - other.getObjective(i);
                    distance += diff * diff;
                }
                totalDistance += Math.sqrt(distance);
            }

            return totalDistance / Math.max(1, population.size() - 1);
        }

        @Override
        public NondominatedPopulation getResult() {
            return archive;
        }

        @Override
        public int getNumberOfEvaluations() {
            return evaluations;
        }

        @Override
        public boolean isTerminated() {
            return terminated;
        }

        @Override
        public void terminate() {
            terminated = true;
        }

        @Override
        public org.moeaframework.core.Problem getProblem() {
            return problem;
        }
    }
}
