# VM ↔ Host Relationship in JavaCloudSimulatorCosmos

## IPS, CPU, and GPU semantics, and where the model departs from real datacenter scheduling

> Anchored on `src/main/java/com/cloudsimulator/FinalExperiment/ScenarioComparisonExperimentRunner.java`
> as the experiment entry point. All file/line references are to the state of the
> repository at the time of writing.

---

## ⚠️ INSTRUCTIONS FOR THE NEXT CLAUDE OPUS INSTANCE (READ FIRST)

**THIS REPORT IDENTIFIES THE FIXES (SEE §6 AND §9). DO NOT APPLY THEM UNILATERALLY.**

- **DO NOT GIVE THE USER WORD SALAD.** NO LONG-WINDED EXPLANATIONS, NO RESTATING THE
  OBVIOUS, NO HEDGING, NO NARRATING EVERY STEP. BE TERSE AND CONCRETE.
- **ASK FOR PERMISSION ON EVERY CHANGE YOU MAKE.** DO NOT EDIT, COMPILE-IN, COMMIT, OR PUSH
  ANY CHANGE WITHOUT THE USER'S EXPLICIT GO-AHEAD FOR THAT SPECIFIC CHANGE. PROPOSE IT, WAIT
  FOR APPROVAL, THEN ACT — ONE CHANGE AT A TIME.
- **CLARIFY WITH THE USER ON EVERY DECISION. MAKE NO ASSUMPTIONS ON YOUR OWN.** IF ANYTHING IS
  UNDERSPECIFIED — SCOPE, APPROACH, NAMING, VALUES, SEMANTICS, EDGE CASES — STOP AND ASK.
  NEVER GUESS, NEVER PICK A DEFAULT, NEVER FILL A GAP YOURSELF. WHEN IN DOUBT, ASK.
- **WHEN DONE, REPORT IN A FEW LINES** WHAT CHANGED, WITH `file:line` REFERENCES — NOTHING MORE.

---

## 0. Scope and method

This report traces the live execution path used by `ScenarioComparisonExperimentRunner.java`:
the infrastructure it builds (`buildScenarioConfig`, lines 345–437), the model classes
(`model/Host.java`, `VM.java`, `Task.java`, `Gpu.java`), the simulation loop
(`steps/VMExecutionStep.java` → `VM.executeOneSecond`), the analytic objectives the
metaheuristics optimize (`MakespanObjective`, `EnergyObjective`), and the power model
(`MeasurementBasedPowerModel`). It also confirms — by grep — *where each "IPS" quantity is
actually read*, which is the crux of the answer.

---

## 1. The object graph

```
CloudDatacenter 1───* Host 1───* VM 1───* Task
                          │
                          └───* Gpu   (physical, 1:1 bound to a VM)
```

- A **Host** owns a list of **VM**s (`Host.assignedVMs`) and a fixed array of physical
  **Gpu** objects (`Host.gpus`, built in the constructor, lines 116–118).
- A **VM** owns a FIFO queue of **Task**s (`VM.assignedTasks`, a `LinkedList`) and a list of
  finished tasks.
- A **Task** carries an `instructionLength` (a fixed instruction count) and a `WorkloadType`.

There are **two completely separate notions of "IPS"** in this graph, and the central finding
of this report is that **they are not connected**:

| Quantity | Field | Where defined | Where *used* |
|---|---|---|---|
| **Host IPS** | `Host.instructionsPerSecond` (commented "IPS per core", `Host.java:26`) | `HostConfig` → `InitializationStep:78` | **Never in execution.** Only: reporting, and as a *power-scaling reference* (see §6) |
| **VM IPS** | `VM.requestedIpsPerVcpu` × `requestedVcpuCount` = `getTotalRequestedIps()` (`VM.java:504`) | `VMConfig` → `InitializationStep:128` | **Everything**: task execution, makespan, energy, waiting time, load balance |

---

## 2. IPS between Host and VM — the host's IPS is decorative

When a VM executes, the task advances by the **VM's own** requested IPS, with the host nowhere
in the calculation:

```java
// VM.executeOneSecond  (VM.java:293-298)
long availableIps = getTotalRequestedIps();          // = requestedIpsPerVcpu * requestedVcpuCount
currentTaskProgress += availableIps;
currentExecutingTask.executeInstructions(availableIps);
```

A grep for `getInstructionsPerSecond()` (host IPS) returns **only** reporting code
(`HostReporter`, `BatchExperimentMain`), the constructor in `InitializationStep`, and the
power-scaling reference in `MeasurementBasedPowerModel` — **no execution path, no admission
check, no scheduling decision reads it.**

Crucially, host admission control ignores IPS entirely:

```java
// Host.hasCapacityFor  (Host.java:131-135)  — note: no IPS term
return (allocatedRamMB + ramMB <= ramCapacityMB) &&
       (allocatedCpuCores + vcpus <= numberOfCpuCores) &&
       (gpus <= getAvailableGpus());
```

**Concrete consequence in the experiment** (`ScenarioComparisonExperimentRunner.java:354–414`):
a CPU-only host is specced at `2_500_000_000` IPS (`:356`). A single "fast" CPU VM is
`5_000_000_000` IPS/vCPU × 4 vCPU = **20 billion IPS** (`:377`). That VM runs at 20 GIPS
*regardless* of being placed on a 2.5 GIPS host — an 8× physical impossibility — and
`BestFitVMPlacementStrategy` will happily stack **four** such VMs on that 16-core host
(4 vCPU each), so the host "delivers" 80 GIPS of work while its own datasheet says 2.5.
Nothing in the model notices or penalizes this.

> Note also an internal ambiguity worth resolving before publishing: the field comment calls
> host IPS "IPS **per core**" (`Host.java:26`) while
> `docs/makespan-experiment/01-infrastructure.md:35` calls it "CPU IPS (**per host**)". It
> doesn't affect execution (host IPS is unused there) but it *does* change the power reference
> in §6.

---

## 3. CPU assignment

**At placement (admission):** vCPUs map to physical cores **1:1 with no oversubscription** —
`allocatedCpuCores + vcpus <= numberOfCpuCores` (`Host.java:133`). A 16-core host holds at
most 16 vCPUs.

**At runtime:** there is **no CPU sharing, contention, or time-slicing among co-located VMs.**
`VMExecutionStep` (lines 111–129) simply loops over every RUNNING VM and gives each a full
second at its *own* `getTotalRequestedIps()`. Two VMs on the same host never slow each other
down.

**Within a single VM, execution is strictly serial — one task at a time:**

```java
// VM.executeOneSecond  (VM.java:286-298)
if (currentExecutingTask == null && !assignedTasks.isEmpty()) {
    currentExecutingTask = assignedTasks.peek();   // head of FIFO queue
    ...
}
// ... only this one task advances this tick
```

So a 4-vCPU VM does **not** run 4 tasks in parallel. It runs **one** task and pools all 4
vCPUs' IPS into that single task. `vCPU count` is therefore purely a **single-task speed
multiplier and a packing/admission unit**, never a parallelism dimension. The makespan
objective encodes exactly this serial model:

```java
// MakespanObjective.evaluate  (MakespanObjective.java:66-77)
for (int taskIdx : taskOrder) {
    long ticksForTask = (instrLen + vmIps - 1) / vmIps;   // ceil division, per task
    vmCompletionTicks += ticksForTask;                    // tasks SUM, not overlap
}
maxCompletionTicks = max(maxCompletionTicks, vmCompletionTicks);  // makespan = slowest VM
```

---

## 4. GPU assignment

GPUs use an **exclusive 1:1 passthrough binding** model (`Gpu.java`, `Host.bindGpus`
lines 177–197): a VM requesting *N* GPUs causes the host to bind *N* free physical `Gpu`
objects to it; rebinding a bound GPU throws. `getAvailableGpus()` counts free GPUs for
admission.

But the decisive point: **a GPU contributes zero compute throughput.** Look again at
`executeOneSecond` — progress is `getTotalRequestedIps()`, which is
`requestedIpsPerVcpu * requestedVcpuCount`. There is **no GPU IPS / FLOPS term anywhere.** A
`LLM_GPU` or `IMAGE_GEN_GPU` task of length *L* completes in `ceil(L / vmCpuIps)` ticks — at
the **CPU** rate — exactly like a CPU task of the same length. The GPU only does two things:

1. **Capability gating** — `VM.canAcceptTask` (lines 245–259) refuses a GPU workload on a
   `CPU_ONLY` VM and a CPU workload on a `GPU_ONLY` VM.
2. **Power** — workload type drives a fixed CPU/GPU utilization pair
   (`VM.calculateUtilization`, lines 338–364; e.g. `FURMARK → {0.08, 1.0}`) which feeds the
   power model.

In other words, the "GPU" is a **boolean capability + a power contributor**, not a compute
resource with its own speed, memory, or scheduling. Task "length" is always denominated in CPU
instructions.

---

## 5. How execution actually advances (the loop)

`VMExecutionStep` (lines 100–146) runs a global discrete clock: each tick, every RUNNING VM
executes one second (one task, at VM IPS), then every host updates power/energy, then the clock
advances. Termination is "all assigned tasks completed" (lines 168–181) — there is **no
wall-clock horizon**; the sim runs until the bag of tasks drains. Makespan is then
`lastEnd − firstStart + 1` (`TaskExecutionStep:195`).

Because the discrete loop and the analytic `MakespanObjective` both use VM IPS and both
serialize a VM's queue, the optimizer's predicted makespan and the simulated makespan agree by
construction — but both inherit every simplification above.

---

## 6. The one place Host IPS and VM IPS meet — and an inconsistency

The single semantic use of host IPS is the **speed-based power scaling** in
`MeasurementBasedPowerModel`:

```
scaleFactor = (vmIPS / referenceIPS) ^ 2.0      // POWER_SCALING_EXPONENT = 2.0, quadratic
```

(`calculateSpeedPowerFactor`, lines 543–548). Faster VMs draw quadratically more power per
second — this is what creates the makespan↔energy Pareto trade-off the experiment depends on.

The subtlety: in the path the runner uses, `EnergyObjective.setHosts()` sets the reference from
the **median host IPS** (`calculateReferenceIpsFromHosts`, `EnergyObjective.java:446`), i.e.
**2.8 GIPS** for the 40-host mix. But the value it is divided into is the **VM total IPS**
(per-vCPU × vCPUs, 2–20 GIPS). These are **dimensionally different quantities** (per-host /
per-core vs per-VM-aggregate). A fast VM gets factor `(20/2.8)² ≈ 51×`; had the reference been
taken from VMs instead (`calculateReferenceIpsFromVMs`, median = 8 GIPS, which is what the
*heuristic* strategies use — `EnergyAware` / `PowerCeiling`), the same VM gets
`(20/8)² ≈ 6.25×`. So **the energy objective's reference differs by ~8× depending on whether
host- or VM-derived reference is used**, and the two families of strategies in the same
experiment use different ones. This is worth standardizing and documenting before publication,
because absolute energy numbers (and thus the Pareto shape) hinge on it.

---

## 7. Where this deviates from real-world datacenter scheduling

Setting aside the offline assumption (granted), these are the substantive departures a reviewer
will look for:

| # | Real datacenter | This model | Impact on results |
|---|---|---|---|
| 1 | A VM/vCPU is **capped by the physical core's speed**; host throughput bounds the sum of its VMs. | **Host IPS never bounds VM IPS.** VMs run at requested IPS even when it exceeds the host's rating (8–32× in the config). | Makespan is a pure function of the *assignment* and per-VM IPS; the physical host is irrelevant to timing. |
| 2 | Co-located VMs **contend** for cores, last-level cache, memory bandwidth ("noisy neighbor"); effective speed drops as a host fills. | **Zero contention.** Each VM always gets full IPS regardless of co-tenants. | No consolidation penalty; packing many VMs on a host is "free" for performance, only affecting power. |
| 3 | Clouds **oversubscribe** pCPU:vCPU (commonly 2:1–20:1) and the hypervisor time-slices. | **No oversubscription** at admission (strict 1:1 vCPU↔core), and no time-slicing at runtime. | Capacity is rigid; you can't model burstable/shared instance classes. |
| 4 | A multi-vCPU VM runs **many threads/tasks in parallel** across its vCPUs. | **One task at a time per VM**, serially; vCPUs only multiply a single task's speed. | "Wide" vs "fast" compute is conflated; intra-VM parallelism and queueing behavior are absent. |
| 5 | GPUs are first-class compute (FLOPS, VRAM, MIG/vGPU partitioning, PCIe/NVLink contention). | GPU = **capability flag + power term, 1:1 passthrough only.** No GPU throughput; GPU tasks run at CPU IPS. | GPU-bound workloads' runtimes are not modeled; only their *power* differs. A "GPU scheduler" study can't really be done here as-is. |
| 6 | Task runtime is **data-dependent and stochastic** (cache, I/O waits, memory-bound stalls, variance). | Runtime is **deterministic**: `ceil(instructionLength / vmIPS)`. Workload type changes power but **not** speed. | E.g. `VERACRYPT` (disk-bound, 3% CPU) and `PRIME95` (100% CPU) of equal length take identical time; no I/O blocking. |
| 7 | RAM, storage, bandwidth, NUMA locality, memory bandwidth are **runtime bottlenecks**. | They are **admission-only constraints**; never throttle a running task. | Memory/network-bound regimes can't be expressed. |
| 8 | Live **migration, autoscaling, consolidation, VM boot latency**, failures. | None. VMs placed once, static for the run; instantaneous start. | No dynamic resource management dimension. |
| 9 | Tasks have **dependencies (DAGs), priorities, QoS/SLA classes, preemption**, and an **arrival process**. | Independent **bag-of-tasks**, all created at t=0, FIFO within a VM, **non-preemptive**, single user. | Pure throughput/makespan problem; no scheduling fairness, deadlines, or gang/DAG scheduling. |
| 10 | Network transfer time and data placement matter. | Bandwidth is an admission number only; **no transfer time, no data locality**. | Communication-aware scheduling can't be studied. |

---

## 8. What the model *does* get defensibly right (for balance)

- **Discrete-time accounting is honest:** ceiling division correctly wastes the remainder of a
  tick when a task finishes mid-second (`MakespanObjective` doc, lines 17–30), and predicted ==
  simulated makespan.
- **Heterogeneity of *shape*** (CPU-only / GPU-only / mixed hosts and VMs, capability gating)
  is modeled and does constrain feasible assignments via the `RepairOperator`.
- **VM speed tiers (10× spread)** plus the quadratic power-scaling give a genuine, defensible
  makespan↔energy trade-off, which is the actual object of study.
- **Power is empirically grounded** (wall-plug measurements, per-workload-type), which is
  stronger than the usual linear-utilization power curve.

The fair one-line framing for the paper: **this is a per-VM, contention-free, instruction-count
scheduling model where the VM is the atomic execution unit and the physical host is essentially
a capability/power container, not a performance-limiting resource.** That is a legitimate
abstraction for an *offline task-to-VM assignment* study — as long as it is stated explicitly,
because it means **host count/speed do not affect makespan**, and **all timing realism lives in
the per-VM IPS and the assignment vector.**

---

## 9. Suggestions before building on this

1. **State the abstraction up front** (items 1, 2, 4, 5 above are the ones reviewers will flag
   fastest), and frame the VM — not the host — as the scheduling resource.
2. **Resolve the host-IPS semantics** ("per core" comment vs "per host" doc, `Host.java:26` vs
   `01-infrastructure.md:35`) and **standardize the energy reference IPS** (host- vs VM-median)
   across *all* strategies in `ScenarioComparisonExperimentRunner`, since §6 shows it swings
   energy by ~8×.
3. If even a thin slice of realism is wanted without redesigning: (a) add an **admission check
   that Σ VM IPS ≤ host aggregate IPS**, or (b) a **contention factor** that scales VM IPS down
   as a host fills. Both are localized changes (`Host.hasCapacityForVM` / `VM.executeOneSecond`)
   and would let you defend host-level effects.

---

## Appendix: key source references

| Concept | File:line |
|---|---|
| Host IPS field ("IPS per core") | `model/Host.java:26` |
| Host admission (no IPS term) | `model/Host.java:131-147` |
| GPU 1:1 passthrough binding | `model/Host.java:177-210`, `model/Gpu.java` |
| VM total IPS | `model/VM.java:504-506` |
| VM execution (serial, VM IPS) | `model/VM.java:280-331` |
| VM compute-type gating | `model/VM.java:245-275` |
| VM per-workload utilization table | `model/VM.java:338-364` |
| Discrete simulation loop | `steps/VMExecutionStep.java:100-146` |
| Makespan objective (ceil, serial sum, max) | `objectives/MakespanObjective.java:40-81` |
| Speed-based power scaling (exp = 2.0) | `model/MeasurementBasedPowerModel.java:543-569` |
| Reference IPS from hosts vs VMs | `model/MeasurementBasedPowerModel.java:448-508`; `objectives/EnergyObjective.java:442-457` |
| Experiment infrastructure (40 hosts / 60 VMs) | `FinalExperiment/ScenarioComparisonExperimentRunner.java:345-437` |
