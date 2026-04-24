# 1. Infrastructure — What the Simulator Models

## 1.1 Plain-English picture

Imagine one **datacenter**: a large room full of physical servers. Each
server is called a **host**. Hosts are shared by slicing them into
**virtual machines** (VMs) — software containers that behave like their
own little computer. **Users** submit computational **tasks** (a data
job, a render, a training step). A **scheduler** decides which VM each
task runs on. Every assignment has two consequences we care about:

- Tasks finish later if they land on already-busy or slow VMs (the
  datacenter's **makespan** — the moment the last task completes —
  stretches out).
- The datacenter consumes more electricity if more hosts are kept
  powered on, or if faster hardware is used.

The makespan experiment is about picking assignments that balance those
two consequences.

## 1.2 Datacenter

One logical datacenter, named `DC-Experiment`, with a 400 000 unit
bandwidth quota and capacity for up to 50 hosts. Defined in
`src/main/java/com/cloudsimulator/FinalExperiment/ScenarioComparisonExperimentRunner.java`
(line 350).

## 1.3 Hosts — the physical machines

Forty hosts in three families. "IPS" below stands for instructions per
second — the coarse-grained measure of compute throughput used
throughout the simulator. (In the source, host speeds are written as
whole integers, e.g. `2_500_000_000L` = 2.5 billion IPS.)

| Family   | Count | CPU cores | CPU IPS (per host)    | GPU units | RAM (MB) | Storage (MB) | Network     | Power-model label            |
|----------|-------|-----------|-----------------------|-----------|----------|--------------|-------------|------------------------------|
| CPU-only | 16    | 16        | 2 500 000 000         | 0         | 65 536   | 2 097 152    | 20 000 Mbps | `StandardPowerModel`         |
| GPU-only | 12    | 8         | 2 800 000 000         | 4         | 65 536   | 2 097 152    | 20 000 Mbps | `HighPerformancePowerModel`  |
| Mixed    | 12    | 32        | 3 000 000 000         | 4         | 131 072  | 2 097 152    | 20 000 Mbps | `HighPerformancePowerModel`  |

Source: `ScenarioComparisonExperimentRunner.java:352-368`.

The power-model **label** attached to each host is looked up by
`factory/PowerModelFactory.java` (lines 31–35), which maps:

- `StandardPowerModel` → maximum CPU 300 W, maximum GPU 250 W, idle
  CPU 50 W, idle GPU 30 W, other components 100 W;
- `HighPerformancePowerModel` → maximum CPU 500 W, maximum GPU 400 W,
  idle CPU 80 W, idle GPU 50 W, other components 150 W.

At evaluation time the energy objective actually uses the
`MeasurementBasedPowerModel`
(`src/main/java/com/cloudsimulator/model/MeasurementBasedPowerModel.java`)
instead of these legacy numbers, because the measurement model ties
power to the **workload type** (`SEVEN_ZIP`, `FURMARK`, `LLM_GPU`, …)
rather than to a blanket "CPU utilization" value. See
[`03-objectives.md`](03-objectives.md) for details.

Why three families? To make the scheduler's life interesting. Some
hosts are only useful for CPU work, some only for GPU work, and some
can do either — so a good scheduler has to think about host *shape*,
not just raw speed.

## 1.4 Virtual machines — the execution slots

Sixty VMs split across the same three families (CPU / GPU / Mixed),
each family in three **speed tiers** (fast / medium / slow). The speed
range is 10× (500 M IPS to 5 B IPS) — matching the burstable /
compute-optimized spread found in real cloud instance catalogues and
combining with the DVFS-like power-scaling exponent (2.0, see
[`03-objectives.md`](03-objectives.md)) to produce a wide Pareto
front.

| Family | Fast (count, IPS/vCPU) | Medium (count, IPS/vCPU) | Slow (count, IPS/vCPU) | vCPU | GPUs (fast/med/slow) | RAM (MB) |
|--------|------------------------|--------------------------|------------------------|------|----------------------|----------|
| CPU    | 8 × 5 000 000 000      | 8 × 2 000 000 000        | 4 × 500 000 000        | 4    | 0 / 0 / 0            | 4 096    |
| GPU    | 8 × 5 000 000 000      | 8 × 2 000 000 000        | 4 × 500 000 000        | 4    | 2 / 1 / 1            | 4 096    |
| Mixed  | 8 × 5 000 000 000      | 8 × 2 000 000 000        | 4 × 500 000 000        | 4    | 2 / 1 / 1            | 4 096    |

All VMs share `bandwidth = 102 400 Mbps` and `image size = 1 000 MB`.
Source: `ScenarioComparisonExperimentRunner.java:374-414`.

## 1.5 Users

One user identity — `ExperimentUser`
(`ScenarioComparisonExperimentRunner.java:429-434`) — with a
maximum-VM budget of 5/5/5 (per datacenter, per user-max, per per-VM
retries). The user is just a label; the interesting variety is in what
*kind* of tasks that user submits, which is the subject of the next
file.

## 1.6 How an algorithm interacts with this setup

The scheduler receives a list of 500 tasks and the list of 60 VMs. Its
job is to produce an **assignment vector** $a \in \{0, 1, \dots, 59\}^{500}$:
`a[i] = j` means "task $i$ runs on VM $j$". Every algorithm — heuristic
or metaheuristic — ultimately produces such a vector. The simulator
then plays the assignment forward in discrete one-second ticks to
measure makespan and energy.

Two natural correctness rules are enforced by the repair operator
(`PlacementStrategy/task/metaheuristic/operators/RepairOperator.java`):

- A task that requires a GPU cannot be placed on a CPU-only VM.
- Tasks cannot exceed VM capacity limits.

Invalid assignments produced by crossover or mutation are snapped back
to feasible before being evaluated.

## 1.7 Execution pipeline

Every run goes through the same eight simulation steps
(`ScenarioComparisonExperimentRunner.java:769-807`), with the scheduler
itself wired into step 5:

1. `InitializationStep` — read the configuration;
2. `HostPlacementStep` (using
   `PowerAwareLoadBalancingHostPlacementStrategy`) — put the 40 hosts
   in the datacenter;
3. `UserDatacenterMappingStep` — bind the user to `DC-Experiment`;
4. `VMPlacementStep` (using `BestFitVMPlacementStrategy`) — place each
   VM onto a host;
5. `TaskAssignmentStep` — **this is where the algorithm under test
   runs** and produces the `task → VM` assignment;
6. `VMExecutionStep` — play the assignment forward in simulation
   ticks;
7. `TaskExecutionStep` — compute makespan from the recorded VM
   timelines;
8. `EnergyCalculationStep` — tally IT energy in kWh from the same
   timelines.

Only step 5 is timed for the runtime column reported in the CSVs
(`ScenarioComparisonExperimentRunner.java:791-795`). For the
multi-objective algorithms (NSGA-II / SPEA-II / AMOSA) steps 6–8 are
then repeated once per Pareto-front member
(`simulateAllParetoSolutions`, lines 845–911), so every objective
vector stored in the results is a genuine simulation outcome rather
than the optimizer's internal estimate.
