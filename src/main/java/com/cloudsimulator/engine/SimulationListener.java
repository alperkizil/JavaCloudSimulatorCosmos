package com.cloudsimulator.engine;

import com.cloudsimulator.model.SimulationSummary;

/**
 * Listener that an external collector (e.g. {@code ExperimentObserver}) can
 * attach to a {@link SimulationEngine} to capture results at the end of a run
 * without the runners having to re-implement campaign bookkeeping.
 *
 * <p>This is the "Shape B" attachment from the ExperimentObserver design:
 * the engine exposes {@link SimulationEngine#addListener(SimulationListener)}
 * and fires the callbacks below; listeners observe, they do not drive the
 * simulation. Both methods are {@code default} no-ops, so the interface adds
 * zero behavioural impact for existing (non-listening) callers.</p>
 *
 * <p>Capture is <em>end-of-run only</em> &mdash; there are no per-generation or
 * per-step hooks.</p>
 */
public interface SimulationListener {

    /**
     * Fired at the end of a successful single-objective {@link SimulationEngine#run()}.
     *
     * <p>Note the {@code summary} is whatever {@code context.getSimulationSummary()}
     * returned, which is {@code null} when no {@code MetricsCollectionStep} ran in
     * the pipeline. Implementations must tolerate a {@code null} summary.</p>
     *
     * @param context the simulation context (already fully executed)
     * @param summary the collected summary, or {@code null} if none was produced
     */
    default void onRunComplete(SimulationContext context, SimulationSummary summary) {
        // no-op by default
    }

    /**
     * Fired at the end of {@link SimulationEngine#runMultiObjective()}, after the
     * full Pareto front has been re-simulated.
     *
     * @param result the aggregated multi-objective result (may carry zero
     *               solution results if the Pareto front was empty)
     */
    default void onMultiObjectiveComplete(MultiObjectiveSimulationResult result) {
        // no-op by default
    }
}
