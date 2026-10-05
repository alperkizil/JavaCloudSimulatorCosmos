# JavaCloudSimulatorCosmos

A discrete-time cloud datacenter simulator in Java for **offline task scheduling**. It
models datacenters, hosts, virtual machines and tasks at a one-second resolution, with
energy taken from a **power model built on wall-plug measurements of real hardware**. On
top of the simulator it compares single- and multi-objective metaheuristics on time–energy
trade-offs, optionally under a datacenter **power cap**.

This code underlies the article *Energy Aware Hybrid CPU and GPU Virtual Machine
Scheduling*, submitted to *IEEE Transactions on Parallel and Distributed Systems*
(Submission ID `fb3a7e80-40c4-4b06-a7b8-86fe6d0de244`). The submitted version is archived
as tag `article1-submitted` — DOI
[10.5281/zenodo.23161629](https://doi.org/10.5281/zenodo.23161629).

---

## Contents

1. [What it models](#what-it-models)
2. [Repository layout](#repository-layout)
3. [Requirements and build](#requirements-and-build)
4. [Running the studies](#running-the-studies)
5. [Using the simulator directly](#using-the-simulator-directly)
6. [Scheduling strategies](#scheduling-strategies)
7. [Configuration files and the GUI](#configuration-files-and-the-gui)
8. [Tests](#tests)
9. [Further documentation](#further-documentation)
10. [Reproducing the article](#reproducing-the-article)
11. [License and citation](#license-and-citation)

---

## What it models

- **Infrastructure.** Datacenters (host capacity, power budget), hosts of three compute
  types (`CPU_ONLY`, `GPU_ONLY`, `CPU_GPU_MIXED`), VMs owned by users, and tasks of eleven
  workload types (`SEVEN_ZIP`, `DATABASE`, `FURMARK`, `IMAGE_GEN_CPU`, `IMAGE_GEN_GPU`,
  `LLM_CPU`, `LLM_GPU`, `CINEBENCH`, `PRIME95SmallFFT`, `VERACRYPT`, `IDLE`).
- **Execution.** Time advances in 1-second ticks. Each vCPU is bound 1:1 to a physical
  core (no oversubscription) and runs its own FIFO lane of tasks; a VM's effective
  per-vCPU speed is the lower of its requested speed and its host's per-core speed. A task
  of length *L* instructions occupies a lane for ⌈*L* / speed⌉ ticks.
- **Power and energy.** `MeasurementBasedPowerModel` uses wall-plug measurements of a Dell
  Precision 7920 workstation with an Nvidia 5080 GPU (October–November 2025; measurement
  log in `power_log_template_v2.txt`):
  - idle power 75.79 W per active host; a host with no running task draws 0 W;
  - a fixed incremental power per workload (e.g. `VERACRYPT` 19.25 W, `SEVEN_ZIP`
    130.29 W, `FURMARK` 352.18 W) for every busy lane;
  - speed scaling: incremental power × (lane speed / reference speed)^1.5. The reference
    speed defaults to 3 GIPS (`MeasurementBasedPowerModel.DEFAULT_REFERENCE_IPS`); the
    studies replace it with the median host per-core speed (2.8 GIPS for the study fleet)
    through `EnergyObjective.setHosts`, which also pushes it into every host's power model.

  The power-model name in a `.cosc` host line is parsed but does not change the energy
  figures: every host uses the measurement-based model.
- **Metrics.** Makespan, waiting / turnaround / execution times, IT and facility energy
  (PUE, default 1.5), coincident peak power of the whole fleet, carbon footprint (regional
  constants) and cost.

## Repository layout

| Path | Contents |
|---|---|
| `src/main/java/com/cloudsimulator/model/` | Datacenter, host, VM, task, user, CPU core / GPU binding, power models |
| `…/engine/` | `SimulationEngine`, `SimulationContext`, step and listener interfaces |
| `…/steps/` | The ten simulation steps (initialisation → reporting) |
| `…/PlacementStrategy/` | Host placement, VM placement, task assignment and the metaheuristics |
| `…/observer/` | Campaign analysis: `ParetoAnalyzer` (HV, HV_fixed, GD, IGD, Spacing, Eps+, contribution) and `ExperimentReporter` (CSV output) |
| `…/newExperiments/` | The three study entry points and the shared campaign driver |
| `…/config/` | `.cosc` parser and configuration classes |
| `…/multiobjectivePerformance/PerfMet/` | Quality-indicator implementations used by `ParetoAnalyzer` |
| `…/enums/`, `…/utils/`, `…/factory/` | Enumerations, seeded `RandomGenerator`, clock, logger, power-model factory |
| `…/FinalExperiment/`, `…/reporter/`, `…/gui/` | Legacy runners, legacy CSV reporters, JavaFX config generator |
| `src/test/java/` | Test programs (see [Tests](#tests)) |
| `scripts/` | Python post-processing and analysis tools |
| `configs/` | Example `.cosc` configuration files |
| `docs/` | Detailed documentation, committed campaign results and proposals |
| `lib/` | MOEA Framework 4.5 and its dependencies |

## Requirements and build

- **Java 17** or later.
- **Python 3** with `numpy`, `pandas` and `matplotlib` for the post-run scripts.
  `scripts/analyze_power_cap_campaign.py` also needs `scipy`; `scripts/results_explorer.py`
  needs `tkinter` (and optionally `openpyxl`).
- **Maven** is optional; it is needed only for the JavaFX GUI.

Compile with `javac` and the jars in `lib/`:

```bash
# Framework and studies (the GUI needs JavaFX, which is not in lib/)
find src/main/java -name "*.java" -not -path "*/gui/*" | xargs javac -cp "lib/*" -d target/classes
```

With Maven: `mvn compile`, and `mvn javafx:run` for the GUI.

## Running the studies

The three studies of the article live in `src/main/java/com/cloudsimulator/newExperiments/`.
Run them from the repository root:

```bash
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.MakespanEnergyExperiment
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.WaitingTimeEnergyExperiment
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.PowerCeilingExperiment
```

| Study | Objectives | Extra |
|---|---|---|
| `MakespanEnergyExperiment` | makespan, IT energy | — |
| `WaitingTimeEnergyExperiment` | average waiting time, IT energy | — |
| `PowerCeilingExperiment` | average waiting time, IT energy | datacenter power cap |

All settings are in code; the entry points take no arguments. Each JVM runs single-threaded.
The three studies can run as separate JVMs at the same time, but runs must never be
parallelised inside one JVM (the random generators are process-wide).

### Study setup

| | |
|---|---|
| Infrastructure | 1 datacenter; 40 hosts: 16 CPU-only (16 cores, 2.5 GIPS per core), 12 GPU-only (8 cores, 4 GPUs, 2.8 GIPS), 12 mixed (32 cores, 4 GPUs, 3.0 GIPS) |
| VMs | 60: per compute type 8 fast, 8 medium and 4 slow (5 / 2 / 0.5 GIPS per vCPU requested, capped at the host's core speed); 4 vCPUs each; GPU and mixed VMs carry 2 / 1 / 1 GPUs |
| Workload | 500 tasks per scenario; lengths from 16 log-spaced values between 0.5 and 25.16 billion instructions |
| Scenarios | Balanced (250 CPU / 250 GPU tasks), GPU_Stress (100 / 400), CPU_Stress (400 / 100) |
| Algorithms (7 arms) | `GA_<Time>_Dominance`, `GA_Energy_Dominance`, `SA_<Time>_Dominance`, `SA_Energy_Dominance`, `NSGA-II`, `SPEA-II`, `AMOSA` |
| Seeds | 10 per scenario (200–209) |
| Budget | 40,000 objective evaluations per run: GA, SA and AMOSA stop at 40,000 evaluations (AMOSA additionally spends 10,200 on building its initial archive); NSGA-II and SPEA-II run 200 generations of 200 |

`<Time>` is `Makespan` or `WaitingTime`. Every arm publishes the non-dominated set of all
solutions it evaluated. Algorithm parameters are in `AlgorithmParameters.java`; their
rationale is in `docs/MetaheuristicTaskScheduler.md`.

**Power-cap study.** `PowerCeilingExperiment` runs in two phases. Phase 1 runs the seven
arms without a cap and derives, per scenario, a reference peak *P_ref*: for each seed,
the peak power of the lowest-waiting-time schedule found by any arm; *P_ref* is the median
over seeds. Phase 2 re-runs every arm with the cap built into its search (Deb's
constrained-domination rules) at 90, 80, 70, 60 and 50 % of *P_ref*. Quality indicators
are computed on cap-feasible solutions only.

### Output

Each campaign writes `results/MakespanVsEnergy_<dd_MM_yyyy_HH_mm_ss>/` (likewise
`WaitingTimeVsEnergy_…` and `PowerCeilingWaitingTimeVsEnergy_…`; `results/` is ignored by
Git), including:

- `experiment_summary.csv`, `plot_options.json` and, per scenario, `scenario_N_pareto_graph_data.csv`,
  `scenario_N_performance_metrics.csv`, `scenario_N_algorithm_pareto_fronts.csv`,
  `scenario_N_seed_collaboration.csv` and `scenario_N_solution_details.json.gz`;
- for the power-cap study also `power_cap_calibration.csv`, `feasibility_summary.csv`,
  `pareto_3d_all.csv`, `pareto_3d_feasible.csv`, the per-tier `*_by_cap.csv` files and
  `algorithm_log.txt`.

The run then calls the Python post-processing in this order (`PostRunScripts`):
`recompute_hv.py`, `plot_scenario_pareto.py`, `statistical_tests.py`,
`plot_power_ceiling.py` (power-cap study only) and `generate_interactive_report.py`. They add
quality-indicator CSVs, plots, statistical tests and a self-contained
`interactive_report.html`. A missing Python installation only produces a warning; to
re-run the post-processing later:

```bash
java -cp "target/classes:lib/*" com.cloudsimulator.newExperiments.PostRunScripts results/<folder>
```

`scripts/results_explorer.py` is an interactive viewer for result folders. Committed
example campaigns are in `docs/ExperimentResults/`.

## Using the simulator directly

A simulation is a sequence of steps executed against a shared `SimulationContext`. This
example loads a `.cosc` file and runs one schedule:

```java
SimulationEngine engine = new SimulationEngine();
engine.configure("configs/sample-experiment.cosc");   // also sets the random seed

TaskExecutionStep tasks = new TaskExecutionStep();
EnergyCalculationStep energy = new EnergyCalculationStep();

engine.addStep(new InitializationStep(engine.getConfiguration()));
engine.addStep(new HostPlacementStep(new PowerAwareLoadBalancingHostPlacementStrategy()));
engine.addStep(new UserDatacenterMappingStep());
engine.addStep(new VMPlacementStep(new BestFitVMPlacementStrategy()));
engine.addStep(new TaskAssignmentStep(new WorkloadAwareTaskAssignmentStrategy()));
engine.addStep(new VMExecutionStep());
engine.addStep(tasks);
engine.addStep(energy);
engine.run();

System.out.printf("Makespan: %d s, average waiting time: %.2f s%n",
    tasks.getMakespan(), tasks.getAverageWaitingTime());
System.out.printf("IT energy: %.6f kWh, peak power: %.1f W%n",
    energy.getTotalITEnergyKWh(), energy.getPeakTotalPowerWatts());
```

Two further steps are available: `MetricsCollectionStep` (builds a `SimulationSummary`
with SLA and percentile metrics) and `ReportingStep` (writes per-entity CSV reports).
Configurations can also be built in code as an `ExperimentConfiguration` and passed to
`engine.configure(...)`; this is how the studies define their infrastructure
(`ExperimentConfig.toExperimentConfiguration()`).

## Scheduling strategies

| Stage | Strategies (package `PlacementStrategy/`) |
|---|---|
| Host → datacenter | `FirstFit`, `SlotBasedBestFit`, `PowerAwareLoadBalancing` (used by the studies) |
| VM → host | `FirstFit`, `BestFit` (used by the studies), `LoadBalancing` |
| Task → VM, heuristics | `FirstAvailable`, `ShortestQueue`, `RoundRobin`, `WorkloadAware`, `EnergyAware`, `LPT`; `PowerCeilingAdmission` (a power-cap admission wrapper, not used by the studies) |
| Task → VM, metaheuristics | Generational GA and Simulated Annealing (single best, or with a dominance archive); NSGA-II, SPEA-II and AMOSA through MOEA Framework 4.5; power-cap-constrained versions of GA, SA, NSGA-II, SPEA-II and AMOSA. MOEA/D and OMOPSO wrappers also exist but are not used by the studies. |

A metaheuristic decides both which VM runs each task and the order of tasks on each VM.
Objectives: makespan, average waiting time, energy, load balance, and energy with peak
power tracking for the power cap. Building blocks for new algorithms: cooling schedules
(`metaheuristic/cooling/`), termination conditions (`termination/`), selection
(`selection/`) and crossover / mutation / repair operators (`operators/`). In the studies
the heuristics `LPT`, `WorkloadAware` and `EnergyAware` provide the metaheuristics'
starting solutions.

## Configuration files and the GUI

A `.cosc` file declares an experiment in sections (lines starting with `#` are comments):

```
[SEED]
42

[DATACENTERS]
<count>
name,maxHostCapacity,maxPowerWatts

[HOSTS]
<count>
ipsPerCore,cpuCores,computeType,gpus,ramMB,networkMbps,storageMB,powerModelName

[USERS]
<count>
name,dc1|dc2,gpuVMs,cpuVMs,mixedVMs,<task counts>
                                 (task counts in this order: SEVEN_ZIP, DATABASE, FURMARK,
                                  IMAGE_GEN_CPU, IMAGE_GEN_GPU, LLM_CPU, LLM_GPU, CINEBENCH,
                                  PRIME95SmallFFT, optionally VERACRYPT)

[VMS]
CPU:<count>                      (also GPU:<count>, MIXED:<count>)
user,ipsPerVcpu,vcpus,gpus,ramMB,storageMB,bandwidthMbps

[TASKS]
SEVEN_ZIP:<count>                (one block per workload type)
name,user,instructionLength
```

`configs/sample-experiment.cosc` is a complete example. The JavaFX application
`com.cloudsimulator.gui.ConfigGeneratorApp` (`mvn javafx:run`) generates `.cosc` files
for a range of seeds; it does not run simulations.

## Tests

The tests are standalone `main()` programs (no JUnit). Compile and run them from the
repository root:

```bash
find src/test/java -name "*.java" | xargs javac -cp "target/classes:lib/*" -d target/test-classes
java -cp "target/test-classes:target/classes:lib/*" com.cloudsimulator.HostTest
```

| Area | Test programs |
|---|---|
| Model | `CloudDatacenterTest`, `HostTest`, `VMTest`, `TaskTest`, `UserTest`, `CoreBindingCheck` |
| Configuration and steps | `ConfigTest`, `InitializationStepTest`, `HostPlacementStepTest`, `HostPlacementConstrainedTest`, `UserDatacenterMappingStepTest`, `VMPlacementStepTest`, `ExecutionStepsTest`, `EnergyMetricsStepTest`, `ReportingStepTest` |
| Power model | `MeasurementBasedPowerModelTest`, `PowerCeilingEnergyObjectiveTest` |
| Algorithms | `GenerationalGAVerificationTest`, `LaneConsistencyCheck` |
| Campaign analysis | `observer.ExperimentObserverTest`, `observer.ByCapAnalysisTest`, `observer.ParetoAnalyzerParityTest`, `newExperiments.CampaignReproducibilityTest` |

Prefix each name with `com.cloudsimulator.`. Fourteen of the programs exit with status 1
when a check fails. The other nine only print `PASSED` / `FAILED` per check, so read their
output; of these, `ConfigTest` and `MeasurementBasedPowerModelTest` only print values and
check nothing. Known failure: `LaneConsistencyCheck` (on some random schedules the
predicted makespan is one second shorter than the simulated one; optimised schedules
match).
`newExperiments.ParityRun` and `observer.SyntheticPowerCeilingFolder` are helper programs,
not tests.

## Further documentation

| Document | Topic |
|---|---|
| `docs/infrastructure.md` | Infrastructure and power model in detail |
| `docs/VMExecution.md` | Lane-based execution and timing |
| `docs/MetaheuristicTaskScheduler.md` | Algorithms, encoding, operators and parameters |
| `docs/PerformanceMetrics.md` | Quality indicators and contribution metrics |
| `docs/ExperimentResults/experiment_outcomes.md` | Results of the 17 Sep 2026 campaigns |
| `docs/ExperimentResults/amosa_summary.md` | Effect of AMOSA's temperature-scaled mutation |
| `docs/proposals/` | The multi-datacenter carbon study proposal |
| `article1problem.md`, `HANDOFF.md` | Development history of the article |

## Reproducing the article

*Energy Aware Hybrid CPU and GPU Virtual Machine Scheduling* — IEEE Transactions on
Parallel and Distributed Systems, Submission ID `fb3a7e80-40c4-4b06-a7b8-86fe6d0de244`.

Check out tag `article1-submitted`, compile, and run the three entry points. The campaigns
reported in the article are committed in `docs/ExperimentResults/`:

- `MakespanVsEnergy_17_09_2026_09_50_39`
- `WaitingTimeVsEnergy_17_09_2026_10_30_00`
- `PowerCeilingWaitingTimeVsEnergy_17_09_2026_10_56_32`

## License and citation

Licensed under the GNU General Public License v3.0 (see `LICENSE`).

To cite the software version used in the article:
DOI [10.5281/zenodo.23161629](https://doi.org/10.5281/zenodo.23161629).
