package com.cloudsimulator.observer;

import com.cloudsimulator.model.SimulationSummary;

import java.util.function.ToDoubleFunction;

/**
 * Defines a single objective of a multi-objective study: a display name plus an
 * extractor that pulls the objective value out of a re-simulated
 * {@link SimulationSummary}.
 *
 * <p>The extractors reuse existing summary fields directly, e.g.
 * {@code summary.getPerformance().makespanSeconds} or
 * {@code summary.getEnergy().totalITEnergyKWh}. Ready-made instances for the
 * objectives used by the experiment runners live as constants on
 * {@link ExperimentSpec}.</p>
 */
public final class ObjectiveSpec {

    private final String name;
    private final ToDoubleFunction<SimulationSummary> extractor;

    /**
     * @param name      column/display name (becomes a CSV header, so it is part
     *                  of the Python plotting contract)
     * @param extractor pulls this objective's value from a simulation summary
     */
    public ObjectiveSpec(String name, ToDoubleFunction<SimulationSummary> extractor) {
        if (name == null) {
            throw new IllegalArgumentException("objective name must not be null");
        }
        if (extractor == null) {
            throw new IllegalArgumentException("objective extractor must not be null");
        }
        this.name = name;
        this.extractor = extractor;
    }

    public String getName() {
        return name;
    }

    /**
     * Extracts this objective's value from a summary.
     *
     * @param summary a re-simulated summary (must not be null)
     * @return the objective value
     */
    public double extract(SimulationSummary summary) {
        return extractor.applyAsDouble(summary);
    }

    @Override
    public String toString() {
        return "ObjectiveSpec{" + name + "}";
    }
}
