# 4. Algorithms

This file documents the **seven search-based algorithms** actually run
in the makespan experiment. Each has two parts: a one-paragraph
explanation aimed at someone who has never read a metaheuristics
paper, then pseudocode. All algorithms share the same representation —
an integer vector of length $N = 500$ whose $i$-th entry is the VM
index (in $\{0, \dots, 59\}$) assigned to task $i$.

Global budget for every algorithm:

- Population size $\mu = 200$ (AMOSA uses its soft archive limit of
  100 as its MOEA-Framework population parameter instead);
- Evaluation cap $F_{\max} = 40\,000$;
- Crossover rate $p_c = 0.95$, mutation rate $p_m = 0.05$;
- Ten independent runs with seeds $200, 201, \dots, 209$;
- The two warm-start chromosomes from
  [`04b-heuristics.md`](04b-heuristics.md) are injected into the
  initial population via `addSeedAssignment(...)` for every algorithm
  in the roster (see the `createStrategy` switch at
  `ScenarioComparisonExperimentRunner.java:455-514`).

The scalar objectives minimised by the single-objective drivers are
either **makespan** (`MakespanObjective`) or **energy**
(`EnergyObjective`) from [`03-objectives.md`](03-objectives.md). When a
scalar fitness is used, the other objective is still added as a
**tiebreaker** with weight $0.001$
(`ScenarioComparisonExperimentRunner.java:124, 593, 645, 666`) so that
among assignments with equal primary fitness the one with smaller
secondary fitness is preferred.

## 4.1 GA with dominance archive — `GA_Makespan_Dominance`, `GA_Energy_Dominance`

**What it does, informally.** A generational Genetic Algorithm
searches a *scalar* fitness surface — either makespan (with energy as
a 0.001-weight tiebreaker) or energy (with makespan as the
tiebreaker). Every candidate the GA ever evaluates is also offered to
a separate **non-dominated archive** that keeps every solution not
strictly beaten on both $(\mathrm{MS}, \mathrm{E})$. At the end, the
archive is returned to the runner rather than the single scalar-best
individual, so the strategy reports a Pareto front for the price of
one scalar search.

```text
Algorithm  GA_Dominance(μ=200, F_max=40000, p_c=0.95, p_m=0.05, elite=0.20, tournament k=5)

 1: P ← random valid assignments of size μ           ⟵ 2 seeds replaced by the WorkloadAware /
                                                       EnergyAware chromosomes from 04b-heuristics.md
 2: evaluate every s ∈ P                              (F ← μ)
 3: A ← non_dominated(P)                              ⟵ dominance archive on (MS, E)
 4: while F < F_max do
 5:   E ← top ⌈elite · μ⌉ solutions of P by primary fitness
 6:   Offspring ← ∅
 7:   while |Offspring| < μ − |E| do
 8:     p1 ← tournament(P, k);  p2 ← tournament(P, k)
 9:     (c1, c2) ← uniform_crossover(p1, p2)    with prob p_c
10:     mutate(c1, p_m);  mutate(c2, p_m)
11:     repair(c1);       repair(c2)            ⟵ fix GPU/CPU violations, capacity limits
12:     evaluate(c1);     evaluate(c2)          (F ← F + 2)
13:     offer(c1, A);     offer(c2, A)          ⟵ keep if non-dominated, drop dominated members
14:     Offspring ← Offspring ∪ {c1, c2}
15:   P ← E ∪ Offspring
16: return A
```

The two variants differ only by which objective is the scalar search
target:

| Variant                  | Primary search objective | Tiebreaker | Seed used          |
|--------------------------|--------------------------|------------|--------------------|
| `GA_Makespan_Dominance`  | Makespan                 | Energy     | WorkloadAware seed |
| `GA_Energy_Dominance`    | Energy                   | Makespan   | EnergyAware seed   |

Sources:
`PlacementStrategy/task/metaheuristic/GenerationalGAwithDominanceStrategy.java`,
`GAConfiguration.java`, operators in
`PlacementStrategy/task/metaheuristic/operators/`. Wiring in
`ScenarioComparisonExperimentRunner.java:480-494` and
`createGADominanceStrategy(...)` at lines 655–673.

## 4.2 SA with dominance archive — `SA_Makespan_Dominance`, `SA_Energy_Dominance`

**What it does, informally.** Start from one candidate and a
"temperature" $T$. Repeatedly propose a small random change
(neighbour); if the new scalar fitness is better, accept it; if worse,
accept it with probability $\exp(-\Delta f / T)$. Cool $T$
geometrically so uphill moves get rarer. Every single candidate the
walk touches is offered to a non-dominated archive on
$(\mathrm{MS}, \mathrm{E})$; at the end the archive is returned.

Three extras make the implementation robust
(`SAConfiguration.java`, lines 108–120 of the runner):

1. **Auto-calibrated $T_0$**: before the main loop, sample 100
   neighbours from the starting solution and pick the $T_0$ that
   yields an 0.80 initial acceptance rate. Avoids the usual "magic
   initial temperature" headache.
2. **Reheating**: if no improvement for 15 temperature steps,
   multiply $T$ by 5 (up to 3 times per run). Helps escape late-stage
   plateaus.
3. **Adaptive equilibrium length**: the number of proposals per
   temperature grows or shrinks in $[50, 400]$ based on how often
   moves are being accepted (thresholds 0.7 / 0.1 on acceptance rate).

```text
Algorithm  SA_Dominance(F_max=40000, α=0.95 adaptive, T_final=0.001, eq_len ∈ [50,400], reheat=5.0 × 3)

 1: s ← random valid assignment                    ⟵ seeded with WA or EA chromosome
 2: T ← calibrate_T0(s, 0.8)                       ⟵ sample 100 neighbours, pick T giving 80 % acceptance
 3: A ← {s};  F ← 1;  stagnation ← 0
 4: while T > T_final and F < F_max do
 5:   for  eq_len  iterations do
 6:     s' ← mutate(s, strength = scale_by(T))     ⟵ bigger perturbations when hot
 7:     repair(s');  evaluate(s')                  (F ← F + 1)
 8:     offer(s', A)                               ⟵ update (MS, E) non-dominated archive
 9:     Δ ← primary(s') − primary(s)
10:     if Δ < 0 or rand() < exp(−Δ / T) then s ← s'
11:     if primary(s) improves best then stagnation ← 0 else stagnation ← stagnation + 1
12:   end for
13:   T ← α(acceptance_rate) · T                   ⟵ adaptive cooling schedule
14:   eq_len ← clamp(adjust_by(acceptance_rate), 50, 400)
15:   if stagnation ≥ 15 and reheats_left > 0 then T ← 5 · T;  reheats_left −= 1 end if
16: return A
```

Variants:

| Variant                  | Primary search objective | Tiebreaker | Seed used          |
|--------------------------|--------------------------|------------|--------------------|
| `SA_Makespan_Dominance`  | Makespan                 | Energy     | WorkloadAware seed |
| `SA_Energy_Dominance`    | Energy                   | Makespan   | EnergyAware seed   |

Sources:
`PlacementStrategy/task/metaheuristic/SimulatedAnnealingWithDominanceStrategy.java`,
`SAConfiguration.java`,
`PlacementStrategy/task/metaheuristic/cooling/AdaptiveCoolingSchedule.java`.
Wiring in `createSADominanceStrategy(...)` at runner lines 675–681.

## 4.3 NSGA-II

**What it does, informally.** NSGA-II evolves two objectives
simultaneously. After each generation it sorts the combined parent +
offspring pool into non-domination *fronts*: front 1 = points nobody
dominates, front 2 = points dominated only by front 1, and so on. The
next generation is filled front-by-front; when a front does not fit,
the algorithm takes the points from that front that are most
*spread out* along the trade-off curve, measured by **crowding
distance** — the size of the empty box around each point in objective
space. The result is a front that covers the whole trade-off, not
just the knee.

```text
Algorithm  NSGA-II(μ=200, F_max=40000, p_c=0.95, p_m=0.05)

 1: P ← random valid assignments of size μ        ⟵ 2 seeds = WorkloadAware, EnergyAware
 2: evaluate every s ∈ P                          (F ← μ)
 3: while F < F_max do
 4:   Q ← ∅
 5:   while |Q| < μ do
 6:     p1 ← binary_tournament(P, rank, crowding_distance)
 7:     p2 ← binary_tournament(P, rank, crowding_distance)
 8:     (c1, c2) ← uniform_crossover(p1, p2)      with prob p_c
 9:     mutate(c1, p_m);  mutate(c2, p_m)
10:     repair(c1);       repair(c2)
11:     evaluate(c1);     evaluate(c2)            (F ← F + 2)
12:     Q ← Q ∪ {c1, c2}
13:   R ← P ∪ Q
14:   F₁, F₂, … ← non_dominated_sort(R)
15:   P' ← ∅;  i ← 1
16:   while |P'| + |Fᵢ| ≤ μ do P' ← P' ∪ Fᵢ;  i ← i + 1
17:   add to P' the ⌈μ − |P'|⌉ elements of Fᵢ with largest crowding distance
18:   P ← P'
19: return front F₁ of the final P
```

Implemented via MOEA Framework 4.5
(`lib/moeaframework-4.5.jar`), wrapped in
`PlacementStrategy/task/metaheuristic/moea/MOEA_NSGA2TaskSchedulingStrategy.java`
with the problem encoder in
`PlacementStrategy/task/metaheuristic/moea/TaskSchedulingProblem.java`.
Wiring at `createNSGA2Strategy(...)`
(`ScenarioComparisonExperimentRunner.java:683-702`). The selection
method for the single "primary" assignment reported alongside the
Pareto front is `KNEE_POINT` (line 700).

## 4.4 SPEA-II

**What it does, informally.** A near-cousin of NSGA-II. It maintains
two pools: a normal population and an external **archive** of the
non-dominated solutions found so far. Fitness of each point is a
**strength** measure (how many others it dominates) plus a density
term (distance to the $k$-th nearest neighbour in objective space) to
encourage spread. When the archive overflows, SPEA-II prunes the most
crowded points. It often excels at *preserving* good corner points
(the extremes of the front) where crowding-distance-based NSGA-II can
drop them.

```text
Algorithm  SPEA-II(μ=200, archive_size=200, F_max=40000, p_c=0.95, p_m=0.05)

 1: P ← random valid assignments of size μ;  A ← ∅
 2: evaluate every s ∈ P                             (F ← μ)
 3: while F < F_max do
 4:   compute strength S(i) = |{ j ∈ P ∪ A : i ≻ j }|   for all i
 5:   raw(i)     = Σ_{j ≻ i} S(j)
 6:   density(i) = 1 / (σ_i^k + 2)                     ⟵ k = √(|P|+|A|)
 7:   fitness(i) = raw(i) + density(i)
 8:   A' ← all non-dominated in P ∪ A
 9:   if |A'| > archive_size then truncate by nearest-neighbour crowding
10:   A  ← A'
11:   Q ← reproduce_and_vary(A, μ, p_c, p_m)           ⟵ binary tournaments on fitness
12:   evaluate(Q)                                      (F ← F + μ)
13:   P ← Q
14: return A
```

Implementation:
`PlacementStrategy/task/metaheuristic/moea/MOEA_SPEA2TaskSchedulingStrategy.java`
(MOEA Framework 4.5). Wiring at `createSPEA2Strategy(...)`
(`ScenarioComparisonExperimentRunner.java:704-723`). Selection
`KNEE_POINT` on line 721.

## 4.5 AMOSA — Archived Multi-Objective Simulated Annealing

**What it does, informally.** The multi-objective cousin of SA. A
single "current" solution walks through objective space; a trajectory
of non-dominated points visited along the way is stored in an archive
with **hard** and **soft** size limits (50 and 100). Acceptance
probabilities use the amount of domination between the current,
proposed, and archive members, so the walk is biased toward filling
gaps in the archive rather than chasing a scalar minimum.

Budget accounting for this experiment: with `hillClimbing = 50`,
`softLimit = 100` and `iterationsPerTemperature = 200`, AMOSA spends
~10 200 evaluations on initialisation (25 % of the budget) and ~29 800
evaluations on the main loop, which translates to ~142 temperature
steps at 200 iterations per step. The temperature sweeps from
$T_0 = 15$ down to $15 \cdot 0.95^{142} \approx 0.011$
(`ScenarioComparisonExperimentRunner.java:131-142`).

```text
Algorithm  AMOSA(HL=50, SL=100, iter_per_T=200, hill_climb=50, T_0=15.0, α=0.95, γ=2.0, F_max=40000)

 1: seed the archive A with SL initial non-dominated solutions by hill climbing
    (each of the SL starting points runs for hill_climb iterations)
 2: s ← a random member of A;  T ← T_0;  F ← |A| · hill_climb + |A|
 3: while F < F_max do
 4:   for iter_per_T iterations do
 5:     s' ← mutate(s, p_m = 0.05);  repair(s');  evaluate(s')     (F ← F + 1)
 6:     Δdom ← domination_amount(s, s', A, γ)                     ⟵ AMOSA paper eq. (1)–(3)
 7:     p    ← 1 / (1 + exp(Δdom · T))                            ⟵ MOEA-Framework sign convention
 8:     if accept (p) then
 9:       s ← s'
10:       update_archive(A, s', HL, SL)                           ⟵ prune to HL if |A| > SL
11:     end if
12:   end for
13:   T ← α · T
14: return A
```

Note on the inverted temperature semantics: because AMOSA uses
$p = 1 / (1 + e^{\Delta\mathrm{dom} \cdot T})$ rather than the
canonical $e^{-\Delta / T}$, **higher** $T$ means **less** acceptance
of worse solutions. This is why $T_0 = 15$ is small-looking compared to
the more familiar hundreds-of-watts temperatures used by single-
objective SA codes. The source comments (runner lines 125–134) spell
out the expected acceptance envelope.

Implementation:
`PlacementStrategy/task/metaheuristic/moea/MOEA_AMOSATaskSchedulingStrategy.java`
(MOEA Framework 4.5). Hyper-parameters set at
`ScenarioComparisonExperimentRunner.java:135-142`. Wiring at
`createAMOSAStrategy(...)` on lines 725–751. Selection `KNEE_POINT`
on line 742.

## 4.6 Shared operators

- **Uniform crossover**
  (`PlacementStrategy/task/metaheuristic/operators/CrossoverOperator.java`)
  — for each task index $i$, with probability 0.5 keep parent 1's VM,
  otherwise parent 2's. This respects the integer encoding without
  introducing "average" VMs.
- **Combined mutation**
  (`PlacementStrategy/task/metaheuristic/operators/MutationOperator.java`,
  `MutationType.COMBINED`) — a mix of **single-index swap** (pick one
  task, move it to a random other VM) and **multi-index random reset**
  (pick $k \le 4$ tasks and randomise each). For SA the perturbation
  strength grows with the current temperature (`SA_SCALED_PERTURBATION`
  at runner line 119).
- **Repair**
  (`PlacementStrategy/task/metaheuristic/operators/RepairOperator.java`)
  — after variation, any task assigned to an incompatible VM (e.g. a
  GPU task on a CPU-only VM, or an over-capacity assignment) is
  reassigned to the closest feasible VM. Guarantees every evaluated
  solution is feasible.

## 4.7 Pareto-front simulation

For the three multi-objective algorithms (NSGA-II, SPEA-II, AMOSA) the
runner does not trust the optimiser's internal objective estimates:
after the scheduler finishes, it resets the simulation state and
**re-runs the full VM execution / task execution / energy pipeline for
every single point in the returned Pareto front**
(`simulateAllParetoSolutions` at
`ScenarioComparisonExperimentRunner.java:845-911`). Every row of
`makespan_24_04_2026/experiment_summary.csv` is therefore a real
simulator measurement, not an optimiser estimate. The
dominance-archive GA/SA variants report the archive they accumulated
in-flight (single-point simulation only) because they are
single-objective-driven and the archive is a by-product.
