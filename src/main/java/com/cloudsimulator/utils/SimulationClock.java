package com.cloudsimulator.utils;

/**
 * Simulation clock for tracking simulation time.
 * Time is measured in seconds with a fixed time step (Δt = 1 second).
 */
public class SimulationClock {
    private long currentTime;
    private final long timeStep; // Delta t = 1 second

    public SimulationClock() {
        this.currentTime = 0;
        this.timeStep = 1;
    }

    /**
     * Advances the clock by one time step.
     */
    public void tick() {
        currentTime += timeStep;
    }

    /**
     * Gets the current simulation time in seconds.
     */
    public long getCurrentTime() {
        return currentTime;
    }

    /**
     * Gets the time step size in seconds.
     */
    public long getTimeStep() {
        return timeStep;
    }

    /**
     * Resets the clock to zero.
     */
    public void reset() {
        currentTime = 0;
    }

    /**
     * Sets the current time (for testing/debugging).
     */
    public void setCurrentTime(long time) {
        this.currentTime = time;
    }

    @Override
    public String toString() {
        return "SimulationClock{currentTime=" + currentTime + " seconds}";
    }
}
