# 3. Fitness Functions

The scheduler is scored on **two objectives**, both minimised.

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

For a VM $v$ holding the ordered sub-sequence of tasks
$t_{i_1}, t_{i_2}, \dots, t_{i_q}$, let $\text{len}(t)$ be the length
of task $t$ in instructions, and $\text{mips}(v)$ the VM's throughput.
Runtime of task $t_i$ on VM $v$ is

$$
\tau(t_i, v) \;=\; \frac{\text{len}(t_i)}{\text{mips}(v)}.
$$

## 3.2 Objective 1 — Average Waiting Time

The waiting time of task $t_i$ is the time it spends queued behind
earlier tasks on the same VM, *before* it starts executing:

$$
w(t_i) \;=\; \sum_{k < i \,:\, a(t_k)=a(t_i)} \tau(t_k,\,a(t_k)).
$$

The objective is the mean over all tasks:

$$
\boxed{\;\mathrm{WT}(a) \;=\; \frac{1}{N}\sum_{i=1}^{N} w(t_i)\;}
$$

Source: `PlacementStrategy/task/metaheuristic/objectives/WaitingTimeObjective.java`
lines 52–89. Minimisation is declared on line 105.

Intuition: waiting time punishes *consolidation*. If you cram every
task onto the two fastest VMs, queues blow up. If you spread them
thinly across many VMs, queues shrink — but now you pay on the other
objective.

## 3.3 Objective 2 — Energy

Total electrical energy in kilowatt-hours, summed across the
datacenter:

$$
\boxed{\;\mathrm{E}(a) \;=\;
\frac{1}{3.6\times10^{6}}
\Bigl[
  \underbrace{P_{\text{idle}} \cdot \text{makespan}(a) \cdot K_{\text{on}}(a)}_{\text{idle cost of every active host}}
  \;+\;
  \underbrace{\sum_{v \in V}\Delta P_v \cdot \text{exec}_v(a)}_{\text{incremental load of busy VMs}}
\Bigr]\;}
$$

where:

- $\text{makespan}(a)$ — total wall-clock time until the last task
  finishes (seconds);
- $K_{\text{on}}(a)$ — count of hosts that must be powered on to serve
  the assignment;
- $P_{\text{idle}}$ — baseline idle draw per host (watts, taken from
  the `PowerModel` on each host);
- $\Delta P_v$ — extra watts drawn while VM $v$ is executing (above
  idle), measured empirically from workload profiles;
- $\text{exec}_v(a)$ — total seconds VM $v$ spends executing tasks
  (seconds);
- $3.6\times10^{6}$ — joules per kilowatt-hour.

Source: `objectives/EnergyObjective.java` lines 186–250 and 196–199.
The `PowerModel` interface supplies $P_{\text{idle}}$ and
$\Delta P_v$; `StandardPowerModel` is used for CPU-only hosts and
`HighPerformancePowerModel` for GPU / Mixed hosts
(`WaitingTimeExperimentRunner.java:354-369`).

A *speed-based scaling* toggle (lines 232–241, 477–498) lets the
faster VM tiers draw proportionally more watts per second, which is
what creates the real makespan-versus-energy trade-off — otherwise a
scheduler could just shove everything onto the fastest VMs for free.

## 3.4 Why the two objectives conflict

- Packing more tasks onto fewer VMs ⇒ fewer hosts need to be on ⇒
  **lower energy**, but queues grow ⇒ **higher waiting time**.
- Spreading tasks across many VMs ⇒ short queues ⇒ **lower waiting
  time**, but more hosts stay idle-but-on ⇒ **higher energy**.

There is no single best assignment; there is a **Pareto front** of
trade-offs. The job of the MO algorithms is to approximate that front;
the job of the SO algorithms is to find one good point on it.

## 3.5 Power-ceiling meter (used only under a cap)

When a power cap is active we also measure the **instantaneous peak**
aggregate datacenter power:

$$
P_{\text{peak}}(a) \;=\; \max_{\,t\,\in\,[0,\,\text{makespan}]} \;\; \sum_{v \in V_{\text{active}}(t)} P_v.
$$

This is computed with an event-driven **sweep-line** over task start
and end times
(`objectives/PowerCeilingEnergyObjective.java` lines 22–39, 82),
cheaper than sampling. The sweep output is *not* an optimisation
objective; it feeds the power-cap constraint explained in
[`05-power-cap.md`](05-power-cap.md).
