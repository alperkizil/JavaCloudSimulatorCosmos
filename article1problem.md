# Article 1 — The Power-Cap Problem

Handoff for the next Claude instance. State as of 2026-08-31. The code work is
**merged** (PR #245, from `claude/powercap-solution-quality-d3w0lm`), and the campaign it
enables has **been run** — see §5 for the results.

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

Two result folders are committed under `docs/ExperimentResults/`:

| Folder | Role |
|---|---|
| `PowerCeilingWaitingTimeVsEnergy_28_08_2026_15_01_10` | **The campaign to report** — anchored P_ref tiers, constrained search |
| `PowerCeilingWaitingTimeVsEnergy_13_07_2026_14_03_03` | Superseded percentile-calibrated run. Keep: the §3 diagnosis derives from it, and it is the "before" evidence |

The explorer labels the July folder neutrally (`PC90 (18.0 kW)`) because it has no
calibration manifest, and the new one as `Cap 90% of P_ref (17.7 kW)`. Do not compare the
two folders' tier names as though they mean the same thing — they do not.

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

*History. This section diagnoses the **July** campaign; every defect below is fixed, and
§5 has the results after fixing. Kept because the diagnosis is the argument for why the
new design is what it is — and because §3.2 and §3.5 are findings in their own right.*

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

An order-of-magnitude understatement, and the 28 Aug campaign confirms it persists
(§5.5). **Still unfixed** — open item 1.

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

## 5. Results — the 28 Aug 2026 campaign

`docs/ExperimentResults/PowerCeilingWaitingTimeVsEnergy_28_08_2026_15_01_10/`
(1,050 runs: 7 base arms + 7×4 constrained, 3 scenarios, 10 seeds; 44,994 solutions).
Every figure below is from that folder, not replayed.

### 5.1 The fixes hold

**84 of 84 constrained (scenario × arm × tier) cells are 100% feasible against their own
cap; none are empty.** In July `SA_WaitingTime_Dominance_PC30` published 11 solutions,
0% of them feasible. That failure mode is gone.

P_ref reproduced the value derived from July's data *exactly* — 19,650.360 / 16,793.838 /
17,582.581 W (S1/S2/S3). Same seeds, deterministic uncapped arms, same anchor.

Reporting machinery: 14-column summary, `ScoredSeeds` = 10 throughout, no `MAX_VALUE`
leak, zero NaN indicator rows (nothing failed to find a feasible solution).

### 5.2 The cost of a power cap

Best achievable waiting time, union front over all arms:

| Scenario | uncapped | PC90 | PC85 | PC80 | PC75 |
|---|---|---|---|---|---|
| Balanced | 1.824s | 1.984s (+8.8%) | 2.054s (+12.6%) | 2.226s (+22.0%) | 2.446s (**+34.1%**) |
| GPU_Stress | 4.496s | 4.564s (+1.5%) | 4.528s (+0.7%) | 4.662s (+3.7%) | 4.760s (**+5.9%**) |
| CPU_Stress | 1.282s | 1.392s (+8.6%) | 1.514s (+18.1%) | 1.596s (+24.5%) | 1.708s (**+33.2%**) |

Monotone in all three scenarios. Energy cost is small: +0.28% to +9.31%.

**These are ~10× cheaper than the filtering-based lower bound predicted** (+360% / +147% /
+371% at PC75). Running a 25% peak reduction costs about a third more waiting time in the
worst scenario and 6% in GPU_Stress.

**The cliff does not exist.** The +274 pp jump between 0.80 and 0.75 that the filtering
estimate showed was an artifact of filtering. The ladder does not need re-tiering.

### 5.3 Constrained search vs post-hoc filtering — the nuanced part

Do not report §5.2 as "constrained search is 10× better". Per arm, **where filtering
produces anything at all**, constrained search gains only a median **+3.0%** HV and
*loses* in 25 of 57 cells. The benefit is concentrated elsewhere:

```
arm/tier cells where filtering keeps NOTHING: 27 / 84
cells under 5% feasible:                      35 / 84
```

Fraction of each arm's *uncapped* output that filtering could have kept:

| Arm | PC90 | PC85 | PC80 | PC75 |
|---|---|---|---|---|
| GA_Energy_Dominance | .966 | .862 | .631 | .463 |
| SA_Energy_Dominance | .681 | .392 | .149 | .095 |
| NSGA-II | .676 | .376 | .073 | **.000** |
| SPEA-II | .577 | .237 | .038 | **.000** |
| AMOSA | .293 | .120 | .053 | **.000** |
| SA_WaitingTime_Dominance | .072 | .061 | .032 | **.000** |
| GA_WaitingTime_Dominance | .039 | .009 | **.000** | **.000** |

The defensible claim:

> Where the unconstrained run happens to visit the feasible region, filtering is
> competitive with constrained search. In a third of cases it visits nothing at all, and
> there constrained search is the only option.

The negative gains are a finding too, not noise: for an arm already 96.6% feasible
(`GA_Energy` at PC90) the constraint is not binding, so constrained search spends budget
enforcing it and comes out slightly behind.

### 5.4 SA acceptance — resolved

At production budget (~170 temperature steps, against 6 in the smoke run):

```
SA_Energy_Dominance      uncapped 43.5%   PC90 44.0%  PC85 43.8%  PC80 43.6%  PC75 44.1%
SA_WaitingTime_Dominance uncapped 40.5%   PC90 41.5%  PC85 40.2%  PC80 39.7%  PC75 38.5%
```

Flat. Neither the collapse that harsh scaling would cause, nor the runaway the smoke run
suggested (94.2% at PC75). The relative-violation currency is correctly scaled; the smoke
figure was an artifact of a schedule that never cooled. **No fix needed.**

### 5.5 HV is still the wrong instrument

Mean ΔHV_fixed vs uncapped: **−0.8% / −5.4% / −7.0% / −9.9%** (PC90→PC75). Directionally
right, but it reports −9.9% for a tier where the best achievable waiting time moved 34%
and a third of the arms lost their entire unconstrained output. AMOSA in CPU_Stress
reports **+30.7%** at PC80 — the constraint *helped* it. See open item 1.

---

## 6. Open items — read before claiming the study is done

Closed by the 28 Aug campaign: the campaign itself, the cap-ladder cliff, and SA
acceptance at production budget. What remains:

1. **Cap-aware indicators do not exist.** This is now the top item. Global HV understates
   the effect by an order of magnitude (§3.4, §5.5) and in one cell reports the wrong
   *sign*. Needed: best waiting time subject to cap; feasible-region HV normalised to the
   *feasible* ideal; attainment at fixed energy. The campaign is sound; the headline
   metric is not, and §5.2 currently has to be computed by hand from `pareto_3d_all.csv`.

2. **Holm correction is too wide.** `scripts/statistical_tests.py` corrects across all 378
   pairwise comparisons per metric, though only the base-vs-cap contrasts are planned.
   In July, 42 of 63 had raw p<0.05 and only 30 survived. The 28 Aug significance figures
   have not been re-checked against this.

3. **§5.3 needs a proper statistical treatment.** The constrained-vs-filtering comparison
   is currently descriptive (median gain, count of empty-filter cells). It is the most
   interesting claim in the study and deserves a test with the right family.

4. **P_ref is empirical and algorithm-dependent** — it comes from the study algorithms'
   own output. A dedicated fixed latency-reference optimiser would remove the
   circularity, at the cost of a comparability question. Documented, not fixed.

5. **No CI statuses on this branch.** Absence of a red mark is not a passing signal.
   Verification is local: compiles, targeted unit-style checks, and this campaign.

**The algorithm log is committed compressed.** A full campaign writes ~123 MB of plain
text, past GitHub's 100 MB per-file limit, so it ships as `algorithm_log.zip` (~6 MB).
Unzip before reading; runs are delimited by
`===== scenario=.. algorithm=.. seed=.. =====`, so rates aggregate per arm and tier by
splitting on that line. Note `.gitignore` excludes `*.zip` globally with an exception for
`docs/ExperimentResults/**` — without that exception `git add` skips the log silently.

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
