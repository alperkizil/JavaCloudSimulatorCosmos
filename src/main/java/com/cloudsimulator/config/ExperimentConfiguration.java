package com.cloudsimulator.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main configuration class for the simulation experiment.
 * Supports deep-copying for running multiple experiments with variations.
 */
public class ExperimentConfiguration implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    private long randomSeed;
    private List<DatacenterConfig> datacenterConfigs;
    private List<HostConfig> hostConfigs;
    private List<UserConfig> userConfigs;
    private List<VMConfig> vmConfigs;
    private List<TaskConfig> taskConfigs;

    // Constructor
    public ExperimentConfiguration() {
        this.randomSeed = System.currentTimeMillis(); // Default seed
        this.datacenterConfigs = new ArrayList<>();
        this.hostConfigs = new ArrayList<>();
        this.userConfigs = new ArrayList<>();
        this.vmConfigs = new ArrayList<>();
        this.taskConfigs = new ArrayList<>();
    }

    // Copy constructor for deep copy
    public ExperimentConfiguration(ExperimentConfiguration other) {
        this.randomSeed = other.randomSeed;

        // Deep copy all lists
        this.datacenterConfigs = other.datacenterConfigs.stream()
            .map(DatacenterConfig::clone)
            .collect(Collectors.toList());

        this.hostConfigs = other.hostConfigs.stream()
            .map(HostConfig::clone)
            .collect(Collectors.toList());

        this.userConfigs = other.userConfigs.stream()
            .map(UserConfig::clone)
            .collect(Collectors.toList());

        this.vmConfigs = other.vmConfigs.stream()
            .map(VMConfig::clone)
            .collect(Collectors.toList());

        this.taskConfigs = other.taskConfigs.stream()
            .map(TaskConfig::clone)
            .collect(Collectors.toList());
    }

    @Override
    public ExperimentConfiguration clone() {
        return new ExperimentConfiguration(this);
    }

    /**
     * Creates a deep copy with a new random seed.
     */
    public ExperimentConfiguration cloneWithSeed(long newSeed) {
        ExperimentConfiguration copy = this.clone();
        copy.setRandomSeed(newSeed);
        return copy;
    }

    // Getters and Setters

    public long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public List<DatacenterConfig> getDatacenterConfigs() {
        return datacenterConfigs;
    }

    public void setDatacenterConfigs(List<DatacenterConfig> datacenterConfigs) {
        this.datacenterConfigs = datacenterConfigs;
    }

    public void addDatacenterConfig(DatacenterConfig config) {
        this.datacenterConfigs.add(config);
    }

    public List<HostConfig> getHostConfigs() {
        return hostConfigs;
    }

    public void setHostConfigs(List<HostConfig> hostConfigs) {
        this.hostConfigs = hostConfigs;
    }

    public void addHostConfig(HostConfig config) {
        this.hostConfigs.add(config);
    }

    public List<UserConfig> getUserConfigs() {
        return userConfigs;
    }

    public void setUserConfigs(List<UserConfig> userConfigs) {
        this.userConfigs = userConfigs;
    }

    public void addUserConfig(UserConfig config) {
        this.userConfigs.add(config);
    }

    public List<VMConfig> getVmConfigs() {
        return vmConfigs;
    }

    public void setVmConfigs(List<VMConfig> vmConfigs) {
        this.vmConfigs = vmConfigs;
    }

    public void addVMConfig(VMConfig config) {
        this.vmConfigs.add(config);
    }

    public List<TaskConfig> getTaskConfigs() {
        return taskConfigs;
    }

    public void setTaskConfigs(List<TaskConfig> taskConfigs) {
        this.taskConfigs = taskConfigs;
    }

    public void addTaskConfig(TaskConfig config) {
        this.taskConfigs.add(config);
    }

    @Override
    public String toString() {
        return "ExperimentConfiguration{" +
                "randomSeed=" + randomSeed +
                ", datacenters=" + datacenterConfigs.size() +
                ", hosts=" + hostConfigs.size() +
                ", users=" + userConfigs.size() +
                ", vms=" + vmConfigs.size() +
                ", tasks=" + taskConfigs.size() +
                '}';
    }
}
