package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.model.VM;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a per-host report with resource allocation, utilization, and energy metrics.
 */
public class HostReporter extends AbstractCSVReporter {

    @Override
    public String getReportType() {
        return "hosts";
    }

    @Override
    public String getDefaultFilename() {
        return "hosts.csv";
    }

    @Override
    protected String[] getHeaders() {
        return new String[] {
            "host_id",
            "datacenter_name",
            "compute_type",
            "cpu_cores",
            "allocated_cpu_cores",
            "gpus",
            "allocated_gpus",
            "ram_mb",
            "allocated_ram_mb",
            "storage_mb",
            "allocated_storage_mb",
            "network_mbps",
            "allocated_network_mbps",
            "ips_per_second",
            "vm_count",
            "power_model",
            "idle_power_watts",
            "max_power_watts",
            "current_power_watts",
            "energy_consumed_joules",
            "energy_consumed_kwh",
            "cpu_utilization",
            "gpu_utilization",
            "ram_utilization",
            "active_seconds",
            "idle_seconds"
        };
    }

    @Override
    protected void writeDataRows(CSVWriter writer, SimulationContext context, SimulationSummary summary) throws IOException {
        List<Host> hosts = context.getHosts();
        List<CloudDatacenter> datacenters = context.getDatacenters();

        if (hosts == null || hosts.isEmpty()) {
            return;
        }

        // Create datacenter lookup
        Map<Long, String> dcNames = new HashMap<>();
        if (datacenters != null) {
            for (CloudDatacenter dc : datacenters) {
                dcNames.put(dc.getId(), dc.getName());
            }
        }

        for (Host host : hosts) {
            String dcName = dcNames.getOrDefault(host.getAssignedDatacenterId(), "unassigned");

            // Calculate allocated resources (total - available)
            int allocatedCpuCores = host.getNumberOfCpuCores() - host.getAvailableCpuCores();
            int allocatedGpus = host.getNumberOfGpus() - host.getAvailableGpus();
            long allocatedRamMB = host.getRamCapacityMB() - host.getAvailableRamMB();

            // Calculate GPU utilization
            double gpuUtil = 0;
            if (host.getNumberOfGpus() > 0) {
                gpuUtil = (double) allocatedGpus / host.getNumberOfGpus();
            }

            // Calculate RAM utilization
            double ramUtil = 0;
            if (host.getRamCapacityMB() > 0) {
                ramUtil = (double) allocatedRamMB / host.getRamCapacityMB();
            }

            // Get power model info
            String powerModelName = "N/A";
            double idlePower = 0;
            double maxPower = 0;
            if (host.getPowerModel() != null) {
                powerModelName = host.getPowerModel().getClass().getSimpleName();
                idlePower = host.getPowerModel().getIdleCpuPower() + host.getPowerModel().getIdleGpuPower();
                maxPower = host.getPowerModel().getMaxCpuPower() +
                           host.getPowerModel().getMaxGpuPower() +
                           host.getPowerModel().getOtherComponentsPower();
            }

            // Calculate allocated storage and bandwidth from VMs
            long allocatedStorageMB = 0;
            long allocatedBandwidthMbps = 0;
            for (VM vm : host.getAssignedVMs()) {
                allocatedStorageMB += vm.getRequestedStorageMB();
                allocatedBandwidthMbps += vm.getRequestedBandwidthMbps();
            }

            writer.writeRow(
                host.getId(),
                dcName,
                host.getComputeType().name(),
                host.getNumberOfCpuCores(),
                allocatedCpuCores,
                host.getNumberOfGpus(),
                allocatedGpus,
                host.getRamCapacityMB(),
                allocatedRamMB,
                host.getHardDriveCapacityMB(),
                allocatedStorageMB,
                host.getNetworkCapacityMbps(),
                allocatedBandwidthMbps,
                host.getInstructionsPerSecond(),
                host.getAssignedVMs().size(),
                powerModelName,
                formatDouble(idlePower, 2),
                formatDouble(maxPower, 2),
                formatDouble(host.getCurrentTotalPowerDraw(), 2),
                formatDouble(host.getTotalEnergyConsumed(), 2),
                formatDouble(host.getTotalEnergyConsumedKWh(), 6),
                formatDouble(host.getResourceUtilization(), 4),
                formatDouble(gpuUtil, 4),
                formatDouble(ramUtil, 4),
                host.getActiveSeconds(),
                host.getSecondsIDLE()
            );

            incrementRowCount();
        }
    }
}
