package com.cloudsimulator.engine;

import com.cloudsimulator.model.*;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.utils.SimulationClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context holding the complete state of the simulation.
 * This includes all entities (datacenters, hosts, users, VMs, tasks)
 * and simulation metrics.
 */
public class SimulationContext {
    // Simulation time
    private SimulationClock clock;

    // Entities
    private List<CloudDatacenter> datacenters;
    private List<Host> hosts;
    private List<User> users;
    private List<VM> vms;
    private List<Task> tasks;

    // Metrics and statistics
    private Map<String, Object> metrics;

    // Simulation summary (populated by MetricsCollectionStep)
    private SimulationSummary simulationSummary;

    // Name lookups for convenience
    private Map<String, CloudDatacenter> datacentersByName;
    private Map<String, User> usersByName;

    public SimulationContext() {
        this.clock = new SimulationClock();
        this.datacenters = new ArrayList<>();
        this.hosts = new ArrayList<>();
        this.users = new ArrayList<>();
        this.vms = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.metrics = new HashMap<>();
        this.datacentersByName = new HashMap<>();
        this.usersByName = new HashMap<>();
    }

    // Clock methods
    public SimulationClock getClock() {
        return clock;
    }

    public long getCurrentTime() {
        return clock.getCurrentTime();
    }

    public void advanceTime() {
        clock.tick();
    }

    // Entity management
    public void addDatacenter(CloudDatacenter datacenter) {
        datacenters.add(datacenter);
        datacentersByName.put(datacenter.getName(), datacenter);
    }

    public void addHost(Host host) {
        hosts.add(host);
    }

    public void addUser(User user) {
        users.add(user);
        usersByName.put(user.getName(), user);
    }

    public void addVM(VM vm) {
        vms.add(vm);
    }

    public void addTask(Task task) {
        tasks.add(task);
    }

    // Getters
    public List<CloudDatacenter> getDatacenters() {
        return datacenters;
    }

    public List<Host> getHosts() {
        return hosts;
    }

    public List<User> getUsers() {
        return users;
    }

    public List<VM> getVms() {
        return vms;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    // Lookups
    public CloudDatacenter getDatacenterByName(String name) {
        return datacentersByName.get(name);
    }

    public User getUserByName(String name) {
        return usersByName.get(name);
    }

    // Metrics
    public void recordMetric(String key, Object value) {
        metrics.put(key, value);
    }

    public Object getMetric(String key) {
        return metrics.get(key);
    }

    public Map<String, Object> getAllMetrics() {
        return metrics;
    }

    // Simulation Summary
    public void setSimulationSummary(SimulationSummary summary) {
        this.simulationSummary = summary;
    }

    public SimulationSummary getSimulationSummary() {
        return simulationSummary;
    }

    // Statistics
    public int getTotalVMCount() {
        return vms.size();
    }

    public int getTotalTaskCount() {
        return tasks.size();
    }

    public int getTotalHostCount() {
        return hosts.size();
    }

    public int getTotalDatacenterCount() {
        return datacenters.size();
    }

    @Override
    public String toString() {
        return "SimulationContext{" +
                "currentTime=" + getCurrentTime() +
                ", datacenters=" + datacenters.size() +
                ", hosts=" + hosts.size() +
                ", users=" + users.size() +
                ", vms=" + vms.size() +
                ", tasks=" + tasks.size() +
                '}';
    }

    /**
     * Resets simulation state for rescheduling scenarios (e.g., Pareto front evaluation).
     * Resets clock, metrics, and all entity execution states.
     * Infrastructure (datacenter/host/VM placement) is preserved.
     */
    public void resetForRescheduling() {
        // Reset simulation clock
        this.clock.reset();

        // Clear metrics and summary
        this.metrics = new HashMap<>();
        this.simulationSummary = null;

        // Reset all tasks
        for (Task task : tasks) {
            task.resetForRescheduling();
        }

        // Reset all VMs
        for (VM vm : vms) {
            vm.resetForRescheduling();
        }

        // Reset all hosts
        for (Host host : hosts) {
            host.resetForRescheduling();
        }
    }
}
