# Makespan Experiment Configuration — Index

This folder documents the **makespan-versus-energy** cloud-scheduling
experiment driven by
`src/main/java/com/cloudsimulator/FinalExperiment/ScenarioComparisonExperimentRunner.java`,
whose results live under
`final_experiment_results/makespan_24_04_2026/`. The layout mirrors the
sibling [`docs/experiment/`](../experiment/README.md) folder that
documents the waiting-time experiment.

The files are split so each can be opened on its own.

1. [`01-infrastructure.md`](01-infrastructure.md) — what the simulator
   models: the datacenter, the physical hosts, the virtual machines,
   the users, and how to read those numbers if you've never looked at a
   cloud simulator before.
2. [`02-scenarios.md`](02-scenarios.md) — the three workload scenarios
   (Balanced, GPU-Stress, CPU-Stress), the bimodal 80/20 task-size
   distribution, the algorithm budget, and the seeding / repetition
   policy.
3. [`03-objectives.md`](03-objectives.md) — the two fitness functions
   (**makespan in seconds** and **energy in kWh**) with their exact
   formulas, the speed-based power-scaling toggle that creates the
   trade-off, and why the two objectives conflict.
4. [`04-algorithms.md`](04-algorithms.md) — pseudocode and a
   first-principles explanation of every algorithm actually run in this
   experiment: the four **dominance-archive** variants of GA and SA,
   plus **NSGA-II**, **SPEA-II**, and **AMOSA**.
5. [`04b-heuristics.md`](04b-heuristics.md) — pseudocode and a detailed
   plain-English walkthrough of the two greedy heuristics
   (`WorkloadAwareTaskAssignmentStrategy` and
   `EnergyAwareTaskAssignmentStrategy`) that are **not** scored as
   standalone algorithms in this experiment but are used to **seed**
   the initial populations of the metaheuristics.
6. [`05-results-analysis.md`](05-results-analysis.md) — interpretation
   of the ten-seed, three-scenario, seven-algorithm run: HV / GD / IGD
   / Pareto-contribution winners, runtime trade-offs, statistical
   significance, a per-scenario recommendation, and a final
   cross-experiment comparison with the sibling waiting-time run in
   `reports/new/` (§5.7).

## Which algorithms are scored

Only the **search-based** algorithms appear in
`experiment_summary.csv`. The five pure heuristics
(`FirstAvailable`, `ShortestQueue`, `WorkloadAware`, `EnergyAware`,
`RoundRobin`) are declared in `ALGORITHM_LABELS` at
`ScenarioComparisonExperimentRunner.java:89-100` but were excluded from
the recorded run, and the four pure single-objective metaheuristics
(`GA_Makespan`, `GA_Energy`, `SA_Makespan`, `SA_Energy`) were excluded
as well. The seven algorithms that survived and appear in
`makespan_24_04_2026/experiment_summary.csv` are:

- **Dominance-archive variants** — `GA_Makespan_Dominance`,
  `GA_Energy_Dominance`, `SA_Makespan_Dominance`,
  `SA_Energy_Dominance`;
- **Multi-objective metaheuristics** — `NSGA-II`, `SPEA-II`, `AMOSA`.

All paths referenced inside these files point at real sources under
`src/main/java/com/cloudsimulator/...` so every claim can be audited.
