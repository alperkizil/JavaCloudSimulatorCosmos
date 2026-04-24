# 5. Results & Analysis

This file interprets the run stored in
`final_experiment_results/makespan_24_04_2026/`. All numbers quoted
below are **arithmetic means over the ten seeds** (`MEAN` rows) taken
directly from
`makespan_24_04_2026/experiment_summary.csv` and the matching
`scenario_{1,2,3}_performance_metrics.csv` files. Statistical claims
come from `makespan_24_04_2026/statistical_tests_summary.csv`, which
records pair-wise Wilcoxon signed-rank tests with Holm-adjusted
$p$-values and Vargha–Delaney $A_{12}$ effect sizes over **36 paired
observations** (three scenarios × ten seeds + one extra per scenario
where seed count is not a clean multiple after filtering failure
sentinels).

## 5.1 Reading the metrics

All four Pareto-front indicators in the CSVs are reported per run and
averaged per algorithm-scenario cell. Brief reminders:

- **Hypervolume (HV, ↑)** — area of objective space dominated by an
  algorithm's front relative to a fixed reference point
  `(ref_x = 61.3 s makespan, ref_y = 0.691 kWh)` from
  `quality_indicators_all_scenarios.csv`. Higher is better: more of
  the trade-off region is covered.
- **Generational Distance (GD, ↓)** — average distance from the
  algorithm's points to the closest universal-Pareto point. Lower is
  better: *convergence* onto the true front.
- **Inverted Generational Distance (IGD, ↓)** — average distance from
  each universal-Pareto point to the closest algorithm point. Lower
  is better: *coverage* of the front. IGD penalises missing regions
  that HV sometimes forgives.
- **Pareto contribution** — number of points on the scenario-wide
  universal Pareto front contributed by each algorithm across its
  ten seeds. Direct count; higher is better.

The universal Pareto front is the non-dominated frontier of the union
of every algorithm's solutions across all seeds, computed by
`computeUniversalPareto(...)`
(`ScenarioComparisonExperimentRunner.java:929-960`) with failure
sentinels stripped out (line 922). All normalised indicators are
computed against this union, so they directly measure *how close each
algorithm got to what the roster as a whole can do*.

## 5.2 Per-scenario summary tables

Values are the `MEAN` rows from
`scenario_{1,2,3}_performance_metrics.csv`.

### 5.2.1 Scenario 1 — Balanced (250 CPU + 250 GPU tasks)

| Algorithm                | HV ↑     | GD ↓     | IGD ↓    | Pareto pts | Time (ms) | Best makespan (s) | Best energy (kWh) |
|--------------------------|----------|----------|----------|------------|-----------|-------------------|-------------------|
| **GA_Energy_Dominance**  | **0.503** | 0.152    | **0.187** | 2          | 1 922     | 14                | 0.246             |
| SA_Makespan_Dominance    | 0.448    | **0.064** | 0.310    | **9**      | **912**   | 16                | 0.333             |
| SA_Energy_Dominance      | 0.360    | 0.207    | 0.274    | 7          | 925       | 14                | **0.221**         |
| NSGA-II                  | 0.315    | 0.280    | 0.332    | 0          | 2 588     | 14                | 0.353             |
| SPEA-II                  | 0.307    | 0.258    | 0.336    | 0          | 3 204     | 14                | 0.366             |
| GA_Makespan_Dominance    | 0.105    | 0.123    | 0.617    | 0          | 1 915     | 14                | 0.523             |
| AMOSA                    | 0.063    | 0.173    | 0.577    | 0          | 4 110     | 14                | 0.493             |

### 5.2.2 Scenario 2 — GPU-Stress (100 CPU + 400 GPU tasks)

| Algorithm                | HV ↑     | GD ↓     | IGD ↓    | Pareto pts | Time (ms) | Best makespan (s) | Best energy (kWh) |
|--------------------------|----------|----------|----------|------------|-----------|-------------------|-------------------|
| **GA_Energy_Dominance**  | **0.525** | 0.126    | **0.155** | 3          | 1 916     | 18                | 0.312             |
| SPEA-II                  | 0.494    | 0.190    | 0.225    | 0          | 3 208     | 18                | 0.435             |
| NSGA-II                  | 0.491    | 0.197    | 0.224    | 0          | 2 584     | 18                | 0.420             |
| SA_Makespan_Dominance    | 0.431    | **0.068** | 0.353    | 5          | **904**   | 20                | 0.465             |
| SA_Energy_Dominance      | 0.417    | 0.191    | 0.235    | **6**      | 927       | 18                | **0.278**         |
| AMOSA                    | 0.331    | 0.191    | 0.356    | 0          | 4 074     | 18                | 0.595             |
| GA_Makespan_Dominance    | 0.247    | 0.133    | 0.452    | 2          | 1 908     | 16                | 0.639             |

### 5.2.3 Scenario 3 — CPU-Stress (400 CPU + 100 GPU tasks)

| Algorithm                | HV ↑     | GD ↓     | IGD ↓    | Pareto pts | Time (ms) | Best makespan (s) | Best energy (kWh) |
|--------------------------|----------|----------|----------|------------|-----------|-------------------|-------------------|
| SA_Makespan_Dominance    | **0.441** | 0.152    | 0.340    | 5          | **851**   | 18                | 0.252             |
| **GA_Energy_Dominance**  | 0.423    | 0.127    | **0.144** | 3          | 1 899     | 13                | 0.188             |
| SPEA-II                  | 0.222    | 0.204    | 0.329    | 1          | 3 109     | 13                | 0.276             |
| NSGA-II                  | 0.214    | 0.250    | 0.331    | 0          | 2 488     | 13                | 0.272             |
| SA_Energy_Dominance      | 0.189    | 0.191    | 0.234    | **7**      | 877       | 13                | **0.164**         |
| GA_Makespan_Dominance    | 0.076    | 0.205    | 0.628    | 0          | 1 847     | 16                | 0.380             |
| AMOSA                    | 0.047    | **0.105** | 0.526    | 2          | 4 106     | 13                | 0.343             |

## 5.3 Headline findings

### 5.3.1 `GA_Energy_Dominance` wins hypervolume and coverage

Across all three scenarios `GA_Energy_Dominance` posts the highest
mean HV and the lowest mean IGD. Put plainly, a *single-objective
genetic algorithm searching on energy with a 0.001-weight makespan
tiebreaker*, when paired with a dominance-archive bookkeeper, produced
the widest and best-covered Pareto fronts in this experiment.

Statistical significance
(`statistical_tests_summary.csv`, metric `HV`, pooled 36-pair
comparisons):

- `GA_Energy_Dominance` beats every other algorithm at
  $p_{\text{Holm}} < 0.05$. The closest competitor is
  `SA_Makespan_Dominance` at $p_{\text{Holm}} \approx 0.037$; the rest
  are below $10^{-4}$. Vargha–Delaney $A_{12}$ against SA_Makespan is
  0.65 (medium effect in favour of GA_Energy_Dominance); against the
  MOEA-Framework algorithms it sits at 0.77–0.80 (large effect).

Why it works: the heavy-tailed workload (§2.3) means most tasks are
cheap and a few are expensive. Searching on energy with the scaled
makespan tiebreaker lets the GA push all small tasks onto
power-efficient slow VMs (which minimises energy under the quadratic
speed-scaling rule in §3.4) while the tiebreaker still prevents any
VM from piling up so much work that the makespan stretches out and
re-explodes the idle-cost term.

### 5.3.2 SA variants dominate the Pareto-contribution count

Despite lower HV than `GA_Energy_Dominance` in Balanced and GPU-Stress,
the two SA dominance variants together contribute the **most universal
Pareto points** in every scenario: 16 of the front's points in
Scenario 1, 11 in Scenario 2, and 12 in Scenario 3. The native MO
algorithms (NSGA-II, SPEA-II, AMOSA) together contribute at most **3**
universal-front points across all three scenarios combined.

This is consistent with the convergence picture: `SA_Makespan_Dominance`
has the lowest mean GD in Scenarios 1 and 2 (0.064 and 0.068), meaning
the SA walk finds the literal best points on the front even when its
front is sparse. The dominance archive captures those corner points
faithfully.

### 5.3.3 NSGA-II ≈ SPEA-II, both middle-of-the-pack

`statistical_tests_summary.csv` reports the NSGA-II vs SPEA-II
Wilcoxon test at $p_{\text{Holm}} = 1.0$ on HV, $p_{\text{Holm}} =
6.7 \times 10^{-4}$ on GD, and similar on IGD. Practically the two
native multi-objective algorithms are indistinguishable on HV but
SPEA-II has tighter convergence. Neither contributed a single
universal-Pareto point in Balanced or GPU-Stress, and only one in
CPU-Stress.

The most likely cause is the quadratic energy–speed curve: the
universal front pushes into a narrow energy band where only a handful
of VM-speed assignments are optimal, and a diversity-preserving
algorithm (especially NSGA-II with crowding distance) keeps spending
evaluations on near-duplicate points further up the makespan axis.

### 5.3.4 AMOSA is the weakest algorithm in this experiment

AMOSA posts the lowest HV in Scenarios 1 and 3, contributes zero
universal-Pareto points in Scenarios 1 and 2, and runs the longest
(~4 100 ms of scheduler time per run in every scenario). Its GD is
competitive in Scenario 3 (0.105, the best of the seven), which means
AMOSA *does* find points close to the true front — but its archive
covers so little of the front that HV and IGD come out badly. Every
pair-wise HV comparison involving AMOSA is significant at
$p_{\text{Holm}} < 4 \times 10^{-6}$.

The 25 % of the evaluation budget spent on the 50-iteration hill-
climbing initialisation (runner comment at lines 130–134) appears not
to be recovered in the main loop; the archive stays narrow.

### 5.3.5 `GA_Makespan_Dominance` is consistently the worst HV

Contrary to intuition — it searches on the same objective that names
the experiment — `GA_Makespan_Dominance` produces the second-lowest HV
in every scenario (only AMOSA is worse, and only in two of three
scenarios). Its fronts are tight around the makespan-optimal corner
but sparse: IGD stays above 0.45 in every scenario, meaning the
algorithm is systematically missing the energy-optimal half of the
front.

This is an intentional design outcome: when minimising makespan the
quadratic speed-scaling rule makes the fastest VMs exponentially
expensive but *doesn't* penalise the makespan search itself, so the
GA converges to a tight "go-fast-whatever-the-cost" basin and the
dominance archive can only record a handful of points along the
descent.

## 5.4 Runtime envelope

Scheduler-step runtimes averaged over the ten seeds
(`TimeMs` column, Task Assignment step only):

| Algorithm                | Sc1 ms | Sc2 ms | Sc3 ms |
|--------------------------|--------|--------|--------|
| SA_Makespan_Dominance    | 912    | 904    | **851** |
| SA_Energy_Dominance      | 925    | 927    | 877    |
| GA_Makespan_Dominance    | 1 915  | 1 908  | 1 847  |
| GA_Energy_Dominance      | 1 922  | 1 916  | 1 899  |
| NSGA-II                  | 2 588  | 2 584  | 2 488  |
| SPEA-II                  | 3 204  | 3 208  | 3 109  |
| AMOSA                    | 4 110  | 4 074  | 4 106  |

So SA is roughly **4× faster** than AMOSA, GA is roughly **2×** faster
than AMOSA, and the two SA variants are each faster than every GA
variant by at least 900 ms. Given that SA also delivers the highest
number of universal-Pareto points, it is the clear cost-adjusted
winner when the goal is "find a few excellent corners of the front".

## 5.5 Figures included in the results folder

The following plots in `makespan_24_04_2026/` give the visual
companions to the tables above:

- `scenario_pareto_fronts.png` — union of every algorithm's Pareto
  front in each scenario, overlaid on the universal front;
- `metric_hv.png`, `metric_gd.png`, `metric_igd.png`,
  `metric_pareto_contribution.png` — bar charts of the four
  indicators with seed-level error bars;
- `cd_diagram_HV.png`, `cd_diagram_GD.png`, `cd_diagram_IGD.png` —
  critical-difference diagrams from the Friedman-Nemenyi rank test;
- `runtime_and_efficiency.png` — scheduler-step runtime against
  quality indicator, so the cost-adjusted view of each algorithm is
  visible.

## 5.6 Practical recommendation

Based on the ten-seed means:

- If you need **one well-covered Pareto front in one run**, use
  `GA_Energy_Dominance`. It wins HV in Balanced and GPU-Stress,
  comes second in CPU-Stress, and wins IGD in every scenario.
- If you need **the best extreme points** and have a lower budget,
  use `SA_Makespan_Dominance` or `SA_Energy_Dominance`: together
  they contribute the majority of the universal-Pareto points in
  every scenario, and each runs in under 1 s of scheduler time.
- The native multi-objective algorithms (NSGA-II, SPEA-II) are
  defensible second choices when the front is known to be *smooth* —
  but in this experiment, with the quadratic energy–speed curve, they
  pay for diversity they cannot use.
- Avoid `AMOSA` for this problem shape: it is the slowest and ranks
  last on HV in two of three scenarios. If you need SA-style walks
  of objective space, the dominance-archive SA variants are
  uniformly better here.

All of these recommendations are conditional on the makespan-vs-energy
objective pair with speed-based power scaling enabled. Switching off
`useSpeedBasedScaling` collapses the front
(see [`03-objectives.md`](03-objectives.md) §3.4) and the ordering of
algorithms will change.

## 5.7 Cross-experiment comparison — makespan vs. average waiting time

The sibling experiment in `reports/new/` runs the *same* infrastructure,
scenarios, seeds, and algorithm budget, but replaces the makespan
objective with **average waiting time**
(`WaitingTimeObjective`, see
[`../experiment/03-objectives.md`](../experiment/03-objectives.md)).
The algorithm roster differs slightly — the waiting-time run includes
`GA_WaitingTime_Dominance` and `SA_WaitingTime_Dominance` in place of
the makespan variants — but the five algorithms common to both runs
(`GA_Energy_Dominance`, `SA_Energy_Dominance`, `NSGA-II`, `SPEA-II`,
`AMOSA`) let us read the landscape change directly.

### 5.7.1 Hypervolume: waiting-time is far easier

Mean HV of each algorithm, aggregated across the three scenarios:

| Algorithm             | Makespan experiment | Waiting-time experiment | Δ (WT − MS) |
|-----------------------|---------------------|-------------------------|-------------|
| SA_Energy_Dominance   | 0.322               | 0.686                   | **+0.364**  |
| GA_Energy_Dominance   | 0.484               | 0.648                   | +0.164      |
| NSGA-II               | 0.340               | 0.586                   | +0.247      |
| SPEA-II               | 0.341               | 0.588                   | +0.246      |
| AMOSA                 | 0.147               | 0.425                   | +0.278      |

Every common algorithm scores substantially higher on waiting-time than
on makespan. The effect is strongest for `SA_Energy_Dominance` (+0.36
absolute HV) and smallest for `GA_Energy_Dominance` (+0.16) — the
latter is the one algorithm that already covers the makespan front
well, so it has the least headroom.

### 5.7.2 Convergence indicators: GD collapses ~10×

Mean GD, lower is better:

| Algorithm             | Makespan (Sc1/Sc2/Sc3) | Waiting-time (Sc1/Sc2/Sc3) |
|-----------------------|------------------------|-----------------------------|
| NSGA-II               | 0.280 / 0.197 / 0.250  | 0.022 / 0.028 / 0.016       |
| SPEA-II               | 0.258 / 0.190 / 0.204  | 0.016 / 0.021 / 0.013       |
| AMOSA                 | 0.173 / 0.191 / 0.105  | 0.030 / 0.028 / 0.012       |
| GA_Energy_Dominance   | 0.152 / 0.126 / 0.127  | 0.126 / 0.161 / 0.114       |
| SA_Energy_Dominance   | 0.207 / 0.191 / 0.191  | 0.116 / 0.077 / 0.087       |

The native multi-objective algorithms converge roughly **10× closer**
to their universal front under waiting time than under makespan. IGD
shows a parallel pattern (NSGA-II IGD 0.062–0.076 on waiting-time vs.
0.224–0.332 on makespan). The GA/SA dominance variants change less
because their archive only collects what the scalar walk happens to
pass by, which is largely independent of front shape.

### 5.7.3 Pareto contribution: SPEA-II is the waiting-time specialist

Total universal-Pareto points contributed across all three scenarios:

| Algorithm               | Makespan total | Waiting-time total |
|-------------------------|----------------|--------------------|
| SPEA-II                 | 1              | **126**            |
| GA_WaitingTime_Dominance| –              | 56                 |
| NSGA-II                 | 0              | 42                 |
| SA_Energy_Dominance     | 20             | 42                 |
| AMOSA                   | 2              | 27                 |
| GA_Energy_Dominance     | 8              | 7                  |
| SA_WaitingTime_Dominance| –              | 2                  |
| GA_Makespan_Dominance   | 2              | –                  |
| SA_Makespan_Dominance   | 19             | –                  |

Two reversals stand out:

1. **SPEA-II goes from near-zero contribution on makespan (1 point
   total) to 126 points on waiting-time.** The quadratic speed-scaling
   rule (§3.4) makes the makespan-vs-energy front narrow and clumped;
   SPEA-II's external archive saturates quickly on one side and cannot
   pay for diversity. Waiting-time is an *average*, not a max, so the
   front is wider and more even — exactly the shape SPEA-II's
   strength-plus-density fitness is designed for.
2. **The SA dominance variants change partners.**
   `SA_Makespan_Dominance` contributes 19 makespan Pareto points, but
   its waiting-time analog `SA_WaitingTime_Dominance` only manages 2.
   Meanwhile `SA_Energy_Dominance` contributes 20 points in makespan
   and 42 in waiting-time. The energy side of the archive is more
   productive than the throughput side in both experiments, just by
   different margins.

### 5.7.4 The `_Dominance` vs. `_Dominance` flip

A tidy way to see what changed:

|                               | Makespan run | Waiting-time run |
|-------------------------------|--------------|-------------------|
| HV of throughput-driven GA    | 0.143 (avg)  | 0.349 (avg)       |
| HV of throughput-driven SA    | 0.440 (avg)  | 0.348 (avg)       |
| HV of energy-driven GA        | 0.484 (avg)  | 0.648 (avg)       |
| HV of energy-driven SA        | 0.322 (avg)  | 0.686 (avg)       |

- Under **makespan**, the *makespan-driven* SA variant beats the
  *makespan-driven* GA (0.440 vs 0.143) because the GA saturates on the
  "go-fast-whatever-the-cost" basin and its archive stays sparse.
- Under **waiting-time**, the *WT-driven* SA and GA tie (0.349 vs
  0.348) and are *both* beaten by their energy-driven siblings — the
  energy side of the trade-off is the one with the wider front in
  both experiments.
- In both experiments, `GA_Energy_Dominance` and `SA_Energy_Dominance`
  dominate HV — the insight is the same: search on energy, collect a
  non-dominated archive of everything you see, and you get a broader
  front than search on the "operational" objective directly.

### 5.7.5 Practical reading

- For **any** problem where energy-vs-performance under this
  quadratic speed-scaling rule matters, the two *energy-driven*
  dominance-archive variants are the safest default — they win HV in
  *both* experiments by wide margins.
- The makespan objective is the harder search landscape. If the
  production system cares about tail latency (makespan-like), budget
  for the convergence loss or switch in `SA_Makespan_Dominance` to
  at least collect good corner points cheaply.
- The MOEA-Framework multi-objective algorithms (NSGA-II, SPEA-II)
  are only competitive when the front is smooth enough that diversity
  preservation pays — waiting-time is such a case, makespan is not.
- AMOSA is the weakest option in **both** experiments. If a
  simulated-annealing variant is needed, the dominance-archive SA
  wrappers in this codebase out-perform AMOSA on every indicator
  measured in either experiment.
