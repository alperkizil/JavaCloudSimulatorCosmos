# 17 Sep 2026 campaign re-runs — comparison with their predecessors

Three article-1 result folders dated 17 Sep 2026 were compared with the previous
folder of the same study under `docs/ExperimentResults/`:

| Study | Previous folder | New folder |
|---|---|---|
| Makespan vs Energy | `MakespanVsEnergy_13_07_2026_14_02_48` | `MakespanVsEnergy_17_09_2026_09_50_39` |
| Waiting time vs Energy | `WaitingTimeVsEnergy_13_07_2026_14_02_51` | `WaitingTimeVsEnergy_17_09_2026_10_30_00` |
| Power ceiling | `PowerCeilingWaitingTimeVsEnergy_31_08_2026_16_26_57` | `PowerCeilingWaitingTimeVsEnergy_17_09_2026_10_56_32` |

## 1. Summary

Exactly one code change separates the runs: commit `5923255`
*"Temperature-scaled mutation for AMOSA"* (17 Sep 2026). It scales AMOSA's
per-task mutation rate by T/T0 and takes a single `mutateSingle` step once the
expected number of mutated tasks is at or below one. Both AMOSA arms
(`FixedAMOSA`, `FixedAMOSAConstrained`) get the same rule.

Consequences in the data:

- **Every non-AMOSA per-seed result is byte-identical** to its predecessor in all
  three studies (HV, Spacing, NonDomSolutions, best objectives). Same seeds,
  deterministic arms.
- **Only AMOSA moved, and it moved in its favour** in every scenario of every study.
- **The article's headline numbers did not move.** Best achievable waiting time and
  best energy on the union feasible front are identical in all 15 (scenario × tier)
  cells of the power-ceiling study.
- Wall-clock (`TimeMs`) differences are machine noise.
- The summary CSVs of the two July studies gained the `ScoredSeeds` column
  (13 → 14 columns) introduced by PR #244. `ScoredSeeds` = 10 throughout.

## 2. AMOSA, uncapped arm — old → new

Mean over 10 seeds of the `experiment_summary.csv` MEAN rows.

### Makespan vs Energy

| Sc | HV | ΔHV | IGD | best makespan | best energy | archive size | ms |
|---|---|---|---|---|---|---|---|
| 1 Balanced | 0.0265 → 0.1672 | +530% | 0.667 → 0.535 | 17.99 → 17.92 | 0.06097 → 0.05745 | 6.0 → 7.9 | 7276 → 3578 |
| 2 GPU_Stress | 0.2349 → 0.2804 | +19% | 0.393 → 0.326 | 26.20 → 25.30 | 0.07892 → 0.07639 | 6.0 → 10.5 | 7104 → 5042 |
| 3 CPU_Stress | 0.1631 → 0.2718 | +67% | 0.478 → 0.378 | 12.39 → 11.75 | 0.04086 → 0.03822 | 13.3 → 11.2 | 7203 → 4710 |

### Waiting time vs Energy

| Sc | HV | ΔHV | IGD | best wait (s) | best energy | archive size | ms |
|---|---|---|---|---|---|---|---|
| 1 Balanced | 0.2421 → 0.3675 | +52% | 0.439 → 0.364 | 3.036 → 2.586 | 0.06027 → 0.05807 | 20.8 → 48.1 | 6612 → 3894 |
| 2 GPU_Stress | 0.2706 → 0.3920 | +45% | 0.359 → 0.287 | 6.936 → 6.036 | 0.07825 → 0.07524 | 15.3 → 33.9 | 9355 → 5353 |
| 3 CPU_Stress | 0.2141 → 0.3269 | +53% | 0.463 → 0.417 | 1.778 → 1.666 | 0.04326 → 0.04086 | 24.9 → 46.5 | 6757 → 4806 |

The power-ceiling Phase-1 (uncapped) AMOSA rows reproduce the waiting-time study
exactly, as they should.

### Ranking effect

Fixed-reference HV (`quality_indicators_all_scenarios.csv`, `HV_fixed`) ordering of
the seven base arms:

- **Waiting time study**: AMOSA stays **5th of 7** in all three scenarios
  (behind GA_Energy, NSGA-II, SA_Energy, SPEA-II; ahead of the two waiting-time
  dominance arms). Same in the power-ceiling study.
- **Makespan study**: AMOSA stays **6th of 7** in scenarios 2 and 3; in scenario 1
  it rises from last to 6th, overtaking `GA_Makespan_Dominance`.

Significant pairwise HV results involving AMOSA are unchanged in both July
studies (Makespan: 1 win / 5 losses; WaitingTime: 2 wins / 4 losses).

## 3. AMOSA, constrained arms (power-ceiling study) — old → new

### The motivating failure is gone

Feasibility of each constrained AMOSA arm against **its own cap**
(`feasibility_summary.csv`; solutions pooled over 10 seeds):

| Sc | Arm | own cap (W) | feasible rate | solutions | feasible |
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

In the 31 Aug run `AMOSA_PC50` had no feasible solution on any GPU_Stress seed
(10 NaN-HV seeds) and on 8 of 10 Balanced seeds. Today **no AMOSA seed is NaN**.
The under-100 % cells that remain (GA, NSGA-II, SPEA-II at PC50) are unchanged.

### Quality under the cap

Mean HV (`experiment_summary.csv`) and best waiting time of the constrained arms:

| Sc | Arm | HV | ΔHV | best wait (s) |
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
| 2 | AMOSA_PC50 | NaN → 0.240 | — | NaN → 10.640 |
| 3 | AMOSA_PC90 | 0.239 → 0.342 | +43% | 1.812 → 1.684 |
| 3 | AMOSA_PC80 | 0.320 → 0.394 | +23% | 2.430 → 2.064 |
| 3 | AMOSA_PC70 | 0.306 → 0.400 | +31% | 2.790 → 2.400 |
| 3 | AMOSA_PC60 | 0.223 → 0.376 | +68% | 3.558 → 2.762 |
| 3 | AMOSA_PC50 | 0.153 → 0.351 | +130% | 5.208 → 3.576 |

The gain grows as the cap tightens, which is the mechanism the commit message
describes: small late moves let the search descend on peak power one task at a
time instead of scrambling the coincidence pattern.

`AMOSA_PC90` now contributes to the union front (Pareto contribution 0 → 19 in
Balanced, 0 → 14 in CPU_Stress).

## 4. The article's headline table is unchanged

Best waiting time on the union feasible front per tier
(`scenario_N_universal_fronts_by_cap.csv`), old and new:

| Scenario | Uncapped | PC90 | PC80 | PC70 | PC60 | PC50 |
|---|---|---|---|---|---|---|
| Balanced | 1.824 | 1.984 (+8.8%) | 2.226 (+22.0%) | 2.690 (+47.5%) | 3.246 (+78.0%) | 4.158 (+128.0%) |
| GPU_Stress | 4.496 | 4.564 (+1.5%) | 4.662 (+3.7%) | 5.146 (+14.5%) | 6.152 (+36.8%) | 7.658 (+70.3%) |
| CPU_Stress | 1.282 | 1.392 (+8.6%) | 1.596 (+24.5%) | 1.830 (+42.7%) | 2.170 (+69.3%) | 2.818 (+119.8%) |

Identical to three decimals in every cell; best energy per tier is identical too.
The new AMOSA enlarged several union fronts (Balanced PC50: 128 → 166 points;
GPU_Stress PC50: 70 → 109) without moving their extremes.

`power_cap_calibration.csv` is identical: scheme `anchored-pref-v1`,
P_ref = 19,650.360 / 16,793.838 / 17,582.581 W, same five tiers.

## 5. Side effects to be aware of

1. **GD and IGD shifted slightly for every arm in all three studies.** The
   reference front is the union across arms, and AMOSA now contributes to it.
   HV, Spacing, NonDomSolutions and best objectives of the non-AMOSA arms are
   unchanged.
2. **Makespan scenario 2 reference point moved** (energy nadir
   0.084971 → 0.084829), so every arm's `HV_fixed` in that scenario changed by a
   small amount. The other five reference points (Makespan sc1/sc3, all
   WaitingTime and PowerCeiling scenarios) are identical.
3. **Uncapped AMOSA now visits fewer cap-feasible points at loose tiers.** It
   optimises harder toward low waiting time, hence higher peaks:
   Balanced PC90 27.2 % → 25.6 %, GPU_Stress PC90 11.9 % → 0.6 %,
   CPU_Stress PC80 14.9 % → 0.0 %, CPU_Stress PC90 48.5 % → 28.2 %.
   The "fraction filtering could keep" table in `article1problem.md` §5.3 must be
   regenerated if AMOSA is reported there.
4. **Pareto contribution of a few non-AMOSA rows changed** (SPEA-II, NSGA-II,
   GA_WaitingTime_Dominance_PC80) because the union front they are scored
   against now contains AMOSA points. Their own fronts are unchanged.

## 6. Statistical-test file: a reporting artifact, not a data difference

`statistical_tests_summary.csv` of the power-ceiling study:

| Folder | rows | `N_pairs` | significant at 0.05 |
|---|---|---|---|
| 28 Aug | 1785 | 36 | 1313 |
| 31 Aug | 2583 | 16 | **0** |
| 17 Sep | 2583 | 23 | 1686 |

The 31 Aug "zero significant" result is not a property of the data.
`scripts/statistical_tests.py` builds one pivot over all 42 arms and drops any
(scenario, seed) row with a missing value anywhere (`pivot.dropna(how='any')`).
The 31 Aug run's NaN `AMOSA_PC50` seeds cut the paired sample to 16 rows, and
Holm over 861 pairwise comparisons then rejects everything (min `p_holm` = 0.415).
Today's run keeps 23 rows and 1686 comparisons survive.

Neither figure uses the right family for the planned base-vs-cap contrasts
(open item 2 in `article1problem.md`), and the three power-ceiling folders are
**not comparable** on significance because their `N_pairs` differ. In the two
July studies (no NaN rows, `N_pairs` = 30) the only significance flips are on GD
(3–4 per study) and one IGD pair, all involving AMOSA or the moved reference front.

## 7. Housekeeping

- The 31 Aug scenario-2 mean `TimeMs` of 188 s is two seeds with wall-clock
  values of 5.8 h (`AMOSA_PC50` seed 209) and 15.5 h (`SPEA-II_PC50` seed 207),
  both with zero solutions and metrics identical to today's. This looks like a
  sleeping machine, not compute. Today's scenario-2 mean is 8.6 s.
- AMOSA runtime fell 25–40 % in the uncapped studies (smaller moves are cheaper
  to evaluate); constrained-arm runtimes are within ±20 %.
- A stray 4-byte `res` file present in the 31 Aug folder and in
  `docs/ExperimentResults/` root was not carried into today's folder.
- File sets are otherwise identical per study (32 / 32 / 64 files). The
  power-ceiling folder ships `algorithm_log.zip` (7.4 MB, 141 MB uncompressed).

## 8. Method

All figures come from the committed CSVs (`experiment_summary.csv`,
`feasibility_summary.csv`, `quality_indicators_all_scenarios.csv`,
`scenario_N_universal_fronts_by_cap.csv`, `statistical_tests_summary.csv`,
`power_cap_calibration.csv`), joined on (Scenario, Algorithm, Seed) and compared
with an absolute tolerance of 1e-9. Nothing was re-run.
