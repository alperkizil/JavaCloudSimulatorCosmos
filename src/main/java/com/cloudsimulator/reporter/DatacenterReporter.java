package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.SimulationSummary;

import java.io.IOException;
import java.util.List;

/**
 * Generates a per-datacenter report with infrastructure and energy metrics.
 */
public class DatacenterReporter extends AbstractCSVReporter {

    @Override
    public String getReportType() {
        return "datacenters";
    }

    @Override
    public String getDefaultFilename() {
        return "datacenters.csv";
    }

    @Override
    protected String[] getHeaders() {
        return new String[] {
            "datacenter_id",
            "datacenter_name",
            "max_host_capacity",
            "current_host_count",
            "active_host_count",
            "vm_count",
            "user_count",
            "total_cpu_cores",
            "allocated_cpu_cores",
            "total_gpus",
            "allocated_gpus",
            "total_ram_mb",
            "allocated_ram_mb",
            "max_power_watts",
            "current_power_watts",
            "energy_consumed_kwh",
            "avg_cpu_utilization",
            "avg_gpu_utilization"
        };
    }

    @Override
    protected void writeDataRows(CSVWriter writer, SimulationContext context, SimulationSummary summary) throws IOException {
        List<CloudDatacenter> datacenters = context.getDatacenters();
        if (datacenters == null || datacenters.isEmpty()) {
            return;
        }

        for (CloudDatacenter dc : datacenters) {
            List<Host> hosts = dc.getHosts();

            // Calculate aggregated metrics
            int totalCpuCores = 0;
            int allocatedCpuCores = 0;
            int totalGpus = 0;
            int allocatedGpus = 0;
            long totalRamMB = 0;
            long allocatedRamMB = 0;
            int activeHosts = 0;
            int vmCount = 0;
            double totalCpuUtil = 0;
            double totalGpuUtil = 0;

            for (Host host : hosts) {
                totalCpuCores += host.getNumberOfCpuCores();
                allocatedCpuCores += (host.getNumberOfCpuCores() - host.getAvailableCpuCores());
                totalGpus += host.getNumberOfGpus();
                allocatedGpus += (host.getNumberOfGpus() - host.getAvailableGpus());
                totalRamMB += host.getRamCapacityMB();
                allocatedRamMB += (host.getRamCapacityMB() - host.getAvailableRamMB());
                vmCount += host.getAssignedVMs().size();

                if (!host.getAssignedVMs().isEmpty()) {
                    activeHosts++;
                    totalCpuUtil += host.getResourceUtilization();
                    if (host.getNumberOfGpus() > 0) {
                        totalGpuUtil += (double)(host.getNumberOfGpus() - host.getAvailableGpus()) / host.getNumberOfGpus();
                    }
                }
            }

            double avgCpuUtil = activeHosts > 0 ? totalCpuUtil / activeHosts : 0;
            double avgGpuUtil = activeHosts > 0 ? totalGpuUtil / activeHosts : 0;

            writer.writeRow(
                dc.getId(),
                dc.getName(),
                dc.getMaxHostCapacity(),
                hosts.size(),
                activeHosts,
                vmCount,
                dc.getCustomers().size(),
                totalCpuCores,
                allocatedCpuCores,
                totalGpus,
                allocatedGpus,
                totalRamMB,
                allocatedRamMB,
                formatDouble(dc.getTotalMaxPowerDraw(), 2),
                formatDouble(dc.getTotalMomentaryPowerDraw(), 2),
                formatDouble(dc.getTotalEnergyConsumedKWh(), 6),
                formatDouble(avgCpuUtil, 4),
                formatDouble(avgGpuUtil, 4)
            );

            incrementRowCount();
        }
    }
}
