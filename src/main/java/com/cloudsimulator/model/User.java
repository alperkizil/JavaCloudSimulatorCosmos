package com.cloudsimulator.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a User in the cloud simulation system.
 * Users create tasks, own VMs, and select datacenters for their workloads.
 */
public class User {
    private static final AtomicLong idGenerator = new AtomicLong(0);

    // Identity
    private final long id;
    private String name;

    // Datacenter preferences
    private List<Long> userSelectedDatacenters;  // List of datacenter IDs the user opts to use

    // Resource ownership
    private List<VM> virtualMachines;            // VMs belonging to this user
    private List<Task> tasks;                    // Tasks belonging to this user

    // Completed resources
    private List<Task> tasksFinishedExecuting;   // Tasks that have completed execution
    private List<VM> vmsFinishedExecuting;       // VMs that have finished all their tasks

    // Timing
    private Long startTimestamp;                 // When user was introduced to the system
    private Long finishTimestamp;                // When all tasks within VMs are executed

    /**
     * Constructor with name.
     */
    public User(String name) {
        this.id = idGenerator.incrementAndGet();
        this.name = name;

        // Initialize collections
        this.userSelectedDatacenters = new ArrayList<>();
        this.virtualMachines = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.tasksFinishedExecuting = new ArrayList<>();
        this.vmsFinishedExecuting = new ArrayList<>();

        // Initialize timestamps as null (not yet set)
        this.startTimestamp = null;
        this.finishTimestamp = null;
    }

    /**
     * Default constructor with generic name.
     */
    public User() {
        this("User-" + (idGenerator.get() + 1));
    }

    /**
     * Adds a datacenter to the user's selected datacenters list.
     */
    public void addSelectedDatacenter(Long datacenterId) {
        if (!this.userSelectedDatacenters.contains(datacenterId)) {
            this.userSelectedDatacenters.add(datacenterId);
        }
    }

    /**
     * Removes a datacenter from the user's selected datacenters list.
     */
    public void removeSelectedDatacenter(Long datacenterId) {
        this.userSelectedDatacenters.remove(datacenterId);
    }

    /**
     * Adds a VM to the user's virtual machines list.
     */
    public void addVirtualMachine(VM vm) {
        if (!this.virtualMachines.contains(vm)) {
            this.virtualMachines.add(vm);
        }
    }

    /**
     * Removes a VM from the user's virtual machines list.
     */
    public void removeVirtualMachine(VM vm) {
        this.virtualMachines.remove(vm);
    }

    /**
     * Adds a task to the user's tasks list.
     */
    public void addTask(Task task) {
        if (!this.tasks.contains(task)) {
            this.tasks.add(task);
        }
    }

    /**
     * Removes a task from the user's tasks list.
     */
    public void removeTask(Task task) {
        this.tasks.remove(task);
    }

    /**
     * Marks a task as finished and moves it to the finished list.
     */
    public void finishTask(Task task) {
        if (this.tasks.contains(task)) {
            this.tasks.remove(task);
            if (!this.tasksFinishedExecuting.contains(task)) {
                this.tasksFinishedExecuting.add(task);
            }
        }
    }

    /**
     * Marks a VM as finished and moves it to the finished list.
     */
    public void finishVM(VM vm) {
        if (this.virtualMachines.contains(vm)) {
            this.virtualMachines.remove(vm);
            if (!this.vmsFinishedExecuting.contains(vm)) {
                this.vmsFinishedExecuting.add(vm);
            }
        }
    }

    /**
     * Starts the user's session by setting the start timestamp.
     */
    public void startSession(long timestamp) {
        this.startTimestamp = timestamp;
    }

    /**
     * Finishes the user's session by setting the finish timestamp.
     * Should be called when all tasks are completed.
     */
    public void finishSession(long timestamp) {
        this.finishTimestamp = timestamp;
    }

    /**
     * Checks if all tasks have been completed.
     */
    public boolean allTasksCompleted() {
        return tasks.isEmpty() &&
               virtualMachines.stream().allMatch(vm -> vm.getAssignedTasks().isEmpty());
    }

    /**
     * Gets the total execution time (finish - start).
     */
    public Long getTotalExecutionTime() {
        if (startTimestamp != null && finishTimestamp != null) {
            return finishTimestamp - startTimestamp;
        }
        return null;
    }

    /**
     * Gets the total number of tasks (active + finished).
     */
    public int getTotalTaskCount() {
        return tasks.size() + tasksFinishedExecuting.size();
    }

    /**
     * Gets the total number of VMs (active + finished).
     */
    public int getTotalVMCount() {
        return virtualMachines.size() + vmsFinishedExecuting.size();
    }

    /**
     * Checks if a datacenter is in the user's selected list.
     */
    public boolean hasSelectedDatacenter(Long datacenterId) {
        return userSelectedDatacenters.contains(datacenterId);
    }

    /**
     * Gets the number of active tasks.
     */
    public int getActiveTaskCount() {
        return tasks.size();
    }

    /**
     * Gets the number of completed tasks.
     */
    public int getCompletedTaskCount() {
        return tasksFinishedExecuting.size();
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Long> getUserSelectedDatacenters() {
        return userSelectedDatacenters;
    }

    public void setUserSelectedDatacenters(List<Long> userSelectedDatacenters) {
        this.userSelectedDatacenters = userSelectedDatacenters;
    }

    public List<VM> getVirtualMachines() {
        return virtualMachines;
    }

    public void setVirtualMachines(List<VM> virtualMachines) {
        this.virtualMachines = virtualMachines;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public List<Task> getTasksFinishedExecuting() {
        return tasksFinishedExecuting;
    }

    public void setTasksFinishedExecuting(List<Task> tasksFinishedExecuting) {
        this.tasksFinishedExecuting = tasksFinishedExecuting;
    }

    public List<VM> getVmsFinishedExecuting() {
        return vmsFinishedExecuting;
    }

    public void setVmsFinishedExecuting(List<VM> vmsFinishedExecuting) {
        this.vmsFinishedExecuting = vmsFinishedExecuting;
    }

    public Long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public Long getFinishTimestamp() {
        return finishTimestamp;
    }

    public void setFinishTimestamp(Long finishTimestamp) {
        this.finishTimestamp = finishTimestamp;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", selectedDatacenters=" + userSelectedDatacenters.size() +
                ", virtualMachines=" + virtualMachines.size() +
                ", activeTasks=" + tasks.size() +
                ", finishedTasks=" + tasksFinishedExecuting.size() +
                ", finishedVMs=" + vmsFinishedExecuting.size() +
                ", startTimestamp=" + startTimestamp +
                ", finishTimestamp=" + finishTimestamp +
                '}';
    }
}
