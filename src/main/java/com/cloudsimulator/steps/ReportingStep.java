package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.reporter.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ReportingStep generates CSV reports from simulation results.
 * This is the tenth and final step in the simulation pipeline.
 *
 * Reports are organized in experiment folders with the naming format:
 * experiment_{DATE}_{TIME}_{UNIQUEID}/
 *
 * Available reports:
 * - summary.csv: One-row overview of the entire simulation
 * - datacenters.csv: Per-datacenter metrics
 * - hosts.csv: Per-host metrics
 * - vms.csv: Per-VM metrics
 * - tasks.csv: Per-task details (streaming for large datasets)
 * - users.csv: Per-user metrics
 *
 * Usage:
 * <pre>
 * ReportingStep step = new ReportingStep();
 * step.setBaseOutputDirectory("./reports");
 * step.setCustomPrefix("my_experiment");  // Optional
 * step.enableReport(ReportType.SUMMARY);
 * step.enableReport(ReportType.TASKS);
 * step.execute(context);
 *
 * System.out.println("Reports saved to: " + step.getOutputDirectory());
 * </pre>
 */
public class ReportingStep implements SimulationStep {

    /**
     * Available report types.
     */
    public enum ReportType {
        SUMMARY("summary"),
        DATACENTERS("datacenters"),
        HOSTS("hosts"),
        VMS("vms"),
        TASKS("tasks"),
        USERS("users");

        private final String name;

        ReportType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    // Configuration
    private String baseOutputDirectory = "./reports";
    private String customPrefix = null;
    private Set<ReportType> enabledReports;
    private boolean useSimulationIdInFilenames = true;

    // Results
    private Path outputDirectory;
    private Map<ReportType, ReportResult> results;
    private long totalBytesWritten;
    private int totalFilesGenerated;

    // Timing
    private long stepStartTimeMs;
    private long stepEndTimeMs;

    // Reporters
    private Map<ReportType, CSVReporter> reporters;

    /**
     * Result container for each report.
     */
    public static class ReportResult {
        public String filename;
        public Path path;
        public int rowCount;
        public long bytesWritten;
        public boolean success;
        public String error;

        public ReportResult(String filename, Path path) {
            this.filename = filename;
            this.path = path;
            this.success = false;
        }
    }

    public ReportingStep() {
        this.enabledReports = new HashSet<>();
        this.results = new LinkedHashMap<>();

        // Enable all reports by default
        for (ReportType type : ReportType.values()) {
            enabledReports.add(type);
        }

        // Initialize reporters
        reporters = new HashMap<>();
        reporters.put(ReportType.SUMMARY, new SummaryReporter());
        reporters.put(ReportType.DATACENTERS, new DatacenterReporter());
        reporters.put(ReportType.HOSTS, new HostReporter());
        reporters.put(ReportType.VMS, new VMReporter());
        reporters.put(ReportType.TASKS, new TaskReporter());
        reporters.put(ReportType.USERS, new UserReporter());
    }

    @Override
    public void execute(SimulationContext context) {
        stepStartTimeMs = System.currentTimeMillis();
        totalBytesWritten = 0;
        totalFilesGenerated = 0;
        results.clear();

        logInfo("Generating CSV reports...");

        try {
            // Create experiment output directory
            outputDirectory = createExperimentDirectory();
            logInfo("Output directory: " + outputDirectory.toAbsolutePath());

            // Get simulation summary if available
            SimulationSummary summary = getSimulationSummary(context);
            String simId = summary != null ? summary.getSimulationId() : generateSimulationId();

            // Generate each enabled report
            for (ReportType type : ReportType.values()) {
                if (!enabledReports.contains(type)) {
                    logInfo("  Skipping " + type.getName() + " report (disabled)");
                    continue;
                }

                generateReport(type, context, summary, simId);
            }

            stepEndTimeMs = System.currentTimeMillis();

            // Log summary
            logSummary();

            // Record metrics
            recordMetrics(context);

        } catch (IOException e) {
            logError("Failed to create output directory: " + e.getMessage());
            throw new RuntimeException("ReportingStep failed", e);
        }
    }

    /**
     * Creates the experiment output directory with naming format:
     * experiment_{DATE}_{TIME}_{UNIQUEID}/
     */
    private Path createExperimentDirectory() throws IOException {
        // Format: experiment_YYYYMMDD_HHmmss_XXXXXX
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HHmmss");

        String date = now.format(dateFormatter);
        String time = now.format(timeFormatter);
        String uniqueId = UUID.randomUUID().toString().substring(0, 6);

        String folderName;
        if (customPrefix != null && !customPrefix.isEmpty()) {
            folderName = String.format("%s_%s_%s_%s", customPrefix, date, time, uniqueId);
        } else {
            folderName = String.format("experiment_%s_%s_%s", date, time, uniqueId);
        }

        Path baseDir = Paths.get(baseOutputDirectory);
        Path experimentDir = baseDir.resolve(folderName);

        Files.createDirectories(experimentDir);
        return experimentDir;
    }

    /**
     * Generates a single report.
     */
    private void generateReport(ReportType type, SimulationContext context,
                                SimulationSummary summary, String simId) {
        CSVReporter reporter = reporters.get(type);
        if (reporter == null) {
            logError("No reporter found for type: " + type);
            return;
        }

        // Build filename
        String filename;
        if (useSimulationIdInFilenames && simId != null) {
            filename = simId + "_" + reporter.getDefaultFilename();
        } else {
            filename = reporter.getDefaultFilename();
        }

        Path filePath = outputDirectory.resolve(filename);
        ReportResult result = new ReportResult(filename, filePath);
        results.put(type, result);

        try {
            logInfo("  Generating " + type.getName() + " report...");

            reporter.generateReport(filePath, context, summary);

            result.rowCount = reporter.getRowCount();
            result.bytesWritten = reporter.getBytesWritten();
            result.success = true;

            totalBytesWritten += result.bytesWritten;
            totalFilesGenerated++;

            logInfo("    -> " + filename + " (" + result.rowCount + " rows, " +
                    formatBytes(result.bytesWritten) + ")");

        } catch (IOException e) {
            result.error = e.getMessage();
            result.success = false;
            logError("    -> Failed: " + e.getMessage());
        }
    }

    /**
     * Gets SimulationSummary from context if MetricsCollectionStep was executed.
     */
    private SimulationSummary getSimulationSummary(SimulationContext context) {
        // Try to get summary from raw metrics (if MetricsCollectionStep stored it)
        Object summaryGenerated = context.getMetric("metricsCollection.summaryGenerated");
        if (summaryGenerated != null && (Boolean) summaryGenerated) {
            // MetricsCollectionStep was executed, but we need to get the actual summary
            // For now, we'll construct what we can from context metrics
            // A better approach would be to store the summary object in context

            // Return null to let reporters use context directly
            // In a real implementation, MetricsCollectionStep should store the summary
            return null;
        }
        return null;
    }

    /**
     * Generates a simulation ID if none is available.
     */
    private String generateSimulationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Logs a summary of report generation.
     */
    private void logSummary() {
        long durationMs = stepEndTimeMs - stepStartTimeMs;

        logInfo("Report Generation Complete");
        logInfo(String.format("  Duration: %d ms", durationMs));
        logInfo(String.format("  Files generated: %d", totalFilesGenerated));
        logInfo(String.format("  Total size: %s", formatBytes(totalBytesWritten)));
        logInfo(String.format("  Output directory: %s", outputDirectory.toAbsolutePath()));

        // List generated files
        logInfo("  Generated files:");
        for (Map.Entry<ReportType, ReportResult> entry : results.entrySet()) {
            ReportResult result = entry.getValue();
            if (result.success) {
                logInfo(String.format("    - %s (%d rows)", result.filename, result.rowCount));
            } else {
                logInfo(String.format("    - %s (FAILED: %s)", result.filename, result.error));
            }
        }
    }

    /**
     * Records reporting metrics to context.
     */
    private void recordMetrics(SimulationContext context) {
        context.recordMetric("reporting.filesGenerated", totalFilesGenerated);
        context.recordMetric("reporting.totalBytesWritten", totalBytesWritten);
        context.recordMetric("reporting.outputDirectory", outputDirectory.toAbsolutePath().toString());
        context.recordMetric("reporting.durationMs", stepEndTimeMs - stepStartTimeMs);

        for (Map.Entry<ReportType, ReportResult> entry : results.entrySet()) {
            String prefix = "reporting." + entry.getKey().getName();
            ReportResult result = entry.getValue();
            context.recordMetric(prefix + ".success", result.success);
            context.recordMetric(prefix + ".rowCount", result.rowCount);
            context.recordMetric(prefix + ".bytesWritten", result.bytesWritten);
        }
    }

    /**
     * Formats bytes into human-readable format.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    private void logInfo(String message) {
        System.out.println("[INFO] ReportingStep: " + message);
    }

    private void logError(String message) {
        System.err.println("[ERROR] ReportingStep: " + message);
    }

    @Override
    public String getStepName() {
        return "CSV Reporting";
    }

    // Configuration methods

    /**
     * Sets the base output directory for reports.
     * Default: ./reports
     */
    public void setBaseOutputDirectory(String directory) {
        this.baseOutputDirectory = directory;
    }

    /**
     * Sets a custom prefix for the experiment folder name.
     * Format will be: {prefix}_{DATE}_{TIME}_{UNIQUEID}/
     * If not set, default prefix is "experiment".
     */
    public void setCustomPrefix(String prefix) {
        this.customPrefix = prefix;
    }

    /**
     * Enables a specific report type.
     */
    public void enableReport(ReportType type) {
        enabledReports.add(type);
    }

    /**
     * Disables a specific report type.
     */
    public void disableReport(ReportType type) {
        enabledReports.remove(type);
    }

    /**
     * Enables all report types.
     */
    public void enableAllReports() {
        for (ReportType type : ReportType.values()) {
            enabledReports.add(type);
        }
    }

    /**
     * Disables all report types.
     */
    public void disableAllReports() {
        enabledReports.clear();
    }

    /**
     * Sets whether to include simulation ID in filenames.
     * Default: true
     */
    public void setUseSimulationIdInFilenames(boolean use) {
        this.useSimulationIdInFilenames = use;
    }

    // Getters

    /**
     * Gets the output directory where reports were saved.
     */
    public Path getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Gets the results for all generated reports.
     */
    public Map<ReportType, ReportResult> getResults() {
        return results;
    }

    /**
     * Gets the result for a specific report type.
     */
    public ReportResult getResult(ReportType type) {
        return results.get(type);
    }

    /**
     * Gets total bytes written across all reports.
     */
    public long getTotalBytesWritten() {
        return totalBytesWritten;
    }

    /**
     * Gets total number of files generated.
     */
    public int getTotalFilesGenerated() {
        return totalFilesGenerated;
    }

    /**
     * Gets the set of enabled report types.
     */
    public Set<ReportType> getEnabledReports() {
        return new HashSet<>(enabledReports);
    }

    /**
     * Checks if a report type is enabled.
     */
    public boolean isReportEnabled(ReportType type) {
        return enabledReports.contains(type);
    }

    /**
     * Gets the step duration in milliseconds.
     */
    public long getStepDurationMs() {
        return stepEndTimeMs - stepStartTimeMs;
    }
}
