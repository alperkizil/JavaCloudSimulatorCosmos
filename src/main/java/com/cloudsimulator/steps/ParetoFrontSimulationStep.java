package com.cloudsimulator.steps;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationSnapshot;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Simulation step that runs all solutions in a Pareto front through the full simulation pipeline.
 *
 * This step spawns separate processes for each Pareto solution to ensure:
 * - Complete isolation between simulations (no shared state)
 * - Deterministic results (each process has its own RandomGenerator)
 * - True parallelism (up to maxConcurrentProcesses)
 *
 * For each solution, the subprocess runs steps 6-10:
 * - VMExecutionStep
 * - TaskExecutionStep
 * - EnergyCalculationStep
 * - MetricsCollectionStep
 * - ReportingStep
 *
 * Results are collected and a comparison report is generated showing
 * estimated vs actual objectives for each solution.
 */
public class ParetoFrontSimulationStep implements SimulationStep {

    private final ParetoFront paretoFront;
    private final List<SchedulingObjective> objectives;
    private final ExperimentConfiguration config;
    private final int maxConcurrentProcesses;
    private final String baseOutputDirectory;
    private final long seed;

    private List<ParetoSimulationResult> results = new ArrayList<>();
    private String outputDirectory;

    /**
     * Creates a new Pareto front simulation step.
     *
     * @param paretoFront           The Pareto front from NSGA-II optimization
     * @param objectives            The objectives used in optimization
     * @param config                The experiment configuration
     * @param seed                  Random seed for reproducibility
     * @param maxConcurrentProcesses Maximum number of parallel processes
     * @param baseOutputDirectory   Base directory for output files
     */
    public ParetoFrontSimulationStep(ParetoFront paretoFront,
                                      List<SchedulingObjective> objectives,
                                      ExperimentConfiguration config,
                                      long seed,
                                      int maxConcurrentProcesses,
                                      String baseOutputDirectory) {
        this.paretoFront = paretoFront;
        this.objectives = objectives;
        this.config = config;
        this.seed = seed;
        this.maxConcurrentProcesses = maxConcurrentProcesses;
        this.baseOutputDirectory = baseOutputDirectory;
    }

    /**
     * Creates a step using available processors for parallelism.
     */
    public ParetoFrontSimulationStep(ParetoFront paretoFront,
                                      List<SchedulingObjective> objectives,
                                      ExperimentConfiguration config,
                                      long seed,
                                      String baseOutputDirectory) {
        this(paretoFront, objectives, config, seed,
             Runtime.getRuntime().availableProcessors(),
             baseOutputDirectory);
    }

    @Override
    public void execute(SimulationContext baseContext) {
        List<SchedulingSolution> solutions = paretoFront.getSolutions();

        if (solutions.isEmpty()) {
            System.out.println("[ParetoFrontSimulation] No solutions to simulate");
            return;
        }

        try {
            // Create timestamped output directory
            String timestamp = String.valueOf(System.currentTimeMillis());
            outputDirectory = baseOutputDirectory + "/pareto_" + timestamp;
            Files.createDirectories(Paths.get(outputDirectory));

            // Create temp directory for snapshot
            String tempDir = Files.createTempDirectory("pareto_sim_").toString();
            String snapshotFile = tempDir + "/snapshot.ser";

            System.out.println("========================================");
            System.out.println("PARETO FRONT SIMULATION");
            System.out.println("========================================");
            System.out.println("Solutions to simulate: " + solutions.size());
            System.out.println("Max concurrent processes: " + maxConcurrentProcesses);
            System.out.println("Output directory: " + outputDirectory);
            System.out.println("Random seed: " + seed);
            System.out.println("========================================");

            // Save snapshot for subprocesses
            System.out.println("Creating simulation snapshot...");
            SimulationSnapshot snapshot = new SimulationSnapshot(
                baseContext, config, paretoFront, objectives
            );
            snapshot.saveToFile(snapshotFile);
            System.out.println("Snapshot saved to: " + snapshotFile);

            // Spawn processes with controlled concurrency
            List<ProcessInfo> runningProcesses = new ArrayList<>();
            Queue<Integer> pendingSolutions = new LinkedList<>();
            for (int i = 0; i < solutions.size(); i++) {
                pendingSolutions.add(i);
            }

            int completedCount = 0;
            int failedCount = 0;

            while (!pendingSolutions.isEmpty() || !runningProcesses.isEmpty()) {
                // Launch new processes up to limit
                while (!pendingSolutions.isEmpty() &&
                       runningProcesses.size() < maxConcurrentProcesses) {
                    int solutionIndex = pendingSolutions.poll();
                    Process p = launchSolutionProcess(snapshotFile, solutionIndex, outputDirectory);
                    runningProcesses.add(new ProcessInfo(solutionIndex, p));
                    System.out.println("Launched process for solution " + solutionIndex);
                }

                // Check for completed processes
                Iterator<ProcessInfo> iter = runningProcesses.iterator();
                while (iter.hasNext()) {
                    ProcessInfo pi = iter.next();
                    if (!pi.process.isAlive()) {
                        int exitCode = pi.process.exitValue();
                        if (exitCode == 0) {
                            completedCount++;
                            System.out.println("Solution " + pi.solutionIndex +
                                " completed (" + completedCount + "/" + solutions.size() + ")");
                        } else {
                            failedCount++;
                            System.err.println("Solution " + pi.solutionIndex +
                                " FAILED with exit code " + exitCode);
                        }
                        iter.remove();
                    }
                }

                // Brief pause between checks
                if (!runningProcesses.isEmpty()) {
                    Thread.sleep(100);
                }
            }

            System.out.println("========================================");
            System.out.println("All processes completed. Success: " + completedCount +
                ", Failed: " + failedCount);

            // Collect results from files
            System.out.println("Collecting results...");
            for (int i = 0; i < solutions.size(); i++) {
                String resultFile = outputDirectory + "/result_" + i + ".ser";
                if (Files.exists(Paths.get(resultFile))) {
                    try {
                        results.add(ParetoSimulationResult.loadFromFile(resultFile));
                    } catch (Exception e) {
                        System.err.println("Failed to load result for solution " + i + ": " + e.getMessage());
                    }
                }
            }

            // Sort results by solution index
            results.sort(Comparator.comparingInt(ParetoSimulationResult::getSolutionIndex));

            // Print comparison report
            printComparisonReport();

            // Cleanup temp files
            Files.deleteIfExists(Paths.get(snapshotFile));
            Files.deleteIfExists(Paths.get(tempDir));

        } catch (Exception e) {
            throw new RuntimeException("Pareto front simulation failed", e);
        }
    }

    /**
     * Launches a subprocess to simulate a single Pareto solution.
     */
    private Process launchSolutionProcess(String snapshotFile,
                                           int solutionIndex,
                                           String outputDir) throws IOException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
            javaBin,
            "-cp", classpath,
            "com.cloudsimulator.steps.ParetoSolutionRunner",
            snapshotFile,
            String.valueOf(solutionIndex),
            String.valueOf(seed),
            outputDir
        );

        // Redirect output to parent process
        pb.inheritIO();

        return pb.start();
    }

    /**
     * Prints a formatted comparison report of estimated vs actual objectives.
     */
    private void printComparisonReport() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("PARETO FRONT SIMULATION RESULTS");
        System.out.println("========================================");

        if (results.isEmpty()) {
            System.out.println("No results available.");
            return;
        }

        // Header
        System.out.printf("%-8s %-15s %-15s %-15s %-15s %-10s %-10s%n",
            "Index", "Est.Makespan", "Act.Makespan", "Est.Energy", "Act.Energy",
            "MS Error", "E Error");
        System.out.println("-".repeat(80));

        // Results
        double totalMakespanError = 0;
        double totalEnergyError = 0;

        for (ParetoSimulationResult r : results) {
            System.out.printf("%-8d %-15.2f %-15.2f %-15.6f %-15.6f %-10.1f%% %-10.1f%%%n",
                r.getSolutionIndex(),
                r.getEstimatedMakespan(),
                r.getActualMakespan(),
                r.getEstimatedEnergy(),
                r.getActualEnergy(),
                r.getMakespanError() * 100,
                r.getEnergyError() * 100);

            totalMakespanError += Math.abs(r.getMakespanError());
            totalEnergyError += Math.abs(r.getEnergyError());
        }

        // Summary statistics
        System.out.println("-".repeat(80));
        int n = results.size();
        System.out.printf("Average absolute errors: Makespan=%.1f%%, Energy=%.1f%%%n",
            (totalMakespanError / n) * 100,
            (totalEnergyError / n) * 100);

        // Find best solutions
        ParetoSimulationResult bestActualMakespan = results.stream()
            .min(Comparator.comparingDouble(ParetoSimulationResult::getActualMakespan))
            .orElse(null);
        ParetoSimulationResult bestActualEnergy = results.stream()
            .min(Comparator.comparingDouble(ParetoSimulationResult::getActualEnergy))
            .orElse(null);

        if (bestActualMakespan != null) {
            System.out.printf("Best actual makespan: Solution %d (%.2f seconds)%n",
                bestActualMakespan.getSolutionIndex(),
                bestActualMakespan.getActualMakespan());
        }
        if (bestActualEnergy != null) {
            System.out.printf("Best actual energy: Solution %d (%.6f kWh)%n",
                bestActualEnergy.getSolutionIndex(),
                bestActualEnergy.getActualEnergy());
        }

        System.out.println("========================================");
    }

    @Override
    public String getStepName() {
        return "Pareto Front Simulation (Process-Based)";
    }

    /**
     * Gets all simulation results.
     */
    public List<ParetoSimulationResult> getResults() {
        return new ArrayList<>(results);
    }

    /**
     * Gets the output directory where reports were saved.
     */
    public String getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Gets the number of solutions that were successfully simulated.
     */
    public int getSuccessCount() {
        return results.size();
    }

    /**
     * Helper class to track running processes.
     */
    private static class ProcessInfo {
        final int solutionIndex;
        final Process process;

        ProcessInfo(int solutionIndex, Process process) {
            this.solutionIndex = solutionIndex;
            this.process = process;
        }
    }
}
