# 2. Workload & Scenarios

## 2.1 Tasks in plain English

Every experiment run submits **500 tasks**. Each task has a *size*
(how much compute it needs) and a *type* (CPU-only or GPU-required).
The type decides which VMs are eligible; the size decides how long it
will occupy the chosen VM.

## 2.2 The 80 / 20 bimodal size distribution

Real workloads are **heavy-tailed**: most jobs are small, a few are
huge (the classic "80 / 20 rule"). We reproduce that with ten fixed
"slots" drawn from two size groups:

- **Eight small slots** — 100 M, 200 M, 300 M, 500 M L each (shown
  twice in a weighted bucket) — the everyday jobs.
- **Two "whale" slots** — 25 B and 40 B L — the rare but disruptive
  large jobs that cause scheduling hotspots.

Source: `WaitingTimeExperimentRunner.java:165-171`. Each task is
sampled uniformly from these ten slots, so in expectation roughly 80 %
of tasks are small and 20 % are large, matching the intent of the
Pareto-style workload without hand-tuning a full distribution.

## 2.3 Three scenarios

The three scenarios test the scheduler under different CPU:GPU task
ratios while keeping the 500-task count fixed.

| Scenario    | CPU tasks | GPU tasks | What it tests |
|-------------|-----------|-----------|---------------|
| Balanced    | 250       | 250       | Even pressure on both sides of the cluster. |
| GPU-Stress  | 100       | 400       | GPU VMs become the bottleneck; CPU VMs partly idle. |
| CPU-Stress  | 400       | 100       | Opposite: CPU VMs saturate; GPU VMs partly idle. |

Source: `WaitingTimeExperimentRunner.java:175-183`.

If you've not thought about heterogeneous clusters before: these
scenarios matter because an algorithm that looks brilliant under
Balanced can fail spectacularly under Stress, where the mismatch
between task demand and VM supply forces trade-offs the Balanced case
hides.

## 2.4 Algorithm budget

Every metaheuristic gets exactly the same budget so runtime
differences reflect *per-evaluation cost*, not number of chances.

- **Population size**: 200 solutions per generation
  (`WaitingTimeExperimentRunner.java:103`, also
  `GAConfiguration.java:291`, `NSGA2Configuration.java:190`).
- **Fitness-evaluations budget**: 40 000 evaluations
  (`WaitingTimeExperimentRunner.java:104`; termination implemented in
  `metaheuristic/termination/FitnessEvaluationsTermination.java`).

## 2.5 Seeds and repetitions

Every scenario is run **10 times** with deterministic seeds starting at
`BASE_SEED = 200L` and incrementing by one per run
(`WaitingTimeExperimentRunner.java:122-123`). Every algorithm, in every
scenario, receives the *same* ten seeds, so a seed-by-seed paired
comparison is valid.

Ten runs × three scenarios × seven algorithms × three cap levels
(uncapped, 190 kW, 120 kW) = the full factorial measured in the paper.
