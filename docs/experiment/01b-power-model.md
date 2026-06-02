# 1b. MeasurementBasedPowerModel — How Energy Is Calculated

## 1b.1 Why a measurement-based model?

Traditional cloud simulators estimate power with a simple formula:

    power = idle + (max - idle) * cpuUtilization

That treats every workload the same: a database query at 12 % CPU draws
the same watts as an encryption job at 12 % CPU. In reality they do
not — different workloads light up different parts of the chip and draw
very different amounts of power at the same utilization percentage.

The `MeasurementBasedPowerModel` replaces that single curve with **per-
workload empirical profiles** measured on real hardware with a wall-plug
power meter.

## 1b.2 Reference system and measurement setup

| Item             | Detail                                        |
|------------------|-----------------------------------------------|
| Workstation      | Dell Precision 7920                           |
| GPU              | Nvidia GeForce RTX 5080                       |
| Power meter      | WellHise PM004 (wall-plug, whole-system draw) |
| Measurement date | October -- November 2025                      |
| Measured idle    | **75.79 W** (24-hour average)                 |

All numbers are **total system power at the wall**, not per-component
TDP figures. That is important because it automatically captures RAM,
fans, VRMs, and other overhead that component-level models miss.

Source: `model/MeasurementBasedPowerModel.java:9-24`.

## 1b.3 Core formula

For every simulation tick the model computes:

    totalPower = (baseIdlePower + incrementalPower) * hardwareScaleFactor

where:

- **baseIdlePower** = 75.79 W (measured system idle).
- **incrementalPower** = additional watts above idle, looked up from
  the workload profile (see table below) and scaled by the current
  utilization ratio.
- **hardwareScaleFactor** = multiplier that adjusts all figures for
  hosts that are more or less powerful than the reference system
  (1.0 = reference).

Source: `MeasurementBasedPowerModel.java:256-258`.

## 1b.4 Workload profiles

Each workload type has an `EmpiricalWorkloadProfile`
(`model/EmpiricalWorkloadProfile.java`) containing the incremental
power, peak power, typical CPU/GPU utilization, and energy-per-work-
unit measured during the benchmark run.

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

Source: `MeasurementBasedPowerModel.java:92-246`.

## 1b.5 Utilization scaling

When the simulator knows the current CPU and GPU utilization of a host,
the profile's incremental power is scaled proportionally:

1. Compute the ratio of current utilization to the **typical**
   utilization recorded during the benchmark. For a CPU-dominant
   workload (e.g. `SEVEN_ZIP`, typical CPU = 100 %):

       ratio = currentCpuUtil / typicalCpuUtil

   For a GPU-dominant workload (e.g. `FURMARK`, typical GPU = 100 %):

       ratio = currentGpuUtil / typicalGpuUtil

2. Clamp the ratio to [0.0, 1.5] (allows modest over-utilization).
3. Multiply: `incrementalPower * ratio`.

This means that a `SEVEN_ZIP` task running at 50 % CPU draws roughly
half its benchmark incremental power (65 W above idle), while a
`FURMARK` task at full GPU draws the full 352 W above idle.

Source: `EmpiricalWorkloadProfile.java:83-105`.

## 1b.6 Speed-based power scaling

Faster VMs consume more power per unit time. The model applies a
**quadratic** scaling factor based on VM speed relative to a reference:

    speedFactor = (vmIPS / referenceIPS) ^ 2.0

The reference IPS is set to the **median** IPS across all VMs in the
experiment (calculated at setup time). With the experiment's VM speeds
of 500 M, 2 B, and 5 B IPS:

| VM tier | IPS           | Speed ratio | Power factor |
|---------|---------------|-------------|--------------|
| Slow    | 500 000 000   | 0.25        | 0.0625       |
| Medium  | 2 000 000 000 | 1.0         | 1.0          |
| Fast    | 5 000 000 000 | 2.5         | 6.25         |

A fast VM draws **100x** more incremental power than a slow VM. This
creates the core energy-vs-speed trade-off that the scheduler must
navigate: placing a task on a fast VM finishes it quickly (reducing
waiting time / makespan) but costs far more energy.

Source: `MeasurementBasedPowerModel.java:529-549`.
