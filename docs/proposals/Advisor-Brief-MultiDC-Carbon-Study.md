# Carbon- and Peak-Power-Aware VM Scheduling with Migration across Geo-Distributed Datacenters — Research Proposal (Advisor Brief)

**Working paper title:** *Joint carbon- and peak-power-aware scheduling and VM migration in
geo-distributed datacenters: a collaborative multi-objective metaheuristic study with
real-world carbon-intensity traces.*

Condensed proposal for advisor review (August 2026). The full specification, including
the log of all resolved design decisions, is maintained in
`MultiDC-Carbon-PeakPower-Migration-Proposal.md` (v5) in this directory. The study is
an independent successor to our single-datacenter, power-capped scheduling study and
reuses its validated components — the discrete-event simulator, the measurement-based
power model, and the multi-algorithm fairness protocol — but none of its results.

---

## 1. Motivation and background

The carbon intensity (CI) of grid electricity varies by region and by hour, often by
an order of magnitude (e.g., Finnish wind-dominated night hours versus Polish
coal-dominated evening peaks). The CO₂ attributable to a computing workload therefore
depends not only on its energy consumption but on where and when that energy is drawn.
An operator of geographically distributed datacenters controls three levers: initial
VM placement, task scheduling within and across VMs, and live VM migration between
sites during execution — the latter at a real cost in downtime, transfer energy, and
network energy.

Prior work addresses these mechanisms largely in isolation:

- **Temporal shifting** (Google's Carbon-Intelligent Computing, Radovanović et al.;
  "Let's Wait Awhile", Wiesner et al., Middleware'21): policy-based, single-mechanism,
  no migration, capacity constraints not first-class.
- **Empirical skepticism** (Sukprasert et al., EuroSys'24): with fixed policies in an
  unconstrained setting, following the hourly-cleanest region yields only marginal
  gains over well-chosen fixed placement. Our pre-analysis reproduces this negative
  result on the authors' own published traces and adopts it as the study's baseline
  condition.
- **Carbon-aware multi-objective metaheuristics:** predominantly assume a static CI
  constant, under which the carbon objective is a scalar multiple of energy and adds
  no information. Under hourly, per-region CI the two objectives decouple; measuring
  the extent of that decoupling is itself a contribution.

**Gap.** No existing study jointly optimizes placement, scheduling, and migration
under time-varying carbon intensity *and* per-datacenter peak-power constraints,
characterizing the carbon–SLA trade-off as a Pareto frontier under a
fairness-controlled multi-algorithm protocol. Our trace pre-analysis (§4) indicates
the two ingredients interact strongly: peak-power caps substantially increase the
value of spatiotemporal flexibility, which is negligible without them.

The core study is offline (all release times, task data, and traces are known in
advance), as in our previous work: it characterizes attainable potential. Section 10
addresses the realism question directly by measuring how much of that potential a
learned policy can retain without any knowledge of the future.

## 2. Research questions

| RQ | Question |
|---|---|
| RQ1 | How much CO₂ does hourly-trace-aware scheduling save versus (a) carbon-agnostic and (b) static annual-average-CI scheduling, at equal SLA attainment? ((b) is the prevailing formulation in the literature; its optimum coincides with the energy optimum.) |
| RQ2 | How do the savings decompose into spatial (initial placement), temporal (deferral/ordering), and migration contributions, and under what cost parameters does migration outweigh its overhead? |
| RQ3 | How do per-datacenter peak-power caps reshape the frontier? Do caps bind disproportionately in low-CI windows; what is the marginal carbon cost of cap-induced displacement; does joint optimization outperform greedy-then-repair? |
| RQ4 | Which arms of the algorithm portfolio contribute which regions of the pooled front in this enlarged, constrained search space? |
| RQ5 | How much of the carbon saving achieved with full future knowledge can be retained without it, using predictors trained on the offline results — a neural network and a clustering-based k-NN prototype policy — against rule and forecast baselines? |

## 3. Data sources

All drive signals come from the public artifact of the EuroSys'24 study (Sukprasert
et al.), Zenodo DOI
[10.5281/zenodo.10790855](https://doi.org/10.5281/zenodo.10790855), CC-BY-4.0;
carbon data originate from Electricity Maps:

| File (in artifact) | Content | Role in this study |
|---|---|---|
| `shared_data/combined_carbon.csv` | Hourly average carbon intensity; 123 grid zones; 2020–2022; gCO₂/kWh; UTC | Drive signal (2022 slice); also the source of the static-CI baseline (per-zone 2022 annual means) |
| `gcp_latency_matrix.csv` | Measured inter-region round-trip times between Google Cloud regions | Inter-datacenter network distances for the migration model |
| `gcp_dc_zonecode_mapper.json` | Google Cloud region → grid-zone mapping | Defines the 24 zones eligible for scenario selection |
| Azure/Google cluster traces (bundled, artifact v2) | Production workload arrival patterns | Shape of the within-day task-arrival profile only |

Parameter provenance is disclosed throughout: inter-region RTTs are measured;
**effective inter-datacenter bandwidth is an assumed, RTT-tiered parameter** (the
matrix contains no throughput data) and **per-GB WAN energy is a literature
constant** — both subjected to sensitivity sweeps. Host power derives from our own
wall-plug measurement campaign (previous study). The repository will carry only the
twelve 2022 zone columns actually used, with a provenance README; the full artifact
remains a cited external download.

## 4. Scenario design

Three four-datacenter scenarios were selected by measurement using the committed
script `scripts/proposal_trace_preanalysis.py` against `combined_carbon.csv`
(year 2022). Selection metrics: **headroom** — CO₂ saving of an idealized scheduler
that runs each hour in the hourly-cleanest zone, relative to the best *fixed* zone
(upper bound on
spatial shifting beyond optimal static placement); **sw/day** — daily count of changes
in the identity of the cleanest zone; **spill** — mean CI gap between cleanest and
second-cleanest zone (the marginal carbon cost of displacement from the cleanest
zone); **leader** — share of hours one zone is cleanest; **noLeader** — headroom with
the leading zone excluded (a proxy for a capacity-saturated cleanest site).
Eligibility was restricted to zones corresponding to Google Cloud regions (24 zones;
all C(24,4) = 10,626 portfolios evaluated exhaustively).

| Scenario | Zones | headroom | sw/day | spill | leader | noLeader | Selection rationale |
|---|---|---|---|---|---|---|---|
| **S1 — rotating leaders** | ES / FI / CH / BE | **26.8%** | 2.9 | 41 g | FI 39% | 16.6% | Global maximum of the exhaustive search; the lowest-CI zone changes ≈3×/day (solar/wind/hydro-nuclear complementarity). The scenario where migration is expected to pay the most, judged from the traces alone |
| **S2 — dominant leader** | PL / DE / FR / GB | 0.1% | 0.2 | **135 g** | FR 99% | 0.1% | Negligible uncapped headroom by construction, so any effect observed under caps is attributable to the caps. The displacement ordering (GB 235 < DE 481 < PL 819 gCO₂/kWh) makes the destination of displaced load consequential |
| **S3 — flat control** | SG / TW / HK / IN-MH | 0.0% | 0.0 | 55 g | HK 100% | 0.2% | Negative control on real data: no exploitable signal exists, so any measured "saving" indicates a methodological artifact. (HK's near-zero variance suggests an estimated feed; verification pending, with substitution if confirmed) |

Context measurements: a globally distributed portfolio (US-CAL/DE/KR/US-TEX) attains
only 3.3% headroom — time-zone diversity alone does not produce leader rotation;
complementary clean generation sources at comparable CI levels do. The predecessor
study's four sites (TR/GB/JP-TK/US-SOCO) score 1.0% and were replaced on that
evidence.

Common world parameters: identical host fleets in all datacenters (the power model is
calibrated on a single measured reference system; synthetic heterogeneity via scale
factors would misrepresent what was actually measured); uniform PUE of 1.2; 72 h
horizon (three trace days — long enough for a migration's cost to be repaid during
the VM's remaining runtime, avoiding a built-in bias against migrating late in the
period); ≈500 tasks in VMs, released over the course of each day, with three SLA
classes; single tenant; fully seeded, bit-reproducible execution.

## 5. Problem formulation

**Decision variables** (one candidate solution comprises all three): (i) task→VM
assignment with per-VM execution order (the predecessor study's encoding, unchanged);
(ii) initial VM→datacenter/host placement; (iii) a sparse migration plan of
(VM, epoch, destination) triples at hour boundaries, at most K = 2 migrations per VM
per day.

**Objectives** (both minimized; all power figures from the measurement-based model):

| Objective | Definition | Notes |
|---|---|---|
| **Carbon** | CO₂ = Σₜ Σ_d PUE_d · P_IT,d(t) · CI_d(t) · dt + Σ_migrations E_WAN · CI_path | Hourly per-DC energy bins × the zone's hourly trace value; migration transfer energy included |
| **SLA (tardiness)** | Σ_tasks w_class · max(0, turnaround − threshold_class) | Class-weighted total tardiness. Continuous; equals zero exactly at 100% compliance |

Carbon-formula symbols (Σₜ Σ_d denotes summation over time slices and datacenters):

| Symbol | Meaning | Provenance |
|---|---|---|
| `P_IT,d(t)` | IT power drawn by the servers of datacenter *d* at time *t* (W) | Measurement-based power model, evaluated on the candidate schedule's utilization state |
| `PUE_d` | ×1.2 — converts IT power to facility power (cooling, conversion losses) | Design constant, uniform across datacenters |
| `CI_d(t)` | Grid carbon intensity of *d*'s zone at hour *t* (gCO₂/kWh) | `combined_carbon.csv`, 2022 column of the zone |
| `dt` | Time-slice length (power × time = energy) | 1 s ticks engine-side; hourly bins in the search evaluator (exact, as CI is piecewise-constant per hour) |
| `E_WAN` | Wide-area network energy of one migration (per-GB constant × GB of VM RAM transferred) | Literature constant, sensitivity-swept. Server-side migration power requires no separate term; it is contained in `P_IT` of both endpoints during the transfer |
| `CI_path` | Carbon intensity applied to the network energy of the transfer path | Derived from the endpoint zones' traces (convention fixed in phase P3) |

Tardiness-formula symbols (evaluated per task, then summed):

| Symbol | Meaning |
|---|---|
| `turnaround` | Elapsed time from task release to completion |
| `threshold_class` | Class deadline: gold ≈ 1 h, silver ≈ 4 h, bronze ≈ end-of-day (final values calibrated so that deadlines bind) |
| `turnaround − threshold` | Lateness relative to the deadline |
| `max(0, ·)` | Hinge: on-time completion contributes exactly zero; only lateness is penalized |
| `w_class` | Class weight, gold ≫ silver ≫ bronze |

Compliance percentage is reported but not optimized: with ≈500 tasks it is a step
function whose plateaus provide no search gradient. Deadline thresholds are
calibrated from baseline turnaround distributions so that they bind for a target
fraction of tasks; otherwise the front degenerates. Makespan is not used (it
degenerates under diurnal release times); mean waiting time and energy (Wh) are
reported as diagnostics. Migration costs need no artificial penalty terms: downtime
enters tardiness, transfer energy enters carbon, and both endpoints' power caps are
charged during the transfer window — the simulation itself prices the consequences.
No hand-tuned penalty weights appear anywhere.

**Constraints.** No resource oversubscription; per-datacenter peak-power caps
`P_IT,d(t) ≤ Cap_d(t)` for all t (handled by Deb's constrained-dominance rules, as
validated in the predecessor study); migration feasibility (per-link bandwidth and
concurrency; at most one concurrent migration per VM; per-VM migration budget).

## 6. Hypotheses

The deliverable of each experiment is the Pareto front in the carbon × tardiness
plane. Expected structure: at the carbon end of the front, work is deferred into
low-CI hours and relocated toward low-CI zones at a lateness cost; at the SLA end,
work is scheduled for earliest completion. Specific hypotheses:

- **H1 (decoupling).** The carbon-optimal front region consumes more energy (Wh) than
  the energy-optimal region while emitting less CO₂; the magnitude of this divergence
  quantifies the information lost by static-CI formulations.
- **H2 (scenario dependence).** Migration expands the front substantially in S1 and
  negligibly in S2/S3 without caps, consistent with the a-priori headroom bounds of §4.
- **H3 (constraint coupling).** Caps reshape the front, with the capped/uncapped
  divergence concentrated in low-CI hours (see §7).

## 7. Peak-power constraints as the coupling mechanism

Each datacenter carries a peak-power cap `Cap_d(t)` (piecewise-constant, hard).
Calibration follows the predecessor study: caps are set at percentiles of observed
uncapped peak draw, at three tiers of approximately {90, 60, 30}% feasibility. A
demand-response variant additionally tightens caps during the trace's highest-CI
stress hours.

The pre-analysis motivates treating caps as the central mechanism rather than a side
constraint. Without caps, one zone dominates the CI ordering and hourly-optimal
relocation adds 0.1–3.3% over fixed placement in dominant-leader portfolios; with the
leading zone excluded — the situation a binding cap produces — residual headroom
roughly doubles and the identity of the best available zone changes daily. Moreover,
carbon-aware policies concentrate load in the cleanest zone precisely during its
low-CI windows, so caps are expected to bind exactly where the carbon objective is
most sensitive; each displaced kWh then incurs the spillover cost (41–162 gCO₂/kWh
across the portfolios). The planned analyses for RQ3: (a) the temporal coincidence of
cap-binding hours and low-CI hours; (b) the realized marginal carbon cost of
displacement and its distribution over destination zones; (c) joint optimization
versus a carbon-greedy-then-repair baseline; (d) the carbon cost of a demand-response
event.

## 8. Algorithm portfolio and fairness protocol

As in the predecessor study, no single algorithm is privileged: seven arms run under
identical budgets and rules; the object of analysis is the **universal front** (the
pooled non-dominated set over all arms), with per-arm attribution via per-seed
contribution shares and a fixed-reference hypervolume. The fairness protocol was
developed and stress-tested in the predecessor study: exactly 40,000 evaluations per
arm; every evaluated solution is offered to the arm's ε-pruned publication archive
(a uniform publication rule — heterogeneous publication conventions measurably
distorted attribution before this was standardized); identical warm-start seeding
(LPT, WorkloadAware, EnergyAware heuristics); 10 seeds (base 200); bit-reproducible
runs.

| Arm | Class | Anticipated contribution |
|---|---|---|
| GA-Carbon (dominance archive) | Population-based, single-objective specialist | Depth at the carbon extreme; its archive also publishes solutions found along the way that are good on both objectives |
| GA-Tardiness (dominance archive) | As above, SLA-directed | Depth at the SLA extreme |
| SA-Carbon | Single-solution annealing | Local refinement of the carbon-extremal region (the SA family dominated refinement in the predecessor study) |
| SA-Tardiness | As above | Refinement of the SLA-extremal region |
| NSGA-II | Population-based multi-objective | Broad central coverage |
| SPEA-II | Population-based multi-objective | Central coverage under a density-based archiving scheme |
| **GT-MOSA** (this work) | Segmented multi-objective SA | Segments 1–2 anchor the two extremes via scalarized SA; subsequent segments target the widest normalized gap of the arm's own archive under Tchebycheff scalarization. Anticipated: gap coverage, including non-convex front regions |

GT-MOSA replaces AMOSA, which contributed 0% in all six scenario×study combinations
of the predecessor campaign after the fairness and search-quality corrections — a
result we report as a negative finding and retain as an appendix baseline for direct
comparison. To pre-empt benchmark-tuning concerns, GT-MOSA's parameters are frozen on
the predecessor study's problem before this campaign, with the protocol disclosed.

## 9. Experimental design

- **On/off comparison grid** (the causal instrument for RQ1–RQ3: each mechanism is
  switched on or off independently, so its individual contribution can be
  measured): {CI signal: static annual | hourly} × {migration: off | on} ×
  {caps: off | calibrated tiers | demand-response}. Readings: static/no-migration
  approximates the prior-literature setting; hourly/no-migration isolates
  placement and deferral; hourly/migration adds the migration increment; each
  combination is repeated under the cap settings.
- **Trace-window selection.** Every 72 h window of 2022 is described by a feature
  vector (per-zone CI profile shape, intra-day variance, leader-rotation count,
  cross-zone spread) and clustered (k-means) into characteristic grid regimes. One
  representative window per regime **per partition** (train / validation / test;
  non-overlapping; temporally ordered within each regime) is chosen
  nearest-to-centroid, and the chosen list is frozen before any campaign runs. RQ1–RQ4
  may pool all windows; the partition discipline binds only the learned-policy track
  (RQ5).
- **Campaign shape:** 7 arms × 10 seeds × 3 scenarios × selected windows × the
  on/off grid combinations; 40,000 evaluations per arm; one single-threaded JVM
  per run (process-level parallelism only, preserving determinism).
- **Validation:** every published front re-simulated in the full tick-level engine
  (search-side evaluators are analytic); a standing parity test (constant trace ⇒
  carbon ≡ k × energy exactly); bit-identical reproducibility tests; S3 as the
  conclusion-level negative control.

## 10. Prediction models (RQ5): learning from the offline results

The optimizer in the main study plans with complete knowledge of the future: every
task arrival and every hourly grid value over the 72 h horizon is available to it in
advance. A real operator has no such knowledge. This track measures how much of the
optimizer's carbon saving can be kept by prediction models that see only past and
present information.

The training data comes from the main campaign itself: its best schedules record,
in hindsight, which VM should have moved where and when. Those recorded decisions
become the answer key for supervised learning — the models are trained to reproduce
good decisions from the information that was available at the moment of each
decision (imitation learning).

- **Training data.** The pooled best-trade-off set of every (scenario, window,
  seed). That set spans plans from fully deadline-focused to fully carbon-focused,
  so each plan is tagged with its position λ ∈ [0,1] along the trade-off curve and
  λ is given to the model as an input. One model thereby learns the whole range of
  operating points instead of an average of contradictory ones; at run time the
  operator sets λ.
- **Samples.** One sample = one (plan, hour, VM). Inputs: λ; time of day; each
  datacenter's current grid CI and its past 24 h (never a future value); each
  datacenter's remaining power-cap headroom and load; the VM's remaining work,
  memory size (≈ cost to move), SLA class, deadline slack, and moves already used.
  The value to predict is what the recorded plan did with that VM at that hour:
  stay, or move to a named datacenter. More than 90% of the answers are "stay", so
  training must up-weight the rare "move" cases; labels keep their exact hour,
  with a ±1 h allowance applied only in scoring and only where the move is still
  physically possible at the shifted hour.
- **Use at run time.** Each hour the model proposes the moves it is confident
  about (above a tuned threshold τ); every proposal is checked against the hard
  limits (destination cap headroom, link capacity, per-VM move budget) and is
  carried out only if it passes. Tasks are dispatched by the validated greedy
  heuristics, and the full simulator scores the resulting three days — on test
  windows the model has never seen.

Comparison hierarchy — each level must beat the previous one to justify its
complexity:

| Level | Predictor | Character |
|---|---|---|
| 1 | CI-gap threshold rule | One-parameter rule, no learning |
| 2 | Persistence forecast (assume tomorrow's grid = today's) with planning | Exploits the strong day-to-day repetition of grid data |
| 3 | **k-NN prototype policy (clustering-based):** k-means clustering over the recorded decision situations; per-cluster **action rates**; confidence = distance-weighted vote over the k nearest cluster centroids (k-nearest-neighbor lookup over cluster centers rather than raw samples), feeding the same τ mechanism | No trained weights; the centroids are recognizable situation types, enabling a plain-language analysis of when moving pays |
| 4 | **Neural network:** small multilayer perceptron (MLP) with λ as an input; gradient-boosted decision trees (GBDT) evaluated as an alternative | The trained predictor proper; deliberately small model |

**Intended result.** One figure per scenario with two curves: the trade-off curve
achieved with full future knowledge, and the curve achieved by the trained
predictor without it. The gap between the curves is the measured value of knowing
the future, reported per grid regime. A small retained fraction would itself be a
publishable finding: some recorded decisions depended on information about the
future and are unpredictable in principle — that unpredictable remainder is exactly
what RQ5 quantifies. Model training is seeded and the final weights are committed
to the repository, so this track is as reproducible as the rest; it can split off
into a follow-up paper if the schedule requires.

## 11. Implementation plan

Phases are sequential unless marked parallel; each concludes with its tests passing.
Sizes are relative (S/M/L).

| Phase | Content | Size | Depends on |
|---|---|---|---|
| **P0 — time and carbon foundations** | Teach the simulator about time-varying carbon: read the hourly CI files; track each datacenter's energy hour by hour; add the two new scores (carbon and lateness); rebuild the workload so tasks arrive through the day, carry deadlines, and run for minutes–hours (long enough for hourly carbon to matter); count idle server power; add automatic tests proving the new accounting is exact and runs repeat bit-for-bit | **L** | — |
| **Data preparation** | Extract and commit the 12 country columns with a README stating their origin; check the Hong Kong feed and which CI definition the dataset uses; write the window-selection script and freeze the chosen windows | S | — (parallel) |
| **GT-MOSA prototype** | Build our custom multi-objective SA: short annealing runs in sequence — the first two aim at the two ends of the trade-off curve, later ones aim at the widest gap remaining in the curve found so far; its settings are fixed on the previous study's problem before this campaign starts | M | — (parallel) |
| **P1 — placement across datacenters** | Let the optimizer choose each VM's starting datacenter; add the simple comparison schedulers (carbon-greedy, annual-average CI, no migration) → RQ1 and the placement share of RQ2 | M | P0 |
| **P2 — power caps** | Per-datacenter cap schedules; calibration runs that set the cap levels; the tightened-caps ("demand response") variant → RQ3 | M | P1 |
| **P3 — migration** | Moving VMs inside the simulator: pause, transfer, resume; power counted at both ends plus network energy; limited simultaneous transfers per link; migration decisions added to the chromosome (≤2 moves per VM per day) with matching mutation and repair operators; the threshold-rule baseline → completes RQ2 | **L** | P2 |
| **P4 — campaign and paper (offline core)** | Full campaign over the frozen windows; analysis; RQ1–RQ4 manuscript | L | P0–P3 |
| **P5 — predictor training and replay** | Build the training set from the campaign's best schedules; train the models; run the comparison hierarchy; replay on unseen test windows → RQ5 | M | P4 (uses its outputs; cannot start earlier and does not delay P0–P4) |

Risk controls: the measured scenario headrooms bound achievable savings in advance;
deadline calibration prevents degenerate fronts; the assumed network parameters are
re-run with higher and lower values; determinism is enforced by test. Principal open
risks: search-space growth from the migration genes (limited by the two-moves-per-VM
-per-day encoding and the repair operators) and the atypical 2022 European grid year
(disclosed; window selection stratified by regime).

---

*Companion documents: `MultiDC-Carbon-PeakPower-Migration-Proposal.md` (full v5
specification; all design decisions resolved and logged) and `HANDOFF.md` §0. The
pre-analysis figures reproduce via `scripts/proposal_trace_preanalysis.py` on
`combined_carbon.csv`, year 2022.*
