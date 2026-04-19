# Experiment Configuration — Index

This folder documents the cloud-scheduling experiment in plain language,
with the underlying math in LaTeX and the algorithms as pseudocode. The
files are split so each can be opened on its own.

1. [`01-infrastructure.md`](01-infrastructure.md) — what the simulator
   models: the datacenter, the physical hosts, the virtual machines, the
   users, and how to read those numbers if you've never looked at a
   cloud simulator before.
2. [`02-scenarios.md`](02-scenarios.md) — the three workload scenarios
   (Balanced, GPU-Stress, CPU-Stress), the bimodal 80/20 task-size
   distribution, and the seeding / repetition policy.
3. [`03-objectives.md`](03-objectives.md) — the two fitness functions
   (average waiting time, energy) with their exact formulas and why
   they pull in opposite directions.
4. [`04-algorithms.md`](04-algorithms.md) — pseudocode and a
   first-principles explanation of every algorithm: GA, SA, NSGA-II,
   SPEA-II, AMOSA, plus the dominance-archive variants of GA/SA.
5. [`05-power-cap.md`](05-power-cap.md) — the datacenter-wide power
   ceiling: what it is, how the three cap levels (220 / 190 / 120 kW)
   were calibrated, how the constrained MOEAs enforce it via Deb's
   constrained-domination (not a penalty, not a third objective), and
   the "admission-control disaster" that the runtime-gating baseline
   exhibits.
6. [`06-results-analysis.md`](06-results-analysis.md) — interpretation
   of the full factorial run: HV / GD / IGD winners, runtime trade-offs,
   statistical significance, cap-induced degradation, the
   energy-dominance surprise, and a quantified account of the
   admission-control failure mode.

All file paths referenced inside point at real sources under
`src/main/java/com/cloudsimulator/...` so you can audit any claim.
