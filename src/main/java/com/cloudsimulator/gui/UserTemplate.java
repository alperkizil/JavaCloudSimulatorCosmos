package com.cloudsimulator.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Template for user configuration including VMs and tasks.
 */
public class UserTemplate {
    private String name;
    private List<String> selectedDatacenterNames;
    private List<VMTemplate> vmTemplates;
    private List<TaskTemplate> taskTemplates;

    public UserTemplate() {
        this.name = "User";
        this.selectedDatacenterNames = new ArrayList<>();
        this.vmTemplates = new ArrayList<>();
        this.taskTemplates = new ArrayList<>();
    }

    public UserTemplate(String name) {
        this.name = name;
        this.selectedDatacenterNames = new ArrayList<>();
        this.vmTemplates = new ArrayList<>();
        this.taskTemplates = new ArrayList<>();
    }

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

    public void addDatacenter(String datacenterName) {
        if (!selectedDatacenterNames.contains(datacenterName)) {
            selectedDatacenterNames.add(datacenterName);
        }
    }

    public void removeDatacenter(String datacenterName) {
        selectedDatacenterNames.remove(datacenterName);
    }

    public List<VMTemplate> getVmTemplates() {
        return vmTemplates;
    }

    public void setVmTemplates(List<VMTemplate> vmTemplates) {
        this.vmTemplates = vmTemplates;
    }

    public void addVmTemplate(VMTemplate vm) {
        vmTemplates.add(vm);
    }

    public void removeVmTemplate(VMTemplate vm) {
        vmTemplates.remove(vm);
    }

    public List<TaskTemplate> getTaskTemplates() {
        return taskTemplates;
    }

    public void setTaskTemplates(List<TaskTemplate> taskTemplates) {
        this.taskTemplates = taskTemplates;
    }

    public void addTaskTemplate(TaskTemplate task) {
        taskTemplates.add(task);
    }

    public void removeTaskTemplate(TaskTemplate task) {
        taskTemplates.remove(task);
    }

    public int getTotalVMCount() {
        return vmTemplates.size();
    }

    public int getTotalTaskCount() {
        return taskTemplates.stream().mapToInt(TaskTemplate::getCount).sum();
    }

    @Override
    public String toString() {
        return String.format("%s (DCs: %d, VMs: %d, Tasks: %d)",
            name, selectedDatacenterNames.size(), getTotalVMCount(), getTotalTaskCount());
    }
}
