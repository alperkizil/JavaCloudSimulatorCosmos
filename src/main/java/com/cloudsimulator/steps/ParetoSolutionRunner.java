package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationSnapshot;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.List;

/**
 * Entry point for subprocess that simulates a single Pareto solution.
 *
 * This class is invoked as a separate JVM process to ensure complete isolation
 * between Pareto solution simulations. Each process:
 * 1. Initializes its own RandomGenerator with the provided seed
 * 2. Loads the simulation snapshot from file
 * 3. Restores the context (steps 1-4 already applied)
 * 4. Applies the specific solution's task assignments and ordering
 * 5. Runs steps 6-10 (VMExecution, TaskExecution, Energy, Metrics, Reporting)
 * 6. Writes the result to an output file
 *
 * Usage:
 * java com.cloudsimulator.steps.ParetoSolutionRunner <snapshotFile> <solutionIndex> <seed> <outputDir>
 */
public class ParetoSolutionRunner {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: ParetoSolutionRunner <snapshotFile> <solutionIndex> <seed> <outputDir>");
            System.exit(1);
        }

        String snapshotFile = args[0];
        int solutionIndex = Integer.parseInt(args[1]);
        long seed = Long.parseLong(args[2]);
        String outputDir = args[3];

        try {
            System.out.println("[Solution " + solutionIndex + "] Starting simulation...");

            // Initialize RandomGenerator with seed for determinism
            RandomGenerator.initialize(seed);

            // Load snapshot
            SimulationSnapshot snapshot = SimulationSnapshot.loadFromFile(snapshotFile);

            // Restore context with steps 1-4 applied
            SimulationContext context = snapshot.restoreContext();

            // Get the solution to simulate
            SchedulingSolution solution = snapshot.getSolution(solutionIndex);

            System.out.println("[Solution " + solutionIndex + "] Applying task assignments...");

            // Apply solution (assignment + ordering)
            applySolution(solution, context);

            System.out.println("[Solution " + solutionIndex + "] Running simulation steps 6-10...");

            // Step 6: VM Execution
            VMExecutionStep vmStep = new VMExecutionStep();
            vmStep.execute(context);

            // Step 7: Task Execution (post-simulation analysis)
            TaskExecutionStep taskStep = new TaskExecutionStep();
            taskStep.execute(context);

            // Step 8: Energy Calculation
            EnergyCalculationStep energyStep = new EnergyCalculationStep();
            energyStep.execute(context);

            // Step 9: Metrics Collection
            MetricsCollectionStep metricsStep = new MetricsCollectionStep();
            metricsStep.execute(context);

            // Step 10: Reporting (with solution-specific prefix)
            ReportingStep reportingStep = new ReportingStep();
            reportingStep.setBaseOutputDirectory(outputDir);
            reportingStep.setCustomPrefix("pareto_solution_" + solutionIndex);
            reportingStep.execute(context);

            // Collect actual metrics
            double actualMakespan = taskStep.getMakespan();
            double actualEnergy = energyStep.getTotalITEnergyKWh();  // Raw IT energy, no PUE

            System.out.println("[Solution " + solutionIndex + "] Actual makespan: " + actualMakespan +
                " (estimated: " + solution.getObjectiveValue(0) + ")");
            System.out.println("[Solution " + solutionIndex + "] Actual energy: " + actualEnergy +
                " kWh (estimated: " + solution.getObjectiveValue(1) + ")");

            // Build result
            ParetoSimulationResult result = new ParetoSimulationResult(
                solutionIndex,
                solution.getObjectiveValues(),
                new double[]{actualMakespan, actualEnergy}
            );

            // Write result to file
            String resultFile = outputDir + "/result_" + solutionIndex + ".ser";
            result.saveToFile(resultFile);

            System.out.println("[Solution " + solutionIndex + "] Completed successfully. Result saved to: " + resultFile);
            System.exit(0);

        } catch (Exception e) {
            System.err.println("[Solution " + solutionIndex + "] FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Applies a Pareto solution's task assignments and ordering to the context.
     * Tasks are assigned to VMs in the order specified by the solution's vmTaskOrder.
     */
    private static void applySolution(SchedulingSolution solution, SimulationContext context) {
        List<Task> tasks = context.getTasks();
        List<VM> vms = context.getVms();
        long currentTime = context.getCurrentTime();

        // Apply assignments respecting execution ORDER from solution
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder != null && !taskOrder.isEmpty()) {
                for (int taskIdx : taskOrder) {
                    if (taskIdx >= 0 && taskIdx < tasks.size()) {
                        Task task = tasks.get(taskIdx);
                        task.assignToVM(vm.getId(), currentTime);
                        vm.assignTask(task);
                    }
                }
            }
        }

        int assignedCount = 0;
        for (Task task : tasks) {
            if (task.isAssigned()) {
                assignedCount++;
            }
        }
        System.out.println("[Solution] Assigned " + assignedCount + "/" + tasks.size() + " tasks");
    }
}
