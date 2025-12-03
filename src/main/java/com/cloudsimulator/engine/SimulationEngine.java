package com.cloudsimulator.engine;

import com.cloudsimulator.config.ConfigParser;
import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.utils.RandomGenerator;
import com.cloudsimulator.utils.SimulationLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Main simulation engine that orchestrates the entire simulation process.
 * Uses Template Method pattern for the simulation flow.
 * Supports random seed for experiment repeatability.
 */
public class SimulationEngine {
    private SimulationContext context;
    private ExperimentConfiguration configuration;
    private List<SimulationStep> steps;
    private SimulationLogger logger;
    private ConfigParser configParser;
    private long randomSeed;

    public SimulationEngine() {
        this.context = new SimulationContext();
        this.steps = new ArrayList<>();
        this.logger = new SimulationLogger();
        this.configParser = new FileConfigParser();
        this.randomSeed = System.currentTimeMillis(); // Default seed
    }

    /**
     * Configure the simulation from a config file.
     *
     * @param configFilePath Path to the .cosc configuration file
     */
    public void configure(String configFilePath) {
        logger.info("Loading configuration from: " + configFilePath);
        this.configuration = configParser.parse(configFilePath);
        this.randomSeed = configuration.getRandomSeed();

        // Initialize random generator with seed
        RandomGenerator.initialize(randomSeed);

        logger.info("Configuration loaded successfully");
        logger.info("Random seed: " + randomSeed);
        logger.info("Datacenters: " + configuration.getDatacenterConfigs().size());
        logger.info("Hosts: " + configuration.getHostConfigs().size());
        logger.info("Users: " + configuration.getUserConfigs().size());
        logger.info("VMs: " + configuration.getVmConfigs().size());
        logger.info("Tasks: " + configuration.getTaskConfigs().size());
    }

    /**
     * Configure with an existing ExperimentConfiguration object.
     *
     * @param config ExperimentConfiguration object
     */
    public void configure(ExperimentConfiguration config) {
        logger.info("Loading configuration from object");
        this.configuration = config.clone(); // Deep copy
        this.randomSeed = configuration.getRandomSeed();

        // Initialize random generator with seed
        RandomGenerator.initialize(randomSeed);

        logger.info("Configuration loaded. Random seed: " + randomSeed);
    }

    /**
     * Sets the random seed for experiment repeatability.
     *
     * @param seed Random seed
     */
    public void setRandomSeed(long seed) {
        this.randomSeed = seed;
        if (this.configuration != null) {
            this.configuration.setRandomSeed(seed);
        }
        RandomGenerator.initialize(seed);
        logger.info("Random seed set to: " + seed);
    }

    /**
     * Gets the current random seed.
     */
    public long getRandomSeed() {
        return randomSeed;
    }

    /**
     * Adds a simulation step to the execution pipeline.
     *
     * @param step Simulation step to add
     */
    public void addStep(SimulationStep step) {
        this.steps.add(step);
        logger.debug("Added step: " + step.getStepName());
    }

    /**
     * Runs the simulation by executing all steps in order.
     */
    public void run() {
        logger.info("========================================");
        logger.info("Starting Simulation");
        logger.info("Random Seed: " + randomSeed);
        logger.info("========================================");

        long startTime = System.currentTimeMillis();

        try {
            // Initialize random generator
            RandomGenerator.initialize(randomSeed);

            // Execute all steps
            executeSteps();

            logger.info("Simulation completed successfully");
        } catch (Exception e) {
            logger.error("Simulation failed: " + e.getMessage(), e);
            throw new RuntimeException("Simulation failed", e);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        logger.info("========================================");
        logger.info("Simulation finished in " + duration + " ms");
        logger.info("========================================");
    }

    /**
     * Executes all simulation steps in order.
     */
    private void executeSteps() {
        for (int i = 0; i < steps.size(); i++) {
            SimulationStep step = steps.get(i);
            logger.info(String.format("Step %d/%d: %s", i + 1, steps.size(), step.getStepName()));

            long stepStart = System.currentTimeMillis();
            step.execute(context);
            long stepEnd = System.currentTimeMillis();

            logger.debug(String.format("Step completed in %d ms", stepEnd - stepStart));
        }
    }

    /**
     * Gets the simulation context (for inspection/testing).
     */
    public SimulationContext getContext() {
        return context;
    }

    /**
     * Gets the experiment configuration (for inspection/testing).
     */
    public ExperimentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the simulation context (for testing).
     */
    public void setContext(SimulationContext context) {
        this.context = context;
    }

    /**
     * Enables or disables debug logging.
     */
    public void setDebugEnabled(boolean enabled) {
        logger.setDebugEnabled(enabled);
    }

    /**
     * Gets the logger instance.
     */
    public SimulationLogger getLogger() {
        return logger;
    }

    /**
     * Sets a custom logger.
     */
    public void setLogger(SimulationLogger logger) {
        this.logger = logger;
    }
}
