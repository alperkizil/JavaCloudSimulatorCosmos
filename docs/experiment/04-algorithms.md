# 4. Algorithms

Each algorithm has two parts: a one-paragraph explanation aimed at
someone who has never read a metaheuristics paper, then LaTeX-style
pseudocode. All algorithms share the same representation: a solution
is an integer vector of length $N=500$ whose $i$-th entry is the VM
index assigned to task $i$.

Global budget for every algorithm:

- Population size $\mu = 200$
- Evaluation cap $F_{\max} = 40\,000$
- Ten independent runs with seeds $200, 201, \dots, 209$

## 4.1 Genetic Algorithm (GA) — single-objective

**What it does, informally.** Keep a pool of 200 candidate assignments.
Every generation, pick two "parents" by holding small tournaments
(winner = lower fitness), mix their assignment vectors to form two
"children", randomly nudge a few entries in each child, and score the
children with the fitness function. Replace the pool with the best mix
of old and new, preserving a small elite that never dies. Repeat until
you've used up the 40 000 evaluations.

The algorithm optimises *one* scalar fitness at a time — either
$\mathrm{WT}$ or $\mathrm{E}$. Two copies therefore exist
(`GA_WaitingTime`, `GA_Energy`).

```text
Algorithm  GA(population μ=200, F_max=40000, p_c=0.95, p_m=0.05, elite=0.20, tournament k=5)

 1: P ← random valid assignments of size μ          ⟵ seeded with WorkloadAware/EnergyAware heuristics
 2: evaluate every s ∈ P                                (F ← μ)
 3: while F < F_max do
 4:   E ← top ⌈elite · μ⌉ solutions of P by fitness
 5:   Offspring ← ∅
 6:   while |Offspring| < μ - |E| do
 7:     p1 ← tournament(P, k);  p2 ← tournament(P, k)
 8:     (c1, c2) ← uniform_crossover(p1, p2)      with prob p_c
 9:     mutate(c1, p_m);  mutate(c2, p_m)
10:     repair(c1);       repair(c2)                 ⟵ fix GPU/CPU violations, capacity limits
11:     evaluate(c1);     evaluate(c2)              (F ← F + 2)
12:     Offspring ← Offspring ∪ {c1, c2}
13:   P ← E ∪ Offspring
14: return argmin_{s∈P} fitness(s)
```

Sources: `metaheuristic/GeneticAlgorithm.java`,
`metaheuristic/GAConfiguration.java` (defaults at lines 291, 445–456),
operators in `metaheuristic/operators/`.

## 4.2 Simulated Annealing (SA) — single-objective

**What it does, informally.** Start from one candidate and a
"temperature" $T$. Repeatedly propose a small random change
(neighbour); if the new fitness is better, accept it; if worse, accept
it with probability $\exp(-\Delta f / T)$. Cool $T$ geometrically so
uphill moves get rarer. The early high-temperature phase escapes local
optima; the late low-temperature phase refines.

Three extras make the implementation robust:

1. **Auto-calibrated $T_0$**: sample 100 neighbours from the starting
   solution and pick the $T_0$ that gives an 0.8 initial acceptance
   rate (`SAConfiguration.java:373-379`). Avoids the usual "magic
   initial temperature" headache.
2. **Reheating**: if no improvement for 15 temperature steps, multiply
   $T$ by 5 (up to 3 times per run). Helps escape late-stage plateaus.
3. **Adaptive equilibrium length**: how many proposals per
   temperature grows/shrinks in $[50, 400]$ based on how often moves
   are being accepted.

```text
Algorithm  SA(F_max=40000, α=0.95, T_final=0.001, eq_len=200, reheat=5.0×3)

 1: s ← random valid assignment                    ⟵ seeded with heuristic
 2: T ← calibrate_T0(s, 0.8)                      ⟵ sample 100 neighbours
 3: s* ← s;  F ← 0;  stagnation ← 0
 4: while T > T_final and F < F_max do
 5:   for  eq_len  iterations do
 6:     s' ← mutate(s, strength = scale_by(T))    ⟵ bigger moves when hot
 7:     repair(s');  evaluate(s')                 (F ← F + 1)
 8:     Δ ← fitness(s') − fitness(s)
 9:     if Δ < 0 or rand() < exp(−Δ / T) then s ← s' end if
10:     if fitness(s) < fitness(s*) then s* ← s;  stagnation ← 0
11:     else                               stagnation ← stagnation + 1
12:   end for
13:   T ← α · T
14:   if stagnation ≥ 15 and reheats_left > 0 then T ← 5 · T;  reheats_left −= 1 end if
15: return s*
```

Sources: `metaheuristic/SimulatedAnnealingAlgorithm.java`,
`metaheuristic/SAConfiguration.java`,
`metaheuristic/cooling/GeometricCoolingSchedule.java:28-33`.

## 4.3 GA- and SA-dominance-archive variants

**The trick.** Run the same GA or SA on a *scalar* fitness (e.g.
waiting-time with energy as tie-breaker, or vice-versa) but every time
the algorithm ever sees a candidate, also offer it to a separate
**non-dominated archive** that keeps every solution not strictly
beaten on both $(\mathrm{WT}, \mathrm{E})$. At the end, report the
archive rather than the scalar-best.

This produces a Pareto front for the price of a single-objective
search, which turned out to be surprisingly competitive in our
experiments (see `reports/new/experiment_summary.csv`).

The four variants differ by which scalar is the search objective:

| Variant                       | Search objective  | Archive also tracks |
|-------------------------------|-------------------|---------------------|
| `GA_WaitingTime_Dominance`    | WT                | E                   |
| `GA_Energy_Dominance`         | E                 | WT                  |
| `SA_WaitingTime_Dominance`    | WT                | E                   |
| `SA_Energy_Dominance`         | E                 | WT                  |

Implementation hook: `metaheuristic/ConstrainedNonDominatedArchive.java`
(shared with the cap variants; the unconstrained case simply offers
violation = 0).

## 4.4 NSGA-II

**What it does, informally.** NSGA-II evolves two objectives
simultaneously. After each generation it sorts the combined parent +
offspring pool into non-domination *fronts*: front 1 = points nobody
dominates, front 2 = points dominated only by front 1, etc. The next
generation is filled front-by-front; when a front doesn't fit, the
algorithm takes the points from that front that are most *spread out*
along the trade-off curve, measured by **crowding distance** — the
size of the empty box around each point.

Outcome: a front that covers the whole trade-off, not just the knee.

```text
Algorithm  NSGA-II(μ=200, F_max=40000, p_c=0.95, p_m=0.05)

 1: P ← random valid assignments of size μ
 2: evaluate every s ∈ P        (F ← μ)
 3: while F < F_max do
 4:   Q ← ∅
 5:   while |Q| < μ do
 6:     p1 ← binary_tournament(P, rank, crowding_distance)
 7:     p2 ← binary_tournament(P, rank, crowding_distance)
 8:     (c1, c2) ← uniform_crossover(p1, p2)     with prob p_c
 9:     mutate(c1, p_m);  mutate(c2, p_m)
10:     repair(c1);       repair(c2)
11:     evaluate(c1);     evaluate(c2)          (F ← F + 2)
12:     Q ← Q ∪ {c1, c2}
13:   R ← P ∪ Q
14:   F₁, F₂, … ← non_dominated_sort(R)
15:   P' ← ∅;  i ← 1
16:   while |P'| + |Fᵢ| ≤ μ do P' ← P' ∪ Fᵢ;  i ← i + 1
17:   add to P' the ⌈μ − |P'|⌉ elements of Fᵢ with largest crowding distance
18:   P ← P'
19: return front F₁ of the final P
```

Implemented via MOEA Framework 4.5 (`lib/moeaframework-4.5.jar`),
wrapped in
`metaheuristic/moea/MOEA_NSGA2TaskSchedulingStrategy.java`. Problem
encoder: `moea/TaskSchedulingProblem.java`.

## 4.5 SPEA-II

**What it does, informally.** A near-cousin of NSGA-II. It maintains
two pools: a normal population and an external **archive** of the
non-dominated solutions found so far. Fitness of each point is a
combination of a **strength** measure (how many others it dominates)
plus a density term (distance to the $k$-th nearest neighbour in
objective space) to encourage spread. When the archive overflows, it
prunes the most crowded points.

SPEA-II often excels at *preserving* good corner points (the extremes
of the front) where crowding-distance-based NSGA-II can drop them.

```text
Algorithm  SPEA-II(μ=200, archive_size=200, F_max=40000, p_c=0.95, p_m=0.05)

 1: P ← random valid assignments of size μ;  A ← ∅
 2: evaluate every s ∈ P       (F ← μ)
 3: while F < F_max do
 4:   compute strength S(i) = |{ j ∈ P ∪ A : i ≻ j }|   for all i
 5:   raw(i)     = Σ_{j ≻ i} S(j)
 6:   density(i) = 1 / (σ_i^k + 2)                      ⟵ k = √(|P|+|A|)
 7:   fitness(i) = raw(i) + density(i)
 8:   A' ← all non-dominated in P ∪ A
 9:   if |A'| > archive_size then truncate by nearest-neighbour crowding
10:   A  ← A'
11:   Q ← reproduce_and_vary(A, μ, p_c, p_m)   with binary tournaments on fitness
12:   evaluate(Q)             (F ← F + μ)
13:   P ← Q
14: return A
```

Implementation: `metaheuristic/moea/MOEA_SPEA2TaskSchedulingStrategy.java`
(MOEA Framework 4.5).

## 4.6 AMOSA — Archived Multi-Objective Simulated Annealing

**What it does, informally.** The multi-objective cousin of SA. A
single "current" solution walks through objective space; a trajectory
of non-dominated points visited along the way is stored in an archive
with hard and soft size limits (50 and 100). Acceptance probabilities
use the amount of domination between current, proposed, and archive
members, so the walk is biased toward filling gaps in the archive
rather than chasing a scalar minimum.

```text
Algorithm  AMOSA(HL=50, SL=100, iter_per_T=200, hill_climb=50, F_max=40000)

 1: seed archive A with initial non-dominated set (hill-climbing 50 iters)
 2: s ← random member of A;  T ← T_max;  F ← |A| + hill_climb
 3: while F < F_max do
 4:   for iter_per_T iterations do
 5:     s' ← mutate(s);  repair(s');  evaluate(s')     (F ← F + 1)
 6:     Δdom ← domination_amount(s, s', A)             ⟵ see AMOSA paper eq. (1–3)
 7:     p    ← 1 / (1 + exp(Δdom · T))                 ⟵ MOEA Framework's sign
 8:     if accept (p) then
 9:       s ← s'
10:       update_archive(A, s', HL, SL)
11:     end if
12:   end for
13:   T ← α · T
14: return A
```

Implementation: `metaheuristic/moea/MOEA_AMOSATaskSchedulingStrategy.java`
(MOEA Framework 4.5). Hyper-parameters set at
`WaitingTimeExperimentRunner.java:138-141`.

## 4.7 Shared operators

- **Uniform crossover** (`operators/CrossoverOperator.java`) — for each
  task index $i$, with probability 0.5 keep parent 1's VM, otherwise
  parent 2's. This respects the integer encoding without introducing
  "average" VMs.
- **Combined mutation** (`operators/MutationOperator.java`,
  `MutationType.COMBINED`) — a mix of *single-index swap* and
  *multi-index random-reset*. Strength scales with SA's temperature.
- **Repair** (`operators/RepairOperator.java`) — post-variation, any
  task assigned to an invalid VM (GPU task → CPU VM, or over-capacity)
  is reassigned to the closest feasible VM. Guarantees every evaluated
  solution is feasible apart from the optional power-cap constraint.
