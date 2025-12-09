package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.model.Task;

import java.io.IOException;
import java.util.List;

/**
 * Generates a per-task report with detailed execution information.
 * Uses streaming writes for memory efficiency with large task counts.
 */
public class TaskReporter extends AbstractCSVReporter {

    @Override
    public String getReportType() {
        return "tasks";
    }

    @Override
    public String getDefaultFilename() {
        return "tasks.csv";
    }

    @Override
    protected String[] getHeaders() {
        return new String[] {
            "task_id",
            "task_name",
            "user_id",
            "workload_type",
            "instruction_length",
            "instructions_executed",
            "progress_percent",
            "vm_id",
            "status",
            "creation_time_s",
            "assignment_time_s",
            "exec_start_time_s",
            "exec_end_time_s",
            "waiting_time_s",
            "execution_time_s",
            "turnaround_time_s",
            "is_completed",
            "is_assigned"
        };
    }

    @Override
    protected void writeDataRows(CSVWriter writer, SimulationContext context, SimulationSummary summary) throws IOException {
        List<Task> tasks = context.getTasks();
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        // Stream through tasks one by one for memory efficiency
        for (Task task : tasks) {
            // Calculate waiting time
            Long waitingTime = task.getWaitingTime();

            // Calculate turnaround time
            Long turnaroundTime = task.getTurnaroundTime();

            // Calculate execution time
            long execTime = task.getTaskCpuExecTime();

            // Progress percentage
            double progress = task.getProgressPercentage();

            writer.writeRow(
                task.getId(),
                task.getName(),
                task.getUserId(),
                task.getWorkloadType().name(),
                task.getInstructionLength(),
                task.getInstructionsExecuted(),
                formatDouble(progress, 2),
                task.getAssignedVmId() != null ? task.getAssignedVmId() : "unassigned",
                task.getTaskExecutionStatus().name(),
                task.getTaskCreationTime(),
                task.getTaskAssignmentTime() != null ? task.getTaskAssignmentTime() : "",
                task.getTaskExecStartTime() != null ? task.getTaskExecStartTime() : "",
                task.getTaskExecEndTime() != null ? task.getTaskExecEndTime() : "",
                waitingTime != null ? waitingTime : "",
                execTime,
                turnaroundTime != null ? turnaroundTime : "",
                task.isCompleted(),
                task.isAssigned()
            );

            incrementRowCount();
        }
    }
}
