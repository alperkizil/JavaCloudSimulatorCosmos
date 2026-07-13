# VM Execution: From Optimized Schedule to Simulated Results

This document describes what happens **after the metaheuristics have decided**
the schedule: how a `SchedulingSolution` is deployed onto the VMs' task queues,
how VMs **execute** their tasks tick by tick under the per-vCPU lane scheduler,
how the timing metrics are read back after the run — and, critically, how the
campaign runners in `newExperiments/` **re-simulate every Pareto-front member
through the real engine**, so that every published number is a simulated
measurement, never an analytic prediction.

Everything below is derived directly from the source code (class and method
names are cited throughout). Infrastructure — entity creation, placement, and
the per-tick host power model — is covered in `docs/infrastructure.md`; the
optimizers that produce the schedules are covered in
`docs/MetaheuristicTaskScheduler.md`. This document picks up where both stop:
Steps 6–8 of the pipeline, plus the re-simulation loop wrapped around them.

---

## 1. Where Execution Sits in the Pipeline

| Step | Class | Role |
|---|---|---|
| 5 | `TaskAssignmentStep` | runs the optimizer; **deploys** one solution onto the VM queues (§2) |
| 6 | `VMExecutionStep` | the tick loop: VMs execute, hosts draw power, clock advances (§3–§4) |
| 7 | `TaskExecutionStep` | post-run timing analysis: makespan, waits, turnaround (§6) |
| 8 | `EnergyCalculationStep` | post-run energy aggregation (`infrastructure.md` §8.4) |

Execution *honors* the optimizer's two decisions exactly — which VM each task
runs on, and each VM's dispatch order — and adds nothing of its own: Steps 6–8
contain **no randomness and no further decision-making**. Given a deployed
solution, the simulated outcome is fully determined.

---

## 2. Deployment: From Chromosome to VM Queues

`TaskAssignmentStep.execute` collects the unassigned tasks and the VMs placed
on hosts, then (all seven arms are batch optimizers,
`isBatchOptimizing() = true`) calls the strategy's `assignAll(tasks, vms, t)`
once, at $t = 0$.

For the metaheuristic arms, `assignAll` (e.g.
`GenerationalGAwithDominanceStrategy.assignAll`) does:

```
front <- optimizeAndGetParetoFront(tasks, vms)     # the 40k-evaluation search
selected <- front.getKneePoint()                   # 2-D knee; front[0] if degenerate
applySolution(selected, tasks, vms, t=0)
```

`applySolution` turns the chromosome into engine state, walking each VM's
order list **in dispatch order**:

| Chromosome part | Engine effect |
|---|---|
| `vmTaskOrder[j]` (per VM, in order) | `vm.assignTask(task)` — appended to VM $j$'s FIFO queue in exactly this order |
| `taskAssignment[i]` | consistency guard: a task in VM $j$'s order list whose assignment gene ≠ $j$ is skipped |
| — | `task.assignToVM(vmId, 0)` — sets `assignedVmId`, `taskAssignmentTime = 0` |

**The FIFO queue *is* the order genome.** `VM.assignedTasks` is a `LinkedList`
filled in `vmTaskOrder` sequence, and the lane scheduler (§4) dispatches
strictly from its head — so the optimizer's order decision survives into
execution verbatim.

A task the optimizer could not place (no valid VM) simply stays unassigned:
counted in `taskAssignment.tasksFailed`, ignored by the execution loop's
termination check. Under the campaign defaults every task has ≥ 1 valid VM,
so all 500 deploy.

---

## 3. The Tick Loop (`VMExecutionStep`)

`steps/VMExecutionStep.java` first starts every placed VM
(`vm.start()` → `RUNNING`), then advances simulated time in **fixed 1-second
ticks** until every *assigned* task is completed:

```java
while (some assigned task is not completed):
    for (VM vm : allVMs)                     // 1. VMs do this tick's work
        if (vm.isAssignedToHost() && vm.getVmState() == RUNNING) {
            vm.executeOneSecond(currentTime);    // §4: lanes execute
            vm.updateState();                    // VM-level counters
        }
    for (Host host : allHosts)               // 2. hosts derive power from the
        if (host.getAssignedDatacenterId() != null)   //    lanes just recorded
            host.updateState();              //    (infrastructure.md §7.1)
    context.advanceTime();                   // 3. clock: t -> t+1
```

| Property | Value |
|---|---|
| Time step | fixed $\Delta t = 1$ s; the clock only advances here |
| Order within a tick | VMs execute **before** hosts compute power — hosts read the lane snapshots (§4.4) recorded this tick |
| Termination | all assigned tasks `EXECUTED`; unassigned tasks are ignored |
| Termination guarantee | every busy lane advances $\text{effIps} > 0$ instructions per tick, queued work always occupies or eventually gets a lane, and `gpuCap = 0` falls back to unconstrained (§4.2) — so the loop always drains. There is no explicit iteration guard |
| Randomness | none — the loop, the lanes, and deployment never touch `RandomGenerator` |

Metrics recorded: `vmExecution.totalSimulationSeconds`, `.tasksCompleted`,
`.peakConcurrentTasks` (max queued tasks over ticks), `.vmSecondsExecuted` /
`.vmSecondsIdle` (per-VM-tick had-pending-work split, checked *before* the
tick) and their ratio `.vmUtilizationRatio`.

> Counter nuance: the VM's own `secondsExecuting` is updated *after* completed
> tasks are retired, so the tick in which a VM's last task finishes counts as
> idle at the VM level but as executed in the step-level `vmSecondsExecuted`.

---

## 4. Inside a VM: the Per-vCPU Lane Scheduler

`VM.executeOneSecond(t)` implements the scheduler that
`LaneSchedule` — the analytic model the objectives use during the search
(`MetaheuristicTaskScheduler.md` §1.1) — is an exact mirror of.

### 4.1 Lanes

| Concept | Value |
|---|---|
| Lanes per VM | `requestedVcpuCount` (4 for every campaign VM) |
| Lane speed | `effectiveIpsPerVcpu` — the host-clamped per-vCPU speed $v_j$ (`infrastructure.md` §6.4) |
| Concurrency | one task per lane ⇒ up to `requestedVcpuCount` tasks at once |
| GPU slots | `getBoundGpuCount()` — the physical GPUs bound at placement cap concurrent **GPU** tasks |

### 4.2 Lane refill (`fillLanes`, start of every tick)

| Rule | Behaviour |
|---|---|
| Source | the VM's FIFO queue (`assignedTasks`), scanned head → tail |
| Skip running | tasks already occupying a lane are skipped (they stay until done) |
| CPU task | takes any free lane |
| GPU task | needs a free lane **and** a free GPU slot; `runningGpu < gpuCap` |
| Head-of-line non-blocking | a GPU task that can't get a GPU is **skipped**, so later CPU tasks can still fill idle lanes this tick |
| `gpuCap = 0` | treated as unconstrained (avoids deadlock for a misconfigured GPU-capable VM with no bound GPUs) |
| Start stamp | a task entering a lane gets `startExecution(t)` → `taskExecStartTime = t`, status `EXECUTING` |

### 4.3 Advancing a tick

Every busy lane executes one second of work:
`task.executeInstructions(effIps)` adds $v_j$ instructions (clamped at the
task's total). A task of length $L$ therefore occupies its lane for

$$
\text{ticks}(L, v_j) = \left\lceil \frac{L}{v_j} \right\rceil
$$

whole ticks — a task finishing mid-tick wastes the remainder of that second,
and its freed lane is only refilled **on the next tick**. This is precisely
the ceiling-division timing the objectives assume, which is what makes the
analytic search model exact (§7.4).

A task whose counter reaches $L$ this tick gets `finishExecution(t)`
(`taskExecEndTime = t`, status `EXECUTED`) and is retired
(`finishTask`: FIFO queue → `finishedTasks`, lane freed). Retirement happens
*after* the lane loop, so the tick's utilization snapshot still contains every
lane that ran.

### 4.4 What execution feeds the power model

For each busy lane the VM records the workload type and its measured
CPU/GPU utilization into the current-tick snapshot
(`VMUtilization.addLane`) plus a per-lane history record. `Host.updateState()`
— running right after all VMs each tick — reads these snapshots and computes
the host's power draw for the tick (idle baseline + per-lane incremental power
× speed factor; full model in `infrastructure.md` §7). Energy is thus
integrated tick-by-tick **during** execution, not estimated afterwards.

---

## 5. Task Lifecycle and Timing

With creation at $t = 0$ (offline workload), assignment at $t = 0$
(deployment, §2), lane entry at start tick $s$, and lane speed $v$:

| Event | Status | Timestamp |
|---|---|---|
| Created (`InitializationStep`) | `NOT_EXECUTED` | `taskCreationTime` $= 0$ |
| Deployed (§2) | `NOT_EXECUTED` | `taskAssignmentTime` $= 0$ |
| Enters a lane (§4.2) | `EXECUTING` | `taskExecStartTime` $= s$ |
| Completes (§4.3) | `EXECUTED` | `taskExecEndTime` $= e = s + \lceil L/v \rceil - 1$ |

(The end stamp is the *last tick it ran*, hence the $-1$; the task occupied
$\lceil L/v \rceil$ ticks inclusive.) Per-task metrics derived from the stamps:

$$
\text{wait} = s - 0 = s
\qquad
\text{turnaround} = e - 0
\qquad
\text{execTime} = e - s = \lceil L/v \rceil - 1
$$

---

## 6. Worked Example

One VM, 2 lanes at $v = 2$ G/s, 1 bound GPU. Deployed queue order:
**T1** (GPU, 3 G), **T2** (GPU, 4 G), **T3** (CPU, 2 G).

| Tick | Lane fill (from queue head) | Lane 1 | Lane 2 | Completions |
|---|---|---|---|---|
| 0 | T1 takes lane+GPU; T2 GPU-blocked → **skipped**; T3 takes lane 2 | T1: 2/3 G | T3: 2/2 G | T3 ($e=0$) |
| 1 | T2 still GPU-blocked (T1 holds the GPU) — lane 2 idles though free | T1: 3/3 G | — | T1 ($e=1$) |
| 2 | T2 takes lane+GPU ($s=2$) | T2: 2/4 G | — | |
| 3 | — | T2: 4/4 G | — | T2 ($e=3$) |

Ceiling timing visible on T1: $3\,\text{G}$ at $2$ G/s needs $1.5$ s but
occupies $\lceil 3/2 \rceil = 2$ ticks — the half-second remainder is wasted.

| Task | $s$ | $e$ | wait | turnaround | execTime |
|---|---:|---:|---:|---:|---:|
| T1 | 0 | 1 | 0 | 1 | 1 |
| T2 | 2 | 3 | 2 | 3 | 1 |
| T3 | 0 | 0 | 0 | 0 | 0 |

Run-level (§7): tick makespan $= 3 - 0 + 1 = 4$; fractional makespan
$= \max(0{+}\tfrac{3}{2},\, 2{+}\tfrac{4}{2},\, 0{+}\tfrac{2}{2}) = 4.0$;
mean wait $= 2/3$ s.

---

## 7. Post-Run Analysis (`TaskExecutionStep`)

Step 7 walks all tasks once after the loop ends and aggregates:

| Metric | Definition |
|---|---|
| Makespan (ticks) | $\text{lastEnd} - \text{firstStart} + 1$ — inclusive-tick convention: a task starting at tick 0 and ending at tick $N$ ran $N{+}1$ ticks |
| **Fractional makespan** | $\max_i \big( s_i + L_i / v_{j(i)} \big) - \text{firstStart}$ — instruction-resolution finish of the last task; satisfies $\text{makespan} - 1 < \text{fractional} \le \text{makespan}$ |
| Avg waiting time | mean of $s_i - 0$ over completed tasks |
| Avg turnaround | mean of $e_i - 0$ |
| Avg execution time | mean of `taskCpuExecTime` $= e_i - s_i$ |
| Throughput | completed ÷ makespan (tasks/s) |
| Sessions | users with all tasks complete get `finishSession(lastCompletion)` |

**The bit-for-bit contract.** The fractional makespan is computed with the
*identical IEEE-754 expression* as `LaneSchedule.getCompletionExact()`
(`startTick + length / (double) effIps` — enforced by a source comment), so
the simulated makespan matches the metaheuristics' predicted
`MakespanObjective` value exactly, bit for bit. It exists because the
tick-rounded makespan collapses to whole-second plateaus and would flatten
the Pareto fronts.

What each study reads off the executed run (`PrimaryObjective`):

| Study | Primary value read | Second value read |
|---|---|---|
| Makespan | `TaskExecutionStep.getFractionalMakespan()` | `EnergyCalculationStep.getTotalITEnergyKWh()` |
| WaitingTime / PowerCeiling | `TaskExecutionStep.getAverageWaitingTime()` | same (PowerCeiling also captures the Step-8 coincident fleet peak) |

---

## 8. Every Published Point Is a Full Simulation

This is the part that makes the campaign results *measurements*. During the
search, a candidate is *never* simulated — all 40,000 evaluations per run
score chromosomes analytically through `LaneSchedule`
(`MetaheuristicTaskScheduler.md` §1.1). But nothing analytic is ever
published: the metaheuristics only decide **which schedules are worth
measuring**, and the engine then measures each one.

### 8.1 The per-run sequence (`CampaignRunner.doRunOne`)

For every (arm × seed × scenario) run:

```
1. fresh SimulationEngine; RandomGenerator.initialize(seed)
2. Steps 1-4: init, host placement, user/DC mapping, VM placement
3. build the arm's strategy from the placed context (warm seeds)
4. Step 5: the optimizer runs (40k analytic evaluations) and deploys the knee point
5. Steps 6-8 execute the knee point  ->  (selectedPrimary, selectedEnergy)
6. simulateAllParetoSolutions: re-simulate EVERY front member          (§8.2)
7. the run's published front = the list of SIMULATED pairs from step 6
```

### 8.2 The re-simulation loop (`CampaignRunner.simulateAllParetoSolutions`)

For **each** solution in the arm's published archive
(`getLastParetoFront()` — the every-evaluated-candidate dominance archive,
`MetaheuristicTaskScheduler.md` §6):

```
for solution in front.getSolutions():
    context.resetForRescheduling()          # wipe execution state, keep placement
    reset datacenter stats (activeSeconds, momentary draw)
    applySolution(solution, tasks, vms, 0)  # deploy this member (§2)
    new VMExecutionStep().execute(context)  # full tick loop (§3-§4)
    new TaskExecutionStep().execute(context)
    new EnergyCalculationStep().execute(context)
    record (primary.extract(taskExec), energyCalc.getTotalITEnergyKWh())
    [PowerCeiling: also record energyCalc.getPeakTotalPowerWatts()]
```

What `resetForRescheduling` clears vs. preserves between members:

| Reset (per member) | Preserved |
|---|---|
| clock → 0; context metrics | datacenter/host/VM **placement** |
| every task: assignment, status, progress, all timestamps | VM→host binding, bound cores/GPUs, clamped `effectiveIpsPerVcpu` |
| every VM: queues, lanes, counters, utilization history (state back to `RUNNING`) | the run's RNG state (irrelevant — Steps 6–8 draw no randomness) |
| every host: power/energy counters, per-tick series | |

So each front member is executed on **identical infrastructure from an
identical clean state** — the members differ only in the deployed schedule,
and the re-simulations are mutually independent (order cannot matter).

### 8.3 Accounting and edge cases

| Item | Value |
|---|---|
| Full engine simulations per run | $1 + \lvert\text{front}\rvert$ (knee point + every member) |
| What lands in `AlgorithmRunResult` (→ all CSVs, fronts, HV, contributions) | **only** the simulated pairs from §8.2 — the analytic search values are never exported |
| Empty front (degenerate) | fall back to the knee point's simulated pair |
| Single-solution baselines (e.g. the `"LPT"` label — not a scored arm) | no front: the one deployed schedule's simulated pair is the result |
| PowerCeiling | each member's simulated coincident peak feeds `PowerCapCalibrator.deriveCaps` and the feasibility reports |
| Optional per-solution schedule export | `SolutionDetailsCollector` (read-only, `ExperimentConfig.exportSolutionDetails`) |

### 8.4 Why prediction and measurement agree

Re-simulating would be pointless if the engine disagreed with the search
model — the front would scatter. It doesn't, by construction:

| Piece | Guarantee |
|---|---|
| Scheduler semantics | `LaneSchedule` mirrors §4 exactly: per-vCPU FIFO lanes at the clamped speed, $\lceil L/v \rceil$ ticks, GPU slot cap, head-of-line non-blocking skip |
| Makespan | bit-for-bit identical (documented IEEE-754 contract, §7) |
| Waiting time | identical integers: predicted start ticks = engine start stamps |
| Energy | same measured model, same speed factors, same idle-window gating (`infrastructure.md` §7); engine integrates per tick, objective sums per task |

The division of labor is deliberate: the analytic model makes 40,000
evaluations per run affordable; the engine re-simulation makes every
published point a measurement of the full simulator — with energy integrated
tick-by-tick from the `MeasurementBasedPowerModel` — rather than a promise.

---

## 9. Reproducibility Notes

- Steps 6–8 and deployment are **RNG-free**; all campaign randomness is spent
  inside the Step-5 search. Given a solution, the simulated result is a pure
  function of the placed infrastructure.
- Re-simulation preserves placement and speed clamps (§8.2), so every front
  member of a run is measured on the same fleet its search assumed.
- `CampaignReproducibilityTest` verifies same-seed runs produce bit-identical
  simulated fronts end-to-end.

## 10. Source Map

| Topic | Where to look |
|---|---|
| Deployment step | `steps/TaskAssignmentStep.java` |
| Knee-point selection + `applySolution` | `metaheuristic/GenerationalGAwithDominanceStrategy.java` (pattern shared by all arms), `metaheuristic/ParetoFront.java` |
| Multi-objective strategy contract | `PlacementStrategy/task/MultiObjectiveTaskSchedulingStrategy.java` |
| Tick loop | `steps/VMExecutionStep.java` |
| Lane scheduler, refill rules, lane snapshots | `model/VM.java` (`executeOneSecond`, `fillLanes`), `model/VMUtilization.java` |
| Task lifecycle & timestamps | `model/Task.java` |
| Post-run timing analysis | `steps/TaskExecutionStep.java` |
| Study-specific value extraction | `newExperiments/PrimaryObjective.java` |
| Re-simulation loop | `newExperiments/CampaignRunner.java` (`doRunOne`, `simulateAllParetoSolutions`) |
| State reset between members | `engine/SimulationContext.resetForRescheduling`, `model/{Task,VM,Host}.resetForRescheduling` |
| Analytic mirror of the scheduler | `metaheuristic/objectives/LaneSchedule.java` |
