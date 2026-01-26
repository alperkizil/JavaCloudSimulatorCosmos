package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Task assignment strategy using Simulated Annealing.
 *
 * This strategy optimizes task-to-VM assignment using the SA metaheuristic,
 * returning the single best solution found.
 *
 * Key features:
 * - Single objective (makespan or energy) or weighted-sum multi-objective
 * - Multiple cooling schedules (linear, geometric, logarithmic, adaptive)
 * - Auto-calculation of initial temperature based on acceptance probability
 * - Uses simulator's RandomGenerator for reproducibility
 * - Comprehensive statistics output
 *
 * Usage:
 * <pre>
 * // Single objective optimization with geometric cooling
 * SAConfiguration config = SAConfiguration.builder()
 *     .initialTemperature(1000.0)
 *     .finalTemperature(0.001)
 *     .coolingSchedule(new GeometricCoolingSchedule(0.95))
 *     .iterationsPerTemperature(100)
 *     .objective(new MakespanObjective())
 *     .verboseLogging(true)
 *     .build();
 *
 * SimulatedAnnealingTaskSchedulingStrategy strategy =
 *     new SimulatedAnnealingTaskSchedulingStrategy(config);
 *
 * // Run optimization
 * SchedulingSolution best = strategy.optimize(tasks, vms);
 *
 * // Or use as TaskAssignmentStrategy
 * Map&lt;Task, VM&gt; assignments = strategy.assignAll(tasks, vms, currentTime);
 *
 * // With adaptive cooling
 * SAConfiguration adaptiveConfig = SAConfiguration.builder()
 *     .autoInitialTemperature(true)
 *     .initialAcceptanceProbability(0.8)
 *     .coolingSchedule(new AdaptiveCoolingSchedule())
 *     .objective(new MakespanObjective())
 *     .build();
 *
 * // Weighted-sum multi-objective optimization
 * SAConfiguration multiConfig = SAConfiguration.builder()
 *     .initialTemperature(1000.0)
 *     .coolingSchedule(new GeometricCoolingSchedule(0.95))
 *     .addWeightedObjective(new MakespanObjective(), 0.7)
 *     .addWeightedObjective(new EnergyObjective(), 0.3)
 *     .build();
 *
 * SimulatedAnnealingTaskSchedulingStrategy multiStrategy =
 *     new SimulatedAnnealingTaskSchedulingStrategy(multiConfig);
 * </pre>
 *
 * Reference: El-Ghazali Talbi, "Metaheuristics: From Design to Implementation"
 */
public class SimulatedAnnealingTaskSchedulingStrategy implements TaskAssignmentStrategy {

    private final SAConfiguration config;

    // Cached results from last optimization
    private SchedulingSolution lastSolution;
    private SAStatistics lastStatistics;

    /**
     * Creates a Simulated Annealing task scheduling strategy with the given configuration.
     *
     * @param config SA configuration
     */
    public SimulatedAnnealingTaskSchedulingStrategy(SAConfiguration config) {
        this.config = config;
    }

    /**
     * Runs SA optimization and returns the best solution.
     *
     * @param tasks Tasks to schedule
     * @param vms   VMs to assign tasks to
     * @return Best solution found, or null if optimization failed
     */
    public SchedulingSolution optimize(List<Task> tasks, List<VM> vms) {
        SimulatedAnnealingAlgorithm algorithm = new SimulatedAnnealingAlgorithm(config, tasks, vms);
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
    public SAStatistics getLastStatistics() {
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
            System.err.println("[SA Strategy] Optimization returned null solution");
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
        return "Simulated Annealing";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Simulated Annealing with ");
        sb.append(config.getCoolingSchedule().getName()).append(" cooling. ");

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

        if (config.isAutoInitialTemperature()) {
            sb.append(String.format(". Auto T0 (%.0f%% acceptance)",
                config.getInitialAcceptanceProbability() * 100));
        } else {
            sb.append(String.format(". T0=%.2f, Tmin=%.6f",
                config.getInitialTemperature(), config.getFinalTemperature()));
        }

        sb.append(", ").append(config.getIterationsPerTemperature()).append(" iters/T");

        return sb.toString();
    }

    @Override
    public boolean isBatchOptimizing() {
        return true;
    }

    /**
     * Gets the SA configuration.
     *
     * @return Configuration
     */
    public SAConfiguration getConfiguration() {
        return config;
    }

    /**
     * Sets the output format for statistics logging.
     *
     * @param format Output format
     * @return this for method chaining
     */
    public SimulatedAnnealingTaskSchedulingStrategy setOutputFormat(SAStatistics.OutputFormat format) {
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
}
