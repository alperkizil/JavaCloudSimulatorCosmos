# Article 1 — The Power-Cap Problem

Handoff for the next Claude instance. State as of 2026-08-31, branch
`claude/powercap-solution-quality-d3w0lm`, PR #244 (draft, 8 commits, not merged).

Read `README.md` and `CLAUDE.md` first, then this file. `HANDOFF.md` describes an
independent second study (multi-DC / carbon) that is **out of scope** — see
`CLAUDE.md`.

---

## 1. Scope — the only three entry points

The article uses **exactly three** entry points, all in
`src/main/java/com/cloudsimulator/newExperiments/`:

| File | Study | Objectives |
|---|---|---|
| `MakespanEnergyExperiment.java` | Makespan vs Energy | makespan, energy |
| `WaitingTimeEnergyExperiment.java` | Avg. waiting time vs Energy (uncapped) | avg wait, energy |
| `PowerCeilingExperiment.java` | Avg. waiting time vs Energy **under a power cap** | avg wait, energy + aux peak |

**Do not use any other runner for the article.** In particular:

- `src/main/java/com/cloudsimulator/FinalExperiment/PowerCeilingWaitingTimeExperimentRunner.java`
  is the **legacy** runner with hardcoded 120/190/220 kW caps. It is superseded by
  `PowerCeilingExperiment` and must not be used or cited.
- `oldExperiments/` holds archived mains. Not for the article.

All three share one back-end: `CampaignRunner` → `ParetoAnalyzer` → `ExperimentReporter`,
with post-run Python in `scripts/`. Everything you would normally tweak (algorithm list,
hyperparameters, infrastructure) lives in the three entry-point files themselves.

---

## 2. What the article is about

The framework schedules tasks onto VMs in a simulated datacenter and compares
metaheuristics (GA, SA, NSGA-II, SPEA-II, AMOSA, plus dominance-archive GA/SA variants)
on bi-objective trade-offs. The third study adds a **datacenter power ceiling** as a
constraint and asks what respecting it costs.

The intended contribution of study 3 is a quantified answer to: *given a cap on
coincident datacenter power, how much waiting time must you give up, and do
constraint-aware metaheuristics beat simply discarding the schedules that violate the
cap?*

---

## 3. The problem we were troubleshooting

The July campaign — `docs/ExperimentResults/PowerCeilingWaitingTimeVsEnergy_13_07_2026_14_03_03/`
— ran caps labelled PC90 / PC60 / PC30 and showed **almost no difference in solution
quality versus uncapped**. That looked like a null result. It was not. It was three
separate defects compounding.

### 3.1 The caps barely constrained anything

`PowerCapCalibrator` defined a tier as a **percentile of the pooled uncapped peak
distribution**. That is self-referential: a cap at the *t*-th percentile leaves *t*% of
the search space feasible by construction, so it can never demand a reduction the arms
were not already close to making.

Measured against each arm's *own* median peak, across all 63 (scenario × arm × tier)
cells:

- **48% required no peak reduction at all** — the cap sat above that arm's median peak.
  For PC90 this was 18 of 21 cells.
- **22% more** required less than the free slack (see 3.2).
- Only **30%** could show any effect.

Two further biases: the pooled distribution weighted arms by **archive size** (the two
energy-optimising arms contributed 53% of the points and have the lowest peaks), and
pooled across scenarios, so one tier meant a different demand in each — PC30 was the
13.7th percentile in Balanced but the 37.2nd in CPU_Stress.

### 3.2 Peak power has large free slack

Holding **both** waiting time and energy fixed (binning solutions into tight (WT, E)
cells), peak power still varies by a median ~18%. You can cut the median peak by
**6.5–7.4%** (p90: 12–17%) at essentially zero objective cost.

Mechanism: peak power is about *coincidence* — which high-power workloads happen to
overlap — and re-permuting VM queue order de-synchronises them without changing average
wait or total energy. Concrete pair from Scenario 1:

```
NSGA-II_PC30         WT 6.99s  E 0.05894 kWh  peak 13,797 W
SA_Energy_Dominance  WT 6.46s  E 0.05893 kWh  peak 18,343 W   <- 25% higher, same objectives
```

The dose-response is monotone once demand exceeds that slack:

| Required peak cut | Cells | Mean ΔHV |
|---|---|---|
| cap above arm's median (0%) | 30 | −0.4% |
| 0–7% (inside free slack) | 14 | −5.5% |
| 7–15% | 11 | −13.0% |
| >15% | 8 | **−23.3%** |

So the effect was real and correctly ordered — it was just never asked for.

### 3.3 Four of seven arms never searched for feasibility

`GenerationalGAPowerCeilingAlgorithm` and `SimulatedAnnealingPowerCeilingAlgorithm`
documented their search dynamics as *identical* to the unconstrained originals, with the
violation handed to the publication archive afterward. Selection, elitism and acceptance
never saw it.

Verified in the data: at the loosest July tier, `GA_Energy_Dominance_PC90` had **99.2%
per-seed point overlap** with its uncapped twin, and 77% of runs were an **exact subset**.
Those arms were post-hoc filters, not constrained optimisers.

Worse, under a tight cap the archive fills with violators (Deb's rules retain
least-violating solutions when nothing feasible exists). `SA_WaitingTime_Dominance_PC30`
emitted 11 solutions, **0% of them feasible against its own cap**, while reporting the
best waiting time of any arm at that tier. Replaying July's peaks against the new tighter
tiers, **18 of 48 filter-only cells would have had no feasible solution at all**.

### 3.4 Global HV is close to blind here

The cap truncates the low-waiting-time tip of the front, which is a thin sliver of area:

| | HV loss | best-WT penalty |
|---|---|---|
| S2 PC30 | 2.7% | +37% |
| S3 PC60 | 0.5% | +33% |
| S3 PC30 | 1.6% | +75% |

An order-of-magnitude understatement. **This is still unfixed** (see §6).

### 3.5 Structural facts worth knowing

- 40 hosts, 60 VMs. **VM→host mapping is fixed infrastructure**, not a decision variable.
- In Scenario 1, 17 of 40 hosts are powered in 98% of solutions; `activeVmCount` is 60
  in 66%. So "how many machines you light up" is *not* the lever.
- Schedules are **work-conserving**: all tasks release at t=0, each VM runs its queue
  back-to-back, no idle insertion. Peak therefore occurs early and is driven by
  concurrency of high-power work.
- Peak correlates −0.71…−0.87 with waiting time and +0.68…+0.88 with energy: faster
  schedules draw more power. Consequently the **energy end of the front is immune** —
  best energy is identical to 6 decimal places under every cap in every scenario.
- Host idle power 75.79 W (`MeasurementBasedPowerModel.REFERENCE_IDLE_POWER`).

---

## 4. What we changed (PR #244, 8 commits)

| Commit | Change |
|---|---|
| `efcfa32` | Cap tiers anchored to **P_ref**, derived **per scenario** |
| `641f91a` | GA/SA genuinely constrained; quality scored on feasible fronts only |
| `a17f9a7` | `algorithm_log.txt` — per-run diagnostics persisted |
| `2fe9e38` | Feasibility chart: per-scenario caps; removed obsolete target-rate guides |
| `b4a4df8` | Tier labels by calibration scheme; zero-feasible runs actually NaN |
| `6e6203d` | `markUnscored` before the empty-scenario return; finite-only aggregation |
| `2eb5c23` | `bestObjective` → NaN not `Double.MAX_VALUE`; `ScoredSeeds` in summary |
| `42c9969` | `captureAlgorithmLog` defaults off; opt-in from `PowerCeilingExperiment` |

### 4.1 Cap calibration

A tier is now a fraction of **P_ref**, the coincident peak drawn by the latency-optimal
schedule. P_ref uses **one anchor per seed** — that seed's lowest-waiting-time solution
across all arms — and takes the median of those peaks. Ten seeds, ten anchors, so archive
size cannot weight the estimate.

Default tiers **90 / 85 / 80 / 75% of P_ref**. On the July data:
P_ref = **19,650 / 16,794 / 17,583 W** (S1/S2/S3).

`power_cap_calibration.csv` records scheme id (`anchored-pref-v1`), P_ref and the derived
caps, so a folder can never be silently compared against a percentile-calibrated one.

### 4.2 Constrained search

New `ConstrainedTournamentSelection` applies Deb's rules (feasible ≻ infeasible; then
lower violation; then fitness). The GA tracks violations parallel to fitness and ranks
elites by the same rule. SA accepts on the constrained comparison, with violations
entering the Metropolis exponent as a **fraction of the cap** (raw Watts against a
temperature calibrated from fitness deltas of order 1 would hard-reject everything).

The violation was already computed for the archive, so this costs **no extra objective
evaluations**.

### 4.3 Honest reporting

- Quality indicators computed on **cap-feasible solutions only**; feasibility CSVs keep
  the unfiltered runs so rates stay meaningful.
- A run with no feasible solution → **NaN** across all indicators (not HV 0 / GD 0 / IGD 0,
  which reads as *perfect*).
- `mean`/`stddev` skip non-finite values; `ScoredSeeds` reports the denominator.
- `bestObjective` returns NaN, not `Double.MAX_VALUE` (a *finite* double that would
  otherwise be averaged in as ~1.8e308).

### 4.4 Cross-study impact (important)

Some of the above is in the shared reporting path, so it touches **all three studies**:

- `experiment_summary.csv` 13→14 columns, `scenario_N_performance_metrics.csv` 11→12
  (both gain `ScoredSeeds`). In-repo Python reads by column name, so it is transparent
  there; anything reading positionally breaks.
- Failure semantics (NaN instead of 0 / `MAX_VALUE`) apply everywhere. They do not fire
  on healthy runs.

**Numbers are unchanged.** Verified by A/B: same reduced `WaitingTimeEnergyExperiment`
campaign built from `origin/main` and from HEAD; only `TimeMs` (wall clock) differed.

---

## 5. What we are trying to achieve

Produce a defensible study 3 for the article showing:

1. **The cost of a power cap** — a monotone waiting-time penalty curve as the cap tightens.
2. **Whether constrained search beats post-hoc filtering.** In July the gap was a median
   0.0–0.8% HV. With genuine constrained search and tight caps it should open up. If it
   does not, that is itself the more interesting finding.
3. **The free-slack result** — peak shaving is nearly free up to ~7% and cheap to ~13%,
   after which it costs waiting time steeply. This is arguably a better headline than
   "caps degrade quality".

Replayed against July's uncapped cloud, the new tiers should give (best-WT penalty; a
**lower bound**, since this is filtering rather than constrained search):

| Scenario | PC90 | PC85 | PC80 | PC75 |
|---|---|---|---|---|
| Balanced | +52% | +75% | +86% | +360% |
| GPU_Stress | +30% | +41% | +65% | +147% |
| CPU_Stress | +39% | +68% | +118% | +371% |

---

## 6. Open items — read before claiming the study is done

1. **No full campaign has been run.** Every number in this document is either replayed
   July data or a reduced-scale smoke run. A full campaign is ~3.2 h serial (~9 s/run).
   **This is the single most important next step.**

2. **Cap-aware indicators do not exist.** Global HV understates the effect by an order of
   magnitude (§3.4). Needed: best waiting time subject to cap; feasible-region HV
   normalised to the *feasible* ideal; attainment at fixed energy. Without these the
   analysis will not tell the story even though the algorithms are now correct.

3. **Holm correction is too wide.** `scripts/statistical_tests.py` corrects across all 378
   pairwise comparisons per metric, though only the base-vs-cap contrasts are planned.
   42 of 63 had raw p<0.05; only 30 survived.

4. **The cap ladder is unsampled at the cliff.** Steps between tiers are wildly uneven:

   ```
   0.90 → 0.85:  +23 / +11 / +28 pp     (S1/S2/S3)
   0.85 → 0.80:  +12 / +24 / +49 pp
   0.80 → 0.75: +274 / +78 / +255 pp    <- ~10x the others
   ```

   The transition actually sits between 0.775 (+145%) and 0.750 (+360%) in S1, and
   nothing samples it. Consider adding a tier at 77–78%. **But** these are filtering
   estimates — genuine constrained search may soften or move the cliff, so the defensible
   order is: run the campaign, see where the real cliff lands, then re-tier.

5. **SA acceptance at production budget is unverified.** The smoke run showed acceptance
   *rising* with cap tightness (uncapped 72.9% → PC75 94.2%), i.e. the relative-violation
   scaling errs permissive, not harsh. But that was 400 evaluations over 6 temperature
   steps where the schedule barely cools. Check the run's algorithm log. If it is still
   ~90%+ in the infeasible region, scale violation deltas by an observed violation range
   rather than by the cap (mirroring `ObjectiveScaleNormalizer`).

   **The log is committed compressed.** A full campaign writes ~116 MB of plain text,
   past GitHub's 100 MB per-file limit, so `algorithm_log.txt` is stored as
   `algorithm_log.zip` in the result folder. Unzip it before reading; each run is
   delimited by a `===== scenario=.. algorithm=.. seed=.. =====` header, so acceptance
   rates can be aggregated per arm and tier by splitting on that line.

6. **P_ref is still empirical and algorithm-dependent** — it comes from the study
   algorithms' own output. A dedicated fixed latency-reference optimiser would remove the
   circularity, at the cost of a comparability question. Documented, not fixed.

7. **No CI statuses on this branch.** Absence of a red mark is not a passing signal. All
   verification so far is local: compiles, targeted unit-style checks, reduced campaigns.

---

## 7. Running it

```bash
# compile (no Maven — network restricted)
find src/main/java -name "*.java" -not -path "*/gui/*" | xargs javac -cp "lib/*" -d target/classes

# run a study
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.PowerCeilingExperiment
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.WaitingTimeEnergyExperiment
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.MakespanEnergyExperiment
```

Output lands in `results/<experimentId>/`. Post-run Python (`scripts/`) runs
automatically; it needs `pandas`, `numpy`, `scipy`, `matplotlib`.

`PowerCeilingExperiment` is two-phase: Phase 1 runs the 7 base arms uncapped and derives
each scenario's caps; Phase 2 re-runs each arm constrained under every tier.

---

## 8. Note on the review history

PR #244 went through six external review rounds (ChatGPT/Codex). Every round found
something real, and the defect class narrowed each time — from "the algorithms don't
implement the constraint" to "a sentinel value in one export column". Two findings were
mine to own specifically: **twice I wrote commit-message claims about behaviour the code
did not have** ("reported as NaN", "means are conditional"), and both times the reviewer
caught it by reading the downstream code path rather than the changed lines.

If you make a claim about behaviour in a commit message or the PR, trace it one level
further out than the lines you changed, and verify it.
