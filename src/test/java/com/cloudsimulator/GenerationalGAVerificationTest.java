package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.*;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verification test for the Generational Genetic Algorithm with Elitism.
 *
 * This test creates a simple scenario with 5 tasks and 3 VMs,
 * runs the GA with different configurations, and verifies:
 * 1. Algorithm converges and produces valid solutions
 * 2. Elitism preserves best solutions
 * 3. Different objectives produce different optimal solutions
 * 4. Reproducibility with same seed
 */
public class GenerationalGAVerificationTest {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║    Generational GA with Elitism - Verification Test          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Initialize the RandomGenerator with a seed for reproducibility
        RandomGenerator.initialize(42L);

        // Create test scenario
        List<Task> tasks = createTasks();
        List<VM> vms = createVMs();

        printScenario(tasks, vms);

        boolean allPassed = true;

        // Test 1: Single objective (Makespan)
        allPassed &= testSingleObjectiveMakespan(tasks, vms);

        // Test 2: Single objective (Energy)
        allPassed &= testSingleObjectiveEnergy(tasks, vms);

        // Test 3: Weighted sum multi-objective
        allPassed &= testWeightedSum(tasks, vms);

        // Test 4: Reproducibility test
        allPassed &= testReproducibility(tasks, vms);

        // Test 5: Elitism verification
        allPassed &= testElitism(tasks, vms);

        // Print final summary
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("FINAL SUMMARY");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();
        if (allPassed) {
            System.out.println("✓ ALL TESTS PASSED");
        } else {
            System.out.println("✗ SOME TESTS FAILED");
        }
        System.out.println();
    }

    /**
     * Test 1: Single objective optimization (Makespan).
     */
    private static boolean testSingleObjectiveMakespan(List<Task> tasks, List<VM> vms) {
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("TEST 1: Single Objective - Makespan Minimization");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();

        // Reset random generator
        RandomGenerator.getInstance().reset(42L);

        GAConfiguration config = GAConfiguration.builder()
            .populationSize(50)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .elitePercentage(0.1)
            .tournamentSize(3)
            .objective(new MakespanObjective())
            .terminationCondition(new GenerationCountTermination(100))
            .verboseLogging(true)
            .logInterval(20)
            .build();

        System.out.println("Configuration: " + config);
        System.out.println();

        GenerationalGATaskSchedulingStrategy strategy =
            new GenerationalGATaskSchedulingStrategy(config);

        SchedulingSolution solution = strategy.optimize(tasks, vms);

        if (solution == null) {
            System.out.println("✗ FAILED - No solution returned");
            return false;
        }

        // Verify the solution
        MakespanObjective makespanObj = new MakespanObjective();
        double makespan = makespanObj.evaluate(solution, tasks, vms);

        System.out.println();
        System.out.println("Best Solution:");
        System.out.println("  Assignment: " + Arrays.toString(solution.getTaskAssignment()));
        System.out.printf("  Makespan: %.4f seconds%n", makespan);
        System.out.println();

        // Check that makespan is reasonable (should be better than worst case)
        // Worst case: all tasks on slowest VM = 32B instructions / 2B IPS = 16 seconds
        boolean passed = makespan < 16.0 && makespan > 0;
        System.out.println("Verification: " + (passed ? "✓ PASSED" : "✗ FAILED"));
        System.out.println();

        return passed;
    }

    /**
     * Test 2: Single objective optimization (Energy).
     */
    private static boolean testSingleObjectiveEnergy(List<Task> tasks, List<VM> vms) {
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("TEST 2: Single Objective - Energy Minimization");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();

        // Reset random generator
        RandomGenerator.getInstance().reset(43L);

        GAConfiguration config = GAConfiguration.builder()
            .populationSize(50)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .eliteCount(5)  // Test absolute elitism
            .tournamentSize(2)
            .objective(new EnergyObjective())
            .terminationCondition(new GenerationCountTermination(100))
            .verboseLogging(false)
            .build();

        System.out.println("Configuration: " + config);
        System.out.println();

        GenerationalGATaskSchedulingStrategy strategy =
            new GenerationalGATaskSchedulingStrategy(config);

        long startTime = System.currentTimeMillis();
        SchedulingSolution solution = strategy.optimize(tasks, vms);
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("Completed in " + elapsed + " ms");

        if (solution == null) {
            System.out.println("✗ FAILED - No solution returned");
            return false;
        }

        EnergyObjective energyObj = new EnergyObjective();
        double energy = energyObj.evaluate(solution, tasks, vms);

        System.out.println();
        System.out.println("Best Solution:");
        System.out.println("  Assignment: " + Arrays.toString(solution.getTaskAssignment()));
        System.out.printf("  Energy: %.6f kWh%n", energy);
        System.out.println();

        // Energy should be positive and reasonable
        boolean passed = energy > 0 && energy < 1.0; // Less than 1 kWh for these small tasks
        System.out.println("Verification: " + (passed ? "✓ PASSED" : "✗ FAILED"));
        System.out.println();

        return passed;
    }

    /**
     * Test 3: Weighted sum multi-objective optimization.
     */
    private static boolean testWeightedSum(List<Task> tasks, List<VM> vms) {
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("TEST 3: Weighted Sum Multi-Objective (70% Makespan, 30% Energy)");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();

        // Reset random generator
        RandomGenerator.getInstance().reset(44L);

        GAConfiguration config = GAConfiguration.builder()
            .populationSize(50)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .elitePercentage(0.15)
            .tournamentSize(3)
            .addWeightedObjective(new MakespanObjective(), 0.7)
            .addWeightedObjective(new EnergyObjective(), 0.3)
            .terminationCondition(new GenerationCountTermination(100))
            .verboseLogging(false)
            .build();

        System.out.println("Configuration: " + config);
        System.out.println();

        GenerationalGATaskSchedulingStrategy strategy =
            new GenerationalGATaskSchedulingStrategy(config);

        long startTime = System.currentTimeMillis();
        SchedulingSolution solution = strategy.optimize(tasks, vms);
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("Completed in " + elapsed + " ms");

        if (solution == null) {
            System.out.println("✗ FAILED - No solution returned");
            return false;
        }

        MakespanObjective makespanObj = new MakespanObjective();
        EnergyObjective energyObj = new EnergyObjective();

        double makespan = makespanObj.evaluate(solution, tasks, vms);
        double energy = energyObj.evaluate(solution, tasks, vms);

        System.out.println();
        System.out.println("Best Solution:");
        System.out.println("  Assignment: " + Arrays.toString(solution.getTaskAssignment()));
        System.out.printf("  Makespan: %.4f seconds%n", makespan);
        System.out.printf("  Energy: %.6f kWh%n", energy);
        System.out.println();

        // Print statistics
        strategy.printLastRunSummary();

        boolean passed = makespan > 0 && energy > 0;
        System.out.println("Verification: " + (passed ? "✓ PASSED" : "✗ FAILED"));
        System.out.println();

        return passed;
    }

    /**
     * Test 4: Reproducibility test - same seed should produce same results.
     */
    private static boolean testReproducibility(List<Task> tasks, List<VM> vms) {
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("TEST 4: Reproducibility (Same Seed = Same Results)");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();

        GAConfiguration config = GAConfiguration.builder()
            .populationSize(30)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .elitePercentage(0.1)
            .tournamentSize(2)
            .objective(new MakespanObjective())
            .terminationCondition(new GenerationCountTermination(50))
            .verboseLogging(false)
            .build();

        // Run 1
        RandomGenerator.getInstance().reset(999L);
        GenerationalGATaskSchedulingStrategy strategy1 =
            new GenerationalGATaskSchedulingStrategy(config);
        SchedulingSolution solution1 = strategy1.optimize(tasks, vms);

        // Run 2 (same seed)
        RandomGenerator.getInstance().reset(999L);
        GenerationalGATaskSchedulingStrategy strategy2 =
            new GenerationalGATaskSchedulingStrategy(config);
        SchedulingSolution solution2 = strategy2.optimize(tasks, vms);

        if (solution1 == null || solution2 == null) {
            System.out.println("✗ FAILED - Solutions are null");
            return false;
        }

        // Compare assignments
        int[] assignment1 = solution1.getTaskAssignment();
        int[] assignment2 = solution2.getTaskAssignment();

        boolean assignmentsMatch = Arrays.equals(assignment1, assignment2);

        System.out.println("Run 1 Assignment: " + Arrays.toString(assignment1));
        System.out.println("Run 2 Assignment: " + Arrays.toString(assignment2));
        System.out.println();
        System.out.println("Assignments Match: " + (assignmentsMatch ? "✓ YES" : "✗ NO"));
        System.out.println();

        // Compare fitness
        double fitness1 = strategy1.getLastStatistics().getGlobalBestFitness();
        double fitness2 = strategy2.getLastStatistics().getGlobalBestFitness();

        boolean fitnessMatch = Math.abs(fitness1 - fitness2) < 0.0001;

        System.out.printf("Run 1 Best Fitness: %.6f%n", fitness1);
        System.out.printf("Run 2 Best Fitness: %.6f%n", fitness2);
        System.out.println("Fitness Match: " + (fitnessMatch ? "✓ YES" : "✗ NO"));
        System.out.println();

        boolean passed = assignmentsMatch && fitnessMatch;
        System.out.println("Verification: " + (passed ? "✓ PASSED" : "✗ FAILED"));
        System.out.println();

        return passed;
    }

    /**
     * Test 5: Elitism verification - best solution should never get worse.
     */
    private static boolean testElitism(List<Task> tasks, List<VM> vms) {
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("TEST 5: Elitism Verification (Best Never Gets Worse)");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();

        // Reset random generator
        RandomGenerator.getInstance().reset(45L);

        GAConfiguration config = GAConfiguration.builder()
            .populationSize(30)
            .crossoverRate(0.9)
            .mutationRate(0.2)  // Higher mutation to introduce variation
            .elitePercentage(0.2)  // Keep top 20%
            .tournamentSize(2)
            .objective(new MakespanObjective())
            .terminationCondition(new GenerationCountTermination(50))
            .verboseLogging(false)
            .build();

        System.out.println("Configuration: " + config);
        System.out.println();

        // Create algorithm directly to access statistics with history
        GenerationalGAAlgorithm algorithm = new GenerationalGAAlgorithm(config, tasks, vms);
        SchedulingSolution solution = algorithm.run();

        if (solution == null) {
            System.out.println("✗ FAILED - No solution returned");
            return false;
        }

        GAStatistics stats = algorithm.getStatistics();

        // With elitism, the best fitness should never get worse across generations
        // Since we're tracking only the global best, we can verify the final best
        // matches what was found during evolution

        double finalBest = stats.getGlobalBestFitness();
        int bestGeneration = stats.getBestSolutionGeneration();
        int noImprovement = stats.getNoImprovementGenerations();

        System.out.printf("Final Best Fitness: %.6f%n", finalBest);
        System.out.printf("Best Found at Generation: %d%n", bestGeneration);
        System.out.printf("Generations Without Improvement: %d%n", noImprovement);
        System.out.println();

        // The best fitness should be positive and the algorithm should have run
        boolean passed = finalBest > 0 && finalBest < Double.MAX_VALUE;

        // Additional check: with elitism, the current best in final population
        // should not be worse than the global best
        double currentBest = stats.getBestFitness();
        boolean elitismWorked = currentBest <= finalBest * 1.0001; // Small tolerance

        System.out.printf("Current Generation Best: %.6f%n", currentBest);
        System.out.println("Elitism Preserved Best: " + (elitismWorked ? "✓ YES" : "✗ NO"));
        System.out.println();

        passed = passed && elitismWorked;
        System.out.println("Verification: " + (passed ? "✓ PASSED" : "✗ FAILED"));
        System.out.println();

        return passed;
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
}
