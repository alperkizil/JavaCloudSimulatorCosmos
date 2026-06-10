# 1b. MeasurementBasedPowerModel — How Energy Is Calculated

## 1b.1 The idea in one paragraph

Think of a server's power draw like an electricity bill with two parts:
a fixed **base rent** the machine pays just for being switched on
(idle power), plus a **surcharge** that depends on *what kind of work*
it is doing and *how hard* it is working. The
`MeasurementBasedPowerModel` gets both parts from real measurements: a
physical workstation was run through eleven different workloads while
a wall-plug power meter recorded the actual watts. The simulator then
replays those measured numbers instead of guessing from a formula.

Two adjustments are layered on top of the measured numbers:

1. **Utilization scaling** — a task using only half the CPU draws
   roughly half the surcharge (§ 1b.6).
2. **Speed scaling** — faster VMs pay a steep (quadratic) power
   penalty, which creates the energy-vs-makespan trade-off the whole
   experiment is about (§ 1b.7).

## 1b.2 Why a measurement-based model?

Traditional cloud simulators estimate power with a single formula:

    power = idle + (max - idle) * cpuUtilization

That treats every workload the same: a database query at 12 % CPU draws
the same watts as an encryption job at 12 % CPU. In reality they do
not — different workloads light up different parts of the chip (cores,
caches, disks, GPU) and draw very different amounts of power at the
same utilization percentage. For example, in the measurements below, an
LLM running on the GPU at just 12 % CPU / 12 % GPU draws *more* power
than a 7-Zip job saturating the CPU at 100 %.

The `MeasurementBasedPowerModel` therefore replaces the single curve
with **one measured power profile per workload type**.

## 1b.3 Where the numbers come from

| Item             | Detail                                        |
|------------------|-----------------------------------------------|
| Workstation      | Dell Precision 7920                           |
| GPU              | Nvidia GeForce RTX 5080                       |
| Power meter      | WellHise PM004 (wall-plug, whole-system draw) |
| Measurement date | October -- November 2025                      |
| Measured idle    | **75.79 W** (24-hour average)                 |

"Wall-plug" means the meter sat between the wall socket and the
machine, so every number is **total system power**, not a
per-component spec-sheet figure. That matters because it automatically
includes RAM, fans, voltage regulators, and other overhead that
component-level models miss.

Source: `model/MeasurementBasedPowerModel.java:9-24`.

## 1b.4 The full formula at a glance

For every simulation tick the model computes:

    totalPower = (baseIdlePower + incrementalPower × utilizationRatio × speedFactor) × hardwareScaleFactor

| Term                  | Meaning                                                                 | Where it comes from        |
|-----------------------|-------------------------------------------------------------------------|----------------------------|
| `baseIdlePower`       | Watts the machine draws doing nothing (75.79 W)                          | Measured (§ 1b.3)          |
| `incrementalPower`    | Extra watts *above idle* that this workload type adds                    | Profile table (§ 1b.5)     |
| `utilizationRatio`    | How hard the workload is running vs. its benchmark conditions (0–1.5)    | Computed per tick (§ 1b.6) |
| `speedFactor`         | Quadratic penalty/discount for fast/slow VMs                             | VM speed (§ 1b.7)          |
| `hardwareScaleFactor` | Adjusts everything for stronger/weaker hosts; 1.0 in this experiment     | Host configuration (§ 1b.8)|

**Worked example.** A `SEVEN_ZIP` task (incremental power 130.29 W) on
a *medium* VM (speed factor 1.0) using 50 % CPU:

    total = 75.79 + 130.29 × 0.5 × 1.0 ≈ 141 W

The same task saturating a *fast* VM (speed factor 6.25, see § 1b.7):

    total = 75.79 + 130.29 × 1.0 × 6.25 ≈ 890 W

Same work, ~6× the power — the fast VM finishes sooner, but the
scheduler pays for it in energy.

Source: `MeasurementBasedPowerModel.java:256-258` (base formula) and
`MeasurementBasedPowerModel.java:561-588` (speed-scaled variant).

## 1b.5 Workload profiles — the measured table

Each workload type has an `EmpiricalWorkloadProfile`
(`model/EmpiricalWorkloadProfile.java`) recorded during a benchmark run
on the reference machine. Reading the columns:

- **Incremental (W)** — extra watts above the 75.79 W idle while the
  benchmark ran. This is the number the formula uses.
- **Peak / Avg (W)** — highest and average *total* wall power observed.
- **CPU / GPU util** — utilization during the benchmark; the
  "typical" values that utilization scaling compares against (§ 1b.6).

| Workload          | Incremental (W) | Peak (W) | Avg (W) | CPU util | GPU util | Notes                                         |
|-------------------|-----------------|----------|---------|----------|----------|-----------------------------------------------|
| `IDLE`            | 0.00            | 126.7    | 75.79   | 0 %      | 0 %      | 24-hour idle baseline                         |
| `VERACRYPT`       | 19.25           | 138.7    | 95.04   | 3 %      | 0 %      | Disk encryption (Camellia), I/O-bound         |
| `DATABASE`        | 39.59           | 133.6    | 115.38  | 12 %     | 0 %      | HammerDB + MySQL, 100 virtual users           |
| `SEVEN_ZIP`       | 130.29          | 215.8    | 206.08  | 100 %    | 0 %      | 7-Zip compression, 40 threads                 |
| `CINEBENCH`       | 133.76          | 214.1    | 209.55  | 100 %    | 0 %      | Multi-core CPU render, 40 threads             |
| `PRIME95SmallFFT` | 124.21          | 203.8    | 200.00  | 100 %    | 0 %      | Small-FFT CPU stress test                     |
| `LLM_CPU`         | 104.21          | 214.1    | 180.00  | 55 %     | 0 %      | LLM inference, CPU-only, KV cache off         |
| `LLM_GPU`         | 185.35          | 349.5    | 261.14  | 12 %     | 12 %     | LLM inference, full GPU memory, KV cache on   |
| `IMAGE_GEN_CPU`   | 90.92           | 202.1    | 166.71  | 80 %     | 0 %      | Stable Diffusion XL, CPU-only                 |
| `IMAGE_GEN_GPU`   | 141.08          | 430.0    | 216.87  | 30 %     | 10 %     | Stable Diffusion XL, GPU-accelerated          |
| `FURMARK`         | 352.18          | 452.3    | 427.97  | 8 %      | 100 %    | GPU stress test, 8K resolution, MSAA 8x       |

Notice the pattern the simple utilization formula cannot capture:
`FURMARK` adds 352 W at only 8 % CPU (the GPU is doing the work), while
`DATABASE` adds just 40 W because it is bottlenecked on disk I/O, not
the processor.

Source: `MeasurementBasedPowerModel.java:92-246`.

## 1b.6 Utilization scaling — running below benchmark intensity

The profile numbers were measured at the benchmark's own intensity
(e.g. `SEVEN_ZIP` at 100 % CPU). When a simulated task runs at a
different intensity, the incremental power is scaled in proportion:

1. Pick the **dominant resource** for the workload: GPU if the
   benchmark's typical GPU utilization exceeds its CPU utilization,
   otherwise CPU.
2. Compute the ratio of current to typical utilization on that
   resource:

       ratio = currentUtil / typicalUtil

3. Clamp the ratio to **[0.0, 1.5]** (explained below).
4. Multiply: `incrementalPower × ratio`.

Concretely: a `SEVEN_ZIP` task at 50 % CPU draws half its benchmark
increment (≈ 65 W above idle); a `FURMARK` task at 100 % GPU draws the
full 352 W above idle, regardless of its (tiny) CPU usage.

**Why the clamp?** Step 2 is a linear extrapolation from a *single
measured point*, and extrapolations are only trustworthy near that
point. The two bounds are guard rails:

- **Lower bound 0.0** — incremental power is watts *above* idle, and a
  running workload cannot make the machine draw less than idle, so the
  increment can shrink to zero but never go negative.
- **Upper bound 1.5** — protects against runaway extrapolation, which
  matters most for workloads benchmarked at *low* utilization. Take
  `DATABASE`: it was measured at only 12 % CPU drawing +39.59 W (it is
  disk-bound; the CPU is not where its power goes). If a simulated
  database task ran at 60 % CPU, the unclamped ratio would be
  0.6 / 0.12 = 5.0, predicting +198 W — a figure never observed on the
  real machine (the benchmark's measured *peak* was 133.6 W total).
  With the cap, `DATABASE` can never draw more than
  39.59 × 1.5 ≈ 59.4 W above idle, however high the simulated CPU
  utilization goes.
- **Why allow anything above 1.0 at all?** The profile numbers are
  benchmark *averages*, and the same runs recorded peaks well above
  them — so running modestly hotter than the benchmark conditions is
  realistic. Up to +50 % is permitted as "modest over-utilization"
  before the cap kicks in.

A useful side effect: for workloads whose typical utilization is
already 100 % (`SEVEN_ZIP`, `CINEBENCH`, `PRIME95SmallFFT`, and
`FURMARK` on the GPU side), the ratio can never exceed 1.0 — current
utilization tops out at 100 % — so the upper clamp only ever activates
for the low-typical-utilization profiles.

Source: `EmpiricalWorkloadProfile.java:83-105`.

## 1b.7 Speed-based power scaling — why fast VMs are expensive

Real processors need disproportionately more power to run faster
(higher clocks need higher voltage — the same physics behind DVFS).
The model captures this with a **quadratic** factor on the VM's speed
relative to a reference:

    speedFactor = (vmIPS / referenceIPS) ^ 2.0

The reference IPS is the **median** IPS across all VMs in the
experiment, computed at setup time — so the "middle" VM tier pays
exactly 1.0. With the experiment's three tiers (500 M / 2 B / 5 B IPS,
median 2 B):

| VM tier | IPS           | Speed ratio (vs. 2 B) | Power factor (ratio²) |
|---------|---------------|-----------------------|-----------------------|
| Slow    | 500 000 000   | 0.25                  | 0.0625                |
| Medium  | 2 000 000 000 | 1.0                   | 1.0                   |
| Fast    | 5 000 000 000 | 2.5                   | 6.25                  |

The fast tier is 10× faster than the slow tier but its incremental
power factor is 6.25 / 0.0625 = **100× higher** — squaring the 10×
speed gap. This is the engine of the experiment's core trade-off: a
fast VM finishes a task quickly (good for makespan), but each second
of that work costs far more energy.

Source: `MeasurementBasedPowerModel.java:529-549`.

## 1b.8 Hardware scale factor — modelling other machines

All profiles were measured on one specific workstation. The
`hardwareScaleFactor` is a simple multiplier on the final result that
lets the same profiles stand in for hosts that are stronger or weaker
than that reference machine:

| Factor | Intended use                          |
|--------|---------------------------------------|
| 0.5    | Low-power edge device or laptop       |
| 0.75   | Entry-level server                    |
| 1.0    | Reference system (default)            |
| 1.25   | High-end workstation                  |
| 1.5    | Dual-socket server                    |
| 2.0    | High-density compute node             |

**In the current experiment every host uses 1.0**, so this term has no
effect on the results — it exists for future scenarios with
heterogeneous hardware.

Source: `PowerModelFactory.java:80-93`.

## 1b.9 Factory bridge: backward compatibility

Older parts of the simulator expect the legacy `PowerModel` interface,
which knows nothing about workload types — it takes only five fixed
parameters (max CPU power, max GPU power, idle CPU, idle GPU, other).
So that those code paths still produce measurement-grounded numbers,
the `PowerModelFactory` derives the five legacy parameters from the
measured data when a host is configured with
`"MeasurementBasedPowerModel"`:

| Parameter      | Value    | Origin                                          |
|----------------|----------|-------------------------------------------------|
| Max CPU power  | 158.76 W | 25 W idle CPU + 133.76 W (Cinebench increment)  |
| Max GPU power  | 367.18 W | 15 W idle GPU + 352.18 W (FurMark increment)    |
| Idle CPU power | 25.0 W   | ~33 % of the 75.79 W system idle                |
| Idle GPU power | 15.0 W   | ~20 % of the 75.79 W system idle                |
| Other          | 35.79 W  | ~47 % of the 75.79 W system idle                |

In short: legacy callers of `calculateTotalPower(cpuUtil, gpuUtil)` get
a measurement-based approximation, while the energy objective itself
calls the richer workload-aware methods on this model directly.

Source: `PowerModelFactory.java:119-137`.
