package com.cloudsimulator.engine;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.model.SimulationSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated results from simulating all solutions in a Pareto front.
 *
 * When using multi-objective optimization (e.g., NSGA-II), each solution
 * in the Pareto front represents a different trade-off between objectives.
 * This class holds the simulation results for ALL solutions, enabling
 * comprehensive analysis of the trade-off surface.
 *
 * Usage:
 * <pre>
 * MultiObjectiveSimulationResult moResult = engine.runMultiObjectiveSimulation();
 *
 * // Access individual solution results
 * for (SolutionSimulationResult result : moResult.getSolutionResults()) {
 *     System.out.println("Solution " + result.getSolutionIndex());
 *     System.out.println("  Predicted makespan: " + result.getPredictedObjectives()[0]);
 *     System.out.println("  Simulated makespan: " + result.getSimulatedMakespan());
 * }
 *
 * // Get best solution by simulated metric
 * SolutionSimulationResult bestMakespan = moResult.getBestBySimulatedMakespan();
 * SolutionSimulationResult bestEnergy = moResult.getBestBySimulatedEnergy();
 * </pre>
 */
public class MultiObjectiveSimulationResult {

    private final ParetoFront paretoFront;
    private final List<SolutionSimulationResult> solutionResults;
    private final List<String> objectiveNames;
    private final long totalSimulationTimeMs;

    /**
     * Creates a new multi-objective simulation result.
     *
     * @param paretoFront The Pareto front that was simulated
     * @param objectiveNames Names of the objectives
     */
    public MultiObjectiveSimulationResult(ParetoFront paretoFront, List<String> objectiveNames) {
        this.paretoFront = paretoFront;
        this.objectiveNames = new ArrayList<>(objectiveNames);
        this.solutionResults = new ArrayList<>();
        this.totalSimulationTimeMs = 0;
    }

    /**
     * Creates a new multi-objective simulation result with timing.
     *
     * @param paretoFront The Pareto front that was simulated
     * @param objectiveNames Names of the objectives
     * @param totalSimulationTimeMs Total time to simulate all solutions
     */
    public MultiObjectiveSimulationResult(ParetoFront paretoFront, List<String> objectiveNames,
                                          long totalSimulationTimeMs) {
        this.paretoFront = paretoFront;
        this.objectiveNames = new ArrayList<>(objectiveNames);
        this.solutionResults = new ArrayList<>();
        this.totalSimulationTimeMs = totalSimulationTimeMs;
    }

    /**
     * Adds a solution simulation result.
     *
     * @param result The result to add
     */
    public void addSolutionResult(SolutionSimulationResult result) {
        solutionResults.add(result);
    }

    /**
     * Gets all solution simulation results.
     *
     * @return Unmodifiable list of results
     */
    public List<SolutionSimulationResult> getSolutionResults() {
        return Collections.unmodifiableList(solutionResults);
    }

    /**
     * Gets the number of solutions simulated.
     *
     * @return Number of solutions
     */
    public int getNumSolutions() {
        return solutionResults.size();
    }

    /**
     * Gets the original Pareto front.
     *
     * @return Pareto front
     */
    public ParetoFront getParetoFront() {
        return paretoFront;
    }

    /**
     * Gets the objective names.
     *
     * @return List of objective names
     */
    public List<String> getObjectiveNames() {
        return Collections.unmodifiableList(objectiveNames);
    }

    /**
     * Gets total simulation time in milliseconds.
     *
     * @return Total simulation time
     */
    public long getTotalSimulationTimeMs() {
        return totalSimulationTimeMs;
    }

    /**
     * Gets the solution with best simulated makespan.
     *
     * @return Best makespan solution, or null if no results
     */
    public SolutionSimulationResult getBestBySimulatedMakespan() {
        return solutionResults.stream()
            .min((a, b) -> Long.compare(a.getSimulatedMakespan(), b.getSimulatedMakespan()))
            .orElse(null);
    }

    /**
     * Gets the solution with best simulated energy consumption.
     *
     * @return Best energy solution, or null if no results
     */
    public SolutionSimulationResult getBestBySimulatedEnergy() {
        return solutionResults.stream()
            .min((a, b) -> Double.compare(a.getSimulatedEnergyKWh(), b.getSimulatedEnergyKWh()))
            .orElse(null);
    }

    /**
     * Gets the solution result at a specific index.
     *
     * @param index Solution index
     * @return Solution result
     */
    public SolutionSimulationResult getSolutionResult(int index) {
        return solutionResults.get(index);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MultiObjectiveSimulationResult{\n");
        sb.append("  paretoFrontSize=").append(paretoFront.size()).append(",\n");
        sb.append("  solutionsSimulated=").append(solutionResults.size()).append(",\n");
        sb.append("  totalTimeMs=").append(totalSimulationTimeMs).append(",\n");
        sb.append("  objectives=").append(objectiveNames).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Result from simulating a single solution from the Pareto front.
     */
    public static class SolutionSimulationResult {
        private final int solutionIndex;
        private final SchedulingSolution solution;
        private final double[] predictedObjectives;
        private final SimulationSummary simulationSummary;
        private final long simulatedMakespan;
        private final double simulatedEnergyKWh;
        private final double simulatedCarbonKg;
        private final long simulationTimeMs;

        /**
         * Creates a solution simulation result.
         *
         * @param solutionIndex Index in the Pareto front
         * @param solution The scheduling solution
         * @param predictedObjectives Objective values predicted by the algorithm
         * @param simulationSummary Full simulation summary
         * @param simulationTimeMs Time to simulate this solution
         */
        public SolutionSimulationResult(int solutionIndex, SchedulingSolution solution,
                                        double[] predictedObjectives,
                                        SimulationSummary simulationSummary,
                                        long simulationTimeMs) {
            this.solutionIndex = solutionIndex;
            this.solution = solution;
            this.predictedObjectives = predictedObjectives.clone();
            this.simulationSummary = simulationSummary;
            this.simulationTimeMs = simulationTimeMs;

            // Extract key metrics from summary
            if (simulationSummary != null && simulationSummary.getPerformance() != null) {
                this.simulatedMakespan = simulationSummary.getPerformance().makespanSeconds;
            } else {
                this.simulatedMakespan = 0;
            }

            if (simulationSummary != null && simulationSummary.getEnergy() != null) {
                this.simulatedEnergyKWh = simulationSummary.getEnergy().totalITEnergyKWh;
                this.simulatedCarbonKg = simulationSummary.getEnergy().carbonFootprintKg;
            } else {
                this.simulatedEnergyKWh = 0.0;
                this.simulatedCarbonKg = 0.0;
            }
        }

        public int getSolutionIndex() {
            return solutionIndex;
        }

        public SchedulingSolution getSolution() {
            return solution;
        }

        public double[] getPredictedObjectives() {
            return predictedObjectives.clone();
        }

        public double getPredictedObjective(int index) {
            return predictedObjectives[index];
        }

        public SimulationSummary getSimulationSummary() {
            return simulationSummary;
        }

        public long getSimulatedMakespan() {
            return simulatedMakespan;
        }

        public double getSimulatedEnergyKWh() {
            return simulatedEnergyKWh;
        }

        public double getSimulatedCarbonKg() {
            return simulatedCarbonKg;
        }

        public long getSimulationTimeMs() {
            return simulationTimeMs;
        }

        /**
         * Gets the discrepancy between predicted and simulated makespan.
         * Positive value means algorithm over-estimated.
         *
         * @return Makespan discrepancy in seconds
         */
        public long getMakespanDiscrepancy() {
            if (predictedObjectives.length > 0) {
                return (long) predictedObjectives[0] - simulatedMakespan;
            }
            return 0;
        }

        /**
         * Gets the discrepancy between predicted and simulated energy.
         * Positive value means algorithm over-estimated.
         *
         * @return Energy discrepancy in kWh
         */
        public double getEnergyDiscrepancy() {
            if (predictedObjectives.length > 1) {
                return predictedObjectives[1] - simulatedEnergyKWh;
            }
            return 0.0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("SolutionResult[").append(solutionIndex).append("]{");
            sb.append("predicted=[");
            for (int i = 0; i < predictedObjectives.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format("%.2f", predictedObjectives[i]));
            }
            sb.append("], ");
            sb.append("simMakespan=").append(simulatedMakespan).append("s, ");
            sb.append("simEnergy=").append(String.format("%.4f", simulatedEnergyKWh)).append("kWh");
            sb.append("}");
            return sb.toString();
        }
    }
}
