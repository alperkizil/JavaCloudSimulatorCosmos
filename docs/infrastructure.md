# Infrastructure: Entity Creation, Placement, and Power Calculation

This document describes how the simulated infrastructure comes to life in
JavaCloudSimulatorCosmos: how datacenters, hosts, VMs, users, and tasks are
**created**, how users are **matched to datacenters**, how hosts are
**assigned to datacenters**, how VMs are **placed on hosts**, and how
**power and energy are calculated** on hosts and datacenters.

Everything below is derived directly from the source code (class and method
names are cited throughout). Task-to-VM scheduling (the heuristics and
metaheuristics that decide which VM runs which task) is deliberately out of
scope here — it deserves its own document.

---

## 1. The Pipeline at a Glance

A simulation is a sequence of `SimulationStep` implementations executed
against a shared `SimulationContext` (the central registry of all entities,
metrics, and the simulation clock). The steps relevant to infrastructure run
in this fixed order:

```
1. InitializationStep          creates ALL entities from the configuration
2. HostPlacementStep           assigns hosts  -> datacenters
3. UserDatacenterMappingStep   validates/repairs users -> datacenters, starts sessions
4. VMPlacementStep             assigns VMs    -> hosts
5. TaskAssignmentStep          assigns tasks  -> VMs   (separate document)
6. VMExecutionStep             tick loop: tasks execute, hosts compute power/energy
7. TaskExecutionStep           post-run task timing analysis
8. EnergyCalculationStep       aggregates energy, PUE, carbon, cost
9. MetricsCollectionStep       builds the SimulationSummary
10. ReportingStep              CSV export
```

The order matters: entities must exist before hosts can be placed, hosts must
be in datacenters before user preferences can be validated (a preference for
an empty datacenter is useless), and users must have valid datacenters before
VM placement (VMs may only land in their owner's datacenters).

The pipeline can be driven two ways:

- **`SimulationEngine`** (`engine/SimulationEngine.java`): steps are added via
  `addStep(...)` and executed by `run()`. `configure(path)` or
  `configure(ExperimentConfiguration)` loads the configuration and seeds the
  global `RandomGenerator`.
- **Directly**, as the research campaigns do
  (`newExperiments/CampaignRunner.doRunOne`): each step is constructed and
  `execute(context)`-ed explicitly. The campaign uses
  `PowerAwareLoadBalancingHostPlacementStrategy` for step 2 and
  `BestFitVMPlacementStrategy` for step 4.

---

## 2. Where Entity Definitions Come From

`InitializationStep` consumes an **`ExperimentConfiguration`**
(`config/ExperimentConfiguration.java`) — a container of per-entity config
objects plus the random seed:

| Config class       | Describes      | Key fields |
|--------------------|----------------|------------|
| `DatacenterConfig` | one datacenter | `name`, `maxHostCapacity`, `totalMaxPowerDraw` (W) |
| `HostConfig`       | one host       | `instructionsPerSecond` (per core), `numberOfCpuCores`, `computeType`, `numberOfGpus`, `ramCapacityMB`, `networkCapacityMbps`, `hardDriveCapacityMB`, `powerModelName` |
| `UserConfig`       | one user       | `name`, `selectedDatacenterNames`, VM-count fields, `taskCounts` per `WorkloadType` |
| `VMConfig`         | one VM         | `userName` (owner), `requestedIpsPerVcpu`, `requestedVcpuCount`, `requestedGpuCount`, `requestedRamMB`, `requestedStorageMB`, `requestedBandwidthMbps`, `computeType` |
| `TaskConfig`       | one task       | `name`, `userName` (owner), `instructionLength`, `workloadType` |

> **Important:** every VM and every task that gets created corresponds to one
> `VMConfig` / `TaskConfig` entry. The VM-count and task-count fields on
> `UserConfig` do **not** drive creation — `InitializationStep` iterates
> `getVmConfigs()` and `getTaskConfigs()`, never the `UserConfig` counts.

An `ExperimentConfiguration` is built in one of two ways:

### 2.1 Parsed from a `.cosc` file

`FileConfigParser` (`config/FileConfigParser.java`) reads the sections
`[SEED]`, `[DATACENTERS]`, `[HOSTS]`, `[USERS]`, `[VMS]`, `[TASKS]`:

```
[SEED]
42

[DATACENTERS]
3                              <- count, then one line per datacenter:
DC-East,50,100000.0               name,maxHostCapacity,totalMaxPowerDraw(W)

[HOSTS]
2                              <- count, then one line per host:
2500000000,16,CPU_ONLY,0,2097152,2000000,20971520,StandardPowerModel
   ips,cores,computeType,gpus,ramMB,networkMbps,storageMB,powerModelName

[USERS]                        <- count, then one line per user:
Alice,DC-East|DC-West,2,3,1,5,3,0,2,1,4,2,1,3,2,1
   name,datacenters(|-separated),gpuVMs,cpuVMs,mixedVMs,
   then task counts in fixed order: SEVEN_ZIP,DATABASE,FURMARK,IMAGE_GEN_CPU,
   IMAGE_GEN_GPU,LLM_CPU,LLM_GPU,CINEBENCH,PRIME95SmallFFT[,VERACRYPT]

[VMS]                          <- subsections per compute type:
GPU:2                             GPU:count | CPU:count | MIXED:count
Alice,2000000000,4,2,8192,102400,1000
   userName,ipsPerVcpu,vcpus,gpus,ramMB,storageMB,bandwidthMbps

[TASKS]                        <- subsections per workload type:
SEVEN_ZIP:3                       WORKLOAD_TYPE:count
CompressData1,Alice,5000000000
   name,userName,instructionLength
```

See `configs/sample-experiment.cosc` for a complete example.

### 2.2 Built programmatically

Experiments construct the configuration in code. The current research
campaigns use `newExperiments/ExperimentConfig.java`, whose `defaults()`
define the infrastructure below (identically for all three studies).

**Datacenter** (one):

| Name | Max host capacity | Power budget (`totalMaxPowerDraw`) |
|---|---|---|
| `DC-Experiment` | 50 hosts | 400,000 W (400 kW) |

**Hosts** — 40, in three groups. Every host group must use
`MeasurementBasedPowerModel`: `toExperimentConfiguration` *throws* for any
other power-model name unless `allowNonMeasurementPowerModel` is explicitly
set.

| Count | Compute type | IPS per core | CPU cores | GPUs | RAM | Network | Storage |
|---:|---|---:|---:|---:|---|---|---|
| 16 | `CPU_ONLY` | 2.5 G | 16 | 0 | 65,536 MB (64 GB) | 20,000 Mbps | 2,097,152 MB (2 TB) |
| 12 | `GPU_ONLY` | 2.8 G | 8 | 4 | 65,536 MB (64 GB) | 20,000 Mbps | 2,097,152 MB (2 TB) |
| 12 | `CPU_GPU_MIXED` | 3.0 G | 32 | 4 | 131,072 MB (128 GB) | 20,000 Mbps | 2,097,152 MB (2 TB) |

Fleet totals: **40 hosts, 736 physical cores, 96 physical GPUs.**

**VMs** — 60, all owned by the single user `ExperimentUser`: three compute
families (20 CPU / 20 GPU / 20 MIXED), each split into fast / medium / slow
speed tiers. Every VM requests 4 vCPUs.

| Count | Compute type | Speed tier | Requested IPS/vCPU | vCPUs | GPUs | RAM | Storage | Bandwidth |
|---:|---|---|---:|---:|---:|---|---|---|
| 8 | `CPU_ONLY` | fast | 5.0 G | 4 | 0 | 4,096 MB (4 GB) | 102,400 MB (100 GB) | 1,000 Mbps |
| 8 | `CPU_ONLY` | medium | 2.0 G | 4 | 0 | 4,096 MB (4 GB) | 102,400 MB (100 GB) | 1,000 Mbps |
| 4 | `CPU_ONLY` | slow | 0.5 G | 4 | 0 | 4,096 MB (4 GB) | 102,400 MB (100 GB) | 1,000 Mbps |
| 8 | `GPU_ONLY` | fast | 5.0 G | 4 | 2 | 4,096 MB (4 GB) | 102,400 MB (100 GB) | 1,000 Mbps |
| 8 | `GPU_ONLY` | medium | 2.0 G | 4 | 1 | 4,096 MB (4 GB) | 102,400 MB (100 GB) | 1,000 Mbps |
| 4 | `GPU_ONLY` | slow | 0.5 G | 4 | 1 | 4,096 MB (4 GB) | 102,400 MB (100 GB) | 1,000 Mbps |
| 8 | `CPU_GPU_MIXED` | fast | 5.0 G | 4 | 2 | 4,096 MB (4 GB) | 102,400 MB (100 GB) | 1,000 Mbps |
| 8 | `CPU_GPU_MIXED` | medium | 2.0 G | 4 | 1 | 4,096 MB (4 GB) | 102,400 MB (100 GB) | 1,000 Mbps |
| 4 | `CPU_GPU_MIXED` | slow | 0.5 G | 4 | 1 | 4,096 MB (4 GB) | 102,400 MB (100 GB) | 1,000 Mbps |

Fleet totals: **60 VMs, 240 vCPUs, 56 VM GPU slots** (28 on GPU_ONLY VMs +
28 on MIXED VMs) — aggregate demand fits within the hosts' 736 cores and
96 GPUs. The IPS/vCPU values are *requests*: placement clamps each VM to its
host's per-core speed (§6.4), e.g. a fast 5 G VM landing on a 2.5 G-core CPU
host runs its lanes at 2.5 G.

**Tasks** — generated by `generateTasks(...)` (pure and RNG-free), cycling
through the workload-type pools and the 16 log-spaced `instructionLengths`
(the "LOG16" workload) with decoupled phases so every (type, length) pair
occurs. All tasks belong to `ExperimentUser` and are created at t = 0.

| Pool | Workload types |
|---|---|
| CPU (6 types) | `SEVEN_ZIP`, `DATABASE`, `LLM_CPU`, `IMAGE_GEN_CPU`, `CINEBENCH`, `VERACRYPT` |
| GPU (3 types) | `FURMARK`, `IMAGE_GEN_GPU`, `LLM_GPU` |

| Scenario | CPU-pool tasks | GPU-pool tasks | Total |
|---|---:|---:|---:|
| 1 — Balanced | 250 | 250 | 500 |
| 2 — GPU_Stress | 100 | 400 | 500 |
| 3 — CPU_Stress | 400 | 100 | 500 |

The 16 log-spaced instruction lengths (consecutive ratio ≈ 1.30; the bottom
is pinned at 0.5 G — the one-tick threshold on the slowest effective lane —
and the top re-fit to 25.16 G so total instruction mass matches the previous
workload):

| # | Lengths (G instructions) |
|---|---|
| 1–4 | 0.50, 0.65, 0.84, 1.09 |
| 5–8 | 1.42, 1.85, 2.40, 3.11 |
| 9–12 | 4.04, 5.25, 6.81, 8.85 |
| 13–16 | 11.49, 14.92, 19.37, 25.16 |

**Tasks per length** — each pool spreads its tasks almost evenly across the
16 lengths (pool ÷ 16 each, ±1; identical for every seed and algorithm arm):

| Scenario | CPU pool | GPU pool |
|---|---:|---:|
| Balanced | 15–16 | 15–16 |
| GPU_Stress | 6–7 | 25 |
| CPU_Stress | 25 | 6–7 |

### 2.3 Random seed

`SimulationEngine.configure(...)` / `setRandomSeed(...)` call
`RandomGenerator.initialize(seed)` — a process-global singleton
(`utils/RandomGenerator.java`). In the infrastructure pipeline the only
consumer of randomness is the user-reassignment fallback in
`UserDatacenterMappingStep` (§5); everything else in steps 1–4 is
deterministic given the configuration. Entity creation itself never draws
random numbers.

---

## 3. Entity Creation (`InitializationStep`)

`steps/InitializationStep.java` creates all entities **in dependency order**:

```java
createDatacenters(context);   // 1st - nothing depends on them yet
createHosts(context);         // 2nd - independent of DCs at creation time
createUsers(context);         // 3rd - resolves DC names -> DC ids
createVMs(context);           // 4th - linked to owner users
createTasks(context);         // 5th - linked to owner users
```

Afterwards it records the metrics `initialization.datacenters`, `.hosts`,
`.users`, `.vms`, `.tasks`.

All five entity types generate their numeric IDs from a private static
`AtomicLong` per class — IDs are unique within a JVM run and increase in
creation order.

### 3.1 CloudDatacenter

One per `DatacenterConfig` — `new CloudDatacenter(name, maxHostCapacity,
totalMaxPowerDraw)`:

| Attribute | At creation |
|---|---|
| `id` | auto-generated |
| `name` | from config |
| `maxHostCapacity` | from config — hard host-count limit (§4) |
| `totalMaxPowerDraw` | from config — power budget (W), checked at host placement (§4.2) |
| `hosts`, `customers` | empty |
| `totalMomentaryPowerDraw` | 0 W |
| `isActive` | `false` |

### 3.2 Host

One per `HostConfig` — `new Host(ips, cpuCores, computeType, gpus)` plus
RAM/network/storage setters and the configured power model:

| Attribute | At creation |
|---|---|
| `id` | auto-generated |
| `instructionsPerSecond` | from config — **per-core** speed; caps every hosted vCPU (§6.4) |
| `numberOfCpuCores`, `numberOfGpus` | from config; also installs one `CpuCore` per core and one `Gpu` per GPU for exclusive 1:1 VM binding (§6.4) |
| `computeType` | from config: `CPU_ONLY` / `GPU_ONLY` / `CPU_GPU_MIXED` |
| `ramCapacityMB`, `networkCapacityMbps`, `hardDriveCapacityMB` | from config (overriding constructor defaults 2 TB / 2 Tbps / 20 TB) |
| `powerModel` (legacy) | `PowerModelFactory.createPowerModel(powerModelName)` — only role: the power-budget projection at host placement (§4.2) |
| `measurementBasedPowerModel` | default-installed on **every** host; drives all per-tick power (§7) regardless of the configured model name |
| `assignedDatacenterId` | `null` |
| Energy/power counters, per-tick series | 0 / empty |

### 3.3 User

One per `UserConfig` — `new User(name)`; configured datacenter *names* are
resolved to datacenter *IDs* here (first half of user→DC matching,
finalized in §5):

| Attribute | At creation |
|---|---|
| `id` | auto-generated |
| `name` | from config |
| `userSelectedDatacenters` | IDs of the resolved datacenter names; a name matching no datacenter is **silently skipped** (§5 repairs empty sets). Each resolved datacenter also adds the user to its `customers` list |
| `virtualMachines`, `tasks` | empty — filled by §3.4 / §3.5 |
| `startTimestamp`, `finishTimestamp` | `null` — the session starts in §5 |

### 3.4 VM

One per `VMConfig`. A VM is a resource *request* — nothing is reserved
until a host accepts it (§6.4):

| Attribute | At creation |
|---|---|
| `id` | auto-generated |
| `userId` | owner's name from config; the VM is also added to the owner's `virtualMachines` (lookup by name — an unknown owner leaves the VM unlinked, and it fails placement, §6.2) |
| `requestedIpsPerVcpu`, `requestedVcpuCount`, `requestedGpuCount`, `requestedRamMB`, `requestedStorageMB`, `requestedBandwidthMbps`, `computeType` | from config |
| `effectiveIpsPerVcpu` | = requested; clamped to the host's per-core speed at placement (§6.4) |
| `vmState` | `CREATED` |
| `assignedHostId` | `null` |
| `assignedTasks`, `finishedTasks` | empty |

### 3.5 Task

One per `TaskConfig`, all created at t = 0 — the whole workload is known
before execution starts (**offline scheduling**):

| Attribute | At creation |
|---|---|
| `id` | auto-generated |
| `name` | from config |
| `userId` | owner's name from config; the task is also added to the owner's `tasks` |
| `instructionLength` | from config — total instructions to execute |
| `workloadType` | from config — sets the utilization and power profile (§7.2) |
| `taskCreationTime` | 0 |
| `taskExecutionStatus` | `NOT_EXECUTED` |
| `assignedVmId`; assignment / start / end timestamps | `null` — waiting time (start − creation) and turnaround (end − creation) derive from these |

---

## 4. Host → Datacenter Assignment (`HostPlacementStep`)

`steps/HostPlacementStep.java` walks **all hosts in creation order** and,
for each host not already in a datacenter, asks a pluggable
`HostPlacementStrategy` to pick one:

```java
Optional<CloudDatacenter> selected = strategy.selectDatacenter(host, datacenters);
if (selected.isPresent()) {
    selected.get().addHost(host);   // sets host.assignedDatacenterId = dc.id
    hostsPlaced++;
} else {
    hostsFailed++;                  // host stays unassigned
}
```

`CloudDatacenter.addHost` re-checks only the slot capacity and throws
`IllegalStateException` if the datacenter is full (counted as a failure —
a belt-and-braces guard; a correct strategy never triggers it).

**A host that fails placement is not an error** — the step records
`hostPlacement.hostsFailed` and moves on. But an unplaced host is inert for
the whole run: the execution loop only updates hosts with
`assignedDatacenterId != null` (§7.1), so it executes nothing and draws no
power.

### 4.1 The three strategies

All three live in `PlacementStrategy/hostPlacement/` and share the eligibility
test `datacenter.canAccommodateHost(host)` (§4.2):

| Strategy | Selection rule |
|----------|----------------|
| `FirstFitHostPlacementStrategy` *(default)* | First datacenter (in list order) that can accommodate the host. |
| `SlotBasedBestFitHostPlacementStrategy` | Among eligible datacenters, the one with the **fewest remaining host slots** after placement (tightest capacity fit — fills datacenters up). |
| `PowerAwareLoadBalancingHostPlacementStrategy` | Among eligible datacenters, the one with the **lowest power-utilization ratio** `totalMomentaryPowerDraw / totalMaxPowerDraw` (spreads power load; a datacenter with `totalMaxPowerDraw <= 0` is treated as fully utilized). Used by the research campaigns — note that with the campaign's single datacenter every host lands in it regardless. |

### 4.2 The admission check: capacity + power budget

`CloudDatacenter.canAccommodateHost(host)` requires **both**:

1. **Slot capacity**: `hosts.size() < maxHostCapacity`.
2. **Power projection**: `currentTotalDraw + hostPower <= totalMaxPowerDraw`,
   where
   - `currentTotalDraw` is refreshed as the sum of `getCurrentTotalPowerDraw()`
     over the hosts already in the datacenter, and
   - `hostPower` is the candidate's current draw, falling back to its **idle
     draw** `powerModel.calculateTotalPower(0.0, 0.0)` (the *legacy* model:
     idle CPU + idle GPU + other-components power) when the host has never
     drawn power yet.

Timing nuance worth knowing: in the standard pipeline, host placement happens
**before any simulation tick**, so every host's `getCurrentTotalPowerDraw()`
is still `0.0` — including hosts already placed. The projection therefore
evaluates to "candidate host's idle draw ≤ total budget" rather than a
cumulative fill-up of the budget. The power budget consequently acts as a
*per-host* idle-draw admission gate at placement time, not as a running total
across placements (and the execution loop itself never enforces the budget —
power-capped scheduling is handled at the task-assignment level by the
PowerCeiling study, outside this document's scope).

For reference, the idle fallback per legacy model: `StandardPowerModel`
50+30+100 = 180 W; the `MeasurementBasedPowerModel` wrapper 25+15+35.79 =
75.79 W (matching the measured idle of the reference machine, §7.2).

---

## 5. User → Datacenter Matching (`UserDatacenterMappingStep`)

Users were *initially* matched to datacenters at creation time (§3.3) by
resolving their configured datacenter names. `UserDatacenterMappingStep`
(`steps/UserDatacenterMappingStep.java`) runs **after host placement** and
finalizes that matching, because only now is it known which datacenters
actually contain hosts.

For the whole step: if **no datacenter has any hosts**, it throws a
`RuntimeException` and the simulation aborts — there is nothing to run on.

Then, per user (`processUser`):

1. **Prune invalid preferences.** Every preferred datacenter that has no
   hosts is removed from the user's list
   (`user.removeSelectedDatacenter(id)`).
2. **Repair empty preference sets.** A user left with zero valid datacenters
   (bad names in the config, or all their DCs ended up hostless) is assigned
   one **uniformly at random** among the datacenters that do have hosts:
   `RandomGenerator.getInstance().randomElement(datacentersWithHosts)`.
   Because the generator was seeded by the engine, this repair is
   reproducible run-to-run. The user is registered as a customer of that
   datacenter, and the `userMapping.reassignedUsers` metric counts it.
3. **Estimate resource demand.** The user's total demand is summed over
   their VMs' *requests* (vCPUs, GPUs, RAM, storage, bandwidth) into a
   `UserResourceRequirements` record.
4. **Advisory feasibility check.** The demand is compared against the free
   RAM/cores/GPUs summed across all hosts of the user's selected
   datacenters. Failure does **not** block anything — it only increments the
   `userMapping.insufficientResources` metric. (Actual enforcement happens
   per-VM at placement time, §6.)
5. **Start the session.** `user.startSession(currentTime)` stamps the
   session start (time 0 in the standard pipeline).

After this step, every user has ≥ 1 datacenter that contains hosts, and
those preference sets are exactly what constrains VM placement next.

---

## 6. VM → Host Placement (`VMPlacementStep`)

`steps/VMPlacementStep.java` places every VM onto a concrete host, honoring
three constraints (in this order):

1. **User datacenter preference** — candidate hosts come only from
   datacenters the VM's owner selected.
2. **Compute-type compatibility** — VM and host compute types must be
   compatible (§6.2).
3. **Resource capacity** — the chosen host must fit the VM's full request
   (§6.4).

### 6.1 Placement order: most constrained first

VMs are sorted by a flexibility score of their compute type and placed in
ascending order:

| VM compute type | Score | Rationale |
|-----------------|-------|-----------|
| `CPU_GPU_MIXED` | 0 (placed first) | can only run on MIXED hosts |
| `GPU_ONLY`      | 1 | runs on GPU_ONLY or MIXED hosts |
| `CPU_ONLY`      | 2 (placed last) | runs on CPU_ONLY or MIXED hosts |

This prevents flexible CPU VMs from occupying MIXED hosts that the
constrained MIXED VMs will need.

### 6.2 Candidate host filtering

For each VM, `getCandidateHosts` collects the hosts of every datacenter in
the owner's preference list, keeping those whose compute type is compatible
(`isComputeTypeCompatible`):

- a `CPU_GPU_MIXED` **host** accepts every VM type;
- a `CPU_GPU_MIXED` **VM** requires a `CPU_GPU_MIXED` host;
- otherwise the types must match exactly (`CPU_ONLY`→`CPU_ONLY`,
  `GPU_ONLY`→`GPU_ONLY`).

A VM whose owner cannot be found, or with zero candidate hosts, fails
placement immediately (with a recorded reason).

### 6.3 The three strategies

The `VMPlacementStrategy` picks one host among the candidates
(`PlacementStrategy/VMPlacement/`); every strategy only considers candidates
passing `host.hasCapacityForVM(vm)`:

| Strategy | Selection rule |
|----------|----------------|
| `FirstFitVMPlacementStrategy` *(default)* | First candidate host (in order) with capacity. |
| `BestFitVMPlacementStrategy` | Host with the **lowest remaining-capacity score** after placement: mean of remaining-CPU and remaining-RAM ratios (plus remaining-GPU ratio, averaged over 3, on hosts that have GPUs). Packs VMs tightly. Used by the research campaigns. |
| `LoadBalancingVMPlacementStrategy` | Host with the **lowest current utilization**, where the utilization formula depends on what the VM needs: CPU-only VMs → `cpuUtil + 0.1·ramUtil`; GPU-only VMs → `gpuUtil + 0.1·cpuUtil + 0.05·ramUtil`; mixed → `(cpuUtil+gpuUtil)/2 + 0.1·ramUtil` (utilizations are allocated/total ratios). Spreads VMs out. |

### 6.4 What "placing a VM" actually does

On success, the step calls `host.assignVM(vm)` followed by `vm.start()`:

`Host.assignVM(vm)` (`model/Host.java`):

1. **Capacity check** (`hasCapacityForVM`): the request must fit in the
   host's *remaining* RAM, CPU cores (1 vCPU ⇔ 1 physical core), free GPUs,
   storage, and network bandwidth. If not, it throws — the step catches
   this and counts the VM as failed.
2. **Reservation** (`allocateResources`): the requested RAM, cores, storage,
   and bandwidth are added to the host's allocated counters. Because
   admission is on *requested* (not used) amounts and 1 vCPU maps to 1 core,
   **oversubscription is impossible by construction**.
3. **Physical binding**: `bindCores` marks `requestedVcpuCount` free
   `CpuCore` objects as bound to this VM (1:1), `bindGpus` does the same for
   `requestedGpuCount` `Gpu` objects. These bindings matter at runtime: a
   VM can concurrently run at most as many GPU tasks as it has bound GPUs.
4. **Back-reference**: `vm.assignedHostId = host.id`.
5. **Speed clamping (rule "A2")**: the VM's effective per-vCPU speed is
   capped by the host's per-core speed:

   ```java
   vm.setEffectiveIpsPerVcpu(Math.min(vm.getRequestedIpsPerVcpu(),
                                      host.getInstructionsPerSecond()));
   ```

   A vCPU can never run faster than the physical core underneath it. E.g.
   a "fast" 5 G IPS/vCPU VM placed on a 2.5 G IPS/core CPU host runs its
   lanes at 2.5 G. This effective speed drives both task execution time and
   the speed-scaled power draw (§7.2).

`vm.start()` then flips the VM to `VmState.RUNNING`. VMs stay RUNNING for
the entire simulation (there is no per-VM shutdown in the pipeline).

Failures (owner missing, no candidates, no host with capacity, capacity
race) are recorded per-VM in `failedVMReasons` and as metrics
(`vmPlacement.vmsFailed`); like host placement, they don't abort the run —
tasks whose only compatible VMs were never placed simply can't be assigned
later.

---

## 7. Power Calculation on Hosts

With the default measurement-based model, a host's power draw for each
1-second tick is

$$
P_{\text{host}} \;=\; P_{\text{idle}} \;+\; \sum_{\ell \,\in\, \text{busy lanes}} P_{\text{inc}}(w_\ell) \cdot \left(\frac{\text{IPS}_{\text{eff}}(\ell)}{\text{IPS}_{\text{ref}}}\right)^{1.5}
$$

— the measured idle baseline plus, for every busy vCPU lane $\ell$ on the
host, the measured incremental power of the workload $w_\ell$ running on
that lane, scaled by the lane's relative speed. A host with no busy lane
draws 0 W that tick. §7.1 shows when this is evaluated during the
simulation loop; §7.2 unpacks each term.

### 7.1 When power is computed: the tick loop

`VMExecutionStep` (`steps/VMExecutionStep.java`) advances simulated time in
**fixed 1-second ticks** until every assigned task has completed. Each tick:

```java
for (VM vm : allVMs)                    // 1. VMs do work
    if (vm.isAssignedToHost() && vm.getVmState() == RUNNING) {
        vm.executeOneSecond(currentTime);   // lanes execute instructions
        vm.updateState();                   // VM-level bookkeeping
    }

for (Host host : allHosts)              // 2. hosts compute power for THIS tick
    if (host.getAssignedDatacenterId() != null)
        host.updateState();

context.advanceTime();                  // 3. clock: t -> t+1
```

Ordering is deliberate: VMs first execute their per-vCPU lanes for the tick
(each busy lane records its workload type and utilization into the VM's
current-tick snapshot), and only then does each host derive its power from
those freshly recorded lanes. Hosts never placed into a datacenter are
skipped entirely.

Inside `Host.updateState()`:

1. `activeSeconds++` (the host's powered-on clock).
2. `updatePowerConsumption()` — computes this tick's draw (§7.2).
3. **Busy/idle classification**: the host is *busy* if any of its VMs has at
   least one busy vCPU lane this tick. Busy ticks increment
   `secondsExecuting`; idle ticks increment `secondsIDLE` **and force the
   host's power to 0 W** for that tick (see below).
4. `updateEnergyConsumption()` — adds this tick's draw to the host's energy
   counters and per-tick history (1-second ticks, so watts double as joules
   per tick; `getTotalEnergyConsumedKWh()` = joules / 3,600,000).

**Idle-host power gating.** An idle host (no lane executing this tick) is
modeled as *suspended*: its draw this tick is overwritten to 0 W (all
components). The rationale, from the code comment: pending tasks always
occupy a lane, so a host with no busy lane has no remaining work — hosts
that never receive work never power on, and drained hosts stop drawing after
their last task's tick. The 0 W tick is still appended to the per-tick series
so tick indices stay aligned across hosts. Consequently the measured idle
baseline (§7.2) is only ever drawn *while the host is busy*, as the floor
under its incremental power.

### 7.2 Default path: the measurement-based, workload-aware model

Every host owns a `MeasurementBasedPowerModel`
(`model/MeasurementBasedPowerModel.java`) by default, and
`Host.updatePowerConsumption()` uses it whenever present — this is the model
behind all published energy numbers. It is calibrated from **wall-plug
measurements of a real machine** (Dell Precision 7920 workstation + Nvidia
5080 GPU, WellHise PM004 power meter, Oct–Nov 2025).

`Host.updatePowerConsumptionWorkloadAware` implements the formula from the
top of §7, summing over every busy vCPU lane of every VM on the host. Each
term:

- **Idle baseline** ($P_{\text{idle}}$): `baseIdlePowerWatts = 75.79 W` ×
  `hardwareScaleFactor` (default 1.0). Drawn once per host (not per VM) on
  busy ticks.
- **Per-lane incremental power** ($P_{\text{inc}}(w_\ell)$): each busy vCPU
  lane runs exactly one task;
  the lane contributes the *measured incremental power* of that task's
  workload type (the wall-plug delta above idle at the workload's typical
  utilization). A VM running several tasks at once contributes the sum of
  its lane powers; an idle VM contributes nothing.
- **Speed scaling** (the $(\text{IPS}_{\text{eff}}/\text{IPS}_{\text{ref}})^{1.5}$
  factor): `POWER_SCALING_EXPONENT = 1.5` (compile-time constant).
  Faster VMs draw super-linearly more power per second, so energy *per
  instruction* scales as speed^0.5 — the speed–energy trade-off the
  multi-objective studies explore. The exponent models full-range CPU DVFS
  behaviour (linear-in-frequency below ~2 GHz where voltage is pinned,
  steeper in the boost region).
- **Reference IPS** ($\text{IPS}_{\text{ref}}$): `referenceVmIps` defaults
  to 3 G. When an experiment
  wires `EnergyObjective.setHosts(hosts)` (the campaign path), the reference
  is recomputed as the **median per-core IPS of the host fleet**
  (`calculateReferenceIpsFromHosts`) and propagated to every host's model,
  so simulation and objective predictions use the same basis.

For the default campaign fleet the reference is **2.8 G** (median of
16 × 2.5 G, 12 × 2.8 G, 12 × 3.0 G hosts), and speed clamping (§6.4) leaves
five effective lane speeds, giving these factors:

| Effective lane speed | ÷ reference (2.8 G) | Power factor (ratio^1.5) | Energy per instruction (ratio^0.5) |
|---:|---:|---:|---:|
| 0.5 G | 0.18 | 0.075 | 0.42 |
| 2.0 G | 0.71 | 0.604 | 0.85 |
| 2.5 G | 0.89 | 0.844 | 0.94 |
| 2.8 G | 1.00 | 1.000 | 1.00 |
| 3.0 G | 1.07 | 1.109 | 1.04 |

Comparing two speeds directly, the reference cancels: a lane draws
(speedA ÷ speedB)^1.5 times the power of the same workload on a speedB
lane. Relative to the slowest (0.5 G) lane:

| Lane speed | Power (vs 0.5 G) | Energy per instruction (vs 0.5 G) |
|---:|---:|---:|
| 0.5 G | 1.0× | 1.00× |
| 2.0 G | 8.0× | 2.00× |
| 2.5 G | 11.2× | 2.24× |
| 2.8 G | 13.3× | 2.37× |
| 3.0 G | 14.7× | 2.45× |

Running slow saves energy, running fast saves time. The worked example
below uses the 2.0 G and 3.0 G rows of the factor table.

The measured profiles (`initializeDefaultProfiles`, incremental W above idle
at the workload's typical utilization):

| Workload | Incremental power (W) | Typical CPU util | Typical GPU util |
|---|---|---|---|
| `IDLE` | 0.00 | 0% | 0% |
| `VERACRYPT` | 19.25 | 3% | 0% |
| `DATABASE` | 39.59 | 12% | 0% |
| `IMAGE_GEN_CPU` | 90.92 | 80% | 0% |
| `LLM_CPU` | 104.21 | 55% | 0% |
| `PRIME95SmallFFT` | 124.21 | 100% | 0% |
| `SEVEN_ZIP` | 130.29 | 100% | 0% |
| `CINEBENCH` | 133.76 | 100% | 0% |
| `IMAGE_GEN_GPU` | 141.08 | 30% | 10% |
| `LLM_GPU` | 185.35 | 12% | 12% |
| `FURMARK` | 352.18 | 8% | 100% |

(`EmpiricalWorkloadProfile.calculateIncrementalPower` returns the measured
value as-is: the simulator always drives a workload at its typical
utilization — `VM.calculateUtilization` hard-codes the same percentages —
so no further utilization scaling applies.)

**CPU/GPU attribution.** Each lane's power is split into CPU and GPU parts
for reporting via `splitIncrementalPower`: proportional to
`typicalUtil × componentFullLoadWatts`, with full-load references
`CPU = 133.76 W` (CINEBENCH) and `GPU = 352.18 W` (FURMARK). The two parts
always sum to the lane power, so this changes the breakdown, never the total.
The host accumulates them as `currentCpuPowerDraw` / `currentGpuPowerDraw`,
with the idle baseline reported under `otherComponentsPowerDraw`.

**Worked example.** A MIXED host (scale 1.0, reference 2.8 G) whose VMs run,
this tick, one `SEVEN_ZIP` lane on a 2 G-effective VM and two `FURMARK`
lanes on a 3 G-effective VM:

| Contribution | Measured power | × speed factor | = draw this tick |
|---|---:|---:|---:|
| Idle baseline | 75.79 W | — | 75.79 W |
| `SEVEN_ZIP` lane at 2.0 G | 130.29 W | 0.6037 | 78.65 W |
| `FURMARK` lanes at 3.0 G (×2) | 352.18 W each | 1.1090 | 781.16 W |
| **Host total** | | | **935.60 W** |

## 8. Power and Energy on Datacenters

A datacenter never computes power of its own — **everything is aggregated
from its hosts** (`model/CloudDatacenter.java`).

### 8.1 Live aggregates

- `updateTotalMomentaryPowerDraw()` — sets `totalMomentaryPowerDraw` to the
  sum of every member host's `currentTotalPowerDraw`. Computed on demand
  (by the placement admission check and the power-aware strategy), not
  automatically per tick.
- `isPowerLimitReached()` — refreshes the momentary draw and compares it to
  `totalMaxPowerDraw`.
- `canAccommodateHost(host)` — the placement admission check (§4.2), the
  budget's only enforcement point in the pipeline.

### 8.2 Per-tick series (derived, not stored)

The datacenter reconstructs its history from the hosts' per-tick series, so
it always reflects the latest run without needing its own reset:

- `getTickCount()` — length of the longest member-host power series
  (= the run's makespan in ticks).
- `getDcPowerAtTick(t)` / `getPowerSeriesWatts()` — sum of member hosts'
  power at tick *t*; the series sums to the datacenter's total energy.
- `getBusyHostCountAtTick(t)`, `getIdleHostCountAtTick(t)`,
  `getIdleHostPercentageAtTick(t)` and their series — derived from each
  host's `busySeries`.
- `getHostContributionAtTick(hostId, t)`, `getHostPowerSeries(hostId)` —
  single-host drill-down.

### 8.3 Totals

- `getTotalEnergyConsumed()` — Σ of member hosts' `totalEnergyConsumedJoules`
  (joules); `getTotalEnergyConsumedKWh()` divides by 3.6 M.
- `getAveragePowerDraw()` — mean of member hosts' *current* draw (only
  meaningful mid-run).

### 8.4 Post-run aggregation (`EnergyCalculationStep`)

After execution, `steps/EnergyCalculationStep.java` computes the
simulation-wide energy picture:

- **IT energy**: `totalITEnergyJoules` = Σ over all hosts of their total
  energy, with per-host and per-datacenter breakdown maps, plus the
  CPU/GPU/other component split integrated during the run.
- **Facility energy**: `totalITEnergyJoules × PUE` (Power Usage
  Effectiveness, default **1.5**, configurable via `setPUE`).
- **Peaks — two distinct notions**:
  - *coincident fleet peak* (`peakTotalPowerWatts`): max over ticks of the
    summed per-host power at that tick — the true simultaneous peak, also
    computed per datacenter from `dc.getPowerSeriesWatts()`;
  - *sum of host peaks* (`sumOfHostPeaksWatts`): Σ of each host's individual
    peak — an upper bound, since host peaks need not coincide.
- **Average power** = IT joules / simulation seconds, and
  **load factor** = average / coincident peak (1.0 = perfectly flat draw).
- **Carbon footprint** = facility kWh × regional carbon intensity
  (kg CO₂/kWh; `CarbonIntensityRegion` enum, default `US_AVERAGE` = 0.42,
  ranging from `RENEWABLE_ONLY` = 0.0 to `EU_POLAND`/`INDIA` = 0.70).
- **Cost** = facility kWh × electricity price (default **$0.10/kWh**).
- **Efficiency**: energy per completed task, instructions per watt.

All of it is recorded as `energy.*` metrics and flows into the
`SimulationSummary` and CSV reports.

---

## 9. Reproducibility Notes

- All randomness flows through the singleton `RandomGenerator`, seeded from
  the configuration (`SimulationEngine.configure`/`setRandomSeed`). Within
  the infrastructure pipeline only the user-reassignment fallback (§5)
  consumes randomness; creation, host placement, and VM placement are
  deterministic given the config and its list order.
- Entity IDs come from static per-class counters, so *absolute* IDs depend
  on what ran earlier in the same JVM; all pipeline logic uses relative
  lookups (names, membership lists), never hard-coded IDs.
- `RandomGenerator` (and MOEA's PRNG) are process-global — never run two
  simulations concurrently inside one JVM if same-seed determinism matters.
- Task generation in the campaign config (`ExperimentConfig.generateTasks`)
  is deliberately RNG-free: it runs while building the configuration,
  *before* the engine re-seeds the generator.

## 10. Source Map

| Topic | Where to look |
|---|---|
| Entity creation | `steps/InitializationStep.java` |
| Config container / parsing | `config/ExperimentConfiguration.java`, `config/FileConfigParser.java`, `config/*Config.java` |
| Campaign infrastructure | `newExperiments/ExperimentConfig.java`, `newExperiments/CampaignRunner.java` |
| Datacenter model | `model/CloudDatacenter.java` |
| Host model, per-tick power/energy | `model/Host.java` |
| VM model, lanes, clamped speed | `model/VM.java` |
| User / Task models | `model/User.java`, `model/Task.java` |
| Host → DC step + strategies | `steps/HostPlacementStep.java`, `PlacementStrategy/hostPlacement/` |
| User → DC step | `steps/UserDatacenterMappingStep.java` |
| VM → Host step + strategies | `steps/VMPlacementStep.java`, `PlacementStrategy/VMPlacement/` |
| Tick loop | `steps/VMExecutionStep.java` |
| Measurement-based power model | `model/MeasurementBasedPowerModel.java`, `model/EmpiricalWorkloadProfile.java` |
| Legacy power model + factory | `model/PowerModel.java`, `factory/PowerModelFactory.java` |
| Post-run energy aggregation | `steps/EnergyCalculationStep.java` |
