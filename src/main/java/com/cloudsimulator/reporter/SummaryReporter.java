package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.SimulationSummary;

import java.io.IOException;

/**
 * Generates a single-row summary report with key simulation metrics.
 * This provides a high-level overview suitable for comparing multiple experiments.
 */
public class SummaryReporter extends AbstractCSVReporter {

    @Override
    public String getReportType() {
        return "summary";
    }

    @Override
    public String getDefaultFilename() {
        return "summary.csv";
    }

    @Override
    protected String[] getHeaders() {
        return new String[] {
            "simulation_id",
            "timestamp",
            "random_seed",
            "config_file",
            "duration_seconds",
            "wall_clock_ms",
            // Infrastructure
            "datacenter_count",
            "host_count",
            "active_host_count",
            "vm_count",
            "user_count",
            "total_cpu_cores",
            "total_gpus",
            // Tasks
            "total_tasks",
            "completed_tasks",
            "failed_tasks",
            "unassigned_tasks",
            "completion_rate",
            "total_instructions",
            // Performance
            "makespan_seconds",
            "throughput_tasks_per_sec",
            "avg_waiting_time_s",
            "avg_turnaround_time_s",
            "avg_execution_time_s",
            "p50_turnaround_s",
            "p90_turnaround_s",
            "p99_turnaround_s",
            "load_balance_index",
            // Energy
            "total_it_energy_kwh",
            "total_facility_energy_kwh",
            "pue",
            "avg_power_watts",
            "peak_power_watts",
            "carbon_region",
            "carbon_footprint_kg",
            "electricity_cost_dollars",
            "energy_per_task_joules",
            // SLA
            "sla_threshold_seconds",
            "tasks_within_sla",
            "tasks_beyond_sla",
            "sla_compliance_percent"
        };
    }

    @Override
    protected void writeDataRows(CSVWriter writer, SimulationContext context, SimulationSummary summary) throws IOException {
        if (summary == null) {
            // No summary available, write empty row with context data only
            writeFromContext(writer, context);
            return;
        }

        SimulationSummary.InfrastructureSummary infra = summary.getInfrastructure();
        SimulationSummary.TaskSummary tasks = summary.getTasks();
        SimulationSummary.PerformanceSummary perf = summary.getPerformance();
        SimulationSummary.EnergySummary energy = summary.getEnergy();
        SimulationSummary.SLASummary sla = summary.getSla();

        writer.writeRow(
            summary.getSimulationId(),
            summary.getTimestamp(),
            summary.getRandomSeed(),
            summary.getConfigurationFile(),
            summary.getSimulationDurationSeconds(),
            summary.getWallClockDurationMs(),
            // Infrastructure
            infra.datacenterCount,
            infra.hostCount,
            infra.activeHostCount,
            infra.vmCount,
            infra.userCount,
            infra.totalCpuCores,
            infra.totalGpus,
            // Tasks
            tasks.totalTasks,
            tasks.completedTasks,
            tasks.failedTasks,
            tasks.unassignedTasks,
            formatDouble(tasks.completionRate, 4),
            tasks.totalInstructionsExecuted,
            // Performance
            perf.makespanSeconds,
            formatDouble(perf.throughputTasksPerSecond, 6),
            formatDouble(perf.avgWaitingTimeSeconds, 2),
            formatDouble(perf.avgTurnaroundTimeSeconds, 2),
            formatDouble(perf.avgExecutionTimeSeconds, 2),
            formatDouble(perf.p50TurnaroundTimeSeconds, 2),
            formatDouble(perf.p90TurnaroundTimeSeconds, 2),
            formatDouble(perf.p99TurnaroundTimeSeconds, 2),
            formatDouble(perf.loadBalanceIndex, 4),
            // Energy
            formatDouble(energy.totalITEnergyKWh, 6),
            formatDouble(energy.totalFacilityEnergyKWh, 6),
            formatDouble(energy.pue, 2),
            formatDouble(energy.averagePowerWatts, 2),
            formatDouble(energy.peakPowerWatts, 2),
            energy.carbonRegion,
            formatDouble(energy.carbonFootprintKg, 6),
            formatDouble(energy.estimatedCostDollars, 4),
            formatDouble(energy.energyPerTaskJoules, 2),
            // SLA
            sla.slaThresholdSeconds,
            sla.tasksWithinSLA,
            sla.tasksBeyondSLA,
            formatDouble(sla.slaCompliancePercent, 2)
        );

        incrementRowCount();
    }

    /**
     * Writes a row from context data when no summary is available.
     */
    private void writeFromContext(CSVWriter writer, SimulationContext context) throws IOException {
        int dcCount = context.getDatacenters() != null ? context.getDatacenters().size() : 0;
        int hostCount = context.getHosts() != null ? context.getHosts().size() : 0;
        int vmCount = context.getVms() != null ? context.getVms().size() : 0;
        int taskCount = context.getTasks() != null ? context.getTasks().size() : 0;
        int userCount = context.getUsers() != null ? context.getUsers().size() : 0;

        writer.writeRow(
            "unknown",          // simulation_id
            "",                 // timestamp
            0,                  // random_seed
            "",                 // config_file
            0,                  // duration_seconds
            0,                  // wall_clock_ms
            dcCount, hostCount, 0, vmCount, userCount, 0, 0,  // infrastructure
            taskCount, 0, 0, 0, "0.0000", 0,                   // tasks
            0, "0.000000", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.0000",  // performance
            "0.000000", "0.000000", "0.00", "0.00", "0.00", "", "0.000000", "0.0000", "0.00",  // energy
            0, 0, 0, "0.00"    // SLA
        );

        incrementRowCount();
    }
}
