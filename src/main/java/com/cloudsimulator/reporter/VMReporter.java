package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.model.VM;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a per-VM report with resource allocation, task execution, and utilization metrics.
 */
public class VMReporter extends AbstractCSVReporter {

    @Override
    public String getReportType() {
        return "vms";
    }

    @Override
    public String getDefaultFilename() {
        return "vms.csv";
    }

    @Override
    protected String[] getHeaders() {
        return new String[] {
            "vm_id",
            "user_id",
            "host_id",
            "compute_type",
            "state",
            "vcpu_count",
            "gpu_count",
            "ram_mb",
            "storage_mb",
            "bandwidth_mbps",
            "ips_per_vcpu",
            "total_ips_capacity",
            "total_tasks_assigned",
            "tasks_completed",
            "tasks_pending",
            "total_open_seconds",
            "active_workload_seconds",
            "idle_seconds",
            "utilization_ratio"
        };
    }

    @Override
    protected void writeDataRows(CSVWriter writer, SimulationContext context, SimulationSummary summary) throws IOException {
        List<VM> vms = context.getVms();
        if (vms == null || vms.isEmpty()) {
            return;
        }

        // Create host lookup for host IDs
        Map<Long, Long> vmToHostMap = new HashMap<>();
        List<Host> hosts = context.getHosts();
        if (hosts != null) {
            for (Host host : hosts) {
                for (VM vm : host.getAssignedVMs()) {
                    vmToHostMap.put(vm.getId(), host.getId());
                }
            }
        }

        for (VM vm : vms) {
            Long hostId = vmToHostMap.get(vm.getId());

            // Calculate tasks
            int pendingTasks = vm.getAssignedTasks().size();
            int completedTasks = vm.getFinishedTasks().size();
            int totalTasks = pendingTasks + completedTasks;

            // Calculate utilization ratio
            double utilRatio = 0;
            long totalOpen = vm.getTotalOpenSeconds();
            long activeWorkload = vm.getTotalActiveWorkloadSeconds();
            if (totalOpen > 0) {
                utilRatio = (double) activeWorkload / totalOpen;
            }

            // Total IPS capacity
            long totalIps = vm.getRequestedIpsPerVcpu() * vm.getRequestedVcpuCount();

            writer.writeRow(
                vm.getId(),
                vm.getUserId(),
                hostId != null ? hostId : "unassigned",
                vm.getComputeType().name(),
                vm.getVmState().name(),
                vm.getRequestedVcpuCount(),
                vm.getRequestedGpuCount(),
                vm.getRequestedRamMB(),
                vm.getRequestedStorageMB(),
                vm.getRequestedBandwidthMbps(),
                vm.getRequestedIpsPerVcpu(),
                totalIps,
                totalTasks,
                completedTasks,
                pendingTasks,
                totalOpen,
                activeWorkload,
                vm.getTotalIDLESeconds(),
                formatDouble(utilRatio, 4)
            );

            incrementRowCount();
        }
    }
}
