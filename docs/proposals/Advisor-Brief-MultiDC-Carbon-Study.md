# Carbon- and Peak-Power-Aware VM Scheduling with Migration across Geo-Distributed Datacenters — Advisor Brief

**Working paper title:** *Joint carbon- and peak-power-aware scheduling and VM migration in
geo-distributed datacenters: a collaborative multi-objective metaheuristic study with
real-world carbon-intensity traces.*

Condensed overview for advisor review (August 2026). Full specification, with every
design decision and its rationale, lives in
`MultiDC-Carbon-PeakPower-Migration-Proposal.md` (v5) in this folder. This is an
independent second study on the simulation framework validated in our first
(single-datacenter, power-capped) study; it reuses the simulator, the
measurement-based power model, and the fairness methodology — not the results.

---

## 1. Problem and background

The carbon cost of one kWh of electricity differs by country and by hour — Finland on a
windy night emits a fraction of what Poland emits at peak. Consequently, **the same
computing job causes different CO₂ depending on where and when it runs.** A cloud
operator with datacenters in several countries has three levers: initial placement of
virtual machines, execution timing/ordering of tasks, and live VM migration between
sites while work is running (at a real cost: downtime, transfer energy, network energy).

The literature covers the pieces but not the coupling:

- **Temporal shifting** (Google's Carbon-Intelligent Computing; "Let's Wait Awhile",
  Middleware'21): policy-based, single mechanism, no migration, no capacity limits.
- **Skeptical measurement** (Sukprasert et al., EuroSys'24): with fixed policies and
  *unconstrained* capacity, chasing the hourly-cleanest region barely beats good fixed
  placement. We reproduce their pessimism on their own published data — as our
  starting point.
- **"Green" multi-objective metaheuristics:** almost universally use a *static* carbon
  intensity, which makes the carbon objective a rescaled copy of energy. With real
  hourly traces the two decouple; measuring that decoupling is itself a contribution.

**Gap:** no study jointly optimizes placement, scheduling, and migration under
time-varying carbon intensity **and** per-datacenter peak-power caps, mapping the
carbon–SLA trade-off as a Pareto frontier under a fairness-controlled multi-algorithm
protocol. Our pre-analysis (§3) shows the two ingredients interact strongly: caps are
precisely what makes migration worth having.

The study is **offline/clairvoyant** (all releases and traces known at t=0), like our
first paper: it measures the attainable *potential*. Section 8 measures how much of
that potential survives without clairvoyance.

## 2. Source data

All drive signals come from the public artifact of the EuroSys'24 paper (Sukprasert
et al.), Zenodo DOI [10.5281/zenodo.10790855](https://doi.org/10.5281/zenodo.10790855)
(CC-BY-4.0; carbon data originally from Electricity Maps):

| File (in artifact) | Content | Role in this study |
|---|---|---|
| `shared_data/combined_carbon.csv` | Hourly average carbon intensity, 123 grid zones, 2020–2022, gCO₂/kWh, UTC | The drive signal (2022 slice). Also source of the static-CI baseline (per-zone 2022 annual means) |
| `gcp_latency_matrix.csv` | Measured RTTs between Google Cloud regions | Real inter-datacenter network distances for the migration model |
| `gcp_dc_zonecode_mapper.json` | GCP region → grid-zone mapping | Defines the 24 zones eligible for scenario selection |
| Azure/Google cluster traces (bundled, v2) | Production workload arrival patterns | Shape of the diurnal task-release profile only |

Honest-parameter policy: RTTs are measured; **effective inter-DC bandwidth is an
assumed, RTT-tiered parameter** (the matrix contains no throughput) and **per-GB WAN
energy is a literature constant** — both sensitivity-swept and labeled as assumptions.
Host power comes from our own wall-plug measurement campaign (first study). We commit
to the repo only the twelve 2022 zone-columns actually used, with a provenance README;
the full artifact stays a cited download.

## 3. The simulated worlds, and why these

Three 4-datacenter scenarios, selected **by measurement, not intuition**, using the
committed script `scripts/proposal_trace_preanalysis.py` against
`combined_carbon.csv` (2022). Selection metrics: *headroom* = CO₂ saving of an oracle
running each hour in the hourly-cleanest zone vs. the best **fixed** zone; *sw/day* =
how often the cleanest-zone identity changes; *spill* = mean gap between cleanest and
second-cleanest zone (the marginal cost of being displaced); *leader* = share of hours
one zone is cleanest; *noLeader* = headroom with the leader removed (proxy for a
capped-out clean site). Only zones that are real Google Cloud regions were eligible
(24 zones ⇒ C(24,4) = 10,626 portfolios brute-forced).

| Scenario | Zones | headroom | sw/day | spill | leader | noLeader | Why chosen |
|---|---|---|---|---|---|---|---|
| **S1 — rotating leaders** | ES / FI / CH / BE | **26.8%** | 2.9 | 41 g | FI 39% | 16.6% | Global maximum of the brute force. Cleanest zone rotates ~3×/day (solar vs. wind vs. hydro/nuclear). If migration ever pays, it pays here |
| **S2 — dominant leader** | PL / DE / FR / GB | 0.1% | 0.2 | **135 g** | FR 99% | 0.1% | Uncapped it is boring by construction — so any effect observed under caps is attributable to the caps. Spillover ordering (GB 235 < DE 481 < PL 819 gCO₂/kWh) makes *where* overflow lands matter |
| **S3 — flat control** | SG / TW / HK / IN-MH | 0.0% | 0.0 | 55 g | HK 100% | 0.2% | Real-data placebo: no exploitable signal. Any claimed savings here indicates a methodological bug. (HK's ≈0 variance suggests an estimated feed — verification pending, swap if fake-flat) |

Context rows measured for comparison: a global spread (US-CAL/DE/KR/US-TEX) reaches
only 3.3% — time-zone diversity does not create rotation; anticorrelated clean
*sources* at similar levels do. Our first study's heritage cities (TR/GB/JP-TK/US-SOCO)
score 1.0% and were dropped on that evidence.

Shared world parameters: identical host fleets in all DCs (the power model is
calibrated on one measured reference machine — heterogeneity via scale factors would
be synthetic data presented as measurement); uniform PUE 1.2; 72 h horizon (three
trace days — long enough for migrations to amortize, avoiding the end-of-horizon
"never migrate late" artifact); ~500 tasks in VMs with diurnal release times and
gold/silver/bronze SLA classes; single tenant; everything seeded and bit-reproducible.

## 4. Objectives

Two-objective minimization, both computed from the measurement-based power model:

| Objective | Definition | Notes |
|---|---|---|
| **Carbon** | CO₂ = Σₜ Σ_d PUE_d · P_IT,d(t) · CI_d(t) · dt + Σ_migrations E_WAN · CI_path | Hourly per-DC energy bins × the zone's hourly trace value; migration transfer energy included |
| **SLA (tardiness)** | Σ_tasks w_class · max(0, turnaround − threshold_class) | Class-weighted total tardiness (gold ≫ silver ≫ bronze). Continuous, zero exactly at 100% compliance |

Compliance % is *reported*, not optimized: at 500 tasks it is a step function
(plateaus kill search gradients and produce staircase fronts). Deadline thresholds are
**calibrated to bind** (percentiles of baseline turnaround distributions), otherwise
the front collapses to a point. Makespan is retired (degenerate under diurnal
releases); average wait time and energy (Wh) are reported diagnostics. Migration costs
are **endogenous** — downtime enters tardiness, transfer energy enters carbon, both
caps are debited during transfer — no ad-hoc penalty weights anywhere.

## 5. The trade-off we expect to see

The deliverable per experiment is the Pareto front carbon × tardiness. Expected
structure: the carbon extreme defers work into clean windows and migrates toward
clean zones (paying lateness); the SLA extreme runs everything immediately wherever
released. Specific hypotheses:

1. **Energy–carbon divergence:** the carbon-optimal front region burns *more* kWh
   than the energy-optimal one, yet emits less — quantifying what static-CI
   formulations cannot express.
2. **Front expansion by migration is scenario-dependent:** substantial in S1,
   negligible in S2/S3 uncapped — matching the trace-derived headroom bounds a priori.
3. **Caps reshape the front** (see §6), and the divergence between capped and
   uncapped fronts concentrates in low-CI hours.

## 6. The power-cap factor

Each DC has a peak-power cap `Cap_d(t)` (piecewise-constant, hard constraint via
Deb's constrained dominance — machinery already validated in the first study).
Calibration as in paper 1: caps set at percentiles of observed *uncapped* peaks,
three tiers ≈ {90, 60, 30}% feasibility. A demand-response variant tightens caps
during the trace's dirtiest/stress hours.

Why caps are the scientific core rather than a side constraint — the pre-analysis
facts: uncapped, one zone dominates and hourly chasing is near-worthless (0.1–3.3%
in dominant-leader portfolios); remove the leader (what a binding cap does) and
residual headroom roughly doubles, with daily lead changes. **Carbon-chasing herds
load into the clean zone exactly in its clean window; the cap clips precisely
there; every spilled kWh pays the spillover price (41–162 g/kWh).** The planned
verdicts: (a) do caps bind disproportionately in clean windows? (b) what is the
realized carbon price of saturation, and does overflow land on the cheap or the
expensive neighbor? (c) does joint optimization beat "carbon-greedy then repair to
feasibility"? (d) what does a demand-response event cost in carbon?

## 7. The collaborative portfolio

As in the first study, no single champion: seven arms run under identical budgets and
rules; the paper's central object is the **universal front** (pooled non-dominated set
over all arms), and each arm is scored by per-seed contribution shares plus
fixed-reference hypervolume. Fairness protocol (validated and stress-tested in paper
1): exactly 40,000 evaluations/arm; *every* evaluated solution is offered to the arm's
ε-pruned publication archive (a single publication rule — differing conventions
silently rigged scoreboards until we fixed this); identical warm-start seeds
(LPT / WorkloadAware / EnergyAware heuristics); 10 seeds (base 200); bit-reproducible.

| Arm | Type | Expected role on the front |
|---|---|---|
| GA-Carbon (dominance archive) | Population, single-objective specialist | Depth at the carbon extreme; archive publishes bi-objective by-catch |
| GA-Tardiness (dominance archive) | Same, SLA-obsessed | Depth at the SLA extreme |
| SA-Carbon | Single-solution annealer | Surgical refinement of the carbon end (SA family dominated refinement in paper 1) |
| SA-Tardiness | Same | Refinement of the SLA end |
| NSGA-II | Population, true MOO | Broad central coverage |
| SPEA-II | Population, true MOO | Central coverage; density-based archiving as a second flavor |
| **GT-MOSA** (ours) | Segmented multi-objective SA | Segments 1–2 anchor the two extremes via scalarized SA; later segments target the widest normalized gap of the arm's own archive under Tchebycheff scalarization. Expected: gap-filling in non-convex regions |

GT-MOSA replaces AMOSA, which scored **0% contribution in all six scenario×study
combinations** of paper 1 after the fairness/search-quality fixes — reported as an
honest negative, retained as an appendix baseline. To pre-empt "tuned on your own
benchmark": GT-MOSA's parameters are frozen on the *first study's* problem before
this campaign, protocol disclosed.

## 8. Predictors: the online track (oracle distillation)

The offline campaign is clairvoyant; this track measures **how much of the
clairvoyant frontier survives without future knowledge**. Key idea: the campaign's
hindsight-optimal migration schedules are the labels — *the oracle is the teacher*
(behavior cloning).

- **Teacher corpus:** per (scenario, window, seed) universal front,
  **λ-conditioned**: each solution's normalized front position λ∈[0,1] is an input
  feature, so one model imitates the entire frontier rather than the average of a
  disagreeing committee; sweeping λ at deployment traces an *achieved* frontier.
- **Sample:** one (solution, epoch, VM) triple. Features: λ; clock; per-DC CI level,
  **backward-only** 24 h history, cleanliness rank; per-DC cap headroom and
  utilization (the herding state no CI forecast contains); per-VM remaining work,
  RAM (≈ transfer cost), SLA class, deadline slack, migration budget spent. Labels:
  the solution's migration genes — {STAY, →each other DC}, factorized per VM.
  >90% STAY by construction ⇒ class weighting / two-stage head; exact-epoch labels
  with tolerance applied in scoring/loss only where the action remains valid under
  teacher-state replay.
- **Deployment:** at each epoch the policy proposes migrations above a
  validation-tuned confidence τ; hard feasibility filters (cap headroom, link
  concurrency, per-VM budget) dispose. Task dispatch by the validated greedy
  heuristics; the full engine scores the replay on held-out test windows.

Every rung of the comparison ladder must beat the one below to justify its
complexity:

| Rung | Predictor | Character |
|---|---|---|
| 1 | CI-gap threshold rule | One line, no learning |
| 2 | Persistence forecast ("tomorrow = today") + planner | Exploits diurnal periodicity; deceptively strong |
| 3 | Cluster-lookup playbook: k-means over teacher decision-states; per-cluster **action rates**; confidence = distance-weighted soft vote over the k nearest prototypes, feeding the same τ machinery | No trained weights; prototypes are nameable situation-types → interpretable "when does the oracle migrate" taxonomy |
| 4 | GBDT / small MLP, λ-conditioned | The distilled policy proper; modest model class by design |

**Hoped-for result:** the headline figure of RQ5 — clairvoyant frontier vs.
achieved-without-clairvoyance frontier on one plot, the area between them being the
measured value of knowing the future, reported per grid regime (e.g. high capture on
rotating regimes, low on flat ones). A small capturable gap is itself a publishable
finding. Some oracle decisions are unpredictable in principle (they conditioned on
the future); that residue is the quantity being measured, not noise. Training is
seeded, weights frozen and committed; the track detaches cleanly as a follow-up
paper if the schedule demands.

## 9. Planned experiments (summary)

- **Ablation grid** (the causal engine): {CI: static annual | hourly} ×
  {migration: off | on} × {caps: off | tiers | demand-response}. Corners:
  static/no-mig ≈ prior-literature world; hourly/no-mig = placement + timing value;
  hourly/mig = migration's added value; each ± caps = constraint coupling.
- **Window selection:** every 72 h window of 2022 is featurized (variance, leader
  rotation, cross-zone spread), k-means-clustered into grid regimes; one
  representative per regime **per partition** (train/validation/test,
  non-overlapping, temporally ordered) chosen nearest-to-centroid; manifest frozen
  before any campaign. RQ1–4 pool all windows; the leakage discipline binds only
  the learned track.
- **Campaign shape:** 7 arms × 10 seeds × 3 scenarios × selected windows ×
  ablation corners; 40k evaluations/arm; single-threaded JVM per run.
- **Validation:** every published front re-simulated in the full tick engine;
  standing parity test (constant trace ⇒ carbon ≡ k × energy exactly);
  bit-identical reproducibility test; S3 as the conclusion-level placebo.

## 10. Crude implementation plan

Phases are sequential except where noted; each lands with its tests green. Sizes are
relative (S/M/L).

| Phase | Content | Size | Depends on |
|---|---|---|---|
| **P0 — temporal foundations** | Trace loader (`CarbonIntensityProvider`: trace/static/synthetic impls); per-DC hourly energy binning in the existing sweep-line evaluators; `CarbonObjective` + `TardinessObjective`; workload redesign (release times, SLA classes, instruction masses ×~10³, diurnal profile); idle-power floor switch (engine + analytic mirror, parity-tested); reproducibility test extended to traces | **L** | — |
| **Data chores** | Extract + commit the 12 zone-columns with provenance README; verify HK feed and direct-vs-lifecycle CI; window-regime clustering script + frozen manifest | S | — (parallel) |
| **GT-MOSA prototype** | Segment loop, gap picker, Tchebycheff wrapper, archive reseeding over the existing SA stack; parameter freeze on the paper-1 problem | M | — (parallel) |
| **P1 — spatial** | VM→DC placement genes; carbon-greedy / static-CI / no-migration baselines → RQ1 + spatial share of RQ2 | M | P0 |
| **P2 — constraints** | `Cap_d(t)` schedules; per-DC `PowerCapCalibrator` runs; demand-response windows → RQ3 | M | P1 |
| **P3 — migration** | Engine mechanics (MIGRATING state, stop-and-copy pause/transfer/resume, both-side cap debit, WAN energy, per-link concurrency); sparse migration genes (≤2/VM/day) + surgical operators + repair; threshold-policy baseline → completes RQ2 | **L** | P2 |
| **P4 — campaign + paper (offline core)** | Full campaign over the frozen window manifest; analysis; RQ1–RQ4 write-up | L | P0–P3 |
| **P5 — distillation + online replay** | Teacher-corpus extraction, λ-conditioned training, ladder baselines, held-out replay → RQ5 | M | P4 (consumes its outputs; cannot start earlier, cannot delay P0–P4) |

**Risk controls already in place:** scenario headrooms bound achievable savings a
priori (no over-promising); tardiness calibration prevents degenerate fronts; the
assumed network parameters are swept; determinism is enforced by test. Main open
risks: search-space growth from migration genes (mitigated by K-capped sparse
encoding + repair) and the 2022 crisis-year traces (disclosed; regime-stratified
windows).

---

*Companion documents: `MultiDC-Carbon-PeakPower-Migration-Proposal.md` (full v5
specification; all design decisions resolved and logged) and `HANDOFF.md` §0
(session state). Pre-analysis numbers reproduce via
`scripts/proposal_trace_preanalysis.py` on `combined_carbon.csv`, year 2022.*
