package com.cloudsimulator.config;

/**
 * Interface for parsing configuration files.
 * Implementations can support different file formats (e.g., .cosc, JSON, YAML).
 */
public interface ConfigParser {
    /**
     * Parses a configuration file and returns an ExperimentConfiguration.
     *
     * @param configFilePath Path to the configuration file
     * @return Parsed configuration
     * @throws ConfigurationException if parsing fails
     */
    ExperimentConfiguration parse(String configFilePath) throws ConfigurationException;

    /**
     * Exception thrown when configuration parsing fails.
     */
    class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
