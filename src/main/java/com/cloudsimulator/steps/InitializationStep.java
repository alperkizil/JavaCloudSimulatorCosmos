package com.cloudsimulator.steps;

import com.cloudsimulator.config.*;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.factory.PowerModelFactory;
import com.cloudsimulator.model.*;

/**
 * InitializationStep creates all simulation entities from the ExperimentConfiguration.
 * This is the first step in the simulation pipeline.
 *
 * It creates:
 * - CloudDatacenters from DatacenterConfig
 * - Hosts from HostConfig
 * - Users from UserConfig (with datacenter preferences)
 * - VMs from VMConfig (linked to users)
 * - Tasks from TaskConfig (linked to users)
 */
public class InitializationStep implements SimulationStep {

    private final ExperimentConfiguration configuration;

    /**
     * Constructor with ExperimentConfiguration.
     *
     * @param configuration The experiment configuration containing entity definitions
     */
    public InitializationStep(ExperimentConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("ExperimentConfiguration cannot be null");
        }
        this.configuration = configuration;
    }

    @Override
    public void execute(SimulationContext context) {
        // Create entities in order of dependency
        createDatacenters(context);
        createHosts(context);
        createUsers(context);
        createVMs(context);
        createTasks(context);

        // Record initialization metrics
        context.recordMetric("initialization.datacenters", context.getTotalDatacenterCount());
        context.recordMetric("initialization.hosts", context.getTotalHostCount());
        context.recordMetric("initialization.users", context.getUsers().size());
        context.recordMetric("initialization.vms", context.getTotalVMCount());
        context.recordMetric("initialization.tasks", context.getTotalTaskCount());
    }

    @Override
    public String getStepName() {
        return "Initialization";
    }

    /**
     * Creates CloudDatacenter objects from DatacenterConfig and adds them to context.
     */
    private void createDatacenters(SimulationContext context) {
        for (DatacenterConfig config : configuration.getDatacenterConfigs()) {
            CloudDatacenter datacenter = new CloudDatacenter(
                config.getName(),
                config.getMaxHostCapacity(),
                config.getTotalMaxPowerDraw()
            );
            context.addDatacenter(datacenter);
        }
    }

    /**
     * Creates Host objects from HostConfig and adds them to context.
     * Sets the power model using PowerModelFactory.
     */
    private void createHosts(SimulationContext context) {
        for (HostConfig config : configuration.getHostConfigs()) {
            Host host = new Host(
                config.getInstructionsPerSecond(),
                config.getNumberOfCpuCores(),
                config.getComputeType(),
                config.getNumberOfGpus()
            );

            // Set additional resource capacities
            host.setRamCapacityMB(config.getRamCapacityMB());
            host.setNetworkCapacityMbps(config.getNetworkCapacityMbps());
            host.setHardDriveCapacityMB(config.getHardDriveCapacityMB());

            // Set power model from factory
            PowerModel powerModel = PowerModelFactory.createPowerModel(config.getPowerModelName());
            host.setPowerModel(powerModel);

            context.addHost(host);
        }
    }

    /**
     * Creates User objects from UserConfig and adds them to context.
     * Maps datacenter names to datacenter IDs for user preferences.
     */
    private void createUsers(SimulationContext context) {
        for (UserConfig config : configuration.getUserConfigs()) {
            User user = new User(config.getName());

            // Map datacenter names to datacenter IDs
            for (String datacenterName : config.getSelectedDatacenterNames()) {
                CloudDatacenter datacenter = context.getDatacenterByName(datacenterName);
                if (datacenter != null) {
                    user.addSelectedDatacenter(datacenter.getId());
                    // Also register the user as a customer of the datacenter
                    datacenter.addCustomer(user.getName());
                }
            }

            context.addUser(user);
        }
    }

    /**
     * Creates VM objects from VMConfig and adds them to context.
     * Links each VM to its owner user.
     */
    private void createVMs(SimulationContext context) {
        for (VMConfig config : configuration.getVmConfigs()) {
            VM vm = new VM(
                config.getUserName(),
                config.getRequestedIpsPerVcpu(),
                config.getRequestedVcpuCount(),
                config.getRequestedGpuCount(),
                config.getRequestedRamMB(),
                config.getRequestedStorageMB(),
                config.getRequestedBandwidthMbps(),
                config.getComputeType()
            );

            // Link VM to its owner user
            User owner = context.getUserByName(config.getUserName());
            if (owner != null) {
                owner.addVirtualMachine(vm);
            }

            context.addVM(vm);
        }
    }

    /**
     * Creates Task objects from TaskConfig and adds them to context.
     * Links each task to its owner user.
     * Tasks are created with creation time 0 (start of simulation).
     */
    private void createTasks(SimulationContext context) {
        long creationTime = context.getCurrentTime();

        for (TaskConfig config : configuration.getTaskConfigs()) {
            Task task = new Task(
                config.getName(),
                config.getUserName(),
                config.getInstructionLength(),
                config.getWorkloadType(),
                creationTime
            );

            // Link task to its owner user
            User owner = context.getUserByName(config.getUserName());
            if (owner != null) {
                owner.addTask(task);
            }

            context.addTask(task);
        }
    }
}
