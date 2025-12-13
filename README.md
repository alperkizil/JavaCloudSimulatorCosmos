# JavaCloudSimulatorCosmos

**Version 1.0**

A comprehensive cloud VM task scheduling simulation framework in Java for modeling datacenter operations, energy consumption, and workload scheduling. Built following object-oriented design principles and Gang of Four design patterns.

## Overview

JavaCloudSimulatorCosmos enables researchers and practitioners to simulate cloud computing environments with:

- **Multi-datacenter infrastructure** modeling with power constraints
- **Virtual machine placement** across physical hosts
- **Task scheduling** with multiple optimization strategies
- **Energy and carbon footprint** tracking with regional carbon intensity
- **SLA compliance** monitoring with percentile metrics
- **Multi-objective optimization** using NSGA-II algorithm
- **Single/weighted-sum optimization** using Generational GA with Elitism
- **Comprehensive reporting** with CSV export

---

## Table of Contents

1. [Architecture](#architecture)
2. [Core Model Classes](#core-model-classes)
3. [Simulation Engine](#simulation-engine)
4. [Simulation Steps](#simulation-steps)
5. [Placement and Scheduling Strategies](#placement-and-scheduling-strategies)
6. [Configuration System](#configuration-system)
7. [GUI Configuration Generator](#gui-configuration-generator)
8. [Workload Types](#workload-types)
9. [Quick Start](#quick-start)
10. [Development](#development)

---

## Architecture

### Design Patterns

| Pattern | Implementation | Purpose |
|---------|----------------|---------|
| **Strategy** | Placement and scheduling algorithms | Interchangeable algorithms without modifying client code |
| **Template Method** | `SimulationEngine` | Defines simulation flow with customizable steps |
| **Factory** | `PowerModelFactory` | Creates power models based on configuration |
| **Singleton** | `RandomGenerator` | Ensures experiment repeatability with seeded randomness |
| **Observer** | `SimulationContext` | Centralized state management and event notification |

### Package Structure

```
com.cloudsimulator
├── model/              # Domain models (CloudDatacenter, Host, VM, Task, User)
├── enums/              # Enumerations (ComputeType, VmState, WorkloadType, etc.)
├── engine/             # Core simulation engine (SimulationEngine, SimulationContext)
├── utils/              # Utilities (RandomGenerator, SimulationLogger, SimulationClock)
├── factory/            # Factories (PowerModelFactory)
├── config/             # Configuration system (.cosc file parsing)
├── steps/              # 10 simulation step implementations
├── PlacementStrategy/  # Placement and assignment strategies
│   ├── hostPlacement/  # 5 host placement strategies
│   ├── VMPlacement/    # 4 VM placement strategies
│   └── task/           # 3 task assignment strategies + metaheuristics
│       └── metaheuristic/  # Evolutionary optimization framework
│           ├── objectives/    # Scheduling objectives (Makespan, Energy)
│           ├── operators/     # Genetic operators (Crossover, Mutation, Repair)
│           ├── selection/     # Selection operators (Tournament)
│           └── termination/   # Termination conditions
├── calculator/         # Energy calculators
├── reporter/           # CSV report generators (6 report types)
└── gui/                # JavaFX Configuration Generator
```

---

## Core Model Classes

### CloudDatacenter

Represents a physical datacenter facility with power constraints and host management.

**Attributes:**
| Attribute | Type | Description |
|-----------|------|-------------|
| `name` | `String` | Unique datacenter identifier |
| `maxHostCapacity` | `int` | Maximum number of physical hosts |
| `totalMaxPowerDrawWatts` | `double` | Power budget in watts |
| `hosts` | `List<Host>` | Physical servers in this datacenter |
| `totalEnergyConsumedJoules` | `double` | Cumulative energy consumption |

**Key Methods:**

```java
// Host management
boolean addHost(Host host)           // Add host if capacity and power allow
boolean canAcceptHost(Host host)     // Check capacity and power constraints
List<Host> getAvailableHosts()       // Get hosts that can accept VMs

// Power and energy
boolean isPowerLimitReached()        // Check if power budget exhausted
double getTotalCurrentPowerDrawWatts()  // Sum of all host power consumption
double getTotalEnergyConsumedKWh()   // Get energy in kilowatt-hours

// Utilization
double getAverageCpuUtilization()    // Average CPU utilization across hosts
double getAverageGpuUtilization()    // Average GPU utilization across hosts
```

### Host

Physical server with compute resources, power modeling, and VM hosting capability.

**Attributes:**
| Attribute | Type | Description |
|-----------|------|-------------|
| `ipsPerSecond` | `long` | Instructions per second capacity |
| `cpuCores` | `int` | Total CPU cores |
| `gpus` | `int` | Total GPU units |
| `ramMB` | `int` | Total RAM in megabytes |
| `networkMbps` | `int` | Network bandwidth |
| `storageMB` | `int` | Storage capacity |
| `computeType` | `ComputeType` | CPU_ONLY, GPU_ONLY, or CPU_GPU_MIXED |
| `powerModel` | `PowerModel` | Energy calculation model |
| `assignedVMs` | `List<VM>` | VMs running on this host |

**Key Methods:**

```java
// VM management
boolean hasCapacityForVM(VM vm)      // Check if VM resources fit
void allocateResources(VM vm)        // Reserve resources for VM
void deallocateResources(VM vm)      // Release VM resources

// Resource tracking
int getAvailableCpuCores()           // Remaining CPU cores
int getAvailableGpus()               // Remaining GPUs
int getAvailableRamMB()              // Remaining RAM

// Utilization metrics
double getCpuUtilization()           // Current CPU utilization (0.0-1.0)
double getGpuUtilization()           // Current GPU utilization (0.0-1.0)

// Power and energy
double getCurrentPowerConsumptionWatts()  // Real-time power draw
void updateEnergyConsumption(double cpuUtil, double gpuUtil)  // Track energy
```

### VM

Virtual machine that executes tasks with state management and utilization tracking.

**Attributes:**
| Attribute | Type | Description |
|-----------|------|-------------|
| `ipsPerVCPU` | `long` | Instructions per second per vCPU |
| `numberOfVCPUs` | `int` | Virtual CPU count |
| `numberOfGPUs` | `int` | Virtual GPU count |
| `ramMB` | `int` | Allocated RAM |
| `storageMB` | `int` | Allocated storage |
| `bandwidthMbps` | `int` | Allocated bandwidth |
| `vmState` | `VmState` | CREATED, RUNNING, SUSPENDED, TERMINATED |
| `assignedTasks` | `Queue<Task>` | Task execution queue |
| `currentExecutingTask` | `Task` | Currently running task |

**Key Methods:**

```java
// Task management
boolean canAcceptTask(Task task)     // Check compute type compatibility
void addTask(Task task)              // Add task to execution queue
void executeOneSecond(long timestamp) // Execute one simulation tick

// State management
void start()                         // Transition to RUNNING state
void suspend()                       // Transition to SUSPENDED state
void terminate()                     // Transition to TERMINATED state

// Utilization
double calculateUtilization(WorkloadType type)  // CPU/GPU utilization for workload
List<UtilizationRecord> getUtilizationHistory() // Historical utilization data
```

### Task

Executable workload with instruction-level progress tracking and timing metrics.

**Attributes:**
| Attribute | Type | Description |
|-----------|------|-------------|
| `name` | `String` | Task identifier |
| `instructionLength` | `long` | Total instructions to execute |
| `instructionsExecuted` | `long` | Progress counter |
| `workloadType` | `WorkloadType` | Type of workload (see Workload Types) |
| `executionStatus` | `TaskExecutionStatus` | PENDING, ASSIGNED, EXECUTING, COMPLETED, FAILED |
| `creationTimestamp` | `long` | When task was created |
| `executionStartTimestamp` | `long` | When execution began |
| `executionEndTimestamp` | `long` | When execution completed |

**Key Methods:**

```java
// Execution
void executeInstructions(long instructions)  // Execute specified instructions
boolean isComplete()                         // Check if all instructions done
long getRemainingInstructions()              // Get remaining work

// Progress tracking
double getProgressPercentage()               // Completion percentage (0-100)

// Timing calculations
long getWaitingTime()                        // Time from creation to execution start
long getTurnaroundTime()                     // Time from creation to completion
long getExecutionTime()                      // Actual execution duration
```

### User

Cloud tenant with datacenter preferences, VM ownership, and session tracking.

**Attributes:**
| Attribute | Type | Description |
|-----------|------|-------------|
| `name` | `String` | User identifier |
| `selectedDatacenterNames` | `Set<String>` | Preferred datacenter names |
| `selectedDatacenterIds` | `Set<Integer>` | Resolved datacenter IDs |
| `virtualMachines` | `List<VM>` | User's VMs |
| `tasks` | `List<Task>` | User's tasks |
| `startTimestamp` | `long` | Session start time |
| `finishTimestamp` | `long` | Session end time |

**Key Methods:**

```java
// Resource management
void addVirtualMachine(VM vm)        // Register VM to user
void addTask(Task task)              // Register task to user
void finishTask(Task task)           // Mark task as complete

// Session tracking
void startSession(long timestamp)    // Begin user session
void finishSession(long timestamp)   // End user session
boolean isSessionComplete()          // Check if all tasks finished

// Datacenter preferences
void selectDatacenter(String name)   // Add datacenter preference
boolean hasSelectedDatacenter(String name)  // Check preference
```

---

## Simulation Engine

### SimulationEngine

Main orchestrator that executes simulation steps in sequence using the Template Method pattern.

```java
SimulationEngine engine = new SimulationEngine();
engine.setDebugEnabled(true);
engine.configure("configs/sample-experiment.cosc");
engine.runSimulation(3600);  // Run for 3600 seconds

// Access results
SimulationContext context = engine.getContext();
SimulationSummary summary = context.getSummary();
```

### SimulationContext

Central state container providing access to all simulation entities and metrics.

```java
SimulationContext context = engine.getContext();

// Access entities
List<CloudDatacenter> datacenters = context.getDatacenters();
List<Host> hosts = context.getHosts();
List<VM> vms = context.getVMs();
List<Task> tasks = context.getTasks();
List<User> users = context.getUsers();

// Access metrics
Map<String, Object> metrics = context.getMetrics();
SimulationClock clock = context.getClock();
```

### SimulationStep Interface

All simulation steps implement this interface for pluggable execution:

```java
public interface SimulationStep {
    void execute(SimulationContext context);
    String getStepName();
}
```

---

## Simulation Steps

The simulation executes 10 steps in sequence:

### 1. InitializationStep

Creates all simulation entities from an `ExperimentConfiguration`.

```java
FileConfigParser parser = new FileConfigParser();
ExperimentConfiguration config = parser.parse("configs/experiment.cosc");
InitializationStep step = new InitializationStep(config);
step.execute(context);
```

**Creates:** CloudDatacenters, Hosts, Users, VMs, Tasks

**Metrics:**
- `initialization.datacenters`, `initialization.hosts`
- `initialization.users`, `initialization.vms`, `initialization.tasks`

### 2. HostPlacementStep

Assigns hosts to datacenters using a configurable placement strategy.

```java
// Default FirstFit strategy
HostPlacementStep step = new HostPlacementStep();

// Custom strategy
HostPlacementStep step = new HostPlacementStep(
    new PowerAwareConsolidatingHostPlacementStrategy()
);
```

**Metrics:**
- `hostPlacement.hostsPlaced`, `hostPlacement.hostsFailed`
- `hostPlacement.strategy`, `hostPlacement.datacenter.<name>.hostCount`

### 3. UserDatacenterMappingStep

Validates and finalizes user-datacenter relationships.

```java
UserDatacenterMappingStep step = new UserDatacenterMappingStep();
step.execute(context);
```

**Actions:**
- Removes datacenters with no hosts from user preferences
- Randomly reassigns users with no valid preferences
- Calculates resource requirements per user
- Starts user sessions

**Metrics:**
- `userMapping.usersProcessed`, `userMapping.validMappings`
- `userMapping.reassignedUsers`, `userMapping.totalRequiredVcpus`

### 4. VMPlacementStep

Assigns VMs to hosts respecting user preferences and resource constraints.

```java
// Default FirstFit strategy
VMPlacementStep step = new VMPlacementStep();

// Custom strategy
VMPlacementStep step = new VMPlacementStep(new PowerAwareVMPlacementStrategy());
```

**Constraints Enforced:**
- User datacenter preferences
- Compute type compatibility (CPU/GPU)
- Resource capacity (vCPUs, GPUs, RAM, storage, bandwidth)

**Metrics:**
- `vmPlacement.vmsPlaced`, `vmPlacement.vmsFailed`
- `vmPlacement.activeHosts`, `vmPlacement.strategy`

### 5. TaskAssignmentStep

Assigns tasks to VMs using scheduling strategies or multi-objective optimization.

```java
// Simple strategy
TaskAssignmentStep step = new TaskAssignmentStep(
    new WorkloadAwareTaskAssignmentStrategy()
);

// NSGA-II multi-objective optimization
NSGA2Configuration config = NSGA2Configuration.builder()
    .populationSize(100)
    .addObjective(new MakespanObjective())
    .addObjective(new EnergyObjective())
    .terminationCondition(new GenerationCountTermination(200))
    .build();
TaskAssignmentStep step = new TaskAssignmentStep(
    new NSGA2TaskSchedulingStrategy(config)
);
```

**Constraints Enforced:**
- User ownership (tasks only assigned to owner's VMs)
- Compute type compatibility
- VM must be in RUNNING state

**Metrics:**
- `taskAssignment.tasksAssigned`, `taskAssignment.tasksFailed`
- `taskAssignment.distribution.maxTasksPerVM`, `taskAssignment.distribution.avgTasksPerVM`

### 6. VMExecutionStep

Orchestrates the time-stepped simulation loop (fixed dt = 1 second).

```java
VMExecutionStep step = new VMExecutionStep();
step.execute(context);

System.out.println("Simulation time: " + step.getTotalSimulationSeconds() + "s");
System.out.println("Tasks completed: " + step.getTasksCompleted());
```

**Execution Flow Per Tick:**
1. For each VM in RUNNING state: `vm.executeOneSecond(currentTime)`
2. For each Host: update power consumption and energy tracking
3. Advance simulation clock by 1 second
4. Log progress every 100 ticks

**Metrics:**
- `vmExecution.totalSimulationSeconds`, `vmExecution.tasksCompleted`
- `vmExecution.vmSecondsExecuted`, `vmExecution.vmSecondsIdle`
- `vmExecution.peakConcurrentTasks`, `vmExecution.vmUtilizationRatio`

### 7. TaskExecutionStep

Performs post-simulation analysis of task completion.

```java
TaskExecutionStep step = new TaskExecutionStep();
step.execute(context);

System.out.println("Makespan: " + step.getMakespan() + " seconds");
System.out.println("Throughput: " + step.getThroughput() + " tasks/second");
```

**Analysis Performed:**
- Makespan, waiting time, turnaround time, execution time
- Per-user completion statistics
- Per-workload type statistics
- User session finalization

**Metrics:**
- `taskExecution.makespan`, `taskExecution.throughput`
- `taskExecution.avgWaitingTime`, `taskExecution.avgTurnaroundTime`
- `taskExecution.user.<name>.completed`, `taskExecution.workload.<type>.avgExecutionTime`

### 8. EnergyCalculationStep

Aggregates energy consumption with PUE, carbon footprint, and cost calculations.

```java
EnergyCalculationStep step = new EnergyCalculationStep();
step.setPUE(1.5);
step.setCarbonIntensity(CarbonIntensityRegion.EU_AVERAGE);
step.setElectricityCostPerKWh(0.12);
step.execute(context);

System.out.println("IT Energy: " + step.getTotalITEnergyKWh() + " kWh");
System.out.println("Carbon: " + step.getCarbonFootprintKg() + " kg CO2");
System.out.println("Cost: $" + step.getEstimatedCostDollars());
```

**Carbon Intensity Regions:**

| Region | kg CO2/kWh | Description |
|--------|------------|-------------|
| `US_AVERAGE` | 0.42 | US national average |
| `US_CALIFORNIA` | 0.22 | California (high renewables) |
| `EU_AVERAGE` | 0.30 | European Union average |
| `EU_FRANCE` | 0.06 | France (nuclear) |
| `EU_NORDICS` | 0.05 | Nordic countries (hydro) |
| `EU_POLAND` | 0.70 | Poland (coal-heavy) |
| `CHINA` | 0.58 | China average |
| `INDIA` | 0.70 | India average |
| `CANADA` | 0.12 | Canada (hydro) |
| `BRAZIL` | 0.08 | Brazil (hydro) |
| `RENEWABLE_ONLY` | 0.00 | 100% renewable |

**Metrics:**
- `energy.totalITEnergyJoules`, `energy.totalFacilityEnergyKWh`
- `energy.pue`, `energy.carbonFootprintKg`, `energy.estimatedCostDollars`

### 9. MetricsCollectionStep

Collects all metrics into a comprehensive `SimulationSummary` object.

```java
MetricsCollectionStep step = new MetricsCollectionStep();
step.setPrimarySLAThreshold(3600);  // 1 hour SLA
step.addSLAThreshold(1800);         // 30 min SLA
step.execute(context);

SimulationSummary summary = step.getSummary();
System.out.println("SLA Compliance: " + summary.getSla().slaCompliancePercent + "%");
System.out.println("P90 Turnaround: " + summary.getPerformance().p90TurnaroundTimeSeconds + "s");

// Export to JSON
String json = summary.toJson();
```

**SimulationSummary Structure:**
```
SimulationSummary
├── metadata (simulationId, timestamp, randomSeed)
├── infrastructure (datacenterCount, hostCount, vmCount, utilization)
├── tasks (totalTasks, completedTasks, completionRate)
├── energy (totalEnergyKWh, carbonFootprintKg, estimatedCostDollars)
├── performance (makespan, throughput, avgTurnaroundTime, p50, p90, p99)
├── sla (slaCompliancePercent, complianceByThreshold)
├── datacenters[] (per-datacenter summaries)
├── hosts[] (per-host summaries)
├── users[] (per-user summaries)
└── workloads[] (per-workload type summaries)
```

### 10. ReportingStep

Generates CSV reports organized in timestamped experiment folders.

```java
ReportingStep step = new ReportingStep();
step.setBaseOutputDirectory("./reports");
step.setCustomPrefix("my_experiment");
step.enableReport(ReportingStep.ReportType.TASKS);
step.enableReport(ReportingStep.ReportType.HOSTS);
step.execute(context);

System.out.println("Output: " + step.getOutputDirectory());
```

**Report Types:**

| Report | Filename | Description |
|--------|----------|-------------|
| `SUMMARY` | `{simId}_summary.csv` | One-row simulation overview |
| `DATACENTERS` | `{simId}_datacenters.csv` | Per-datacenter metrics |
| `HOSTS` | `{simId}_hosts.csv` | Per-host resources and energy |
| `VMS` | `{simId}_vms.csv` | Per-VM task execution |
| `TASKS` | `{simId}_tasks.csv` | Per-task timing details |
| `USERS` | `{simId}_users.csv` | Per-user session metrics |

**Output Folder Naming:**
```
{prefix}_{DATE}_{TIME}_{UNIQUEID}/
Example: my_experiment_20241209_143025_a1b2c3/
```

---

## Placement and Scheduling Strategies

### Host Placement Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `FirstFitHostPlacementStrategy` | Places in first datacenter with capacity | Fast, simple default |
| `PowerBasedBestFitHostPlacementStrategy` | Minimizes remaining power budget | Power optimization |
| `SlotBasedBestFitHostPlacementStrategy` | Minimizes remaining host slots | Capacity utilization |
| `PowerAwareConsolidatingHostPlacementStrategy` | Consolidates into fewer datacenters | Green computing |
| `PowerAwareLoadBalancingHostPlacementStrategy` | Balances load across datacenters | Fault tolerance |

### VM Placement Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `FirstFitVMPlacementStrategy` | Places on first host with capacity | Fast, simple default |
| `BestFitVMPlacementStrategy` | Minimizes remaining capacity | Resource utilization |
| `LoadBalancingVMPlacementStrategy` | Distributes to least utilized hosts | Even distribution |
| `PowerAwareVMPlacementStrategy` | Consolidates to minimize active hosts | Energy savings |

**PowerAwareVMPlacementStrategy Algorithm:**
1. Categorize hosts as "active" (has VMs) or "inactive" (no VMs)
2. For each VM: try active hosts first (prefer highest utilization)
3. If no active host fits: select smallest inactive host
4. Rationale: hosts with 0 VMs can be powered off

### Task Assignment Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `FirstAvailableTaskAssignmentStrategy` | Assigns to first compatible VM | Simple baseline |
| `ShortestQueueTaskAssignmentStrategy` | Assigns to VM with fewest tasks | Balance task count |
| `WorkloadAwareTaskAssignmentStrategy` | Minimizes estimated completion time | Heterogeneous workloads |
| `NSGA2TaskSchedulingStrategy` | Multi-objective Pareto optimization | Research, trade-off analysis |
| `GenerationalGATaskSchedulingStrategy` | Single/weighted-sum optimization with elitism | Fast convergence, single best solution |

### NSGA-II Multi-Objective Optimization

The NSGA-II strategy optimizes both task-to-VM assignment and execution ordering:

```java
NSGA2Configuration config = NSGA2Configuration.builder()
    .populationSize(100)
    .crossoverRate(0.9)
    .mutationRate(0.1)
    .addObjective(new MakespanObjective())
    .addObjective(new EnergyObjective())
    .terminationCondition(CompositeTermination.or(
        new GenerationCountTermination(200),
        TimeLimitTermination.seconds(60)
    ))
    .randomSeed(42L)
    .verboseLogging(true)
    .build();

NSGA2TaskSchedulingStrategy strategy = new NSGA2TaskSchedulingStrategy(config);
ParetoFront front = strategy.optimize(tasks, vms);

// Access trade-off solutions
SchedulingSolution bestMakespan = front.getBestForObjective(0);
SchedulingSolution bestEnergy = front.getBestForObjective(1);
SchedulingSolution kneePoint = front.getKneePoint();
```

**Objectives:**
- `MakespanObjective`: Minimize total completion time (seconds)
- `EnergyObjective`: Minimize energy consumption (kWh)

**Termination Conditions:**
- `GenerationCountTermination`: Stop after N generations
- `FitnessEvaluationsTermination`: Stop after N evaluations
- `TimeLimitTermination`: Stop after specified time
- `TargetFitnessTermination`: Stop when target reached
- `CompositeTermination`: Combine with AND/OR logic

### Generational GA with Elitism

The Generational GA strategy provides single-objective or weighted-sum optimization with guaranteed reproducibility:

```java
// Single objective optimization (minimize makespan)
GAConfiguration config = GAConfiguration.builder()
    .populationSize(100)
    .crossoverRate(0.9)
    .mutationRate(0.1)
    .elitePercentage(0.1)              // Keep top 10% unchanged
    .tournamentSize(3)                  // Tournament selection
    .objective(new MakespanObjective()) // Single objective
    .terminationCondition(new GenerationCountTermination(200))
    .verboseLogging(true)
    .build();

GenerationalGATaskSchedulingStrategy strategy =
    new GenerationalGATaskSchedulingStrategy(config);
SchedulingSolution best = strategy.optimize(tasks, vms);

// Access statistics
GAStatistics stats = strategy.getLastStatistics();
System.out.println("Best fitness: " + stats.getGlobalBestFitness());
System.out.println("Found at generation: " + stats.getBestSolutionGeneration());
```

**Weighted-Sum Multi-Objective:**

```java
// Combine objectives with weights (normalized automatically)
GAConfiguration config = GAConfiguration.builder()
    .populationSize(100)
    .eliteCount(10)                     // Absolute elitism: keep top 10
    .tournamentSize(2)
    .addWeightedObjective(new MakespanObjective(), 0.7)  // 70% weight
    .addWeightedObjective(new EnergyObjective(), 0.3)    // 30% weight
    .terminationCondition(new GenerationCountTermination(200))
    .build();
```

**Key Features:**
- **Single objective** (default): Optimize one metric (Makespan or Energy)
- **Weighted-sum**: Combine multiple objectives with configurable weights
- **Elitism**: Preserve best solutions (absolute count or percentage)
- **Tournament selection**: Configurable tournament size (k=2 to k=N)
- **Reproducibility**: Uses simulator's `RandomGenerator` for identical results with same seed

**Elitism Configuration:**

| Method | Description | Example |
|--------|-------------|---------|
| `.eliteCount(N)` | Keep exactly N best individuals | `.eliteCount(10)` |
| `.elitePercentage(P)` | Keep top P% of population | `.elitePercentage(0.1)` |

**Statistics Output:**

The algorithm tracks comprehensive metrics per generation:

```
Generation: X, Best Candidate: [task assignments], Fitness Value: Y
```

Additional metrics available via `GAStatistics`:
- `getBestFitness()`: Best fitness in current generation
- `getAverageFitness()`: Average fitness in current generation
- `getWorstFitness()`: Worst fitness in current generation
- `getStandardDeviation()`: Fitness standard deviation
- `getNoImprovementGenerations()`: Generations since last improvement
- `getGlobalBestFitness()`: Best fitness found across all generations
- `getBestSolutionGeneration()`: Generation where best was found

**Output Formats:**

```java
// Configure output format
statistics.setOutputFormat(GAStatistics.OutputFormat.DETAILED);

// Available formats:
// MINIMAL:  "Generation: X, Best: Y"
// DEFAULT:  "Generation: X, Best Candidate: [...], Fitness Value: Y"
// DETAILED: Full metrics including avg, worst, std dev
// CSV:      "generation,best,avg,worst,stddev,no_improvement"
```

**Comparison: NSGA-II vs Generational GA:**

| Feature | NSGA-II | Generational GA |
|---------|---------|-----------------|
| Output | Pareto front (multiple solutions) | Single best solution |
| Objectives | True multi-objective | Single or weighted-sum |
| Selection | Crowded tournament | Standard tournament |
| Use case | Trade-off analysis | Fast, focused optimization |
| Complexity | Higher | Lower |

---

## Configuration System

### .cosc File Format

The `.cosc` (Cosmos Config) format provides declarative experiment configuration:

```
[SEED]
42

[DATACENTERS]
3
DC-East,50,100000.0
DC-West,30,75000.0
DC-Central,40,90000.0

[HOSTS]
2
2500000000,16,CPU_ONLY,0,2097152,2000000,20971520,StandardPowerModel
3000000000,32,CPU_GPU_MIXED,4,4194304,4000000,41943040,HighPerformancePowerModel

[USERS]
2
Alice,DC-East|DC-West,2,3,1,5,3,0,2,1,4,2,1,3,2,1
Bob,DC-Central,1,2,0,3,2,1,1,0,2,1,0,2,1,0

[VMS]
GPU:2
Alice,2000000000,4,2,8192,102400,1000
Bob,2500000000,8,4,16384,204800,2000
CPU:3
Alice,2000000000,4,0,8192,102400,1000
Alice,2000000000,2,0,4096,51200,500
Bob,2500000000,8,0,16384,204800,2000

[TASKS]
SEVEN_ZIP:3
CompressData1,Alice,5000000000
CompressData2,Alice,3000000000
CompressBackup,Bob,7000000000
DATABASE:2
QueryProcessing,Alice,2000000000
TransactionBatch,Bob,4000000000
```

### Section Formats

**DATACENTERS:** `name,maxHostCapacity,totalMaxPowerDraw`

**HOSTS:** `ips,cpuCores,computeType,gpus,ram,network,storage,powerModel`
- computeType: `CPU_ONLY`, `GPU_ONLY`, or `CPU_GPU_MIXED`

**USERS:** `name,datacenters,gpuVMs,cpuVMs,mixedVMs,sevenZipTasks,dbTasks,furmarkTasks,...`
- datacenters: pipe-separated list (e.g., `DC-East|DC-West`)

**VMS:** Subsections by compute type (`GPU:count`, `CPU:count`, `MIXED:count`)
- Each line: `userName,ipsPerVcpu,vcpus,gpus,ram,storage,bandwidth`

**TASKS:** Subsections by workload type (`WORKLOAD_TYPE:count`)
- Each line: `name,userName,instructionLength`

### Programmatic Configuration

```java
// Load from file
SimulationEngine engine = new SimulationEngine();
engine.configure("configs/sample-experiment.cosc");

// Or build programmatically
ExperimentConfiguration config = new ExperimentConfiguration();
config.setRandomSeed(42);

DatacenterConfig dc = new DatacenterConfig("DC-Main", 100, 200000.0);
config.addDatacenterConfig(dc);

HostConfig host = new HostConfig(3000000000L, 32, ComputeType.CPU_GPU_MIXED,
                                  4, 4194304, 4000000, 41943040,
                                  "HighPerformancePowerModel");
config.addHostConfig(host);

engine.configure(config);
```

### Deep-Copy for Experiment Variations

```java
ExperimentConfiguration baseConfig = engine.getConfiguration();

// Run with different seeds
ExperimentConfiguration variant1 = baseConfig.cloneWithSeed(999);
engine.configure(variant1);
engine.runSimulation(3600);

ExperimentConfiguration variant2 = baseConfig.clone();
// Modify variant2...
engine.configure(variant2);
engine.runSimulation(3600);
```

### Configuration Classes

| Class | Description |
|-------|-------------|
| `ExperimentConfiguration` | Main container with `clone()` and `cloneWithSeed()` |
| `DatacenterConfig` | Datacenter specs (name, capacity, power) |
| `HostConfig` | Host specs (IPS, CPU, GPU, RAM, power model) |
| `UserConfig` | User preferences (datacenters, VM/task counts) |
| `VMConfig` | VM specs (resources, compute type, owner) |
| `TaskConfig` | Task definition (name, owner, instructions, workload) |
| `FileConfigParser` | Parser for `.cosc` files |

---

## GUI Configuration Generator

A JavaFX application for visually creating experiment configuration files.

### Features

- **Tabbed interface** for datacenters, hosts, users, VMs, and tasks
- **Multi-seed generation** with seed ranges (e.g., 1-10 generates 10 files)
- **Instruction length ranges** randomized per seed
- **Configuration summary** and file preview

### Running the GUI

**Using Maven (Recommended):**
```bash
mvn compile
mvn javafx:run
```

**Manual JavaFX:**
```bash
javac --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls \
    -d out src/main/java/com/cloudsimulator/**/*.java

java --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls \
    -cp out com.cloudsimulator.gui.ConfigGeneratorApp
```

### GUI Package Structure

```
com.cloudsimulator.gui
├── ConfigGeneratorApp.java    # Main application
├── ExperimentTemplate.java    # Configuration container
├── UserTemplate.java          # User with VMs and tasks
├── VMTemplate.java            # VM specification
├── TaskTemplate.java          # Task with instruction range
├── DatacenterPanel.java       # Datacenter UI
├── HostPanel.java             # Host UI
├── UserPanel.java             # User/VM/Task UI
├── SummaryPanel.java          # Summary and export UI
└── CosmosConfigWriter.java    # .cosc file generator
```

---

## Workload Types

10 workload types with different CPU/GPU utilization profiles:

| Workload | Description | Resource Profile |
|----------|-------------|------------------|
| `SEVEN_ZIP` | Compression | CPU-intensive |
| `DATABASE` | Database operations | CPU + moderate memory |
| `FURMARK` | GPU stress test | GPU-intensive |
| `IMAGE_GEN_CPU` | CPU image generation | CPU-intensive |
| `IMAGE_GEN_GPU` | GPU image generation | GPU-intensive |
| `LLM_CPU` | LLM inference on CPU | CPU-intensive |
| `LLM_GPU` | LLM inference on GPU | GPU-intensive |
| `CINEBENCH` | CPU rendering | CPU-intensive |
| `PRIME95SmallFFT` | CPU stress test | High CPU utilization |
| `VERACRYPT` | Disk encryption | CPU-intensive (AES) |

---

## Quick Start

### Prerequisites

- Java 17 or later
- Maven 3.6+ (for GUI and testing)

### Compile the Project

```bash
# Using Maven
mvn compile

# Manual compilation (excluding GUI)
find src/main/java -name "*.java" -not -path "*/gui/*" | xargs javac -d out
```

### Run a Simulation

```bash
# Using Maven
mvn exec:java -Dexec.mainClass="com.cloudsimulator.SimulationExample"

# Manual
java -cp out com.cloudsimulator.SimulationExample
```

### Run Tests

```bash
# Compile tests
find src/test/java -name "*.java" | xargs javac -cp out -d out

# Run individual tests
java -cp out com.cloudsimulator.ConfigTest
java -cp out com.cloudsimulator.InitializationStepTest
java -cp out com.cloudsimulator.HostPlacementStepTest
java -cp out com.cloudsimulator.VMPlacementStepTest
java -cp out com.cloudsimulator.TaskAssignmentStepTest
java -cp out com.cloudsimulator.ExecutionStepsTest
java -cp out com.cloudsimulator.ReportingStepTest
java -cp out com.cloudsimulator.NSGA2VerificationTest
java -cp out com.cloudsimulator.GenerationalGAVerificationTest
```

---

## Development

### Project Structure

```
JavaCloudSimulatorCosmos/
├── src/
│   ├── main/java/com/cloudsimulator/
│   │   ├── model/              # Domain models
│   │   ├── enums/              # Enumerations
│   │   ├── engine/             # Simulation engine
│   │   ├── utils/              # Utilities
│   │   ├── factory/            # Factories
│   │   ├── config/             # Configuration system
│   │   ├── steps/              # 10 simulation steps
│   │   ├── PlacementStrategy/  # Placement strategies
│   │   ├── calculator/         # Energy calculators
│   │   ├── reporter/           # CSV reporters
│   │   └── gui/                # JavaFX GUI
│   └── test/java/com/cloudsimulator/
│       ├── ConfigTest.java
│       ├── InitializationStepTest.java
│       ├── HostPlacementStepTest.java
│       ├── VMPlacementStepTest.java
│       ├── TaskAssignmentStepTest.java
│       ├── ExecutionStepsTest.java
│       ├── ReportingStepTest.java
│       ├── CloudDatacenterTest.java
│       ├── HostTest.java
│       ├── VMTest.java
│       ├── UserTest.java
│       ├── TaskTest.java
│       ├── NSGA2VerificationTest.java
│       └── GenerationalGAVerificationTest.java
├── configs/
│   └── sample-experiment.cosc
├── pom.xml
└── README.md
```

### Example Configuration

See `configs/sample-experiment.cosc` for a complete example with:
- 3 datacenters (DC-East, DC-West, DC-Central)
- 5 hosts with varied compute types
- 2 users (Alice, Bob)
- 6 VMs (2 GPU, 3 CPU, 1 Mixed)
- 10 tasks across 5 workload types

---

## License

This is an educational simulation framework developed for cloud computing research.
