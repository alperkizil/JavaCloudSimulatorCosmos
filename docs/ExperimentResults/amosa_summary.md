# What changed in the 17 Sep 2026 experiment runs

On 17 Sep 2026 the three article-1 experiments were run again. This note compares
each new result folder with the previous folder of the same experiment.

| Experiment | Previous folder | New folder |
|---|---|---|
| Makespan vs Energy | `MakespanVsEnergy_13_07_2026_14_02_48` | `MakespanVsEnergy_17_09_2026_09_50_39` |
| Waiting time vs Energy | `WaitingTimeVsEnergy_13_07_2026_14_02_51` | `WaitingTimeVsEnergy_17_09_2026_10_30_00` |
| Waiting time vs Energy under a power cap | `PowerCeilingWaitingTimeVsEnergy_31_08_2026_16_26_57` | `PowerCeilingWaitingTimeVsEnergy_17_09_2026_10_56_32` |

## 1. The short version

- Between the old and the new runs, **only one piece of code changed**: the AMOSA
  algorithm got a new mutation rule (commit `5923255`, "Temperature-scaled mutation
  for AMOSA", 17 Sep 2026).
- Because of that, **only AMOSA's results changed**. Every other algorithm produced
  exactly the same numbers as before, seed for seed.
- **AMOSA got better everywhere.** In every scenario of every experiment it now finds
  a larger and better set of schedules, and it runs faster.
- **The main result of the power-cap study did not move.** The "how much waiting
  time does a cap cost" table is the same to three decimal places.
- Run times differ, but that is just the computer being faster or slower on the day.
  It is not a result.

## 2. What the code change does

AMOSA is a simulated-annealing style algorithm. Each step it "mutates" the current
schedule: it moves some tasks to other machines or swaps their order. Before the
change, every step touched about 25 tasks at once, no matter how far the search had
progressed. That is a big jump. Near the end of a run, when the search should be
fine-tuning, a 25-task jump destroys whatever it had found.

After the change, the number of touched tasks shrinks as the "temperature" drops.
Early on it still makes big jumps. Late in the run it moves a single task at a time.
This is how the plain SA algorithm in the same study already worked.

This matters most under a power cap. A schedule that is slightly over the cap is
usually one or two tasks away from being under it. A one-task move can fix that.
A 25-task move cannot.

## 3. How to read the numbers below

- **HV (hypervolume)**: how much of the objective space the algorithm's schedules
  cover. Higher is better. It is the main quality score in the article.
- **IGD**: how far the algorithm's schedules are from the best schedules found by
  anyone. Lower is better.
- **Best wait / best makespan / best energy**: the single best value the algorithm
  found for that objective.
- **Archive size**: how many non-dominated schedules the algorithm ended with. More is
  usually a sign of a richer trade-off curve.
- **Feasible**: under a power cap, a schedule is feasible if its peak power stays
  under the cap.
- All values are averages over the 10 seeds unless stated otherwise.

## 4. AMOSA without a power cap — old → new

### Makespan vs Energy

| Scenario | HV | change | IGD | best makespan | best energy | archive size | run time (ms) |
|---|---|---|---|---|---|---|---|
| 1 Balanced | 0.027 → 0.167 | +530% | 0.667 → 0.535 | 17.99 → 17.92 | 0.06097 → 0.05745 | 6.0 → 7.9 | 7276 → 3578 |
| 2 GPU_Stress | 0.235 → 0.280 | +19% | 0.393 → 0.326 | 26.20 → 25.30 | 0.07892 → 0.07639 | 6.0 → 10.5 | 7104 → 5042 |
| 3 CPU_Stress | 0.163 → 0.272 | +67% | 0.478 → 0.378 | 12.39 → 11.75 | 0.04086 → 0.03822 | 13.3 → 11.2 | 7203 → 4710 |

### Waiting time vs Energy

| Scenario | HV | change | IGD | best wait (s) | best energy | archive size | run time (ms) |
|---|---|---|---|---|---|---|---|
| 1 Balanced | 0.242 → 0.368 | +52% | 0.439 → 0.364 | 3.036 → 2.586 | 0.06027 → 0.05807 | 20.8 → 48.1 | 6612 → 3894 |
| 2 GPU_Stress | 0.271 → 0.392 | +45% | 0.359 → 0.287 | 6.936 → 6.036 | 0.07825 → 0.07524 | 15.3 → 33.9 | 9355 → 5353 |
| 3 CPU_Stress | 0.214 → 0.327 | +53% | 0.463 → 0.417 | 1.778 → 1.666 | 0.04326 → 0.04086 | 24.9 → 46.5 | 6757 → 4806 |

Every column improved in every scenario. AMOSA also runs 25 to 40 percent faster,
because a smaller move is cheaper to evaluate.

The uncapped AMOSA rows in the power-cap study are the same as the waiting-time
study rows above, as they should be (same seeds, same code).

### Does AMOSA's place in the ranking change?

Mostly no. Ranking the seven algorithms by HV against a fixed reference point:

- **Waiting time study**: AMOSA stays **5th of 7** in all three scenarios. It is still
  behind GA_Energy, NSGA-II, SA_Energy and SPEA-II, and still ahead of the two
  waiting-time-first algorithms.
- **Makespan study**: AMOSA stays **6th of 7** in scenarios 2 and 3. In scenario 1 it
  moves up from last place to 6th, overtaking GA_Makespan_Dominance.

The list of statistically significant head-to-head HV results involving AMOSA is
the same as before in both studies.

## 5. AMOSA under a power cap — old → new

### The failure that motivated the change is fixed

In the 31 Aug run, `AMOSA_PC50` (AMOSA with the cap set to 50% of the reference
peak) could not find a single feasible schedule on any GPU_Stress seed, and only
on 2 of 10 Balanced seeds. Today **every AMOSA arm is 100% feasible against its own
cap on every seed**, and each arm produces 3 to 30 times more schedules.

| Scenario | Arm | own cap (W) | share feasible | schedules found | feasible schedules |
|---|---|---|---|---|---|
| 1 | AMOSA_PC50 | 9825 | 0.20 → **1.00** | 17 → 349 | 9 → 349 |
| 1 | AMOSA_PC60 | 11790 | 1.00 → 1.00 | 79 → 425 | 79 → 425 |
| 1 | AMOSA_PC70 | 13755 | 1.00 → 1.00 | 181 → 430 | 181 → 430 |
| 1 | AMOSA_PC80 | 15720 | 1.00 → 1.00 | 150 → 390 | 150 → 390 |
| 1 | AMOSA_PC90 | 17685 | 1.00 → 1.00 | 256 → 416 | 256 → 416 |
| 2 | AMOSA_PC50 | 8397 | **0.00 → 1.00** | 11 → 315 | 0 → 315 |
| 2 | AMOSA_PC60 | 10076 | 0.90 → 1.00 | 70 → 389 | 69 → 389 |
| 2 | AMOSA_PC70 | 11756 | 1.00 → 1.00 | 134 → 463 | 134 → 463 |
| 2 | AMOSA_PC80 | 13435 | 1.00 → 1.00 | 265 → 436 | 265 → 436 |
| 2 | AMOSA_PC90 | 15114 | 1.00 → 1.00 | 149 → 391 | 149 → 391 |
| 3 | AMOSA_PC50 | 8791 | 1.00 → 1.00 | 54 → 323 | 54 → 323 |
| 3 | AMOSA_PC60 | 10550 | 1.00 → 1.00 | 121 → 409 | 121 → 409 |
| 3 | AMOSA_PC70 | 12308 | 1.00 → 1.00 | 250 → 505 | 250 → 505 |
| 3 | AMOSA_PC80 | 14066 | 1.00 → 1.00 | 276 → 507 | 276 → 507 |
| 3 | AMOSA_PC90 | 15824 | 1.00 → 1.00 | 265 → 466 | 265 → 466 |

("Schedules found" and "feasible schedules" are totals across the 10 seeds.)

The other algorithms that were not fully feasible at the 50% cap (GA, NSGA-II,
SPEA-II) are exactly as before. Only AMOSA changed.

### Quality under the cap

| Scenario | Arm | HV | change | best wait (s) |
|---|---|---|---|---|
| 1 | AMOSA_PC90 | 0.260 → 0.363 | +40% | 3.048 → 2.566 |
| 1 | AMOSA_PC80 | 0.201 → 0.349 | +74% | 3.176 → 2.672 |
| 1 | AMOSA_PC70 | 0.197 → 0.320 | +63% | 4.746 → 3.540 |
| 1 | AMOSA_PC60 | 0.122 → 0.297 | +143% | 6.456 → 4.472 |
| 1 | AMOSA_PC50 | 0.105 → 0.271 | +160% | 9.880 → 5.668 |
| 2 | AMOSA_PC90 | 0.270 → 0.396 | +47% | 7.182 → 6.208 |
| 2 | AMOSA_PC80 | 0.269 → 0.370 | +37% | 7.704 → 6.524 |
| 2 | AMOSA_PC70 | 0.139 → 0.293 | +111% | 11.184 → 7.544 |
| 2 | AMOSA_PC60 | 0.076 → 0.273 | +259% | 14.760 → 8.784 |
| 2 | AMOSA_PC50 | none → 0.240 | — | none → 10.640 |
| 3 | AMOSA_PC90 | 0.239 → 0.342 | +43% | 1.812 → 1.684 |
| 3 | AMOSA_PC80 | 0.320 → 0.394 | +23% | 2.430 → 2.064 |
| 3 | AMOSA_PC70 | 0.306 → 0.400 | +31% | 2.790 → 2.400 |
| 3 | AMOSA_PC60 | 0.223 → 0.376 | +68% | 3.558 → 2.762 |
| 3 | AMOSA_PC50 | 0.153 → 0.351 | +130% | 5.208 → 3.576 |

The tighter the cap, the bigger the gain. That fits the explanation in section 2:
small late moves let the search creep under the cap one task at a time.

`AMOSA_PC90` now contributes schedules to the combined best front (19 in Balanced,
14 in CPU_Stress). Before, it contributed none.

## 6. The article's main table is unchanged

The article reports the best waiting time reachable under each cap, taking the best
schedule from **any** algorithm. That table is identical before and after:

| Scenario | Uncapped | PC90 | PC80 | PC70 | PC60 | PC50 |
|---|---|---|---|---|---|---|
| Balanced | 1.824 | 1.984 (+8.8%) | 2.226 (+22.0%) | 2.690 (+47.5%) | 3.246 (+78.0%) | 4.158 (+128.0%) |
| GPU_Stress | 4.496 | 4.564 (+1.5%) | 4.662 (+3.7%) | 5.146 (+14.5%) | 6.152 (+36.8%) | 7.658 (+70.3%) |
| CPU_Stress | 1.282 | 1.392 (+8.6%) | 1.596 (+24.5%) | 1.830 (+42.7%) | 2.170 (+69.3%) | 2.818 (+119.8%) |

The best energy per cap is also identical. The improved AMOSA added points to the
middle of several combined fronts (Balanced PC50 grew from 128 to 166 points) but
never beat the best point at either end.

The cap values themselves are identical. `power_cap_calibration.csv` in both folders
uses scheme `anchored-pref-v1` with reference peaks of 19,650 / 16,794 / 17,583 W.

## 7. Small side effects to know about

1. **GD and IGD moved a little for every algorithm, in all three studies.** These two
   scores measure distance to the combined best front of all algorithms. AMOSA now
   adds points to that front, so everyone's distance changed slightly. Their own
   schedules did not change.
2. **One reference point moved.** In the Makespan study, scenario 2, the worst-case
   energy used as the HV reference changed from 0.084971 to 0.084829. Every
   algorithm's fixed-reference HV in that one scenario shifted slightly as a result.
   The other five reference points are identical.
3. **Uncapped AMOSA now produces fewer schedules that happen to sit under a loose cap.**
   It pushes harder toward low waiting time, and low waiting time means high peak
   power. For example, the share of its uncapped schedules under the 80% cap in
   CPU_Stress fell from 15% to 0%, and under the 90% cap in GPU_Stress from 12% to
   0.6%. If the "how much could simple filtering keep" table in
   `article1problem.md` section 5.3 includes AMOSA, it needs to be regenerated.
4. **A few "Pareto contribution" counts for other algorithms changed** (SPEA-II,
   NSGA-II, GA_WaitingTime_Dominance_PC80). That count says how many of an
   algorithm's points made it into the combined front. AMOSA now competes for those
   places. Again, their own schedules are unchanged.

## 8. A warning about the statistics file

The file `statistical_tests_summary.csv` in the 31 Aug power-cap folder says that
**none** of its 2583 comparisons are statistically significant. Today's folder says
1686 are. This looks alarming but it is a quirk of the script, not a real difference.

| Folder | comparisons | seeds used per test | significant at 5% |
|---|---|---|---|
| 28 Aug | 1785 | 36 | 1313 |
| 31 Aug | 2583 | 16 | **0** |
| 17 Sep | 2583 | 23 | 1686 |

What happens: the script lines up all 42 algorithm arms side by side, one row per
(scenario, seed). If **any** arm has no value in a row, the whole row is thrown away.
In the 31 Aug run, `AMOSA_PC50` had no feasible schedule on many seeds, so it had
no HV there. That deleted so many rows that only 16 were left. Then the script
applies a very strict correction for running 861 tests at once (Holm). With 16
rows, nothing can pass it. Today the AMOSA gaps are gone, 23 rows survive, and many
results pass.

Two consequences:

- The significance columns of the three power-cap folders **cannot be compared with
  each other**, because each used a different number of rows.
- This is the same weakness already listed as open item 2 in `article1problem.md`:
  the test should only compare the pairs the article actually needs, not all 861.

In the two July studies there are no missing values, all 30 rows are used, and the
only significance changes are 3 to 4 GD results and one IGD result per study. All
of them involve AMOSA or the moved combined front.

## 9. Housekeeping notes

- The old power-cap run shows an average run time of 188 seconds in scenario 2. That
  comes from two seeds that took 5.8 hours (`AMOSA_PC50` seed 209) and 15.5 hours
  (`SPEA-II_PC50` seed 207). Both found zero schedules, and their results are the
  same as today's. This looks like the computer went to sleep during the run, not
  like real work. Today's average is 8.6 seconds.
- A stray 4-byte file called `res` sits in the 31 Aug folder and in
  `docs/ExperimentResults/` itself. It was not copied into today's folder.
- Apart from that, each new folder has the same set of files as its predecessor
  (32 / 32 / 64 files). The power-cap folder ships `algorithm_log.zip` (7.4 MB,
  141 MB unzipped).
- The two July studies gained one column, `ScoredSeeds`, in their summary CSVs
  (13 → 14 columns). This was introduced by PR #244 and is 10 everywhere.

## 10. How this comparison was made

All numbers come straight from the committed CSV files (`experiment_summary.csv`,
`feasibility_summary.csv`, `quality_indicators_all_scenarios.csv`,
`scenario_N_universal_fronts_by_cap.csv`, `statistical_tests_summary.csv`,
`power_cap_calibration.csv`). Rows were matched on scenario, algorithm and seed and
compared with a tolerance of one billionth. Nothing was re-run.
