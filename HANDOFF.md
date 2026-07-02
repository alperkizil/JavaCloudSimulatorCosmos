# Session Handoff — JavaCloudSimulatorCosmos Review & Fixes

For the next Claude instance (or collaborator). State as of 2026-07-02, repo `main` at
merge of PR #209. Read `README.md` (in chunks) and `CLAUDE.md` first, as always.

---

## 1. Research background (original brief, from the project owner)

> We are trying to publish a scientific journal article on Task Scheduling in Cloud
> Computing. Our main objectives are Makespan(s) – Energy Consumption(Wh),
> Avg. Wait Time(s) – Energy Consumption(Wh), and Avg. Wait Time(s) – Energy
> Consumption(Wh) in a power-capped datacenter environment.
>
> We are trying to find a collaborative Pareto front using:
> a. Genetic Algorithm with Dominance Archive (separate variants optimizing the two objectives)
> b. Simulated Annealing with Dominance Archive (separate variants optimizing the two objectives)
> c. NSGA-II
> d. SPEA-II
> e. AMOSA
>
> They are seeded with WorkloadAware and EnergyAware heuristics to start from a
> promising point.
>
> Start with `MakespanEnergyExperiment.java` in
> `src/main/java/com/cloudsimulator/newExperiments/` (the other objective pairs also
> have runners in that folder). Check the mechanisms for: (1) initialization schema
> (Datacenter, Host, VM, User, Task); (2) the simulation engine; (3) the simulation
> context; (4) each simulation step — host placement, user–datacenter mapping, VM
> placement, task scheduling, task/VM execution, energy calculation and reporting.
>
> Constraints: Energy consumption must come from the MeasurementBased model, this is
> OFFLINE scheduling, and oversubscription is NOT allowed.

Preferences: sub-agent work goes to **Sonnet**; discuss findings before changing code;
ask rather than assume.

---

## 2. What was completed

### 2.1 Full pipeline review (multi-agent, adversarially verified)

Eleven area reviews covering every mechanism in the brief, deduplicated, then every
non-trivial finding adversarially verified (all 14 CONFIRMED) plus 12 info-level
observations. Headline verdicts:

- **Invariants HOLD**: energy comes from `MeasurementBasedPowerModel` on every published
  path (enforced by `ExperimentConfig.requireMeasurementModel`); scheduling is truly
  offline (all tasks at t=0); no oversubscription (all 5 resource dimensions checked
  strictly at VM placement; GPU/core binding consistent; lane/GPU caps enforced at
  execution). Reproducibility holds (same seed ⇒ identical results).
- The core simulator (execution loop, energy integration, placement) was sound.
  `LaneSchedule` mirrors `VM.executeOneSecond` exactly; surrogate objectives match
  re-simulation bit-exactly. All confirmed defects concerned the **fairness/semantics
  of the 7-arm algorithm comparison** — and all were fixed.

### 2.2 Fixes merged via PR #208 (all verified, full descriptions in the PR body)

1. **Objective-scale normalization** for GA/SA weighted-sum fitness
   (`ObjectiveScaleNormalizer`; the 0.001 "tiebreaker" was 36.6% of the energy term in
   the Energy-primary arms before). Raw objectives/archives/published fronts untouched.
2. **WorkloadAware seed heuristic** now models the GPU concurrency cap (delegates to
   `LaneSchedule`).
3. **GA elite caching** (no re-evaluation of unchanged elites) + GA arms switched to
   `FitnessEvaluationsTermination(40 000)`.
4. **AMOSA archive-initialization budget grant**: `γ·softLimit·(1+hillClimb)` = 10 200
   evaluations granted on top of the 40 000 search budget (both wrappers + aux methods).
5. **NSGA-II/SPEA-II use domain operators** (`TaskSchedulingVariation`: uniform
   crossover + REASSIGN/SWAP_ORDER mutation + repair — same family as GA/SA) instead of
   real-coded SBX+PM. Executor paths removed; algorithms constructed directly.
6. **Order-carrying MOEA encoding**: a dispatch-order `Permutation` variable joins the
   per-task assignment genes; per-VM order = permutation filtered by assignment. All
   decode/encode logic consolidated into static helpers on `TaskSchedulingProblem`.
   AMOSA mutation switched to COMBINED.
7. **Constrained AMOSA comparator injection**: `FixedAMOSAConstrained` reflectively
   installs its Deb-rule comparator into the MOEA library's private `AMOSA.comparator`
   so `initialize()` is no longer constraint-blind (fail-fast if library layout changes).
8. **Surgical REASSIGN mutation**: reassignment no longer calls
   `rebuildTaskOrdering()` (which erased ALL accumulated order information); it now
   surgically moves the task between order lists. Shared-operator change, affects all
   arms equally.
9. **Minors**: independent operator RNG streams
   (`RandomGenerator.deriveStreamSeed`); `ParetoFront.getObjectiveRanges` max sentinel
   fixed (`-Double.MAX_VALUE`); task generation decoupled workload type from
   instruction length (length phase advances by 1 per `lcm(T,L)` tasks — previously a
   parity lock meant the huge 25G/40G tasks always hit a fixed subset of CPU workload
   types).

### 2.3 Fix merged via PR #209

- **Speed–power scaling exponent 2.0 → 1.5** (`MeasurementBasedPowerModel
  .POWER_SCALING_EXPONENT`). Rationale: real full-range CPU DVFS behaves ~f^1.5
  (Vmin-pinned linear region below ~2 GHz; ~cubic only in boost). Under the default
  fleet (effective per-vCPU speeds 0.5G–3.0G after host clamping, reference = 2.8G host
  median) this gives a **14.7× incremental-power span** and **2.4× energy-per-instruction
  span** fast-vs-slow — matching the "clock-capped vs unlocked modern desktop core"
  envelope. Trade-off survives (energy/instruction ∝ √speed + host idle floor rewards
  short makespans).
- **Build warning**: `POWER_SCALING_EXPONENT` is a compile-time constant — javac
  INLINES it into referencing classes. Full rebuild required whenever it changes.

### 2.4 How things were verified (harnesses were session-local, NOT in the repo)

Smoke harnesses lived in the session scratchpad and are gone; commit messages document
what each verified. Key properties confirmed (recreate tests as needed):

- Prediction ↔ simulation equality: re-simulated front solutions reproduce predicted
  WaitingTime exactly and Energy to ~1e-16 (surrogate and simulator share the model).
- Same-seed determinism across full rebuild+run cycles for GA/SA/NSGA-II.
- Encoding round-trip: non-canonical per-VM orders survive encode→decode exactly;
  same assignment + different order changes the WaitingTime objective.
- Budget accounting: GA ≈ budget +≤1 generation of unique evaluations; AMOSA total =
  budget + init cost exactly.
- Order sensitivity, GPU-cap ground truth (2 GPU tasks on a 1-GPU VM = 2 ticks), and
  surgical-reassign invariants (once-per-task; untouched VMs bit-identical).

---

## 3. What remains to be done

### 3.1 Re-run all three campaigns (REQUIRED before citing any results)

Algorithms, workload mix (type–length decoupling), AND the energy curve (exponent 1.5)
all changed — every previously produced number is stale.

```bash
# Compile (no Maven — network-restricted; see CLAUDE.md)
find src/main/java oldExperiments -name "*.java" -not -path "*/gui/*" \
  | xargs javac -cp "lib/*" -d target/classes

# The three studies (results land in results/<experimentId>/)
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.MakespanEnergyExperiment
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.WaitingTimeEnergyExperiment
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.PowerCeilingExperiment
```

Scale warning: each study = 7 arms × 10 seeds × 3 scenarios × 40k evaluations, plus
re-simulation of every Pareto solution; PowerCeiling is two-phase (uncapped + capped
re-runs). Many hours on a small container — commit results per study as they finish,
or run on a real machine.

### 3.2 Paper-text items (no code required, decided + owed to the methodology section)

- **Pooled fronts**: per-algorithm fronts in the reports are non-dominated UNIONS
  pooled across the 10 seeds (best-of-10 envelope). Must be disclosed; consider adding
  per-seed hypervolume mean±std to `ParetoAnalyzer`/`ExperimentReporter` for
  distributional evidence.
- **Per-arm evaluation accounting**: GA ≈ 40 000 unique (+≤1 generation overshoot);
  SA = 40 000 (incl. ~101 init/auto-temperature sampling); NSGA-II/SPEA-II = 40 000;
  AMOSA = 40 000 annealing + 10 200 archive-initialization granted separately (an
  intrinsic phase of AMOSA — disclose, don't present all arms as identical-total).
- **Speed–power law**: incremental power ∝ (clamped VM speed / fleet-median host
  speed)^1.5; energy per instruction ∝ √speed. Defensible via full-range DVFS argument.

### 3.3 Known-open items (deliberately NOT fixed — decide or disclose)

- **EnergyAware reference-IPS basis** (skipped by owner choice): the greedy seed
  heuristic calibrates speed scaling from the VM-list median
  (`EnergyAwareTaskAssignmentStrategy.initializeContext`, ~line 100) while the real
  model uses the host-list median (2.8G). Affects warm-start seed quality only; fix =
  setter + wire from `AlgorithmRegistry`.
- Info-level observations from the review (all verified, none affect published numbers
  under defaults): `HostConfig.powerModelName` is dead config (host measurement model
  is hardcoded-on); `VM.calculatePowerDraw` uses a simplified fallback (VM-level
  bookkeeping only, not published energy); `UserConfig` positional-constructor field
  mismatch; power-aware host placement is inert with 1 DC and the DC power-admission
  check is weak (irrelevant at 400 kW budget vs ~22 kW worst-case draw);
  `HostPlacementStep`/task-assignment failures are metric-only (silent); makespan has a
  +1 tick convention that per-task turnaround doesn't; `VMExecutionStep` has no
  infinite-loop guard; PowerCeiling doc constants in `ExperimentSpec` are stale.

### 3.4 Practical notes for the next instance

- **Branch workflow**: feature branch `claude/cloud-scheduling-simulation-review-wa4dvz`;
  its PRs #208 and #209 are MERGED — restart the branch from latest `main` before any
  new work (`git fetch origin main && git checkout -B <branch> origin/main`).
- Owner preferences: sub-agents on Sonnet; discuss before changing code; verify claims
  against code, not docs (several javadocs were found stale during the review).
- The full review report (14 defects + 12 observations with evidence) was delivered as
  a chat attachment, not committed; section 2 above is the surviving summary.
