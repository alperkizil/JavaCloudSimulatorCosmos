package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.User;
import com.cloudsimulator.model.VM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TaskExecutionStep performs post-simulation analysis of task completion.
 *
 * This is the seventh step in the simulation pipeline, executed after VMExecutionStep.
 * It aggregates task completion metrics and finalizes user sessions.
 *
 * Analysis performed:
 * 1. Collect all completed tasks from all VMs
 * 2. Calculate task metrics (makespan, waiting time, turnaround time, throughput)
 * 3. Finalize user sessions for users with all tasks completed
 * 4. Generate per-user and per-workload-type statistics
 *
 * Usage:
 * <pre>
 * TaskExecutionStep step = new TaskExecutionStep();
 * step.execute(context);
 *
 * System.out.println("Makespan: " + step.getMakespan() + " seconds");
 * System.out.println("Avg turnaround time: " + step.getAverageTurnaroundTime() + " seconds");
 * </pre>
 */
public class TaskExecutionStep implements SimulationStep {

    // Task completion statistics
    private int completedTasks;
    private int failedTasks;
    private int unassignedTasks;

    // Timing metrics
    private long makespan;
    private double averageWaitingTime;
    private double averageTurnaroundTime;
    private double averageExecutionTime;
    private double throughput;

    // First and last task times
    private Long firstTaskStartTime;
    private Long lastTaskEndTime;

    // Per-user statistics
    private Map<String, UserTaskStatistics> userStatistics;

    // Per-workload statistics
    private Map<WorkloadType, WorkloadStatistics> workloadStatistics;

    // Users who completed all tasks
    private int usersCompleted;
    private List<String> completedUserNames;

    public TaskExecutionStep() {
        this.completedTasks = 0;
        this.failedTasks = 0;
        this.unassignedTasks = 0;
        this.makespan = 0;
        this.averageWaitingTime = 0.0;
        this.averageTurnaroundTime = 0.0;
        this.averageExecutionTime = 0.0;
        this.throughput = 0.0;
        this.firstTaskStartTime = null;
        this.lastTaskEndTime = null;
        this.userStatistics = new HashMap<>();
        this.workloadStatistics = new HashMap<>();
        this.usersCompleted = 0;
        this.completedUserNames = new ArrayList<>();
    }

    @Override
    public void execute(SimulationContext context) {
        List<Task> allTasks = context.getTasks();
        List<User> users = context.getUsers();
        long currentTime = context.getCurrentTime();

        if (allTasks == null || allTasks.isEmpty()) {
            logInfo("No tasks to analyze. Skipping TaskExecutionStep.");
            recordMetrics(context);
            return;
        }

        logInfo("Analyzing task execution results...");

        // Initialize per-user statistics
        for (User user : users) {
            userStatistics.put(user.getName(), new UserTaskStatistics());
        }

        // Initialize per-workload statistics
        for (WorkloadType type : WorkloadType.values()) {
            workloadStatistics.put(type, new WorkloadStatistics());
        }

        // Analyze each task
        List<Long> waitingTimes = new ArrayList<>();
        List<Long> turnaroundTimes = new ArrayList<>();
        List<Long> executionTimes = new ArrayList<>();

        for (Task task : allTasks) {
            String userId = task.getUserId();
            WorkloadType workloadType = task.getWorkloadType();

            UserTaskStatistics userStats = userStatistics.get(userId);
            WorkloadStatistics workloadStats = workloadStatistics.get(workloadType);

            if (userStats != null) {
                userStats.totalTasks++;
            }
            if (workloadStats != null) {
                workloadStats.totalTasks++;
            }

            if (!task.isAssigned()) {
                unassignedTasks++;
                if (userStats != null) {
                    userStats.unassignedTasks++;
                }
                continue;
            }

            if (task.isCompleted()) {
                completedTasks++;

                if (userStats != null) {
                    userStats.completedTasks++;
                }
                if (workloadStats != null) {
                    workloadStats.completedTasks++;
                }

                // Track first task start time
                Long startTime = task.getTaskExecStartTime();
                if (startTime != null) {
                    if (firstTaskStartTime == null || startTime < firstTaskStartTime) {
                        firstTaskStartTime = startTime;
                    }
                }

                // Track last task end time
                Long endTime = task.getTaskExecEndTime();
                if (endTime != null) {
                    if (lastTaskEndTime == null || endTime > lastTaskEndTime) {
                        lastTaskEndTime = endTime;
                    }
                }

                // Calculate waiting time
                Long waitingTime = task.getWaitingTime();
                if (waitingTime != null) {
                    waitingTimes.add(waitingTime);
                    if (userStats != null) {
                        userStats.totalWaitingTime += waitingTime;
                    }
                    if (workloadStats != null) {
                        workloadStats.totalWaitingTime += waitingTime;
                    }
                }

                // Calculate turnaround time
                Long turnaroundTime = task.getTurnaroundTime();
                if (turnaroundTime != null) {
                    turnaroundTimes.add(turnaroundTime);
                    if (userStats != null) {
                        userStats.totalTurnaroundTime += turnaroundTime;
                    }
                    if (workloadStats != null) {
                        workloadStats.totalTurnaroundTime += turnaroundTime;
                    }
                }

                // Calculate execution time
                long execTime = task.getTaskCpuExecTime();
                executionTimes.add(execTime);
                if (userStats != null) {
                    userStats.totalExecutionTime += execTime;
                }
                if (workloadStats != null) {
                    workloadStats.totalExecutionTime += execTime;
                }
            } else {
                // Task was assigned but not completed (failed)
                failedTasks++;
                if (userStats != null) {
                    userStats.failedTasks++;
                }
                if (workloadStats != null) {
                    workloadStats.failedTasks++;
                }
            }
        }

        // Calculate makespan
        if (firstTaskStartTime != null && lastTaskEndTime != null) {
            makespan = lastTaskEndTime - firstTaskStartTime;
        }

        // Calculate averages
        if (!waitingTimes.isEmpty()) {
            averageWaitingTime = waitingTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        }

        if (!turnaroundTimes.isEmpty()) {
            averageTurnaroundTime = turnaroundTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        }

        if (!executionTimes.isEmpty()) {
            averageExecutionTime = executionTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        }

        // Calculate throughput (tasks per second)
        if (makespan > 0) {
            throughput = (double) completedTasks / makespan;
        }

        // Finalize user sessions
        finalizeUserSessions(users, allTasks, currentTime);

        // Log summary
        logSummary(allTasks.size());

        // Record metrics
        recordMetrics(context);
    }

    /**
     * Finalizes user sessions for users who have completed all their tasks.
     */
    private void finalizeUserSessions(List<User> users, List<Task> tasks, long currentTime) {
        // Group tasks by user
        Map<String, List<Task>> tasksByUser = tasks.stream()
            .collect(Collectors.groupingBy(Task::getUserId));

        for (User user : users) {
            List<Task> userTasks = tasksByUser.get(user.getName());

            if (userTasks == null || userTasks.isEmpty()) {
                // User had no tasks - consider session complete
                user.finishSession(currentTime);
                usersCompleted++;
                completedUserNames.add(user.getName());
                continue;
            }

            // Check if all user tasks are completed
            boolean allCompleted = userTasks.stream()
                .filter(Task::isAssigned)
                .allMatch(Task::isCompleted);

            if (allCompleted) {
                // Find the last task completion time for this user
                Long lastCompletion = userTasks.stream()
                    .filter(Task::isCompleted)
                    .map(Task::getTaskExecEndTime)
                    .filter(t -> t != null)
                    .max(Long::compare)
                    .orElse(currentTime);

                user.finishSession(lastCompletion);
                usersCompleted++;
                completedUserNames.add(user.getName());
            }
        }
    }

    /**
     * Logs a summary of task execution analysis.
     */
    private void logSummary(int totalTasks) {
        logInfo("Task Execution Analysis Complete");
        logInfo("  Total tasks: " + totalTasks);
        logInfo("  Completed: " + completedTasks);
        logInfo("  Failed: " + failedTasks);
        logInfo("  Unassigned: " + unassignedTasks);
        logInfo("  Makespan: " + makespan + " seconds");
        logInfo("  Avg waiting time: " + String.format("%.2f", averageWaitingTime) + " seconds");
        logInfo("  Avg turnaround time: " + String.format("%.2f", averageTurnaroundTime) + " seconds");
        logInfo("  Avg execution time: " + String.format("%.2f", averageExecutionTime) + " seconds");
        logInfo("  Throughput: " + String.format("%.4f", throughput) + " tasks/second");
        logInfo("  Users completed: " + usersCompleted + " / " + userStatistics.size());
    }

    /**
     * Records task execution metrics to the simulation context.
     */
    private void recordMetrics(SimulationContext context) {
        // Overall metrics
        context.recordMetric("taskExecution.completedTasks", completedTasks);
        context.recordMetric("taskExecution.failedTasks", failedTasks);
        context.recordMetric("taskExecution.unassignedTasks", unassignedTasks);
        context.recordMetric("taskExecution.makespan", makespan);
        context.recordMetric("taskExecution.avgWaitingTime", averageWaitingTime);
        context.recordMetric("taskExecution.avgTurnaroundTime", averageTurnaroundTime);
        context.recordMetric("taskExecution.avgExecutionTime", averageExecutionTime);
        context.recordMetric("taskExecution.throughput", throughput);
        context.recordMetric("taskExecution.usersCompleted", usersCompleted);

        // Per-user metrics
        for (Map.Entry<String, UserTaskStatistics> entry : userStatistics.entrySet()) {
            String userName = entry.getKey();
            UserTaskStatistics stats = entry.getValue();
            context.recordMetric("taskExecution.user." + userName + ".total", stats.totalTasks);
            context.recordMetric("taskExecution.user." + userName + ".completed", stats.completedTasks);
            context.recordMetric("taskExecution.user." + userName + ".failed", stats.failedTasks);

            if (stats.completedTasks > 0) {
                double avgWait = (double) stats.totalWaitingTime / stats.completedTasks;
                double avgTurnaround = (double) stats.totalTurnaroundTime / stats.completedTasks;
                context.recordMetric("taskExecution.user." + userName + ".avgWaitingTime", avgWait);
                context.recordMetric("taskExecution.user." + userName + ".avgTurnaroundTime", avgTurnaround);
            }
        }

        // Per-workload metrics
        for (Map.Entry<WorkloadType, WorkloadStatistics> entry : workloadStatistics.entrySet()) {
            WorkloadType type = entry.getKey();
            WorkloadStatistics stats = entry.getValue();

            if (stats.totalTasks > 0) {
                context.recordMetric("taskExecution.workload." + type.name() + ".total", stats.totalTasks);
                context.recordMetric("taskExecution.workload." + type.name() + ".completed", stats.completedTasks);

                if (stats.completedTasks > 0) {
                    double avgExec = (double) stats.totalExecutionTime / stats.completedTasks;
                    context.recordMetric("taskExecution.workload." + type.name() + ".avgExecutionTime", avgExec);
                }
            }
        }
    }

    /**
     * Logs an info message.
     */
    private void logInfo(String message) {
        System.out.println("[INFO] TaskExecutionStep: " + message);
    }

    @Override
    public String getStepName() {
        return "Task Execution Analysis";
    }

    // Getters for inspection/testing

    public int getCompletedTasks() {
        return completedTasks;
    }

    public int getFailedTasks() {
        return failedTasks;
    }

    public int getUnassignedTasks() {
        return unassignedTasks;
    }

    public long getMakespan() {
        return makespan;
    }

    public double getAverageWaitingTime() {
        return averageWaitingTime;
    }

    public double getAverageTurnaroundTime() {
        return averageTurnaroundTime;
    }

    public double getAverageExecutionTime() {
        return averageExecutionTime;
    }

    public double getThroughput() {
        return throughput;
    }

    public int getUsersCompleted() {
        return usersCompleted;
    }

    public List<String> getCompletedUserNames() {
        return completedUserNames;
    }

    public Map<String, UserTaskStatistics> getUserStatistics() {
        return userStatistics;
    }

    public Map<WorkloadType, WorkloadStatistics> getWorkloadStatistics() {
        return workloadStatistics;
    }

    /**
     * Statistics for a single user's tasks.
     */
    public static class UserTaskStatistics {
        public int totalTasks = 0;
        public int completedTasks = 0;
        public int failedTasks = 0;
        public int unassignedTasks = 0;
        public long totalWaitingTime = 0;
        public long totalTurnaroundTime = 0;
        public long totalExecutionTime = 0;

        public double getCompletionRate() {
            return totalTasks > 0 ? (double) completedTasks / totalTasks : 0.0;
        }

        public double getAverageWaitingTime() {
            return completedTasks > 0 ? (double) totalWaitingTime / completedTasks : 0.0;
        }

        public double getAverageTurnaroundTime() {
            return completedTasks > 0 ? (double) totalTurnaroundTime / completedTasks : 0.0;
        }

        @Override
        public String toString() {
            return String.format("UserTaskStatistics{total=%d, completed=%d, failed=%d, avgTurnaround=%.2f}",
                totalTasks, completedTasks, failedTasks, getAverageTurnaroundTime());
        }
    }

    /**
     * Statistics for a workload type.
     */
    public static class WorkloadStatistics {
        public int totalTasks = 0;
        public int completedTasks = 0;
        public int failedTasks = 0;
        public long totalWaitingTime = 0;
        public long totalTurnaroundTime = 0;
        public long totalExecutionTime = 0;

        public double getCompletionRate() {
            return totalTasks > 0 ? (double) completedTasks / totalTasks : 0.0;
        }

        public double getAverageExecutionTime() {
            return completedTasks > 0 ? (double) totalExecutionTime / completedTasks : 0.0;
        }

        @Override
        public String toString() {
            return String.format("WorkloadStatistics{total=%d, completed=%d, avgExecTime=%.2f}",
                totalTasks, completedTasks, getAverageExecutionTime());
        }
    }
}
