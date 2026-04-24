# 2. Workload & Scenarios

## 2.1 Tasks in plain English

Every experiment run submits **500 tasks**. Each task has a *size*
(how much compute it needs) and a *type* (CPU-only or GPU-required).
The type decides which VMs are eligible; the size decides how long
the task occupies the chosen VM.

## 2.2 Workload types

Nine distinct workload types are used, drawn directly from the
simulator's `WorkloadType` enum:

- **CPU workloads** (`CPU_WORKLOADS` at
  `ScenarioComparisonExperimentRunner.java:148-151`):
  `SEVEN_ZIP`, `DATABASE`, `LLM_CPU`, `IMAGE_GEN_CPU`, `CINEBENCH`,
  `VERACRYPT`.
- **GPU workloads** (`GPU_WORKLOADS` at lines 153–156):
  `FURMARK`, `IMAGE_GEN_GPU`, `LLM_GPU`.

Each workload type has an empirical CPU/GPU utilization profile in
`MeasurementBasedPowerModel` (e.g. `SEVEN_ZIP = 100 % CPU, 0 % GPU`;
`FURMARK = 8 % CPU, 100 % GPU`; `LLM_GPU = 12 % CPU, 12 % GPU`). These
profiles drive the energy calculation in
[`03-objectives.md`](03-objectives.md).

## 2.3 The 80 / 20 bimodal size distribution

Real workloads are **heavy-tailed**: most jobs are small, a few are
huge (the classic "80 / 20 rule"). The simulator reproduces that with
ten fixed instruction-length slots split into two groups
(`ScenarioComparisonExperimentRunner.java:164-170`):

- **Eight small slots (80 %)** — 100 M, 200 M, 300 M, 500 M
  instructions, each repeated twice — the everyday jobs.
- **Two whale slots (20 %)** — 25 B and 40 B instructions — the rare
  but disruptive large jobs that cause scheduling hotspots.

Tasks are drawn deterministically (not randomly) via
`INSTRUCTION_LENGTHS[i % 10]`
(`ScenarioComparisonExperimentRunner.java:444`), so in every scenario
exactly 80 % of tasks are small and 20 % are whales. The average per-
task work matches the previously-used uniform 1 B–15 B range (mean
~6.7 B) so total system load is held constant while the distribution
shape is heavy-tailed. This is a direct challenge to Min-Min-style
greedy heuristics that would pack whales onto the fastest VMs and
starve the small tasks that come after.

## 2.4 Three scenarios

The three scenarios test the scheduler under different CPU:GPU task
ratios while keeping the 500-task count fixed
(`ScenarioComparisonExperimentRunner.java:174-182`).

| Scenario   | CPU tasks | GPU tasks | What it tests                                                       |
|------------|-----------|-----------|---------------------------------------------------------------------|
| Balanced   | 250       | 250       | Even pressure on both sides of the cluster.                         |
| GPU-Stress | 100       | 400       | GPU VMs become the bottleneck; CPU VMs partly idle.                 |
| CPU-Stress | 400       | 100       | Opposite: CPU VMs saturate; GPU VMs partly idle.                    |

If you have not thought about heterogeneous clusters before: these
scenarios matter because an algorithm that looks brilliant under
Balanced can fail under either Stress scenario, where the mismatch
between task demand and VM supply forces trade-offs the Balanced case
hides.

Task names are stamped with the scenario number and workload type so
they are easy to trace in downstream CSVs, e.g.
`S1_CPU_SEVEN_ZIP_0`, `S2_GPU_FURMARK_42`
(`ScenarioComparisonExperimentRunner.java:445`).

## 2.5 Algorithm budget

Every metaheuristic receives exactly the same budget, so runtime
differences reflect *per-evaluation cost*, not number of chances.

- **Population size**: 200 solutions per generation
  (`ScenarioComparisonExperimentRunner.java:102`).
- **Fitness-evaluations budget**: 40 000 evaluations
  (`ScenarioComparisonExperimentRunner.java:103`). GA and AMOSA are
  terminated on total evaluations; NSGA-II, SPEA-II and the
  dominance-archive GA use the derived generation count
  `40 000 / 200 = 200 generations` (see lines 594, 667, 694, 715).
- **Crossover rate** $p_c = 0.95$; **mutation rate** $p_m = 0.05$
  (lines 104–105);
- **GA elitism** = 20 %; **GA tournament size** = 5 (lines 106–107);
- **SA** uses an auto-calibrated initial temperature (line 108–110),
  adaptive equilibrium length $[50, 400]$ (lines 117–118), geometric
  cooling, and up to three re-heats at factor 5 after 15 stagnant
  temperature steps (lines 112–115).
- **AMOSA** uses $T_0 = 15.0$, $\alpha = 0.95$, hard limit 50, soft
  limit 100, 200 iterations per temperature, 50 hill-climbing
  iterations at initialisation, $\gamma = 2.0$, mutation rate 0.05
  (lines 135–142).

## 2.6 Heuristic seeding

Before the metaheuristics start, both heuristics from
[`04b-heuristics.md`](04b-heuristics.md) are run on the initial
assignment problem to produce two greedy chromosomes
(`computeHeuristicSeed`, `ScenarioComparisonExperimentRunner.java:525-574`).
These are injected into the initial population of the GA, SA, NSGA-II,
SPEA-II and AMOSA runs via `addSeedAssignment(...)` — every
search-based algorithm starts from the same two warm-start points plus
random individuals for the remainder of the population. This is why
the heuristics appear in the code but do not appear as stand-alone
rows in the results CSV.

## 2.7 Seeds and repetitions

Every scenario is run **10 times** with deterministic seeds starting
at `BASE_SEED = 200L` and incrementing by one per run
(`ScenarioComparisonExperimentRunner.java:121-122`). Every algorithm,
in every scenario, receives the *same* ten seeds, so a seed-by-seed
paired comparison is valid.

Ten runs × three scenarios × seven algorithms = **210 full simulator
executions** recorded in
`final_experiment_results/makespan_24_04_2026/experiment_summary.csv`.
