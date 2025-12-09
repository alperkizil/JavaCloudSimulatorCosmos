package com.cloudsimulator.reporter;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.SimulationSummary;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Abstract base class for CSV reporters with streaming write support.
 * Provides common CSV utilities and handles file writing efficiently.
 *
 * Subclasses must implement:
 * - getReportType(): Return the report type identifier
 * - getDefaultFilename(): Return the default filename
 * - getHeaders(): Return CSV column headers
 * - writeDataRows(): Write data rows using the streaming writer
 */
public abstract class AbstractCSVReporter implements CSVReporter {

    // Statistics
    protected int rowCount;
    protected long bytesWritten;

    // Buffer size for streaming (8KB)
    private static final int BUFFER_SIZE = 8192;

    /**
     * CSV writer wrapper that tracks bytes written.
     */
    protected class CSVWriter implements AutoCloseable {
        private final BufferedWriter writer;
        private long bytes;

        public CSVWriter(Path path) throws IOException {
            this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            this.bytes = 0;
        }

        /**
         * Writes a single CSV row.
         * @param values the values to write (will be escaped)
         */
        public void writeRow(Object... values) throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(escapeCSV(values[i]));
            }
            sb.append("\n");
            String row = sb.toString();
            writer.write(row);
            bytes += row.getBytes(StandardCharsets.UTF_8).length;
        }

        /**
         * Writes a pre-formatted CSV line (with newline).
         */
        public void writeLine(String line) throws IOException {
            String formatted = line.endsWith("\n") ? line : line + "\n";
            writer.write(formatted);
            bytes += formatted.getBytes(StandardCharsets.UTF_8).length;
        }

        /**
         * Flushes the writer buffer.
         */
        public void flush() throws IOException {
            writer.flush();
        }

        public long getBytesWritten() {
            return bytes;
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }

    @Override
    public void generateReport(Path outputPath, SimulationContext context, SimulationSummary summary) throws IOException {
        rowCount = 0;
        bytesWritten = 0;

        try (CSVWriter writer = new CSVWriter(outputPath)) {
            // Write header row
            String[] headers = getHeaders();
            writer.writeRow((Object[]) headers);

            // Write data rows (streaming)
            writeDataRows(writer, context, summary);

            writer.flush();
            bytesWritten = writer.getBytesWritten();
        }
    }

    /**
     * Returns the CSV column headers.
     * @return array of header names
     */
    protected abstract String[] getHeaders();

    /**
     * Writes data rows to the CSV writer.
     * Implementations should use streaming (row by row) for large datasets.
     *
     * @param writer the CSV writer
     * @param context the simulation context
     * @param summary the simulation summary (may be null)
     */
    protected abstract void writeDataRows(CSVWriter writer, SimulationContext context, SimulationSummary summary) throws IOException;

    /**
     * Escapes a value for CSV format.
     * - Null values become empty string
     * - Values containing comma, quote, or newline are quoted
     * - Quotes within values are doubled
     */
    protected String escapeCSV(Object value) {
        if (value == null) {
            return "";
        }

        String str = value.toString();

        // Check if escaping is needed
        boolean needsQuotes = str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r");

        if (needsQuotes) {
            // Escape quotes by doubling them
            str = str.replace("\"", "\"\"");
            return "\"" + str + "\"";
        }

        return str;
    }

    /**
     * Formats a double value with specified decimal places.
     */
    protected String formatDouble(double value, int decimals) {
        return String.format("%." + decimals + "f", value);
    }

    /**
     * Formats a percentage value (0-100) with 2 decimal places.
     */
    protected String formatPercent(double value) {
        return String.format("%.2f", value);
    }

    /**
     * Increments the row counter.
     */
    protected void incrementRowCount() {
        rowCount++;
    }

    @Override
    public int getRowCount() {
        return rowCount;
    }

    @Override
    public long getBytesWritten() {
        return bytesWritten;
    }
}
