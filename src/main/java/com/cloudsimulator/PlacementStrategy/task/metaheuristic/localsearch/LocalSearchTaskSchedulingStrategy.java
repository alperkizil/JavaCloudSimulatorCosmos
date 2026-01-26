package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch;

import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Task assignment strategy using Local Search.
 *
 * This strategy optimizes task-to-VM assignment using the Local Search metaheuristic,
 * finding a local optimum from a random (or provided) starting solution.
 *
 * Key features:
 * - Swappable neighbor selection strategies (Best Improvement, First Improvement, Random)
 * - Single objective (makespan or energy) or weighted-sum multi-objective
 * - Can accept external initial solution for hybrid algorithms
 * - Comprehensive statistics output
 *
 * Usage:
 * <pre>
 * // Best improvement with combined neighborhood
 * LocalSearchConfiguration config = LocalSearchConfiguration.builder()
 *     .neighborSelectionStrategy(new BestImprovementStrategy())
 *     .neighborhoodType(NeighborhoodGenerator.NeighborhoodType.COMBINED)
 *     .objective(new MakespanObjective())
 *     .maxIterationsWithoutImprovement(50)
 *     .verboseLogging(true)
 *     .build();
 *
 * LocalSearchTaskSchedulingStrategy strategy =
 *     new LocalSearchTaskSchedulingStrategy(config);
 *
 * // Run optimization
 * SchedulingSolution best = strategy.optimize(tasks, vms);
 *
 * // Or use as TaskAssignmentStrategy
 * Map&lt;Task, VM&gt; assignments = strategy.assignAll(tasks, vms, currentTime);
 *
 * // First improvement for faster execution
 * LocalSearchConfiguration fastConfig = LocalSearchConfiguration.builder()
 *     .neighborSelectionStrategy(new FirstImprovementStrategy())
 *     .neighborhoodType(NeighborhoodGenerator.NeighborhoodType.REASSIGN)
 *     .objective(new MakespanObjective())
 *     .build();
 *
 * // Weighted-sum multi-objective optimization
 * LocalSearchConfiguration multiConfig = LocalSearchConfiguration.builder()
 *     .neighborSelectionStrategy(new RandomSelectionStrategy())
 *     .addWeightedObjective(new MakespanObjective(), 0.7)
 *     .addWeightedObjective(new EnergyObjective(), 0.3)
 *     .build();
 * </pre>
 *
 * Reference: El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 */
public class LocalSearchTaskSchedulingStrategy implements TaskAssignmentStrategy {

    private final LocalSearchConfiguration config;

    // Cached results from last optimization
    private SchedulingSolution lastSolution;
    private LocalSearchStatistics lastStatistics;

    // Optional initial solution for hybrid algorithms
    private SchedulingSolution initialSolution;

    /**
     * Creates a Local Search task scheduling strategy with the given configuration.
     *
     * @param config Local Search configuration
     */
    public LocalSearchTaskSchedulingStrategy(LocalSearchConfiguration config) {
        this.config = config;
    }

    /**
     * Sets an initial solution to start the local search from.
     * Use this for hybrid algorithms that want to improve an existing solution.
     *
     * @param initialSolution Solution to start from
     * @return this for method chaining
     */
    public LocalSearchTaskSchedulingStrategy withInitialSolution(SchedulingSolution initialSolution) {
        this.initialSolution = initialSolution;
        return this;
    }

    /**
     * Runs Local Search optimization and returns the best solution (local optimum).
     *
     * @param tasks Tasks to schedule
     * @param vms   VMs to assign tasks to
     * @return Best solution found (local optimum), or null if optimization failed
     */
    public SchedulingSolution optimize(List<Task> tasks, List<VM> vms) {
        LocalSearchAlgorithm algorithm = new LocalSearchAlgorithm(config, tasks, vms);

        // Set initial solution if provided
        if (initialSolution != null) {
            algorithm.setInitialSolution(initialSolution);
        }

        lastSolution = algorithm.run();
        lastStatistics = algorithm.getStatistics();
        return lastSolution;
    }

    /**
     * Gets the solution from the last optimization run.
     *
     * @return Last solution, or null if optimize() hasn't been called
     */
    public SchedulingSolution getLastSolution() {
        return lastSolution;
    }

    /**
     * Gets the statistics from the last optimization run.
     *
     * @return Last statistics, or null if optimize() hasn't been called
     */
    public LocalSearchStatistics getLastStatistics() {
        return lastStatistics;
    }

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        // For single-task selection, just return the first candidate
        // This strategy is designed for batch optimization
        if (candidateVMs == null || candidateVMs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidateVMs.get(0));
    }

    @Override
    public Map<Task, VM> assignAll(List<Task> tasks, List<VM> vms, long currentTime) {
        Map<Task, VM> assignments = new LinkedHashMap<>();

        if (tasks.isEmpty() || vms.isEmpty()) {
            return assignments;
        }

        // Build lookup maps
        Map<Integer, Task> taskByIndex = new LinkedHashMap<>();
        Map<Integer, VM> vmByIndex = new LinkedHashMap<>();

        List<Task> taskList = new ArrayList<>(tasks);
        List<VM> vmList = new ArrayList<>(vms);

        for (int i = 0; i < taskList.size(); i++) {
            taskByIndex.put(i, taskList.get(i));
        }
        for (int i = 0; i < vmList.size(); i++) {
            vmByIndex.put(i, vmList.get(i));
        }

        // Run optimization
        SchedulingSolution solution = optimize(taskList, vmList);

        if (solution == null) {
            System.err.println("[LS Strategy] Optimization returned null solution");
            return assignments;
        }

        // Apply the solution
        int[] taskAssignment = solution.getTaskAssignment();
        List<List<Integer>> vmTaskOrder = solution.getVmTaskOrder();

        // Assign tasks according to the solution, respecting task order within VMs
        for (int vmIdx = 0; vmIdx < vmTaskOrder.size(); vmIdx++) {
            VM vm = vmByIndex.get(vmIdx);
            if (vm == null) continue;

            List<Integer> taskOrder = vmTaskOrder.get(vmIdx);

            for (int taskIdx : taskOrder) {
                Task task = taskByIndex.get(taskIdx);
                if (task == null) continue;

                // Verify assignment matches
                if (taskAssignment[taskIdx] != vmIdx) {
                    continue; // Skip inconsistent assignments (shouldn't happen)
                }

                // Assign task to VM
                task.assignToVM(vm.getId(), currentTime);
                vm.assignTask(task);
                assignments.put(task, vm);
            }
        }

        return assignments;
    }

    @Override
    public String getStrategyName() {
        return "Local Search";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Local Search with ");
        sb.append(config.getNeighborSelectionStrategy().getName());
        sb.append(" and ");
        sb.append(config.getNeighborhoodType().name()).append(" neighborhood. ");

        if (config.isWeightedSum()) {
            sb.append("Weighted-sum optimization with objectives: ");
            for (int i = 0; i < config.getObjectives().size(); i++) {
                if (i > 0) sb.append(", ");
                SchedulingObjective obj = config.getObjectives().get(i);
                sb.append(obj.getName());
                sb.append(":");
                sb.append(String.format("%.2f", config.getObjectiveWeight(obj)));
            }
        } else {
            sb.append("Single objective: ");
            sb.append(config.getPrimaryObjective().getName());
        }

        sb.append(String.format(". Max iterations: %d, max no-improvement: %d",
            config.getMaxIterations(), config.getMaxIterationsWithoutImprovement()));

        return sb.toString();
    }

    @Override
    public boolean isBatchOptimizing() {
        return true;
    }

    /**
     * Gets the Local Search configuration.
     *
     * @return Configuration
     */
    public LocalSearchConfiguration getConfiguration() {
        return config;
    }

    /**
     * Sets the output format for statistics logging.
     *
     * @param format Output format
     * @return this for method chaining
     */
    public LocalSearchTaskSchedulingStrategy setOutputFormat(LocalSearchStatistics.OutputFormat format) {
        // This will be applied when the algorithm runs
        return this;
    }

    /**
     * Prints a summary of the last optimization run.
     */
    public void printLastRunSummary() {
        if (lastStatistics != null) {
            System.out.println(lastStatistics);
        } else {
            System.out.println("No optimization has been run yet.");
        }
    }

    /**
     * Clears the initial solution.
     * Call this between runs if you want to start fresh.
     */
    public void clearInitialSolution() {
        this.initialSolution = null;
    }
}
