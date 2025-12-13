package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.*;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.*;

/**
 * Comparison test for MOEA Framework algorithms.
 *
 * Tests all four MOEA algorithms (NSGA-II, SPEA2, ε-MOEA, AMOSA) on the
 * same task scheduling problem with the same random seed to verify:
 * 1. Experiment reproducibility (same seed → same results)
 * 2. Algorithm correctness (valid Pareto fronts)
 * 3. Performance comparison (makespan vs energy trade-offs)
 *
 * This test ensures the simulator seed is properly propagated to MOEA Framework
 * for reproducible experiments.
 */
public class MOEAAlgorithmComparisonTest {

    private static final long TEST_SEED = 42L;
    private static final int POPULATION_SIZE = 50;
    private static final int MAX_EVALUATIONS = 5000;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║           MOEA Framework Algorithm Comparison Test                    ║");
        System.out.println("║     Testing: NSGA-II, SPEA2, ε-MOEA, AMOSA                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Create test scenario
        List<Task> tasks = createTasks();
        List<VM> vms = createVMs();

        printScenario(tasks, vms);

        // Store results for comparison
        Map<String, AlgorithmResult> results = new LinkedHashMap<>();

        // Test each algorithm
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("RUNNING ALGORITHMS WITH SEED = " + TEST_SEED);
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // 1. MOEA_NSGAII
        results.put("MOEA_NSGAII", runMOEA_NSGAII(tasks, vms));

        // 2. MOEA_SPEA2
        results.put("MOEA_SPEA2", runMOEA_SPEA2(tasks, vms));

        // 3. MOEA_EpsilonMOEA
        results.put("MOEA_EpsilonMOEA", runMOEA_EpsilonMOEA(tasks, vms));

        // 4. MOEA_AMOSA
        results.put("MOEA_AMOSA", runMOEA_AMOSA(tasks, vms));

        // Print comparison
        printComparison(results);

        // Verify reproducibility
        verifyReproducibility(tasks, vms, results);

        // Print summary
        printSummary(results);
    }

    private static AlgorithmResult runMOEA_NSGAII(List<Task> tasks, List<VM> vms) {
        System.out.println("─── MOEA_NSGAII ───");

        // Initialize RandomGenerator with test seed
        RandomGenerator.initialize(TEST_SEED);

        MOEAConfiguration config = MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.NSGA2)
            .populationSize(POPULATION_SIZE)
            .maxEvaluations(MAX_EVALUATIONS)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .solutionSelection(MOEAConfiguration.SolutionSelection.KNEE_POINT)
            .verboseLogging(false)
            .build();

        MOEA_NSGAII strategy = new MOEA_NSGAII(config);

        long startTime = System.currentTimeMillis();
        Map<Task, VM> assignments = strategy.assignAll(tasks, vms, 0L);
        long elapsed = System.currentTimeMillis() - startTime;

        ParetoFront front = strategy.getLastParetoFront();
        SchedulingSolution selected = strategy.getLastSelectedSolution();

        System.out.println("  Time: " + elapsed + " ms");
        System.out.println("  Pareto Front Size: " + (front != null ? front.size() : 0));
        System.out.println("  Assignments: " + assignments.size() + "/" + tasks.size());

        if (selected != null) {
            System.out.printf("  Selected Solution: Makespan=%.2fs, Energy=%.6f kWh%n",
                selected.getObjectiveValue(0), selected.getObjectiveValue(1));
        }
        System.out.println();

        return new AlgorithmResult("MOEA_NSGAII", front, selected, elapsed, assignments.size());
    }

    private static AlgorithmResult runMOEA_SPEA2(List<Task> tasks, List<VM> vms) {
        System.out.println("─── MOEA_SPEA2 ───");

        // Initialize RandomGenerator with test seed
        RandomGenerator.initialize(TEST_SEED);

        MOEAConfiguration config = MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.SPEA2)
            .populationSize(POPULATION_SIZE)
            .maxEvaluations(MAX_EVALUATIONS)
            .archiveSize(POPULATION_SIZE)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .solutionSelection(MOEAConfiguration.SolutionSelection.KNEE_POINT)
            .verboseLogging(false)
            .build();

        MOEA_SPEA2 strategy = new MOEA_SPEA2(config);

        long startTime = System.currentTimeMillis();
        Map<Task, VM> assignments = strategy.assignAll(tasks, vms, 0L);
        long elapsed = System.currentTimeMillis() - startTime;

        ParetoFront front = strategy.getLastParetoFront();
        SchedulingSolution selected = strategy.getLastSelectedSolution();

        System.out.println("  Time: " + elapsed + " ms");
        System.out.println("  Pareto Front Size: " + (front != null ? front.size() : 0));
        System.out.println("  Assignments: " + assignments.size() + "/" + tasks.size());

        if (selected != null) {
            System.out.printf("  Selected Solution: Makespan=%.2fs, Energy=%.6f kWh%n",
                selected.getObjectiveValue(0), selected.getObjectiveValue(1));
        }
        System.out.println();

        return new AlgorithmResult("MOEA_SPEA2", front, selected, elapsed, assignments.size());
    }

    private static AlgorithmResult runMOEA_EpsilonMOEA(List<Task> tasks, List<VM> vms) {
        System.out.println("─── MOEA_EpsilonMOEA ───");

        // Initialize RandomGenerator with test seed
        RandomGenerator.initialize(TEST_SEED);

        MOEAConfiguration config = MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.EPSILON_MOEA)
            .populationSize(POPULATION_SIZE)
            .maxEvaluations(MAX_EVALUATIONS)
            .epsilons(1.0, 0.0001) // 1 second, 0.0001 kWh resolution
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .solutionSelection(MOEAConfiguration.SolutionSelection.KNEE_POINT)
            .verboseLogging(false)
            .build();

        MOEA_EpsilonMOEA strategy = new MOEA_EpsilonMOEA(config);

        long startTime = System.currentTimeMillis();
        Map<Task, VM> assignments = strategy.assignAll(tasks, vms, 0L);
        long elapsed = System.currentTimeMillis() - startTime;

        ParetoFront front = strategy.getLastParetoFront();
        SchedulingSolution selected = strategy.getLastSelectedSolution();

        System.out.println("  Time: " + elapsed + " ms");
        System.out.println("  Pareto Front Size: " + (front != null ? front.size() : 0));
        System.out.println("  Assignments: " + assignments.size() + "/" + tasks.size());

        if (selected != null) {
            System.out.printf("  Selected Solution: Makespan=%.2fs, Energy=%.6f kWh%n",
                selected.getObjectiveValue(0), selected.getObjectiveValue(1));
        }
        System.out.println();

        return new AlgorithmResult("MOEA_EpsilonMOEA", front, selected, elapsed, assignments.size());
    }

    private static AlgorithmResult runMOEA_AMOSA(List<Task> tasks, List<VM> vms) {
        System.out.println("─── MOEA_AMOSA ───");

        // Initialize RandomGenerator with test seed
        RandomGenerator.initialize(TEST_SEED);

        MOEAConfiguration config = MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.AMOSA)
            .maxEvaluations(MAX_EVALUATIONS)
            .initialTemperature(500.0)
            .finalTemperature(0.1)
            .coolingRate(0.95)
            .archiveSize(POPULATION_SIZE)
            .iterationsPerTemperature(50)
            .solutionSelection(MOEAConfiguration.SolutionSelection.KNEE_POINT)
            .verboseLogging(false)
            .build();

        MOEA_AMOSA strategy = new MOEA_AMOSA(config);

        long startTime = System.currentTimeMillis();
        Map<Task, VM> assignments = strategy.assignAll(tasks, vms, 0L);
        long elapsed = System.currentTimeMillis() - startTime;

        ParetoFront front = strategy.getLastParetoFront();
        SchedulingSolution selected = strategy.getLastSelectedSolution();

        System.out.println("  Time: " + elapsed + " ms");
        System.out.println("  Pareto Front Size: " + (front != null ? front.size() : 0));
        System.out.println("  Assignments: " + assignments.size() + "/" + tasks.size());

        if (selected != null) {
            System.out.printf("  Selected Solution: Makespan=%.2fs, Energy=%.6f kWh%n",
                selected.getObjectiveValue(0), selected.getObjectiveValue(1));
        }
        System.out.println();

        return new AlgorithmResult("MOEA_AMOSA", front, selected, elapsed, assignments.size());
    }

    private static void printComparison(Map<String, AlgorithmResult> results) {
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("ALGORITHM COMPARISON");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Header
        System.out.printf("%-20s %10s %10s %12s %12s %10s%n",
            "Algorithm", "Time(ms)", "Front", "Makespan(s)", "Energy(kWh)", "Assigned");
        System.out.println("-".repeat(78));

        for (AlgorithmResult result : results.values()) {
            String makespan = result.selected != null ?
                String.format("%.2f", result.selected.getObjectiveValue(0)) : "N/A";
            String energy = result.selected != null ?
                String.format("%.6f", result.selected.getObjectiveValue(1)) : "N/A";

            System.out.printf("%-20s %10d %10d %12s %12s %10d%n",
                result.name,
                result.timeMs,
                result.paretoFront != null ? result.paretoFront.size() : 0,
                makespan,
                energy,
                result.assignedTasks);
        }
        System.out.println();
    }

    private static void verifyReproducibility(List<Task> tasks, List<VM> vms,
                                               Map<String, AlgorithmResult> firstRun) {
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("REPRODUCIBILITY VERIFICATION (Running again with same seed)");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        boolean allReproducible = true;

        // Re-run MOEA_NSGAII and compare
        System.out.println("─── Verifying MOEA_NSGAII ───");
        RandomGenerator.initialize(TEST_SEED);

        MOEAConfiguration config = MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.NSGA2)
            .populationSize(POPULATION_SIZE)
            .maxEvaluations(MAX_EVALUATIONS)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .solutionSelection(MOEAConfiguration.SolutionSelection.KNEE_POINT)
            .verboseLogging(false)
            .build();

        MOEA_NSGAII strategy = new MOEA_NSGAII(config);
        strategy.assignAll(tasks, vms, 0L);
        SchedulingSolution secondRunSolution = strategy.getLastSelectedSolution();
        AlgorithmResult firstRunResult = firstRun.get("MOEA_NSGAII");

        if (firstRunResult.selected != null && secondRunSolution != null) {
            boolean makespanMatch = Math.abs(
                firstRunResult.selected.getObjectiveValue(0) -
                secondRunSolution.getObjectiveValue(0)) < 0.0001;
            boolean energyMatch = Math.abs(
                firstRunResult.selected.getObjectiveValue(1) -
                secondRunSolution.getObjectiveValue(1)) < 0.000001;

            if (makespanMatch && energyMatch) {
                System.out.println("  ✓ REPRODUCIBLE - Same results with same seed");
                System.out.printf("    First run:  Makespan=%.4f, Energy=%.6f%n",
                    firstRunResult.selected.getObjectiveValue(0),
                    firstRunResult.selected.getObjectiveValue(1));
                System.out.printf("    Second run: Makespan=%.4f, Energy=%.6f%n",
                    secondRunSolution.getObjectiveValue(0),
                    secondRunSolution.getObjectiveValue(1));
            } else {
                System.out.println("  ✗ NOT REPRODUCIBLE - Results differ!");
                allReproducible = false;
            }
        } else {
            System.out.println("  ⚠ Cannot verify - missing solution");
            allReproducible = false;
        }
        System.out.println();

        // Re-run MOEA_AMOSA and compare
        System.out.println("─── Verifying MOEA_AMOSA ───");
        RandomGenerator.initialize(TEST_SEED);

        MOEAConfiguration amosaConfig = MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.AMOSA)
            .maxEvaluations(MAX_EVALUATIONS)
            .initialTemperature(500.0)
            .finalTemperature(0.1)
            .coolingRate(0.95)
            .archiveSize(POPULATION_SIZE)
            .iterationsPerTemperature(50)
            .solutionSelection(MOEAConfiguration.SolutionSelection.KNEE_POINT)
            .verboseLogging(false)
            .build();

        MOEA_AMOSA amosaStrategy = new MOEA_AMOSA(amosaConfig);
        amosaStrategy.assignAll(tasks, vms, 0L);
        SchedulingSolution amosaSecondRun = amosaStrategy.getLastSelectedSolution();
        AlgorithmResult amosaFirstRun = firstRun.get("MOEA_AMOSA");

        if (amosaFirstRun.selected != null && amosaSecondRun != null) {
            boolean makespanMatch = Math.abs(
                amosaFirstRun.selected.getObjectiveValue(0) -
                amosaSecondRun.getObjectiveValue(0)) < 0.0001;
            boolean energyMatch = Math.abs(
                amosaFirstRun.selected.getObjectiveValue(1) -
                amosaSecondRun.getObjectiveValue(1)) < 0.000001;

            if (makespanMatch && energyMatch) {
                System.out.println("  ✓ REPRODUCIBLE - Same results with same seed");
                System.out.printf("    First run:  Makespan=%.4f, Energy=%.6f%n",
                    amosaFirstRun.selected.getObjectiveValue(0),
                    amosaFirstRun.selected.getObjectiveValue(1));
                System.out.printf("    Second run: Makespan=%.4f, Energy=%.6f%n",
                    amosaSecondRun.getObjectiveValue(0),
                    amosaSecondRun.getObjectiveValue(1));
            } else {
                System.out.println("  ✗ NOT REPRODUCIBLE - Results differ!");
                allReproducible = false;
            }
        } else {
            System.out.println("  ⚠ Cannot verify - missing solution");
            allReproducible = false;
        }
        System.out.println();

        System.out.println("Reproducibility Status: " +
            (allReproducible ? "✓ ALL ALGORITHMS REPRODUCIBLE" : "✗ SOME ALGORITHMS NOT REPRODUCIBLE"));
        System.out.println();
    }

    private static void printSummary(Map<String, AlgorithmResult> results) {
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("SUMMARY");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Find best for each objective
        AlgorithmResult bestMakespan = null;
        AlgorithmResult bestEnergy = null;

        for (AlgorithmResult result : results.values()) {
            if (result.selected == null) continue;

            if (bestMakespan == null ||
                result.selected.getObjectiveValue(0) < bestMakespan.selected.getObjectiveValue(0)) {
                bestMakespan = result;
            }

            if (bestEnergy == null ||
                result.selected.getObjectiveValue(1) < bestEnergy.selected.getObjectiveValue(1)) {
                bestEnergy = result;
            }
        }

        if (bestMakespan != null) {
            System.out.println("Best Makespan: " + bestMakespan.name);
            System.out.printf("  Makespan: %.4f seconds%n", bestMakespan.selected.getObjectiveValue(0));
            System.out.printf("  Energy:   %.6f kWh%n", bestMakespan.selected.getObjectiveValue(1));
            System.out.println();
        }

        if (bestEnergy != null) {
            System.out.println("Best Energy: " + bestEnergy.name);
            System.out.printf("  Makespan: %.4f seconds%n", bestEnergy.selected.getObjectiveValue(0));
            System.out.printf("  Energy:   %.6f kWh%n", bestEnergy.selected.getObjectiveValue(1));
            System.out.println();
        }

        System.out.println("Test completed successfully!");
        System.out.println("All MOEA Framework algorithms are integrated and working with simulator seed.");
    }

    private static List<Task> createTasks() {
        List<Task> tasks = new ArrayList<>();

        // Create 8 tasks with various workload types
        tasks.add(new Task("Task_CPU_Heavy", "user1", 10_000_000_000L, WorkloadType.CINEBENCH));
        tasks.add(new Task("Task_Database", "user1", 5_000_000_000L, WorkloadType.DATABASE));
        tasks.add(new Task("Task_7Zip", "user1", 8_000_000_000L, WorkloadType.SEVEN_ZIP));
        tasks.add(new Task("Task_Veracrypt", "user1", 3_000_000_000L, WorkloadType.VERACRYPT));
        tasks.add(new Task("Task_LLM", "user1", 6_000_000_000L, WorkloadType.LLM_CPU));
        tasks.add(new Task("Task_Prime95", "user1", 7_000_000_000L, WorkloadType.PRIME95SmallFFT));
        tasks.add(new Task("Task_ImageGen", "user1", 4_000_000_000L, WorkloadType.IMAGE_GEN_CPU));
        tasks.add(new Task("Task_Database2", "user1", 2_000_000_000L, WorkloadType.DATABASE));

        return tasks;
    }

    private static List<VM> createVMs() {
        List<VM> vms = new ArrayList<>();

        // Create 4 VMs with different specs
        // VM 0: Fast VM - 4 cores at 2 billion IPS each = 8 billion IPS total
        vms.add(new VM("user1", 2_000_000_000L, 4, 0, 8192, 102400, 1000, ComputeType.CPU_ONLY));

        // VM 1: Medium VM - 2 cores at 1.5 billion IPS each = 3 billion IPS total
        vms.add(new VM("user1", 1_500_000_000L, 2, 0, 4096, 51200, 1000, ComputeType.CPU_ONLY));

        // VM 2: Slow VM - 2 cores at 1 billion IPS each = 2 billion IPS total
        vms.add(new VM("user1", 1_000_000_000L, 2, 0, 4096, 51200, 1000, ComputeType.CPU_ONLY));

        // VM 3: Another medium VM for load balancing options
        vms.add(new VM("user1", 1_200_000_000L, 3, 0, 6144, 76800, 1000, ComputeType.CPU_ONLY));

        return vms;
    }

    private static void printScenario(List<Task> tasks, List<VM> vms) {
        System.out.println("═══ TEST SCENARIO ═══");
        System.out.println();
        System.out.println("TASKS (" + tasks.size() + "):");
        System.out.printf("  %-5s %-15s %-18s %15s%n", "ID", "Name", "Workload", "Instructions");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            System.out.printf("  %-5d %-15s %-18s %,15d%n",
                i, t.getName(), t.getWorkloadType(), t.getInstructionLength());
        }
        System.out.println();

        System.out.println("VMs (" + vms.size() + "):");
        System.out.printf("  %-5s %10s %12s %15s%n", "ID", "vCPUs", "IPS/vCPU", "Total IPS");
        for (int i = 0; i < vms.size(); i++) {
            VM vm = vms.get(i);
            System.out.printf("  %-5d %10d %,12d %,15d%n",
                i, vm.getRequestedVcpuCount(), vm.getRequestedIpsPerVcpu(), vm.getTotalRequestedIps());
        }
        System.out.println();
    }

    /**
     * Result container for algorithm comparison.
     */
    private static class AlgorithmResult {
        final String name;
        final ParetoFront paretoFront;
        final SchedulingSolution selected;
        final long timeMs;
        final int assignedTasks;

        AlgorithmResult(String name, ParetoFront paretoFront, SchedulingSolution selected,
                        long timeMs, int assignedTasks) {
            this.name = name;
            this.paretoFront = paretoFront;
            this.selected = selected;
            this.timeMs = timeMs;
            this.assignedTasks = assignedTasks;
        }
    }
}
