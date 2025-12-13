package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;
import com.cloudsimulator.utils.SimulationLogger;

import org.moeaframework.core.Algorithm;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.PRNG;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract base class for MOEA Framework-based task scheduling strategies.
 *
 * Provides common functionality:
 * - Seed propagation for experiment reproducibility
 * - Solution conversion and selection
 * - Logging and statistics
 * - Caching of last optimization results
 *
 * Subclasses implement the createAlgorithm() method to instantiate their
 * specific MOEA Framework algorithm (NSGA-II, SPEA2, ε-MOEA, AMOSA).
 */
public abstract class AbstractMOEAStrategy implements TaskAssignmentStrategy {

    protected final MOEAConfiguration config;

    // Cached results from last optimization
    protected ParetoFront lastParetoFront;
    protected SchedulingSolution lastSelectedSolution;
    protected long lastOptimizationTimeMs;

    /**
     * Creates a new strategy with the given configuration.
     *
     * @param config Configuration parameters
     */
    protected AbstractMOEAStrategy(MOEAConfiguration config) {
        this.config = config;
    }

    /**
     * Creates the MOEA Framework algorithm instance for the given problem.
     * Subclasses implement this to provide their specific algorithm.
     *
     * @param problem The task scheduling problem
     * @return Configured MOEA Framework algorithm
     */
    protected abstract Algorithm createAlgorithm(TaskSchedulingProblem problem);

    /**
     * Gets the algorithm name for logging and reporting.
     *
     * @return Algorithm name (e.g., "MOEA_NSGAII")
     */
    protected abstract String getAlgorithmName();

    @Override
    public boolean isBatchOptimizing() {
        return true;
    }

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        // Single-task selection not supported for batch optimizers
        // Return first compatible VM as fallback
        return candidateVMs.isEmpty() ? Optional.empty() : Optional.of(candidateVMs.get(0));
    }

    @Override
    public Map<Task, VM> assignAll(List<Task> tasks, List<VM> vms, long currentTime) {
        Map<Task, VM> assignments = new LinkedHashMap<>();

        if (tasks.isEmpty() || vms.isEmpty()) {
            return assignments;
        }

        // Set MOEA Framework's RNG seed for reproducibility
        // CRITICAL: This ensures experiments are repeatable with the same simulator seed
        long seed = RandomGenerator.getInstance().getSeed();
        PRNG.setSeed(seed);

        if (config.isVerboseLogging()) {
            SimulationLogger.info(getAlgorithmName() +
                ": Starting optimization with seed=" + seed +
                ", tasks=" + tasks.size() +
                ", vms=" + vms.size());
        }

        // Create the problem adapter
        TaskSchedulingProblem problem = new TaskSchedulingProblem(
            tasks, vms, config.getObjectives());

        if (!problem.isFeasible()) {
            SimulationLogger.warn(getAlgorithmName() +
                ": Problem is infeasible - some tasks have no valid VMs");
        }

        // Create and run the algorithm
        long startTime = System.currentTimeMillis();
        Algorithm algorithm = createAlgorithm(problem);

        try {
            // Run optimization
            int evaluations = 0;
            int lastLoggedEvaluation = 0;

            while (!algorithm.isTerminated() && evaluations < config.getMaxEvaluations()) {
                algorithm.step();
                evaluations = algorithm.getNumberOfEvaluations();

                // Periodic logging
                if (config.isVerboseLogging() &&
                    evaluations - lastLoggedEvaluation >= config.getLogInterval()) {
                    logProgress(algorithm, evaluations);
                    lastLoggedEvaluation = evaluations;
                }
            }

            lastOptimizationTimeMs = System.currentTimeMillis() - startTime;

            // Get results
            NondominatedPopulation result = algorithm.getResult();

            // Convert to our ParetoFront
            SolutionConverter converter = new SolutionConverter(problem);
            lastParetoFront = converter.convertToParetoFront(result);

            if (config.isVerboseLogging()) {
                SimulationLogger.info(getAlgorithmName() +
                    ": Optimization complete in " + lastOptimizationTimeMs + "ms" +
                    ", evaluations=" + evaluations +
                    ", paretoFrontSize=" + lastParetoFront.size());
                SimulationLogger.info(converter.getPopulationStatistics(result));
            }

            // Select solution from Pareto front
            lastSelectedSolution = converter.selectSolution(
                lastParetoFront,
                config.getSolutionSelection(),
                config.getSelectionWeights());

            if (lastSelectedSolution == null) {
                SimulationLogger.warn(getAlgorithmName() +
                    ": No feasible solution found");
                return assignments;
            }

            // Convert solution to task-VM assignments
            assignments = applyAssignments(lastSelectedSolution, tasks, vms, currentTime);

            if (config.isVerboseLogging()) {
                logSelectedSolution(lastSelectedSolution);
            }

        } finally {
            algorithm.terminate();
        }

        return assignments;
    }

    /**
     * Applies the scheduling solution by assigning tasks to VMs.
     */
    protected Map<Task, VM> applyAssignments(SchedulingSolution solution,
                                              List<Task> tasks,
                                              List<VM> vms,
                                              long currentTime) {
        Map<Task, VM> assignments = new LinkedHashMap<>();
        int[] taskAssignment = solution.getTaskAssignment();

        for (int taskIdx = 0; taskIdx < tasks.size(); taskIdx++) {
            int vmIdx = taskAssignment[taskIdx];
            if (vmIdx >= 0 && vmIdx < vms.size()) {
                Task task = tasks.get(taskIdx);
                VM vm = vms.get(vmIdx);

                // Verify assignment is valid
                if (vm.getUserId().equals(task.getUserId()) && vm.canAcceptTask(task)) {
                    task.assignToVM(vm.getId(), currentTime);
                    vm.assignTask(task);
                    assignments.put(task, vm);
                }
            }
        }

        return assignments;
    }

    /**
     * Logs optimization progress.
     */
    protected void logProgress(Algorithm algorithm, int evaluations) {
        NondominatedPopulation result = algorithm.getResult();
        int frontSize = result.size();
        int feasibleCount = 0;

        for (var sol : result) {
            if (sol.isFeasible()) feasibleCount++;
        }

        SimulationLogger.debug(String.format(
            "%s: Evaluations=%d, FrontSize=%d, Feasible=%d",
            getAlgorithmName(), evaluations, frontSize, feasibleCount));
    }

    /**
     * Logs the selected solution details.
     */
    protected void logSelectedSolution(SchedulingSolution solution) {
        StringBuilder sb = new StringBuilder();
        sb.append(getAlgorithmName()).append(": Selected solution - ");

        double[] objectives = solution.getObjectiveValues();
        List<? extends com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective> configObjectives =
            config.getObjectives();

        for (int i = 0; i < objectives.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(configObjectives.get(i).getName())
              .append("=")
              .append(String.format("%.4f", objectives[i]))
              .append(" ")
              .append(configObjectives.get(i).getUnit());
        }

        SimulationLogger.info(sb.toString());
    }

    /**
     * Gets the Pareto front from the last optimization.
     *
     * @return Last Pareto front, or null if no optimization has run
     */
    public ParetoFront getLastParetoFront() {
        return lastParetoFront;
    }

    /**
     * Gets the selected solution from the last optimization.
     *
     * @return Last selected solution, or null if no optimization has run
     */
    public SchedulingSolution getLastSelectedSolution() {
        return lastSelectedSolution;
    }

    /**
     * Gets the optimization time from the last run.
     *
     * @return Optimization time in milliseconds
     */
    public long getLastOptimizationTimeMs() {
        return lastOptimizationTimeMs;
    }

    /**
     * Gets the configuration.
     *
     * @return The MOEAConfiguration
     */
    public MOEAConfiguration getConfiguration() {
        return config;
    }

    @Override
    public String getDescription() {
        return String.format("%s multi-objective optimizer with %s solution selection",
            getAlgorithmName(), config.getSolutionSelection());
    }
}
