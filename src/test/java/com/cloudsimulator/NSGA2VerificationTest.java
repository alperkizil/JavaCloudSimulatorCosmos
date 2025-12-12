package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verification test for NSGA-II algorithm.
 *
 * This test creates a simple scenario with 5 tasks and 3 VMs,
 * runs NSGA-II, and verifies the objective calculations manually.
 */
public class NSGA2VerificationTest {

    // Power model constants (from EnergyObjective)
    private static final double BASE_POWER_WATTS = 50.0;
    private static final double CPU_POWER_PER_CORE_WATTS = 30.0;
    private static final double GPU_POWER_PER_UNIT_WATTS = 200.0;
    private static final double JOULES_TO_KWH = 3_600_000.0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║         NSGA-II Verification Test                            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Create simple test scenario
        List<Task> tasks = createTasks();
        List<VM> vms = createVMs();

        printScenario(tasks, vms);

        // Configure NSGA-II with small population for quick test
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(50)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .addObjective(new MakespanObjective())
            .addObjective(new EnergyObjective())
            .terminationCondition(new GenerationCountTermination(100))
            .randomSeed(42L)
            .verboseLogging(false)
            .build();

        System.out.println("NSGA-II Configuration:");
        System.out.println("  Population size: " + config.getPopulationSize());
        System.out.println("  Generations: 100");
        System.out.println("  Random seed: 42");
        System.out.println();

        // Run NSGA-II
        System.out.println("Running NSGA-II optimization...");
        long startTime = System.currentTimeMillis();

        NSGA2TaskSchedulingStrategy strategy = new NSGA2TaskSchedulingStrategy(config);
        ParetoFront paretoFront = strategy.optimize(tasks, vms);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Completed in " + elapsed + " ms");
        System.out.println();

        // Print Pareto front
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("PARETO FRONT (" + paretoFront.size() + " solutions):");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println(paretoFront);

        // Verify each solution in the Pareto front
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("VERIFICATION - Manual Calculation vs NSGA-II");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();

        MakespanObjective makespanObj = new MakespanObjective();
        EnergyObjective energyObj = new EnergyObjective();

        int solutionNum = 1;
        boolean allPassed = true;

        for (SchedulingSolution solution : paretoFront.getSolutions()) {
            System.out.println("─── Solution " + solutionNum + " ───");

            // Get NSGA-II reported values
            double nsga2Makespan = solution.getObjectiveValue(0);
            double nsga2Energy = solution.getObjectiveValue(1);

            // Manually recalculate using the objectives
            double recalcMakespan = makespanObj.evaluate(solution, tasks, vms);
            double recalcEnergy = energyObj.evaluate(solution, tasks, vms);

            // Also do a completely manual calculation
            double[] manualCalc = calculateObjectivesManually(solution, tasks, vms);
            double manualMakespan = manualCalc[0];
            double manualEnergy = manualCalc[1];

            // Print assignment details
            System.out.println("Task Assignment: " + Arrays.toString(solution.getTaskAssignment()));
            System.out.println();

            // Print per-VM breakdown
            printVMBreakdown(solution, tasks, vms);

            System.out.println("Results Comparison:");
            System.out.printf("  %-20s %15s %15s %15s%n", "", "NSGA-II", "Recalc (Obj)", "Manual");
            System.out.printf("  %-20s %15.4f %15.4f %15.4f%n", "Makespan (s):", nsga2Makespan, recalcMakespan, manualMakespan);
            System.out.printf("  %-20s %15.6f %15.6f %15.6f%n", "Energy (kWh):", nsga2Energy, recalcEnergy, manualEnergy);

            // Check if values match (with small tolerance for floating point)
            double tolerance = 0.0001;
            boolean makespanMatch = Math.abs(nsga2Makespan - manualMakespan) < tolerance;
            boolean energyMatch = Math.abs(nsga2Energy - manualEnergy) < tolerance;

            System.out.println();
            System.out.println("Verification:");
            System.out.println("  Makespan: " + (makespanMatch ? "✓ PASSED" : "✗ FAILED"));
            System.out.println("  Energy:   " + (energyMatch ? "✓ PASSED" : "✗ FAILED"));

            if (!makespanMatch || !energyMatch) {
                allPassed = false;
            }

            System.out.println();
            solutionNum++;
        }

        // Print summary
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("SUMMARY");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();
        if (allPassed) {
            System.out.println("✓ ALL SOLUTIONS VERIFIED SUCCESSFULLY");
            System.out.println();
            System.out.println("The NSGA-II algorithm correctly calculates:");
            System.out.println("  - Makespan = max(completion_time) across all VMs");
            System.out.println("  - Energy = sum(power × time) for all tasks, converted to kWh");
        } else {
            System.out.println("✗ SOME SOLUTIONS FAILED VERIFICATION");
        }
        System.out.println();

        // Print best solutions
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("BEST SOLUTIONS");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();

        SchedulingSolution bestMakespan = paretoFront.getBestForObjective(0);
        SchedulingSolution bestEnergy = paretoFront.getBestForObjective(1);
        SchedulingSolution kneePoint = paretoFront.getKneePoint();

        System.out.println("Best Makespan Solution:");
        System.out.printf("  Makespan: %.4f seconds%n", bestMakespan.getObjectiveValue(0));
        System.out.printf("  Energy:   %.6f kWh%n", bestMakespan.getObjectiveValue(1));
        System.out.println("  Assignment: " + Arrays.toString(bestMakespan.getTaskAssignment()));
        System.out.println();

        System.out.println("Best Energy Solution:");
        System.out.printf("  Makespan: %.4f seconds%n", bestEnergy.getObjectiveValue(0));
        System.out.printf("  Energy:   %.6f kWh%n", bestEnergy.getObjectiveValue(1));
        System.out.println("  Assignment: " + Arrays.toString(bestEnergy.getTaskAssignment()));
        System.out.println();

        System.out.println("Knee Point (Balanced Trade-off):");
        System.out.printf("  Makespan: %.4f seconds%n", kneePoint.getObjectiveValue(0));
        System.out.printf("  Energy:   %.6f kWh%n", kneePoint.getObjectiveValue(1));
        System.out.println("  Assignment: " + Arrays.toString(kneePoint.getTaskAssignment()));
    }

    /**
     * Creates a simple set of 5 tasks with different workload types.
     */
    private static List<Task> createTasks() {
        List<Task> tasks = new ArrayList<>();

        // Task 0: CPU-heavy task (CINEBENCH) - 10 billion instructions
        tasks.add(new Task("Task_CPU_Heavy", "user1", 10_000_000_000L, WorkloadType.CINEBENCH));

        // Task 1: Light CPU task (DATABASE) - 5 billion instructions
        tasks.add(new Task("Task_Database", "user1", 5_000_000_000L, WorkloadType.DATABASE));

        // Task 2: CPU compression (7ZIP) - 8 billion instructions
        tasks.add(new Task("Task_7Zip", "user1", 8_000_000_000L, WorkloadType.SEVEN_ZIP));

        // Task 3: Light task (VERACRYPT) - 3 billion instructions
        tasks.add(new Task("Task_Veracrypt", "user1", 3_000_000_000L, WorkloadType.VERACRYPT));

        // Task 4: CPU LLM task - 6 billion instructions
        tasks.add(new Task("Task_LLM", "user1", 6_000_000_000L, WorkloadType.LLM_CPU));

        return tasks;
    }

    /**
     * Creates 3 VMs with different specs.
     */
    private static List<VM> createVMs() {
        List<VM> vms = new ArrayList<>();

        // VM 0: Fast VM - 4 cores at 2 billion IPS each = 8 billion IPS total
        vms.add(new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY));

        // VM 1: Medium VM - 2 cores at 1.5 billion IPS each = 3 billion IPS total
        vms.add(new VM("user1", 1_500_000_000L, 2, 0, 4096, 51200, 1000, ComputeType.CPU_ONLY));

        // VM 2: Slow VM - 2 cores at 1 billion IPS each = 2 billion IPS total
        vms.add(new VM("user1", 1_000_000_000L, 2, 0, 4096, 51200, 1000, ComputeType.CPU_ONLY));

        return vms;
    }

    /**
     * Prints the test scenario details.
     */
    private static void printScenario(List<Task> tasks, List<VM> vms) {
        System.out.println("═══ TEST SCENARIO ═══");
        System.out.println();
        System.out.println("TASKS (" + tasks.size() + "):");
        System.out.printf("  %-5s %-15s %-15s %15s%n", "ID", "Name", "Workload", "Instructions");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            System.out.printf("  %-5d %-15s %-15s %,15d%n",
                i, t.getName(), t.getWorkloadType(), t.getInstructionLength());
        }
        System.out.println();

        System.out.println("VMs (" + vms.size() + "):");
        System.out.printf("  %-5s %10s %10s %15s%n", "ID", "vCPUs", "IPS/vCPU", "Total IPS");
        for (int i = 0; i < vms.size(); i++) {
            VM vm = vms.get(i);
            System.out.printf("  %-5d %10d %,10d %,15d%n",
                i, vm.getRequestedVcpuCount(), vm.getRequestedIpsPerVcpu(), vm.getTotalRequestedIps());
        }
        System.out.println();
    }

    /**
     * Prints detailed breakdown of tasks per VM.
     */
    private static void printVMBreakdown(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder.isEmpty()) {
                System.out.println("VM " + vmIdx + " (IPS=" + String.format("%,d", vm.getTotalRequestedIps()) + "): No tasks");
            } else {
                double vmTime = 0;
                double vmEnergy = 0;
                StringBuilder taskList = new StringBuilder();

                for (int taskIdx : taskOrder) {
                    Task task = tasks.get(taskIdx);
                    double execTime = (double) task.getInstructionLength() / vm.getTotalRequestedIps();
                    double power = calculatePower(task.getWorkloadType(), vm);
                    double energyJoules = power * execTime;

                    vmTime += execTime;
                    vmEnergy += energyJoules;

                    if (taskList.length() > 0) taskList.append(", ");
                    taskList.append("T").append(taskIdx);
                }

                System.out.printf("VM %d (IPS=%,d): Tasks=[%s], Time=%.2fs, Energy=%.2fJ (%.6f kWh)%n",
                    vmIdx, vm.getTotalRequestedIps(), taskList, vmTime, vmEnergy, vmEnergy / JOULES_TO_KWH);
            }
        }
        System.out.println();
    }

    /**
     * Manually calculates both objectives for verification.
     *
     * @return [makespan, energy_kWh]
     */
    private static double[] calculateObjectivesManually(SchedulingSolution solution,
                                                         List<Task> tasks, List<VM> vms) {
        double maxCompletionTime = 0.0;
        double totalEnergyJoules = 0.0;

        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (taskOrder.isEmpty()) {
                continue;
            }

            double vmCompletionTime = 0.0;
            long vmIps = vm.getTotalRequestedIps();

            for (int taskIdx : taskOrder) {
                Task task = tasks.get(taskIdx);

                // Calculate execution time: instructions / IPS
                double executionTime = (double) task.getInstructionLength() / vmIps;
                vmCompletionTime += executionTime;

                // Calculate power based on workload type
                double power = calculatePower(task.getWorkloadType(), vm);

                // Energy (Joules) = Power (Watts) × Time (seconds)
                totalEnergyJoules += power * executionTime;
            }

            if (vmCompletionTime > maxCompletionTime) {
                maxCompletionTime = vmCompletionTime;
            }
        }

        // Convert energy to kWh
        double energyKWh = totalEnergyJoules / JOULES_TO_KWH;

        return new double[] { maxCompletionTime, energyKWh };
    }

    /**
     * Calculates power draw for a workload type on a VM.
     * Replicates the EnergyObjective calculation.
     */
    private static double calculatePower(WorkloadType workloadType, VM vm) {
        double[] utilization = getUtilizationProfile(workloadType);
        double cpuUtil = utilization[0];
        double gpuUtil = utilization[1];

        double cpuPower = cpuUtil * vm.getRequestedVcpuCount() * CPU_POWER_PER_CORE_WATTS;
        double gpuPower = gpuUtil * vm.getRequestedGpuCount() * GPU_POWER_PER_UNIT_WATTS;

        return BASE_POWER_WATTS + cpuPower + gpuPower;
    }

    /**
     * Returns CPU/GPU utilization profile for a workload type.
     * Matches EnergyObjective.getUtilizationProfile().
     */
    private static double[] getUtilizationProfile(WorkloadType workloadType) {
        switch (workloadType) {
            case SEVEN_ZIP:
                return new double[]{0.8, 0.0};
            case DATABASE:
                return new double[]{0.6, 0.0};
            case FURMARK:
                return new double[]{0.1, 1.0};
            case IMAGE_GEN_CPU:
                return new double[]{0.9, 0.0};
            case IMAGE_GEN_GPU:
                return new double[]{0.2, 0.9};
            case LLM_CPU:
                return new double[]{0.95, 0.0};
            case LLM_GPU:
                return new double[]{0.3, 0.95};
            case CINEBENCH:
                return new double[]{1.0, 0.0};
            case PRIME95SmallFFT:
                return new double[]{1.0, 0.0};
            case VERACRYPT:
                return new double[]{0.85, 0.0};
            case IDLE:
            default:
                return new double[]{0.0, 0.0};
        }
    }
}
