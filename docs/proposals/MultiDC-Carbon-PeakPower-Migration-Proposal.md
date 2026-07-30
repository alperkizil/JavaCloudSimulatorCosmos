# Research Proposal — Carbon- and Peak-Power-Aware VM Scheduling with Migration across Geo-Distributed Datacenters

**Status:** DRAFT for discussion — nothing here is committed; §6 lists the decisions the
owner needs to make. Written 2026-07-30 on branch `claude/multi-datacenter-proposal-jromkx`.

**Working title (paper):** *Joint carbon- and peak-power-aware scheduling and VM migration
in geo-distributed datacenters: a collaborative multi-objective metaheuristic study with
real-world carbon-intensity traces.*

---

## 1. From paper 1 to paper 2

Paper 1 (in progress, see `HANDOFF.md`) studies **offline task scheduling in a single
power-capped datacenter**, finding collaborative Pareto fronts for Makespan–Energy and
WaitingTime–Energy with a 7-arm metaheuristic portfolio (GA/SA dominance-archive variants,
NSGA-II, SPEA-II, AMOSA), MeasurementBased energy, no oversubscription, and a hardened
fairness methodology (single publication rule, per-seed collaboration shares, HV_fixed).

Paper 2 keeps that validated core — offline optimization, measurement-based power,
deterministic seeds, the collaborative-Pareto scoreboard — and expands the *world model*
along three axes that are individually topical and jointly under-studied:

1. **Multiple, geographically distributed datacenters** whose grid **carbon intensity
   (CI) varies by region and by hour**, driven by real-world trace data (already in hand).
2. **Peak-power limitations** per datacenter — evolving paper 1's static power ceiling
   into time-varying caps (grid demand-response windows, demand-charge avoidance).
3. **VM migration** as the runtime mechanism that lets a schedule exploit spatial and
   temporal CI variation after initial placement — at a cost (transfer energy, downtime)
   that the optimizer must weigh.

### Why these three together (the core of the idea)

Each ingredient alone has literature; the **interactions** are the research substance:

- **Carbon-chasing causes herding.** If every scheduler independently shifts load into
  the cleanest region/hours, the clean datacenter's power peaks precisely in the clean
  window. Peak caps couple otherwise-independent placement decisions and clip the naive
  "follow-the-renewables" policy. Question: what does the carbon–performance frontier
  look like when the caps bind, and does joint optimization beat greedy-then-repair?
- **Migration is a lever with a price.** Moving a VM to a cleaner grid saves carbon on
  future work but spends energy/carbon/time on the move itself (RAM transfer over WAN,
  downtime that hurts makespan/wait). Whether a migration pays off depends on the VM's
  remaining work, the CI gap, and the cap headroom at the destination — a genuinely
  non-trivial scheduling decision. Crucially, migration overhead is *endogenously*
  penalized through the existing objectives (its energy/carbon enters the carbon
  objective; its downtime enters makespan/wait) — no ad-hoc penalty weights needed.
- **Energy-optimal ≠ carbon-optimal.** With time- and region-varying CI, a schedule that
  consumes *more* kWh can emit *less* CO₂ (run at night in a hydro region vs. at noon in
  a coal region). This decouples carbon from energy — today, in this simulator, carbon is
  a constant × energy, so they are the *same* objective up to scale. The decoupling is
  what makes "carbon" a legitimate new objective for the multi-objective machinery, and
  quantifying the divergence is itself a publishable observation.

---

## 2. Research gap and positioning

Landscape (to be deepened into a proper related-work section later):

- **Temporal shifting:** Google's Carbon-Intelligent Computing (Radovanović et al.) and
  "Let's Wait Awhile" (Wiesner et al., Middleware '21) defer flexible load into cleaner
  hours — single-mechanism, policy-based, no migration, caps treated (if at all) as a
  fixed capacity curve, not a first-class constraint interacting with the carbon signal.
- **Spatial shifting / migration:** "Free Lunch" (Akoush et al.) migrates VMs to follow
  renewables; geo load balancing for electricity cost (Qureshi et al.) — typically
  single-objective, simplified overhead accounting.
- **Skeptical quantification:** Sukprasert et al. (EuroSys '24) measure the *limits* of
  spatiotemporal shifting on real workloads — an excellent foil: they evaluate fixed
  policies in an unconstrained setting; we characterize the attainable **Pareto
  frontier** of a *jointly optimized* schedule under peak-power constraints, with honest
  migration overhead, using perfect-hindsight offline optimization as the upper bound.
- **Carbon-aware multi-objective metaheuristics:** existing NSGA-II-style "green cloud"
  papers overwhelmingly use a **static** regional CI factor — which, per the decoupling
  argument above, silently reduces the carbon objective to a rescaled energy objective.
  Calling this out (and measuring what static-CI optimization *misses*) is a clean,
  quantifiable critique that motivates the whole study. RQ1 operationalizes it.
- **Simulators:** CloudSim/CloudSimPlus have power + migration but no native hourly CI
  traces or carbon objectives; Vessim/LEAF are carbon-aware but not built for offline
  multi-objective placement benchmarking. A reproducible benchmark (real traces + caps +
  migration + measurement-based power + deterministic multi-seed campaigns) is a citable
  artifact in its own right.

**Gap statement (one sentence):** No existing study jointly optimizes initial placement,
task scheduling, and VM migration across geo-distributed datacenters under *time-varying*
carbon intensity **and** peak-power constraints, characterizing the carbon–performance
trade-off as a Pareto frontier with a rigorously fair multi-algorithm methodology.

---

## 3. Research questions

- **RQ1 — Value of the carbon signal.** How much CO₂ does hourly-trace-aware scheduling
  save versus (a) carbon-agnostic scheduling and (b) *static average-CI* carbon-aware
  scheduling, at equal performance? ((b) isolates the value of temporal information and
  doubles as the critique of static-CI literature. It also directly measures the
  energy–carbon decoupling: the static-CI optimum *is* the energy optimum.)
- **RQ2 — Mechanism decomposition.** Decompose the savings into **spatial** (where work
  initially lands), **temporal** (when it runs / execution order), and **migration**
  (runtime re-placement) contributions, via ablations (§7). When is migration worth its
  overhead? Sensitivity to VM memory size, WAN bandwidth, migration energy coefficient.
- **RQ3 — Constraint coupling ("herding").** How do per-DC peak-power caps reshape the
  frontier? Do caps bind precisely in low-CI windows (herding)? Does jointly optimized
  scheduling beat "greedy carbon-first, then repair to caps"? What is the carbon price
  of a demand-response event (cap tightening during grid stress hours)?
- **RQ4 — Algorithmic portfolio.** Which arms of the collaborative portfolio contribute
  which regions of the front in this larger, constrained, time-expanded search space?
  (Methodological continuity: same scoreboard — per-seed shares, HV_fixed — as paper 1;
  tests whether paper 1's algorithm findings generalize to a harder problem.)

---

## 4. System model and problem definition (offline, deterministic)

### 4.1 World model

- Horizon `H` (proposed: 24h; sensitivity: 72h) at the engine's 1-second ticks; CI and
  caps piecewise-constant per hour (or per 15/30 min if the data supports it).
- Datacenters `d ∈ D` (proposed 3–5), each with: a host fleet (existing `Host` model,
  MeasurementBased power), `PUE_d`, carbon trace `CI_d(t)` [gCO₂/kWh], peak cap
  `Cap_d(t)` [W], and pairwise WAN bandwidth `BW(d,d')` for migrations. Regions sit in
  different time zones — the diurnal offset between grids (solar noon moving westward)
  is a real, exploitable structure in the data.
- Workload: long-running VMs carrying task queues (existing model), with tasks given
  **release times over the day** (diurnal arrival pattern) and instruction masses scaled
  so that meaningful work spans hours (see workload redesign, §8.3). Offline means: all
  releases, lengths, and traces are known at t=0 (clairvoyant offline with release
  dates); this matches paper 1's mode and gives a clean upper-bound ("potential") study.

### 4.2 Decision variables

1. Task → VM assignment + per-VM execution order (existing chromosome, unchanged).
2. **NEW:** initial VM → datacenter/host placement (today fixed by config + placement
   step before optimization).
3. **NEW:** migration plan — a sparse set of `(vm, epoch, destination-DC)` genes, where
   **epochs are the hour boundaries** (aligned with CI changes, which is where moving
   ever becomes newly attractive). Cap of `K` migrations per VM per day (proposed K=2)
   keeps the space tractable and is operationally realistic.

### 4.3 Objectives and constraints

Pairwise planes, following paper 1's study structure:

- **Study A: Makespan – Carbon.**
- **Study B: AvgWait – Carbon.**
- **Study C (successor of the PowerCeiling study): A/B repeated under binding
  time-varying caps** — constrained twins via the existing Deb's-rules machinery.

Carbon objective: `CO₂ = Σ_t Σ_d PUE_d · P_IT,d(t) · CI_d(t) · dt + Σ_migrations
E_WAN · CI_path`, i.e. time-resolved power × time-resolved intensity, plus migration
transfer energy carbonized at a defensible network intensity constant. Energy (Wh) stays
reported as a diagnostic column everywhere (the energy–carbon divergence is a headline
number), but is not a third Pareto axis in v1 — the analyzer/scoreboard is 2-D; a
3-objective extension is flagged as optional stretch (§6, D6).

Hard constraints: `P_IT,d(t) ≤ Cap_d(t)` for all t (generalizes paper 1's single-DC
static ceiling; same constrained-dominance handling), no oversubscription (unchanged
invariant), migration feasibility (bandwidth, ≤1 concurrent migration per VM, per-link
concurrency limit), user→DC preference sets (existing model) where used.

### 4.4 VM migration model (v1: simple, honest, tick-native)

- **Stop-and-copy** live-migration approximation: at epoch boundary, VM pauses; transfer
  time `T_mig = RAM_dirty / BW_eff(d,d')` (v1: full RAM; pre-copy dirty-page refinement
  is future work and only tightens downtime); tasks in the VM stall during `T_mig`
  (hurts makespan/wait endogenously).
- **Migration energy:** source and destination hosts each draw an overhead power during
  transfer (proportional to NIC utilization), plus per-GB WAN energy (constant from
  literature) → both enter energy and carbon accounting.
- Effects on caps: migration overhead power counts against *both* DCs' caps during the
  transfer window (this is exactly the kind of coupling that makes the problem
  interesting — you cannot migrate *into* a clean window if the destination is
  cap-saturated by everyone else doing the same).

---

## 5. What exists vs. what must be built (simulator gap analysis)

Grounded in README/HANDOFF; exact file-level extension points to be confirmed against
the code audit (this section will be refined after review).

### Already in place (reuse as-is or with small extensions)

| Capability | Where | Notes |
|---|---|---|
| Multi-DC model with per-DC power budget | `model/CloudDatacenter` (`totalMaxPowerDrawWatts`, `isPowerLimitReached()`), `.cosc [DATACENTERS]` | Dormant in current studies (1 DC); becomes load-bearing |
| Host placement across DCs | `PlacementStrategy/hostPlacement/*` incl. power-aware load balancing | Noted "inert at 1 DC" in HANDOFF §3.3 — activates here |
| User → DC preferences | `model/User` (`selectedDatacenterNames`) | Optional constraint dimension |
| Static carbon + PUE + cost accounting | `steps/EnergyCalculationStep`, `CarbonIntensityRegion` enum | Post-hoc constant × kWh — the exact thing RQ1 critiques; keep as the static-CI baseline |
| Power-capped constrained optimization | PowerCeiling study: `PowerCeilingSchedulingProblem`, `ConstrainedNonDominatedArchive` (Deb's rules), constrained-AMOSA comparator | Generalize cap from scalar to `Cap_d(t)` schedule |
| Measurement-based energy | `MeasurementBasedPowerModel` (speed–power exponent 1.5) | Untouched invariant: all published energy/carbon from this model |
| 7-arm portfolio + fairness machinery | `newExperiments/` (`CampaignRunner`, `AlgorithmRegistry`), PR #218 publication rule, PR #220 scoreboard (`ParetoAnalyzer`, per-seed shares, HV_fixed), PR #222 operator settings | Reuse wholesale; same 10-seed × 40k-eval campaign shape |
| Determinism | `RandomGenerator` singleton, `CampaignReproducibilityTest` | Traces are deterministic inputs; discipline unchanged |
| Time-stepped engine | `steps/VMExecutionStep` (dt = 1 s) | Hour-scale horizons = ~86 400 ticks/day — fine for a single sim, but see two-level evaluation below |

### To build (the project's engineering content)

| # | Component | Sketch |
|---|---|---|
| B1 | **Carbon-intensity traces** | `CarbonIntensityProvider` interface: `getIntensity(dcId, tick)`. Impls: `StaticRegionProvider` (wraps existing enum — backward compat + RQ1 baseline), `TraceBasedProvider` (CSV: `region,timestamp_utc,gco2_per_kwh`, hourly, validated coverage + timezone alignment), `SyntheticProvider` (parameterized sinusoids for controlled experiments) |
| B2 | **Time-resolved power accounting** | Today energy is a cumulative scalar; carbon needs `P_d(t)`. Engine side: per-tick per-DC power is already computed each tick — bin it per hour (cheap). Optimizer side: extend the fast analytic evaluator (LaneSchedule completion-tick projections) to emit a time-binned per-DC power profile instead of only totals — **the** key perf-critical piece (see E1 risk) |
| B3 | **Carbon objective** | New `CarbonObjective` alongside Makespan/Energy/Wait in `metaheuristic/objectives/`, consuming B1+B2. Same normalization treatment as PR #208's objective-scale fix |
| B4 | **Time-varying caps** | `Cap_d(t)` schedule in config; generalize PowerCeiling constraint check to per-hour-bin max; violation = max-over-bins excess (keeps a scalar constraint value for Deb's rules). Scenario option: derive cap-tightening windows from the same grid data (demand-response during the grid's top-load hours — realistically correlated with dirty hours) |
| B5 | **Migration mechanics (engine)** | `VmState.MIGRATING`; VM pause/transfer/resume at epoch boundaries; RAM/BW transfer time; overhead power on both hosts; WAN energy; per-link concurrency. New events in `VMExecutionStep` loop |
| B6 | **Chromosome extension** | `[task→VM + order (existing)] ⊕ [VM→DC initial placement] ⊕ [sparse migration genes (vm, epoch, dest), ≤K per VM]`. New mutation moves (retarget/add/remove migration, re-place VM) in the surgical-move style of PR #208/#222; `RepairOperator` extension for cap/bandwidth/preference feasibility |
| B7 | **Baselines** | Carbon-greedy initial placement ("lowest-CI-first"); follow-the-renewables migration *policy* (threshold rule: migrate when ΔCI × remaining-energy > β × migration-cost — also interpretable as the rule-based alternative to direct encoding); static-CI arm; no-migration arm; existing LPT/WA/EnergyAware seeds retained |
| B8 | **Workload redesign** | Hour-scale instruction masses + diurnal release times (see §8.3). Same pure/RNG-free task-generation discipline as LOG16 (HANDOFF §2.2 change 3) |
| B9 | **Reporting** | Per-DC hourly CSV (power, cap, CI, CO₂); campaign columns: total gCO₂ (split compute/idle/migration/WAN), peak kW per DC, cap-binding hours, migration count/downtime; carbon columns through `ParetoAnalyzer` unchanged (it is objective-agnostic) |

---

## 6. Key design decisions (owner input wanted; recommendations marked ▸)

- **D1 — Offline vs. online.** ▸ Offline with perfect trace hindsight, exactly like
  paper 1 (clean upper-bound/"potential" framing, reuses everything). Optional final
  experiment: re-run best schedules against ±X% perturbed traces to measure robustness
  (cheap bridge toward practicality, without building an online scheduler). A true
  online/forecast study is paper 3 material.
- **D2 — Migration decision representation.** ▸ Direct sparse genes at hour epochs with
  K≤2 per VM (searchable, analyzable), with the threshold-rule policy as a *baseline
  arm* rather than the primary representation. Alternative (smaller space, less direct):
  optimize only the rule's parameters.
- **D3 — Objective planes.** ▸ Makespan–Carbon and Wait–Carbon (+ capped twins), energy
  as reported diagnostic. Alternative: replace one plane with Cost–Carbon using price
  traces (data permitting) — operators' actual tension. Defer 3-objective fronts (D6).
- **D4 — Migration model fidelity.** ▸ Stop-and-copy v1 (honest, tick-native, few
  parameters). Pre-copy dirty-page model as sensitivity/future work. Literature
  constants for WAN J/GB and overhead power; disclose.
- **D5 — Average vs. marginal CI.** ▸ Average CI (attributional; matches most reporting
  and most datasets, incl. ElectricityMaps). If the in-hand dataset has marginal
  signals, add a sensitivity appendix. Must be stated explicitly either way.
- **D6 — Scoreboard scope.** ▸ Keep 2-D planes + existing analyzer. Stretch: 3-objective
  (Perf, Energy, Carbon) would need HV_fixed and contribution logic generalized —
  meaningful analyzer work; only if a reviewer-visible payoff is expected.
- **D7 — AMOSA.** Carries the paper-1 open issue (0% contribution post-#222). ▸ Keep the
  7-arm portfolio for continuity and report honestly; decide in paper 1 first.
- **D8 — Fleet heterogeneity across DCs.** ▸ Identical fleets per DC in the main study
  (isolates grid effects from hardware effects); a heterogeneous-fleet scenario as
  sensitivity (efficiency-vs-cleanliness tension: efficient hosts on a dirty grid).
- **D9 — Where does the real data come from / licensing.** Owner has real CI data —
  need: source, regions covered, resolution, license for publication, and whether
  price traces are also available (affects D3). Public complements if gaps: ENTSO-E,
  ElectricityMaps academic, EIA, EPİAŞ.

---

## 7. Experimental design

- **Scenarios (DC portfolios), 3 proposed:**
  - S1 *High spatial contrast:* coal-heavy + nuclear/hydro + solar-duck-curve regions
    (e.g., PL + FR + DE-style traces) — spatial signal dominates.
  - S2 *Temporal-only control:* all DCs on the *same* trace (or homogeneous synthetic) —
    spatial savings ≡ 0 by construction; isolates temporal + ordering effects and gives
    the migration arms nothing spatial to exploit (falsification control).
  - S3 *Time-zone spread:* similar mix but offset diurnal cycles (e.g., EU + US-East +
    US-West-style) — migration should "follow the night/sun"; the showcase scenario.
  - Day selection from the real dataset: one high-variance day, one low-variance day
    (disclosed rule, e.g., top/bottom decile of intra-day CI std) — CI variance is the
    resource the optimizer exploits, so it must be a controlled variable.
- **Arms:** the 7-arm portfolio (per D7) + policy baselines (B7). Campaign shape as
  paper 1: 10 seeds (baseSeed=200), 40k evaluations/arm, per-seed collaboration shares +
  HV_fixed, single-threaded JVM per study, separate processes for parallelism.
- **Ablation grid (the RQ2/RQ3 engine):** {CI: static avg | hourly} × {migration: off |
  on} × {caps: off | loose | tight | demand-response event} — 4 corners headline the
  paper: static/no-mig (≈ paper-1 world), hourly/no-mig (temporal+spatial placement
  only), hourly/mig (full), each ± caps.
- **Metrics:** total gCO₂ (split: compute / idle / migration / WAN), Wh, makespan, avg
  wait, per-DC peak kW, cap-binding hours, migration count + total downtime, % savings
  vs. carbon-agnostic and vs. static-CI at matched performance (iso-performance
  comparison read off the fronts), energy–carbon divergence (Δ between energy-optimal
  and carbon-optimal points), CUE. Constraint studies: zero violations by construction
  (hard constraint), report cap headroom profiles instead.
- **Validation:** final fronts re-simulated in the full engine (two-level evaluation:
  fast analytic evaluator inside the search, engine as ground truth — same pattern as
  paper 1); `CampaignReproducibilityTest`-style bit-identical same-seed check extended
  to traces.

## 8. Phased roadmap (each phase independently runnable/testable)

- **P0 — Temporal foundations:** B1 traces + B2 time-binned power + B3 carbon objective
  + B8 workload redesign + unit tests (incl. parity: constant trace ⇒ carbon ≡
  k × energy, the invariant that must hold exactly).
- **P1 — Spatial study (no migration, no caps):** multi-DC initial placement genes (B6
  partial) + baselines; first results for RQ1 and the spatial share of RQ2.
- **P2 — Constraints:** B4 time-varying caps; herding analysis (RQ3).
- **P3 — Migration:** B5 engine mechanics + B6 migration genes + B7 policy baseline;
  completes RQ2.
- **P4 — Campaign + paper.** Optional **P5:** forecast-perturbation robustness (D1).

### 8.3 Workload redesign note (the biggest modeling risk, so it's called out)

Paper 1's LOG16 workload has seconds-scale makespans (~13–37 s) — three orders of
magnitude below the hourly CI signal. For carbon-awareness to have anything to exploit,
meaningful work must span CI changes: scale instruction masses so per-VM busy time is
hours, and spread task release times over the day (diurnal profile). This changes
absolute numbers vs. paper 1 (fine — different study), but the generation discipline
(pure, RNG-free, mass-matched across scenario variants for comparability) carries over
verbatim. Engine cost stays modest (86 400 ticks × small fleet), but the *search-loop*
evaluator must stay analytic (B2); full-engine evaluation inside 40k-eval runs is off
the table, exactly as in paper 1.

## 9. Risks

| Risk | Mitigation |
|---|---|
| Search-space blow-up from migration genes | Epoch-restricted, K-capped sparse genes (D2); surgical mutation moves; repair operator |
| Fast evaluator with time-binned power too slow at 40k evals | Piecewise-constant CI ⇒ per-hour binning of lane busy-ticks is O(tasks + bins) per eval; profile early in P0 |
| Carbon savings look small (Sukprasert-style outcome) | Still publishable: frontier + decomposition + herding analysis are contributions independent of savings magnitude; scenario S1/S3 chosen for real signal diversity; upper-bound framing is honest |
| Migration overhead parameters contested by reviewers | Literature-sourced constants + sensitivity sweep (B7/RQ2); stop-and-copy is conservative (overstates downtime) |
| Time-zone/DST alignment bugs in traces | UTC-only internal clock; alignment unit tests; trace-coverage validation at load |
| Determinism regressions | Traces deterministic; same seed discipline; extend reproducibility test in P0 |

## 10. Immediate next steps

1. Owner review of this document — especially decisions D1–D9.
2. Inventory the in-hand carbon dataset against D9 (regions, resolution, license).
3. P0 spike: trace loader + constant-trace parity test + evaluator profiling.

---

*Prepared as a discussion artifact; supersedes nothing. Companion to `HANDOFF.md`
(paper-1 state). Comments welcome directly on the PR.*
