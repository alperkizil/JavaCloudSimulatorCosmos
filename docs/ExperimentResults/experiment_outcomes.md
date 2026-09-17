# What the 17 Sep 2026 experiments tell us

This note compares the three article-1 experiments across their three scenarios and
seven algorithms. It only uses today's result folders:

- `MakespanVsEnergy_17_09_2026_09_50_39`
- `WaitingTimeVsEnergy_17_09_2026_10_30_00`
- `PowerCeilingWaitingTimeVsEnergy_17_09_2026_10_56_32`

It answers four questions:

1. Which algorithms work well **together** to build one good trade-off curve?
2. Which algorithm does best on which experiment and scenario?
3. Which scenario is the hardest?
4. Why did each algorithm succeed or fail where it did?

---

## 1. What was run

**The problem.** 500 tasks must be placed on 60 virtual machines (VMs) that live on
40 physical hosts (16 CPU-only, 12 GPU-only, 12 mixed). Each VM runs its tasks
back-to-back. A schedule is judged on two things at once, so there is no single
"best" schedule, only a curve of trade-offs (the Pareto front).

**Three experiments.** Each one trades energy against a different time measure:

| Experiment | Objective 1 | Objective 2 | Extra |
|---|---|---|---|
| Makespan vs Energy | total time until the last task finishes | energy (kWh) | none |
| Waiting time vs Energy | average time a task waits before it starts | energy | none |
| Waiting time vs Energy under a power cap | average waiting time | energy | the datacenter's peak power must stay under a cap |

**Three scenarios.** They differ only in the mix of tasks:

| Scenario | CPU tasks | GPU tasks | What it means |
|---|---|---|---|
| 1 Balanced | 250 | 250 | both kinds of machine are busy |
| 2 GPU_Stress | 100 | 400 | the 20 GPU VMs and 20 mixed VMs are the bottleneck |
| 3 CPU_Stress | 400 | 100 | the 20 CPU VMs and 20 mixed VMs are the bottleneck |

**Seven algorithms.** Two families:

- *Single-goal with a memory.* `GA_..._Dominance` and `SA_..._Dominance` chase one
  objective (the one in their name) and treat the other as a tiny tie-breaker. While
  they search, they remember every schedule that was not beaten on both objectives.
  That memory is their "front". GA is a genetic algorithm (a population of 200
  schedules mixed and mutated). SA is simulated annealing (one schedule, changed a
  little at a time).
- *True two-goal search.* `NSGA-II`, `SPEA-II` and `AMOSA` try to cover the whole
  trade-off curve directly. NSGA-II and SPEA-II are population methods. AMOSA is
  annealing with an archive of good schedules.

Every algorithm gets the same budget of 40,000 schedule evaluations and is run with
10 random seeds per scenario.

**In the power-cap experiment** each algorithm is run once without a cap, and then
five more times with the cap set to 90, 80, 70, 60 and 50 percent of a reference
peak (`PC90` … `PC50`). Those extra runs are "cap-aware": they are told the cap and
search for schedules that respect it.

## 2. How to read the scores

- **HV (hypervolume).** How much of the trade-off space an algorithm's front covers,
  measured against a fixed worst-case corner. Higher is better. The best overall
  score.
- **IGD.** Average distance from the combined best front to the algorithm's own
  points. Lower is better. It punishes fronts that miss a whole region.
- **Share of the front.** When all algorithms' points are merged and only the
  unbeaten ones are kept, this is the fraction that came from one algorithm. It says
  who actually pushes the curve outward.
- **Best value.** The single best waiting time, makespan or energy the algorithm
  reached.
- **Low / middle / high third.** The combined front sorted by the time objective and
  split in three. "Low" is the fast end, "high" is the slow-but-cheap end.

All scores are averages over the 10 seeds unless stated otherwise.

---

## 3. Question 1: who forms a good front together?

The short answer: **no single algorithm covers the curve. The curve is a relay.** The
single-goal algorithms hold the two ends, and the true two-goal algorithms fill the
middle.

### Waiting time vs Energy

Share of the combined front, and which third of the curve those points sit in:

| Algorithm | Balanced | GPU_Stress | CPU_Stress | Where its points sit |
|---|---|---|---|---|
| GA_Energy_Dominance | **43%** | 20% | **46%** | slow, cheap end (high third) and middle |
| NSGA-II | 25% | 22% | 21% | middle (the "knee") |
| SPEA-II | 15% | **30%** | 12% | fast side of the middle |
| SA_WaitingTime_Dominance | 10% | 7% | 11% | the very fastest tip |
| GA_WaitingTime_Dominance | 6% | 11% | 3% | fast tip, just behind SA |
| SA_Energy_Dominance | 2% | 10% | 5% | cheap end, mostly beaten by GA_Energy |
| AMOSA | 0% | 1% | 3% | middle, just behind NSGA-II and SPEA-II |

The relay is the same in every scenario:

1. **Fast tip:** `SA_WaitingTime_Dominance` always holds the single best waiting time
   (1.82 s, 4.50 s, 1.28 s). `GA_WaitingTime_Dominance` is right behind it.
2. **Fast side of the middle:** `SPEA-II`.
3. **Middle:** `NSGA-II`.
4. **Slow, cheap end:** `GA_Energy_Dominance` alone. Nobody else reaches waiting times
   above about 13 s at low energy, and nobody else gets below its energy
   (0.0470, 0.0658, 0.0298 kWh).

A practical minimum team is **SA_WaitingTime + SPEA-II + NSGA-II + GA_Energy**. That
set produces 94% of the Balanced front, 92% in GPU_Stress and 89% in CPU_Stress.

### Makespan vs Energy

| Algorithm | Balanced | GPU_Stress | CPU_Stress | Where its points sit |
|---|---|---|---|---|
| GA_Energy_Dominance | **39%** | **57%** | **42%** | slow, cheap end and middle |
| SA_Energy_Dominance | 34% | 11% | 14% | middle (Balanced), cheap end (GPU_Stress) |
| SA_Makespan_Dominance | 14% | 4% | **34%** | fast end |
| GA_Makespan_Dominance | 6% | 6% | 8% | the very fastest tip |
| SPEA-II | 7% | 7% | 2% | fast end |
| NSGA-II | 0% | 13% | 0% | fast end (GPU_Stress only) |
| AMOSA | 0% | 2% | 0% | fast end (GPU_Stress only) |

Here the relay is shorter, because the combined front itself is small (71, 99 and 59
points against 215, 149 and 174 in the waiting-time experiment). The fast end is held
by the two makespan-first algorithms, the rest by the two energy-first algorithms.
NSGA-II and SPEA-II score well on HV but add almost nothing to the front: their points
are a hair behind a specialist's.

A practical minimum team is **GA_Makespan + SA_Makespan + SA_Energy + GA_Energy**
(93%, 78% and 98% of the three fronts).

### Waiting time vs Energy under a power cap

Under a cap the relay changes as the cap gets tighter. Share of the front at three
tiers (cap-aware runs):

| Tier | Balanced | GPU_Stress | CPU_Stress |
|---|---|---|---|
| PC90 (loose) | GA_Energy 38%, NSGA-II 21%, AMOSA 15%, SPEA-II 9% | NSGA-II 23%, SPEA-II 22%, GA_Energy 19%, AMOSA 17% | GA_Energy 33%, NSGA-II 25%, AMOSA 13%, SPEA-II 13% |
| PC70 | GA_Energy 36%, SA_Energy 14%, AMOSA 14%, SPEA-II 13%, SA_WT 11%, NSGA-II 11% | GA_Energy 24%, NSGA-II 20%, SPEA-II 19%, SA_WT 16%, AMOSA 13% | SPEA-II 34%, GA_Energy 32%, NSGA-II 14%, SA_WT 12% |
| PC50 (tight) | **SA_Energy 56%, AMOSA 25%**, SA_WT 8%, GA_Energy 4%, NSGA-II 0%, SPEA-II 0% | **AMOSA 42%, SA_Energy 40%**, SA_WT 12%, everyone else 0% | GA_Energy 32%, SPEA-II 16%, NSGA-II 15%, SA_Energy 15%, SA_WT 13% |

Two things stand out:

- **The fastest feasible schedule is always from `SA_WaitingTime_Dominance`**, at
  every tier in every scenario.
- **When the cap is tight, the annealing algorithms take over.** At PC50 in Balanced
  and GPU_Stress, `SA_Energy` and `AMOSA` supply 80 percent of the front, and the two
  population methods `NSGA-II` and `SPEA-II` supply nothing. Section 6 explains why.

Cap-aware runs also add points that the uncapped runs never found: 35% of the whole
Balanced front, 25% of GPU_Stress and 57% of CPU_Stress come from cap-aware arms.

---

## 4. Question 2: who does best where?

### Overall HV by experiment and scenario

| Algorithm | Makespan S1 | S2 | S3 | Waiting S1 | S2 | S3 |
|---|---|---|---|---|---|---|
| GA_Energy_Dominance | 0.518 | **0.575** | 0.471 | **0.649** | **0.592** | **0.668** |
| SA_Energy_Dominance | **0.522** | 0.572 | 0.485 | 0.559 | 0.568 | 0.543 |
| NSGA-II | 0.516 | 0.504 | **0.492** | 0.552 | 0.575 | 0.550 |
| SPEA-II | 0.454 | 0.426 | 0.457 | 0.485 | 0.535 | 0.519 |
| SA_Makespan / SA_WaitingTime | 0.443 | 0.466 | 0.397 | 0.203 | 0.367 | 0.192 |
| AMOSA | 0.376 | 0.363 | 0.374 | 0.409 | 0.475 | 0.369 |
| GA_Makespan / GA_WaitingTime | 0.289 | 0.231 | 0.229 | 0.228 | 0.380 | 0.223 |

Bold is the best in that column. Under the power cap the uncapped ordering is the same
as in the waiting-time experiment.

### Head-to-head wins (statistically significant, HV, all scenarios pooled)

| Experiment | Ranking by wins minus losses |
|---|---|
| Makespan | NSGA-II 3–0, SA_Energy 3–0, GA_Energy 2–0, SA_Makespan 2–0, SPEA-II 2–2, AMOSA 1–5, GA_Makespan 0–6 |
| Waiting time | GA_Energy 6–0, NSGA-II 4–1, SA_Energy 4–1, SPEA-II 3–3, AMOSA 2–4, GA_WaitingTime 1–5, SA_WaitingTime 0–6 |
| Power cap (uncapped arms) | GA_Energy 6–0, NSGA-II 3–1, SA_Energy 3–1, SPEA-II 3–1, AMOSA 2–4, GA_WaitingTime 0–5, SA_WaitingTime 0–5 |

### Best single values

| | Balanced | GPU_Stress | CPU_Stress |
|---|---|---|---|
| Best waiting time | SA_WaitingTime 1.824 s | SA_WaitingTime 4.496 s | SA_WaitingTime 1.282 s |
| Best energy (waiting-time exp.) | GA_Energy 0.0470 | GA_Energy 0.0658 | GA_Energy 0.0298 |
| Best makespan | GA_Makespan 15.51 s | GA_Makespan 24.75 s | GA_Makespan 10.75 s |
| Best energy (makespan exp.) | GA_Energy 0.0482 | GA_Energy 0.0659 | GA_Energy 0.0302 |

### Reading the two tables together

- **`GA_Energy_Dominance` is the strongest single algorithm** in the waiting-time
  experiments. It wins HV in all three scenarios and never loses a significant
  head-to-head. It also has the best energy everywhere.
- **`NSGA-II` is the best all-rounder.** It is never first on HV in the waiting-time
  experiments but is second or third everywhere, has the best GD (its points are
  closest to the front), and wins the CPU_Stress makespan scenario.
- **The waiting-time-first algorithms score worst on HV but own the fast tip.** HV
  cannot see that. If the goal is "fastest schedule", `SA_WaitingTime_Dominance` is
  the answer in every scenario.
- **`GA_Makespan_Dominance` is the same story in the makespan experiment**: last on
  HV, first on best makespan in every scenario.
- **`SA_Makespan_Dominance` is much better than `GA_Makespan_Dominance`** on HV
  (0.40–0.47 against 0.23–0.29) and is the star of CPU_Stress, where it supplies a
  third of the front.
- **`AMOSA` is mid-table** (5th of 7) after today's mutation fix, and is the most
  useful algorithm under a tight cap.
- **Run time** (Balanced, waiting-time experiment): SA arms about 2 s, GA arms 3–4 s,
  AMOSA 4 s, NSGA-II and SPEA-II 6–7 s. The population methods cost three times the
  annealing ones for the same budget.

---

## 5. Question 3: which scenario is hardest?

Different scenarios are hard in different ways.

### GPU_Stress is the hardest to schedule well

| Evidence | Balanced | GPU_Stress | CPU_Stress |
|---|---|---|---|
| Best waiting time reachable | 1.82 s | **4.50 s** | 1.28 s |
| Spread of the front, waiting time (slowest ÷ fastest) | 17× | **7.7×** | 21× |
| Spread of the front, energy (highest ÷ lowest) | 1.40× | **1.27×** | 1.59× |
| Combined front size (waiting-time exp.) | 215 | **149** | 174 |
| Combined front size (makespan exp.) | 71 | 99 | 59 |
| Cap-aware arms with seeds that found no feasible schedule at PC50 | 2 arms (3 seeds) | **4 arms (26 seeds)** | none |

In GPU_Stress, 400 GPU tasks share 40 VMs with GPUs. The queue on those VMs decides
almost everything. There is little room to trade: the fastest schedule is 2.5 times
slower than in Balanced, the whole front is short, and the energy range is narrow. The
specialist algorithms are closer to each other here, which is why the
waiting-time-first arms score comparatively well (HV 0.37–0.38 instead of 0.19–0.23)
and why `SPEA-II` becomes the biggest single contributor.

Under a power cap, GPU_Stress is the only scenario where cap-aware population methods
fail outright: at PC50, `GA_WaitingTime` found no feasible schedule on 8 of 10 seeds,
`SPEA-II` on 7, `NSGA-II` on 6, `GA_Energy` on 5. GPU work draws a lot of power and
there are few ways to spread it out in time.

### CPU_Stress is the widest, so it separates algorithms the most

The front is longest (21× spread in waiting time, 1.59× in energy). The gap between
the best and worst algorithm is largest here (HV 0.668 against 0.192). Every
cap-aware arm found feasible schedules at every tier. In the makespan experiment
the makespan-first arms reach 10.7 s while the energy-first arms cannot get below
17 s, a 58% gap. That is where a specialist matters most.

### Balanced sits in between

Widest choice of machine types, medium spread, and the relay described in section 3
is at its cleanest.

### The cost of a power cap by scenario

Best waiting time reachable under each cap, relative to no cap:

| Scenario | PC90 | PC80 | PC70 | PC60 | PC50 |
|---|---|---|---|---|---|
| Balanced | +8.8% | +22.0% | +47.5% | +78.0% | **+128%** |
| GPU_Stress | +1.5% | +3.7% | +14.5% | +36.8% | +70% |
| CPU_Stress | +8.6% | +24.5% | +42.7% | +69.3% | +120% |

The percentage cost looks smallest in GPU_Stress, but only because its uncapped
waiting time is already high. In absolute terms GPU_Stress pays the most
(4.50 s → 7.66 s). Best energy is unaffected by the cap in every scenario until
PC50, and even then it only moves in two of them.

---

## 6. Question 4: why did each algorithm succeed or fail?

### GA_Energy_Dominance

**Succeeds:** HV winner in all waiting-time scenarios, best energy everywhere, largest
share of the front (up to 57%).

**Why.** It starts from random schedules, which are mid-speed and expensive, and walks
downhill on energy. Every unbeaten schedule it passes goes into its memory. So its
memory naturally traces the curve from the middle to the cheap end. It also produces
the largest memories (about 90 points per seed). The cheap end of the curve is long
(waiting times from 4 s to 31 s at nearly the same energy) and HV rewards covering it.

**Fails:** It never gets fast. Its best waiting time is 2.3 times worse than
`SA_WaitingTime` (4.17 s against 1.82 s) and its best makespan is 17–27 s against
10.7–24.7 s. Under a tight cap it fades: at PC50 in Balanced its share drops to 4%,
and in GPU_Stress it found no feasible schedule on 5 of 10 seeds. A population that
recombines whole schedules cannot creep under a power cap one task at a time.

### SA_Energy_Dominance

**Succeeds:** 2nd–3rd on HV, the largest memories of all (107–115 points), and the
main supplier under a tight cap (56% of the Balanced front and 40% of GPU_Stress at
PC50). Fastest algorithm to run.

**Why.** Annealing moves one to four tasks at a time, so it walks along the curve in
small steps and remembers many nearby points. Under a cap, a schedule that is
slightly over the limit is usually one task away from being under it, and small moves
find that. Its cap-aware runs stay 100% feasible at every tier.

**Fails:** Uncapped, its points are mostly beaten by `GA_Energy` (only 2% of the
Balanced front) because its energy is about 8% worse (0.0509 against 0.0470 kWh).
A single walker does not push as far as a population of 200.

### SA_WaitingTime_Dominance and GA_WaitingTime_Dominance

**Succeed:** They own the fast tip. `SA_WaitingTime` has the best waiting time in
every scenario, with and without a cap, at every tier. `GA_WaitingTime` is second.

**Why.** They chase waiting time only. Waiting time rewards spreading tasks evenly and
finishing the long ones first, and both algorithms find that quickly.

**Fail:** Worst HV and worst IGD in every waiting-time scenario. Their memories are
tiny (10–27 points) and sit in one corner, because they never explore the cheap side.
HV punishes a small corner. They do comparatively better in GPU_Stress only because
the front there is short, so a corner is a larger share of it.

Under a cap the two split: `SA_WaitingTime_PC50` is 100% feasible everywhere, but
`GA_WaitingTime_PC50` failed on 8 of 10 GPU_Stress seeds. Same reason as above: small
moves handle a cap, whole-schedule recombination does not.

### GA_Makespan_Dominance and SA_Makespan_Dominance

**Succeed:** `GA_Makespan` has the best makespan in every scenario. `SA_Makespan` is
close behind and, in CPU_Stress, supplies 34% of the front.

**Fail:** `GA_Makespan` is last on HV everywhere (0.23–0.29) with only 1–3 points per
seed. Makespan is a "worst machine" measure with flat steps, so many schedules tie.
The GA converges to one such point and its memory stays nearly empty.

**Why SA does better here.** The annealing walk passes through many intermediate
schedules with different makespans on its way down, and keeps them. Its memory
spans makespan 12.5 s to 27 s in CPU_Stress, where the GA's spans 10.7 s to 13 s.
The price is an uneven front (spacing 0.15 against 0.001).

### NSGA-II

**Succeeds:** Best all-rounder. 2nd on HV in the waiting-time experiments, 1st in
CPU_Stress makespan, best GD everywhere (its points are the closest to the true
front), fills the knee of the curve (45 of its 53 Balanced front points are in the
middle third).

**Why.** It ranks schedules by how many others beat them and spreads the population
along the curve. That is exactly the middle of the curve, where neither specialist
looks.

**Fails:** It never reaches an end. Best waiting time 2.83 s (against 1.82 s), best
energy 0.0541 (against 0.0470). In the makespan experiment it adds zero front points
in Balanced and CPU_Stress despite good HV: a specialist beats each of its points by
a little. It is the slowest to run. Under a tight cap it collapses: 0% of the front
at PC50 in Balanced and GPU_Stress, and 6 of 10 GPU_Stress seeds with no feasible
schedule. Its selection can only prefer "less over the cap"; when no member of the
population is under the cap, that signal is too weak to get there in 40,000
evaluations. In CPU_Stress, where the cap is easy to meet, it stays a 15–19%
contributor at every tier.

### SPEA-II

**Succeeds:** Like NSGA-II but leaning to the fast side (31 of its 32 Balanced front
points are in the fast third). Biggest single contributor in GPU_Stress (30%). Under
caps in CPU_Stress it is the largest contributor at PC70 (34%) and its PC90 run adds
more points to the overall front than any other cap-aware arm (39).

**Why.** SPEA-II's fitness counts how many schedules each point beats, which pulls it
toward the crowded fast side of the curve, and its archive truncation keeps the
spread.

**Fails:** HV a step below NSGA-II (0.485–0.535 against 0.552–0.575). Same collapse
as NSGA-II under a tight cap in Balanced and GPU_Stress (7 of 10 GPU_Stress seeds
infeasible at PC50).

### AMOSA

**Succeeds:** 5th of 7 uncapped, but the most useful algorithm under a tight cap.
At PC50 it supplies 25% of the Balanced front and 42% of GPU_Stress, is 100% feasible
at every tier in every scenario, and at PC90 it adds new points at the fast tip
(19 in Balanced, 14 in CPU_Stress).

**Why.** Today's code change made its moves shrink as the search cools, so late in
the run it moves one task at a time. That is the right move size for creeping under a
power cap. Its acceptance rule also uses "by how much" a schedule is beaten, which
lets it slide along the cap boundary.

**Fails:** Uncapped it adds almost nothing to the front (0–3%). Its curve is narrow
(waiting time 2.6–4.9 s in Balanced) and sits just behind NSGA-II and SPEA-II. Its
archive is capped at 50–100 points, so it cannot hold a wide curve. In CPU_Stress
under caps it contributes little (1–6%), because there every population method is
feasible and covers the curve better.

---

## 7. Things to keep in mind

- **HV favours the cheap end.** In these problems the time axis stretches 8 to 21
  times while the energy axis stretches only 1.3 to 1.6 times. Whoever covers the
  long, slow, cheap tail wins HV. That is why energy-first algorithms top the HV
  tables even though nobody would run a schedule that waits 30 s to save a little
  energy. Read HV together with "share of the front" and "best value".
- **The makespan experiment has a very long tail.** The combined front reaches
  makespans of 196–321 s at the lowest energy, against 11–25 s at the fastest. That
  tail is real but not useful, and it inflates the energy-first arms' HV.
- **Same seeds everywhere.** The uncapped rows in the power-cap folder are identical
  to the waiting-time folder, so the two can be read as one experiment.
- **Under a cap, "no feasible schedule" counts as no score.** Averages for those arms
  at PC50 use fewer seeds (see `ScoredSeeds`).

## 8. One-line summary

Use `SA_WaitingTime` (or `GA_Makespan`) for the fastest schedule, `GA_Energy` for
the cheapest, `NSGA-II` and `SPEA-II` for everything in between, and switch to
`SA_Energy` and `AMOSA` when a tight power cap is in force. GPU_Stress is the
scenario with the least room to move; CPU_Stress is the one where picking the right
algorithm matters most.
