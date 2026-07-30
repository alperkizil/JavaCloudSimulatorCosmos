# Research Proposal — Carbon- and Peak-Power-Aware VM Scheduling with Migration across Geo-Distributed Datacenters

**Status:** DRAFT v2 for discussion (2026-07-30, branch `claude/multi-datacenter-proposal-jromkx`).
v2 incorporates the code audit of the simulator, the owner's decisions from the
2026-07-30 brainstorm (objectives = Carbon × SLA; trace year 2022; datacenter
locations free to choose), and a quantitative pre-analysis of the real carbon
traces (`scripts/proposal_trace_preanalysis.py`). Open decisions are in §6.

**Working title (paper):** *Joint carbon- and peak-power-aware scheduling and VM
migration in geo-distributed datacenters: a collaborative multi-objective
metaheuristic study with real-world carbon-intensity traces.*

---

## 1. Scope: an independent study on the existing framework

**This is a standalone study, not a sequel** (owner decision, 2026-07-30): it does not
depend on the previous paper's results, narrative, or publication timeline. What it
reuses is the **framework** — the validated simulator and methodology built for the
earlier single-datacenter power-capped study (see `HANDOFF.md`): offline optimization,
MeasurementBased energy, no oversubscription, deterministic seeds, and the
collaborative-Pareto fairness machinery (single publication rule, per-seed
collaboration shares, HV_fixed). Mentions of "paper 1" below are engineering
references to that framework and its calibrated components, never narrative
dependencies.

The study's world model extends the framework along three axes:

1. **Multiple, geographically distributed datacenters** whose grid **carbon intensity
   (CI) varies by region and by hour**, driven by real 2022 hourly traces (§8).
2. **Peak-power limitations** per datacenter — evolving paper 1's static ceiling into
   per-DC (optionally time-varying) caps.
3. **VM migration** as the runtime mechanism for exploiting spatial and temporal CI
   variation after initial placement — at an endogenous cost (transfer energy,
   downtime, speed re-clamping) the optimizer must weigh.

### Why these three together — now with evidence

Each ingredient alone has literature; the **interactions** are the research substance,
and the trace pre-analysis (§7.1) turned one hypothesis into a measured fact:

- **Uncapped, migration is nearly worthless — the caps resurrect it.** Across realistic
  portfolios, one zone is the cleanest ~90–100% of hours, so an oracle that migrates
  freely to the hourly-cleanest zone beats perfect *fixed* placement by only 0.1–3.3%
  (consistent with Sukprasert et al., EuroSys'24). But when the dominant clean zone is
  unavailable — the situation a binding peak-power cap creates — the residual
  portfolio's headroom roughly doubles (3.3%→7.4% global; 26.8%→16.6% remains in the
  rotating-leader portfolio) and lead changes become daily events. **Peak caps are not
  a side-constraint; they are the mechanism that makes spatiotemporal shifting and
  migration worth studying.** No prior work treats this coupling as the object of study.
- **Carbon-chasing causes herding, and herding has a measurable price.** If every
  scheduler independently targets the cleanest zone, that DC's power peaks exactly in
  the clean window and the cap clips the policy. The *spillover price* — the mean gap
  between cleanest and second-cleanest zone — is 41–162 gCO₂/kWh depending on
  portfolio (§7.1): the marginal carbon cost of saturation, i.e. the stakes of RQ3.
- **Migration overhead is endogenously priced.** Transfer energy enters the carbon
  objective; downtime enters the SLA objective; arriving on a slower host re-clamps
  `effectiveIpsPerVcpu` (existing `Host.assignVM` behavior) and slows the VM. No
  ad-hoc penalty weights anywhere.
- **Energy-optimal ≠ carbon-optimal once CI varies.** Today the simulator computes
  carbon as one static constant × total kWh (post-hoc), making carbon a rescaled copy
  of energy — as does most "green metaheuristic" literature. With hourly, per-region
  CI, the two decouple: a schedule may burn more kWh yet emit less CO₂. Quantifying
  the divergence is a standalone publishable observation and motivates carbon as a
  genuine objective.

---

## 2. Research gap and positioning

- **Temporal shifting:** Google's Carbon-Intelligent Computing (Radovanović et al.);
  "Let's Wait Awhile" (Wiesner et al., Middleware'21) — single-mechanism, policy-based,
  no migration, caps not first-class.
- **Spatial shifting / migration:** "Free Lunch" (Akoush et al.); electricity-cost geo
  load balancing (Qureshi et al.) — single-objective, simplified overheads.
- **Skeptical quantification:** Sukprasert et al. (EuroSys'24) measure the *limits* of
  spatiotemporal shifting with fixed policies in an *unconstrained* setting. Our
  pre-analysis reproduces their pessimism for dominant-leader portfolios — and shows
  the picture inverts for rotating-leader portfolios and under capacity/power
  contention. We characterize the attainable **Pareto frontier** of *jointly optimized*
  schedules under peak-power constraints with honest migration overhead. We also use
  their published traces (§8), which makes the comparison direct and reviewer-friendly.
- **Carbon-aware multi-objective metaheuristics:** overwhelmingly static-CI, which
  reduces the carbon objective to rescaled energy (see §1). RQ1 measures what that
  misses.
- **Simulators:** CloudSim/CloudSimPlus have power + migration but no hourly CI traces
  or carbon objectives; Vessim/LEAF are carbon-aware but not offline multi-objective
  benchmarks. A reproducible benchmark (real traces + caps + migration +
  measurement-based power + deterministic multi-seed campaigns) is a citable artifact.

**Gap statement:** No existing study jointly optimizes initial placement, task
scheduling, and VM migration across geo-distributed datacenters under time-varying
carbon intensity **and** peak-power constraints, characterizing the carbon–SLA
trade-off as a Pareto frontier with a rigorously fair multi-algorithm methodology.

---

## 3. Research questions

- **RQ1 — Value of the carbon signal.** How much CO₂ does hourly-trace-aware
  scheduling save versus (a) carbon-agnostic and (b) *static annual-average-CI*
  scheduling, at equal SLA attainment? ((b) doubles as the critique of static-CI
  literature; the static-CI optimum coincides with the energy optimum, so RQ1 also
  measures the energy–carbon decoupling.)
- **RQ2 — Mechanism decomposition.** Decompose savings into **spatial** (initial
  placement), **temporal** (deferral/ordering), and **migration** contributions via
  the ablation grid (§7.3). When does migration beat its overhead? Sensitivity to VM
  memory size, WAN bandwidth, migration energy. The trace-derived oracle headrooms
  (§7.1) provide a priori upper bounds to compare achieved savings against.
- **RQ3 — Constraint coupling ("herding").** How do per-DC peak-power caps reshape
  the frontier? Do caps bind precisely in low-CI windows? Does joint optimization
  beat "greedy carbon-first, then repair to caps"? What is the carbon price of a
  demand-response event (cap tightening during grid stress hours)?
- **RQ4 — Algorithmic portfolio.** Which arms of the collaborative portfolio
  contribute which regions of the front in this larger, constrained, time-expanded
  search space? (Same scoreboard as paper 1: per-seed shares, HV_fixed.)
- **RQ5 — Capturable potential (owner proposal, 2026-07-30).** How much of the
  clairvoyant frontier's carbon saving survives without clairvoyance? A neural
  policy is **distilled from the offline oracle**: the metaheuristic campaign's
  optimal migration schedules become imitation-learning labels; the frozen policy
  then runs on held-out days with no future knowledge (§4.6) and is scored against
  the clairvoyant bound, a threshold rule, and a persistence forecast.

---

## 4. System model and problem definition (offline, deterministic)

### 4.1 World model

- Horizon `H` = **72 h** (owner decision D16): three consecutive trace days at the
  engine's native 1-second ticks (259 200 ticks — engine cost modest, search-side
  evaluators are event-based); CI piecewise-constant per hour from the 2022 traces;
  caps piecewise-constant per hour. Migrations amortize across days, diluting the
  end-of-horizon artifact.
- Datacenters `d ∈ D` (4 per scenario, §7.2), each with: a host fleet (existing `Host`
  model, MeasurementBased power), `PUE_d`, carbon trace `CI_d(t)`, peak cap `Cap_d(t)`
  [W], and pairwise WAN bandwidth/latency from the artifact's measured GCP
  inter-region latency matrix (§8). Zones sit in different time zones; traces are UTC.
- Workload: VMs carrying task queues (existing model) with tasks given **release
  times** over the day (diurnal profile) and **SLA classes** (§4.3); instruction
  masses scaled so meaningful work spans hours (§9.1). Offline = all releases,
  lengths, and traces known at t=0 (clairvoyant offline with release dates), exactly
  paper 1's mode: an upper-bound/"potential" study.

### 4.2 Decision variables

1. Task → VM assignment + per-VM execution order (existing chromosome, unchanged).
2. **NEW:** initial VM → datacenter/host placement.
3. **NEW:** sparse migration plan `(vm, epoch, destination-DC)`, epochs at hour
   boundaries (aligned with CI changes), ≤ K migrations per VM per day (proposed K=2).

### 4.3 Objectives and constraints (updated per 2026-07-30 decision: Carbon × SLA)

**Primary plane: Carbon – SLA**, where the SLA axis is optimized as **class-weighted
total tardiness** `Σ_tasks w_class · max(0, turnaround − threshold_class)` and
*reported* as per-class compliance %. Rationale: compliance % is a step function (≤501
distinct values at 500 tasks — plateaus kill search gradients and produce staircase
fronts); tardiness is continuous, classic in scheduling, and hits zero exactly at 100%
compliance. SLA classes: gold/silver/bronze (e.g. 1 h / 4 h / end-of-day; final values
calibrated, §7.4). Makespan is retired for this study: with diurnal release times it
degenerates (dominated by the last arrivals). An AvgWait–Carbon plane may be kept as a
continuity appendix to paper 1 (open decision D11).

Carbon objective:
`CO₂ = Σ_t Σ_d PUE_d · P_IT,d(t) · CI_d(t) · dt + Σ_migrations E_WAN · CI_path`.
Energy (Wh) stays a reported diagnostic everywhere; the energy–carbon divergence
(Δ between energy-optimal and carbon-optimal front points) is a headline number.

Hard constraints: `P_IT,d(t) ≤ Cap_d(t)` ∀t (generalizes paper 1's ceiling; same
Deb's-rules constrained-dominance machinery); no oversubscription; migration
feasibility (per-link bandwidth/concurrency, ≤1 concurrent migration per VM).
Study variants (§7.3) toggle caps. Optional constrained twin: compliance ≥ X% as a
hard constraint (the fully "operational" scenario).

### 4.4 VM migration model (v1: simple, honest, tick-native)

- **Stop-and-copy approximation:** at an epoch boundary the VM pauses; transfer time
  `T_mig = RAM / BW_eff(d,d')`; tasks stall during `T_mig` (hurts tardiness
  endogenously). Pre-copy/dirty-page refinement is future work (only tightens downtime).
- **Migration energy:** overhead power on source and destination during transfer +
  per-GB WAN energy (literature constant), all entering energy/carbon accounting.
- **Speed re-clamping:** existing `Host.assignVM` re-clamps `effectiveIpsPerVcpu` to
  the destination host — heterogeneous-fleet migrations change execution speed for free.
- **Cap coupling:** migration overhead counts against *both* DCs' caps during the
  transfer window — you cannot migrate into a clean window the destination cannot host.

### 4.5 GT-MOSA — the custom multi-objective SA replacing AMOSA (owner decision)

Rationale from paper-1 evidence: SA's strength on this problem is surgical
single-objective refinement, and the collaborative front is built by *specialists*;
AMOSA is a generalist (one chain, domination-amount acceptance, cluster-truncated
archive that discards extremes) and scored 0% everywhere post-#222. GT-MOSA
("gap-targeting MOSA") internalizes the specialist pattern into one budget-fair arm:

1. **Segmented budget:** the 40k evaluations are split into short SA runs
   ("segments"), each with its own reheat + cooling (existing cooling schedules and
   `autoInitialTemperature` machinery reused unchanged).
2. **Extreme anchoring:** segments 1–2 run pure-Carbon and pure-Tardiness
   scalarizations — replicating the proven SA-specialist behavior in-arm.
3. **Gap targeting:** each later segment finds the widest normalized gap between
   adjacent points of the arm's own non-dominated archive
   (`ObjectiveScaleNormalizer` basis) and anneals toward the gap midpoint under a
   **Tchebycheff scalarization** (reaches non-convex front regions where linear
   weights cannot).
4. **Archive reseeding:** each segment warm-starts from the (perturbed) archive
   member nearest its target — the archive doubles as memory, so refinement
   compounds.
5. **Fairness identical to all arms:** every evaluation offered to the ε-pruned
   publication archive (PR #218 rule); exactly 40k evaluations; **no archive-init
   grant** (AMOSA's +10 200 footnote disappears).
6. **Constrained twin for free:** acceptance and archiving wrapped in the Deb's-rules
   comparator, exactly like the existing `*PowerCeiling*` twins.

Honesty requirements: (a) cite the lineage — Ulungu's MOSA and Czyżak &
Jaszkiewicz's Pareto SA used weight-diversified SA chains; the novelty here is
archive-gap-adaptive targeting + the shared publication/fairness protocol; (b) to
preempt "tuned on your own benchmark": freeze GT-MOSA's parameters (segment count,
reheat schedule) by tuning on paper 1's problem or a held-out seed block, disclose
the protocol, and keep AMOSA as the appendix literature baseline it is compared
against. Implementation ≈ a few hundred lines (segment loop, gap picker, Tchebycheff
wrapper, reseeding); everything else is reuse.

### 4.6 Oracle-distilled migration policy — the online track (owner proposal, RQ5)

Answers the standing critique of clairvoyant-offline studies ("nobody knows the
future") constructively. Key insight: raw CI history has no labels — *the offline
campaign is the teacher*. Its hindsight-optimal migration schedules become
imitation-learning targets. This is also why D2 had to resolve to **direct epoch
genes**: a policy-parameter encoding would leave nothing to distill.

1. **Label generation (free byproduct of P1–P4):** per-day oracle `(vm, epoch,
   destination)` decisions from the campaign's best schedules, across scenarios,
   days, and cap regimes.
2. **Supervised imitation:** features per epoch = recent per-zone CI history,
   hour-of-day/day-of-week encodings, **per-DC cap headroom**, per-VM state
   (remaining work, RAM footprint, SLA class, deadline slack). Labels = oracle
   decisions (heavy "no-migration" class imbalance handled explicitly). The state
   features are the point: cap headroom and deadline slack are the herding variables
   no pure CI forecast sees.
3. **Frozen-policy replay on held-out days** (strict temporal split): migrations
   from the policy, task dispatch by the validated heuristics, the full engine
   scores carbon/tardiness. Scored against (a) the clairvoyant frontier (upper
   bound), (b) the threshold rule, (c) a persistence-forecast pipeline ("tomorrow's
   grid = today's" — deceptively strong given CI periodicity). Headline metric: %
   of the oracle-over-threshold gain captured at matched SLA.

Guardrails: model class starts modest (GBDT/small MLP before sequence models — the
contribution is the distillation pipeline, not architecture novelty); seeded
training + committed frozen weights preserve campaign determinism; Python sidecar
beside the existing pandas pipeline; the policy is an **online-track arm only**,
never part of the offline collaborative campaign; detaches cleanly as paper 2b if
the schedule slips.

---

## 5. What exists vs. what must be built (verified against the 2026-07-30 code audit)

### Already in place (reuse as-is or with small extensions)

| Capability | Where (verified) | Notes |
|---|---|---|
| Multi-DC model with per-DC power cap | `model/CloudDatacenter` (`totalMaxPowerDraw`, `totalMomentaryPowerDraw`, `isPowerLimitReached()`, `canAccommodateHost()`); `.cosc [DATACENTERS]`; `config/DatacenterConfig` | Dormant in current campaigns (1 DC). A 4-DC corpus exists (`configs/sampleScenario/`, Istanbul/London/Tokyo/Atlanta) and the archived `oldExperiments/{SampleScenarioRunner,BatchExperimentRunner}` are the only code that ever exercised multi-DC + the carbon/PUE API |
| Host→DC and VM→host routing | `PlacementStrategy/hostPlacement/*` (3 strategies incl. PowerAware); `steps/VMPlacementStep.getCandidateHosts()` filters to the owner's preferred DCs (`User.userSelectedDatacenters`) | The existing multi-DC routing mechanism |
| Static carbon + PUE + cost accounting | `steps/EnergyCalculationStep` (`CarbonIntensityRegion` enum, `setPUE`, `setCarbonIntensityKgPerKWh`, `setElectricityCostPerKWh`); `carbon_footprint_kg` in `SummaryReporter`; `simulated_carbon_kg` in `ParetoFrontReporter` + `MultiObjectiveSimulationResult` | One global scalar × kWh, post-hoc — the RQ1 baseline. Carbon is already *reported* per solution, never optimized |
| Time-resolved power, engine side | `Host.powerSeriesWatts`, `vmPowerSeriesWatts`, `busySeries` (per-tick, migration-tolerant zero-padding `Host.java:635`); coincident fleet peak in `EnergyCalculationStep.computeCoincidentPeakPower()` | Hourly per-DC binning is cheap aggregation of recorded data |
| Time-resolved power, search side | `PowerCeilingEnergyObjective` (sweep-line power profile: peak W, overflow s, avg W), `LaneSchedule` (single source of truth for start/again ticks), `EnergyObjective` (analytic mirror of the tick engine incl. idle gating) | **The** foundation for the carbon objective: same sweep-line, binned per DC per hour, dotted with `CI_d(t)`. Event-based cost — does not scale with horizon length |
| Power-cap constrained optimization | `PowerCeilingViolationObjective` (3 modes); `ConstrainedNonDominatedArchive` (Deb's rules); `FixedAMOSAConstrained`; 5 `*PowerCeiling*` strategy twins; runtime admission `PowerCeilingAdmissionTaskAssignmentStrategy`; calibration `newExperiments/PowerCapCalibrator` (cap tiers from observed peak percentiles); two-phase `CampaignRunner` | Generalize scalar cap → `Cap_d(t)`; reuse the calibration methodology per-DC — and for SLA thresholds (§7.4) |
| Measurement-based energy | `MeasurementBasedPowerModel` (empirical wall-plug profiles, speed–power exponent 1.5, `hardwareScaleFactor`) | Untouched invariant: all published energy/carbon from this model |
| 7-arm portfolio + fairness machinery | `newExperiments/{CampaignRunner,AlgorithmRegistry}`; publication rule via `TaskSchedulingProblem.evaluate()`; `observer/ParetoAnalyzer` (HV_fixed, per-seed collaboration shares — objective-agnostic) | Reuse wholesale; `MOEA_MOEAD` / `MOEA_OMOPSO` wrappers exist unused if the arm set changes (D7) |
| Two-level evaluation | `SimulationEngine.runMultiObjective()`: analytic objectives inside the search, then re-simulates every Pareto solution in the full engine | The validation pattern §7.5 needs, already built |
| Migration-ready detach/attach | `Host.removeVM`/`deallocateResources` ↔ `Host.assignVM` (re-clamps `effectiveIpsPerVcpu`, rebinds cores/GPUs) | Complete pair; everything around it is B5 |
| Determinism | `RandomGenerator` singleton; `CampaignReproducibilityTest` | Traces are deterministic inputs |

**Confirmed absent (full-repo audit):** any location/region/timezone attribute; any
trace/time-series loader; per-DC carbon intensity; time-varying CI/caps/tariffs; task
arrival times (`TaskConfig` has no field; all tasks created at t=0); deadlines or
priorities (SLA is reporting-only: `MetricsCollectionStep.calculateSLACompliance`);
inter-DC network model; migration code of any kind (repo-wide grep: 3 hits, none
functional); rolling-window demand metrics; ramp limits.

### To build

| # | Component | Sketch |
|---|---|---|
| B1 | **Carbon-intensity traces** | `CarbonIntensityProvider`: `getIntensity(dcId, tick)`. Impls: `TraceBasedProvider` (CSV from the artifact, §8; UTC alignment + coverage validation), `StaticRegionProvider` (wraps existing enum / annual means — RQ1 baseline), `SyntheticProvider` (controlled experiments) |
| B2 | **Time-binned per-DC power in the search evaluator** | Extend the `PowerCeilingEnergyObjective` sweep-line + `EnergyObjective` mirror to emit per-DC per-hour energy bins (cost scales with task events, not ticks) . Engine side: aggregate existing per-tick host series by DC and hour |
| B3 | **CarbonObjective + TardinessObjective** | Two new `SchedulingObjective` impls (interface designed for this). Carbon = B2 bins × B1. Tardiness = class-weighted `max(0, completion − deadline)` from `LaneSchedule` ticks. Parity test: constant trace ⇒ carbon ≡ k × energy exactly |
| B4 | **Per-DC (time-varying) caps** | `Cap_d(t)` schedule in config; constraint value = max-over-bins excess (scalar for Deb's rules). Scenario option: demand-response windows derived from the trace's dirtiest/stress hours |
| B5 | **Migration mechanics (engine)** | `VmState.MIGRATING`; pause/transfer/resume around the detach/attach pair; RAM/BW time; overhead power both sides; WAN energy; per-link concurrency. Needs a per-tick placement hook in the `VMExecutionStep` loop (none exists) + an inter-DC bandwidth/latency matrix (artifact data) |
| B6 | **Chromosome extension** | `[task→VM + order (existing)] ⊕ [VM→DC initial placement] ⊕ [sparse migration genes, ≤K/VM]`. Surgical mutation moves (retarget/add/remove migration; re-place VM) in the PR #208/#222 style; `RepairOperator` extension for cap/bandwidth/preference feasibility |
| B7 | **Baselines** | Carbon-greedy placement; follow-the-renewables threshold policy (also the rule-based alternative to direct encoding); **CI-threshold admission** (adapt `PowerCeilingAdmissionTaskAssignmentStrategy` — the Google-CICS-style baseline, nearly free); static-CI arm; no-migration arm; LPT/WA/EnergyAware seeds retained |
| B8 | **Workload redesign + release times + SLA classes** | New columns in `[TASKS]`/`TaskConfig` (+ `InitializationStep`): release tick, SLA class. `LaneSchedule` gains release-awareness. Instruction masses ×~10³ (minutes–hours per task), diurnal release profile (shape from the Azure/Google traces bundled with artifact v2). Same ~500-task genome; same pure/RNG-free generation discipline as LOG16 |
| B9 | **Reporting** | Per-DC hourly CSV (power, cap, CI, CO₂); campaign columns: gCO₂ split (compute/idle/migration/WAN), per-DC peak kW, cap-binding hours, migration count/downtime, per-class compliance %; `ParetoAnalyzer` unchanged |
| B10 | **Campaign scenario builder generalization** | `newExperiments/ExperimentConfig.toExperimentConfiguration()` hard-codes one DC ("DC-Experiment", 400 kW); generalize to a DC-portfolio spec (name, fleet, PUE, trace zone, cap schedule) |
| B11 | **GT-MOSA implementation** (§4.5) | Segment loop + archive-gap picker + Tchebycheff scalarization wrapper + archive reseeding over the existing SA stack (`SAConfiguration`, cooling, operators, `ObjectiveScaleNormalizer`, `NonDominatedArchive`); Deb's-rules constrained twin; frozen-parameter tuning protocol |

---

## 6. Design decisions

**Resolved (2026-07-30 brainstorm):**

- **Independence (owner): standalone study**, not a sequel — reuses the existing
  framework only; no dependence on the previous paper's results or timeline (§1).

- **Objectives = Carbon × SLA** (owner). SLA optimized as class-weighted tardiness,
  reported as compliance % (§4.3). Energy demoted to diagnostic.
- **Data = EuroSys'24 artifact traces, year 2022** (owner: "2022 is close enough").
  Cite v2 DOI (§8). Datacenter locations are free variables, chosen by trace analysis
  (owner: heritage 4-city "not set in stone").
- **D1 Offline** with perfect trace hindsight (paper-1 mode; upper-bound framing).
  Optional robustness pass: re-evaluate best schedules under ±X% perturbed traces.
- **D7 Arm set (owner, 2026-07-30): AMOSA is dropped from the collaborative arm set**
  (0% contribution everywhere post-#222). Replaced by a **custom multi-objective SA
  (working name GT-MOSA, §4.5)**. Arm set stays at seven with paper-1 symmetry:
  GA-Tardiness, GA-Carbon, SA-Tardiness, SA-Carbon (dominance-archive variants),
  NSGA-II, SPEA-II, GT-MOSA. ▸ Recommended: retain AMOSA as an appendix baseline
  (already implemented — a free head-to-head for GT-MOSA's validation).
- **D11 AvgWait (owner, 2026-07-30):** secondary *reported* performance metric
  (per-solution diagnostic column + analysis section), not a Pareto axis and not a
  separate campaign study.
- **D2 Migration representation: direct sparse epoch genes**, ≤K/VM/day (K=2
  default), threshold policy as baseline arm — required by the oracle-distillation
  track (§4.6): a policy-parameter encoding would leave nothing to distill.
- **RQ5/online track (owner, 2026-07-30):** neural migration policy distilled from
  the offline oracle, evaluated on held-out days without future knowledge (§4.6).
  Amends D1: offline remains the core; the online replay is a separate track.
- **D10 Idle-host power (owner, 2026-07-30): idle power is ASSUMED (not free)** in
  this study. Powered-on hosts draw the measured idle floor
  (`REFERENCE_IDLE_POWER = 75.79 W` × `hardwareScaleFactor`); the engine's 0 W
  idle-gating and its `EnergyObjective` mirror are switched consistently (parity
  test). P0 detail to fix: "on" = full horizon (recommended — simplest, conservative,
  service-fleet realistic) vs. first-to-last-use window. The 0 W-gating variant is
  kept as an ablation for paper-1 comparability; deviation from paper 1 disclosed.
- **D12 SLA classes (owner, 2026-07-30): three** (gold/silver/bronze), thresholds
  calibrated per §7.4; class mix ratio remains a scenario knob.
- **D13 S1 fourth zone (owner, 2026-07-30): Spain stays** — S1 = ES/FI/CH/BE, all-EU,
  as pre-analyzed (26.8% headroom).
- **D8 Fleet composition (owner, 2026-07-30): identical fleets in every DC.**
  Owner rationale, stronger than mere experimental control: the MeasurementBased
  power model is calibrated on **one** physical reference system — heterogeneous
  fleets via `hardwareScaleFactor` would be synthetic scaling presented as
  measurement. Heterogeneity deferred until new wall-plug measurement campaigns
  exist. Goes in the method section as-is.
- **D14 PUE (owner, 2026-07-30): uniform 1.2** across all DCs.
- **D15 Cap tiers (owner, 2026-07-30):** per-DC `PowerCapCalibrator` percentile
  calibration at **90 / 60 / 30 %** feasibility targets, as in paper 1.
- **D17 Tenancy (owner, 2026-07-30): single tenant**, as in paper 1.
- **D18 Online-track dispatcher (owner, 2026-07-30): validated greedy heuristics**
  place tasks in the §4.6 replay; no rolling metaheuristic bursts.
- **D16 Horizon (owner, 2026-07-30): 72 hours** — three consecutive trace days per
  experiment. **Reason (cited at owner request): the end-of-horizon effect.** A
  migration is an investment — cost paid at transfer time (downtime, transfer
  energy), recouped over the VM's *remaining* runtime. In a short (24 h) window, a
  migration late in the day has too little remaining runtime to amortize, so the
  optimizer artificially learns "never migrate near the end" — an artifact of where
  the tape is cut, not of real operations. A 72 h horizon lets migrations amortize
  across days, dilutes the artifact, and additionally captures multi-day grid
  structure (windy→calm sequences, weekday↔weekend transitions), at 3× simulation
  length. Window-selection rule (§7.2) picks 72 h spans; the migration cap stays
  per-day (≤2/VM/day ⇒ ≤6 over a horizon).

**Open: none.** All design decisions are resolved as of 2026-07-30. What remains are
the verification chores in §11 (direct-vs-lifecycle CI; S3's HK feed) and P0
implementation details flagged inline (e.g. the D10 "on-window" definition).

---

## 7. Experimental design

### 7.1 Trace pre-analysis (2022; `scripts/proposal_trace_preanalysis.py`)

Definitions: *migration headroom* = CO₂ savings of an oracle running each hour in the
hourly-cleanest zone vs. the best **fixed** zone (upper bound on spatial shifting
beyond perfect initial placement); *noLeader* = same with the dominant zone removed
(proxy for a saturated/capped clean DC); *spill* = mean gap between cleanest and
2nd-cleanest zone (the marginal carbon price of herding); *sw/day* = cleanest-zone
identity changes per day.

| Portfolio | headroom | sw/day | spill | leader (share) | noLeader |
|---|---|---|---|---|---|
| **S1 rotating leaders: ES/FI/CH/BE** | **26.8%** | 2.9 | 41 g | FI (39%) | 16.6% |
| S1-alt global: CISO/DE/KR/ERCO | 3.3% | 1.2 | 124 g | CISO (83%) | 7.4% |
| **S2 dominant leader: PL/DE/FR/GB** | 0.1% | 0.2 | **135 g** | FR (99%) | 0.1% |
| **S3 flat control: SG/TW/HK/IN-MH** | 0.0% | 0.0 | 55 g | HK (100%) | 0.2% |
| heritage: TR/GB/JP-TK/US-SE-SOCO | 1.0% | 0.2 | 162 g | GB (96%) | 1.0% |

S1 was found by brute-forcing all 4-zone portfolios over GCP-region zones — ES/FI/CH/BE
is the global maximum, and all four are real Google Cloud regions per the artifact's
mapping (Madrid, Hamina, Zurich, St-Ghislain), with real measured inter-region latencies
in the artifact. This table belongs in the paper's motivation section: it reproduces the
EuroSys'24 "limitations" result for dominant-leader portfolios and shows where — and
under what constraint pressure — spatiotemporal scheduling has real value.

### 7.2 Scenarios (each stresses one mechanism; mirrors paper 1's 3-scenario shape)

- **S1 — Rotating leaders (migration showcase):** ES/FI/CH/BE. Cleanest zone rotates
  ~3×/day; 26.8% oracle headroom; migration and temporal shifting should genuinely pay.
- **S2 — Dominant leader (herding/caps showcase):** PL/DE/FR/GB. FR is cleanest 99% of
  hours at 99 gCO₂/kWh mean; uncapped, everything goes to FR and migration is
  worthless (0.1%). Under a binding FR cap, every spilled kWh pays +135 g — the caps
  carry the whole story, and spillover *ordering* (GB 235 < DE 481 < PL 819) matters.
- **S3 — Flat control (falsification):** SG/TW/HK/IN-MH. No signal to exploit
  (headroom 0.0%); any claimed savings is noise or overhead. Real-data replacement for
  a synthetic control. (HK's CV of 0.00 suggests an estimated/static feed — verify
  before final selection; TW/SG/IN-MH have genuine but tiny variation.)
- 72 h windows within 2022 (D16): one high-variance and one low-variance window per scenario
  (disclosed rule, e.g. deciles of intra-day CI std), since CI variance is the
  resource the optimizer exploits. 2022's European energy-crisis context is disclosed.

### 7.3 Arms, campaign shape, ablations

- Arms (7, paper-1 symmetry): GA-Tardiness-dom, GA-Carbon-dom, SA-Tardiness-dom,
  SA-Carbon-dom, NSGA-II, SPEA-II, **GT-MOSA** (§4.5; AMOSA demoted to appendix
  baseline) + policy baselines (B7). Campaign shape as
  paper 1: 10 seeds (baseSeed=200), 40k evaluations/arm, per-seed collaboration
  shares + HV_fixed, single-threaded JVM per study, separate processes for
  parallelism.
- Ablation grid (the RQ1/RQ2/RQ3 engine): {CI: static annual | hourly} ×
  {migration: off | on} × {caps: off | calibrated tiers | demand-response window}.
  Headline corners: static/no-mig (≈ paper-1 world), hourly/no-mig (placement +
  deferral only), hourly/mig (full), each ± caps.
- Metrics: total gCO₂ (split compute/idle/migration/WAN), Wh, class-weighted
  tardiness, per-class compliance %, per-DC peak kW, cap-binding hours, migration
  count + downtime, savings at iso-SLA vs. carbon-agnostic and static-CI arms,
  energy–carbon divergence, CUE; **AvgWait as the secondary performance metric**
  (owner decision — reported per solution, analyzed, never an axis).

### 7.4 Calibration (reusing paper 1's method)

- **Caps:** per-DC tiers from observed uncapped peak percentiles at target
  feasibility (the `PowerCapCalibrator` method), applied per scenario. Optional
  demand-response variant: cap tightening during the trace's dirtiest hours.
- **SLA thresholds:** same philosophy — run carbon-agnostic and carbon-greedy
  baselines, take the turnaround distribution, set class thresholds at percentiles
  that make deadlines bind for a target fraction of tasks. Without this, the
  carbon-greediest schedule may hit 100% compliance and the front collapses to a
  point.

### 7.5 Validation

Final fronts re-simulated in the full engine (`runMultiObjective` phase-3 pattern);
reproducibility test extended to traces (same seed + same trace ⇒ bit-identical
fronts); constant-trace parity (carbon ≡ k × energy) as a standing unit test.

---

## 8. Data plan (resolved)

- **Drive signal:** `combined_carbon.csv` from the EuroSys'24 artifact — Sukprasert,
  Souza, Bashir, Irwin, Shenoy, *"On the Limitations of Carbon-Aware Temporal and
  Spatial Workload Shifting in the Cloud"* — **hourly average CI, 123 zones,
  2020–2022, gCO₂/kWh, UTC; CC-BY-4.0** (Electricity Maps data republished). Cite
  the artifact DOI (v2: `10.5281/zenodo.10790855`; v1 `10682335` is the 40 MB
  variant whose 22 MB carbon CSV we verified directly — v2 adds the bundled
  Google/Azure workload traces, single 6.6 GB tar, no need to mirror it in-repo).
  Year 2022 selected (owner decision). To verify from the artifact's prep scripts:
  whether values are direct or lifecycle intensity — disclose whichever.
- **Inter-DC latencies:** the artifact's `gcp_latency_matrix.csv` (measured
  Google-Cloud inter-region RTTs) + `gcp_dc_zonecode_mapper.json` (region→zone map)
  parameterize the migration network model with citable real data.
- **Static-CI baseline:** per-zone 2022 annual means computed from the same traces
  (internal consistency). The GCP fossil-CO₂ dataset (Zenodo `10065794`, annual
  national totals) is **motivation/context only** — it is neither electricity-specific
  nor sub-annual, so it cannot drive scheduling.
- **Diurnal release profile:** shape from the Azure/Google cluster traces (bundled in
  artifact v2; also independently public), applied via the pure/RNG-free generator.
- **In-repo:** commit only the extracted per-zone 2022 columns actually used
  (≈ 8760 rows × ≤6 zones, trivial size), with a provenance README; the full
  artifact stays a cited download.

## 9. Phased roadmap

- **P0 — Temporal foundations:** B1 traces + B2 time-binned power + B3 objectives +
  B8 workload/SLA redesign + parity/reproducibility tests.
- **P1 — Spatial study (no migration, no caps):** multi-DC placement genes + B7
  baselines → RQ1 and the spatial share of RQ2 on S1/S2/S3.
- **P2 — Constraints:** B4 caps + calibration → RQ3 herding analysis.
- **P3 — Migration:** B5 mechanics + B6 genes + policy baseline → completes RQ2.
- **P4 — Campaign + paper (offline core).**
- **P5 — Oracle distillation + online replay (§4.6, RQ5):** consumes P4's schedules,
  so it cannot start earlier and cannot delay P0–P4; detaches as paper 2b if needed.
  (Supersedes the earlier trace-perturbation idea — forecast realism is now tested
  by the distilled policy itself.)

### 9.1 Workload redesign note (the biggest modeling lift)

Paper 1's LOG16 workload has seconds-scale makespans (measured 15–20 s in committed
results) — three orders of magnitude below the hourly CI signal, so carbon-awareness
would have zero leverage. Fix: keep the ~500-task genome (search space unchanged),
scale instruction masses ~10³ (tasks run minutes–hours), spread release times
diurnally, attach SLA classes. Engine cost stays modest (259 200 ticks per 72 h run, small
fleet); the search-loop evaluators are event-based sweep-lines whose cost does not
grow with horizon. `SimulationClock.timeStep` stays 1 s (hard-coded `final`).

## 10. Risks

| Risk | Mitigation |
|---|---|
| Search-space blow-up from migration genes | Epoch-restricted, K-capped sparse genes; surgical moves; repair operator |
| Tardiness objective plateaus (many schedules meet all deadlines) | SLA calibration §7.4 makes deadlines bind by construction |
| Carbon savings look small | Pre-analysis already bounds what's achievable per scenario (§7.1) and the frontier/herding/decomposition contributions stand independent of magnitude; S1 chosen for real signal |
| Idle-power semantics diverge from paper 1 (D10: idle floor now assumed) | Consistent switch in engine + analytic mirrors, guarded by the parity test; 0 W-gating ablation kept for paper-1 comparability; deviation disclosed |
| ML track scope creep, or the NN fails to beat persistence + threshold | Staged as P5 consuming campaign outputs (cannot delay P0–P4); modest-model-first protocol; a small capturable gap is itself a publishable finding |
| Migration overhead parameters contested | Artifact latency matrix + literature WAN-energy constants + sensitivity sweep; stop-and-copy is conservative |
| Trace anomalies (2022 energy crisis; HK flat feed) | Disclosed year choice; day-selection rule; verify S3 zone feeds before final selection |
| Determinism regressions | Deterministic traces; extend `CampaignReproducibilityTest` in P0 |
| "You tuned your own algorithm on your own benchmark" (GT-MOSA) | Freeze parameters on paper-1's problem / held-out seeds before the campaign; disclose protocol; AMOSA kept as appendix literature baseline; cite MOSA lineage (Ulungu; Czyżak & Jaszkiewicz PSA; AMOSA) |

## 11. Immediate next steps

1. All design decisions resolved (§6); nothing blocks P0.
2. P0 spike: trace loader + constant-trace parity test + sweep-line binning
   profiling + idle-floor semantics switch (D10) with its "on-window" definition.
3. Extract and commit the ES/FI/CH/BE + S2/S3 2022 trace columns with provenance
   README.
4. Verify direct-vs-lifecycle CI and the S3 zone feeds (HK variance ≈ 0) from the
   artifact's prep scripts; re-run `scripts/proposal_trace_preanalysis.py` if zones
   change.
5. GT-MOSA prototype + frozen-parameter tuning on paper-1's problem (can proceed in
   parallel with P0).

---

*Discussion artifact; companion to `HANDOFF.md` (paper-1 state). Pre-analysis numbers
are reproducible via `scripts/proposal_trace_preanalysis.py` against the artifact CSV.*
