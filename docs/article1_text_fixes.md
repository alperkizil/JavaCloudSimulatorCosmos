# Article 1: required text fixes

These fixes make the article agree with the code that produced its results and with
the 17 Sep 2026 result folders (`docs/ExperimentResults/*_17_09_2026_*`). The code is
unchanged since those runs (commit `322a0ff`). Page and section numbers refer to the
reviewed PDF.

Escape the percent signs (`\%`) when pasting into LaTeX.

---

## A. Red-highlighted text

### A1. Cinebench workload type (p.2, §III-A)

**Reason:** the code treats Cinebench as CPU-only (`ExperimentConfig.java:101`, CPU/GPU
split `{1.0, 0.0}`), and Table II measured the CPU multi-core test.

**Current:**
> Veracrypt, HammerDB and 7-Zip workloads are CPU-only. Cinebench requires both the CPU
> and the GPU, while FurMark2 is GPU-only. LM Studio and Comfy UI ImageGen can be
> executed on the CPU or the GPU.

**Replace with:**
> Veracrypt, HammerDB, 7-Zip and Cinebench 2024 are CPU-only workloads; Cinebench 2024
> was run in its CPU multi-core mode. FurMark 2 is GPU-only. LM Studio and ComfyUI
> ImageGen can run on either the CPU or the GPU, so we measured both versions and treat
> each as a separate task type (e.g., LM Studio (CPU) and LM Studio (GPU)).

### A2. Table I, row 4 (Cinebench 2024)

| Column | Current | Replace with |
|---|---|---|
| Description | Benchmark for CPU and GPU using the Cinema 4D rendering engine | Benchmark for CPU and GPU using the Cinema 4D rendering engine (CPU multi-core test used) |
| Task Types | Extensive CPU and GPU hybrid usage | Extensive CPU usage (multi-core rendering) |

### A3. Power cap levels (p.8, §IV-E)

**Reason:** the caps are not CDF percentiles. They are 90/70/50% of a reference peak
`P_ref`, set per scenario (`PowerCapCalibrator.java`, scheme `anchored-pref-v1` in
`power_cap_calibration.csv`). 17.7/13.8/9.8 kW are the Balanced caps only.

**Current:**
> To determine the power cap levels, we first ran uncapped experiments and examined the
> instantaneous peak power of all solutions. We built an empirical Cumulative
> Distribution Function (CDF) of these peak values. We considered the cap levels based
> on the calibrated CDF percentiles at 90, 70, and 30 percent, which roughly correspond
> to 17.7, 13.8, and 9.8 kW.

**Replace with:**
> To determine the power cap levels, we first ran all seven algorithms without a cap.
> For each scenario and each of the ten seeds, we took the solution with the lowest
> average waiting time among the solutions returned by all algorithms, and recorded its
> peak power. The median of these ten peak values is the reference peak power
> $P_{\mathrm{ref}}$, the power drawn by the fastest schedule. The cap levels are set to
> 90%, 70% and 50% of $P_{\mathrm{ref}}$, separately for each scenario, as given in
> Table X.

**Add as a new table** (the text above refers to it; renumber as needed):

| Scenario | $P_{\mathrm{ref}}$ (kW) | 90% cap (kW) | 70% cap (kW) | 50% cap (kW) |
|---|---|---|---|---|
| Balanced | 19.65 | 17.7 | 13.8 | 9.8 |
| GPU_Stress | 16.79 | 15.1 | 11.8 | 8.4 |
| CPU_Stress | 17.58 | 15.8 | 12.3 | 8.8 |

### A4. Edits elsewhere that follow from A1–A3

- **Introduction.** *"We consider typical computing tasks that require or use CPU-only,
  GPU-only, and hybrid computing resources."* → *"We consider typical CPU-only and
  GPU-only computing tasks running on CPU-only, GPU-only and hybrid (CPU+GPU) hosts."*
- **p.2, §III.** *"…that determines its computational requirement, which can be
  CPU-only, GPU-only, or run on either a CPU or a GPU."* → *"…that determines its
  computational requirement. A task is either CPU-only or GPU-only."*
- **p.7, §IV-E.** *"We consider four different power-usage scenarios: uncapped, 90%,
  70%, and 50% capped."* → *"We consider four power-usage scenarios: uncapped, and
  capped at 90%, 70% and 50% of the reference peak power $P_{\mathrm{ref}}$ defined
  below."*
- **p.8, §IV-E.** *"HV is almost the same at 17.7 kW, which is the 90 percentile cap,"*
  → *"HV is almost the same at the 90% cap (17.7 kW in the Balanced scenario),"*.
  Note: the rest of this sentence ("keeps 85% of its uncapped HV") compares HV across
  cap levels, which B9 says is not valid. It still needs rewriting.

---

## B. Method text that differs from the code

### B1. Operators and mutation rate (p.6, §IV-C)

**Reason:** the population-based algorithms use a mutation rate of 0.004, not 0.05
(`AlgorithmParameters.java:52`). SA and AMOSA scale their moves with temperature.
Elitism and tournament size apply to GA only.

**Current:**
> All metaheuristics use the same operators: uniform crossover, combined mutation that
> is a mix of single-index swap and multi-index random reset, and the above-mentioned
> repair operator. Cross-over rate is 0.95, mutation rate is 0.05, elitism is 20% and
> tournament size is 5.

**Replace with:**
> All metaheuristics use the same two mutation moves, chosen with equal probability:
> moving a task to another randomly chosen compatible VM, or swapping the execution
> order of two tasks on the same VM. After every crossover or mutation, the repair
> operator described above fixes any invalid assignment. The population-based
> algorithms (GA, NSGA-II and SPEA-2) use uniform crossover with a rate of 0.95: each
> task's VM comes from a randomly chosen parent, and each child keeps the execution
> order of one parent. Each task is mutated with probability 0.004, i.e., about two
> tasks per child for 500 tasks. GA uses tournament selection with a tournament size of
> 5 and keeps the best 20% of its population (elitism). NSGA-II and SPEA-2 use the
> binary tournament selection of their MOEA Framework implementations. The
> single-solution algorithms make fewer moves as the temperature $T$ falls. SA applies
> $1 + \lfloor 3T/T_0 \rfloor$ moves per neighbour, where $T_0$ is the initial
> temperature: four moves at $T_0$, and one move once $T < T_0/3$. AMOSA mutates each
> task with probability $0.05 \cdot T/T_0$ (25 tasks at $T_0$) and makes a single move
> once $T \le T_0/25$.

### B2. Heuristic starting solutions (p.3 and p.6)

**Reason:** only NSGA-II, SPEA-2 and AMOSA receive both heuristic solutions. GA and SA
receive only the one that matches their objective (`AlgorithmRegistry.java:152-181`).

**p.3, §III-B-1. Current:**
> All metaheuristics start with these two solutions, providing them with good starting
> points for further optimization.

**Replace with:**
> These solutions are used as starting points for the metaheuristics. Each
> single-objective algorithm starts from the heuristic for the objective it optimizes:
> GA_Makespan and SA_Makespan from the LPT solution, GA_WaitingTime and SA_WaitingTime
> from the WorkloadAware solution, and the energy variants of GA and SA from the
> EnergyAware solution. The multi-objective algorithms (NSGA-II, SPEA-2 and AMOSA)
> start from both heuristic solutions of the problem they solve.

**p.6, §IV-C. Current:**
> Two solutions are generated using the LPT and EnergyAware heuristics and seeded into
> the initial populations for the makespan-versus-energy-consumption problem.
> Similarly, two solutions are generated using the WorkloadAware and EnergyAware
> heuristics and seeded into the initial populations for the average waiting time vs.
> energy consumption problem. The other individuals in the initial populations are
> generated randomly.

**Replace with:**
> For the makespan vs. energy consumption problem, the LPT and EnergyAware solutions
> are placed in the initial populations of NSGA-II and SPEA-2 and in the initial
> archive of AMOSA. For the average waiting time vs. energy consumption problem, the
> WorkloadAware and EnergyAware solutions are used in the same way. GA's initial
> population contains the one heuristic solution that matches its objective, and SA
> starts its search from that solution. All other members of the initial populations
> are generated randomly.

### B3. Run time under a cap (p.8, §IV-E)

**Reason:** there is no repair for cap violations. The extra time is the peak-power
calculation for every evaluated solution
(`GenerationalGAPowerCeilingAlgorithm.java:307`). The ratios below come from
Tables XII–XXIII. AMOSA is never the slowest.

**Current:**
> Time is doubled in the capped scenarios, because it is required to repair the
> infeasible solutions. SA is the fastest algorithm, and AMOSA is the slowest algorithm.

**Replace with:**
> Run times are 1.7 to 3.9 times longer in the capped experiments. The extra time comes
> from computing the peak power of every evaluated solution, which is needed to check it
> against the cap; solutions that exceed the cap are not repaired. This fixed cost per
> evaluation weighs more on the fast GA and SA runs (1.8–3.9 times longer) than on
> NSGA-II and SPEA-2 (1.7–2.2 times). SA remains the fastest algorithm, while NSGA-II
> and SPEA-2 are the slowest.

### B4. How workload power is computed (p.5, §IV-B)

**Reason:** utilization does not change task power. Task power is the measured average
minus idle; utilization is only used to split it between CPU and GPU
(`EmpiricalWorkloadProfile.java:77`, `MeasurementBasedPowerModel.java:321`).

**Current:**
> We use the average CPU utilization values in the table to calculate the power
> consumption of these tasks. For example, 7-Zip uses 100% CPU, whereas VeraCrypt uses
> 3% CPU.

**Replace with:**
> The power a task adds to its host is its measured average power minus the idle power
> (Table II). The measurement therefore already reflects how heavily the workload uses
> the machine: 7-Zip runs at 100% CPU and adds 130.29 W, whereas VeraCrypt runs at 3%
> CPU and adds 19.25 W. The utilization values are used only to report how this power
> is split between the CPU and the GPU; they do not change the total.

### B5. SA moves per temperature step (p.3, §III-B-3, third bullet)

**Reason:** Table III is the cooling rule and matches the code. The move count follows
a separate rule (`SimulatedAnnealingAlgorithm.java:458`).

**Current:**
> The number of moves created per temperature grows or shrinks in the range of
> [50, 400], depending on how often moves are being accepted, as shown in Table III.

**Replace with these two bullets:**
> • The number of moves tried at each temperature depends on the acceptance rate $a$ at
> the previous temperature. If $a < 0.10$ or $a > 0.70$, 50 moves are tried. Between
> these limits the number rises linearly to a maximum of 400 moves at $a = 0.40$, i.e.,
> $50 + \lfloor 350\,(1 - |a - 0.40|/0.30) \rfloor$ moves.
>
> • The cooling multiplier also depends on the acceptance rate at the previous
> temperature, as shown in Table III.

### B6. SA reheating (p.3, §III-B-3, second bullet)

**Reason:** the stagnation counter counts temperature steps, not iterations. It is not
reset by a reheat, so when the best solution does not improve, the three reheats fire
on consecutive steps. The temperature is capped at its initial value. In the 17 Sep
power-cap campaign, 27 of the 360 SA runs reheated.

**Current:**
> If there is no improvement for 15 iterations, the temperature is multiplied by 5, in
> order to escape local optima, a maximum 3 times

**Replace with:**
> To escape local optima, SA reheats when the best solution has not improved for 15
> consecutive temperature steps. The search returns to the best solution found so far,
> and the temperature is multiplied by 5, but not above the initial temperature. If the
> best solution still does not improve, the next temperature step reheats again, up to a
> maximum of three reheats per run.

### B7. Deb's rules (p.8, §IV-E)

**Reason:** the violation is compared by amount (Watts above the cap), not by count.
Among feasible solutions GA and SA compare their scalar objective, not dominance. SA
accepts worse violations probabilistically
(`SimulatedAnnealingPowerCeilingAlgorithm.java`, `acceptNeighbor`).

**Current:**
> 1) If one solution is feasible and the other is infeasible, choose the feasible
> solution
> 2) If both solutions are infeasible, choose the solution with fewer violations
> 3) If both solutions are feasible, use domination rules

**Replace with:**
> 1) If one solution is feasible and the other is infeasible, the feasible solution is
> preferred.
> 2) If both solutions are infeasible, the solution with the smaller violation, i.e.,
> the lower peak power above the cap, is preferred.
> 3) If both solutions are feasible, they are compared as in the unconstrained
> algorithm: by Pareto dominance in NSGA-II, SPEA-2, AMOSA and the archives, and by the
> optimized objective in GA and SA.
>
> In SA, a move that increases the violation is not rejected outright. It is accepted
> with the Metropolis probability $\exp(-\Delta v / T)$, where $\Delta v$ is the
> increase in violation divided by the cap.

### B8. VM speed classes and GPUs (p.5, §IV-B)

**Reason:** VM classes are set by the requested MIPS per vCPU, not by the host. GPUs
have no speed in the model. The GPU count limits how many GPU tasks run at once
(`LaneSchedule.java`).

**Current:**
> 20 VMs are further divided into fast, medium, and slow groups based on the hosts they
> are assigned to. All VMs are assigned 4 cores. The GPUs are also divided into three
> classes (fast, medium, and slow), and each class has a different required MIPS.

**Replace with:**
> The 20 VMs of each family are divided into 8 fast, 8 medium and 4 slow VMs, which
> request 5000, 2000 and 500 MIPS per vCPU, respectively. A VM cannot run faster than
> the cores of its host, so a fast VM runs at 2500, 2800 or 3000 MIPS, depending on its
> host. All VMs have 4 vCPUs, and each vCPU runs one task at a time. GPU-only and mixed
> VMs also have GPUs: 2 for fast VMs and 1 for medium and slow VMs. GPUs do not have
> their own speed in the model. A GPU task runs at the speed of the vCPU it uses and
> also occupies one GPU, so the number of GPUs limits how many GPU tasks a VM can run at
> the same time.

### B9. How the metrics are computed (p.6, §IV-D, and §IV-E)

**Reason:** the tables report metrics of each algorithm's front merged over ten runs,
not per-run means. Normalization is per scenario, and per cap level in the power-cap
study, so there is no single fixed frame across scenarios
(`scripts/results_explorer.py`, `collective_metrics`).

**p.6, §IV-D. Current:**
> We combined the results of all algorithms to obtain the universal Pareto set, the set
> of best solutions across all algorithms over 10 runs. […] We use a fixed reference
> point to calculate HV and to normalize the axes for comparison across scenarios.

**Replace with:**
> In each run, every algorithm reports the non-dominated solutions among all the
> solutions it evaluated during that run; solutions within 1% of the objective range of
> an already stored solution are not added. For each algorithm, we merge the solutions
> reported in its ten runs and keep the non-dominated ones. All metrics in Tables IX–XXIII
> are computed on this combined front, and Front size is its number of solutions.
> Merging the combined fronts of all algorithms gives the universal Pareto front. For
> HV, both objectives are normalized to [0, 1] using the best and worst values among
> all solutions reported in the scenario, and HV is measured against the reference
> point (1.1, 1.1). GD is the mean distance from the algorithm's points to the nearest
> point of the universal Pareto front, and IGD is the mean distance from the universal
> front's points to the nearest point of the algorithm. Both are measured after
> normalizing the two fronts to the range they span together. Contribution is the
> number of points on the universal Pareto front that the algorithm found, and Avg time
> is the mean running time of one run.

**§IV-E (power-cap experiments). Add:**
> In the power-cap experiments, only solutions that respect the cap are used, and the
> normalization is done separately for each cap level. A run that found no feasible
> solution contributes no points. HV values should therefore be compared between
> algorithms within the same table, not across cap levels.
