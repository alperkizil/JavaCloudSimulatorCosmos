package com.cloudsimulator.engine;

import com.cloudsimulator.config.ConfigParser;
import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.FileConfigParser;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.MultiObjectiveTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.steps.TaskAssignmentStep;
import com.cloudsimulator.utils.RandomGenerator;
import com.cloudsimulator.utils.SimulationLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // ==================== Multi-Objective Simulation Support ====================

    private MultiObjectiveSimulationResult lastMultiObjectiveResult;

    /**
     * Checks if the simulation uses a multi-objective task scheduling strategy.
     *
     * @return true if a multi-objective strategy is configured
     */
    public boolean isMultiObjectiveSimulation() {
        TaskAssignmentStep taskStep = findTaskAssignmentStep();
        if (taskStep == null) return false;

        TaskAssignmentStrategy strategy = taskStep.getStrategy();
        return strategy instanceof MultiObjectiveTaskSchedulingStrategy;
    }

    /**
     * Finds the TaskAssignmentStep in the step pipeline.
     *
     * @return TaskAssignmentStep or null if not found
     */
    private TaskAssignmentStep findTaskAssignmentStep() {
        for (SimulationStep step : steps) {
            if (step instanceof TaskAssignmentStep) {
                return (TaskAssignmentStep) step;
            }
        }
        return null;
    }

    /**
     * Finds the index of TaskAssignmentStep in the pipeline.
     *
     * @return Index or -1 if not found
     */
    private int findTaskAssignmentStepIndex() {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i) instanceof TaskAssignmentStep) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Runs simulation for all solutions in a Pareto front.
     *
     * This method:
     * 1. Executes setup steps (initialization through VM placement)
     * 2. Runs multi-objective optimization to get the Pareto front
     * 3. For each solution in the Pareto front:
     *    - Resets execution state
     *    - Applies the solution's task assignments
     *    - Runs execution and analysis steps
     *    - Collects results
     *
     * @return MultiObjectiveSimulationResult containing all solution results
     * @throws IllegalStateException if not configured for multi-objective optimization
     */
    public MultiObjectiveSimulationResult runMultiObjective() {
        logger.info("========================================");
        logger.info("Starting Multi-Objective Simulation");
        logger.info("Random Seed: " + randomSeed);
        logger.info("========================================");

        long totalStartTime = System.currentTimeMillis();

        // Validate configuration
        TaskAssignmentStep taskStep = findTaskAssignmentStep();
        if (taskStep == null) {
            throw new IllegalStateException("No TaskAssignmentStep found in pipeline");
        }

        TaskAssignmentStrategy strategy = taskStep.getStrategy();
        if (!(strategy instanceof MultiObjectiveTaskSchedulingStrategy)) {
            throw new IllegalStateException(
                "TaskAssignmentStrategy is not multi-objective. Use run() for single-objective simulation.");
        }

        MultiObjectiveTaskSchedulingStrategy moStrategy =
            (MultiObjectiveTaskSchedulingStrategy) strategy;

        int taskStepIndex = findTaskAssignmentStepIndex();

        try {
            // Initialize random generator
            RandomGenerator.initialize(randomSeed);

            // ===== Phase 1: Setup (Steps 1 through TaskAssignment-1) =====
            logger.info("Phase 1: Running setup steps...");
            for (int i = 0; i < taskStepIndex; i++) {
                SimulationStep step = steps.get(i);
                logger.info(String.format("  Step %d/%d: %s", i + 1, steps.size(), step.getStepName()));
                step.execute(context);
            }

            // ===== Phase 2: Run Multi-Objective Optimization =====
            logger.info("Phase 2: Running multi-objective optimization...");

            // Get tasks and VMs for optimization
            List<Task> tasks = context.getTasks().stream()
                .filter(task -> !task.isAssigned())
                .collect(Collectors.toList());
            List<VM> vms = context.getVms().stream()
                .filter(VM::isAssignedToHost)
                .collect(Collectors.toList());

            // Run optimization
            ParetoFront paretoFront = moStrategy.optimizeAndGetParetoFront(tasks, vms);

            logger.info("Pareto front contains " + paretoFront.size() + " solutions");

            // ===== Phase 3: Simulate Each Solution =====
            logger.info("Phase 3: Simulating each Pareto front solution...");

            MultiObjectiveSimulationResult moResult = new MultiObjectiveSimulationResult(
                paretoFront,
                moStrategy.getObjectiveNames()
            );

            List<SchedulingSolution> solutions = paretoFront.getSolutions();

            for (int solIdx = 0; solIdx < solutions.size(); solIdx++) {
                SchedulingSolution solution = solutions.get(solIdx);

                logger.info(String.format("  Simulating solution %d/%d (objectives: %s)",
                    solIdx + 1, solutions.size(), formatObjectives(solution)));

                long solStartTime = System.currentTimeMillis();

                // Reset context for this solution
                context.resetForRescheduling();

                // Apply this solution's task assignments
                long currentTime = context.getCurrentTime();
                Map<Task, VM> assignments = moStrategy.applySolution(
                    solution, tasks, vms, currentTime);

                logger.debug("  Applied " + assignments.size() + " task assignments");

                // Execute post-assignment steps (execution, metrics, etc.)
                for (int i = taskStepIndex + 1; i < steps.size(); i++) {
                    SimulationStep step = steps.get(i);
                    step.execute(context);
                }

                long solEndTime = System.currentTimeMillis();

                // Collect results for this solution
                MultiObjectiveSimulationResult.SolutionSimulationResult solResult =
                    new MultiObjectiveSimulationResult.SolutionSimulationResult(
                        solIdx,
                        solution,
                        solution.getObjectiveValues(),
                        context.getSimulationSummary(),
                        solEndTime - solStartTime
                    );

                moResult.addSolutionResult(solResult);

                logger.info(String.format("  Solution %d: simulated makespan=%ds, energy=%.4f kWh",
                    solIdx + 1,
                    solResult.getSimulatedMakespan(),
                    solResult.getSimulatedEnergyKWh()));
            }

            long totalEndTime = System.currentTimeMillis();
            long totalDuration = totalEndTime - totalStartTime;

            logger.info("========================================");
            logger.info("Multi-Objective Simulation Complete");
            logger.info("Solutions simulated: " + moResult.getNumSolutions());
            logger.info("Total time: " + totalDuration + " ms");
            logger.info("========================================");

            lastMultiObjectiveResult = moResult;
            return moResult;

        } catch (Exception e) {
            logger.error("Multi-objective simulation failed: " + e.getMessage(), e);
            throw new RuntimeException("Multi-objective simulation failed", e);
        }
    }

    /**
     * Formats objective values for logging.
     */
    private String formatObjectives(SchedulingSolution solution) {
        double[] values = solution.getObjectiveValues();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.2f", values[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Gets the result from the last multi-objective simulation run.
     *
     * @return Last multi-objective result, or null if not run
     */
    public MultiObjectiveSimulationResult getLastMultiObjectiveResult() {
        return lastMultiObjectiveResult;
    }

    /**
     * Gets all steps in the pipeline.
     *
     * @return List of simulation steps
     */
    public List<SimulationStep> getSteps() {
        return steps;
    }
}
