# 6. Results — What the Numbers Say

This file interprets the raw metrics produced by the full factorial run
(10 seeds × 3 scenarios × 7 algorithms × 3 cap levels). Numbers below
are means across the 10 seeds unless stated otherwise. Sources:

- `reports/new/experiment_summary.csv` — uncapped run
- `reports/powerceiling/experiment_summary.csv` — capped run
- `reports/new/statistical_tests_summary.csv` — paired Wilcoxon +
  Holm-corrected p-values and Vargha–Delaney $A_{12}$
- `reports/powerceiling/feasibility_summary.csv` — fraction of each
  algorithm's front that survives each cap

## 6.1 Uncapped baseline — who wins on what

### 6.1.1 Hypervolume (HV, higher is better)

| Scenario    | 1st                        | HV    | 2nd                        | HV    | 3rd     | HV    |
|-------------|----------------------------|-------|----------------------------|-------|---------|-------|
| Balanced    | SA_Energy_Dominance         | 0.684 | GA_Energy_Dominance        | 0.633 | SPEA-II | 0.599 |
| GPU_Stress  | SA_Energy_Dominance         | 0.667 | GA_Energy_Dominance        | 0.612 | NSGA-II | 0.574 |
| CPU_Stress  | SA_Energy_Dominance         | 0.706 | GA_Energy_Dominance        | 0.700 | SPEA-II | 0.595 |

**The headline surprise.** The two *dominance-archive* variants — a
single-objective GA or SA that just offers every candidate to a
non-dominated archive on the way — beat the dedicated multi-objective
algorithms (NSGA-II, SPEA-II, AMOSA) on HV in every scenario.

Why? Because HV measured against a raw reference point
$(r_{\mathrm{WT}}, r_{\mathrm{E}})$ rewards whoever extends the front
*farthest* on either axis. The energy-dominance variants chase a
scalar (energy), which pushes them hard into the low-energy corner,
and the archive catches the handful of accidentally non-dominated
points they stumble through on the way. MOEAs instead spread budget
evenly along the front, reaching neither extreme as aggressively.

Statistical significance (Balanced, Wilcoxon + Holm): all pairwise HV
differences are significant at $p < 10^{-5}$ **except**
`NSGA-II` vs `SPEA-II` ($p_{\text{holm}} = 0.99$) and
`GA_WaitingTime_Dominance` vs `SA_WaitingTime_Dominance`
($p_{\text{holm}} = 0.99$). So the ranking above is not noise.

### 6.1.2 Front coverage (GD / IGD, lower is better)

HV hides *where* a front lives. GD and IGD expose it: they measure
distance to a reference (Global Pareto) front.

| Scenario    | GD winner | GD    | IGD winner | IGD   |
|-------------|-----------|-------|------------|-------|
| Balanced    | GA_WT_Dom | 0.011 | SPEA-II    | 0.068 |
| GPU_Stress  | SPEA-II   | 0.021 | SPEA-II    | 0.062 |
| CPU_Stress  | SPEA-II   | 0.013 | SPEA-II    | 0.070 |

Energy-dominance winners on HV (SA / GA) have GD in the 0.08–0.16
range — an order of magnitude worse than SPEA-II. That is the cost
of the corner-hunting strategy: they contribute *a few* very good
points, but the rest of their archive is well off the Global front.

SPEA-II's consistent IGD lead means the Global Pareto front is
**best covered** by SPEA-II: most points on it have a near-neighbour
in SPEA-II's output. NSGA-II trails by a hair.

### 6.1.3 Extremes (lowest WT, lowest E)

| Scenario    | Min WT (s) by             | Min E (kWh) by          |
|-------------|---------------------------|-------------------------|
| Balanced    | 4.03 (SA_WT_Dom)          | 0.226 (SA_E_Dom)        |
| GPU_Stress  | 4.65 (GA_WT_Dom)          | 0.291 (SA_E_Dom)        |
| CPU_Stress  | 4.59 (GA_WT_Dom)          | 0.163 (SA_E_Dom)        |

Lowest waiting time always comes from a **WT-dominance** variant;
lowest energy always comes from **SA_Energy_Dominance**. NSGA-II and
SPEA-II never win a corner — their optimisation pressure is spread
along the curve.

### 6.1.4 Runtime (wall-clock, ms)

| Algorithm                  | Balanced | GPU_Stress | CPU_Stress |
|----------------------------|----------|------------|------------|
| SA_WaitingTime_Dominance   | 821      | 808        | 785        |
| SA_Energy_Dominance        | 893      | 853        | 853        |
| GA_WaitingTime_Dominance   | 1 951    | 1 921      | 1 862      |
| GA_Energy_Dominance        | 1 963    | 1 944      | 1 945      |
| NSGA-II                    | 2 554    | 2 642      | 2 502      |
| SPEA-II                    | 3 145    | 3 248      | 3 147      |
| AMOSA                      | 4 048    | 4 095      | 4 142      |

Same 40 000-evaluation budget across the board, so these numbers
track *per-evaluation* cost. SA is fastest because an SA step only
mutates one solution; MOEAs run full non-dominated sorts /
strength-Pareto fitness every generation. AMOSA loses to the GAs
despite being a "simple" trajectory method because its archive
update runs on every iteration, not once per generation.

**Practical read:** if the scheduler has ~1 s per batch,
SA_Energy_Dominance is 4× faster than AMOSA and returns a
strictly-dominating front on HV. There is no budget-constrained
case where AMOSA is the right answer in these data.

## 6.2 Price of safety — how the cap degrades quality

The cap is a hard constraint. Every feasible solution under a cap is
a subset of the unconstrained search space, so HV cannot go up — the
question is how much it drops.

### 6.2.1 HV degradation (Balanced, same axes as §6.1.1)

| Algorithm                 | Uncapped | 190 kW | 120 kW | $\Delta$ at 120 kW |
|---------------------------|----------|--------|--------|--------------------|
| SA_Energy_Dominance       | 0.684    | 0.683  | 0.556  | **−19 %**          |
| GA_Energy_Dominance       | 0.633    | 0.627  | 0.414  | −35 %              |
| SPEA-II                   | 0.599    | 0.532  | 0.430  | −28 %              |
| NSGA-II                   | 0.596    | 0.518  | 0.392  | −34 %              |
| AMOSA                     | 0.418    | 0.421  | 0.198  | −53 %              |
| GA_WaitingTime_Dominance  | 0.369    | 0.216  | 0.060  | **−84 %**          |
| SA_WaitingTime_Dominance  | 0.365    | 0.168  | 0.163  | −55 %              |

**SA_Energy_Dominance is cap-robust.** At 190 kW (≈48-percentile cap)
it barely moves; even at 120 kW (≈21-percentile, stress regime) it
keeps 81 % of its uncapped HV. The reason is mechanical: its
archive is already populated with low-energy assignments, and
low-energy assignments generally have low peak power too.

**WT-dominance variants collapse under the cap.** They chase waiting
time by *spreading* load, which drives many VMs — and therefore many
hosts — into simultaneous execution. High parallelism means high
instantaneous power, which is precisely what the cap forbids.
GA_WT_Dominance at 120 kW keeps only 16 % of its uncapped HV; SA_WT
stalls at the 190 kW level and makes essentially no progress from
there.

NSGA-II / SPEA-II land in the middle — they give up ~30 % of HV at
120 kW but remain the most *balanced* choice if you need a real
front rather than corner points.

### 6.2.2 What fraction of each algorithm's uncapped front survives?

From `feasibility_summary.csv` — Balanced scenario, fraction of
uncapped-run solutions that satisfy each cap:

| Algorithm                 | @ 220 kW | @ 190 kW | @ 120 kW |
|---------------------------|----------|----------|----------|
| GA_Energy_Dominance       | 1.00     | 0.94     | 0.51     |
| SA_Energy_Dominance       | 1.00     | 0.74     | 0.36     |
| NSGA-II                   | 0.96     | 0.45     | 0.00     |
| SPEA-II                   | 0.97     | 0.44     | 0.00     |
| AMOSA                     | 0.94     | 0.04     | 0.00     |
| GA_WaitingTime_Dominance  | 0.46     | 0.00     | 0.00     |
| SA_WaitingTime_Dominance  | 0.76     | 0.00     | 0.00     |

If an operator *ran the uncapped algorithms and hoped the cap holds*:
at 120 kW, **only the two Energy-Dominance variants produce any
feasible solutions at all**. Every other uncapped algorithm hands
back a front that is entirely over the ceiling. This is the
empirical justification for running cap-aware variants rather than
post-filtering an uncapped front.

### 6.2.3 Cap-aware variants — what changes

When the same algorithms are run with Deb's constrained domination
(`_PC_` suffix), the feasibility column becomes essentially solid
ones at the target cap. E.g. Balanced at 120 kW:

| Algorithm                        | Feasibility @ 120 kW |
|----------------------------------|----------------------|
| NSGA-II_PC_120kW                 | 1.00                 |
| SPEA-II_PC_120kW                 | 1.00                 |
| AMOSA_PC_120kW                   | 1.00                 |
| SA_Energy_Dominance_PC_120kW     | 1.00                 |
| GA_Energy_Dominance_PC_120kW     | 1.00                 |
| *(uncapped counterparts)*        | 0.00 – 0.51          |

This is what the cap hook is for: moving feasibility from a lottery
to a guarantee.

## 6.3 Admission-control disaster — quantified

The `WorkloadAware_Admission_PC_{cap}` baseline is the only strategy
that applies the cap at *runtime*, per task (see
[`05-power-cap.md`](05-power-cap.md) §5.4). The numbers:

| Scenario    | WT (s), admission | WT (s), SPEA-II_PC_120 | Blow-up |
|-------------|-------------------|------------------------|---------|
| Balanced    | **148.6**         | 6.88                   | 22×     |
| GPU_Stress  | **202.6**         | 10.21                  | 20×     |
| CPU_Stress  | **202.6**         | 6.20                   | 33×     |

| Scenario    | E (kWh), admission | E (kWh), SPEA-II_PC_120 |
|-------------|--------------------|-------------------------|
| Balanced    | 1.41               | 0.38                    |
| GPU_Stress  | 1.88               | 0.49                    |
| CPU_Stress  | 1.25               | 0.29                    |

Two things are striking:

1. **Waiting time explodes by 20–33×** while energy also gets
   *worse* — because deferred tasks still have to run, they just run
   later on worse-sized VMs, keeping hosts on for longer.
2. Admission control delivers **HV = 0** against any reasonable
   reference point: every one of its points is dominated by the
   constrained MOEAs on both axes at once.

The peak-power metric in `pareto_3d_all.csv` shows admission
control's peak aggregate draw sitting around 27.6 kW — well *below*
120 kW. It is not that the strategy is cleverly using the headroom:
it is starving the datacenter by refusing to schedule. The cap is
technically respected; the workload is unserved.

This is the canonical illustration of local greedy enforcement of a
global constraint: locally every "no" looks reasonable, globally the
queue cascades. The constrained MOEAs escape the failure by planning
the whole 500-task batch at once.

See `final_experiment_results/admission_disaster.png` for the
log-scale visual.

## 6.4 Per-scenario recommendations

What the operator should pick, given objectives, cap tightness, and
how much search budget is available.

| Situation                                    | Pick                       | Why |
|----------------------------------------------|----------------------------|-----|
| No cap, want lowest energy                   | `SA_Energy_Dominance`       | Best HV and lowest E-corner, 0.9 s runtime |
| No cap, want lowest waiting time             | `SA_WT_Dominance`           | Best WT-corner, 0.8 s runtime |
| No cap, want a real front to pick from       | `SPEA-II`                   | Best IGD; consistent GD; preserves corners better than NSGA-II |
| Moderate cap (190 kW), front needed          | `SPEA-II_PC_190kW`          | HV only 10 % worse than uncapped SPEA-II, 100 % feasibility |
| Tight cap (120 kW), front needed             | `SPEA-II_PC_120kW` or `NSGA-II_PC_120kW` | Survive the cap; HV ≈ 0.43 / 0.39 in Balanced |
| Tight cap, single energy-biased schedule     | `SA_Energy_Dominance_PC_120kW` | Loses only 19 % HV vs uncapped; cheapest runtime |
| *Any* cap, runtime admission                 | **Do not use**              | 20–33× WT blow-up; HV = 0; starved datacenter |

## 6.5 Take-aways for the paper

1. The *energy-dominance* trick — run a scalar search on energy and
   let the archive catch incidental non-dominated points — is the
   highest-HV configuration at every cap level tested. That it beats
   dedicated MOEAs on HV is the single most unexpected finding.
2. The *spread-based* MOEAs (NSGA-II, SPEA-II) win on GD/IGD, i.e.
   they give the operator the most usable Pareto front to choose
   from. They are the correct pick when the operator needs *choice*,
   not a single low-energy point.
3. Deb's constrained domination turns feasibility from a coin-flip
   into a guarantee and costs 10–35 % HV at moderate caps, 20–53 %
   at the tight cap — an acceptable price for staying under a real
   power budget.
4. Runtime admission control, which is what a naïve operator might
   implement first, is catastrophic: it blows up both objectives by
   roughly an order of magnitude. The paper uses this as the
   negative control.
