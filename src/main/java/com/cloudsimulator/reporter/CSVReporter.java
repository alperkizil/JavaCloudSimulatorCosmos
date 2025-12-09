package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.SimulationSummary;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for CSV report generators.
 * Each implementation generates a specific type of report from simulation data.
 */
public interface CSVReporter {

    /**
     * Gets the report type identifier.
     * @return report type name (e.g., "summary", "tasks", "hosts")
     */
    String getReportType();

    /**
     * Gets the default filename for this report (without path).
     * @return default filename (e.g., "summary.csv")
     */
    String getDefaultFilename();

    /**
     * Generates the CSV report and writes it to the specified path.
     * Uses streaming writes for memory efficiency with large datasets.
     *
     * @param outputPath the path to write the CSV file
     * @param context the simulation context with raw data
     * @param summary the simulation summary (may be null)
     * @throws IOException if writing fails
     */
    void generateReport(Path outputPath, SimulationContext context, SimulationSummary summary) throws IOException;

    /**
     * Returns the number of rows written in the last report generation.
     * @return row count (excluding header)
     */
    int getRowCount();

    /**
     * Returns the number of bytes written in the last report generation.
     * @return byte count
     */
    long getBytesWritten();
}
