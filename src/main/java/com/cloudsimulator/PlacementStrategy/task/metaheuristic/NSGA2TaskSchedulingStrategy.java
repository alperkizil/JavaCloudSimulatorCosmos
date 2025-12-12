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
 * Task assignment strategy using NSGA-II multi-objective optimization.
 *
 * This strategy optimizes task-to-VM assignment and task ordering simultaneously,
 * returning the entire Pareto front of non-dominated solutions.
 *
 * The strategy is batch-optimizing: it considers all tasks together rather than
 * assigning them one at a time. This allows global optimization of the objectives.
 *
 * Usage:
 * <pre>
 * NSGA2Configuration config = NSGA2Configuration.builder()
 *     .populationSize(100)
 *     .addObjective(new MakespanObjective())
 *     .addObjective(new EnergyObjective())
 *     .terminationCondition(new GenerationCountTermination(200))
 *     .build();
 *
 * NSGA2TaskSchedulingStrategy strategy = new NSGA2TaskSchedulingStrategy(config);
 *
 * // Get entire Pareto front
 * ParetoFront front = strategy.optimize(tasks, vms);
 *
 * // Or use as a TaskAssignmentStrategy (uses knee point by default)
 * Map<Task, VM> assignments = strategy.assignAll(tasks, vms, currentTime);
 * </pre>
 */
public class NSGA2TaskSchedulingStrategy implements TaskAssignmentStrategy {

    /**
     * Selection method for choosing a single solution from the Pareto front
     * when using the TaskAssignmentStrategy interface.
     */
    public enum SolutionSelectionMethod {
        KNEE_POINT,       // Use knee point (balanced trade-off)
        BEST_MAKESPAN,    // Best makespan (objective 0)
        BEST_ENERGY,      // Best energy (objective 1)
        WEIGHTED_SUM,     // Weighted sum with specified weights
        FIRST             // First solution in front
    }

    private final NSGA2Configuration config;
    private SolutionSelectionMethod selectionMethod;
    private double[] selectionWeights;

    // Cached results from last optimization
    private ParetoFront lastParetoFront;
    private SchedulingSolution selectedSolution;

    /**
     * Creates a NSGA-II task scheduling strategy with the given configuration.
     *
     * @param config NSGA-II configuration
     */
    public NSGA2TaskSchedulingStrategy(NSGA2Configuration config) {
        this.config = config;
        this.selectionMethod = SolutionSelectionMethod.KNEE_POINT;
        this.selectionWeights = new double[]{0.5, 0.5}; // Default equal weights
    }

    /**
     * Sets the method for selecting a single solution from the Pareto front.
     *
     * @param method Selection method
     * @return this for method chaining
     */
    public NSGA2TaskSchedulingStrategy setSelectionMethod(SolutionSelectionMethod method) {
        this.selectionMethod = method;
        return this;
    }

    /**
     * Sets weights for weighted sum selection.
     *
     * @param weights Weights for each objective
     * @return this for method chaining
     */
    public NSGA2TaskSchedulingStrategy setSelectionWeights(double[] weights) {
        this.selectionWeights = weights.clone();
        return this;
    }

    /**
     * Runs NSGA-II optimization and returns the entire Pareto front.
     *
     * @param tasks Tasks to schedule
     * @param vms   VMs to assign tasks to
     * @return Pareto front containing all non-dominated solutions
     */
    public ParetoFront optimize(List<Task> tasks, List<VM> vms) {
        NSGA2Algorithm algorithm = new NSGA2Algorithm(config, tasks, vms);
        lastParetoFront = algorithm.run();
        return lastParetoFront;
    }

    /**
     * Gets the Pareto front from the last optimization run.
     *
     * @return Last Pareto front, or null if optimize() hasn't been called
     */
    public ParetoFront getLastParetoFront() {
        return lastParetoFront;
    }

    /**
     * Gets the selected solution from the last optimization.
     *
     * @return Selected solution, or null if assignAll() hasn't been called
     */
    public SchedulingSolution getSelectedSolution() {
        return selectedSolution;
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
        ParetoFront front = optimize(taskList, vmList);

        if (front.isEmpty()) {
            System.err.println("[NSGA-II Strategy] Optimization returned empty Pareto front");
            return assignments;
        }

        // Select a solution from the Pareto front
        selectedSolution = selectSolution(front);

        if (selectedSolution == null) {
            System.err.println("[NSGA-II Strategy] Could not select solution from Pareto front");
            return assignments;
        }

        // Apply the selected solution
        int[] taskAssignment = selectedSolution.getTaskAssignment();
        List<List<Integer>> vmTaskOrder = selectedSolution.getVmTaskOrder();

        // Assign tasks according to the solution, respecting task order within VMs
        for (int vmIdx = 0; vmIdx < vmTaskOrder.size(); vmIdx++) {
            VM vm = vmByIndex.get(vmIdx);
            List<Integer> taskOrder = vmTaskOrder.get(vmIdx);

            for (int taskIdx : taskOrder) {
                Task task = taskByIndex.get(taskIdx);

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

    /**
     * Selects a single solution from the Pareto front based on the configured method.
     */
    private SchedulingSolution selectSolution(ParetoFront front) {
        switch (selectionMethod) {
            case KNEE_POINT:
                return front.getKneePoint();

            case BEST_MAKESPAN:
                return front.getBestForObjective(0);

            case BEST_ENERGY:
                return front.getBestForObjective(1);

            case WEIGHTED_SUM:
                return front.getByWeightedSum(selectionWeights);

            case FIRST:
            default:
                List<SchedulingSolution> solutions = front.getSolutions();
                return solutions.isEmpty() ? null : solutions.get(0);
        }
    }

    @Override
    public String getStrategyName() {
        return "NSGA-II";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Multi-objective optimization using NSGA-II. ");
        sb.append("Objectives: ");
        for (int i = 0; i < config.getObjectives().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(config.getObjectives().get(i).getName());
        }
        sb.append(". Selection: ").append(selectionMethod);
        return sb.toString();
    }

    @Override
    public boolean isBatchOptimizing() {
        return true;
    }

    /**
     * Gets the NSGA-II configuration.
     *
     * @return Configuration
     */
    public NSGA2Configuration getConfiguration() {
        return config;
    }
}
