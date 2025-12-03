package com.cloudsimulator.config;

import java.io.Serializable;

/**
 * Configuration for a single Datacenter.
 * Supports deep-copy for experiment variations.
 */
public class DatacenterConfig implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private int maxHostCapacity;
    private double totalMaxPowerDraw;

    public DatacenterConfig() {}

    public DatacenterConfig(String name, int maxHostCapacity, double totalMaxPowerDraw) {
        this.name = name;
        this.maxHostCapacity = maxHostCapacity;
        this.totalMaxPowerDraw = totalMaxPowerDraw;
    }

    // Copy constructor for deep copy
    public DatacenterConfig(DatacenterConfig other) {
        this.name = other.name;
        this.maxHostCapacity = other.maxHostCapacity;
        this.totalMaxPowerDraw = other.totalMaxPowerDraw;
    }

    @Override
    public DatacenterConfig clone() {
        return new DatacenterConfig(this);
    }

    // Getters and Setters
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

    public double getTotalMaxPowerDraw() {
        return totalMaxPowerDraw;
    }

    public void setTotalMaxPowerDraw(double totalMaxPowerDraw) {
        this.totalMaxPowerDraw = totalMaxPowerDraw;
    }

    @Override
    public String toString() {
        return "DatacenterConfig{" +
                "name='" + name + '\'' +
                ", maxHostCapacity=" + maxHostCapacity +
                ", totalMaxPowerDraw=" + totalMaxPowerDraw +
                '}';
    }
}
