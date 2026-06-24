package com.cloudsimulator.model;

import com.cloudsimulator.enums.ComputeType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Represents a Cloud Datacenter that hosts multiple Host machines and serves customers.
 */
public class CloudDatacenter {
    private static final AtomicLong idGenerator = new AtomicLong(0);

    private final long id;
    private long activeSeconds;
    private List<Host> hosts;
    private List<String> customers;  // List of customer IDs
    private String name;
    private int maxHostCapacity;
    private double totalMomentaryPowerDraw;  // in Watts
    private double totalMaxPowerDraw;         // in Watts
    private boolean isActive;

    /**
     * Constructor with default max host capacity of 50.
     */
    public CloudDatacenter(String name) {
        this(name, 50);
    }

    /**
     * Constructor with custom max host capacity.
     */
    public CloudDatacenter(String name, int maxHostCapacity) {
        this.id = idGenerator.incrementAndGet();
        this.name = name;
        this.maxHostCapacity = maxHostCapacity;
        this.activeSeconds = 0;
        this.hosts = new ArrayList<>();
        this.customers = new ArrayList<>();
        this.totalMomentaryPowerDraw = 0.0;
        this.totalMaxPowerDraw = 0.0;
        this.isActive = false;
    }

    /**
     * Constructor with all parameters.
     */
    public CloudDatacenter(String name, int maxHostCapacity, double totalMaxPowerDraw) {
        this(name, maxHostCapacity);
        this.totalMaxPowerDraw = totalMaxPowerDraw;
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public long getActiveSeconds() {
        return activeSeconds;
    }

    public void setActiveSeconds(long activeSeconds) {
        this.activeSeconds = activeSeconds;
    }

    public void incrementActiveSeconds() {
        this.activeSeconds++;
    }

    public List<Host> getHosts() {
        return hosts;
    }

    public void setHosts(List<Host> hosts) {
        this.hosts = hosts;
    }

    public void addHost(Host host) {
        if (this.hosts.size() < maxHostCapacity) {
            this.hosts.add(host);
            host.setAssignedDatacenterId(this.id);
        } else {
            throw new IllegalStateException("Datacenter has reached maximum host capacity: " + maxHostCapacity);
        }
    }

    public void removeHost(Host host) {
        this.hosts.remove(host);
    }

    public List<String> getCustomers() {
        return customers;
    }

    public void setCustomers(List<String> customers) {
        this.customers = customers;
    }

    public void addCustomer(String customerId) {
        if (!this.customers.contains(customerId)) {
            this.customers.add(customerId);
        }
    }

    public void removeCustomer(String customerId) {
        this.customers.remove(customerId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaxHostCapacity() {
        return maxHostCapacity;
    }

    public void setMaxHostCapacity(int maxHostCapacity) {
        this.maxHostCapacity = maxHostCapacity;
    }

    public double getTotalMomentaryPowerDraw() {
        return totalMomentaryPowerDraw;
    }

    public void setTotalMomentaryPowerDraw(double totalMomentaryPowerDraw) {
        this.totalMomentaryPowerDraw = totalMomentaryPowerDraw;
    }

    public double getTotalMaxPowerDraw() {
        return totalMaxPowerDraw;
    }

    public void setTotalMaxPowerDraw(double totalMaxPowerDraw) {
        this.totalMaxPowerDraw = totalMaxPowerDraw;
    }

    /**
     * Calculates the total momentary power draw from all hosts.
     */
    public void updateTotalMomentaryPowerDraw() {
        this.totalMomentaryPowerDraw = hosts.stream()
            .mapToDouble(Host::getCurrentTotalPowerDraw)
            .sum();
    }

    /**
     * Checks if the datacenter can accept a new host.
     */
    public boolean canAcceptHost() {
        return hosts.size() < maxHostCapacity;
    }

    /**
     * Checks if the power limit has been reached.
     */
    public boolean isPowerLimitReached() {
        updateTotalMomentaryPowerDraw();
        return totalMomentaryPowerDraw >= totalMaxPowerDraw;
    }

    /**
     * Checks if the datacenter can accommodate a specific host without exceeding power limit.
     * Updates the current power draw before checking to ensure accurate projection.
     */
    public boolean canAccommodateHost(Host host) {
        if (!canAcceptHost()) return false;

        // Update current power draw from all existing hosts
        updateTotalMomentaryPowerDraw();

        // Get the host's power draw (use idle power if not yet calculated)
        double hostPower = host.getCurrentTotalPowerDraw();
        if (hostPower <= 0 && host.getPowerModel() != null) {
            hostPower = host.getPowerModel().calculateTotalPower(0.0, 0.0);
        }

        double projectedPower = totalMomentaryPowerDraw + hostPower;
        return projectedPower <= totalMaxPowerDraw;
    }

    /**
     * Gets all hosts that match the required compute type.
     */
    public List<Host> getAvailableHosts(ComputeType requiredType) {
        return hosts.stream()
            .filter(h -> h.getComputeType() == requiredType ||
                        h.getComputeType() == ComputeType.CPU_GPU_MIXED)
            .collect(Collectors.toList());
    }

    /**
     * Gets all hosts that have capacity for the specified resources.
     */
    public List<Host> getHostsWithCapacity(long ramMB, int vcpus, int gpus) {
        return hosts.stream()
            .filter(h -> h.hasCapacityFor(ramMB, vcpus, gpus))
            .collect(Collectors.toList());
    }

    /**
     * Activates the datacenter at the given timestamp.
     */
    public void activate(long timestamp) {
        if (!isActive) {
            isActive = true;
            this.activeSeconds = 0;
        }
    }

    /**
     * Gets whether the datacenter is active.
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Gets the average power draw across all hosts.
     */
    public double getAveragePowerDraw() {
        return activeSeconds > 0 && !hosts.isEmpty() ?
            hosts.stream().mapToDouble(Host::getCurrentTotalPowerDraw).average().orElse(0.0) : 0.0;
    }

    /**
     * Gets the total energy consumed by all hosts (in Joules).
     */
    public double getTotalEnergyConsumed() {
        return hosts.stream()
            .mapToDouble(Host::getTotalEnergyConsumed)
            .sum();
    }

    /**
     * Gets the total energy consumed in kWh.
     */
    public double getTotalEnergyConsumedKWh() {
        return getTotalEnergyConsumed() / 3_600_000.0;
    }

    // ==================== Per-tick tracking (aggregated from hosts) ====================
    // The datacenter follows its power and host busy/idle state tick-by-tick by
    // aggregating the per-tick series each Host records during the simulation.
    // No per-tick state is stored here — everything derives from the hosts, so it
    // always reflects the latest run and needs no separate reset.

    /** Number of simulation ticks the datacenter ran (= longest host series = makespan). */
    public int getTickCount() {
        int max = 0;
        for (Host h : hosts) {
            max = Math.max(max, h.getPowerSeriesWatts().size());
        }
        return max;
    }

    /** Seconds the datacenter was powered on (open) — equals the tick count. */
    public long getOpenSeconds() {
        return getTickCount();
    }

    /** Datacenter total power draw (W) at tick t — the sum of host powers that tick. */
    public double getDcPowerAtTick(int t) {
        double sum = 0.0;
        for (Host h : hosts) {
            sum += h.getHostPowerAtTick(t);
        }
        return sum;
    }

    /** Datacenter total power (W) per tick (index = tick). Sums to getTotalEnergyConsumed(). */
    public List<Double> getPowerSeriesWatts() {
        int n = getTickCount();
        List<Double> series = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            series.add(getDcPowerAtTick(t));
        }
        return series;
    }

    /** One host's power contribution (W) at tick t (0 if no such host). */
    public double getHostContributionAtTick(long hostId, int t) {
        for (Host h : hosts) {
            if (h.getId() == hostId) {
                return h.getHostPowerAtTick(t);
            }
        }
        return 0.0;
    }

    /** One host's per-tick power series (W); empty if no such host. */
    public List<Double> getHostPowerSeries(long hostId) {
        for (Host h : hosts) {
            if (h.getId() == hostId) {
                return h.getPowerSeriesWatts();
            }
        }
        return new ArrayList<>();
    }

    /** Number of hosts with a busy vCPU lane at tick t. */
    public int getBusyHostCountAtTick(int t) {
        int count = 0;
        for (Host h : hosts) {
            if (h.isBusyAtTick(t)) {
                count++;
            }
        }
        return count;
    }

    /** Number of hosts idle (no busy lane) at tick t. */
    public int getIdleHostCountAtTick(int t) {
        return hosts.size() - getBusyHostCountAtTick(t);
    }

    /** Idle hosts as a percentage of all hosts at tick t (idleHosts / totalHosts × 100). */
    public double getIdleHostPercentageAtTick(int t) {
        return hosts.isEmpty() ? 0.0 : 100.0 * getIdleHostCountAtTick(t) / hosts.size();
    }

    /** Per-tick busy-host counts (index = tick). */
    public List<Integer> getBusyHostCountSeries() {
        int n = getTickCount();
        List<Integer> series = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            series.add(getBusyHostCountAtTick(t));
        }
        return series;
    }

    /** Per-tick idle-host counts (index = tick). */
    public List<Integer> getIdleHostCountSeries() {
        int n = getTickCount();
        List<Integer> series = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            series.add(getIdleHostCountAtTick(t));
        }
        return series;
    }

    /** Per-tick idle-host percentage (index = tick). */
    public List<Double> getIdleHostPercentageSeries() {
        int n = getTickCount();
        List<Double> series = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            series.add(getIdleHostPercentageAtTick(t));
        }
        return series;
    }

    @Override
    public String toString() {
        return "CloudDatacenter{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", activeSeconds=" + activeSeconds +
                ", hosts=" + hosts.size() +
                ", customers=" + customers.size() +
                ", maxHostCapacity=" + maxHostCapacity +
                ", totalMomentaryPowerDraw=" + totalMomentaryPowerDraw +
                ", totalMaxPowerDraw=" + totalMaxPowerDraw +
                '}';
    }
}
