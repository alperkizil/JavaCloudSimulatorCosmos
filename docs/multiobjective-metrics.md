# Multi-Objective Simulation Metrics Issue

## Problem
Multi-objective simulations currently report `Sim.Makespan` and `Sim.Energy` as zero for every solution. The pipeline that runs inside `SimulationEngine.runMultiObjective` does not produce a `SimulationSummary` for each solution. Downstream reporting reads the missing summary and falls back to default zero values, so the output table shows zeros even though VM/task execution occurs.

## Proposed Fix
Add a metrics-collection step after task execution in the multi-objective pipeline to create and store a `SimulationSummary` in the simulation context before reporting. The existing `MetricsCollectionStep` already records makespan and energy and writes the summary to the context; including it will allow `SimulationEngine.runMultiObjective` to report real simulated metrics instead of zeros.
