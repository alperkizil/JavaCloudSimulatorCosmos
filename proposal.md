# Proposal: ParetoFrontSimulationStep

## Goal
Simulate **all solutions** in a Pareto front from multi-objective algorithms (NSGA-II, SPEA2, etc.) and record actual execution metrics vs estimated.

---

## Problem Statement
Currently, NSGA-II and MOEA Task Scheduling Strategiese produce a Pareto front with estimated makespan/energy values (e.g., 21-32 seconds), but actual simulation yields significantly different results (e.g., 88 seconds). This is due to:
- Time-step discretization (1-second ticks waste remaining IPS when task completes mid-tick)
- Only one solution from the front is selected and executed
- No way to validate all Pareto solutions against actual simulation

---

## Components to Create

### 1. Reset Methods
Add `resetForNewSimulation()` to each class:

| Class | Fields to Reset |
|-------|-----------------|
| `Task` | `taskExecutionStatus`, `instructionsExecuted`, `taskExecStartTime`, `taskExecEndTime`, `taskCpuExecTime`, `assignedVmId`, `taskAssignmentTime` |
| `VM` | `assignedTasks`, `finishedTasks`, `currentExecutingTask`, `currentTaskProgress`, `vmState`, `activeSeconds`, `secondsIDLE`, `secondsExecuting`, `utilizationHistory` |
| `Host` | `totalEnergyConsumedJoules`, `currentTotalPowerDraw`, `secondsIDLE`, `secondsExecuting`, `utilizationHistory` |
| `CloudDatacenter` | `totalEnergyConsumedJoules` |
| `SimulationContext` | Reset clock, clear metrics, call reset on all entities |

### 2. `MultiObjectiveSchedulingStrategy` Interface
```java
package com.cloudsimulator.PlacementStrategy.task;

public interface MultiObjectiveSchedulingStrategy extends TaskAssignmentStrategy {
    ParetoFront getParetoFront();
    List<SchedulingObjective> getObjectives();
}
```
- `MOEA_NSGA2TaskSchedulingStrategy` implements this
- `MOEA_SPEA2TaskSchedulingStrategy` implements this
- Used to detect multi-objective algorithms at runtime

### 3. `ParetoSimulationResult` Class
```java
package com.cloudsimulator.steps;

public class ParetoSimulationResult {
    private int solutionIndex;
    private double[] estimatedObjectives;  // From NSGA-II optimization
    private double[] actualObjectives;     // From actual simulation

    // Convenience getters
    public double getEstimatedMakespan();
    public double getActualMakespan();
    public double getEstimatedEnergy();
    public double getActualEnergy();

    // Comparison
    public double getMakespanError();      // (actual - estimated) / estimated
    public double getEnergyError();
}
```

### 4. `ParetoFrontSimulationStep` Class
```java
package com.cloudsimulator.steps;

public class ParetoFrontSimulationStep implements SimulationStep {

    private final ParetoFront paretoFront;
    private final List<SchedulingObjective> objectives;
    private List<ParetoSimulationResult> results;

    public ParetoFrontSimulationStep(ParetoFront front, List<SchedulingObjective> objectives) {
        this.paretoFront = front;
        this.objectives = objectives;
        this.results = new ArrayList<>();
    }

    @Override
    public void execute(SimulationContext context) {
        List<SchedulingSolution> solutions = paretoFront.getSolutions();

        for (int i = 0; i < solutions.size(); i++) {
            SchedulingSolution solution = solutions.get(i);

            // 1. Reset all entity state
            context.resetForNewSimulation();

            // 2. Apply this solution's task assignments
            applySolution(solution, context);

            // 3. Run VM execution
            VMExecutionStep vmStep = new VMExecutionStep();
            vmStep.execute(context);

            // 4. Calculate actual metrics
            double actualMakespan = calculateActualMakespan(context);
            double actualEnergy = calculateActualEnergy(context);

            // 5. Store result
            ParetoSimulationResult result = new ParetoSimulationResult(
                i,
                solution.getObjectiveValues(),  // estimated
                new double[]{actualMakespan, actualEnergy}  // actual
            );
            results.add(result);

            // 6. Log progress
            logProgress(i, solutions.size(), result);
        }
    }

    private void applySolution(SchedulingSolution solution, SimulationContext context) {
        int[] taskAssignment = solution.getTaskAssignment();
        List<Task> tasks = context.getTasks();
        List<VM> vms = context.getVms();

        for (int taskIdx = 0; taskIdx < taskAssignment.length; taskIdx++) {
            int vmIdx = taskAssignment[taskIdx];
            Task task = tasks.get(taskIdx);
            VM vm = vms.get(vmIdx);

            task.assignToVM(vm.getId(), context.getCurrentTime());
            vm.addTask(task);
        }
    }

    public List<ParetoSimulationResult> getResults() {
        return results;
    }

    @Override
    public String getStepName() {
        return "Pareto Front Simulation";
    }
}
```

---

## Integration in BatchExperimentMain

```java
private void singleRun(ExperimentConfiguration config) {
    // ... existing setup ...

    // Step 5: Task Assignment with multi-objective strategy
    MOEA_NSGA2TaskSchedulingStrategy strategy = new MOEA_NSGA2TaskSchedulingStrategy(nsga2Config);
    engine.addStep(new TaskAssignmentStep(strategy));

    // Run steps 1-5 only (stop before execution)
    engine.run();

    // Check if multi-objective and simulate all Pareto solutions
    if (strategy instanceof MultiObjectiveSchedulingStrategy) {
        MultiObjectiveSchedulingStrategy moStrategy = (MultiObjectiveSchedulingStrategy) strategy;
        ParetoFront front = moStrategy.getParetoFront();

        // Create and run Pareto simulation step
        ParetoFrontSimulationStep paretoStep = new ParetoFrontSimulationStep(
            front,
            moStrategy.getObjectives()
        );
        paretoStep.execute(engine.getContext());

        // Print comparison report
        printParetoComparisonReport(paretoStep.getResults());
    } else {
        // Single-objective: run normal execution
        VMExecutionStep vmStep = new VMExecutionStep();
        vmStep.execute(engine.getContext());

        TaskExecutionStep taskStep = new TaskExecutionStep();
        taskStep.execute(engine.getContext());
    }
}

private void printParetoComparisonReport(List<ParetoSimulationResult> results) {
    System.out.println("========================================");
    System.out.println("PARETO FRONT SIMULATION RESULTS");
    System.out.println("========================================");
    System.out.printf("%-8s %-15s %-15s %-15s %-15s %-10s %-10s%n",
        "Index", "Est.Makespan", "Act.Makespan", "Est.Energy", "Act.Energy", "MS Error", "E Error");
    System.out.println("--------------------------------------------------------------------------------");

    for (ParetoSimulationResult r : results) {
        System.out.printf("%-8d %-15.2f %-15.2f %-15.6f %-15.6f %-10.1f%% %-10.1f%%%n",
            r.getSolutionIndex(),
            r.getEstimatedMakespan(),
            r.getActualMakespan(),
            r.getEstimatedEnergy(),
            r.getActualEnergy(),
            r.getMakespanError() * 100,
            r.getEnergyError() * 100);
    }
}
```

---

## Expected Output

```
========================================
PARETO FRONT SIMULATION RESULTS
========================================
Index    Est.Makespan    Act.Makespan    Est.Energy      Act.Energy      MS Error   E Error
--------------------------------------------------------------------------------
0        21.00           85.00           0.008012        0.009500        304.8%     18.6%
1        21.50           86.00           0.007845        0.009420        300.0%     20.1%
2        22.00           84.00           0.007690        0.009380        281.8%     22.0%
...
50       32.00           92.00           0.006450        0.008200        187.5%     27.1%
```

---

## Implementation Order

1. **Add reset methods** to Task, VM, Host, CloudDatacenter, SimulationContext
2. **Create `MultiObjectiveSchedulingStrategy`** interface
3. **Make MOEA strategies implement** the interface
4. **Create `ParetoSimulationResult`** class
5. **Create `ParetoFrontSimulationStep`** class
6. **Update `BatchExperimentMain`** to use the new step
7. **Test** with existing NSGA-II configuration

---

## Benefits

- Validates optimization estimates against real simulation
- Identifies systematic estimation errors
- Allows researchers to see full Pareto front with actual metrics
- Helps tune optimization objectives to better match reality
- Supports any multi-objective algorithm via interface

