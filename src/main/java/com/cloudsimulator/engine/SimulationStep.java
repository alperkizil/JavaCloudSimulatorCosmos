package com.cloudsimulator.engine;

/**
 * Interface for simulation steps.
 * Each step represents a distinct phase in the simulation lifecycle.
 * Steps are executed sequentially by the SimulationEngine.
 */
public interface SimulationStep {
    /**
     * Executes this simulation step.
     *
     * @param context The simulation context containing all simulation state
     */
    void execute(SimulationContext context);

    /**
     * Gets the name of this step (for logging and debugging).
     *
     * @return Step name
     */
    String getStepName();
}
