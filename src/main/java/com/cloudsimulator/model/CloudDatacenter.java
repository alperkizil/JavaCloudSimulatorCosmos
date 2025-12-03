package com.cloudsimulator.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
