package com.cloudsimulator;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.TaskAssignmentStep;
import com.cloudsimulator.PlacementStrategy.task.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.*;

import java.util.Map;

/**
 * Test for the TaskAssignmentStep implementation.
 *
 * Tests all three basic strategies plus the NSGA-II metaheuristic strategy.
 */
public class TaskAssignmentStepTest {

    public static void main(String[] args) {
        System.out.println("=== TaskAssignmentStep Test ===\n");

        // Basic strategy tests
        testFirstAvailableStrategy();
        testShortestQueueStrategy();
        testWorkloadAwareStrategy();

        // Strategy differentiation test
        testStrategyDifferentiation();

        // Compute type compatibility test
        testComputeTypeCompatibility();

        // NSGA-II tests
        testNSGA2BasicOptimization();
        testNSGA2ParetoFront();

        // Edge cases
        testEdgeCases();

        System.out.println("\n=== All Tests Completed ===");
    }

    /**
     * Creates a test context with multiple VMs and tasks.
     *
     * Setup:
     * - 1 datacenter with 2 hosts
     * - 1 user with 3 VMs and 6 tasks
     * - VMs have different processing power
     */
    private static SimulationContext createTestContext() {
        SimulationContext context = new SimulationContext();

        // Create datacenter
        CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);
        context.addDatacenter(dc);

        // Create hosts
        Host host1 = createHost(16, 2, 65536, ComputeType.CPU_GPU_MIXED);
        Host host2 = createHost(8, 0, 32768, ComputeType.CPU_ONLY);

        dc.addHost(host1);
        dc.addHost(host2);
        context.addHost(host1);
        context.addHost(host2);

        // Create user
        User user = new User("TestUser");
        user.addSelectedDatacenter(dc.getId());
        context.addUser(user);

        // Create VMs with different specs - assign to hosts manually
        VM vm1 = createVM(user.getName(), 4, 1, 8192, ComputeType.CPU_GPU_MIXED);
        VM vm2 = createVM(user.getName(), 4, 0, 8192, ComputeType.CPU_ONLY);
        VM vm3 = createVM(user.getName(), 2, 0, 4096, ComputeType.CPU_ONLY);

        // Assign VMs to hosts (simulating VMPlacementStep completion)
        host1.assignVM(vm1);
        vm1.start();
        host2.assignVM(vm2);
        vm2.start();
        host2.assignVM(vm3);
        vm3.start();

        user.addVirtualMachine(vm1);
        user.addVirtualMachine(vm2);
        user.addVirtualMachine(vm3);
        context.addVM(vm1);
        context.addVM(vm2);
        context.addVM(vm3);

        // Create tasks with different workloads and sizes
        Task task1 = createTask("SevenZip1", user.getName(), WorkloadType.SEVEN_ZIP, 2_000_000_000L);
        Task task2 = createTask("Database1", user.getName(), WorkloadType.DATABASE, 1_000_000_000L);
        Task task3 = createTask("Cinebench1", user.getName(), WorkloadType.CINEBENCH, 3_000_000_000L);
        Task task4 = createTask("Prime95", user.getName(), WorkloadType.PRIME95SmallFFT, 1_500_000_000L);
        Task task5 = createTask("SevenZip2", user.getName(), WorkloadType.SEVEN_ZIP, 2_500_000_000L);
        Task task6 = createTask("LLM_GPU", user.getName(), WorkloadType.LLM_GPU, 4_000_000_000L);

        user.addTask(task1);
        user.addTask(task2);
        user.addTask(task3);
        user.addTask(task4);
        user.addTask(task5);
        user.addTask(task6);

        context.addTask(task1);
        context.addTask(task2);
        context.addTask(task3);
        context.addTask(task4);
        context.addTask(task5);
        context.addTask(task6);

        return context;
    }

    private static Host createHost(int cpuCores, int gpus, long ramMB, ComputeType computeType) {
        Host host = new Host(2_500_000_000L, cpuCores, computeType, gpus);
        host.setRamCapacityMB(ramMB);
        host.setNetworkCapacityMbps(10000);
        host.setHardDriveCapacityMB(1024 * 1024);
        return host;
    }

    private static VM createVM(String userId, int vcpus, int gpus, long ramMB, ComputeType computeType) {
        return new VM(userId, 2_000_000_000L, vcpus, gpus, ramMB, 51200, 1000, computeType);
    }

    private static Task createTask(String name, String userId, WorkloadType workloadType, long instructions) {
        return new Task(name, userId, instructions, workloadType);
    }

    private static void testFirstAvailableStrategy() {
        System.out.println("Test 1: FirstAvailable Strategy");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();
        TaskAssignmentStep step = new TaskAssignmentStep(new FirstAvailableTaskAssignmentStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  Tasks assigned: " + step.getTasksAssigned());
        System.out.println("  Tasks failed: " + step.getTasksFailed());
        System.out.println("  Active VMs: " + step.getActiveVMCount());

        printTaskDistribution(context, step);

        boolean passed = step.getTasksAssigned() == 6 && step.getTasksFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testShortestQueueStrategy() {
        System.out.println("Test 2: ShortestQueue Strategy");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();
        TaskAssignmentStep step = new TaskAssignmentStep(new ShortestQueueTaskAssignmentStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  Tasks assigned: " + step.getTasksAssigned());
        System.out.println("  Tasks failed: " + step.getTasksFailed());
        System.out.println("  Active VMs: " + step.getActiveVMCount());

        printTaskDistribution(context, step);

        // ShortestQueue should balance task count across VMs
        boolean passed = step.getTasksAssigned() == 6 && step.getTasksFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testWorkloadAwareStrategy() {
        System.out.println("Test 3: WorkloadAware Strategy");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();
        TaskAssignmentStep step = new TaskAssignmentStep(new WorkloadAwareTaskAssignmentStrategy());

        step.execute(context);

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  Tasks assigned: " + step.getTasksAssigned());
        System.out.println("  Tasks failed: " + step.getTasksFailed());
        System.out.println("  Active VMs: " + step.getActiveVMCount());

        printTaskDistribution(context, step);

        boolean passed = step.getTasksAssigned() == 6 && step.getTasksFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testStrategyDifferentiation() {
        System.out.println("Test 4: Strategy Differentiation");
        System.out.println("-".repeat(60));
        System.out.println("  Setup: 2 VMs with same compute type, 4 tasks of varying sizes");
        System.out.println("  Goal: Show different strategies produce different distributions\n");

        // Run each strategy and collect distributions
        int[] firstAvailable = runDifferentiationScenario(new FirstAvailableTaskAssignmentStrategy());
        int[] shortestQueue = runDifferentiationScenario(new ShortestQueueTaskAssignmentStrategy());
        int[] workloadAware = runDifferentiationScenario(new WorkloadAwareTaskAssignmentStrategy());

        System.out.println("\n  Summary (tasks per VM):");
        System.out.println("  +----------------------+--------+--------+");
        System.out.println("  | Strategy             | VM1    | VM2    |");
        System.out.println("  +----------------------+--------+--------+");
        System.out.printf("  | FirstAvailable       |   %d    |   %d    |%n", firstAvailable[0], firstAvailable[1]);
        System.out.printf("  | ShortestQueue        |   %d    |   %d    |%n", shortestQueue[0], shortestQueue[1]);
        System.out.printf("  | WorkloadAware        |   %d    |   %d    |%n", workloadAware[0], workloadAware[1]);
        System.out.println("  +----------------------+--------+--------+");

        // Check differentiation
        boolean firstAvailableImbalanced = Math.abs(firstAvailable[0] - firstAvailable[1]) >= 2;
        boolean shortestQueueBalanced = Math.abs(shortestQueue[0] - shortestQueue[1]) <= 1;

        System.out.println("\n  Differentiation Checks:");
        System.out.println("    FirstAvailable creates imbalance: " +
            (firstAvailableImbalanced ? "PASSED" : "FAILED"));
        System.out.println("    ShortestQueue balances task count: " +
            (shortestQueueBalanced ? "PASSED" : "FAILED"));

        boolean passed = firstAvailableImbalanced && shortestQueueBalanced;
        System.out.println("\n  Overall Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static int[] runDifferentiationScenario(TaskAssignmentStrategy strategy) {
        SimulationContext context = createDifferentiationContext();
        TaskAssignmentStep step = new TaskAssignmentStep(strategy);
        step.execute(context);

        System.out.println("  " + strategy.getStrategyName() + ":");
        System.out.printf("    Tasks assigned: %d, Active VMs: %d%n",
            step.getTasksAssigned(), step.getActiveVMCount());

        // Count tasks per VM
        int[] distribution = new int[2];
        int vmIndex = 0;
        for (VM vm : context.getVms()) {
            distribution[vmIndex] = vm.getAssignedTasks().size();
            System.out.printf("    VM%d: %d tasks%n", vmIndex + 1, distribution[vmIndex]);
            vmIndex++;
        }

        return distribution;
    }

    private static SimulationContext createDifferentiationContext() {
        SimulationContext context = new SimulationContext();

        CloudDatacenter dc = new CloudDatacenter("DC-Diff", 10, 100000.0);
        context.addDatacenter(dc);

        // Create 2 identical CPU hosts
        Host host1 = createHost(8, 0, 32768, ComputeType.CPU_ONLY);
        Host host2 = createHost(8, 0, 32768, ComputeType.CPU_ONLY);

        dc.addHost(host1);
        dc.addHost(host2);
        context.addHost(host1);
        context.addHost(host2);

        User user = new User("DiffUser");
        user.addSelectedDatacenter(dc.getId());
        context.addUser(user);

        // Create 2 identical VMs
        VM vm1 = createVM(user.getName(), 4, 0, 8192, ComputeType.CPU_ONLY);
        VM vm2 = createVM(user.getName(), 4, 0, 8192, ComputeType.CPU_ONLY);

        host1.assignVM(vm1);
        vm1.start();
        host2.assignVM(vm2);
        vm2.start();

        user.addVirtualMachine(vm1);
        user.addVirtualMachine(vm2);
        context.addVM(vm1);
        context.addVM(vm2);

        // Create 4 CPU tasks
        for (int i = 1; i <= 4; i++) {
            Task task = createTask("Task" + i, user.getName(), WorkloadType.SEVEN_ZIP, 1_000_000_000L * i);
            user.addTask(task);
            context.addTask(task);
        }

        return context;
    }

    private static void testComputeTypeCompatibility() {
        System.out.println("Test 5: Compute Type Compatibility");
        System.out.println("-".repeat(60));

        SimulationContext context = new SimulationContext();

        CloudDatacenter dc = new CloudDatacenter("DC-Compute", 10, 100000.0);
        context.addDatacenter(dc);

        Host host = createHost(16, 4, 65536, ComputeType.CPU_GPU_MIXED);
        dc.addHost(host);
        context.addHost(host);

        User user = new User("ComputeUser");
        user.addSelectedDatacenter(dc.getId());
        context.addUser(user);

        // CPU-only VM
        VM cpuVM = createVM(user.getName(), 4, 0, 8192, ComputeType.CPU_ONLY);
        // GPU-capable VM
        VM gpuVM = createVM(user.getName(), 4, 2, 8192, ComputeType.CPU_GPU_MIXED);

        host.assignVM(cpuVM);
        cpuVM.start();
        host.assignVM(gpuVM);
        gpuVM.start();

        user.addVirtualMachine(cpuVM);
        user.addVirtualMachine(gpuVM);
        context.addVM(cpuVM);
        context.addVM(gpuVM);

        // CPU task (should go to either VM)
        Task cpuTask = createTask("CPUTask", user.getName(), WorkloadType.SEVEN_ZIP, 1_000_000_000L);
        // GPU task (should only go to GPU VM)
        Task gpuTask = createTask("GPUTask", user.getName(), WorkloadType.LLM_GPU, 2_000_000_000L);

        user.addTask(cpuTask);
        user.addTask(gpuTask);
        context.addTask(cpuTask);
        context.addTask(gpuTask);

        TaskAssignmentStep step = new TaskAssignmentStep(new FirstAvailableTaskAssignmentStrategy());
        step.execute(context);

        System.out.println("  Tasks assigned: " + step.getTasksAssigned());
        System.out.println("  Tasks failed: " + step.getTasksFailed());

        // Verify GPU task is on GPU VM
        boolean gpuTaskOnGpuVM = gpuTask.getAssignedVmId() != null &&
            gpuTask.getAssignedVmId().equals(gpuVM.getId());

        System.out.println("  GPU task assigned to GPU VM: " + gpuTaskOnGpuVM);
        System.out.println("  Result: " + (gpuTaskOnGpuVM ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testNSGA2BasicOptimization() {
        System.out.println("Test 6: NSGA-II Basic Optimization");
        System.out.println("-".repeat(60));

        SimulationContext context = createTestContext();

        // Configure NSGA-II with small population for quick test
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(20)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .addObjective(new MakespanObjective())
            .addObjective(new EnergyObjective())
            .terminationCondition(new GenerationCountTermination(10))
            .randomSeed(42L)
            .verboseLogging(false)
            .build();

        NSGA2TaskSchedulingStrategy strategy = new NSGA2TaskSchedulingStrategy(config);
        TaskAssignmentStep step = new TaskAssignmentStep(strategy);

        long startTime = System.currentTimeMillis();
        step.execute(context);
        long endTime = System.currentTimeMillis();

        System.out.println("  Strategy: " + step.getStrategy().getStrategyName());
        System.out.println("  Tasks assigned: " + step.getTasksAssigned());
        System.out.println("  Tasks failed: " + step.getTasksFailed());
        System.out.println("  Execution time: " + (endTime - startTime) + "ms");

        ParetoFront front = strategy.getLastParetoFront();
        if (front != null) {
            System.out.println("  Pareto front size: " + front.size());
        }

        boolean passed = step.getTasksAssigned() == 6 && step.getTasksFailed() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testNSGA2ParetoFront() {
        System.out.println("Test 7: NSGA-II Pareto Front Quality");
        System.out.println("-".repeat(60));

        // Create a simple context for Pareto front analysis
        SimulationContext context = new SimulationContext();

        CloudDatacenter dc = new CloudDatacenter("DC-Pareto", 10, 100000.0);
        context.addDatacenter(dc);

        Host host = createHost(32, 4, 131072, ComputeType.CPU_GPU_MIXED);
        dc.addHost(host);
        context.addHost(host);

        User user = new User("ParetoUser");
        user.addSelectedDatacenter(dc.getId());
        context.addUser(user);

        // Create 3 VMs with different power characteristics
        VM vm1 = new VM(user.getName(), 3_000_000_000L, 8, 0, 16384, 51200, 1000, ComputeType.CPU_ONLY);
        VM vm2 = new VM(user.getName(), 2_000_000_000L, 4, 0, 8192, 51200, 1000, ComputeType.CPU_ONLY);
        VM vm3 = new VM(user.getName(), 1_000_000_000L, 2, 0, 4096, 51200, 1000, ComputeType.CPU_ONLY);

        host.assignVM(vm1);
        vm1.start();
        host.assignVM(vm2);
        vm2.start();
        host.assignVM(vm3);
        vm3.start();

        user.addVirtualMachine(vm1);
        user.addVirtualMachine(vm2);
        user.addVirtualMachine(vm3);
        context.addVM(vm1);
        context.addVM(vm2);
        context.addVM(vm3);

        // Create tasks
        for (int i = 1; i <= 5; i++) {
            Task task = createTask("T" + i, user.getName(), WorkloadType.SEVEN_ZIP, 500_000_000L * i);
            user.addTask(task);
            context.addTask(task);
        }

        // Run NSGA-II with more generations
        NSGA2Configuration config = NSGA2Configuration.builder()
            .populationSize(30)
            .crossoverRate(0.9)
            .mutationRate(0.1)
            .addObjective(new MakespanObjective())
            .addObjective(new EnergyObjective())
            .terminationCondition(new GenerationCountTermination(30))
            .randomSeed(123L)
            .build();

        NSGA2TaskSchedulingStrategy strategy = new NSGA2TaskSchedulingStrategy(config);

        // Get Pareto front directly
        ParetoFront front = strategy.optimize(context.getTasks(), context.getVms());

        System.out.println("  Pareto front size: " + front.size());
        System.out.println("  Objectives: " + front.getObjectiveNames());

        if (!front.isEmpty()) {
            double[][] ranges = front.getObjectiveRanges();
            System.out.printf("  Makespan range: [%.2f, %.2f] seconds%n", ranges[0][0], ranges[0][1]);
            System.out.printf("  Energy range: [%.2f, %.2f] joules%n", ranges[1][0], ranges[1][1]);

            SchedulingSolution kneePoint = front.getKneePoint();
            System.out.printf("  Knee point: Makespan=%.2f, Energy=%.2f%n",
                kneePoint.getObjectiveValue(0), kneePoint.getObjectiveValue(1));

            SchedulingSolution bestMakespan = front.getBestForObjective(0);
            SchedulingSolution bestEnergy = front.getBestForObjective(1);
            System.out.printf("  Best makespan solution: %.2f seconds%n", bestMakespan.getObjectiveValue(0));
            System.out.printf("  Best energy solution: %.2f joules%n", bestEnergy.getObjectiveValue(1));
        }

        boolean passed = front.size() >= 1;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testEdgeCases() {
        System.out.println("Test 8: Edge Cases");
        System.out.println("-".repeat(60));

        // Test 8a: Empty tasks list
        System.out.println("  Test 8a: Empty tasks list");
        SimulationContext emptyContext = new SimulationContext();
        TaskAssignmentStep step = new TaskAssignmentStep();
        step.execute(emptyContext);
        boolean test8a = step.getTasksAssigned() == 0 && step.getTasksFailed() == 0;
        System.out.println("    Result: " + (test8a ? "PASSED" : "FAILED"));

        // Test 8b: Default strategy (FirstAvailable)
        System.out.println("  Test 8b: Default strategy is FirstAvailable");
        TaskAssignmentStep defaultStep = new TaskAssignmentStep();
        boolean test8b = defaultStep.getStrategy() instanceof FirstAvailableTaskAssignmentStrategy;
        System.out.println("    Strategy: " + defaultStep.getStrategy().getStrategyName());
        System.out.println("    Result: " + (test8b ? "PASSED" : "FAILED"));

        // Test 8c: Strategy descriptions
        System.out.println("  Test 8c: Strategy descriptions available");
        TaskAssignmentStrategy[] strategies = {
            new FirstAvailableTaskAssignmentStrategy(),
            new ShortestQueueTaskAssignmentStrategy(),
            new WorkloadAwareTaskAssignmentStrategy()
        };
        boolean test8c = true;
        for (TaskAssignmentStrategy s : strategies) {
            if (s.getDescription() == null || s.getDescription().isEmpty()) {
                test8c = false;
            }
            System.out.println("    " + s.getStrategyName() + ": " +
                (s.getDescription().length() > 50 ? s.getDescription().substring(0, 50) + "..." : s.getDescription()));
        }
        System.out.println("    Result: " + (test8c ? "PASSED" : "FAILED"));

        // Test 8d: Metrics recorded
        System.out.println("  Test 8d: Metrics recorded correctly");
        SimulationContext context = createTestContext();
        TaskAssignmentStep metricStep = new TaskAssignmentStep(new FirstAvailableTaskAssignmentStrategy());
        metricStep.execute(context);
        boolean test8d = context.getMetric("taskAssignment.tasksAssigned") != null &&
                        context.getMetric("taskAssignment.tasksFailed") != null &&
                        context.getMetric("taskAssignment.strategy") != null;
        System.out.println("    taskAssignment.tasksAssigned: " + context.getMetric("taskAssignment.tasksAssigned"));
        System.out.println("    taskAssignment.tasksFailed: " + context.getMetric("taskAssignment.tasksFailed"));
        System.out.println("    taskAssignment.strategy: " + context.getMetric("taskAssignment.strategy"));
        System.out.println("    Result: " + (test8d ? "PASSED" : "FAILED"));

        // Test 8e: No running VMs
        System.out.println("  Test 8e: No running VMs (all tasks should fail)");
        SimulationContext noVmContext = new SimulationContext();
        CloudDatacenter dc = new CloudDatacenter("DC-NoVM", 10, 100000.0);
        noVmContext.addDatacenter(dc);
        User user = new User("NoVMUser");
        user.addSelectedDatacenter(dc.getId());
        noVmContext.addUser(user);
        Task task = createTask("OrphanTask", user.getName(), WorkloadType.SEVEN_ZIP, 1_000_000_000L);
        user.addTask(task);
        noVmContext.addTask(task);

        TaskAssignmentStep noVmStep = new TaskAssignmentStep();
        noVmStep.execute(noVmContext);
        boolean test8e = noVmStep.getTasksFailed() == 1;
        System.out.println("    Tasks failed: " + noVmStep.getTasksFailed());
        System.out.println("    Result: " + (test8e ? "PASSED" : "FAILED"));

        System.out.println();
    }

    private static void printTaskDistribution(SimulationContext context, TaskAssignmentStep step) {
        System.out.println("  Task Distribution:");
        for (VM vm : context.getVms()) {
            int taskCount = vm.getAssignedTasks().size();
            long totalInstructions = 0;
            StringBuilder taskNames = new StringBuilder();
            for (Task task : vm.getAssignedTasks()) {
                totalInstructions += task.getInstructionLength();
                if (taskNames.length() > 0) taskNames.append(", ");
                taskNames.append(task.getName());
            }
            System.out.printf("    VM %d (%s): %d tasks, %.2fB instructions [%s]%n",
                vm.getId(),
                vm.getComputeType(),
                taskCount,
                totalInstructions / 1_000_000_000.0,
                taskNames);
        }
    }
}
