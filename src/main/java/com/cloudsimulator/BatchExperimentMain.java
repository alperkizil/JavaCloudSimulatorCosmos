package com.cloudsimulator;

import com.cloudsimulator.config.*;
import com.cloudsimulator.engine.SimulationEngine;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.steps.InitializationStep;
import com.cloudsimulator.steps.UserDatacenterMappingStep;
import com.cloudsimulator.steps.VMPlacementStep;
import com.cloudsimulator.steps.TaskAssignmentStep;
import com.cloudsimulator.steps.VMExecutionStep;
import com.cloudsimulator.PlacementStrategy.VMPlacement.BestFitVMPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea.MOEA_NSGA2TaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.MakespanObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.*;
import java.util.Queue;
import java.util.stream.Collectors;

/**
 * Main entry point for batch experiment file processing.
 * Provides a Swing-based folder selector for .cosc configuration files.
 */
public class BatchExperimentMain {

    // List of original configurations to be passed to experiment runs
    private List<ExperimentConfiguration> experimentConfigurations = new ArrayList<>();

    public static void main(String[] args) {
        // Set look and feel to system default
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default look and feel
        }

        // Run on EDT
        SwingUtilities.invokeLater(() -> {
            BatchExperimentMain app = new BatchExperimentMain();
            app.run();
        });
    }

    private void run() {
        // Get the project root directory
        String projectRoot = System.getProperty("user.dir");
        File defaultConfigFolder = new File(projectRoot, "configs");

        // Create file chooser
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Folder Containing Batch Experiment Files (.cosc)");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);

        // Set default directory to configs folder
        if (defaultConfigFolder.exists() && defaultConfigFolder.isDirectory()) {
            fileChooser.setCurrentDirectory(defaultConfigFolder);
        } else {
            fileChooser.setCurrentDirectory(new File(projectRoot));
        }

        // Show dialog
        int result = fileChooser.showOpenDialog(null);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fileChooser.getSelectedFile();
            processFolder(selectedFolder);
        } else {
            System.out.println("No folder selected. Exiting.");
            System.exit(0);
        }
    }

    private void processFolder(File folder) {
        System.out.println("========================================");
        System.out.println("BATCH EXPERIMENT FILE PROCESSOR");
        System.out.println("========================================");
        System.out.println("Selected folder: " + folder.getAbsolutePath());
        System.out.println();

        // Find all .cosc files
        File[] coscFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".cosc"));

        if (coscFiles == null || coscFiles.length == 0) {
            System.out.println("No .cosc files found in the selected folder.");
            return;
        }

        // Sort files by name
        Arrays.sort(coscFiles, Comparator.comparing(File::getName));

        System.out.println("Found " + coscFiles.length + " .cosc file(s)");
        System.out.println();

        // Parse all files
        FileConfigParser parser = new FileConfigParser();
        List<ExperimentConfiguration> configurations = new ArrayList<>();
        Map<String, Long> fileSeedMap = new LinkedHashMap<>();

        for (File file : coscFiles) {
            try {
                ExperimentConfiguration config = parser.parse(file.getAbsolutePath());
                configurations.add(config);
                fileSeedMap.put(file.getName(), config.getRandomSeed());
            } catch (ConfigParser.ConfigurationException e) {
                System.out.println("Error parsing file: " + file.getName() + " - " + e.getMessage());
            }
        }

        if (configurations.isEmpty()) {
            System.out.println("No valid configurations parsed.");
            return;
        }

        // Store configurations for later experiment runs
        this.experimentConfigurations = configurations;

        // Display seeds for each file
        printFileSeeds(fileSeedMap);

        // Display shared configuration (using first file as reference)
        ExperimentConfiguration referenceConfig = configurations.get(0);
        printSharedConfiguration(referenceConfig);

        // Run experiments for each configuration
        System.out.println("========================================");
        System.out.println("RUNNING EXPERIMENTS");
        System.out.println("========================================");

        for (int i = 0; i < experimentConfigurations.size(); i++) {
            ExperimentConfiguration config = experimentConfigurations.get(i);
            System.out.printf("%nExperiment %d/%d (Seed: %d)%n",
                    i + 1, experimentConfigurations.size(), config.getRandomSeed());
            System.out.println("----------------------------------------");
            singleRun(config);
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("ALL EXPERIMENTS COMPLETED");
        System.out.println("========================================");
    }

    /**
     * Runs a single simulation experiment with the given configuration.
     * Uses a deep copy of the configuration to ensure isolation between runs.
     *
     * @param config The experiment configuration for this run
     */
    private void singleRun(ExperimentConfiguration config) {
        // Create a deep copy of the configuration to ensure isolation
        ExperimentConfiguration configCopy = config.clone();

        // Create a new SimulationEngine for this run
        SimulationEngine engine = new SimulationEngine();

        // Configure engine with the experiment configuration (sets seed, initializes RandomGenerator)
        engine.configure(configCopy);

        // Step 1: Initialize all simulation entities from configuration
        engine.addStep(new InitializationStep(configCopy));

        // Step 2: Assign hosts to datacenters using placement strategy
        engine.addStep(new HostPlacementStep());

        // Step 3: Validate and finalize user-datacenter relationships
        engine.addStep(new UserDatacenterMappingStep());

        // Step 4: Assign VMs to hosts using Best Fit strategy
        engine.addStep(new VMPlacementStep(new BestFitVMPlacementStrategy()));

        // Step 5: Assign tasks to VMs using NSGA-II multi-objective optimization
        NSGA2Configuration nsga2Config = NSGA2Configuration.builder()
                .populationSize(100)
                .addObjective(new MakespanObjective())
                .addObjective(new EnergyObjective())
                .build();
        engine.addStep(new TaskAssignmentStep(new MOEA_NSGA2TaskSchedulingStrategy(nsga2Config)));

        // Run steps 1-5 (before execution)
        engine.run();

        // Print VM assignments after Step 4
        printVMAssignments(engine.getContext());

        // Print task assignments after Step 5 (before execution empties the queues)
        printTaskAssignments(engine.getContext());

        // Step 6: Execute VMs - runs the main simulation loop until all tasks complete
        engine.addStep(new VMExecutionStep());

        // Run step 6
        engine.run();
    }

    /**
     * Prints VM assignments for each host after VM placement.
     */
    private void printVMAssignments(SimulationContext context) {
        System.out.println();
        System.out.println("VM ASSIGNMENTS BY HOST");
        System.out.println("----------------------------------------");

        int totalVMsAssigned = 0;
        int activeHosts = 0;

        for (CloudDatacenter dc : context.getDatacenters()) {
            System.out.println("Datacenter: " + dc.getName());

            for (Host host : dc.getHosts()) {
                List<VM> assignedVMs = host.getAssignedVMs();
                if (!assignedVMs.isEmpty()) {
                    activeHosts++;
                    totalVMsAssigned += assignedVMs.size();

                    System.out.printf("  Host %d [%s, %d cores, %d GPUs, %s RAM]: %d VMs%n",
                            host.getId(),
                            host.getComputeType(),
                            host.getNumberOfCpuCores(),
                            host.getNumberOfGpus(),
                            formatNumber(host.getRamCapacityMB()) + "MB",
                            assignedVMs.size());

                    for (VM vm : assignedVMs) {
                        System.out.printf("    -> VM %d [%s, %d vCPUs, %d GPUs, Owner: %s]%n",
                                vm.getId(),
                                vm.getComputeType(),
                                vm.getRequestedVcpuCount(),
                                vm.getRequestedGpuCount(),
                                vm.getUserId());
                    }
                }
            }
        }

        System.out.println("----------------------------------------");
        System.out.printf("Total: %d VMs assigned to %d active hosts%n", totalVMsAssigned, activeHosts);
    }

    /**
     * Prints task assignments for each VM after task assignment.
     */
    private void printTaskAssignments(SimulationContext context) {
        System.out.println();
        System.out.println("TASK ASSIGNMENTS BY VM");
        System.out.println("----------------------------------------");

        int totalTasksAssigned = 0;
        int totalCpuTasks = 0;
        int totalGpuTasks = 0;

        for (VM vm : context.getVms()) {
            Queue<Task> assignedTasks = vm.getAssignedTasks();

            System.out.printf("VM %d [%s, Owner: %s]:%n", vm.getId(), vm.getComputeType(), vm.getUserId());

            if (assignedTasks.isEmpty()) {
                System.out.println("  Queue: EMPTY");
            } else {
                int cpuTasks = 0;
                int gpuTasks = 0;

                for (Task task : assignedTasks) {
                    if (isGpuWorkload(task.getWorkloadType())) {
                        gpuTasks++;
                    } else {
                        cpuTasks++;
                    }
                }

                totalTasksAssigned += assignedTasks.size();
                totalCpuTasks += cpuTasks;
                totalGpuTasks += gpuTasks;

                System.out.printf("  CPU Tasks: %d, GPU Tasks: %d, Total: %d%n",
                        cpuTasks, gpuTasks, assignedTasks.size());
            }
        }

        System.out.println("----------------------------------------");
        System.out.printf("Total: %d tasks assigned (CPU: %d, GPU: %d)%n",
                totalTasksAssigned, totalCpuTasks, totalGpuTasks);
    }

    /**
     * Determines if a workload type uses GPU.
     */
    private boolean isGpuWorkload(WorkloadType type) {
        return type == WorkloadType.FURMARK ||
               type == WorkloadType.IMAGE_GEN_GPU ||
               type == WorkloadType.LLM_GPU;
    }

    private void printFileSeeds(Map<String, Long> fileSeedMap) {
        System.out.println("========================================");
        System.out.println("FILE SEEDS");
        System.out.println("========================================");

        int index = 1;
        for (Map.Entry<String, Long> entry : fileSeedMap.entrySet()) {
            System.out.printf("%3d. %-50s Seed: %d%n", index++, entry.getKey(), entry.getValue());
        }
        System.out.println();
    }

    private void printSharedConfiguration(ExperimentConfiguration config) {
        System.out.println("========================================");
        System.out.println("SHARED CONFIGURATION PROPERTIES");
        System.out.println("(Same across all files in batch)");
        System.out.println("========================================");
        System.out.println();

        printDatacenters(config);
        printHosts(config);
        printUsers(config);
        printVMs(config);
        printTasks(config);
    }

    private void printDatacenters(ExperimentConfiguration config) {
        System.out.println("----------------------------------------");
        System.out.println("DATACENTERS (" + config.getDatacenterConfigs().size() + ")");
        System.out.println("----------------------------------------");
        System.out.printf("%-5s %-20s %-15s %-15s%n", "#", "Name", "Max Hosts", "Max Power (W)");
        System.out.println("-------------------------------------------------------------");

        int index = 1;
        for (DatacenterConfig dc : config.getDatacenterConfigs()) {
            System.out.printf("%-5d %-20s %-15d %-15.1f%n",
                    index++,
                    dc.getName(),
                    dc.getMaxHostCapacity(),
                    dc.getTotalMaxPowerDraw());
        }
        System.out.println();
    }

    private void printHosts(ExperimentConfiguration config) {
        System.out.println("----------------------------------------");
        System.out.println("HOSTS (" + config.getHostConfigs().size() + ")");
        System.out.println("----------------------------------------");

        // Group hosts by their configuration for better display
        Map<String, List<HostConfig>> groupedHosts = new LinkedHashMap<>();
        for (HostConfig host : config.getHostConfigs()) {
            String key = String.format("%d_%d_%s_%d_%s",
                    host.getInstructionsPerSecond(),
                    host.getNumberOfCpuCores(),
                    host.getComputeType(),
                    host.getNumberOfGpus(),
                    host.getPowerModelName());
            groupedHosts.computeIfAbsent(key, k -> new ArrayList<>()).add(host);
        }

        System.out.printf("%-8s %-15s %-8s %-15s %-6s %-12s %-15s%n",
                "Count", "IPS", "Cores", "Compute Type", "GPUs", "RAM (MB)", "Power Model");
        System.out.println("--------------------------------------------------------------------------------");

        for (Map.Entry<String, List<HostConfig>> entry : groupedHosts.entrySet()) {
            List<HostConfig> hosts = entry.getValue();
            HostConfig host = hosts.get(0);
            System.out.printf("%-8d %-15s %-8d %-15s %-6d %-12d %-15s%n",
                    hosts.size(),
                    formatNumber(host.getInstructionsPerSecond()),
                    host.getNumberOfCpuCores(),
                    host.getComputeType(),
                    host.getNumberOfGpus(),
                    host.getRamCapacityMB(),
                    host.getPowerModelName());
        }
        System.out.println();
    }

    private void printUsers(ExperimentConfiguration config) {
        System.out.println("----------------------------------------");
        System.out.println("USERS (" + config.getUserConfigs().size() + ")");
        System.out.println("----------------------------------------");
        System.out.printf("%-20s %-25s %-8s %-8s %-8s %-10s%n",
                "Name", "Datacenters", "GPU VMs", "CPU VMs", "Mixed", "Tasks");
        System.out.println("--------------------------------------------------------------------------------");

        for (UserConfig user : config.getUserConfigs()) {
            int totalTasks = user.getTaskCounts().values().stream()
                    .mapToInt(Integer::intValue).sum();
            String datacenters = String.join(", ", user.getSelectedDatacenterNames());
            if (datacenters.length() > 23) {
                datacenters = datacenters.substring(0, 20) + "...";
            }

            System.out.printf("%-20s %-25s %-8d %-8d %-8d %-10d%n",
                    user.getName(),
                    datacenters,
                    user.getNumberOfGpuVMs(),
                    user.getNumberOfCpuVMs(),
                    user.getNumberOfMixedVMs(),
                    totalTasks);
        }

        // Print detailed task breakdown per user
        System.out.println();
        System.out.println("Task breakdown by workload type:");
        for (UserConfig user : config.getUserConfigs()) {
            System.out.println("  " + user.getName() + ":");
            for (Map.Entry<WorkloadType, Integer> entry : user.getTaskCounts().entrySet()) {
                if (entry.getValue() > 0) {
                    System.out.printf("    %-20s: %d%n", entry.getKey(), entry.getValue());
                }
            }
        }
        System.out.println();
    }

    private void printVMs(ExperimentConfiguration config) {
        System.out.println("----------------------------------------");
        System.out.println("VMs (" + config.getVmConfigs().size() + ")");
        System.out.println("----------------------------------------");

        // Group VMs by compute type
        Map<ComputeType, List<VMConfig>> vmsByType = config.getVmConfigs().stream()
                .collect(Collectors.groupingBy(VMConfig::getComputeType));

        System.out.printf("%-15s %-20s %-8s %-6s %-10s %-12s%n",
                "Compute Type", "Owner", "vCPUs", "GPUs", "RAM (MB)", "IPS/vCPU");
        System.out.println("--------------------------------------------------------------------------------");

        for (ComputeType type : ComputeType.values()) {
            List<VMConfig> vms = vmsByType.get(type);
            if (vms == null || vms.isEmpty()) continue;

            // Group by configuration within each type
            Map<String, List<VMConfig>> grouped = new LinkedHashMap<>();
            for (VMConfig vm : vms) {
                String key = String.format("%s_%d_%d_%d_%d",
                        vm.getUserName(),
                        vm.getRequestedVcpuCount(),
                        vm.getRequestedGpuCount(),
                        vm.getRequestedRamMB(),
                        vm.getRequestedIpsPerVcpu());
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(vm);
            }

            for (Map.Entry<String, List<VMConfig>> entry : grouped.entrySet()) {
                List<VMConfig> vmGroup = entry.getValue();
                VMConfig vm = vmGroup.get(0);
                System.out.printf("%-15s %-20s %-8d %-6d %-10d %-12s (x%d)%n",
                        type,
                        vm.getUserName(),
                        vm.getRequestedVcpuCount(),
                        vm.getRequestedGpuCount(),
                        vm.getRequestedRamMB(),
                        formatNumber(vm.getRequestedIpsPerVcpu()),
                        vmGroup.size());
            }
        }

        // Summary by compute type
        System.out.println();
        System.out.println("Summary by compute type:");
        for (ComputeType type : ComputeType.values()) {
            List<VMConfig> vms = vmsByType.get(type);
            if (vms != null && !vms.isEmpty()) {
                System.out.printf("  %-15s: %d VMs%n", type, vms.size());
            }
        }
        System.out.println();
    }

    private void printTasks(ExperimentConfiguration config) {
        System.out.println("----------------------------------------");
        System.out.println("TASKS (" + config.getTaskConfigs().size() + ")");
        System.out.println("----------------------------------------");

        // Group tasks by workload type
        Map<WorkloadType, List<TaskConfig>> tasksByType = config.getTaskConfigs().stream()
                .collect(Collectors.groupingBy(TaskConfig::getWorkloadType));

        System.out.println("Summary by workload type:");
        for (WorkloadType type : WorkloadType.values()) {
            List<TaskConfig> tasks = tasksByType.get(type);
            if (tasks != null && !tasks.isEmpty()) {
                long minInstructions = tasks.stream()
                        .mapToLong(TaskConfig::getInstructionLength).min().orElse(0);
                long maxInstructions = tasks.stream()
                        .mapToLong(TaskConfig::getInstructionLength).max().orElse(0);
                double avgInstructions = tasks.stream()
                        .mapToLong(TaskConfig::getInstructionLength).average().orElse(0);

                System.out.printf("  %-20s: %4d tasks, Instructions: min=%s, max=%s, avg=%s%n",
                        type,
                        tasks.size(),
                        formatNumber(minInstructions),
                        formatNumber(maxInstructions),
                        formatNumber((long) avgInstructions));
            }
        }

        // Group by user
        System.out.println();
        System.out.println("Tasks by user:");
        Map<String, List<TaskConfig>> tasksByUser = config.getTaskConfigs().stream()
                .collect(Collectors.groupingBy(TaskConfig::getUserName));

        for (Map.Entry<String, List<TaskConfig>> entry : tasksByUser.entrySet()) {
            System.out.printf("  %-20s: %d tasks%n", entry.getKey(), entry.getValue().size());
        }
        System.out.println();
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000_000_000L) {
            return String.format("%.1fT", number / 1_000_000_000_000.0);
        } else if (number >= 1_000_000_000L) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000L) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000L) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }
}
