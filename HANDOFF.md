# Session Handoff — JavaCloudSimulatorCosmos Review, Fixes & Collaborative-Pareto Intervention

For the next Claude instance (or collaborator). State as of 2026-07-10, repo `main` at
merge of PR #222. Read `README.md` (in chunks) and `CLAUDE.md` first, as always.

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
> Constraints: Energy consumption must come from the MeasurementBased model, this is
> OFFLINE scheduling, and oversubscription is NOT allowed.

Preferences: sub-agent work goes to **Sonnet**; discuss findings before changing code;
ask rather than assume.

Campaign shape: 7 arms × 10 seeds (baseSeed=200) × 3 scenarios × 40 000 evaluations/arm.
Entry points in `newExperiments/`: `MakespanEnergyExperiment`, `WaitingTimeEnergyExperiment`,
`PowerCeilingExperiment`; shared driver `CampaignRunner`; arms built in `AlgorithmRegistry`.

---

## 2. What was completed

### 2.1 Pipeline review & fairness fixes (PRs #208, #209 — merged 2026-07-02)

Full multi-agent review of every mechanism; invariants HOLD (MeasurementBased energy on
every published path, truly offline, no oversubscription, same-seed determinism). All
confirmed defects concerned the fairness/semantics of the 7-arm comparison and were fixed:
objective-scale normalization for GA/SA weighted fitness; GPU-cap-aware WorkloadAware seed;
GA elite caching + FitnessEvaluationsTermination(40k); AMOSA archive-init budget grant
(10 200 evals on top of 40k); NSGA-II/SPEA-II on domain operators (TaskSchedulingVariation);
order-carrying MOEA encoding (dispatch permutation); constrained-AMOSA comparator injection;
surgical REASSIGN mutation; type/length-decoupled task generation (lcm block); speed–power
exponent 2.0 → 1.5 (`MeasurementBasedPowerModel.POWER_SCALING_EXPONENT` — compile-time
constant, full rebuild required if changed).

### 2.2 The collaborative-Pareto intervention (PRs #218, #219, #220 — merged 2026-07-10)

Motivated by the lopsided 2026-07-08 campaign (SA arms supplied 69–86% of the
Makespan–Energy universal front; SA-Energy alone 61%/43%/22% per scenario in
WaitingTime–Energy; NSGA-II/AMOSA at ~0). Root-cause analysis found four interacting
mechanisms; all four were fixed. **Every pre-2026-07-10 absolute number is stale**
(workload changed, see Change 3).

**Change 1 — one publication rule for all 7 arms (PR #218).** Previously GA/SA published
every evaluated solution (unbounded `NonDominatedArchive`, 1% epsilon near-duplicate
filter), NSGA-II/SPEA-II only the final generation (≤200), AMOSA a cluster-truncated
archive (~50–100) — so `ParetoContribution` measured publication convention. Now
`TaskSchedulingProblem` (and `PowerCeilingSchedulingProblem` for the constrained twins,
via `ConstrainedNonDominatedArchive`, Deb's rules) takes an optional publication archive
offered every evaluated solution inside `evaluate()` — the single choke point through
which all MOEA evaluations pass exactly once (bytecode-verified: offspring-only
`evaluateAll`, AMOSA's init grant included). Wrappers publish `archive.getMembers()`;
the library snapshot stays in `lastMoeaResult` for plotting. A latent bug was fixed as a
prerequisite: `evaluate()` never wrote objectives onto the decoded `SchedulingSolution`
(all-zero `objectiveValues`). `NSGA2Configuration.archiveEpsilonFraction` (0.01 via
`AlgorithmParameters`) makes pruning symmetric with GA/SA.

**Change 4 — study-matched two-seed warm start (PR #219).** New
`LPTTaskAssignmentStrategy` (descending-length greedy over `LaneSchedule` completion-tick
projections, GPU cap included, RNG-free; overrides `assignAll` — the interface default
dispatches in caller order and can never realize LPT; also exposed as an `"LPT"` baseline
label). `AlgorithmRegistry.newPrimarySeedHeuristic()`: primary seed = **LPT for the
Makespan study, WorkloadAware for the WaitingTime/PowerCeiling studies**; EnergyAware is
the second seed for the MOEA arms (GA/SA arms keep their single affinity-matched seed).
Chosen from a measured seed-quality probe (assignment-only, canonical order — exactly how
seeds are consumed): LPT strictly dominates WA in the Makespan–Energy plane in all three
scenarios (makespan 28→23.3 / 37→24 / 22.3→13.3 s, energy lower too) while WA is ~2×
better than LPT on average wait — the classical LPT-vs-mean-flow-time trade-off.
Also fixed the EnergyAware reference-IPS basis (host-fleet median via
`calculateReferenceIpsFromHosts`, same basis as `EnergyObjective`; the old §3.3 open
item). Measured **behaviorally inert** under the default fleet (identical seed
assignments — the reference is a common-mode factor the greedy argmin is invariant to);
an explicit no-arg constructor keeps the 31 legacy `FinalExperiment` call sites compiling.

**Change 2 — scoreboard (PR #220, additive only).** The pooled universal front is a
best-of-all-seeds envelope and the zero-epsilon dominance cut hands full credit to
razor-thin winners (observed: the arm with the best per-seed HV had zero
ParetoContribution). Added: (a) `ParetoAnalyzer.analyzeSeedCollaboration` — per-seed
all-arms universal fronts (union logic unchanged) with near-tie credit, written to a new
`scenario_N_seed_collaboration.csv` (per-seed shares, MEAN/STDDEV per arm,
Universal_Pareto mean±std trailer) and a table in the interactive report; (b) near-tie
credit `paretoContributionCountEps`: within `CONTRIB_REL_EPS = 3e-3` relative on BOTH
objectives (scaled to the universal point's own magnitude), **nearest-match** (first-match
is non-monotonic in the tolerance); 3e-3 was empirically calibrated — 1e-4 recovers zero
real near-ties; (c) `HV_fixed` (the recompute_hv.py fixed-reference formula, the only HV
comparable across arms/seeds) now emitted as a trailing column in
`performance_metrics.csv` and surfaced in the report. The strict union, exact-match
contribution columns, and legacy HV column are untouched byte-for-byte
(`ParetoAnalyzerParityTest` still passes against the Python oracle).

**Change 3 — LOG16 workload (PR #220).** The old bimodal lengths
({100,200,300,500}M ×2 + 25G/40G) made 80% of tasks makespan-invisible (every length
≤0.5G costs exactly 1 tick on all five effective lane speeds 0.5/2.0/2.5/2.8/3.0 G/s),
collapsing makespan to ~3 plateaus (the ~20s wall / staircase fronts). New
`ExperimentConfig.instructionLengths`: 16 log-spaced values, bottom pinned at 0.5G (the
one-tick threshold), top re-fit to 25.16G so **total instruction mass matches the old
workload** (+0.85%/−0.43%/−0.43% per scenario, −0.004% overall). Distinct per-lane tick
classes 3 → {14,9,8,8,7}; 1-tick fraction 0.80 → 0.35. `generateTasks` (pure, RNG-free,
lcm decoupling) untouched; never put RNG in it — it runs before `engine.setRandomSeed`.

### 2.3 Population-arm search-quality fix (PR #222 — merged 2026-07-10)

After the intervention, a first M–E run (`MakespanVsEnergy_10_07_2026_13_53_14`) showed
the SA arms still out-refining every population arm. Root cause was search quality, not
scoring; two fixes, both affecting only GA-dominance / NSGA-II / SPEA-II (SA and AMOSA
verified **byte-identical** — bit-equal fronts across A/B variants):

- `AlgorithmParameters.mutationRate` 0.05 → **0.004** (= 2/N at the studies' 500-task
  workloads). The per-gene scan previously mutated ~25 genes per offspring — a random
  walk that prevented convergence while SA refined with 1–4 surgical moves. 0.004 chosen
  from a 0.002/0.004/0.008 sensitivity sweep.
- `CrossoverOperator` UNIFORM/TWO_POINT now derive each offspring's per-VM execution
  order from its donor parent's dispatch precedence instead of `rebuildTaskOrdering()`,
  which had reset order genes to canonical ascending-index on ~95% of pairings, leaving
  the order dimension evolutionarily dead (the crossover-side dual of PR #208's
  surgical-REASSIGN fix).

Budget unchanged (40k evals/arm). Tests green: `ParetoAnalyzerParityTest`,
`CampaignReproducibilityTest`, `ExperimentObserverTest`, `GenerationalGAVerificationTest`.

### 2.4 Validation results — post-#222 campaign runs (current numbers)

Two full 10-seed campaigns were run by the owner on `main` after PR #222 and their
interactive reports reviewed on 2026-07-10: `MakespanVsEnergy_10_07_2026_14_52_59` and
`WaitingTimeVsEnergy_10_07_2026_14_52_58`. **These supersede every earlier number**,
including the pre-#222 `MakespanVsEnergy_10_07_2026_13_53_14` run cited in previous
handoffs. The raw CSVs are NOT in the repo yet (`newExperimentResults/` still holds the
2026-07-03 reference runs) — ask the owner for the result folders before deeper analysis.

- **Makespan–Energy** (per-seed mean shares, S1/S2/S3): GA-E 31/29/46%,
  SA-E 27/23/13%, SPEA-II 18/12/19%, NSGA-II 15/26/13%, GA-MS 5/7/3%, SA-MS 4/5/6%,
  **AMOSA 0/0/0**. SA combined fell to 31/28/19% (was 53/46/38% pre-#222, 86/82/69% at
  the July-08 baseline) — the population arms now genuinely compete. Pooled universal
  fronts 71/98/59 points (pre-#222: 44/65/44); per-seed universal fronts ~43/47/38 with
  HV_fixed ≈ .70/.73/.68. Mean HV_fixed ranking: GA-E/SA-E/NSGA-II cluster at ~.51–.63,
  then SPEA-II .46–.50, SA-MS .43–.51, GA-MS and AMOSA lowest (~.25–.31).
- **WaitingTime–Energy** (first run on the full intervention; per-seed mean shares):
  GA-E 38/15/42%, NSGA-II 27/39/25%, SPEA-II 16/25/18%, SA-WT 8/8/10%, GA-WT 7/11/4%,
  SA-E 5/4/2%, **AMOSA 0/0/0**. The old failure mode is gone: SA-Energy fell from
  61/43/22% (baseline) to single digits. Fronts are dense: pooled universal 215/147/181
  points, per-seed universal ~129/88/139. Mean HV_fixed: GA-E clearly best (.64–.73),
  then NSGA-II .60–.62 ≈ SA-E .59–.62, SPEA-II .52–.58, AMOSA .30–.42, GA-WT/SA-WT
  lowest (dedicated wait-time arms occupy a narrow corner).
- **AMOSA regression (new open issue).** AMOSA's contribution is exactly 0 in all six
  scenario×study combinations, under BOTH the strict pooled cut and the near-tie
  per-seed credit (every seed 0.000000). Pre-#222 it held 15/11/27% per-seed in M–E; its
  own search is unchanged (#222 verified bit-equal fronts), so the improved population
  arms now dominate the regions it used to cover. Its HV_fixed shift (.33–.35 → .25–.31)
  is frame movement, not behavior: `recompute_hv.py` normalizes by the run's own pooled
  ideal/nadir, so **HV_fixed is comparable across arms/seeds within a run, not across
  campaigns**. See §3.3 for the decision this forces.
- The pooled-vs-per-seed divergence persists and remains diagnostic (e.g. NSGA-II strict
  pooled contribution is 0 in M–E S1 and S3 while its per-seed means are 15%/13%).
- Tests green throughout: `ExperimentObserverTest`, `ParetoAnalyzerParityTest`,
  `CampaignReproducibilityTest` (same-seed fronts bit-identical, incl. on LOG16).

---

## 3. What remains to be done

### 3.1 Campaign re-runs (REQUIRED before citing any results)

Makespan–Energy and WaitingTime–Energy are done on post-#222 `main` (2026-07-10 14:52
runs, §2.4). Still needed on current `main`:

```bash
find src/main/java oldExperiments -name "*.java" -not -path "*/gui/*" \
  | xargs javac -cp "lib/*" -d target/classes

java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.PowerCeilingExperiment
```

Also outstanding: commit (or otherwise obtain) the two 14:52 result folders — only their
interactive reports have been seen so far; the CSVs are needed for the CONTRIB_REL_EPS
re-validation below and for the paper's tables.

Each is a single-threaded JVM (one core); the three studies can safely run as separate
concurrent JVMs (fully independent processes/results dirs). Do NOT try to parallelize
runs inside one JVM: `RandomGenerator` and MOEA's `PRNG` are process-global singletons —
concurrent in-JVM runs would break same-seed determinism. (A verified scenario-split
multiprocess tool was built and byte-parity-tested but **not adopted** — owner decision
2026-07-10; it is not in `main`. Ask before resurrecting it.)

After the fresh runs: **re-validate `CONTRIB_REL_EPS = 3e-3`** (`ParetoAnalyzer`) against
the new CSVs — it was calibrated on the pre-LOG16 campaigns.

### 3.2 Paper-text items (methodology section)

- Lead collaboration claims with the **per-seed mean±std shares**
  (`scenario_N_seed_collaboration.csv`); keep the strict pooled numbers as the
  conservative variant and present the divergence between them as diagnostic.
- Disclose: near-tie credit is **shared** credit (shares can sum >100% — equally-good
  points within 0.3% credit multiple arms); two contribution definitions coexist
  (exact-match 1e-9, near-tie 3e-3 nearest-match).
- Cite **HV_fixed only**. The legacy per-pair HV is not comparable across arms/seeds by
  construction (`metric_hv.png` from `plot_metrics_comparison` still plots legacy HV —
  regenerate or omit it).
- Per-arm evaluation accounting: GA ≈ 40k (+≤1 generation); SA = 40k (incl. ~101
  auto-temperature probes); NSGA-II/SPEA-II = 40k; AMOSA = 40k + 10 200 archive-init
  grant (intrinsic phase — disclose, don't present as identical totals).
- Publication rule: all 7 arms publish the non-dominated set of ALL evaluated solutions
  under the same 1% epsilon-fraction filter (since PR #218).
- Seeding: study-matched primary heuristic (LPT/WA) + EnergyAware; include the
  seed-quality probe table (PR #219 body) as the justification; note the EnergyAware
  calibration fix is dimensional-consistency only (behaviorally inert, measured).
- Operator settings (PR #222): mutation rate 2/N (0.004 at N=500) and order-preserving
  crossover belong in the method section; cite the 0.002/0.004/0.008 sensitivity sweep.
  Also state that HV_fixed's normalization frame is per-run (pooled ideal/nadir), so
  HV_fixed values must not be compared across differently-configured campaigns.
- Workload: LOG16 rationale + mass-matching (so cross-workload comparisons aren't
  confounded); pooled per-arm fronts are 10-seed unions (already owed disclosure).
- Speed–power law: incremental power ∝ (clamped VM speed / host-median)^1.5.

### 3.3 Known-open items (deliberately not done — decide or disclose)

- **AMOSA contributes zero everywhere post-#222** (§2.4) — the "7-arm collaborative
  front" is currently a 6-arm front. Decide with the owner: (a) present AMOSA as an
  honest negative result (it was only ever competitive against the pre-#222 weakened
  population arms), or (b) attempt AMOSA-side tuning (e.g. its neighborhood/cooling —
  its 40k+10 200-eval budget is already the largest). Do NOT retune the other arms to
  re-admit AMOSA; that would undo PR #222's premise.
- `experiment_summary.csv` has no `HV_fixed` column (only `performance_metrics.csv`).
- `metric_hv.png` is legacy-HV-only (see §3.2).
- The `"LPT"` standalone baseline label exists in `AlgorithmRegistry` but is not part of
  the scored arm set — usable for a heuristic-baseline table if the paper wants one.
- S2 (GPU-stress) remains the most concentrated scenario — the 56-GPU-slot bottleneck is
  a real infrastructure property, worth a sentence rather than a fix.
- Info-level observations from the 2026-07 review (unchanged, none affect published
  numbers under defaults): dead `HostConfig.powerModelName`; `VM.calculatePowerDraw`
  fallback; `UserConfig` positional-constructor mismatch; inert power-aware host
  placement at 1 DC; metric-only placement failures; makespan +1 tick convention;
  no `VMExecutionStep` infinite-loop guard; stale PowerCeiling doc constants.

### 3.4 Practical notes for the next instance

- Branch workflow: PRs #218–#220 (branch `claude/collaborative-pareto-evidence-n2wy0h`),
  #221 (handoff update) and #222 (branch `claude/nsgaii-speaii-search-quality-en581n`)
  are all MERGED — always restart a work branch from latest `main`
  (`git fetch origin main && git checkout -B <branch> origin/main`).
- Owner preferences: sub-agents on Sonnet; discuss/propose before changing code; verify
  claims against code, not docs.
- Campaign outputs land in `results/<experimentId>/`; the owner's committed reference
  results live under `newExperimentResults/`. The Python pipeline
  (`PostRunScripts` → recompute_hv / plots / stats / interactive report) needs pandas,
  numpy and matplotlib.
- The full root-cause evidence for the intervention (verified file:line analysis) was
  delivered as session documents (`collaborativeparetoevidence.md`, the solution
  proposal); PR bodies #218–#220 carry the surviving summaries.
