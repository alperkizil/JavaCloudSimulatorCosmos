# 4b. Greedy Heuristics — `WorkloadAware` and `EnergyAware`

These two heuristics are **not** scored as stand-alone algorithms in
the recorded makespan run, but they do play an essential role: they
produce the two chromosomes injected as warm-start seeds into the
initial population of every metaheuristic in
[`04-algorithms.md`](04-algorithms.md). The injection site is
`computeHeuristicSeed(...)` at
`ScenarioComparisonExperimentRunner.java:525-574`; it runs the
heuristic, records the assignment as an integer vector, then calls
`context.resetForRescheduling()` so the metaheuristic that follows
sees a clean simulation state.

Both heuristics are **per-task greedy**, **online**, **deterministic**
given the task arrival order, and both respect the two correctness
rules the simulator enforces:

- A task may only run on a VM owned by the same user
  (`vm.getUserId().equals(task.getUserId())`);
- A task may only run on a VM that accepts it
  (`vm.canAcceptTask(task)`, which rolls in the GPU/CPU compatibility
  and capacity limits).

Throughout this file, $N = 500$ is the number of tasks, $M = 60$ is
the number of VMs, and $k$ is the average queue length on a VM.

---

## 4b.1 `WorkloadAwareTaskAssignmentStrategy`

### 4b.1.1 Plain-but-formal English

Given a new task $t$, the strategy estimates how long each candidate
VM $v$ would take to drain its queue **plus** the new task, and picks
the VM that completes first. Concretely, for every candidate VM the
strategy computes an *estimated completion time*

$$
\widehat{C}(v, t)
\;=\;
\frac{\bigl(\sum_{t' \in \mathrm{queue}(v)} \mathrm{remaining\_instructions}(t')\bigr)
      \;+\;\mathrm{instruction\_length}(t)}
     {\mathrm{total\_ips}(v)},
$$

where:

- $\mathrm{remaining\_instructions}(t')$ is the work still pending on
  task $t'$ in VM $v$'s queue (not the task's original length, which
  matters if $t'$ has partially executed);
- $\mathrm{instruction\_length}(t)$ is the new task's total
  instruction count;
- $\mathrm{total\_ips}(v) = \mathrm{ips\_per\_vcpu}(v) \cdot \mathrm{vcpu\_count}(v)$
  is the VM's aggregate throughput (exposed via
  `VM.getTotalRequestedIps()`).

The VM that minimises $\widehat{C}(v, t)$ among all eligible VMs wins
the task. Ties are broken by selecting the **first** VM encountered in
the candidate list (the implementation uses strict `<` when updating
the running best, so no tie-breaking heuristic fires). If a VM
reports zero IPS the strategy skips it — dividing by zero would give
infinite runtime estimates and the skip is safer than a sentinel.

Three consequences follow from this rule:

1. **Task instruction length matters.** A whale (25–40 B instructions)
   on a slow VM pushes its estimated completion time far above the
   alternatives, so the heuristic refuses to dump whales onto slow
   VMs unless every eligible VM is also slow.
2. **VM throughput matters.** Fast VMs (5 B IPS) absorb new work with
   tiny increments in $\widehat{C}$; slow VMs (500 M IPS) see much
   larger increments per added task, so the heuristic packs whales
   onto the fast tier first.
3. **Current queue load matters as *work*, not as *count*.** The sum
   $\sum \mathrm{remaining\_instructions}$ means a VM with ten tiny
   tasks can still look more attractive than a VM with one queued
   whale. This is the main reason to prefer this heuristic over a
   plain "shortest queue".

Time and space complexity: for each task the strategy visits every
candidate VM ($M$) and walks each VM's queue to compute
$\sum \mathrm{remaining\_instructions}$ ($k$ entries on average), so
the cost per task is $\mathcal{O}(M \cdot k)$. Over all $N$ tasks the
strategy is $\mathcal{O}(N \cdot M \cdot k)$. No persistent state is
kept on the strategy object itself — all the state it reads is the
live queue state on the VM objects — so it is safe to call from
multiple dispatchers in sequence.

Where the simulator invokes this strategy: whenever a
`TaskAssignmentStep` is driven by an instance of
`WorkloadAwareTaskAssignmentStrategy`. In the makespan experiment
that happens only inside `computeHeuristicSeed(...)`; the resulting
chromosome is then handed to `GA_Makespan_Dominance`,
`SA_Makespan_Dominance`, `NSGA-II`, `SPEA-II`, and `AMOSA` as a
warm-start individual.

### 4b.1.2 Pseudocode

```text
Algorithm  WorkloadAware.selectVM(t, candidates)
Input:   task t, list of candidate VMs
Output:  a VM from candidates, or ⊥ if none is eligible

 1: if candidates is empty then return ⊥
 2: best    ← ⊥
 3: bestEct ← +∞                                    ⟵ best estimated completion time so far
 4: for each vm ∈ candidates do
 5:     if vm.userId ≠ t.userId          then continue  ⟵ ownership filter
 6:     if not vm.canAcceptTask(t)        then continue  ⟵ GPU/CPU and capacity filter
 7:     ips ← vm.totalRequestedIps                 ⟵ ipsPerVCPU × vcpuCount
 8:     if ips = 0                        then continue  ⟵ avoid division by zero
 9:     load ← 0
10:     for each t' ∈ vm.assignedTasks do          ⟵ already-queued tasks, possibly mid-execution
11:         load ← load + t'.remainingInstructions
12:     end for
13:     ect ← (load + t.instructionLength) / ips   ⟵ estimated completion time
14:     if ect < bestEct then                      ⟵ strict <, so earliest candidate wins ties
15:         bestEct ← ect
16:         best    ← vm
17:     end if
18: end for
19: return best

Algorithm  WorkloadAware.assignAll(tasks, vms, now)
Input:   ordered task list tasks, VM list vms, simulation time now
Output:  task → VM map

 1: assignments ← empty ordered map
 2: for each t ∈ tasks (in given order) do
 3:     v ← WorkloadAware.selectVM(t, vms)
 4:     if v ≠ ⊥ then
 5:         t.assignToVM(v.id, now)                ⟵ mutates t so later tasks see updated queue
 6:         v.assignTask(t)                        ⟵ mutates v.assignedTasks → affects future ECT
 7:         assignments[t] ← v
 8:     end if
 9: end for
10: return assignments
```

Source:
`src/main/java/com/cloudsimulator/PlacementStrategy/task/WorkloadAwareTaskAssignmentStrategy.java`
(lines 36–101; `selectVM` at lines 38–72, `calculateQueueWorkload`
at lines 80–89).

---

## 4b.2 `EnergyAwareTaskAssignmentStrategy`

### 4b.2.1 Plain-but-formal English

Where the workload-aware heuristic minimises per-task completion
time, the energy-aware heuristic minimises the **marginal energy
cost** of adding the task to each candidate VM. This marginal cost
has two components, chosen to mirror the two terms of the global
energy objective in [`03-objectives.md`](03-objectives.md):

$$
\Delta\mathrm{E}(v, t)
\;=\;
\underbrace{P^{\text{incr}}_{v,\,\text{wl}(t)} \cdot \tau(t, v)}_{\text{execution energy}}
\;+\;
\underbrace{\max\bigl(0,\; \hat{C}(v) + \tau(t, v) - C^{\text{global}}\bigr) \cdot K_{\text{on}} \cdot P_{\text{idle}}}_{\text{idle extension cost}}
$$

where, for the candidate VM $v$ and workload $\text{wl}(t)$ of the
new task $t$:

- $\tau(t, v) = \lceil \mathrm{instruction\_length}(t) / \mathrm{total\_ips}(v) \rceil$
  is the execution time in ticks (ceiling division matches the
  simulator's discrete one-second clock);
- $P^{\text{incr}}_{v,\,w}$ is the incremental power (watts above
  idle) that VM $v$ draws while executing a task of workload type
  $w$. It comes from
  `MeasurementBasedPowerModel.calculateIncrementalPowerWithSpeedScaling(
  workloadType, cpuUtil, gpuUtil, vmIps)`, where `(cpuUtil, gpuUtil)`
  is taken from the workload's empirical profile and the method
  applies the speed-based scaling from [§3.4](03-objectives.md#34-speed-based-power-scaling);
- $\hat{C}(v)$ is the VM's current completion tick as maintained by
  the heuristic itself (see below);
- $C^{\text{global}}$ is the running global makespan across all VMs;
- $K_{\text{on}}$ is the number of distinct hosts that carry at least
  one running VM;
- $P_{\text{idle}}$ is the per-host idle power
  (`powerModel.getScaledIdlePower()`), taken once at
  initialisation.

The two terms capture the two drivers of datacenter energy:

1. **Execution energy.** Power times time for the task itself. Under
   the quadratic speed-scaling rule from §3.4, this term *prefers
   slower, more power-efficient VMs* for the same unit of work. On
   its own, this rule would cram every task onto the slowest
   compatible VM.
2. **Idle extension cost.** If assigning the task to VM $v$ would
   push $v$'s finish time *past* the current global makespan, the
   overshoot extends the time every other active host sits idle but
   powered on. The cost is the overshoot multiplied by
   $K_{\text{on}} \cdot P_{\text{idle}}$. This term *prefers faster
   VMs* once doing so would shift the bottleneck, which is precisely
   what keeps rule (1) from running amok on whales.

The heuristic walks the tasks in the order they are given. On each
task it recomputes the two terms for every eligible VM, picks the VM
with the **smallest $\Delta\mathrm{E}(v, t)$**, then updates its own
per-VM completion-tick map and the running global makespan so the
next task's calculation reflects the assignment. Initialisation
(`initializeContext`, lines 104–132) walks the VM list once to set
$P_{\text{idle}}$, $K_{\text{on}}$, and each VM's starting
completion tick from whatever the VM is already carrying.

Complexity is the same as the workload-aware heuristic: per task
$\mathcal{O}(M \cdot k)$ for the completion-tick bookkeeping and the
per-VM candidate loop, so $\mathcal{O}(N \cdot M \cdot k)$ overall.
Unlike `WorkloadAware`, this heuristic **does** keep state across
tasks (`completionTicksByVm`, `globalMakespanTicks`,
`contextInitialized`) — the state is what couples the two terms.

When `selectVM(...)` is called in isolation (without the runner
calling `assignAll(...)` first), the strategy still works: it
initialises itself against the candidate list on the first call
(`contextInitialized` guard, lines 86–89), but the idle-extension
term is then only approximate because it only sees that subset of
VMs. The runner always goes through `assignAll(...)`, so this
fallback path is not exercised in the makespan experiment.

Where the simulator invokes this strategy: exactly like
`WorkloadAware`, only inside `computeHeuristicSeed(...)`. The
resulting chromosome is given to `GA_Energy_Dominance`,
`SA_Energy_Dominance`, `NSGA-II`, `SPEA-II`, and `AMOSA` as a
warm-start individual (runner lines 473, 485, 493, 498, 503, 508).

### 4b.2.2 Pseudocode

```text
Algorithm  EnergyAware.assignAll(tasks, vms, now)
Input:   ordered task list tasks, VM list vms, simulation time now
Output:  task → VM map

 1: initializeContext(vms)                          ⟵ see below
 2: assignments ← empty ordered map
 3: for each t ∈ tasks (in given order) do
 4:     v ← pickBestVm(t, vms)
 5:     if v = ⊥ then continue
 6:     ips       ← v.totalRequestedIps
 7:     execTicks ← ceil(t.instructionLength / ips)
 8:     newFinish ← completionTicks[v] + execTicks
 9:     completionTicks[v] ← newFinish            ⟵ update per-VM clock
10:     if newFinish > globalMakespan then        ⟵ update global clock
11:         globalMakespan ← newFinish
12:     t.assignToVM(v.id, now)
13:     v.assignTask(t)
14:     assignments[t] ← v
15: end for
16: return assignments


Procedure  initializeContext(vms)
 1: powerModel.calculateReferenceIpsFromVMs(vms)      ⟵ median-IPS reference for speed scaling
 2: idlePower ← powerModel.scaledIdlePower
 3: hostIds ← ∅
 4: for each vm ∈ vms with assignedHostId ≠ ⊥ do
 5:     hostIds ← hostIds ∪ {vm.assignedHostId}
 6: end for
 7: K_on ← max(1, |hostIds|)                          ⟵ active host count
 8: completionTicks ← empty IdentityHashMap
 9: globalMakespan  ← 0
10: for each vm ∈ vms do
11:     ips ← vm.totalRequestedIps
12:     ticks ← 0
13:     if ips > 0 then
14:         for each t' ∈ vm.assignedTasks do         ⟵ pre-existing queue
15:             ticks ← ticks + ceil(t'.remainingInstructions / ips)
16:         end for
17:     end if
18:     completionTicks[vm] ← ticks
19:     if ticks > globalMakespan then globalMakespan ← ticks
20: end for
21: contextInitialized ← true


Function   pickBestVm(t, candidates) → VM
 1: best    ← ⊥
 2: bestΔE  ← +∞
 3: for each vm ∈ candidates do
 4:     if vm.userId ≠ t.userId        then continue      ⟵ ownership filter
 5:     if not vm.canAcceptTask(t)     then continue      ⟵ compatibility filter
 6:     ips ← vm.totalRequestedIps
 7:     if ips ≤ 0                     then continue
 8:     execTicks ← ceil(t.instructionLength / ips)
 9:     (cpuUtil, gpuUtil) ← utilizationProfile(t.workloadType)
10:     incrPower ← powerModel.calculateIncrementalPowerWithSpeedScaling(
11:                     t.workloadType, cpuUtil, gpuUtil, ips)
12:     execEnergyJ ← incrPower · execTicks                 ⟵ term 1 (watts · seconds = joules)
13:     currTicks ← completionTicks[vm]  (default 0)
14:     newFinish ← currTicks + execTicks
15:     extension ← max(0, newFinish − globalMakespan)
16:     idleEnergyJ ← extension · K_on · idlePower         ⟵ term 2
17:     ΔE ← execEnergyJ + idleEnergyJ
18:     if ΔE < bestΔE then                                ⟵ strict <, earliest wins ties
19:         bestΔE  ← ΔE
20:         best    ← vm
21:     end if
22: end for
23: return best
```

`utilizationProfile(wl)` returns the workload's empirical
`(cpu, gpu)` utilisation pair from
`MeasurementBasedPowerModel.getWorkloadProfile(wl)` if one exists, and
otherwise falls back to the switch statement at
`EnergyAwareTaskAssignmentStrategy.java:178-191` — e.g. `SEVEN_ZIP →
(1.0, 0.0)`, `FURMARK → (0.08, 1.0)`, `LLM_GPU → (0.12, 0.12)`,
`IDLE → (0.0, 0.0)`. These numbers match the defaults used by the
energy objective itself (see `EnergyObjective.getUtilizationProfile`,
lines 299–332), so the heuristic and the optimiser are scoring the
same workloads with the same utilisation assumptions.

Source:
`src/main/java/com/cloudsimulator/PlacementStrategy/task/EnergyAwareTaskAssignmentStrategy.java`
(lines 41–204; `assignAll` at lines 52–76, `selectVM` at lines
78–102, `initializeContext` at lines 104–132, `pickBestVm` at lines
134–168).

---

## 4b.3 Why these two specifically seed the metaheuristics

The two heuristics cover the **two extreme ends** of the
$(\mathrm{makespan}, \mathrm{energy})$ Pareto front:

- `WorkloadAware` is a makespan-leaning greedy strategy — it packs
  work onto fast VMs and thereby minimises completion time without
  explicitly looking at energy;
- `EnergyAware` is an energy-leaning greedy strategy — it prefers
  slower, more power-efficient VMs and only diverts to fast VMs when
  doing so avoids extending the global makespan.

Handing these two chromosomes to the initial population of every
metaheuristic gives the optimiser a good "corner" solution on each
side of the trade-off for free, which accelerates convergence and
(more importantly for NSGA-II / SPEA-II / AMOSA) ensures the initial
Pareto archive is not entirely dominated by random individuals.
