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
 * Task assignment strategy using Generational Genetic Algorithm with Elitism.
 *
 * This strategy optimizes task-to-VM assignment using evolutionary optimization,
 * returning the single best solution found.
 *
 * Key features:
 * - Single objective (makespan or energy) or weighted-sum multi-objective
 * - Configurable elitism (absolute or percentage)
 * - Tournament selection with configurable size
 * - Uses simulator's RandomGenerator for reproducibility
 * - Comprehensive statistics output
 *
 * Usage:
 * <pre>
 * // Single objective optimization (minimize makespan)
 * GAConfiguration config = GAConfiguration.builder()
 *     .populationSize(100)
 *     .crossoverRate(0.9)
 *     .mutationRate(0.1)
 *     .elitePercentage(0.1)
 *     .tournamentSize(3)
 *     .objective(new MakespanObjective())
 *     .terminationCondition(new GenerationCountTermination(200))
 *     .verboseLogging(true)
 *     .build();
 *
 * GenerationalGATaskSchedulingStrategy strategy = new GenerationalGATaskSchedulingStrategy(config);
 *
 * // Run optimization
 * SchedulingSolution best = strategy.optimize(tasks, vms);
 *
 * // Or use as TaskAssignmentStrategy
 * Map<Task, VM> assignments = strategy.assignAll(tasks, vms, currentTime);
 *
 * // Weighted-sum multi-objective optimization
 * GAConfiguration multiConfig = GAConfiguration.builder()
 *     .populationSize(100)
 *     .eliteCount(10)
 *     .addWeightedObjective(new MakespanObjective(), 0.7)
 *     .addWeightedObjective(new EnergyObjective(), 0.3)
 *     .terminationCondition(new GenerationCountTermination(200))
 *     .build();
 *
 * GenerationalGATaskSchedulingStrategy multiStrategy =
 *     new GenerationalGATaskSchedulingStrategy(multiConfig);
 * </pre>
 */
public class GenerationalGATaskSchedulingStrategy implements TaskAssignmentStrategy {

    private final GAConfiguration config;

    // Cached results from last optimization
    private SchedulingSolution lastSolution;
    private GAStatistics lastStatistics;

    /**
     * Creates a Generational GA task scheduling strategy with the given configuration.
     *
     * @param config GA configuration
     */
    public GenerationalGATaskSchedulingStrategy(GAConfiguration config) {
        this.config = config;
    }

    /**
     * Runs GA optimization and returns the best solution.
     *
     * @param tasks Tasks to schedule
     * @param vms   VMs to assign tasks to
     * @return Best solution found, or null if optimization failed
     */
    public SchedulingSolution optimize(List<Task> tasks, List<VM> vms) {
        GenerationalGAAlgorithm algorithm = new GenerationalGAAlgorithm(config, tasks, vms);
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
    public GAStatistics getLastStatistics() {
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
            System.err.println("[GA Strategy] Optimization returned null solution");
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
        return "Generational GA";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Generational Genetic Algorithm with Elitism. ");

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

        sb.append(". Elitism: ");
        if (config.getElitismType() == GAConfiguration.ElitismType.PERCENTAGE) {
            sb.append(String.format("%.0f%%", config.getElitePercentage() * 100));
        } else {
            sb.append(config.getEliteCount());
        }

        sb.append(", Tournament size: ").append(config.getTournamentSize());

        return sb.toString();
    }

    @Override
    public boolean isBatchOptimizing() {
        return true;
    }

    /**
     * Gets the GA configuration.
     *
     * @return Configuration
     */
    public GAConfiguration getConfiguration() {
        return config;
    }

    /**
     * Sets the output format for statistics logging.
     *
     * @param format Output format
     * @return this for method chaining
     */
    public GenerationalGATaskSchedulingStrategy setOutputFormat(GAStatistics.OutputFormat format) {
        // This will be applied when the algorithm runs
        // For now, we can't set it directly on lastStatistics since it may be null
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
