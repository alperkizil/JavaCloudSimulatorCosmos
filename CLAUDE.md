# Claude Code Instructions for JavaCloudSimulatorCosmos

## Session Handoff — READ FIRST

**The article is submitted and frozen:** *Energy Aware Hybrid CPU and GPU Virtual Machine
Scheduling*, submitted to *IEEE Transactions on Parallel and Distributed Systems*
(Submission ID `fb3a7e80-40c4-4b06-a7b8-86fe6d0de244`). Older notes, the tag and
`article1problem.md` call it "Article 1". The submitted state is the protected tag
`article1-submitted` (commit `322a0ff`), archived on Zenodo as
[10.5281/zenodo.23161629](https://doi.org/10.5281/zenodo.23161629), with a protected copy
on branch `Article1-article1-submitted`. Its three studies are the entry points in
`src/main/java/com/cloudsimulator/newExperiments/` (`MakespanEnergyExperiment`,
`WaitingTimeEnergyExperiment`, `PowerCeilingExperiment`). The reported campaigns are the
three `*_17_09_2026_*` folders in `docs/ExperimentResults/`, summarised in
`docs/ExperimentResults/experiment_outcomes.md`. `article1problem.md` is the history of
the power-cap study (its §5 figures are from the 28 Aug run, superseded by 17 Sep).

**Current focus: the multi-DC carbon study (reopened by the owner on 2026-10-05).** Read
`docs/proposals/MultiDC-Carbon-PeakPower-Migration-Proposal.md` and `HANDOFF.md` §0.
Two of the proposal's "resolved" decisions predate the article's final results and must
be revisited with the owner before they are implemented:

- **D15 (cap tiers at 90/60/30 % feasibility percentiles, "as in paper 1").** The article
  dropped percentile calibration because it barely constrained anything
  (`article1problem.md` §3.1) and anchors tiers to a reference peak instead (P_ref;
  90/80/70/60/50 %, `PowerCapCalibrator.DEFAULT_ANCHOR_FRACTIONS`).
- **D7 (replace AMOSA with GT-MOSA because AMOSA "scored 0 % everywhere").** AMOSA's
  temperature-scaled mutation (PR #250, 17 Sep 2026) made it competitive; see
  `docs/ExperimentResults/amosa_summary.md`.

## Owner Rules — standing (2026-10-05)

1. **Nothing under `docs/` is deleted, moved or rewritten** — result folders (line
   endings included), proposals and notes stay exactly as committed.
2. **Do not change the framework code or anything the `newExperiments` studies use**:
   `model/`, `engine/`, `steps/`, `config/`, `enums/`, `utils/`, `factory/`,
   `PlacementStrategy/` (metaheuristics, objectives, operators), `observer/`,
   `multiobjectivePerformance/`, `newExperiments/` itself, and the five post-run scripts
   `PostRunScripts` calls (`recompute_hv.py`, `plot_scenario_pareto.py`,
   `statistical_tests.py`, `plot_power_ceiling.py`, `generate_interactive_report.py`).
   New work, including the multi-DC study, goes in its own package and extends or wraps
   the framework instead of editing it.
3. **Legacy code is parked.** `FinalExperiment/`, `gui/`, `metaheuristic/localsearch/`
   and the `reporter/` CSV writers are not used by the studies; do not delete or refactor
   them until the owner decides. The archived experiment mains (`oldExperiments/`) were
   removed on 2026-10-05 and are recoverable from tag `article1-submitted`, including
   `SampleScenarioRunner` and `BatchExperimentRunner`: the only runners that executed the
   four-DC `configs/sampleScenario/` corpus end to end with the carbon/PUE settings
   (`EnergyCalculationStep.setPUE` / `setCarbonIntensity`), which the multi-DC proposal
   cites; that API itself is also covered by `EnergyMetricsStepTest` and
   `ReportingStepTest`. To read one:
   `git show article1-submitted:oldExperiments/com/cloudsimulator/SampleScenarioRunner.java`
4. **Ask before acting.** Propose every change (files, branches, PRs, anything outside
   the session) and wait for the owner's confirmation. Verify claims against the code,
   not the docs. Sub-agent work goes to Sonnet.
5. **Fable review before posting.** Before pushing to or posting on a PR, have a Fable
   sub-agent double-check the work, and report its findings to the owner.
6. **No Claude attribution.** Commit messages, PR descriptions and GitHub comments carry
   no Claude Code attribution: no "Generated with Claude Code" line, no session link, no
   `Co-Authored-By: Claude` or `Claude-Session` trailer.

## Build Environment Notes

### Maven in cloud sessions
`pom.xml` is valid (Java 17, JavaFX 21, MOEA Framework 4.5) and works on the owner's
machine, but Maven cannot download dependencies in cloud sessions. Use `javac` with the
jars in `lib/`:

```bash
# Compile the framework and studies (GUI excluded: JavaFX is not in lib/)
find src/main/java -name "*.java" -not -path "*/gui/*" | xargs javac -cp "lib/*" -d target/classes
```

### Running the studies
Run from the repository root (outputs go to `results/`, post-run scripts are read from
`scripts/`; both paths are relative):

```bash
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.MakespanEnergyExperiment
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.WaitingTimeEnergyExperiment
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.PowerCeilingExperiment
```

Each run writes `results/<StudyId>_<dd_MM_yyyy_HH_mm_ss>/` (`MakespanVsEnergy`,
`WaitingTimeVsEnergy` or `PowerCeilingWaitingTimeVsEnergy`) and then runs the Python
post-processing (needs `numpy`, `pandas`, `matplotlib`). Each JVM is single-threaded; the
three studies can run as separate JVMs, but never parallelise runs inside one JVM
(`RandomGenerator` and MOEA's `PRNG` are process-global).

### Tests
Tests are `main()` programs (no JUnit). Run them from the repository root:

```bash
find src/test/java -name "*.java" | xargs javac -cp "target/classes:lib/*" -d target/test-classes
java -cp "target/test-classes:target/classes:lib/*" com.cloudsimulator.HostTest
```

Exit codes are not enough: 14 programs exit with status 1 on a failed check, but
`ConfigTest`, `ExecutionStepsTest`, `GenerationalGAVerificationTest`,
`HostPlacementConstrainedTest`, `HostPlacementStepTest`, `InitializationStepTest`,
`MeasurementBasedPowerModelTest`, `UserDatacenterMappingStepTest` and
`VMPlacementStepTest` always exit 0 — read their output for `FAILED`
(`ConfigTest` and `MeasurementBasedPowerModelTest` print values only and check nothing).
Known failure: `LaneConsistencyCheck` (predicted makespan is 1 s shorter than the
simulated one on some random schedules; a framework-level inconsistency, left as is
under rule 2). All other test mains pass.

### `lib/`
Project code imports only `org.moeaframework.*` (and `javafx.*` in `gui/`). The other jars
are MOEA Framework's runtime dependencies. `moeaframework-4.5-sources.jar` holds MOEA's
source for reference (`unzip -p lib/moeaframework-4.5-sources.jar <path>`).

### Large Files & Required Reading
At the start of every session, read `README.md` completely (in chunks if needed), then
this file's handoff section.

## Project Architecture

- **Studies:** `newExperiments/` (`CampaignRunner` → `ParetoAnalyzer` → `ExperimentReporter`, arms in `AlgorithmRegistry`, parameters in `AlgorithmParameters`, infrastructure in `ExperimentConfig`)
- **Metaheuristics:** `PlacementStrategy/task/metaheuristic/` — GA and SA (with dominance archive and power-cap-constrained twins); MOEA Framework wrappers for NSGA-II, SPEA-II and AMOSA in `metaheuristic/moea/`
- **Cooling schedules:** `metaheuristic/cooling/` (Simulated Annealing)
- **Termination conditions:** `metaheuristic/termination/`
- **Genetic operators:** `metaheuristic/operators/`
- **Objectives:** `metaheuristic/objectives/`
- **Power model:** `model/MeasurementBasedPowerModel` (measurement provenance in `power_log_template_v2.txt`)

## Patterns to Follow

When implementing new metaheuristic algorithms:
1. Follow the `GAConfiguration` / `SAConfiguration` builder pattern
2. Follow the `GAStatistics` / `SAStatistics` tracking pattern
3. Implement `TaskAssignmentStrategy` interface for the facade
4. Reuse existing operators (`MutationOperator`, `RepairOperator`) where applicable
5. Use `RandomGenerator.getInstance()` for reproducibility
