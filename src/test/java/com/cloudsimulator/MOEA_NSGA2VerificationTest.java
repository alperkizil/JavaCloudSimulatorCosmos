package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verification test for MOEA Framework NSGA-II integration.
 *
 * Test scenario:
 * - 1 datacenter (simulated via 50 hosts)
 * - 50 hosts capacity (represented by 20 VMs)
 * - 20 VMs with varying CPU specs
 * - 20 CPU-only tasks
 *
 * Compares results between native NSGA-II and MOEA Framework NSGA-II.
 */
public class MOEA_NSGA2VerificationTest {

    private static final long SEED = 42L;

    public static void main(String[] args) {
        // Initialize the RandomGenerator for reproducibility
        RandomGenerator.initialize(SEED);

        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         MOEA Framework NSGA-II Verification Test                     ║");
        System.out.println("║         1 Datacenter - 50 Hosts - 20 VMs - 20 CPU Tasks              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Create test scenario
        List<Task> tasks = createTasks(20);
        List<VM> vms = createVMs(20);

        printScenario(tasks, vms);

        // Configure NSGA-II
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(100)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .addObjective(new MakespanObjective())
            .addObjective(new EnergyObjective())
            .terminationCondition(new GenerationCountTermination(100))
            .randomSeed(SEED)
            .verboseLogging(true)
            .build();

        System.out.println("NSGA-II Configuration:");
        System.out.println("  Population size: " + config.getPopulationSize());
        System.out.println("  Generations: 100");
        System.out.println("  Crossover rate: " + config.getCrossoverRate());
        System.out.println("  Mutation rate: " + config.getMutationRate());
        System.out.println("  Random seed: " + SEED);
        System.out.println();

        // ════════════════════════════════════════════════════════════════
        // Run MOEA Framework NSGA-II
        // ════════════════════════════════════════════════════════════════
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("MOEA FRAMEWORK NSGA-II");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Reset random state before MOEA run
        RandomGenerator.getInstance().reset(SEED);

        long moeaStartTime = System.currentTimeMillis();

        MOEA_NSGA2TaskSchedulingStrategy moeaStrategy = new MOEA_NSGA2TaskSchedulingStrategy(config);
        ParetoFront moeaFront = moeaStrategy.optimize(tasks, vms);

        long moeaElapsed = System.currentTimeMillis() - moeaStartTime;
        System.out.println();
        System.out.println("MOEA Framework completed in " + moeaElapsed + " ms");
        System.out.println("Function evaluations: " + moeaStrategy.getLastEvaluationCount());
        System.out.println("Pareto front size: " + moeaFront.size());
        System.out.println();

        // ════════════════════════════════════════════════════════════════
        // Run Native NSGA-II for comparison
        // ════════════════════════════════════════════════════════════════
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("NATIVE NSGA-II (for comparison)");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Reset random state before native run
        RandomGenerator.getInstance().reset(SEED);

        long nativeStartTime = System.currentTimeMillis();

        NSGA2TaskSchedulingStrategy nativeStrategy = new NSGA2TaskSchedulingStrategy(config);
        ParetoFront nativeFront = nativeStrategy.optimize(tasks, vms);

        long nativeElapsed = System.currentTimeMillis() - nativeStartTime;
        System.out.println("Native NSGA-II completed in " + nativeElapsed + " ms");
        System.out.println("Pareto front size: " + nativeFront.size());
        System.out.println();

        // ════════════════════════════════════════════════════════════════
        // Compare Results
        // ════════════════════════════════════════════════════════════════
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("RESULTS COMPARISON");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // MOEA Results
        System.out.println("MOEA Framework Pareto Front:");
        printParetoFrontSummary(moeaFront);

        System.out.println();

        // Native Results
        System.out.println("Native NSGA-II Pareto Front:");
        printParetoFrontSummary(nativeFront);

        // ════════════════════════════════════════════════════════════════
        // Verify MOEA solutions
        // ════════════════════════════════════════════════════════════════
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("MOEA FRAMEWORK SOLUTION VERIFICATION");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        MakespanObjective makespanObj = new MakespanObjective();
        EnergyObjective energyObj = new EnergyObjective();

        boolean allPassed = true;
        int verifyCount = Math.min(5, moeaFront.size()); // Verify first 5 solutions

        for (int i = 0; i < verifyCount; i++) {
            SchedulingSolution solution = moeaFront.getSolutions().get(i);

            // Get MOEA reported values
            double reportedMakespan = solution.getObjectiveValue(0);
            double reportedEnergy = solution.getObjectiveValue(1);

            // Recalculate using objectives
            double recalcMakespan = makespanObj.evaluate(solution, tasks, vms);
            double recalcEnergy = energyObj.evaluate(solution, tasks, vms);

            double tolerance = 0.0001;
            boolean makespanMatch = Math.abs(reportedMakespan - recalcMakespan) < tolerance;
            boolean energyMatch = Math.abs(reportedEnergy - recalcEnergy) < tolerance;

            System.out.printf("Solution %d: Makespan=%.4fs, Energy=%.6f kWh ", i + 1, reportedMakespan, reportedEnergy);

            if (makespanMatch && energyMatch) {
                System.out.println("✓ VERIFIED");
            } else {
                System.out.println("✗ MISMATCH");
                System.out.printf("  Recalculated: Makespan=%.4fs, Energy=%.6f kWh%n", recalcMakespan, recalcEnergy);
                allPassed = false;
            }
        }

        // ════════════════════════════════════════════════════════════════
        // Summary
        // ════════════════════════════════════════════════════════════════
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("SUMMARY");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("%-30s %15s %15s%n", "Metric", "MOEA Framework", "Native");
        System.out.printf("%-30s %15d %15d%n", "Pareto front size:", moeaFront.size(), nativeFront.size());
        System.out.printf("%-30s %13d ms %13d ms%n", "Execution time:", moeaElapsed, nativeElapsed);
        System.out.println();

        // Best solutions comparison
        SchedulingSolution moeaBestMakespan = moeaFront.getBestForObjective(0);
        SchedulingSolution moeaBestEnergy = moeaFront.getBestForObjective(1);
        SchedulingSolution moeaKnee = moeaFront.getKneePoint();

        SchedulingSolution nativeBestMakespan = nativeFront.getBestForObjective(0);
        SchedulingSolution nativeBestEnergy = nativeFront.getBestForObjective(1);
        SchedulingSolution nativeKnee = nativeFront.getKneePoint();

        System.out.println("Best Makespan (seconds):");
        System.out.printf("  MOEA:   %.4f%n", moeaBestMakespan.getObjectiveValue(0));
        System.out.printf("  Native: %.4f%n", nativeBestMakespan.getObjectiveValue(0));
        System.out.println();

        System.out.println("Best Energy (kWh):");
        System.out.printf("  MOEA:   %.6f%n", moeaBestEnergy.getObjectiveValue(1));
        System.out.printf("  Native: %.6f%n", nativeBestEnergy.getObjectiveValue(1));
        System.out.println();

        System.out.println("Knee Point (Balanced Trade-off):");
        System.out.printf("  MOEA:   Makespan=%.4fs, Energy=%.6f kWh%n",
            moeaKnee.getObjectiveValue(0), moeaKnee.getObjectiveValue(1));
        System.out.printf("  Native: Makespan=%.4fs, Energy=%.6f kWh%n",
            nativeKnee.getObjectiveValue(0), nativeKnee.getObjectiveValue(1));
        System.out.println();

        if (allPassed) {
            System.out.println("✓ MOEA FRAMEWORK INTEGRATION VERIFIED SUCCESSFULLY");
        } else {
            System.out.println("✗ SOME VERIFICATION CHECKS FAILED");
        }
        System.out.println();

        // Print a sample solution assignment
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("SAMPLE MOEA KNEE POINT SOLUTION DETAILS");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();
        printSolutionDetails(moeaKnee, tasks, vms);
    }

    /**
     * Creates N CPU-only tasks with varying instruction lengths.
     */
    private static List<Task> createTasks(int count) {
        List<Task> tasks = new ArrayList<>();
        WorkloadType[] cpuWorkloads = {
            WorkloadType.CINEBENCH,
            WorkloadType.SEVEN_ZIP,
            WorkloadType.LLM_CPU,
            WorkloadType.DATABASE,
            WorkloadType.VERACRYPT,
            WorkloadType.PRIME95SmallFFT,
            WorkloadType.IMAGE_GEN_CPU
        };

        for (int i = 0; i < count; i++) {
            // Varying instruction lengths: 1B to 20B instructions
            long instructions = (1 + (i % 10)) * 2_000_000_000L;
            WorkloadType workload = cpuWorkloads[i % cpuWorkloads.length];

            tasks.add(new Task("Task_" + i, "user1", instructions, workload));
        }

        return tasks;
    }

    /**
     * Creates N VMs with varying CPU specs (simulating 50 hosts capacity).
     */
    private static List<VM> createVMs(int count) {
        List<VM> vms = new ArrayList<>();

        // Different VM configurations to simulate heterogeneous infrastructure
        int[][] vmConfigs = {
            // {ipsPerCore, cores}
            {2_000_000_000, 8},   // High-end: 16B IPS total
            {2_000_000_000, 4},   // Mid-high: 8B IPS total
            {1_500_000_000, 4},   // Medium: 6B IPS total
            {1_500_000_000, 2},   // Mid-low: 3B IPS total
            {1_000_000_000, 2},   // Low-end: 2B IPS total
        };

        for (int i = 0; i < count; i++) {
            int[] config = vmConfigs[i % vmConfigs.length];
            vms.add(new VM("user1", config[0], config[1], 0, 8192, 102400, 1000, ComputeType.CPU_ONLY));
        }

        return vms;
    }

    /**
     * Prints the test scenario.
     */
    private static void printScenario(List<Task> tasks, List<VM> vms) {
        System.out.println("═══ TEST SCENARIO ═══");
        System.out.println();
        System.out.println("TASKS (" + tasks.size() + "):");
        System.out.printf("  %-8s %-15s %-18s %18s%n", "ID", "Name", "Workload", "Instructions");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            System.out.printf("  %-8d %-15s %-18s %,18d%n",
                i, t.getName(), t.getWorkloadType(), t.getInstructionLength());
        }
        System.out.println();

        System.out.println("VMs (" + vms.size() + "):");
        System.out.printf("  %-8s %8s %15s %18s%n", "ID", "vCPUs", "IPS/vCPU", "Total IPS");
        for (int i = 0; i < vms.size(); i++) {
            VM vm = vms.get(i);
            System.out.printf("  %-8d %8d %,15d %,18d%n",
                i, vm.getRequestedVcpuCount(), vm.getRequestedIpsPerVcpu(), vm.getTotalRequestedIps());
        }
        System.out.println();
    }

    /**
     * Prints summary of Pareto front.
     */
    private static void printParetoFrontSummary(ParetoFront front) {
        if (front.isEmpty()) {
            System.out.println("  (empty)");
            return;
        }

        double[][] ranges = front.getObjectiveRanges();
        System.out.printf("  Makespan range: %.4f - %.4f seconds%n", ranges[0][0], ranges[0][1]);
        System.out.printf("  Energy range:   %.6f - %.6f kWh%n", ranges[1][0], ranges[1][1]);
        System.out.println("  Solutions: " + front.size());
    }

    /**
     * Prints detailed solution assignment.
     */
    private static void printSolutionDetails(SchedulingSolution solution, List<Task> tasks, List<VM> vms) {
        System.out.println("Task Assignment: " + Arrays.toString(solution.getTaskAssignment()));
        System.out.printf("Objectives: Makespan=%.4fs, Energy=%.6f kWh%n",
            solution.getObjectiveValue(0), solution.getObjectiveValue(1));
        System.out.println();

        System.out.println("Per-VM Breakdown:");
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            List<Integer> taskOrder = solution.getTaskOrderForVM(vmIdx);

            if (!taskOrder.isEmpty()) {
                long totalInstructions = 0;
                for (int taskIdx : taskOrder) {
                    totalInstructions += tasks.get(taskIdx).getInstructionLength();
                }
                double execTime = (double) totalInstructions / vm.getTotalRequestedIps();

                System.out.printf("  VM %2d (IPS=%,12d): %2d tasks, total %.2fs, tasks=%s%n",
                    vmIdx, vm.getTotalRequestedIps(), taskOrder.size(), execTime, taskOrder);
            }
        }
    }
}
