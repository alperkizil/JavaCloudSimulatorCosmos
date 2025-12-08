package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.enums.VmState;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.List;

/**
 * VMExecutionStep orchestrates the main time-stepped simulation loop.
 *
 * This is the sixth step in the simulation pipeline, executed after TaskAssignmentStep.
 * It runs the simulation loop where all VMs execute their assigned tasks until
 * all tasks are completed.
 *
 * Execution flow per tick:
 * 1. For each VM in RUNNING state: execute one second of work
 * 2. For each Host: update state (power consumption, energy tracking)
 * 3. Advance simulation clock
 * 4. Check termination condition (all tasks completed)
 *
 * Progress logging occurs every 100 ticks to provide real-time feedback.
 *
 * Usage:
 * <pre>
 * VMExecutionStep step = new VMExecutionStep();
 * step.execute(context);
 *
 * System.out.println("Simulation ran for: " + step.getTotalSimulationSeconds() + " seconds");
 * System.out.println("Tasks completed: " + step.getTasksCompleted());
 * </pre>
 */
public class VMExecutionStep implements SimulationStep {

    private static final int PROGRESS_LOG_INTERVAL = 100;

    // Execution statistics
    private long totalSimulationSeconds;
    private long vmSecondsExecuted;
    private long vmSecondsIdle;
    private int tasksCompleted;
    private int peakConcurrentTasks;
    private long simulationStartTime;
    private long simulationEndTime;

    public VMExecutionStep() {
        this.totalSimulationSeconds = 0;
        this.vmSecondsExecuted = 0;
        this.vmSecondsIdle = 0;
        this.tasksCompleted = 0;
        this.peakConcurrentTasks = 0;
        this.simulationStartTime = 0;
        this.simulationEndTime = 0;
    }

    @Override
    public void execute(SimulationContext context) {
        List<VM> allVMs = context.getVms();
        List<Host> allHosts = context.getHosts();
        List<Task> allTasks = context.getTasks();

        if (allVMs == null || allVMs.isEmpty()) {
            logInfo("No VMs to execute. Skipping VMExecutionStep.");
            recordMetrics(context);
            return;
        }

        // Count assigned VMs (VMs that have been placed on hosts)
        long assignedVMCount = allVMs.stream()
            .filter(VM::isAssignedToHost)
            .count();

        if (assignedVMCount == 0) {
            logInfo("No VMs assigned to hosts. Skipping VMExecutionStep.");
            recordMetrics(context);
            return;
        }

        // Start all assigned VMs
        for (VM vm : allVMs) {
            if (vm.isAssignedToHost() && vm.getVmState() != VmState.RUNNING) {
                vm.start();
            }
        }

        // Count total tasks to track
        int totalTasks = allTasks != null ? allTasks.size() : 0;
        int assignedTasks = allTasks != null ?
            (int) allTasks.stream().filter(Task::isAssigned).count() : 0;

        logInfo("Starting VM Execution Loop");
        logInfo("  VMs assigned to hosts: " + assignedVMCount);
        logInfo("  Tasks assigned to VMs: " + assignedTasks + " / " + totalTasks);

        simulationStartTime = context.getCurrentTime();

        // Main simulation loop
        while (!isSimulationComplete(allVMs, allTasks)) {
            long currentTime = context.getCurrentTime();

            // Track concurrent executing tasks
            int currentExecutingTasks = countExecutingTasks(allVMs);
            if (currentExecutingTasks > peakConcurrentTasks) {
                peakConcurrentTasks = currentExecutingTasks;
            }

            // Execute one second for each running VM
            for (VM vm : allVMs) {
                if (vm.isAssignedToHost() && vm.getVmState() == VmState.RUNNING) {
                    // Track if VM was executing or idle before this tick
                    boolean wasExecuting = vm.getAssignedTasks().size() > 0;

                    // Execute one simulation second
                    vm.executeOneSecond(currentTime);

                    // Update VM state tracking
                    vm.updateState();

                    // Track execution statistics
                    if (wasExecuting) {
                        vmSecondsExecuted++;
                    } else {
                        vmSecondsIdle++;
                    }
                }
            }

            // Update all hosts (power consumption, energy tracking)
            for (Host host : allHosts) {
                if (host.getAssignedDatacenterId() != null) {
                    host.updateState();
                }
            }

            // Advance simulation clock
            context.advanceTime();
            totalSimulationSeconds++;

            // Progress logging every 100 ticks
            if (totalSimulationSeconds % PROGRESS_LOG_INTERVAL == 0) {
                logProgress(allTasks, allVMs, currentTime);
            }
        }

        simulationEndTime = context.getCurrentTime();

        // Count completed tasks
        tasksCompleted = allTasks != null ?
            (int) allTasks.stream().filter(Task::isCompleted).count() : 0;

        // Final progress log
        logInfo("VM Execution Loop Completed");
        logInfo("  Total simulation time: " + totalSimulationSeconds + " seconds");
        logInfo("  Tasks completed: " + tasksCompleted + " / " + totalTasks);
        logInfo("  Peak concurrent tasks: " + peakConcurrentTasks);

        // Record metrics
        recordMetrics(context);
    }

    /**
     * Checks if the simulation is complete.
     * Simulation is complete when all assigned tasks have finished execution.
     */
    private boolean isSimulationComplete(List<VM> vms, List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return true;
        }

        // Check if all assigned tasks are completed
        for (Task task : tasks) {
            if (task.isAssigned() && !task.isCompleted()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Counts the number of tasks currently executing across all VMs.
     */
    private int countExecutingTasks(List<VM> vms) {
        int count = 0;
        for (VM vm : vms) {
            if (vm.isAssignedToHost() && vm.getVmState() == VmState.RUNNING) {
                // Count tasks in queue (including currently executing)
                count += vm.getAssignedTasks().size();
            }
        }
        return count;
    }

    /**
     * Logs progress information every PROGRESS_LOG_INTERVAL ticks.
     */
    private void logProgress(List<Task> tasks, List<VM> vms, long currentTime) {
        int completed = tasks != null ?
            (int) tasks.stream().filter(Task::isCompleted).count() : 0;
        int total = tasks != null ? (int) tasks.stream().filter(Task::isAssigned).count() : 0;
        int executing = countExecutingTasks(vms);

        double progressPercent = total > 0 ? (double) completed / total * 100.0 : 100.0;

        logInfo(String.format(
            "[Tick %d] Progress: %.1f%% (%d/%d tasks completed, %d executing)",
            totalSimulationSeconds, progressPercent, completed, total, executing
        ));
    }

    /**
     * Records execution metrics to the simulation context.
     */
    private void recordMetrics(SimulationContext context) {
        context.recordMetric("vmExecution.totalSimulationSeconds", totalSimulationSeconds);
        context.recordMetric("vmExecution.vmSecondsExecuted", vmSecondsExecuted);
        context.recordMetric("vmExecution.vmSecondsIdle", vmSecondsIdle);
        context.recordMetric("vmExecution.tasksCompleted", tasksCompleted);
        context.recordMetric("vmExecution.peakConcurrentTasks", peakConcurrentTasks);
        context.recordMetric("vmExecution.simulationStartTime", simulationStartTime);
        context.recordMetric("vmExecution.simulationEndTime", simulationEndTime);

        // Calculate utilization ratio
        long totalVMSeconds = vmSecondsExecuted + vmSecondsIdle;
        double utilizationRatio = totalVMSeconds > 0 ?
            (double) vmSecondsExecuted / totalVMSeconds : 0.0;
        context.recordMetric("vmExecution.vmUtilizationRatio", utilizationRatio);
    }

    /**
     * Logs an info message.
     */
    private void logInfo(String message) {
        System.out.println("[INFO] VMExecutionStep: " + message);
    }

    @Override
    public String getStepName() {
        return "VM Execution";
    }

    // Getters for inspection/testing

    /**
     * Gets the total simulation time in seconds.
     */
    public long getTotalSimulationSeconds() {
        return totalSimulationSeconds;
    }

    /**
     * Gets the total VM-seconds spent executing tasks.
     */
    public long getVmSecondsExecuted() {
        return vmSecondsExecuted;
    }

    /**
     * Gets the total VM-seconds spent idle.
     */
    public long getVmSecondsIdle() {
        return vmSecondsIdle;
    }

    /**
     * Gets the number of tasks that completed during execution.
     */
    public int getTasksCompleted() {
        return tasksCompleted;
    }

    /**
     * Gets the peak number of concurrent executing tasks.
     */
    public int getPeakConcurrentTasks() {
        return peakConcurrentTasks;
    }

    /**
     * Gets the simulation start time (first tick).
     */
    public long getSimulationStartTime() {
        return simulationStartTime;
    }

    /**
     * Gets the simulation end time (last tick).
     */
    public long getSimulationEndTime() {
        return simulationEndTime;
    }

    /**
     * Gets the VM utilization ratio (executing time / total time).
     */
    public double getVmUtilizationRatio() {
        long total = vmSecondsExecuted + vmSecondsIdle;
        return total > 0 ? (double) vmSecondsExecuted / total : 0.0;
    }
}
