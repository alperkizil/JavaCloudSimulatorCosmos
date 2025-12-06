package com.cloudsimulator.gui;

import com.cloudsimulator.config.DatacenterConfig;
import com.cloudsimulator.config.HostConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Main template for experiment configuration.
 * Contains all configuration elements that remain constant across seed variations.
 */
public class ExperimentTemplate {
    private List<DatacenterConfig> datacenterConfigs;
    private List<HostConfig> hostConfigs;
    private List<UserTemplate> userTemplates;

    public ExperimentTemplate() {
        this.datacenterConfigs = new ArrayList<>();
        this.hostConfigs = new ArrayList<>();
        this.userTemplates = new ArrayList<>();
    }

    public List<DatacenterConfig> getDatacenterConfigs() {
        return datacenterConfigs;
    }

    public void setDatacenterConfigs(List<DatacenterConfig> datacenterConfigs) {
        this.datacenterConfigs = datacenterConfigs;
    }

    public void addDatacenterConfig(DatacenterConfig config) {
        datacenterConfigs.add(config);
    }

    public void removeDatacenterConfig(DatacenterConfig config) {
        datacenterConfigs.remove(config);
    }

    public List<HostConfig> getHostConfigs() {
        return hostConfigs;
    }

    public void setHostConfigs(List<HostConfig> hostConfigs) {
        this.hostConfigs = hostConfigs;
    }

    public void addHostConfig(HostConfig config) {
        hostConfigs.add(config);
    }

    public void removeHostConfig(HostConfig config) {
        hostConfigs.remove(config);
    }

    public List<UserTemplate> getUserTemplates() {
        return userTemplates;
    }

    public void setUserTemplates(List<UserTemplate> userTemplates) {
        this.userTemplates = userTemplates;
    }

    public void addUserTemplate(UserTemplate user) {
        userTemplates.add(user);
    }

    public void removeUserTemplate(UserTemplate user) {
        userTemplates.remove(user);
    }

    public List<String> getDatacenterNames() {
        List<String> names = new ArrayList<>();
        for (DatacenterConfig dc : datacenterConfigs) {
            names.add(dc.getName());
        }
        return names;
    }

    public int getTotalVMCount() {
        return userTemplates.stream().mapToInt(UserTemplate::getTotalVMCount).sum();
    }

    public int getTotalTaskCount() {
        return userTemplates.stream().mapToInt(UserTemplate::getTotalTaskCount).sum();
    }

    @Override
    public String toString() {
        return String.format("ExperimentTemplate{DCs=%d, Hosts=%d, Users=%d, VMs=%d, Tasks=%d}",
            datacenterConfigs.size(), hostConfigs.size(), userTemplates.size(),
            getTotalVMCount(), getTotalTaskCount());
    }
}
