package com.cloudsimulator.config;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for .cosc (Cosmos Config) files.
 * Supports the following sections: SEED, DATACENTERS, HOSTS, USERS, VMS, TASKS
 */
public class FileConfigParser implements ConfigParser {

    @Override
    public ExperimentConfiguration parse(String configFilePath) throws ConfigurationException {
        ExperimentConfiguration config = new ExperimentConfiguration();

        try (BufferedReader reader = new BufferedReader(new FileReader(configFilePath))) {
            String line;
            String currentSection = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Check for section headers
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1);
                    continue;
                }

                // Parse based on current section
                if (currentSection != null) {
                    parseSection(currentSection, line, config, reader);
                }
            }

        } catch (IOException e) {
            throw new ConfigurationException("Failed to read config file: " + configFilePath, e);
        }

        return config;
    }

    private void parseSection(String section, String line, ExperimentConfiguration config,
                              BufferedReader reader) throws IOException {
        switch (section) {
            case "SEED":
                parseSeed(line, config);
                break;
            case "DATACENTERS":
                parseDatacenters(line, config, reader);
                break;
            case "HOSTS":
                parseHosts(line, config, reader);
                break;
            case "USERS":
                parseUsers(line, config, reader);
                break;
            case "VMS":
                parseVMs(line, config, reader);
                break;
            case "TASKS":
                parseTasks(line, config, reader);
                break;
            default:
                // Unknown section, skip
                break;
        }
    }

    private void parseSeed(String line, ExperimentConfiguration config) {
        long seed = Long.parseLong(line.trim());
        config.setRandomSeed(seed);
    }

    private void parseDatacenters(String line, ExperimentConfiguration config, BufferedReader reader)
            throws IOException {
        int count = Integer.parseInt(line.trim());

        for (int i = 0; i < count; i++) {
            String dcLine = reader.readLine();
            if (dcLine == null) {
                throw new ConfigurationException("Unexpected end of file while parsing datacenters. Expected " + count + " entries, found " + i);
            }
            dcLine = dcLine.trim();
            String[] parts = dcLine.split(",");

            if (parts.length != 3) {
                throw new ConfigurationException("Invalid datacenter config: " + dcLine);
            }

            String name = parts[0].trim();
            int maxHostCapacity = Integer.parseInt(parts[1].trim());
            double totalMaxPowerDraw = Double.parseDouble(parts[2].trim());

            DatacenterConfig dcConfig = new DatacenterConfig(name, maxHostCapacity, totalMaxPowerDraw);
            config.addDatacenterConfig(dcConfig);
        }
    }

    private void parseHosts(String line, ExperimentConfiguration config, BufferedReader reader)
            throws IOException {
        int count = Integer.parseInt(line.trim());

        for (int i = 0; i < count; i++) {
            String hostLine = reader.readLine();
            if (hostLine == null) {
                throw new ConfigurationException("Unexpected end of file while parsing hosts. Expected " + count + " entries, found " + i);
            }
            hostLine = hostLine.trim();
            String[] parts = hostLine.split(",");

            if (parts.length != 8) {
                throw new ConfigurationException("Invalid host config: " + hostLine);
            }

            long ips = Long.parseLong(parts[0].trim());
            int cpuCores = Integer.parseInt(parts[1].trim());
            ComputeType computeType = ComputeType.valueOf(parts[2].trim());
            int gpus = Integer.parseInt(parts[3].trim());
            long ram = Long.parseLong(parts[4].trim());
            long network = Long.parseLong(parts[5].trim());
            long storage = Long.parseLong(parts[6].trim());
            String powerModel = parts[7].trim();

            HostConfig hostConfig = new HostConfig(ips, cpuCores, computeType, gpus,
                                                   ram, network, storage, powerModel);
            config.addHostConfig(hostConfig);
        }
    }

    private void parseUsers(String line, ExperimentConfiguration config, BufferedReader reader)
            throws IOException {
        int count = Integer.parseInt(line.trim());

        for (int i = 0; i < count; i++) {
            String userLine = reader.readLine();
            if (userLine == null) {
                throw new ConfigurationException("Unexpected end of file while parsing users. Expected " + count + " entries, found " + i);
            }
            userLine = userLine.trim();
            String[] parts = userLine.split(",");

            if (parts.length < 14) {
                throw new ConfigurationException("Invalid user config: " + userLine);
            }

            String name = parts[0].trim();
            List<String> datacenters = Arrays.asList(parts[1].trim().split("\\|"));
            int gpuVMs = Integer.parseInt(parts[2].trim());
            int cpuVMs = Integer.parseInt(parts[3].trim());
            int mixedVMs = Integer.parseInt(parts[4].trim());

            // Parse task counts: SEVEN_ZIP, DATABASE, FURMARK, IMAGE_GEN_CPU, IMAGE_GEN_GPU,
            // LLM_CPU, LLM_GPU, CINEBENCH, PRIME95SmallFFT, VERACRYPT
            Map<WorkloadType, Integer> taskCounts = new HashMap<>();
            taskCounts.put(WorkloadType.SEVEN_ZIP, Integer.parseInt(parts[5].trim()));
            taskCounts.put(WorkloadType.DATABASE, Integer.parseInt(parts[6].trim()));
            taskCounts.put(WorkloadType.FURMARK, Integer.parseInt(parts[7].trim()));
            taskCounts.put(WorkloadType.IMAGE_GEN_CPU, Integer.parseInt(parts[8].trim()));
            taskCounts.put(WorkloadType.IMAGE_GEN_GPU, Integer.parseInt(parts[9].trim()));
            taskCounts.put(WorkloadType.LLM_CPU, Integer.parseInt(parts[10].trim()));
            taskCounts.put(WorkloadType.LLM_GPU, Integer.parseInt(parts[11].trim()));
            taskCounts.put(WorkloadType.CINEBENCH, Integer.parseInt(parts[12].trim()));
            taskCounts.put(WorkloadType.PRIME95SmallFFT, Integer.parseInt(parts[13].trim()));
            if (parts.length > 14) {
                taskCounts.put(WorkloadType.VERACRYPT, Integer.parseInt(parts[14].trim()));
            }

            UserConfig userConfig = new UserConfig(name, datacenters, gpuVMs, cpuVMs, mixedVMs, taskCounts);
            config.addUserConfig(userConfig);
        }
    }

    private void parseVMs(String line, ExperimentConfiguration config, BufferedReader reader)
            throws IOException {
        // Format: GPU:2 or CPU:3 or MIXED:1
        String[] parts = line.split(":");
        if (parts.length != 2) {
            System.err.println("Warning: Invalid VM section format, skipping: " + line);
            return; // Skip invalid format
        }

        String typeStr = parts[0].trim();
        ComputeType type;
        if (typeStr.equals("MIXED")) {
            type = ComputeType.CPU_GPU_MIXED;
        } else {
            type = ComputeType.valueOf(typeStr + "_ONLY");
        }

        int count = Integer.parseInt(parts[1].trim());

        for (int i = 0; i < count; i++) {
            String vmLine = reader.readLine();
            if (vmLine == null) {
                throw new ConfigurationException("Unexpected end of file while parsing VMs. Expected " + count + " entries, found " + i);
            }
            vmLine = vmLine.trim();
            String[] vmParts = vmLine.split(",");

            if (vmParts.length != 7) {
                throw new ConfigurationException("Invalid VM config: " + vmLine);
            }

            String userName = vmParts[0].trim();
            long ipsPerVcpu = Long.parseLong(vmParts[1].trim());
            int vcpus = Integer.parseInt(vmParts[2].trim());
            int gpus = Integer.parseInt(vmParts[3].trim());
            long ram = Long.parseLong(vmParts[4].trim());
            long storage = Long.parseLong(vmParts[5].trim());
            long bandwidth = Long.parseLong(vmParts[6].trim());

            VMConfig vmConfig = new VMConfig(userName, ipsPerVcpu, vcpus, gpus,
                                            ram, storage, bandwidth, type);
            config.addVMConfig(vmConfig);
        }
    }

    private void parseTasks(String line, ExperimentConfiguration config, BufferedReader reader)
            throws IOException {
        // Format: SEVEN_ZIP:5 or DATABASE:3
        String[] parts = line.split(":");
        if (parts.length != 2) {
            System.err.println("Warning: Invalid TASKS section format, skipping: " + line);
            return; // Skip invalid format
        }

        WorkloadType workloadType = WorkloadType.valueOf(parts[0].trim());
        int count = Integer.parseInt(parts[1].trim());

        for (int i = 0; i < count; i++) {
            String taskLine = reader.readLine();
            if (taskLine == null) {
                throw new ConfigurationException("Unexpected end of file while parsing tasks. Expected " + count + " entries, found " + i);
            }
            taskLine = taskLine.trim();
            String[] taskParts = taskLine.split(",");

            if (taskParts.length != 3) {
                throw new ConfigurationException("Invalid task config: " + taskLine);
            }

            String name = taskParts[0].trim();
            String userName = taskParts[1].trim();
            long instructionLength = Long.parseLong(taskParts[2].trim());

            TaskConfig taskConfig = new TaskConfig(name, userName, instructionLength, workloadType);
            config.addTaskConfig(taskConfig);
        }
    }
}
