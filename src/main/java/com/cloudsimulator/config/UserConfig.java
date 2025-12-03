package com.cloudsimulator.config;

import com.cloudsimulator.enums.WorkloadType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a single User.
 * Supports deep-copy for experiment variations.
 */
public class UserConfig implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private List<String> selectedDatacenterNames;
    private int numberOfGpuVMs;
    private int numberOfCpuVMs;
    private int numberOfMixedVMs;
    private Map<WorkloadType, Integer> taskCounts; // Count per workload type

    public UserConfig() {
        selectedDatacenterNames = new ArrayList<>();
        taskCounts = new HashMap<>();
    }

    public UserConfig(String name, List<String> selectedDatacenterNames,
                     int numberOfGpuVMs, int numberOfCpuVMs, int numberOfMixedVMs,
                     Map<WorkloadType, Integer> taskCounts) {
        this.name = name;
        this.selectedDatacenterNames = new ArrayList<>(selectedDatacenterNames);
        this.numberOfGpuVMs = numberOfGpuVMs;
        this.numberOfCpuVMs = numberOfCpuVMs;
        this.numberOfMixedVMs = numberOfMixedVMs;
        this.taskCounts = new HashMap<>(taskCounts);
    }

    // Copy constructor
    public UserConfig(UserConfig other) {
        this.name = other.name;
        this.selectedDatacenterNames = new ArrayList<>(other.selectedDatacenterNames);
        this.numberOfGpuVMs = other.numberOfGpuVMs;
        this.numberOfCpuVMs = other.numberOfCpuVMs;
        this.numberOfMixedVMs = other.numberOfMixedVMs;
        this.taskCounts = new HashMap<>(other.taskCounts);
    }

    @Override
    public UserConfig clone() {
        return new UserConfig(this);
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getSelectedDatacenterNames() {
        return selectedDatacenterNames;
    }

    public void setSelectedDatacenterNames(List<String> selectedDatacenterNames) {
        this.selectedDatacenterNames = selectedDatacenterNames;
    }

    public int getNumberOfGpuVMs() {
        return numberOfGpuVMs;
    }

    public void setNumberOfGpuVMs(int numberOfGpuVMs) {
        this.numberOfGpuVMs = numberOfGpuVMs;
    }

    public int getNumberOfCpuVMs() {
        return numberOfCpuVMs;
    }

    public void setNumberOfCpuVMs(int numberOfCpuVMs) {
        this.numberOfCpuVMs = numberOfCpuVMs;
    }

    public int getNumberOfMixedVMs() {
        return numberOfMixedVMs;
    }

    public void setNumberOfMixedVMs(int numberOfMixedVMs) {
        this.numberOfMixedVMs = numberOfMixedVMs;
    }

    public Map<WorkloadType, Integer> getTaskCounts() {
        return taskCounts;
    }

    public void setTaskCounts(Map<WorkloadType, Integer> taskCounts) {
        this.taskCounts = taskCounts;
    }

    @Override
    public String toString() {
        return "UserConfig{" +
                "name='" + name + '\'' +
                ", datacenters=" + selectedDatacenterNames.size() +
                ", gpuVMs=" + numberOfGpuVMs +
                ", cpuVMs=" + numberOfCpuVMs +
                ", mixedVMs=" + numberOfMixedVMs +
                ", tasks=" + taskCounts.values().stream().mapToInt(Integer::intValue).sum() +
                '}';
    }
}
