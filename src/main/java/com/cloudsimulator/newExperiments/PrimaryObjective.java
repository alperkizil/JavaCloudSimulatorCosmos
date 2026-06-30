package com.cloudsimulator.newExperiments;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.MakespanObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.WaitingTimeObjective;
import com.cloudsimulator.steps.TaskExecutionStep;

import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * The primary objective that distinguishes the two pairwise studies (the second
 * objective is always Energy). This is the <em>only</em> structural difference
 * between {@code ScenarioComparisonExperimentRunner} (Makespan) and
 * {@code WaitingTimeExperimentRunner} (WaitingTime): the metaheuristic objective
 * class, the per-solution value read back from the simulation, and the CSV/label
 * name.
 *
 * <p>Crucially, the value is read <em>directly from the {@link TaskExecutionStep}
 * instance</em> ({@code getMakespan()} / {@code getAverageWaitingTime()}) exactly
 * as the legacy runners do (they skip the metrics step), so reproduction is
 * byte-faithful.</p>
 */
public enum PrimaryObjective {

    /** Makespan (seconds) — used by the ScenarioComparison study. */
    MAKESPAN("Makespan", MakespanObjective::new, TaskExecutionStep::getMakespan),

    /** Average task waiting time (seconds) — used by the WaitingTime study. */
    WAITING_TIME("WaitingTime", WaitingTimeObjective::new, TaskExecutionStep::getAverageWaitingTime);

    private final String csvName;
    private final Supplier<SchedulingObjective> objectiveFactory;
    private final ToDoubleFunction<TaskExecutionStep> stepExtractor;

    PrimaryObjective(String csvName,
                     Supplier<SchedulingObjective> objectiveFactory,
                     ToDoubleFunction<TaskExecutionStep> stepExtractor) {
        this.csvName = csvName;
        this.objectiveFactory = objectiveFactory;
        this.stepExtractor = stepExtractor;
    }

    /** Column/label name (e.g. {@code "Makespan"} or {@code "WaitingTime"}). */
    public String csvName() {
        return csvName;
    }

    /** A fresh {@link SchedulingObjective} instance for the metaheuristics. */
    public SchedulingObjective newObjective() {
        return objectiveFactory.get();
    }

    /** Reads the simulated primary value from a run's {@link TaskExecutionStep}. */
    public double extract(TaskExecutionStep taskExec) {
        return stepExtractor.applyAsDouble(taskExec);
    }
}
