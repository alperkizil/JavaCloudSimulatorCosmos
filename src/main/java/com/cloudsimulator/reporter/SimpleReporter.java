package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.model.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * SimpleReporter generates a single-row CSV report with essential simulation metrics
 * focused on Makespan and Energy consumption for optimization testing.
 *
 * Output metrics:
 * - Makespan (seconds)
 * - Infrastructure counts (datacenters, hosts, users, tasks)
 * - Task breakdown by type (GPU vs CPU)
 * - Performance metrics (waiting time, throughput)
 * - Per-datacenter energy (kWh, pure IT - no PUE) and peak power (watts)
 *
 * Output file: reports/{HHmmss}_{yyyyMMdd}_{simulationId}.csv
 */
public class SimpleReporter extends AbstractCSVReporter {

    private static final String DEFAULT_OUTPUT_DIR = "reports";

    private String outputDirectory = DEFAULT_OUTPUT_DIR;
    private List<String> datacenterNames = new ArrayList<>();
    private Date reportGenerationTime;

    public SimpleReporter() {
        this.reportGenerationTime = new Date();
    }

    /**
     * Sets the output directory for reports.
     */
    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Override
    public String getReportType() {
        return "simple";
    }

    @Override
    public String getDefaultFilename() {
        return "simple_report.csv";
    }

    /**
     * Generates the filename based on wall clock time, date, and simulation ID.
     * Format: {HHmmss}_{yyyyMMdd}_{simulationId}.csv
     */
    public String generateFilename(SimulationSummary summary) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

        String time = timeFormat.format(reportGenerationTime);
        String date = dateFormat.format(reportGenerationTime);
        String simId = summary != null && summary.getSimulationId() != null
            ? summary.getSimulationId()
            : "unknown";

        return String.format("%s_%s_%s.csv", time, date, simId);
    }

    /**
     * Generates the report and saves it to the configured output directory.
     * Creates the directory if it doesn't exist.
     */
    public void generateAndSaveReport(SimulationContext context, SimulationSummary summary) throws IOException {
        // Ensure output directory exists
        Path outputDir = Paths.get(outputDirectory);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // Initialize datacenter names for dynamic columns
        initializeDatacenterNames(context);

        // Generate filename and full path
        String filename = generateFilename(summary);
        Path outputPath = outputDir.resolve(filename);

        // Generate the report
        generateReport(outputPath, context, summary);

        System.out.println("[INFO] SimpleReporter: Report saved to " + outputPath.toAbsolutePath());
    }

    /**
     * Initializes the list of datacenter names for dynamic column generation.
     */
    private void initializeDatacenterNames(SimulationContext context) {
        datacenterNames.clear();
        List<CloudDatacenter> datacenters = context.getDatacenters();
        if (datacenters != null) {
            for (CloudDatacenter dc : datacenters) {
                datacenterNames.add(dc.getName());
            }
        }
    }

    @Override
    protected String[] getHeaders() {
        List<String> headers = new ArrayList<>();

        // Static headers
        headers.add("makespan_seconds");
        headers.add("num_datacenters");
        headers.add("num_hosts");
        headers.add("num_users");
        headers.add("num_total_tasks");
        headers.add("num_gpu_tasks");
        headers.add("num_cpu_tasks");
        headers.add("num_mixed_tasks");
        headers.add("avg_waiting_time_seconds");
        headers.add("num_completed_tasks");
        headers.add("num_failed_tasks");
        headers.add("throughput_tasks_per_second");

        // Dynamic datacenter headers
        for (String dcName : datacenterNames) {
            String safeName = sanitizeColumnName(dcName);
            headers.add("DC_" + safeName + "_energy_kwh");
            headers.add("DC_" + safeName + "_peak_power_watts");
        }

        return headers.toArray(new String[0]);
    }

    @Override
    protected void writeDataRows(CSVWriter writer, SimulationContext context, SimulationSummary summary) throws IOException {
        List<Object> values = new ArrayList<>();

        // Makespan
        long makespan = 0;
        if (summary != null && summary.getPerformance() != null) {
            makespan = summary.getPerformance().makespanSeconds;
        }
        values.add(makespan);

        // Infrastructure counts
        int numDatacenters = 0;
        int numHosts = 0;
        int numUsers = 0;

        if (summary != null && summary.getInfrastructure() != null) {
            numDatacenters = summary.getInfrastructure().datacenterCount;
            numHosts = summary.getInfrastructure().hostCount;
            numUsers = summary.getInfrastructure().userCount;
        } else if (context != null) {
            numDatacenters = context.getDatacenters() != null ? context.getDatacenters().size() : 0;
            numHosts = context.getHosts() != null ? context.getHosts().size() : 0;
            numUsers = context.getUsers() != null ? context.getUsers().size() : 0;
        }

        values.add(numDatacenters);
        values.add(numHosts);
        values.add(numUsers);

        // Task counts
        int totalTasks = 0;
        int gpuTasks = 0;
        int cpuTasks = 0;
        int completedTasks = 0;
        int failedTasks = 0;
        double avgWaitingTime = 0.0;
        double throughput = 0.0;

        List<Task> tasks = context.getTasks();
        if (tasks != null) {
            totalTasks = tasks.size();

            for (Task task : tasks) {
                if (isGpuTask(task.getWorkloadType())) {
                    gpuTasks++;
                } else {
                    cpuTasks++;
                }

                if (task.isCompleted()) {
                    completedTasks++;
                } else if (task.isAssigned() && !task.isCompleted()) {
                    failedTasks++;
                }
            }

            // Calculate average waiting time for completed tasks
            long totalWaitingTime = 0;
            int waitingCount = 0;
            for (Task task : tasks) {
                if (task.isCompleted() && task.getWaitingTime() != null) {
                    totalWaitingTime += task.getWaitingTime();
                    waitingCount++;
                }
            }
            if (waitingCount > 0) {
                avgWaitingTime = (double) totalWaitingTime / waitingCount;
            }
        }

        // Get throughput from summary
        if (summary != null && summary.getPerformance() != null) {
            throughput = summary.getPerformance().throughputTasksPerSecond;
            // Use summary's avg waiting time if available
            avgWaitingTime = summary.getPerformance().avgWaitingTimeSeconds;
        }

        values.add(totalTasks);
        values.add(gpuTasks);
        values.add(cpuTasks);
        values.add(0); // num_mixed_tasks is always 0 per user's specification
        values.add(formatDouble(avgWaitingTime, 2));
        values.add(completedTasks);
        values.add(failedTasks);
        values.add(formatDouble(throughput, 6));

        // Per-datacenter energy and peak power
        List<CloudDatacenter> datacenters = context.getDatacenters();
        List<SimulationSummary.DatacenterSummary> dcSummaries =
            summary != null ? summary.getDatacenters() : null;

        for (int i = 0; i < datacenterNames.size(); i++) {
            double energyKwh = 0.0;
            double peakPowerWatts = 0.0;

            // Try to get from datacenter summaries first
            if (dcSummaries != null && i < dcSummaries.size()) {
                SimulationSummary.DatacenterSummary dcSummary = dcSummaries.get(i);
                energyKwh = dcSummary.energyKWh;
                peakPowerWatts = dcSummary.peakPowerWatts;
            } else if (datacenters != null && i < datacenters.size()) {
                // Fallback to direct datacenter query
                CloudDatacenter dc = datacenters.get(i);
                energyKwh = dc.getTotalEnergyConsumedKWh();
                peakPowerWatts = dc.getTotalMomentaryPowerDraw();
            }

            values.add(formatDouble(energyKwh, 6));
            values.add(formatDouble(peakPowerWatts, 2));
        }

        writer.writeRow(values.toArray());
        incrementRowCount();
    }

    /**
     * Determines if a workload type uses GPU resources.
     * GPU tasks: FURMARK, LLM_GPU, IMAGE_GEN_GPU (any GPU utilization > 0)
     */
    private boolean isGpuTask(WorkloadType workloadType) {
        if (workloadType == null) {
            return false;
        }

        switch (workloadType) {
            case FURMARK:
            case LLM_GPU:
            case IMAGE_GEN_GPU:
                return true;
            default:
                return false;
        }
    }

    /**
     * Sanitizes a datacenter name for use as a column name.
     * Replaces spaces and special characters with underscores.
     */
    private String sanitizeColumnName(String name) {
        if (name == null) {
            return "unknown";
        }
        return name.replaceAll("[^a-zA-Z0-9]", "_");
    }

    /**
     * Gets the full path where the report was/will be saved.
     */
    public Path getReportPath(SimulationSummary summary) {
        return Paths.get(outputDirectory).resolve(generateFilename(summary));
    }
}
