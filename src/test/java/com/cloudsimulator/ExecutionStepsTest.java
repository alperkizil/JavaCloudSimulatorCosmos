package com.cloudsimulator;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.*;
import com.cloudsimulator.steps.TaskAssignmentStep;
import com.cloudsimulator.steps.VMExecutionStep;
import com.cloudsimulator.steps.TaskExecutionStep;
import com.cloudsimulator.PlacementStrategy.task.FirstAvailableTaskAssignmentStrategy;

/**
 * Test for VMExecutionStep and TaskExecutionStep implementations.
 *
 * Tests the simulation execution loop (Stage 6) and post-simulation analysis (Stage 7).
 */
public class ExecutionStepsTest {

    public static void main(String[] args) {
        System.out.println("=== VMExecutionStep & TaskExecutionStep Test ===\n");

        // Basic execution tests
        testBasicExecution();
        testMultipleVMsExecution();
        testGPUWorkloadExecution();

        // Edge cases
        testEmptyTasksExecution();
        testNoAssignedVMs();

        // Metrics verification
        testMetricsRecording();

        // Full pipeline test
        testFullExecutionPipeline();

        System.out.println("\n=== All Tests Completed ===");
    }

    /**
     * Creates a simple test context with tasks assigned to VMs.
     *
     * Setup:
     * - 1 datacenter with 1 host
     * - 1 user with 1 VM and 2 tasks
     */
    private static SimulationContext createSimpleTestContext() {
        SimulationContext context = new SimulationContext();

        // Create datacenter
        CloudDatacenter dc = new CloudDatacenter("DC-Test", 10, 100000.0);
        context.addDatacenter(dc);

        // Create host
        Host host = createHost(16, 0, 65536, ComputeType.CPU_ONLY);
        dc.addHost(host);
        context.addHost(host);

        // Create user
        User user = new User("TestUser");
        user.addSelectedDatacenter(dc.getId());
        user.startSession(0);
        context.addUser(user);

        // Create VM
        VM vm = createVM(user.getName(), 4, 0, 8192, ComputeType.CPU_ONLY);
        host.assignVM(vm);
        vm.start();
        user.addVirtualMachine(vm);
        context.addVM(vm);

        // Create small tasks (quick execution)
        // With 4 vCPUs at 2B IPS each = 8B IPS total
        // Task with 8B instructions = 1 second execution
        Task task1 = createTask("Task1", user.getName(), WorkloadType.SEVEN_ZIP, 8_000_000_000L);
        Task task2 = createTask("Task2", user.getName(), WorkloadType.DATABASE, 16_000_000_000L);

        user.addTask(task1);
        user.addTask(task2);
        context.addTask(task1);
        context.addTask(task2);

        // Assign tasks to VM (simulating TaskAssignmentStep)
        task1.assignToVM(vm.getId(), 0);
        vm.assignTask(task1);
        task2.assignToVM(vm.getId(), 0);
        vm.assignTask(task2);

        return context;
    }

    /**
     * Creates a test context with multiple VMs.
     */
    private static SimulationContext createMultiVMTestContext() {
        SimulationContext context = new SimulationContext();

        CloudDatacenter dc = new CloudDatacenter("DC-Multi", 10, 100000.0);
        context.addDatacenter(dc);

        Host host = createHost(32, 0, 131072, ComputeType.CPU_ONLY);
        dc.addHost(host);
        context.addHost(host);

        User user = new User("MultiUser");
        user.addSelectedDatacenter(dc.getId());
        user.startSession(0);
        context.addUser(user);

        // Create 3 VMs with different speeds
        VM vm1 = new VM(user.getName(), 4_000_000_000L, 4, 0, 8192, 51200, 1000, ComputeType.CPU_ONLY);
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

        // Create tasks and distribute across VMs
        // VM1: 16B IPS, VM2: 8B IPS, VM3: 2B IPS
        Task task1 = createTask("Fast1", user.getName(), WorkloadType.SEVEN_ZIP, 16_000_000_000L); // 1s on VM1
        Task task2 = createTask("Medium1", user.getName(), WorkloadType.DATABASE, 8_000_000_000L);  // 1s on VM2
        Task task3 = createTask("Slow1", user.getName(), WorkloadType.CINEBENCH, 4_000_000_000L);   // 2s on VM3

        for (Task task : new Task[]{task1, task2, task3}) {
            user.addTask(task);
            context.addTask(task);
        }

        // Assign tasks
        task1.assignToVM(vm1.getId(), 0);
        vm1.assignTask(task1);
        task2.assignToVM(vm2.getId(), 0);
        vm2.assignTask(task2);
        task3.assignToVM(vm3.getId(), 0);
        vm3.assignTask(task3);

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

    private static void testBasicExecution() {
        System.out.println("Test 1: Basic VM Execution");
        System.out.println("-".repeat(60));

        SimulationContext context = createSimpleTestContext();

        // Run VMExecutionStep
        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        System.out.println("  VMExecutionStep Results:");
        System.out.println("    Total simulation seconds: " + vmExecStep.getTotalSimulationSeconds());
        System.out.println("    Tasks completed: " + vmExecStep.getTasksCompleted());
        System.out.println("    Peak concurrent tasks: " + vmExecStep.getPeakConcurrentTasks());
        System.out.println("    VM utilization ratio: " + String.format("%.2f", vmExecStep.getVmUtilizationRatio()));

        // Run TaskExecutionStep
        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        System.out.println("  TaskExecutionStep Results:");
        System.out.println("    Completed tasks: " + taskExecStep.getCompletedTasks());
        System.out.println("    Makespan: " + taskExecStep.getMakespan() + " seconds");
        System.out.println("    Avg waiting time: " + String.format("%.2f", taskExecStep.getAverageWaitingTime()) + " seconds");
        System.out.println("    Avg turnaround time: " + String.format("%.2f", taskExecStep.getAverageTurnaroundTime()) + " seconds");
        System.out.println("    Throughput: " + String.format("%.4f", taskExecStep.getThroughput()) + " tasks/sec");
        System.out.println("    Users completed: " + taskExecStep.getUsersCompleted());

        // Verify both tasks completed
        boolean passed = vmExecStep.getTasksCompleted() == 2 &&
                        taskExecStep.getCompletedTasks() == 2 &&
                        taskExecStep.getUsersCompleted() == 1;

        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testMultipleVMsExecution() {
        System.out.println("Test 2: Multiple VMs Parallel Execution");
        System.out.println("-".repeat(60));

        SimulationContext context = createMultiVMTestContext();

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        System.out.println("  VMExecutionStep Results:");
        System.out.println("    Total simulation seconds: " + vmExecStep.getTotalSimulationSeconds());
        System.out.println("    Tasks completed: " + vmExecStep.getTasksCompleted());
        System.out.println("    Peak concurrent tasks: " + vmExecStep.getPeakConcurrentTasks());

        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        System.out.println("  TaskExecutionStep Results:");
        System.out.println("    Completed tasks: " + taskExecStep.getCompletedTasks());
        System.out.println("    Makespan: " + taskExecStep.getMakespan() + " seconds");

        // All 3 tasks should complete, max concurrent should be 3 (one per VM)
        boolean passed = vmExecStep.getTasksCompleted() == 3 &&
                        vmExecStep.getPeakConcurrentTasks() == 3;

        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testGPUWorkloadExecution() {
        System.out.println("Test 3: GPU Workload Execution");
        System.out.println("-".repeat(60));

        SimulationContext context = new SimulationContext();

        CloudDatacenter dc = new CloudDatacenter("DC-GPU", 10, 100000.0);
        context.addDatacenter(dc);

        Host host = createHost(16, 4, 65536, ComputeType.CPU_GPU_MIXED);
        host.setNumberOfGpus(4);
        dc.addHost(host);
        context.addHost(host);

        User user = new User("GPUUser");
        user.addSelectedDatacenter(dc.getId());
        user.startSession(0);
        context.addUser(user);

        // GPU-capable VM
        VM gpuVM = new VM(user.getName(), 2_000_000_000L, 4, 2, 16384, 102400, 2000, ComputeType.CPU_GPU_MIXED);
        host.assignVM(gpuVM);
        gpuVM.start();
        user.addVirtualMachine(gpuVM);
        context.addVM(gpuVM);

        // GPU workload task
        Task gpuTask = createTask("LLMInference", user.getName(), WorkloadType.LLM_GPU, 8_000_000_000L);
        user.addTask(gpuTask);
        context.addTask(gpuTask);

        gpuTask.assignToVM(gpuVM.getId(), 0);
        gpuVM.assignTask(gpuTask);

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        System.out.println("  GPU Task completed: " + gpuTask.isCompleted());
        System.out.println("  Execution time: " + gpuTask.getTaskCpuExecTime() + " seconds");

        // Check workload statistics
        TaskExecutionStep.WorkloadStatistics llmGpuStats =
            taskExecStep.getWorkloadStatistics().get(WorkloadType.LLM_GPU);
        System.out.println("  LLM_GPU workload stats: " + llmGpuStats);

        boolean passed = gpuTask.isCompleted() && taskExecStep.getCompletedTasks() == 1;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testEmptyTasksExecution() {
        System.out.println("Test 4: Empty Tasks Execution");
        System.out.println("-".repeat(60));

        SimulationContext context = new SimulationContext();

        CloudDatacenter dc = new CloudDatacenter("DC-Empty", 10, 100000.0);
        context.addDatacenter(dc);

        Host host = createHost(8, 0, 32768, ComputeType.CPU_ONLY);
        dc.addHost(host);
        context.addHost(host);

        User user = new User("EmptyUser");
        user.addSelectedDatacenter(dc.getId());
        context.addUser(user);

        VM vm = createVM(user.getName(), 4, 0, 8192, ComputeType.CPU_ONLY);
        host.assignVM(vm);
        vm.start();
        user.addVirtualMachine(vm);
        context.addVM(vm);

        // No tasks added

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        System.out.println("  Simulation seconds: " + vmExecStep.getTotalSimulationSeconds());
        System.out.println("  Tasks completed: " + vmExecStep.getTasksCompleted());

        boolean passed = vmExecStep.getTotalSimulationSeconds() == 0 &&
                        taskExecStep.getCompletedTasks() == 0;

        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testNoAssignedVMs() {
        System.out.println("Test 5: No Assigned VMs");
        System.out.println("-".repeat(60));

        SimulationContext context = new SimulationContext();

        CloudDatacenter dc = new CloudDatacenter("DC-NoVM", 10, 100000.0);
        context.addDatacenter(dc);

        User user = new User("NoVMUser");
        user.addSelectedDatacenter(dc.getId());
        context.addUser(user);

        // Create VM but don't assign to host
        VM vm = createVM(user.getName(), 4, 0, 8192, ComputeType.CPU_ONLY);
        user.addVirtualMachine(vm);
        context.addVM(vm);

        // Create task
        Task task = createTask("OrphanTask", user.getName(), WorkloadType.SEVEN_ZIP, 1_000_000_000L);
        user.addTask(task);
        context.addTask(task);

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        System.out.println("  VM assigned to host: " + vm.isAssignedToHost());
        System.out.println("  Simulation ran: " + (vmExecStep.getTotalSimulationSeconds() > 0 ? "Yes" : "No"));

        boolean passed = vmExecStep.getTotalSimulationSeconds() == 0;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testMetricsRecording() {
        System.out.println("Test 6: Metrics Recording");
        System.out.println("-".repeat(60));

        SimulationContext context = createSimpleTestContext();

        VMExecutionStep vmExecStep = new VMExecutionStep();
        vmExecStep.execute(context);

        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        // Check VMExecutionStep metrics
        System.out.println("  VMExecutionStep Metrics:");
        Object totalSecs = context.getMetric("vmExecution.totalSimulationSeconds");
        Object tasksCompleted = context.getMetric("vmExecution.tasksCompleted");
        Object utilRatio = context.getMetric("vmExecution.vmUtilizationRatio");

        System.out.println("    vmExecution.totalSimulationSeconds: " + totalSecs);
        System.out.println("    vmExecution.tasksCompleted: " + tasksCompleted);
        System.out.println("    vmExecution.vmUtilizationRatio: " + utilRatio);

        boolean vmMetricsOk = totalSecs != null && tasksCompleted != null && utilRatio != null;

        // Check TaskExecutionStep metrics
        System.out.println("  TaskExecutionStep Metrics:");
        Object makespan = context.getMetric("taskExecution.makespan");
        Object avgWait = context.getMetric("taskExecution.avgWaitingTime");
        Object throughput = context.getMetric("taskExecution.throughput");
        Object usersCompleted = context.getMetric("taskExecution.usersCompleted");

        System.out.println("    taskExecution.makespan: " + makespan);
        System.out.println("    taskExecution.avgWaitingTime: " + avgWait);
        System.out.println("    taskExecution.throughput: " + throughput);
        System.out.println("    taskExecution.usersCompleted: " + usersCompleted);

        boolean taskMetricsOk = makespan != null && avgWait != null && throughput != null;

        // Check user-specific metrics
        Object userCompleted = context.getMetric("taskExecution.user.TestUser.completed");
        System.out.println("    taskExecution.user.TestUser.completed: " + userCompleted);

        boolean passed = vmMetricsOk && taskMetricsOk && userCompleted != null;
        System.out.println("  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }

    private static void testFullExecutionPipeline() {
        System.out.println("Test 7: Full Execution Pipeline (Assignment -> Execution -> Analysis)");
        System.out.println("-".repeat(60));

        // Create context without pre-assigned tasks
        SimulationContext context = new SimulationContext();

        CloudDatacenter dc = new CloudDatacenter("DC-Full", 10, 100000.0);
        context.addDatacenter(dc);

        Host host = createHost(32, 4, 131072, ComputeType.CPU_GPU_MIXED);
        dc.addHost(host);
        context.addHost(host);

        User user = new User("FullUser");
        user.addSelectedDatacenter(dc.getId());
        user.startSession(0);
        context.addUser(user);

        // Create VMs
        VM cpuVM = new VM(user.getName(), 2_000_000_000L, 8, 0, 16384, 102400, 2000, ComputeType.CPU_ONLY);
        VM gpuVM = new VM(user.getName(), 2_000_000_000L, 4, 2, 16384, 102400, 2000, ComputeType.CPU_GPU_MIXED);

        host.assignVM(cpuVM);
        cpuVM.start();
        host.assignVM(gpuVM);
        gpuVM.start();

        user.addVirtualMachine(cpuVM);
        user.addVirtualMachine(gpuVM);
        context.addVM(cpuVM);
        context.addVM(gpuVM);

        // Create diverse tasks
        Task[] tasks = {
            createTask("Compress", user.getName(), WorkloadType.SEVEN_ZIP, 16_000_000_000L),
            createTask("DBQuery", user.getName(), WorkloadType.DATABASE, 8_000_000_000L),
            createTask("Render", user.getName(), WorkloadType.CINEBENCH, 32_000_000_000L),
            createTask("GPUInfer", user.getName(), WorkloadType.LLM_GPU, 16_000_000_000L)
        };

        for (Task task : tasks) {
            user.addTask(task);
            context.addTask(task);
        }

        System.out.println("  Setup:");
        System.out.println("    VMs: 2 (1 CPU-only, 1 Mixed)");
        System.out.println("    Tasks: 4 (3 CPU, 1 GPU)");

        // Step 1: Task Assignment
        System.out.println("\n  Step 1: Task Assignment");
        TaskAssignmentStep assignStep = new TaskAssignmentStep(new FirstAvailableTaskAssignmentStrategy());
        assignStep.execute(context);
        System.out.println("    Tasks assigned: " + assignStep.getTasksAssigned());
        System.out.println("    Tasks failed: " + assignStep.getTasksFailed());

        // Step 2: VM Execution
        System.out.println("\n  Step 2: VM Execution");
        VMExecutionStep vmExecStep = new VMExecutionStep();
        long execStart = System.currentTimeMillis();
        vmExecStep.execute(context);
        long execEnd = System.currentTimeMillis();

        System.out.println("    Simulation seconds: " + vmExecStep.getTotalSimulationSeconds());
        System.out.println("    Tasks completed: " + vmExecStep.getTasksCompleted());
        System.out.println("    Real execution time: " + (execEnd - execStart) + "ms");

        // Step 3: Task Execution Analysis
        System.out.println("\n  Step 3: Task Execution Analysis");
        TaskExecutionStep taskExecStep = new TaskExecutionStep();
        taskExecStep.execute(context);

        System.out.println("    Makespan: " + taskExecStep.getMakespan() + " seconds");
        System.out.println("    Avg turnaround time: " + String.format("%.2f", taskExecStep.getAverageTurnaroundTime()) + " seconds");
        System.out.println("    Throughput: " + String.format("%.6f", taskExecStep.getThroughput()) + " tasks/sec");
        System.out.println("    Users completed: " + taskExecStep.getUsersCompleted());

        // Print per-workload stats
        System.out.println("\n  Workload Statistics:");
        for (WorkloadType type : new WorkloadType[]{
            WorkloadType.SEVEN_ZIP, WorkloadType.DATABASE,
            WorkloadType.CINEBENCH, WorkloadType.LLM_GPU}) {

            TaskExecutionStep.WorkloadStatistics stats = taskExecStep.getWorkloadStatistics().get(type);
            if (stats != null && stats.totalTasks > 0) {
                System.out.printf("    %s: completed=%d, avgExecTime=%.2f sec%n",
                    type.name(), stats.completedTasks, stats.getAverageExecutionTime());
            }
        }

        boolean passed = assignStep.getTasksAssigned() == 4 &&
                        vmExecStep.getTasksCompleted() == 4 &&
                        taskExecStep.getCompletedTasks() == 4 &&
                        taskExecStep.getUsersCompleted() == 1;

        System.out.println("\n  Result: " + (passed ? "PASSED" : "FAILED"));
        System.out.println();
    }
}
