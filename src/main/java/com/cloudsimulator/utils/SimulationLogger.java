package com.cloudsimulator.utils;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logger for simulation events with support for different log levels.
 */
public class SimulationLogger {
    private boolean debugEnabled;
    private PrintStream outputStream;
    private SimpleDateFormat dateFormat;

    public SimulationLogger() {
        this(System.out, false);
    }

    public SimulationLogger(PrintStream outputStream, boolean debugEnabled) {
        this.outputStream = outputStream;
        this.debugEnabled = debugEnabled;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    /**
     * Logs an info message.
     */
    public void info(String message) {
        log("INFO", message);
    }

    /**
     * Logs a debug message (only if debug is enabled).
     */
    public void debug(String message) {
        if (debugEnabled) {
            log("DEBUG", message);
        }
    }

    /**
     * Logs a warning message.
     */
    public void warn(String message) {
        log("WARN", message);
    }

    /**
     * Logs an error message.
     */
    public void error(String message) {
        log("ERROR", message);
    }

    /**
     * Logs an error message with exception.
     */
    public void error(String message, Throwable throwable) {
        log("ERROR", message);
        if (throwable != null) {
            throwable.printStackTrace(outputStream);
        }
    }

    private void log(String level, String message) {
        String timestamp = dateFormat.format(new Date());
        outputStream.println(String.format("[%s] [%s] %s", timestamp, level, message));
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public void setOutputStream(PrintStream outputStream) {
        this.outputStream = outputStream;
    }
}
