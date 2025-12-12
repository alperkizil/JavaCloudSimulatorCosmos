package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.User;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.FirstAvailableTaskAssignmentStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * TaskAssignmentStep assigns tasks to VMs using a configurable assignment strategy.
 *
 * This is the fifth step in the simulation pipeline, executed after VMPlacementStep.
 * It takes all unassigned tasks and assigns them to appropriate VMs based on
 * the selected assignment strategy.
 *
 * Assignment constraints enforced:
 * 1. User Ownership: Tasks can only be assigned to VMs owned by the same user
 * 2. Compute Type Compatibility: Task workload type must be compatible with VM compute type
 * 3. VM State: Tasks can only be assigned to VMs that are in RUNNING state
 *
 * Supported Strategies:
 * - FirstAvailableTaskAssignmentStrategy: Assigns to first compatible VM
 * - ShortestQueueTaskAssignmentStrategy: Balances task count across VMs
 * - WorkloadAwareTaskAssignmentStrategy: Considers task size and VM power
 * - NSGA2TaskSchedulingStrategy: Multi-objective optimization (Pareto front)
 *
 * Usage:
 * <pre>
 * // Using default FirstAvailable strategy
 * TaskAssignmentStep step = new TaskAssignmentStep();
 *
 * // Using custom strategy
 * TaskAssignmentStep step = new TaskAssignmentStep(new WorkloadAwareTaskAssignmentStrategy());
 * </pre>
 */
public class TaskAssignmentStep implements SimulationStep {

    private final TaskAssignmentStrategy strategy;
    private int tasksAssigned;
    private int tasksFailed;
    private Map<Long, Integer> tasksPerVM;
    private Map<String, Integer> tasksPerUser;
    private List<String> failedTaskReasons;

    /**
     * Creates a TaskAssignmentStep with the default FirstAvailable strategy.
     */
    public TaskAssignmentStep() {
        this(new FirstAvailableTaskAssignmentStrategy());
    }

    /**
     * Creates a TaskAssignmentStep with a custom assignment strategy.
     *
     * @param strategy The task assignment strategy to use
     */
    public TaskAssignmentStep(TaskAssignmentStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("TaskAssignmentStrategy cannot be null");
        }
        this.strategy = strategy;
        this.tasksAssigned = 0;
        this.tasksFailed = 0;
        this.tasksPerVM = new HashMap<>();
        this.tasksPerUser = new HashMap<>();
        this.failedTaskReasons = new ArrayList<>();
    }

    @Override
    public void execute(SimulationContext context) {
        List<Task> allTasks = context.getTasks();
        List<VM> allVMs = context.getVms();
        List<User> users = context.getUsers();
        long currentTime = context.getCurrentTime();

        if (allTasks == null || allTasks.isEmpty()) {
            recordMetrics(context);
            return;
        }

        // Filter to only unassigned tasks
        List<Task> unassignedTasks = allTasks.stream()
            .filter(task -> !task.isAssigned())
            .collect(Collectors.toList());

        if (unassignedTasks.isEmpty()) {
            recordMetrics(context);
            return;
        }

        // Filter to only running VMs (that have been placed on hosts)
        List<VM> runningVMs = allVMs.stream()
            .filter(VM::isAssignedToHost)
            .collect(Collectors.toList());

        if (runningVMs.isEmpty()) {
            // All tasks fail - no running VMs
            for (Task task : unassignedTasks) {
                tasksFailed++;
                failedTaskReasons.add("Task " + task.getId() + " (" + task.getName() +
                    "): No running VMs available");
            }
            recordMetrics(context);
            return;
        }

        // Build user lookup
        Map<String, User> usersByName = new HashMap<>();
        for (User user : users) {
            usersByName.put(user.getName(), user);
            tasksPerUser.put(user.getName(), 0);
        }

        // Initialize VM task counts
        for (VM vm : runningVMs) {
            tasksPerVM.put(vm.getId(), 0);
        }

        // Check if strategy uses batch optimization
        if (strategy.isBatchOptimizing()) {
            // Use batch assignment for metaheuristic strategies
            Map<Task, VM> assignments = strategy.assignAll(unassignedTasks, runningVMs, currentTime);

            for (Map.Entry<Task, VM> entry : assignments.entrySet()) {
                Task task = entry.getKey();
                VM vm = entry.getValue();

                tasksAssigned++;
                tasksPerVM.merge(vm.getId(), 1, Integer::sum);
                tasksPerUser.merge(task.getUserId(), 1, Integer::sum);
            }

            // Count failed tasks (those not in assignments)
            for (Task task : unassignedTasks) {
                if (!assignments.containsKey(task)) {
                    tasksFailed++;
                    failedTaskReasons.add("Task " + task.getId() + " (" + task.getName() +
                        "): No compatible VM found by batch optimizer");
                }
            }
        } else {
            // Use per-task assignment for greedy strategies
            for (Task task : unassignedTasks) {
                // Find candidate VMs for this task
                List<VM> candidateVMs = getCandidateVMs(task, runningVMs);

                if (candidateVMs.isEmpty()) {
                    tasksFailed++;
                    failedTaskReasons.add("Task " + task.getId() + " (" + task.getName() +
                        "): No compatible VMs for user '" + task.getUserId() + "'");
                    logWarning("Task " + task.getId() + " has no candidate VMs");
                    continue;
                }

                // Use strategy to select a VM
                Optional<VM> selectedVM = strategy.selectVM(task, candidateVMs);

                if (selectedVM.isPresent()) {
                    VM vm = selectedVM.get();

                    // Assign task to VM
                    task.assignToVM(vm.getId(), currentTime);
                    vm.assignTask(task);

                    tasksAssigned++;
                    tasksPerVM.merge(vm.getId(), 1, Integer::sum);
                    tasksPerUser.merge(task.getUserId(), 1, Integer::sum);
                } else {
                    tasksFailed++;
                    failedTaskReasons.add("Task " + task.getId() + " (" + task.getName() +
                        "): Strategy returned no selection");
                    logWarning("Task " + task.getId() + " could not be assigned by strategy");
                }
            }
        }

        // Record metrics
        recordMetrics(context);
    }

    /**
     * Gets candidate VMs for a task based on user ownership and compute type compatibility.
     *
     * @param task       The task to find VMs for
     * @param runningVMs All running VMs
     * @return List of compatible VMs owned by the task's user
     */
    private List<VM> getCandidateVMs(Task task, List<VM> runningVMs) {
        return runningVMs.stream()
            .filter(vm -> vm.getUserId().equals(task.getUserId()))
            .filter(vm -> vm.canAcceptTask(task))
            .collect(Collectors.toList());
    }

    /**
     * Logs a warning message about task assignment issues.
     */
    private void logWarning(String message) {
        System.out.println("[WARN] TaskAssignmentStep: " + message);
    }

    /**
     * Records metrics about task assignment.
     */
    private void recordMetrics(SimulationContext context) {
        context.recordMetric("taskAssignment.tasksAssigned", tasksAssigned);
        context.recordMetric("taskAssignment.tasksFailed", tasksFailed);
        context.recordMetric("taskAssignment.strategy", strategy.getStrategyName());

        // Record tasks per user
        for (Map.Entry<String, Integer> entry : tasksPerUser.entrySet()) {
            context.recordMetric("taskAssignment.user." + entry.getKey() + ".taskCount", entry.getValue());
        }

        // Record tasks per VM
        for (Map.Entry<Long, Integer> entry : tasksPerVM.entrySet()) {
            context.recordMetric("taskAssignment.vm." + entry.getKey() + ".taskCount", entry.getValue());
        }

        // Calculate statistics about task distribution
        if (!tasksPerVM.isEmpty()) {
            int maxTasks = tasksPerVM.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int minTasks = tasksPerVM.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            double avgTasks = tasksPerVM.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);

            context.recordMetric("taskAssignment.distribution.maxTasksPerVM", maxTasks);
            context.recordMetric("taskAssignment.distribution.minTasksPerVM", minTasks);
            context.recordMetric("taskAssignment.distribution.avgTasksPerVM", avgTasks);
        }
    }

    @Override
    public String getStepName() {
        return "Task Assignment (" + strategy.getStrategyName() + ")";
    }

    /**
     * Gets the assignment strategy being used.
     *
     * @return The task assignment strategy
     */
    public TaskAssignmentStrategy getStrategy() {
        return strategy;
    }

    /**
     * Gets the number of tasks successfully assigned.
     *
     * @return Number of tasks assigned
     */
    public int getTasksAssigned() {
        return tasksAssigned;
    }

    /**
     * Gets the number of tasks that failed to be assigned.
     *
     * @return Number of tasks that failed assignment
     */
    public int getTasksFailed() {
        return tasksFailed;
    }

    /**
     * Gets the number of tasks assigned to each VM.
     *
     * @return Map of VM ID to task count
     */
    public Map<Long, Integer> getTasksPerVM() {
        return tasksPerVM;
    }

    /**
     * Gets the number of tasks assigned per user.
     *
     * @return Map of user name to task count
     */
    public Map<String, Integer> getTasksPerUser() {
        return tasksPerUser;
    }

    /**
     * Gets the reasons for failed task assignments.
     *
     * @return List of failure reason strings
     */
    public List<String> getFailedTaskReasons() {
        return failedTaskReasons;
    }

    /**
     * Gets the number of VMs that have at least one task assigned.
     *
     * @return Number of VMs with tasks
     */
    public int getActiveVMCount() {
        return (int) tasksPerVM.values().stream().filter(count -> count > 0).count();
    }
}
