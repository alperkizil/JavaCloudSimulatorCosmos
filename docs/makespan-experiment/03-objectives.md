# 3. Fitness Functions

The scheduler is scored on **two objectives**, both minimised:
**makespan** (seconds) and **energy** (kilowatt-hours). This pairing
replaces the average-waiting-time / energy pairing used by the sibling
waiting-time experiment.

## 3.1 Notation

Let

$$
\begin{aligned}
T &= \{t_1, \dots, t_N\} &&\text{set of tasks, } N=500 \\
V &= \{v_1, \dots, v_M\} &&\text{set of VMs, } M=60 \\
H &= \{h_1, \dots, h_K\} &&\text{set of hosts, } K=40 \\
a &: T \to V &&\text{assignment, } a(t_i)=v_{j(i)}
\end{aligned}
$$

For a VM $v$ assigned the ordered sub-sequence
$t_{i_1}, t_{i_2}, \dots, t_{i_q}$, write $\text{len}(t)$ for the
length of task $t$ in instructions and $\text{ips}(v)$ for the VM's
aggregate throughput. The simulator executes in discrete one-second
ticks, so the runtime of task $t$ on VM $v$ uses **ceiling division**:

$$
\tau(t, v) \;=\; \left\lceil \frac{\text{len}(t)}{\text{ips}(v)} \right\rceil.
$$

Any fraction of a tick left unused when a task finishes is *wasted* —
the next task on the same VM starts at the following tick boundary.

## 3.2 Objective 1 — Makespan

The completion tick of VM $v$ is the sum of its tasks' runtimes:

$$
C(v) \;=\; \sum_{t \,:\, a(t)=v} \tau(t,\,v).
$$

The **makespan** of an assignment is the last VM to finish:

$$
\boxed{\;\mathrm{MS}(a) \;=\; \max_{v \in V} C(v)\;}
$$

Source:
`src/main/java/com/cloudsimulator/PlacementStrategy/task/metaheuristic/objectives/MakespanObjective.java`
lines 40–81. The minimisation flag is declared on line 84; the unit is
seconds (line 94).

Intuition: minimising makespan rewards **spreading** work evenly
across fast VMs so that the slowest-finishing machine finishes as
early as possible. Unlike average waiting time, a single heavily-loaded
VM dominates the metric, so the optimiser must avoid pile-ups on any
one VM.

## 3.3 Objective 2 — Energy

Total IT energy in kilowatt-hours:

$$
\boxed{\;\mathrm{E}(a) \;=\;
\frac{1}{3.6\times10^{6}}\,\Bigl[\;
  \underbrace{P_{\text{idle}} \cdot \mathrm{MS}(a) \cdot K_{\text{on}}(a)}_{\text{idle cost of every active host}}
  \;+\;
  \underbrace{\sum_{t \in T} P^{\text{incr}}_{a(t),\,\text{wl}(t)} \cdot \tau(t,\,a(t))}_{\text{incremental load per task}}
\;\Bigr]\;}
$$

where:

- $\mathrm{MS}(a)$ — makespan in seconds (same quantity as
  objective 1);
- $K_{\text{on}}(a)$ — number of hosts that must be powered on: every
  host that carries at least one VM with a task, **plus** any hosts
  placed in the datacenter without VMs (an accounting correction made
  at `EnergyObjective.java:130-139`);
- $P_{\text{idle}}$ — baseline idle draw per host in watts, obtained
  from `MeasurementBasedPowerModel.getScaledIdlePower()` (line 186);
- $P^{\text{incr}}_{v,\,w}$ — incremental watts (above idle) drawn by
  VM $v$ while it is executing a task of workload type $w$; derived
  from the empirical workload profile for $w$ (CPU and GPU
  utilisation) and, with speed scaling on, from $v$'s IPS relative
  to the fleet's median (see §3.4);
- $\text{wl}(t)$ — the workload type of task $t$ (e.g. `SEVEN_ZIP`,
  `FURMARK`, `LLM_GPU`);
- $3.6\times10^{6}$ — joules per kilowatt-hour (`JOULES_TO_KWH`,
  `EnergyObjective.java:107`).

Source: `EnergyObjective.java:110-199`. The minimisation flag is on
line 335; the unit is `kWh` (line 346).

## 3.4 Speed-based power scaling

When `useSpeedBasedScaling` is true (default, line 49), faster VMs
draw proportionally **more** watts per second. Concretely the
incremental power is scaled by

$$
P^{\text{incr}}_v \;\propto\; \left(\frac{\text{ips}(v)}{\text{ips}_{\text{ref}}}\right)^{\alpha}
$$

where $\text{ips}_{\text{ref}}$ is the median VM IPS in the fleet and
the exponent $\alpha$ is fixed to 2.0 in the power model (see
`MeasurementBasedPowerModel.calculateIncrementalPowerWithSpeedScaling`
invoked at `EnergyObjective.java:236-241`).

This is what creates the real makespan-versus-energy trade-off —
otherwise an optimiser could just shove every task onto the fastest VM
for free. With $\alpha = 2$ a fast VM running at 5 B IPS draws
$\approx (5 / 1.5)^2 \approx 11\times$ the incremental power of the
median-speed VM, so finishing quickly costs energy.

If speed scaling is disabled, makespan and energy become
near-degenerate (minimising one also minimises the other) and the
Pareto front collapses to roughly one point. That is documented in the
source as an explanatory note (`EnergyObjective.java:481-496`) and is
*not* the configuration used by this experiment.

## 3.5 Why the two objectives conflict

- **Packing onto fast VMs to finish quickly** → low makespan but high
  incremental power per second *and* short idle-time offset, so the
  integrated energy bill explodes.
- **Spreading onto slow, power-efficient VMs** → low incremental
  power per second, but the last VM to finish stretches the makespan,
  which in turn stretches $K_{\text{on}}(a) \cdot \mathrm{MS}(a)$ —
  the idle-cost term — across every active host.
- **Consolidating onto a few VMs** can win energy (fewer hosts
  powered on, so $K_{\text{on}}$ drops) but blows up makespan if the
  queues grow.

There is no single best assignment; there is a **Pareto front** of
trade-offs. The job of the MO algorithms (NSGA-II, SPEA-II, AMOSA) is
to approximate that front. The job of the dominance-archive GA/SA
variants is to walk a scalar surface but collect every non-dominated
$(\mathrm{MS}, \mathrm{E})$ pair seen along the way, which
[§05](05-results-analysis.md) shows is often competitive with the
native MO algorithms.
