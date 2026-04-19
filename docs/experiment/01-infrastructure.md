# 1. Infrastructure — What the Simulator Models

## 1.1 Plain-English picture

Imagine one **datacenter**: a large room full of physical servers. Each
server is called a **host**. Hosts are shared by slicing them into
**virtual machines** (VMs) — software containers that behave like their
own little computer. **Users** submit computational **tasks** (a data
job, a render, a training step). A **scheduler** decides which VM each
task runs on. Every assignment has two consequences we care about:

- Users wait longer if their task lands on an already-busy VM (queues
  form).
- The datacenter consumes more electricity if more hosts are kept busy
  or if faster hardware is used.

The whole experiment is about picking assignments that balance those
two consequences.

## 1.2 Datacenter

One logical datacenter, named `DC-Experiment`, with a 400 000 unit
bandwidth quota. Defined in
`src/main/java/com/cloudsimulator/FinalExperiment/WaitingTimeExperimentRunner.java`
(line 351).

## 1.3 Hosts — the physical machines

Forty hosts in three families. "MIPS" below stands for millions of
instructions per second — a coarse proxy for compute throughput.

| Family   | Count | CPU cores | CPU MIPS | GPU units | RAM (GB) | Storage (GB) | Network   | Power model        |
|----------|-------|-----------|----------|-----------|----------|--------------|-----------|--------------------|
| CPU-only | 16    | 16        | 2 500    | 0         | 64       | 2 048        | 20 Gbps   | `StandardPowerModel` |
| GPU-only | 12    | 8         | 2 800    | 4         | 64       | 2 048        | 20 Gbps   | `HighPerformancePowerModel` |
| Mixed    | 12    | 32        | 3 000    | 4         | 128      | 2 048        | 20 Gbps   | `HighPerformancePowerModel` |

Source: `WaitingTimeExperimentRunner.java:354-369`,
`config/HostConfig.java:14-37`.

Why three families? To make the scheduler's life interesting. Some
hosts are only useful for CPU work, some only for GPU work, and some
can do either — so a good scheduler has to think about host *shape*,
not just raw speed.

## 1.4 Virtual machines — the execution slots

Sixty VMs split across the same three families (CPU / GPU / Mixed),
each family in three **speed tiers** (fast / medium / slow). The speed
range is 10×, so the scheduler has to decide between finishing a job
quickly on an expensive fast VM or slowly on a cheap slow VM.

| Family | Fast (count, MIPS/vCPU) | Medium (count, MIPS/vCPU) | Slow (count, MIPS/vCPU) | vCPU | GPUs (fast/med/slow) | RAM (MB) |
|--------|-------------------------|---------------------------|-------------------------|------|----------------------|----------|
| CPU    | 8 × 5 000               | 8 × 2 000                 | 4 × 500                 | 4    | 0 / 0 / 0            | 4 096    |
| GPU    | 8 × 5 000               | 8 × 2 000                 | 4 × 500                 | 4    | 2 / 1 / 1            | 4 096    |
| Mixed  | 8 × 5 000               | 8 × 2 000                 | 4 × 500                 | 4    | 2 / 1 / 1            | 4 096    |

All VMs share `bandwidth = 102 400 Mbps` and `image size = 1 000 MB`.
Source: `WaitingTimeExperimentRunner.java:371-415`,
`config/VMConfig.java:14-21`.

## 1.5 Users

One user identity — `ExperimentUser` (line 429–435). The user is just a
label; the interesting variety is in what *kind* of tasks that user
submits, which is the subject of the next file.

## 1.6 How an algorithm interacts with this setup

The scheduler receives a list of 500 tasks and the list of 60 VMs. Its
job is to produce an **assignment vector** $a \in \{0, 1, \dots, 59\}^{500}$:
`a[i] = j` means "task $i$ runs on VM $j$". Every algorithm — heuristic
or metaheuristic — ultimately produces such a vector. The simulator
then plays the assignment forward in time to measure waiting time and
energy.

Two natural correctness rules are enforced by the repair operator
(`metaheuristic/operators/RepairOperator.java`):

- A task that requires a GPU cannot be placed on a CPU-only VM.
- Tasks cannot exceed VM capacity limits.

Invalid assignments produced by crossover/mutation are snapped back to
feasible before being evaluated.
