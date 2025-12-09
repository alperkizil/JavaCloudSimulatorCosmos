package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.User;
import com.cloudsimulator.model.VM;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a per-user report with VM, task, and session metrics.
 */
public class UserReporter extends AbstractCSVReporter {

    @Override
    public String getReportType() {
        return "users";
    }

    @Override
    public String getDefaultFilename() {
        return "users.csv";
    }

    @Override
    protected String[] getHeaders() {
        return new String[] {
            "user_name",
            "datacenter_preferences",
            "vm_count",
            "cpu_vms",
            "gpu_vms",
            "mixed_vms",
            "total_vcpus",
            "total_gpus",
            "total_ram_mb",
            "task_count",
            "completed_tasks",
            "failed_tasks",
            "pending_tasks",
            "completion_rate",
            "avg_waiting_time_s",
            "avg_turnaround_time_s",
            "avg_execution_time_s",
            "total_instructions",
            "session_start_time",
            "session_end_time",
            "session_duration_s"
        };
    }

    @Override
    protected void writeDataRows(CSVWriter writer, SimulationContext context, SimulationSummary summary) throws IOException {
        List<User> users = context.getUsers();
        List<Task> allTasks = context.getTasks();
        List<CloudDatacenter> datacenters = context.getDatacenters();

        if (users == null || users.isEmpty()) {
            return;
        }

        // Create datacenter ID to name lookup
        Map<Long, String> dcIdToName = new HashMap<>();
        if (datacenters != null) {
            for (CloudDatacenter dc : datacenters) {
                dcIdToName.put(dc.getId(), dc.getName());
            }
        }

        // Group tasks by user
        Map<String, List<Task>> tasksByUser = allTasks != null
            ? allTasks.stream().collect(Collectors.groupingBy(Task::getUserId))
            : new HashMap<>();

        for (User user : users) {
            List<VM> vms = user.getVirtualMachines();
            List<Task> userTasks = tasksByUser.getOrDefault(user.getName(), new ArrayList<>());

            // Count VM types
            int cpuVms = 0, gpuVms = 0, mixedVms = 0;
            int totalVcpus = 0, totalGpus = 0;
            long totalRamMB = 0;

            for (VM vm : vms) {
                switch (vm.getComputeType()) {
                    case CPU_ONLY:
                        cpuVms++;
                        break;
                    case GPU_ONLY:
                        gpuVms++;
                        break;
                    case CPU_GPU_MIXED:
                        mixedVms++;
                        break;
                }
                totalVcpus += vm.getRequestedVcpuCount();
                totalGpus += vm.getRequestedGpuCount();
                totalRamMB += vm.getRequestedRamMB();
            }

            // Count task statuses
            int completed = 0, failed = 0, pending = 0;
            long totalInstructions = 0;
            List<Long> waitingTimes = new ArrayList<>();
            List<Long> turnaroundTimes = new ArrayList<>();
            List<Long> execTimes = new ArrayList<>();

            for (Task task : userTasks) {
                totalInstructions += task.getInstructionLength();

                if (task.isCompleted()) {
                    completed++;
                    Long wt = task.getWaitingTime();
                    Long tt = task.getTurnaroundTime();
                    if (wt != null) waitingTimes.add(wt);
                    if (tt != null) turnaroundTimes.add(tt);
                    execTimes.add(task.getTaskCpuExecTime());
                } else if (task.isAssigned()) {
                    failed++;  // Assigned but not completed
                } else {
                    pending++;
                }
            }

            // Calculate averages
            double avgWaiting = waitingTimes.isEmpty() ? 0 :
                waitingTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            double avgTurnaround = turnaroundTimes.isEmpty() ? 0 :
                turnaroundTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            double avgExec = execTimes.isEmpty() ? 0 :
                execTimes.stream().mapToLong(Long::longValue).average().orElse(0);

            double completionRate = userTasks.isEmpty() ? 0 :
                (double) completed / userTasks.size();

            // Datacenter preferences - convert IDs to names
            String dcPrefs = "";
            List<Long> dcIds = user.getUserSelectedDatacenters();
            if (dcIds != null && !dcIds.isEmpty()) {
                List<String> dcNames = new ArrayList<>();
                for (Long dcId : dcIds) {
                    String name = dcIdToName.get(dcId);
                    if (name != null) {
                        dcNames.add(name);
                    } else {
                        dcNames.add("DC-" + dcId);
                    }
                }
                dcPrefs = String.join("|", dcNames);
            }

            // Session duration
            Long sessionStart = user.getStartTimestamp();
            Long sessionEnd = user.getFinishTimestamp();
            Long sessionDuration = null;
            if (sessionStart != null && sessionEnd != null) {
                sessionDuration = sessionEnd - sessionStart;
            }

            writer.writeRow(
                user.getName(),
                dcPrefs,
                vms.size(),
                cpuVms,
                gpuVms,
                mixedVms,
                totalVcpus,
                totalGpus,
                totalRamMB,
                userTasks.size(),
                completed,
                failed,
                pending,
                formatDouble(completionRate, 4),
                formatDouble(avgWaiting, 2),
                formatDouble(avgTurnaround, 2),
                formatDouble(avgExec, 2),
                totalInstructions,
                sessionStart != null ? sessionStart : "",
                sessionEnd != null ? sessionEnd : "",
                sessionDuration != null ? sessionDuration : ""
            );

            incrementRowCount();
        }
    }
}
