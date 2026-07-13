# Performance Metrics: The Quality Indicators Shown by `results_explorer.py`

This document describes every performance metric displayed by the analysis
tool `scripts/results_explorer.py` for campaign result folders produced by the
runners in `newExperiments/` — with special attention to the **two different
hypervolume columns** (`HV_fixed` and the legacy `HV`).

One fact frames everything: **the explorer computes no indicator itself.** All
metrics are computed in Java during the campaign
(`observer/ParetoAnalyzer.analyzeScenario`, delegating the legacy four to
`multiobjectivePerformance/PerfMet/PerformanceMetrics`) and written to CSV by
`observer/ExperimentReporter`. The explorer only reads those CSVs, pivots
them, and draws mean ± std bars. So this document is really about what the
Java analyzer computes and how to read it.

Everything below is derived directly from the source code (class and method
names are cited throughout). How the fronts are *produced* is covered by the
sibling documents: the optimizers and their publication archives in
`docs/MetaheuristicTaskScheduler.md` (§6), and the re-simulation that turns
every published solution into measured objective values in
`docs/VMExecution.md` (§8).

---

## 1. Data Flow: Who Computes What

```
CampaignRunner.doRunOne          one (arm, seed) run -> published front + runtime
        v
ParetoAnalyzer.analyzeScenario   universal front, per-arm fronts, all indicators
        v
ExperimentReporter               scenario_N_*.csv  (per scenario)
        v
results_explorer.py              reads CSVs, displays them (GUI or --export-all)
```

| File in the result folder | Contents | Explorer use |
|---|---|---|
| `scenario_N_pareto_graph_data.csv` | every published point of every (arm, seed) + `Universal_Pareto` rows | Scatter tab (clouds + universal front) |
| `scenario_N_algorithm_pareto_fronts.csv` | each arm's pooled front (union over its seeds) | Scatter tab (connected fronts) |
| `scenario_N_performance_metrics.csv` | per-seed `HV, GD, IGD, Spacing, NonDomSolutions, TotalSolutions, ParetoContribution, TimeMs, HV_fixed` + MEAN/STDDEV rows + `Universal_Pareto` trailer | Metrics, Runtime, Tables tabs |
| `scenario_N_seed_collaboration.csv` | per-seed near-tie contribution scoreboard | Metrics + Tables tabs |
| `scenario_N_solution_details.json(.gz)` | per-solution schedules (task→VM, queues, energy) | Details tab |
| `experiment_summary.csv` | scenario names + cross-scenario summary | scenario labels only |

Older folders without `algorithm_pareto_fronts.csv` still work: the explorer
derives each arm's pooled front from the point clouds itself
(`_non_dominated`, same dominance rule). The `HV_fixed` choices appear only
when the column exists in the folder.

---

## 2. Common Ground: Fronts and Normalization Frames

**Both objectives are minimized.** Each study is a 2-D objective pair; units
follow the CSVs (`AXIS_UNITS` in the explorer):

| Study | Objective 1 | Objective 2 |
|---|---|---|
| MakespanEnergy | Makespan (s) | Energy (kWh) |
| WaitingTimeEnergy | Average Waiting Time (s) | Energy (kWh) |
| PowerCeiling | Average Waiting Time (s) | Energy (kWh) — plus an auxiliary `PeakPowerWatts` column (not an objective, not scored) |

Four point sets appear in the metrics. All use the same dominance rule (weak
Pareto dominance, minimization); the three union sets are built by
`ParetoAnalyzer` with de-duplication at $10^{-9}$:

| Set | Built by | Meaning |
|---|---|---|
| **Published front** of one (arm, seed) | the arm's publication archive (1 % epsilon-fraction near-duplicate filter, `MetaheuristicTaskScheduler.md` §6), re-simulated (`VMExecution.md` §8) | the unit every per-seed metric is computed on |
| **Per-arm pooled front** | `ParetoAnalyzer.computeAlgorithmFront` | non-dominated union over that arm's 10 seeds |
| **Universal front** (pooled) | `ParetoAnalyzer.computeUniversalPareto` | non-dominated union over ALL arms and seeds — the reference set for GD/IGD and strict contribution |
| **Per-seed universal front** | same union, scoped to one seed's runs | reference set for the seed-collaboration scoreboard (§5.2) |

Metrics are computed on **normalized** objectives. Two different frames are
in play, and they are exactly what separates the two HV columns:

| Frame | Bounds taken over | Used by |
|---|---|---|
| **Scenario union frame** (fixed) | ALL published points of all arms and seeds in the scenario (plus the universal rows) | `HV_fixed`, near-tie scoreboard, the Scatter tab's "Normalize axes" option |
| **Per-pair frame** (moving) | only {this seed's front ∪ universal front}, recomputed for every (arm, seed) | legacy `HV`, `GD`, `IGD`, `Spacing` |

Normalization is min–max in both cases:

$$\hat f_k = \frac{f_k - f_k^{\min}}{f_k^{\max} - f_k^{\min}}, \qquad k \in \{1, 2\}$$

with the range floored at $10^{-12}$. In the scenario union frame,
$f_k^{\min}$/$f_k^{\max}$ are the scenario-wide **ideal**/**nadir**; every
published point lands in $[0,1]^2$ and the frame is identical for every arm
and seed. In the per-pair frame the bounds move with the seed's own extreme
points — the root of the legacy HV's comparability problem (§3.2).

---

## 3. The Two Hypervolume Columns

Hypervolume (HV) measures **how much of the objective space a front
dominates**, up to a reference point $\mathbf r$. For a non-dominated front
sorted by $x$ ascending ($y$ then descends), both implementations use the same
2-D sweep:

$$\mathrm{HV}(F;\mathbf r) = \sum_{i=1}^{n} (r_x - x_i)(y_{i-1} - y_i), \qquad y_0 := r_y$$

i.e. the area between the staircase of the front and the reference corner.
**Higher is better** for both columns. Everything else about them differs.

### 3.1 `HV_fixed` — fixed reference, one frame per scenario

Computed by `ParetoAnalyzer.computeFixedIndicators`, a verified Java port of
`scripts/recompute_hv.py` (`ParetoAnalyzerParityTest` pins them to each
other). For one (arm, seed) published front $F$:

1. Normalize $F$ into the **scenario union frame** (§2).
2. Reference point $\mathbf r = (1.1,\ 1.1)$ — in raw units that is
   $r_k = f_k^{\text{nadir}} + 0.1\,(f_k^{\text{nadir}} - f_k^{\text{ideal}})$,
   the nadir plus a 10 % margin.
3. Divide by the maximum possible area:

$$\text{HV\_fixed} = \frac{\mathrm{HV}(\hat F;\ (1.1,\,1.1))}{1.1 \times 1.1} \;\in\; [0, 1]$$

Properties:

- **Comparable across arms and seeds within one run** — every value in a
  scenario is measured in the same box against the same reference. This is
  the only HV the campaign treats as citable.
- **Not comparable across campaigns.** The frame is the run's own pooled
  ideal/nadir; change the workload, the arms, or the budget and the frame
  moves. Never compare `HV_fixed` between differently-configured campaigns.
- The 10 % margin means even a point *at* the nadir dominates the
  $[1.0,1.1]^2$ sliver: a worst-case front scores $0.01/1.21 \approx 0.008$,
  not 0, so extreme points are ranked rather than zeroed out.
- 1.0 is unreachable in practice ($\hat F$ never covers the ideal corner
  exactly); the per-seed values sit below the universal front's `HV_fixed`,
  which is the scenario's ceiling (it dominates every published point by
  construction).

### 3.2 `HV` — legacy, per-pair frame

Computed by `PerformanceMetrics.HV(0)` on the pair
{seed's non-dominated front, universal front}
(`ParetoAnalyzer.computeLegacyIndicators`):

1. Normalize **both fronts of the pair** by the pair's own min/max (§2,
   per-pair frame).
2. Reference point $(1,\,1)$ — the worst corner of *that pair's* bounding
   box.
3. Same sweep formula; no further division (the frame box has area 1).

The problem: the frame is **self-referential**. The pair's max on each axis
is usually set by the seed's own worst point, so every (arm, seed) is
measured in a different box — a box it partly defines itself. Two concrete
artifacts:

- A point lying on the pair's boundary contributes zero area. Extreme case:
  a seed whose only point is one *endpoint* of the universal front
  normalizes to $(0, 1)$ and scores $\mathrm{HV} = 0$ — despite being on the
  universal front.
- An arm with terrible outlier points stretches its own frame, changing its
  own score in a direction that has nothing to do with front quality.

This is why `recompute_hv.py` exists ("disadvantages single-objective
algorithms" — its own docstring) and why `HV_fixed` was later added as a
trailing CSV column. The legacy column is kept **only** so the CSV schema
stays byte-compatible with the historical runs.

### 3.3 Side by side

| | `HV_fixed` | `HV` (legacy) |
|---|---|---|
| Normalization frame | scenario union (fixed per scenario) | pair {seed front ∪ universal} (moves per seed) |
| Reference point | $(1.1, 1.1)$ normalized = nadir + 10 % | $(1, 1)$ normalized = pair's worst corner |
| Range | $[0, 1]$ (divided by 1.21) | $[0, 1]$ (undivided) |
| Comparable across arms/seeds in a run | **yes** | **no** |
| Comparable across campaigns | no (frame is per-run) | no |
| Source | `ParetoAnalyzer.computeFixedIndicators` (= `recompute_hv.py`) | `PerformanceMetrics.HV` |
| Explorer label | "Hypervolume (fixed reference)" | "Hypervolume (legacy, per-pair frame)" |
| Use in the paper | **cite this one** | do not cite |

The `Universal_Pareto` trailer row mixes the two conventions: its `HV` is the
universal front normalized by *its own* bounds
(`ParetoAnalyzer.universalHV`) — comparable to nothing — while its `HV_fixed`
is in the scenario frame and is the valid ceiling for every arm's `HV_fixed`.
The Tables tab shows both in the "Pooled universal front: …" info line.

---

## 4. GD, IGD, Spacing (legacy family)

All three are computed in the **per-pair normalized space** (§2) by
`PerformanceMetrics`, on the seed's non-dominated front $A$ against the
universal front $U$. The frame caveat of §3.2 applies to them too, though
more mildly (they measure distances, not boxed areas).

| Metric | Formula | Answers | Better |
|---|---|---|---|
| **GD** | $\frac{1}{\|A\|}\sum_{a \in A} \min_{u \in U} \lVert a-u \rVert_2$ | how close are my points to the universal front? (convergence) | lower; 0 = every published point lies on it |
| **IGD** | $\frac{1}{\|U\|}\sum_{u \in U} \min_{a \in A} \lVert u-a \rVert_2$ | how well do my points cover the *whole* universal front? (convergence + spread) | lower; 0 = front fully reproduced |
| **Spacing** | see below | how evenly are my points spread along my own front? | lower = more even |

GD averages over *my* points — a single excellent point gives GD ≈ 0 with no
coverage. IGD averages over *universal* points — the same single point yields
a large IGD because the uncovered rest of the universal front is far from it.
Read them together. (Both are arithmetic means of Euclidean distances — this
implementation, not the root-mean-square textbook variant.)

**Spacing** (`PerformanceMetrics.Spacing`): sort the front by $x$, take the
Manhattan gap between neighbours
$g_i = |\hat x_{i+1} - \hat x_i| + |\hat y_i - \hat y_{i+1}|$, give each point
its adjacent gap ($d_1 = g_1$, interior $d_i = \min(g_{i-1}, g_i)$,
$d_n = g_{n-1}$), then

$$S = \sqrt{\tfrac{1}{n-1} \sum_{i=1}^{n} (\bar d - d_i)^2}$$

— a standard-deviation of gap sizes. $S = 0$ means perfectly regular spacing;
it says **nothing about quality** (an evenly spaced bad front scores 0 too).
`ParetoAnalyzer` forces Spacing = 0 for fronts of size ≤ 1. Fallbacks on a
degenerate run: `hv=0`, `gd=igd=Double.MAX_VALUE`, `spacing=0`.

---

## 5. Pareto Contribution — Two Deliberately Different Definitions

Both ask "how much of the collaborative front did this arm supply?", against
different reference sets and with different matching. They coexist by design;
the divergence between them is diagnostic, not a bug.

### 5.1 `ParetoContribution` — strict count vs the pooled front

`ParetoAnalyzer.paretoContributionCount`: the number of distinct **pooled
universal front** points that this (arm, seed)'s published points match
**exactly** — within $10^{-9}$ absolute on both objectives.

- Explorer label "Pareto Contribution (pooled union count)", y-axis "points
  contributed", with the pooled front size annotated on each panel.
- **The MEAN row is not a mean.** For this column it holds
  `unionContributionCount`: the distinct pooled-front points matched by *any*
  of the arm's seeds. The explorer's bar chart plots exactly this row (no
  error bar). The per-seed rows are ordinary per-seed counts (Tables tab).
- Harsh by construction: the pooled front is a best-of-all-seeds envelope,
  and a $10^{-9}$ cut gives full credit to razor-thin winners. An arm can
  have the best mean `HV_fixed` and still score 0 here.

### 5.2 `CollabSharePct` — near-tie share of each seed's own front

From `scenario_N_seed_collaboration.csv`
(`ParetoAnalyzer.analyzeSeedCollaboration`), the fairer, distributional
variant. Per seed:

1. Build **that seed's** all-arms universal front (same union rules, one
   seed's runs only) — removing the lucky-seed envelope effect.
2. Credit arm $X$ for each front point $u$ that some published point $p$ of
   $X$ near-ties on **both** objectives:

$$|p_k - u_k| \;\le\; \max\!\big(3 \times 10^{-3} \cdot |u_k|,\; 10^{-9}\big), \qquad k \in \{1,2\}$$

   (0.3 % relative, scaled to the universal point's own magnitude). Each $p$
   credits only its **nearest** qualifying $u$ — nearest-match keeps credit
   monotonic in the tolerance, first-match does not. Distinct credited points
   are counted (`ContributionCount`), and

$$\texttt{ContributionPct} = 100 \cdot \frac{\text{credited points}}{|\text{seed universal front}|}$$

3. The explorer plots mean ± std of `ContributionPct` over the 10 seeds
   ("Pareto Contribution (per-seed near-tie share %)"); the Tables tab shows
   the full per-seed scoreboard.

Caveats to disclose wherever these numbers are cited:

- Near-tie credit is **shared** credit: equally-good points within 0.3 %
  credit several arms, so shares can sum to **> 100 %**.
- `CONTRIB_REL_EPS = 3e-3` was calibrated empirically on the committed
  campaigns ($10^{-4}$ recovers zero real near-ties); re-validate it after
  workload changes (`HANDOFF.md` §3.1).
- The tolerance affects the scoreboard **only** — union construction,
  de-duplication, and §5.1 keep their $10^{-9}$ semantics untouched.

| | §5.1 strict | §5.2 near-tie |
|---|---|---|
| Reference set | pooled universal front (all seeds) | each seed's own universal front |
| Match tolerance | $10^{-9}$ absolute | 0.3 % relative, nearest-match |
| Headline number | union count over seeds (MEAN row) | mean ± std of per-seed share % |
| Shares sum to | ≤ front size | can exceed 100 % |
| Character | conservative, winner-takes-all | distributional collaboration evidence |

---

## 6. Runtime (`TimeMs`)

`TimeMs` is the wall-clock time of the **entire run**
(`System.currentTimeMillis` bracketing `CampaignRunner.doRunOne`): setup
steps 1–4, warm-start seed construction, the 40 000-evaluation search, steps
6–8 for the selected solution, **plus the re-simulation of every published
front member** through the real engine (`VMExecution.md` §8). It is
runner-loop cost, not pure optimizer cost — an arm publishing a large front
pays for simulating it.

The Runtime tab converts to seconds and shows mean ± std per scenario plus an
"All scenarios" panel. The CSV's own MEAN row averages only runs with
`TimeMs > 0`.

---

## 7. Supporting Columns and Trailer Rows

| Column / row | Meaning |
|---|---|
| `NonDomSolutions` | size of the seed's published front after re-filtering to non-dominated (defensive; published archives are already non-dominated) |
| `TotalSolutions` | raw published-front size |
| `ParetoContribution` STDDEV cell | empty by design (the MEAN cell is a union count, § 5.1) |
| `Universal_Pareto,ALL` trailer (`performance_metrics.csv`) | pooled front: own-frame legacy `HV`, `GD=IGD=Spacing=0`, sizes, scenario-frame `HV_fixed` (§3.3) |
| `Universal_Pareto,MEAN/STDDEV` trailer (`seed_collaboration.csv`) | mean ± std of the per-seed universal fronts' size and `HV_fixed` — shown in the Tables info line |
| `PeakPowerWatts` (PowerCeiling only) | auxiliary coincident fleet peak per point; carried in the cloud CSV, never scored |
| `EpsPlus` | additive $\varepsilon+$ to the universal front — written to `quality_indicators.csv` by `recompute_hv.py` (run post-campaign by `PostRunScripts`; also computed Java-side but never written by `ExperimentReporter`), **not displayed by the explorer** |

---

## 8. Where Each Number Appears in the Explorer

| Tab | Shows | Source |
|---|---|---|
| Scatter | universal front (black, connected), per-arm pooled fronts, optional per-seed clouds; "Normalize axes" rescales by the **scenario union frame — the same frame `HV_fixed` uses** (`scenario_bounds`) | graph-data + fronts CSVs |
| Metrics | one bar panel per scenario for: `HV_fixed`, `HV`, `GD`, `IGD`, `Spacing` (mean ± std), `ParetoContribution` (MEAN-row count), `CollabSharePct` (mean ± std) | metrics + collaboration CSVs |
| Runtime | `TimeMs`/1000 mean ± std, per scenario + overall | metrics CSV |
| Tables | per-seed pivot of any metric column + MEAN/STDDEV rows + universal info line; the seed-collaboration scoreboard | metrics + collaboration CSVs |
| Details | per-solution schedule: objectives, makespan, avg wait, energy, peak power, queue sizes, task→VM map — all simulated measurements | solution-details JSON |

For seed-sourced bars the explorer recomputes mean and **sample** std
(`ddof=1`) from the per-seed rows itself; the CSV MEAN/STDDEV rows are used
for the collaboration chart and shown verbatim in tables. Everything the GUI
draws is also written headlessly by `--export-all`, and the "Save for Claude"
bundle embeds the comparability notes of §3/§5 alongside the raw tables.

---

## 9. Source Map

| What | Where |
|---|---|
| Explorer (display only) | `scripts/results_explorer.py` (`METRIC_CHOICES`, `scenario_bounds`, `build_metric_figure`) |
| Fixed-reference indicators (`HV_fixed`, `EpsPlus`, contribution %) | `observer/ParetoAnalyzer.computeFixedIndicators` |
| Python oracle for the above | `scripts/recompute_hv.py` (parity: `ParetoAnalyzerParityTest`) |
| Legacy `HV`/`GD`/`IGD`/`Spacing` | `multiobjectivePerformance/PerfMet/PerformanceMetrics` via `ParetoAnalyzer.computeLegacyIndicators` |
| Universal / per-arm fronts, both contribution counts | `observer/ParetoAnalyzer` (`computeUniversalPareto`, `computeAlgorithmFront`, `paretoContributionCount`, `paretoContributionCountEps`, `analyzeSeedCollaboration`) |
| CSV schemas | `observer/ExperimentReporter` |
| Run production (fronts, `TimeMs`) | `newExperiments/CampaignRunner.doRunOne`, `simulateAllParetoSolutions` |
