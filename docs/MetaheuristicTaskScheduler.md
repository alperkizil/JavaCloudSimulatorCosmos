# Metaheuristic Task Scheduler: Seed Heuristics, Algorithms, and Dominance Archives

This document describes how tasks are matched to VMs by the **seven metaheuristic
arms** of the research campaigns in `newExperiments/`
(`MakespanEnergyExperiment`, `WaitingTimeEnergyExperiment`,
`PowerCeilingExperiment`): the **heuristic starting points** that warm-start
every arm, the **solution representation** they all share, **each algorithm**
with its pseudo-code and the exact **parameters the runners use**, and how the
GA/SA arms build their **dominance archives**.

Everything below is derived directly from the source code (class and method
names are cited throughout). Infrastructure — how datacenters, hosts, VMs and
tasks are created and placed — is covered in `docs/infrastructure.md`; this
document picks up at Step 5 of the pipeline (`TaskAssignmentStep`).

---

## 1. The Optimization Problem

Scheduling runs **offline**: all 500 tasks exist at $t = 0$ and every VM is
already placed on a host before the optimizer starts (see
`infrastructure.md` §2.2, §6). The optimizer decides two things:

| Decision | Meaning |
|---|---|
| **Assignment** | which VM each task runs on |
| **Order** | the dispatch order of the tasks queued on each VM |

A task may only go to a VM that is **owned by the same user** and
**compute-type compatible** (`VM.canAcceptTask`). The valid VM set per task is
precomputed once per run by `RepairOperator` (§3.3). Oversubscription cannot
occur at this level — it is excluded by VM placement, by construction.

With 500 tasks and 60 VMs the assignment space alone has $60^{500} \approx
10^{889}$ points (before ordering), which is why the campaigns use
metaheuristics rather than exact methods.

### 1.1 Objectives

Each study optimizes a pair (the second objective is always Energy):

| Study | Primary objective | Second objective |
|---|---|---|
| `MakespanEnergyExperiment` | Makespan (s) | Energy (kWh) |
| `WaitingTimeEnergyExperiment` | Avg. waiting time (s) | Energy (kWh) |
| `PowerCeilingExperiment` | Avg. waiting time (s) | Energy (kWh), plus a power cap (§8) |

A candidate is **never simulated during the search**. The objectives
(`metaheuristic/objectives/`) evaluate it analytically through
**`LaneSchedule`** — an exact mirror of the simulator's per-vCPU FIFO
scheduler: each VM has `vcpuCount` lanes running at the host-clamped speed
$v_j$ (effective IPS per vCPU), tasks dispatch in queue order onto free lanes,
and a GPU task additionally needs a free GPU slot (concurrency capped by the
VM's bound GPU count). A task of length $L$ occupies its lane for

$$
\text{ticks}(L, v_j) \;=\; \left\lceil L / v_j \right\rceil
$$

With $\text{start}_i$ = the start tick LaneSchedule computes for task $i$ on
its VM $j(i)$, the three objective functions are:

$$
\text{Makespan} \;=\; \max_{i} \left( \text{start}_i + \frac{L_i}{v_{j(i)}} \right)
\qquad \text{(fractional finish of the last task, seconds)}
$$

$$
\text{WaitingTime} \;=\; \frac{1}{n} \sum_{i=1}^{n} \text{start}_i
\qquad \text{(mean queue wait, seconds)}
$$

$$
\text{Energy} \;=\; \frac{1}{3.6 \times 10^6}
\Bigg(
\underbrace{P_{\text{idle}} \sum_{h} W_h}_{\text{host idle windows}}
\;+\;
\underbrace{\sum_{i} P_{\text{inc}}(w_i) \left( \frac{v_{j(i)}}{v_{\text{ref}}} \right)^{1.5} \Big\lceil \tfrac{L_i}{v_{j(i)}} \Big\rceil}_{\text{task execution}}
\Bigg)
\quad \text{kWh}
$$

where $P_{\text{idle}} = 75.79$ W, $W_h$ is host $h$'s powered-on window
(from $t=0$ to the completion of its last VM — idle-host power gating),
$P_{\text{inc}}(w)$ is the measured incremental power of workload $w$, and
$v_{\text{ref}}$ is the host-fleet median core speed (2.8 G under the default
fleet; wired via `EnergyObjective.setHosts`). The $1.5$ exponent is the
speed–power law — the source of the fast-vs-efficient trade-off
(`infrastructure.md` §7.2 unpacks every term).

The makespan is *fractional* deliberately: tick-rounded makespans collapse to
a handful of whole-second plateaus and would flatten the Pareto front
(`MakespanObjective` javadoc).

### 1.2 Search budget and campaign shape

| Item | Value | Where |
|---|---|---|
| Objective evaluations per run | **40,000** (AMOSA: + 10,200 archive-init grant, §6.5) | `AlgorithmParameters.iterationCount` |
| Runs per study | 3 scenarios × 7 arms × 10 seeds | `CampaignRunner.run` |
| Seeds | 200 … 209 (`baseSeed = 200`, `numRuns = 10`) | `ExperimentConfig` |

---

## 2. Solution Representation (shared by all seven arms)

All seven arms optimize **the same chromosome**: `SchedulingSolution`
(`metaheuristic/SchedulingSolution.java`). It has exactly the two parts the
problem asks for:

| Field | Type | Meaning |
|---|---|---|
| `taskAssignment` | `int[n]` | `taskAssignment[i]` = index of the VM running task $i$ |
| `vmTaskOrder` | list per VM | `vmTaskOrder[j]` = task indices on VM $j$, in dispatch order |
| `objectiveValues` | `double[k]` | cached raw objective vector after evaluation |

**Example** — 6 tasks on 3 VMs:

```
taskAssignment = [0, 2, 0, 1, 2, 0]      # task 0 -> VM 0, task 1 -> VM 2, ...

vmTaskOrder[0] = [2, 0, 5]               # VM 0 runs task 2 first, then 0, then 5
vmTaskOrder[1] = [3]
vmTaskOrder[2] = [1, 4]
```

Only the **relative order within one VM** is meaningful — LaneSchedule
dispatches each VM's list independently. Swapping tasks 2 and 0 on VM 0
changes waiting times (and possibly makespan/energy); moving task 5 to VM 1
changes both VMs' loads.

**The MOEA arms carry the same information in a different genotype.**
NSGA-II, SPEA-II and AMOSA run inside the MOEA Framework, whose `Solution`
holds (`TaskSchedulingProblem`, encoding is the class's single source of
truth):

| Variable | Type | Meaning |
|---|---|---|
| 0 … n−1 | `RealVariable` in $[0, m-1]$ | task $i$'s VM index (rounded on decode) |
| n | `Permutation` over all n tasks | global **dispatch priority** |

A VM's order list is the permutation **filtered down** to the tasks assigned
to it. The example above corresponds to:

```
assignment genes  = [0, 2, 0, 1, 2, 0]
dispatch priority = [2, 3, 0, 1, 5, 4]
    -> VM 0: [2, 0, 5]   VM 1: [3]   VM 2: [1, 4]     (same solution)
```

Every MOEA evaluation decodes to a `SchedulingSolution`, repairs it, scores
it, and writes the repaired genome back (`TaskSchedulingProblem.evaluate`), so
all seven arms genuinely search the same space.

### 2.1 Validity and repair

A chromosome is valid when every task sits on a VM from its precomputed valid
set (same user + compute type). `RepairOperator.repair` fixes each invalid
gene by drawing a random valid VM, then rebuilds the order lists. Mutation and
crossover always run repair on their outputs, so the search never evaluates an
invalid candidate.

---

## 3. Heuristic Starting-Point Generators

Instead of starting from purely random candidates, every arm is **warm-started**
with one or two greedy schedules ("seeds"). Three greedy heuristics from
`PlacementStrategy/task/` act as seed generators; all three are **RNG-free**,
so seeding never perturbs reproducibility.

### 3.1 Who seeds whom

`AlgorithmRegistry` computes the seeds from the *placed* context and matches
them to each arm's flavour (`create`, lines per arm):

| Arm | Seed(s) injected |
|---|---|
| `GA_<Primary>_Dominance` | primary heuristic |
| `GA_Energy_Dominance` | EnergyAware |
| `SA_<Primary>_Dominance` | primary heuristic |
| `SA_Energy_Dominance` | EnergyAware |
| `NSGA-II`, `SPEA-II`, `AMOSA` | primary heuristic **and** EnergyAware |

The **primary heuristic is study-matched** (`newPrimarySeedHeuristic`):

| Study | Primary seed heuristic | Why (measured seed-quality probe, PR #219) |
|---|---|---|
| Makespan | **LPT** | LPT strictly dominates WorkloadAware on both makespan and energy in all three scenarios |
| WaitingTime, PowerCeiling | **WorkloadAware** | its arrival-order dispatch gives ≈2× lower average wait than LPT's giants-first packing |

### 3.2 From greedy schedule to chromosome

`AlgorithmRegistry.computeHeuristicSeed` runs the heuristic once on the placed
context and converts the resulting task→VM plan into an assignment array:

```
unassigned <- all tasks not yet assigned
running    <- all VMs placed on a host
plan       <- heuristic.assignAll(unassigned, running, now)   # greedy schedule

for i in 0 .. n-1:
    if plan assigned task i to some VM v:  seed[i] <- index of v in running
    else:                                  seed[i] <- i mod |running|   # round-robin fallback

undo all assignments (context.resetForRescheduling)
return seed                                 # an int[n] chromosome, §2
```

The seed carries **assignment only**; its order lists start canonical
(ascending task index per VM) when the algorithm injects it. Each algorithm
repairs the seed before use.

### 3.3 WorkloadAware — "shortest projected completion"

`WorkloadAwareTaskAssignmentStrategy` walks the tasks in **arrival (list)
order**. For each task it pretends to append the task to each candidate VM's
queue, replays that queue through LaneSchedule, and keeps the VM that would
finish soonest:

```
for each task in arrival order:
    for each candidate VM v (same user, compatible):
        finish(v) <- completion tick of v's queue + this task    # LaneSchedule replay,
                                                                 # GPU cap included
    assign task to the v with the smallest finish(v)             # tie: first v in list order
```

In simple terms: *"put each task where it will finish earliest, given
everything already queued."* Because it dispatches in arrival order and always
looks for a free-soonest VM, it produces **balanced schedules with short
queues — low average waiting time**.

### 3.4 LPT — "longest tasks first"

`LPTTaskAssignmentStrategy` is the classical Longest-Processing-Time-first
list-scheduling heuristic for makespan. It reuses WorkloadAware's VM choice
but controls the **processing order** (it must override `assignAll` — the
interface default dispatches in caller order, which can never realize LPT):

```
sort tasks by instruction length, longest first     # tie: lower task id first
for each task in that order:
    assign it to the VM with the smallest projected finish   # same rule as §3.3
```

In simple terms: *"place the giants first while every machine is still empty;
the small tasks then fill the gaps."* This packs VM loads evenly and produces
**short-makespan schedules**, at the cost of longer queues in front of small
tasks (they queue behind giants) — the classical LPT vs. mean-flow-time
trade-off.

### 3.5 Worked example: WorkloadAware vs. LPT

Two single-lane VMs of the same user: **A** at $v_A = 2$ G/s, **B** at
$v_B = 1$ G/s. Four CPU tasks in arrival order T1…T4 with lengths
$2, 2, 2, 6$ G. Tick costs: on A $\lceil L/2 \rceil$, on B $\lceil L/1 \rceil$.

**WorkloadAware** (arrival order T1, T2, T3, T4):

| Step | Projected finish on A | on B | Choice |
|---|---:|---:|---|
| T1 (2 G) | 1 | 2 | **A** |
| T2 (2 G) | 2 | 2 | **A** (tie → first) |
| T3 (2 G) | 3 | 2 | **B** |
| T4 (6 G) | 5 | 8 | **A** |

**LPT** (sorted order T4, T1, T2, T3):

| Step | Projected finish on A | on B | Choice |
|---|---:|---:|---|
| T4 (6 G) | 3 | 6 | **A** |
| T1 (2 G) | 4 | 2 | **B** |
| T2 (2 G) | 4 | 4 | **A** (tie → first) |
| T3 (2 G) | 5 | 4 | **B** |

The two candidates they generate (VM indices A = 0, B = 1):

| Heuristic | Seed chromosome | Schedule | Makespan | Mean wait |
|---|---|---|---:|---:|
| WorkloadAware | `[0, 0, 1, 0]` | A: T1,T2,T4 · B: T3 | 5 ticks | $(0{+}1{+}0{+}2)/4 = 0.75$ |
| LPT | `[1, 0, 1, 0]` | A: T4,T2 · B: T1,T3 | **4 ticks** | $(0{+}3{+}0{+}2)/4 = 1.25$ |

Exactly the measured pattern of §3.1: LPT wins makespan, WorkloadAware wins
waiting time — which is why each study seeds with its own matched heuristic.

### 3.6 EnergyAware — "cheapest marginal energy"

`EnergyAwareTaskAssignmentStrategy` walks the tasks in arrival order and gives
each one to the VM where it adds the **least energy**. Its cost model mirrors
the two terms of the Energy objective (§1.1):

$$
\Delta E(v) \;=\;
\underbrace{P_{\text{inc}}(w) \left( \frac{v_{\text{eff}}}{v_{\text{ref}}} \right)^{1.5} \Big\lceil \tfrac{L}{v_{\text{eff}}} \Big\rceil}_{\text{execution energy (J)}}
\;+\;
\underbrace{\max\!\big(0,\; \text{newFinish}(v) - \text{makespan}\big) \cdot H \cdot P_{\text{idle}}}_{\text{idle-power cost of extending the fleet's busy window}}
$$

where $H$ is the number of active hosts. It tracks per-VM lane loads (and GPU
slots) as it commits tasks, so later decisions see earlier ones:

```
for each task in arrival order:
    for each candidate VM v:
        execTicks  <- ceil(L / v.effIps)                     # one lane
        execEnergy <- P_inc(workload) × (v.effIps/v_ref)^1.5 × execTicks
        newFinish  <- finish of v's least-loaded lane + execTicks   # GPU slot too, if GPU task
        idleCost   <- max(0, newFinish - currentMakespan) × activeHosts × P_idle
        ΔE(v)      <- execEnergy + idleCost
    assign task to the v with the smallest ΔE; commit it to v's least-loaded lane
```

The hosts-aware constructor (used by the registry) calibrates
$v_{\text{ref}}$ from the **host-fleet median** core speed — the same basis as
the real `EnergyObjective` — so the seed optimizes the objective it
warm-starts.

**Worked example.** A 6 G `SEVEN_ZIP` task ($P_{\text{inc}} = 130.29$ W,
$v_{\text{ref}} = 2.8$ G). Two options: a fast 3.0 G lane free at $t=10$, a
slow 0.5 G lane free at $t=0$. Fleet makespan is 40 ticks, 5 hosts active,
$P_{\text{idle}} = 75.79$ W. Speed factors: $(3.0/2.8)^{1.5} = 1.109$,
$(0.5/2.8)^{1.5} = 0.075$.

| Option | Exec ticks | Exec energy | Finish | Idle extension | $\Delta E$ |
|---|---:|---:|---:|---:|---:|
| fast lane | 2 | $130.29 \times 1.109 \times 2 = 289$ J | 12 | 0 | **289 J** |
| slow lane | 12 | $130.29 \times 0.075 \times 12 = 118$ J | 12 | 0 | **118 J** → chosen |

The slow lane runs 6× longer but still costs ~2.4× less energy — the
speed–power law at work. If instead the slow lane were only free at $t=45$
(finish 57, extending the 40-tick makespan by 17 ticks), the idle term would
add $17 \times 5 \times 75.79 = 6{,}442$ J and the fast lane would win. In
simple terms: *"run on the slowest lane that doesn't keep the whole fleet
powered on longer."*

### 3.7 What kind of candidate each seed is

| Seed | Character of the schedule it generates | Starts the search near |
|---|---|---|
| LPT | tightly packed, balanced loads | the **low-makespan** end of the front |
| WorkloadAware | arrival-order, short queues | the **low-waiting-time** end |
| EnergyAware | consolidated on slow/efficient lanes | the **low-energy** end |

The GA/SA arms get the single seed matching their own flavour; the MOEA arms
get both ends of the trade-off. The greedy heuristics are deliberately **not**
scored as arms (they seed the metaheuristics; scoring them would double-count
— the mains' comment), though `"LPT"`, `"WorkloadAware"`, `"EnergyAware"` and
other labels remain available as standalone baselines via
`AlgorithmRegistry.create`.

---

## 4. The Seven Arms and Their Shared Machinery

Per study, the arm set is (`AlgorithmRegistry.defaultLabels`; `<P>` =
`Makespan` or `WaitingTime`):

| Arm label | Algorithm | Search type | Section |
|---|---|---|---|
| `GA_<P>_Dominance` | Generational GA + archive | single-objective (primary-flavoured) | §5.1 |
| `GA_Energy_Dominance` | Generational GA + archive | single-objective (energy-flavoured) | §5.1 |
| `SA_<P>_Dominance` | Simulated Annealing + archive | single-solution (primary-flavoured) | §5.2 |
| `SA_Energy_Dominance` | Simulated Annealing + archive | single-solution (energy-flavoured) | §5.2 |
| `NSGA-II` | MOEA Framework `NSGAII` | population, true multi-objective | §5.3 |
| `SPEA-II` | MOEA Framework `SPEA2` | population, true multi-objective | §5.4 |
| `AMOSA` | `FixedAMOSA` (MOEA Framework) | single-solution + archive, multi-objective | §5.5 |

All seven share:

**Move operators** (`metaheuristic/operators/`) — the same domain operators
everywhere (the MOEA arms adapt them via `TaskSchedulingVariation` /
`TaskSchedulingMutation` instead of MOEA's generic real-coded SBX+PM, so the
comparison is not confounded by operator family):

| Operator | What it does |
|---|---|
| `REASSIGN` mutation | move one task to a different valid VM (surgical: removed from the old VM's order, appended to the new one's — other order lists untouched) |
| `SWAP_ORDER` mutation | swap the positions of two tasks on the same VM |
| `COMBINED` (used) | 50/50 coin flip between the two, per mutated task |
| `UNIFORM` crossover (used) | each assignment gene from a random parent; each offspring's per-VM order is inherited from its donor parent's dispatch precedence (so order genes stay heritable — PR #222) |
| Repair | reassign invalid genes to a random valid VM (§2.1) |

**One publication rule** (since PR #218): *every evaluated candidate* is
offered to a non-dominated archive with a 1 % epsilon near-duplicate filter
(§6 for GA/SA, §6.6 for the MOEA arms). The published Pareto front of a run
is that archive — never just the algorithm's final search state.

**Deployment + reporting**: after the search, the arm's `assignAll` applies
the front's **knee point** (2-D: the member with maximum perpendicular
distance from the line joining the two single-objective extremes,
`ParetoFront.getKneePoint`) and the run is simulated through Steps 6–8. Then
`CampaignRunner.simulateAllParetoSolutions` **re-simulates every front
member**, so all published numbers are simulated values, not analytic
predictions.

---

## 5. The Algorithms

### 5.1 Generational GA with Dominance Archive

**In simple English:** keep a population of 200 schedules. Each generation,
copy the 40 best unchanged (elitism), then breed the remaining 160 by picking
parents in 5-way tournaments, mixing their genes (crossover), making a couple
of random moves (mutation), and fixing anything invalid (repair). The search
itself chases a *single* number (the weighted fitness below); a passive
archive (§6) collects the multi-objective trade-offs it wanders past.

The scalar fitness both GA arms minimize is a **normalized weighted sum**
(`GenerationalGAAlgorithm.evaluateFitness`, `ObjectiveScaleNormalizer`):

$$
F(x) \;=\; 1.0 \cdot \frac{f_{\text{flav}}(x)}{f_{\text{flav}}(x_0)}
\;+\; 0.001 \cdot \frac{f_{\text{other}}(x)}{f_{\text{other}}(x_0)}
$$

where $f_{\text{flav}}$ is the arm's flavour objective (the primary for
`GA_<P>_…`, Energy for `GA_Energy_…`), $f_{\text{other}}$ the other one, and
$x_0$ the first evaluated solution of the run (the injected seed —
deterministic). Normalizing by $x_0$ puts both objectives at $O(1)$, so the
0.001 weight is a genuine tiebreaker regardless of units (seconds vs. kWh).

```
population <- injected seed + random assignments (200 total); repair each
evaluate all 200; offer each to the archive
while evaluations < 40,000:
    elite <- 40 best by fitness (copied unchanged; fitness cached, not re-evaluated)
    offspring <- []
    while |offspring| < 160:
        p1, p2 <- winners of two 5-way tournaments
        c1, c2 <- crossover(p1, p2) with prob 0.95, else copies of parents
        mutate each task gene of c1, c2 with prob 0.004; repair
        offspring += c1, c2
    population <- elite + offspring
    evaluate the 160 offspring; offer each to the archive
return the best-fitness solution; published front = archive (§6)
```

Parameters used by the runners (`AlgorithmParameters` →
`AlgorithmRegistry.createGADominanceStrategy`):

| Parameter | Value | Note |
|---|---|---|
| Population size | 200 | |
| Crossover | UNIFORM, rate 0.95 | order inherited from donor parent |
| Mutation rate | **0.004** per task gene | ≈ 2 moves per offspring at 500 tasks ($c/N$, $c=2$); retuned from 0.05 in PR #222 |
| Elitism | 20 % → 40 individuals | carried unchanged, fitness cached |
| Tournament size | 5 | |
| Objective weights | 1.0 flavour + 0.001 tiebreaker | normalized scales (see $F(x)$) |
| Archive epsilon | 1 % of per-objective range | §6.3 |
| Termination | 40,000 evaluations | 200 init + 160/generation → stops after generation 249 (40,040 evals — the "≤1 generation" overshoot) |
| Seed | 1 (flavour-matched, §3.1) | injected first, so elitism preserves it |

### 5.2 Simulated Annealing with Dominance Archive

**In simple English:** hold *one* schedule. Repeatedly nudge it (1–4 random
moves), always accept improvements, and sometimes accept a worse neighbor —
often at high "temperature", rarely once cooled — so the search can escape
local optima. If it stagnates, reheat and restart from the best found. The
same passive archive (§6) collects the trade-off points; both SA arms minimize
the same normalized weighted fitness $F(x)$ as §5.1 (same formula and code
path, `SimulatedAnnealingAlgorithm.evaluateFitness`).

A worse neighbor ($\Delta E = F(s') - F(s) > 0$) is accepted with the
classical Metropolis probability

$$
p_{\text{accept}} \;=\; e^{-\Delta E / T}
$$

and the initial temperature is **auto-calibrated** so that a target 80 % of
worse moves are accepted at the start
(`SimulatedAnnealingAlgorithm.calculateInitialTemperature`):

$$
T_0 \;=\; \frac{-\,\overline{\Delta E^{+}}}{\ln(0.8)}
\qquad \text{with } \overline{\Delta E^{+}} \text{ = mean worsening over 100 sampled neighbors}
$$

```
s <- heuristic seed (repaired); evaluate; offer to archive
T <- T0 from the 100-neighbor probe (the probe's ~101 evaluations count toward the budget)
best <- s
while evaluations < 40,000:                      # checked at temperature-step boundaries
    n <- iterations for this step: 50..400, adaptive on last step's acceptance rate
    repeat n times:
        s' <- copy of s with k random moves, k = 1 + floor(3 T / T0)  in [1, 4]; repair
        evaluate s'; offer to archive
        ΔE <- F(s') - F(s)
        if ΔE <= 0:                        s <- s'          # better: always accept
        else with probability e^(-ΔE/T):   s <- s'          # worse: sometimes accept
        if F(s) < F(best): best <- s
    if best unchanged for 15 steps and reheats < 3:
        T <- min(5 × T, T0); s <- best                      # reheat, restart from best
    T <- T × rate(acceptance)                               # adaptive cooling, table below
return best; published front = archive (§6)
```

Parameters used by the runners (`AlgorithmParameters` →
`AlgorithmRegistry.buildSAConfig`):

| Parameter | Value | Note |
|---|---|---|
| Initial temperature | auto: target 80 % initial acceptance, 100 samples | ≈ 101 probe evaluations, inside the 40 k budget |
| Iterations per temperature | 200 base; adaptive 50–400 | max near 40 % acceptance, min outside the (0.1, 0.7) band |
| Neighbor | 1–4 COMBINED moves, scaled by $T/T_0$ | big jumps early, surgical moves late |
| Cooling (adaptive) | see table below | `AdaptiveCoolingSchedule` |
| Reheat | ×5 (capped at $T_0$) after 15 stagnant steps, max 3 | restarts from the best solution |
| Objective weights | 1.0 flavour + 0.001 tiebreaker, normalized | identical to GA |
| Archive epsilon | 1 % | §6.3 |
| Termination | 40,000 evaluations | stops at the first temperature-step boundary ≥ 40 k |
| Seed | 1 (flavour-matched) | used as the initial solution |

The adaptive cooling schedule the campaign constructs is
`AdaptiveCoolingSchedule(0.5, 0.15, 0.90, 0.97, 0.995)` — **target acceptance
50 % ± 15 %**, with the multiplier chosen per temperature step:

| Acceptance rate last step | Cooling multiplier | Reading |
|---|---|---|
| > 0.65 | × 0.90 | accepting too much → cool fast |
| 0.35 – 0.65 | × 0.97 | on target → normal cooling |
| < 0.35 | × 0.995 | freezing → cool very slowly |

> Note: the `AlgorithmParameters` field names (`saCoolingBaseRate`,
> `saCoolingMinRate`, `saCoolingMaxRate`, `saCoolingLowAccept`,
> `saCoolingHighAccept` = 0.5, 0.15, 0.90, 0.97, 0.995) are positional legacy
> names; their actual constructor meaning is the one above
> (target, tolerance, fast, normal, slow).

### 5.3 NSGA-II

**In simple English:** a genetic algorithm that never squashes the two
objectives into one number. Parents and offspring compete together; survivors
are chosen first by *Pareto rank* (is anyone strictly better on both
objectives?), then by *crowding distance* (prefer solutions in sparse regions
of the front, to keep it spread out). Elitist by construction: the merged
parent+offspring pool means a front member is only lost to a better front.

The campaign runs MOEA Framework's `NSGAII` wired to the project's problem
and operators (`MOEA_NSGA2TaskSchedulingStrategy.runDirect`):

```
P <- 2 heuristic seeds + 198 random (InjectedInitialization); evaluate
repeat for 200 generations:
    mating: binary tournament by (rank, then crowding distance)
    Q <- 200 offspring: crossover 0.95, per-gene mutation 0.004, repair
    evaluate Q                     # every evaluation also feeds the publication archive
    R <- P ∪ Q                     # 400 candidates
    non-dominated sort R into fronts F1, F2, ...
    P <- fill 200 slots front by front; cut the last front by crowding distance
published front = publication archive; deployed schedule = knee point
```

| Parameter | Value | Note |
|---|---|---|
| Population | 200 | |
| Generations | 200 (= 40,000 / 200) | `GenerationCountTermination`, mapped to a 40 k evaluation cap |
| Variation | UNIFORM crossover 0.95, mutation 0.004, repair | `TaskSchedulingVariation` — same operators as GA |
| Selection | binary tournament without replacement (rank, crowding) | MOEA default (Deb's original) |
| Seeds | LPT/WA **and** EnergyAware | both ends of the front, §3.7 |
| Publication archive epsilon | 1 % | offered every evaluated solution inside `evaluate()` |
| RNG | `PRNG.setSeed(run seed)` | §9 |

### 5.4 SPEA-II

**In simple English:** like NSGA-II, but "how good is a solution?" is computed
differently. Each solution gets a *strength* (how many others it dominates);
a solution's *raw fitness* is the summed strength of everyone dominating it
(0 = non-dominated); a *density* term (distance to the k-th nearest neighbor,
k = 1 here) breaks ties so the archive stays spread out. An external archive
of fixed size carries the best set between generations, truncated by
nearest-neighbor distance when it overflows.

For each solution $x$, with $S(y)$ = number of solutions $y$ dominates and
$\sigma^k_x$ = distance to its $k$-th nearest neighbor:

$$
\text{Fitness}(x) \;=\; \underbrace{\sum_{y \,\succ\, x} S(y)}_{\text{raw fitness (0 = non-dominated)}}
\;+\; \underbrace{\frac{1}{\sigma^k_x + 2}}_{\text{density}} ,
\qquad \text{lower is better}
$$

```
P <- 2 heuristic seeds + 198 random; evaluate
A <- environmental selection of P (archive of 200)
repeat until 40,000 evaluations:
    mating: binary tournament on SPEA-II fitness over A
    Q <- 200 offspring (crossover 0.95, mutation 0.004, repair); evaluate
    A <- non-dominated of A ∪ Q; if > 200, truncate by nearest-neighbor distance;
         if < 200, fill with best dominated ones
published front = publication archive; deployed schedule = knee point
```

Runner parameters are identical to NSGA-II's table (§5.3); the SPEA-II
specifics are `numberOfOffspring = 200` and `k = 1`
(`MOEA_SPEA2TaskSchedulingStrategy`: `new SPEA2(problem, 200, init,
variation, 200, 1)` — MOEA's defaults).

### 5.5 AMOSA

**In simple English:** simulated annealing for multiple objectives. One
current schedule wanders; an **archive of non-dominated schedules** is both
the memory and the output. Whether a mutated neighbor is accepted depends on
its domination relationship to the current point *and* the archive, weighted
by an "amount of domination" (how *much* better/worse, not just yes/no). The
archive is kept small by clustering: above 100 members it is truncated to the
50 cluster centers.

The *amount of domination* between $a$ and $b$ is the geometric mean of the
normalized objective gaps (`FixedAMOSA.calculateDeltaDominance`; $r_i$ =
objective $i$'s range over archive ∪ {new point}, zero-range dimensions
skipped):

$$
\Delta \text{dom}(a,b) \;=\;
\Bigg( \prod_{i \,:\, r_i > 0} \frac{\lvert f_i(a) - f_i(b) \rvert}{r_i} \Bigg)^{1/k}
$$

Acceptance of a mutated neighbor $s'$ against current point $s$
(`FixedAMOSA.iterate`, the paper's case analysis):

| Case | Decision |
|---|---|
| $s'$ dominated by $s$ and/or archive points | accept with $p = \big(1 + e^{\Delta\text{dom}_{avg} \cdot T}\big)^{-1}$ |
| $s'$ mutually non-dominated with everything | accept; add $s'$ to archive (truncate if > soft limit) |
| $s'$ dominates archive point(s) | accept; add $s'$ (dominated members evicted) |
| $s'$ dominates $s$ but archive dominates $s'$ | with $p = \big(1 + e^{-\Delta\text{dom}_{min}}\big)^{-1}$ jump to the closest dominating archive member, else accept $s'$ |

$T$ falls geometrically from 15.0 (× 0.95 per step, ≈ 200 temperature levels
within budget), so dominated moves are almost never accepted early and
approach 50/50 as $T \to 0$.

```
build 200 initial candidates (2 heuristic seeds + 198 random)     # gamma × softLimit
evaluate each, hill-climb each for 50 iterations                  # = 10,200 evaluations
archive <- non-dominated survivors; s <- an archive member; T <- 15.0
while evaluations < 40,000 + 10,200:
    repeat 200 times:                                             # iterations per temperature
        s' <- s with each task gene mutated at prob 0.05 (at least 1 move); repair; evaluate
        accept / archive per the case table above
        if archive > 100 members: cluster and truncate to 50      # soft/hard limit
    T <- 0.95 × T
published front = publication archive (every evaluated candidate, §6.6)
```

Parameters used by the runners (`AlgorithmParameters` →
`AlgorithmRegistry.createAMOSAStrategy`):

| Parameter | Value | Note |
|---|---|---|
| Initial temperature $T_0$ | 15.0 | geometric cooling $\alpha = 0.95$ |
| Iterations per temperature | 200 | |
| Soft / hard archive limit | 100 / 50 | single-linkage cluster truncation |
| $\gamma$ (init scaling) | 2.0 | initial candidates = $\gamma \times$ soft limit = 200 |
| Hill-climbing iterations | 50 per initial candidate | archive construction |
| Mutation rate | **0.05** per task gene (≈ 25 moves per neighbor; min 1) | AMOSA's own rate — deliberately not retuned in PR #222 |
| Seeds | LPT/WA + EnergyAware | injected among the 200 initial candidates |
| Termination | 40,000 evaluations **+ 10,200 archive-init grant** | the grant is intrinsic to `AMOSA.initialize()`; granted on top so the annealing search gets the full 40 k (disclose in the paper — HANDOFF §3.2) |

`FixedAMOSA` exists because MOEA Framework's `AMOSA.calculateDeltaDominance`
initializes its product with 0.0 (always returning 0, flattening every
acceptance probability to 0.5) and divides by zero on zero-range objectives;
the subclass fixes both and uses the geometric mean above.

---

## 6. How GA and SA Build Their Dominance Archive

The GA/SA arms search with a *single* scalar fitness — so on their own they
would return one point, not a front. The dominance-archive variants
(`GenerationalGAwithDominanceStrategy`, `SimulatedAnnealingWithDominanceStrategy`)
bolt a **passive** `NonDominatedArchive` onto the search: every evaluated
candidate is *offered* to it, and whatever survives is published as the arm's
Pareto front. Passive means `offer()` stores a private copy and never
influences selection, acceptance, or temperature — the search trajectory is
identical with or without the archive.

### 6.1 Who gets offered

| Arm | Offered to the archive |
|---|---|
| GA | every individual when first evaluated: the 200 initial (seed + random), then all 160 offspring per generation. Carried elites are *not* re-offered (already archived at first evaluation) |
| SA | the initial (seed) solution, the ~101 auto-temperature probe solutions, and every neighbor ever evaluated. Extra seeds beyond the first (none under current wiring) would be evaluated and offered directly |

### 6.2 Admission rules

The archive compares **raw objective vectors** $(f_1, f_2)$ — makespan or
waiting time in seconds, energy in kWh — never the weighted fitness. For
minimization, $a$ *dominates* $b$ iff

$$
\forall i:\; f_i(a) \le f_i(b) \quad \text{and} \quad \exists i:\; f_i(a) < f_i(b)
$$

```
offer(candidate c):
    for each member m of the archive:
        if m dominates c:            reject c
        if c dominates m:            evict m
        if same objective vector:    reject c          # exact duplicate
    if c lies within the epsilon box of any remaining member:  reject c   # §6.3
    add a copy of c
```

So the archive is always a mutually non-dominated set, and it can shrink: one
strong candidate may evict several members at once.

### 6.3 The 1 % epsilon filter

`archiveEpsilonFraction = 0.01` (`AlgorithmParameters`). A candidate that is
mutually non-dominated with every member but lies within

$$
\varepsilon_i \;=\; 0.01 \times \big( \max_i - \min_i \big)
\qquad \text{(per-objective range over the members plus the candidate)}
$$

of some member **on every objective** is rejected as a near-duplicate.
Annealing otherwise hoards thousands of trajectory points that differ by
fractions of a percent. The filter is admission-time only (members admitted
earlier are not re-pruned as ranges widen) and passive — it never changes the
search, only what gets published.

### 6.4 Worked example

Two objectives, both minimized: (makespan s, energy kWh). Archive starts empty.

| # | Offer | Against the archive | Result | Archive after |
|---|---|---|---|---|
| 1 | (20.0, 1.00) | empty | **added** | {(20.0, 1.00)} |
| 2 | (22.0, 0.90) | trades: worse makespan, better energy | **added** | {(20.0, 1.00), (22.0, 0.90)} |
| 3 | (21.0, 1.05) | dominated by (20.0, 1.00) — worse on both | **rejected** | unchanged |
| 4 | (19.0, 0.95) | dominates (20.0, 1.00); non-dominated vs. (22.0, 0.90) | **added, evicts** (20.0, 1.00) | {(19.0, 0.95), (22.0, 0.90)} |
| 5 | (19.02, 0.9498) | non-dominated with both, but ranges are 3.0 s / 0.05 kWh → $\varepsilon = (0.03, 0.0005)$; distance to (19.0, 0.95) is (0.02, 0.0002) ≤ ε on both | **rejected** (near-duplicate) | unchanged |

Offer #5 is the epsilon filter earning its keep: an annealer at low
temperature produces exactly such 0.1 %-apart neighbors in bulk.

### 6.5 Why the heuristic seed survives

The seed is evaluated first, so it enters the archive immediately. Even after
the fitness-driven search drifts away (e.g. `GA_Energy` trading a little
energy for much worse makespan), the seed stays archived unless something
*actually dominates it* — this is how a seed that starts on the universal
front is never lost (`NonDominatedArchive` javadoc).

### 6.6 The same rule for the MOEA arms

NSGA-II, SPEA-II and AMOSA publish through the identical mechanism: their
`TaskSchedulingProblem.evaluate()` — the single choke point through which
every MOEA evaluation passes exactly once — offers each evaluated, repaired
solution to a `NonDominatedArchive` with the same 1 % epsilon (the
*publication archive*, PR #218). The wrappers return that archive's members
as the published front; the library's own final population/archive snapshot
is kept only for plotting (`lastMoeaResult`). Consequently **all seven arms
publish under one rule**: the non-dominated set of *all evaluated solutions*,
epsilon-pruned — `ParetoContribution` measures search quality, not
publication convention.

---

## 7. PowerCeiling: the Constrained Variants (brief)

The PowerCeiling study runs in two phases (`CampaignRunner.run`,
`PowerCeilingExperiment` javadoc):

1. **Phase 1 — uncapped.** The same 7 arms as the WaitingTime study
   (§4) run unconstrained. During front re-simulation each solution's
   *coincident* Step-8 fleet peak is captured, and
   `PowerCapCalibrator.deriveCaps` derives the cap tiers from the pooled peak
   distribution (percentiles targeting ≈ 90 / 60 / 30 % feasibility) — no
   fixed caps are baked in.
2. **Phase 2 — constrained re-runs.** Every arm is re-run as a
   `_PC<tier>` variant under each derived cap $P_{\text{cap}}$
   (`AlgorithmRegistry.createPowerCeiling`), with the same hyperparameters and
   warm seeds as its base arm.

What changes under a cap:

| Piece | Constrained behaviour |
|---|---|
| Constraint value | $\text{violation} = \max(0,\; \text{peak aggregate DC power} - P_{\text{cap}})$, measured per evaluation by a sweep-line power meter (`PowerCeilingEnergyObjective`) |
| MOEA arms | `PowerCeilingSchedulingProblem` (1 constraint) → MOEA applies **Deb's constrained domination**: feasible beats infeasible; two infeasibles → smaller violation wins; two feasibles → normal Pareto |
| GA/SA arms | `GenerationalGAwithDominancePowerCeiling` / `SimulatedAnnealingWithDominancePowerCeiling` run the capped problem with the same search |
| Publication archive | `ConstrainedNonDominatedArchive` — same admission contract as §6 but under Deb's rules; the epsilon filter applies only among feasible members |

The combined report scores uncapped baselines and all constrained arms, plus
the `PowerCapFeasibility` CSVs. The `_PC_<n>kW` label syntax (e.g.
`NSGA-II_PC_190kW`) remains available for explicit fixed caps.

---

## 8. Reproducibility Notes

- `CampaignRunner.doRunOne` re-seeds the global `RandomGenerator` before
  *every* (arm, seed, scenario) run: `engine.setRandomSeed(seed)` with
  $\text{seed} = 200 + \text{runIndex}$.
- The MOEA arms propagate the same run seed to MOEA Framework's global `PRNG`
  (`propagateSeed`); `NSGA2Configuration.randomSeed` is set by the registry.
- GA/SA give each operator its own **derived stream seed**
  (`RandomGenerator.deriveStreamSeed(base, 0/1/2)` for repair / crossover /
  mutation) — seeding all from the same value would correlate their streams.
- The seed heuristics are RNG-free (§3), so warm-starting consumes no
  randomness; `computeHeuristicSeed` also resets the context afterwards.
- Both `RandomGenerator` and MOEA's `PRNG` are **process-global** — never run
  two optimizations concurrently in one JVM if same-seed determinism matters.
- `CampaignReproducibilityTest` verifies same-seed fronts are bit-identical.

## 9. Source Map

| Topic | Where to look |
|---|---|
| Experiment mains & parameters | `newExperiments/MakespanEnergyExperiment.java`, `WaitingTimeEnergyExperiment.java`, `PowerCeilingExperiment.java`, `AlgorithmParameters.java` |
| Arm construction & seeding | `newExperiments/AlgorithmRegistry.java` |
| Campaign driver & re-simulation | `newExperiments/CampaignRunner.java` |
| Primary objective per study | `newExperiments/PrimaryObjective.java` |
| Seed heuristics | `PlacementStrategy/task/LPTTaskAssignmentStrategy.java`, `WorkloadAwareTaskAssignmentStrategy.java`, `EnergyAwareTaskAssignmentStrategy.java` |
| Chromosome / encoding | `metaheuristic/SchedulingSolution.java`, `metaheuristic/moea/TaskSchedulingProblem.java` |
| Objectives & the lane scheduler | `metaheuristic/objectives/MakespanObjective.java`, `WaitingTimeObjective.java`, `EnergyObjective.java`, `LaneSchedule.java` |
| Operators | `metaheuristic/operators/MutationOperator.java`, `CrossoverOperator.java`, `RepairOperator.java` |
| GA | `metaheuristic/GenerationalGAAlgorithm.java`, `GenerationalGAwithDominanceStrategy.java`, `GAConfiguration.java` |
| SA | `metaheuristic/SimulatedAnnealingAlgorithm.java`, `SimulatedAnnealingWithDominanceStrategy.java`, `SAConfiguration.java`, `cooling/AdaptiveCoolingSchedule.java` |
| Dominance archives | `metaheuristic/NonDominatedArchive.java`, `ConstrainedNonDominatedArchive.java`, `ObjectiveScaleNormalizer.java` |
| MOEA arms | `metaheuristic/moea/MOEA_NSGA2TaskSchedulingStrategy.java`, `MOEA_SPEA2TaskSchedulingStrategy.java`, `MOEA_AMOSATaskSchedulingStrategy.java`, `FixedAMOSA.java`, `TaskSchedulingVariation.java`, `TaskSchedulingMutation.java` |
| PowerCeiling constrained pieces | `metaheuristic/moea/PowerCeilingSchedulingProblem.java`, `newExperiments/PowerCapCalibrator.java`, PC strategy/algorithm classes in `metaheuristic/` |
| Front utilities (knee point) | `metaheuristic/ParetoFront.java` |
