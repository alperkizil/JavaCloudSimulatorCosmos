# JavaCloudSimulatorCosmos

A comprehensive cloud VM task scheduling simulation framework in Java, following object-oriented design principles and Gang of Four design patterns.

## Project Status

**Current Completion: 100%**

All 10 simulation steps are complete: InitializationStep, HostPlacementStep (5 strategies), UserDatacenterMappingStep, VMPlacementStep (4 strategies), TaskAssignmentStep (3 strategies + NSGA-II), VMExecutionStep, TaskExecutionStep, EnergyCalculationStep, MetricsCollectionStep, and ReportingStep (CSV export with 6 report types). The framework is fully functional for cloud VM task scheduling simulation experiments.

## Architecture

### Design Patterns Used

- **Strategy Pattern**: Interchangeable algorithms for placement and scheduling
- **Template Method Pattern**: Simulation flow orchestration in SimulationEngine
- **Factory Pattern**: PowerModelFactory for creating power models
- **Singleton Pattern**: RandomGenerator for experiment repeatability
- **Observer Pattern**: SimulationContext for state management

### Package Structure

```
com.cloudsimulator
├── model/          # Domain models (CloudDatacenter, Host, VM, Task, User)
├── enums/          # Enumerations (ComputeType, VmState, WorkloadType, etc.)
├── engine/         # Core simulation engine (SimulationEngine, SimulationContext)
├── utils/          # Utilities (RandomGenerator, SimulationLogger, SimulationClock)
├── factory/        # Factories (PowerModelFactory)
├── config/         # Configuration system - COMPLETE ✓
├── steps/          # Simulation step implementations - COMPLETE ✓ (10/10)
├── strategy/       # Placement & assignment strategies (Host: 5, VM: 4, Task: 3 + NSGA-II) - COMPLETE ✓
├── calculator/     # Energy calculators (integrated into EnergyCalculationStep) ✓
├── reporter/       # CSV report generators - COMPLETE ✓
└── gui/            # JavaFX Configuration Generator GUI ✓
```

## Features

- **Time-Stepped Simulation**: Fixed Δt = 1 second
- **Random Seed Support**: For experiment repeatability
- **Power & Energy Modeling**: Configurable power models with real-time tracking
- **Carbon Footprint Tracking**: Region-specific CO2 emissions (16 predefined regions)
- **PUE (Power Usage Effectiveness)**: Industry-standard datacenter efficiency metric
- **SLA Compliance Tracking**: Configurable thresholds with percentile metrics (P50, P90, P99)
- **Multi-Tenancy**: User-datacenter preferences and resource isolation
- **Comprehensive Workloads**: 10 workload types (CPU, GPU, and mixed)
- **Extensible Architecture**: Plugin-based simulation steps
- **Configuration System**: Custom .cosc file format for declarative experiment setup
- **Deep-Copy Support**: Clone configurations for running multiple experiment variations
- **GUI Configuration Generator**: JavaFX application for visual experiment configuration
- **JSON-Serializable Summary**: SimulationSummary object for integration with external tools
- **CSV Report Generation**: 6 report types with streaming writes and custom folder naming

## Quick Start

### 1. Compile the Project

```bash
javac -d out src/main/java/com/cloudsimulator/**/*.java
```

### 2. Run Configuration Test

```bash
java -cp out com.cloudsimulator.ConfigTest
```

### 3. Run Basic Simulation Example

```bash
java -cp out com.cloudsimulator.SimulationExample
```

---

# GUI Configuration Generator

The project includes a JavaFX-based GUI application for visually creating experiment configuration files. This tool allows you to generate multiple `.cosc` files with different random seeds while maintaining identical infrastructure configurations.

## Features

- **Visual Configuration**: Tabbed interface for configuring datacenters, hosts, users, VMs, and tasks
- **Multi-Seed Generation**: Generate multiple configuration files with seeds in a specified range (e.g., 1-10 generates 10 files)
- **Instruction Length Ranges**: Specify min/max instruction lengths for tasks; actual values are randomized per seed
- **Configuration Summary**: Review all settings before generation
- **File Preview**: Preview generated `.cosc` file content before saving

## How Multi-Seed Generation Works

When you generate configurations with a seed range (e.g., 46-56):

1. **Identical across all files**: Datacenters, Hosts, Users, VMs, task counts, and task type distribution
2. **Varies per seed**: Task instruction lengths are randomized within the specified min/max range using each seed

This allows you to run multiple experiment variations with the same infrastructure but different workload characteristics.

## Running the GUI Application

### Prerequisites

- Java 17 or later
- Maven 3.6+ (recommended) OR JavaFX SDK manually installed

### Option 1: Using Maven (Recommended)

The project includes a `pom.xml` with OpenJFX dependencies configured. This is the easiest way to run the application.

```bash
# Compile the project
mvn compile

# Run the GUI application
mvn javafx:run
```

**In VS Code**: You can also right-click on `ConfigGeneratorApp.java` and select "Run Java" after Maven downloads the dependencies.

### Option 2: Manual JavaFX Setup

If not using Maven, download the JavaFX SDK from https://openjfx.io and run manually:

```bash
# Compile with JavaFX modules (adjust path to your JavaFX SDK)
javac --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls \
    -d out src/main/java/com/cloudsimulator/**/*.java

# Run the application
java --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls \
    -cp out com.cloudsimulator.gui.ConfigGeneratorApp
```

### Using the GUI

1. **Datacenters Tab**: Add datacenter configurations (name, host capacity, max power)
2. **Hosts Tab**: Define physical host specifications (CPU cores, GPUs, RAM, storage, power model)
3. **Users Tab**: Create users, assign them to datacenters, configure VMs and task templates
   - For each user, add VMs with specific compute types (CPU, GPU, Mixed)
   - Add task templates with workload type and instruction length range
4. **Summary Tab**: Review configuration, set seed range, and generate files

## Generated File Naming

Files are named: `{seed}_{date}_{time}_{sequence}.cosc`

Example: `46_20241206_143025_001.cosc`

## GUI Package Structure

```
com.cloudsimulator.gui
├── ConfigGeneratorApp.java    # Main JavaFX application
├── ExperimentTemplate.java    # Main configuration container
├── UserTemplate.java          # User with VMs and tasks
├── VMTemplate.java            # VM specification
├── TaskTemplate.java          # Task with instruction range
├── DatacenterPanel.java       # Datacenter configuration UI
├── HostPanel.java             # Host configuration UI
├── UserPanel.java             # User/VM/Task configuration UI
├── SummaryPanel.java          # Summary and export UI
└── CosmosConfigWriter.java    # .cosc file generator
```

---

# Configuration System

The simulation uses a custom `.cosc` (Cosmos Config) file format for declarative experiment configuration.

## Configuration File Format (.cosc)

A `.cosc` file consists of 6 sections:

### 1. SEED Section

Defines the random seed for experiment repeatability.

```
[SEED]
42
```

### 2. DATACENTERS Section

Format: `count` followed by `count` lines of datacenter definitions
- Each line: `name,maxHostCapacity,totalMaxPowerDraw`

```
[DATACENTERS]
3
DC-East,50,100000.0
DC-West,30,75000.0
DC-Central,40,90000.0
```

### 3. HOSTS Section

Format: `count` followed by `count` lines of host specifications
- Each line: `ips,cpuCores,computeType,gpus,ram,network,storage,powerModel`
- computeType: `CPU_ONLY`, `GPU_ONLY`, or `CPU_GPU_MIXED`

```
[HOSTS]
2
2500000000,16,CPU_ONLY,0,2097152,2000000,20971520,StandardPowerModel
3000000000,32,CPU_GPU_MIXED,4,4194304,4000000,41943040,HighPerformancePowerModel
```

### 4. USERS Section

Format: `count` followed by `count` lines of user definitions
- Each line: `name,datacenters,gpuVMs,cpuVMs,mixedVMs,sevenZipTasks,dbTasks,furmarkTasks,imgGenCpuTasks,imgGenGpuTasks,llmCpuTasks,llmGpuTasks,cinebenchTasks,prime95Tasks,veracryptTasks`
- datacenters: pipe-separated list (e.g., `DC-East|DC-West`)

```
[USERS]
2
Alice,DC-East|DC-West,2,3,1,5,3,0,2,1,4,2,1,3,2,1
Bob,DC-Central,1,2,0,3,2,1,1,0,2,1,0,2,1,0
```

### 5. VMS Section

Format: Multiple subsections for each compute type
- Subsection header: `GPU:count`, `CPU:count`, or `MIXED:count`
- Each VM line: `userName,ipsPerVcpu,vcpus,gpus,ram,storage,bandwidth`

```
[VMS]
GPU:2
Alice,2000000000,4,2,8192,102400,1000
Bob,2500000000,8,4,16384,204800,2000
CPU:3
Alice,2000000000,4,0,8192,102400,1000
Alice,2000000000,2,0,4096,51200,500
Bob,2500000000,8,0,16384,204800,2000
```

### 6. TASKS Section

Format: Multiple subsections for each workload type
- Subsection header: `WORKLOAD_TYPE:count`
- Each task line: `name,userName,instructionLength`

```
[TASKS]
SEVEN_ZIP:3
CompressData1,Alice,5000000000
CompressData2,Alice,3000000000
CompressBackup,Bob,7000000000
DATABASE:2
QueryProcessing,Alice,2000000000
TransactionBatch,Bob,4000000000
```

## Using the Configuration System

### Loading Configuration from File

```java
SimulationEngine engine = new SimulationEngine();
engine.configure("configs/sample-experiment.cosc");

// Access the configuration
ExperimentConfiguration config = engine.getConfiguration();
System.out.println("Loaded " + config.getDatacenterConfigs().size() + " datacenters");
```

### Programmatic Configuration

```java
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

### Deep-Copy for Multiple Experiments

```java
// Load base configuration
ExperimentConfiguration baseConfig = engine.getConfiguration();

// Run with original seed
engine.configure(baseConfig);
engine.runSimulation(3600); // Run for 1 hour

// Run with different seed
ExperimentConfiguration variant1 = baseConfig.cloneWithSeed(999);
engine.configure(variant1);
engine.runSimulation(3600);

// Create another variation
ExperimentConfiguration variant2 = baseConfig.clone();
// Modify variant2 as needed...
engine.configure(variant2);
engine.runSimulation(3600);
```

## Configuration Classes

### Core Classes

- **ExperimentConfiguration**: Main container with deep-copy support via `clone()` and `cloneWithSeed()`
- **DatacenterConfig**: Datacenter specifications (name, capacity, power limit)
- **HostConfig**: Physical host specifications (IPS, CPU, GPU, RAM, storage, network, power model)
- **UserConfig**: User preferences (name, datacenter selection, VM/task counts by type)
- **VMConfig**: Virtual machine specifications (resources, compute type, owner)
- **TaskConfig**: Task definitions (name, owner, instruction length, workload type)

### Parser Interface

```java
public interface ConfigParser {
    ExperimentConfiguration parse(String configFilePath) throws ConfigurationException;
}
```

Current implementation: **FileConfigParser** for `.cosc` files

---

# Model Classes

## CloudDatacenter

Represents a physical datacenter with power constraints.

**Key Attributes:**
- `name`: Datacenter identifier
- `maxHostCapacity`: Maximum number of hosts
- `totalMaxPowerDrawWatts`: Power budget
- `hosts`: List of physical hosts
- `totalEnergyConsumedJoules`: Energy tracking

**Key Methods:**
- `addHost(Host)`: Add host if capacity/power allows
- `canAcceptHost(Host)`: Check if host can be accommodated
- `isPowerLimitReached()`: Check power budget
- `getAvailableHosts()`: Get hosts accepting VMs
- `getTotalEnergyConsumedKWh()`: Get energy in kWh

## Host

Physical server with resources and power modeling.

**Key Attributes:**
- `ipsPerSecond`: Instructions per second capacity
- `cpuCores`, `gpus`, `ramMB`, `networkMbps`, `storageMB`: Resource specs
- `allocatedCpuCores`, `allocatedGpus`, etc.: Resource tracking
- `assignedDatacenter`: Parent datacenter
- `powerModel`: Energy calculation model

**Key Methods:**
- `hasCapacityForVM(VM)`: Check if VM fits
- `allocateResources(VM)`: Reserve resources
- `deallocateResources(VM)`: Release resources
- `updateEnergyConsumption(double, double)`: Track energy usage
- `getCpuUtilization()`, `getGpuUtilization()`: Current utilization %

## VM

Virtual machine executing tasks.

**Key Attributes:**
- `ipsPerVCPU`, `numberOfVCPUs`, `numberOfGPUs`: Compute resources
- `vmState`: Current state (VmState enum)
- `assignedTasks`: Task queue
- `currentExecutingTask`: Currently running task
- `utilizationHistory`: Historical utilization records

**Key Methods:**
- `executeOneSecond(long)`: Execute for one time step
- `canAcceptTask(Task)`: Check task compatibility
- `addTask(Task)`: Enqueue task
- `calculateUtilization(WorkloadType)`: Get CPU/GPU utilization based on workload

## Task

Executable workload with instruction-level progress tracking.

**Key Attributes:**
- `instructionLength`: Total instructions
- `instructionsExecuted`: Progress counter
- `workloadType`: WorkloadType enum (SEVEN_ZIP, DATABASE, etc.)
- `executionStatus`: TaskExecutionStatus enum

**Key Methods:**
- `executeInstructions(long)`: Execute specified instructions
- `getProgressPercentage()`: Get completion %
- `isComplete()`: Check if finished
- `getRemainingInstructions()`: Get remaining work

## User

Cloud user with multi-tenancy support.

**Key Attributes:**
- `selectedDatacenterNames`: Preferred datacenters
- `virtualMachines`: User's VMs
- `tasks`: User's tasks
- `startTimestamp`, `finishTimestamp`: Session timing

**Key Methods:**
- `addVirtualMachine(VM)`: Register VM
- `addTask(Task)`: Register task
- `finishTask(Task)`: Mark task complete
- `startSession()`, `finishSession()`: Track session timing

---

# Workload Types

The simulator supports 10 workload types with different CPU/GPU utilization profiles:

1. **SEVEN_ZIP**: Compression (CPU-intensive)
2. **DATABASE**: Database operations (CPU-intensive, moderate memory)
3. **FURMARK**: GPU stress test (GPU-intensive)
4. **IMAGE_GEN_CPU**: CPU-based image generation
5. **IMAGE_GEN_GPU**: GPU-based image generation
6. **LLM_CPU**: Large language model inference on CPU
7. **LLM_GPU**: Large language model inference on GPU
8. **CINEBENCH**: CPU rendering benchmark
9. **PRIME95SmallFFT**: CPU stress test (high CPU utilization)
10. **VERACRYPT**: Disk encryption/decryption (CPU-intensive AES operations)

---

# Simulation Engine

## Core Components

### SimulationEngine

Main orchestrator using Template Method pattern.

```java
SimulationEngine engine = new SimulationEngine();
engine.setDebugEnabled(true);
engine.configure("configs/sample-experiment.cosc");
engine.runSimulation(3600); // Run for 3600 seconds
```

### SimulationContext

Central state container with:
- All entities (datacenters, hosts, VMs, tasks, users)
- Simulation clock
- Metrics tracking
- Logger access

### SimulationStep Interface

Pluggable simulation steps:

```java
public interface SimulationStep {
    void execute(SimulationContext context);
    String getStepName();
}
```

## 10 Simulation Steps (All Complete)

1. **Initialization**: Create entities from configuration ✓ COMPLETE
2. **Host Placement**: Assign hosts to datacenters (Strategy) ✓ COMPLETE
3. **User-Datacenter Mapping**: Map users to preferred datacenters ✓ COMPLETE
4. **VM Placement**: Assign VMs to hosts (Strategy) ✓ COMPLETE
5. **Task Assignment**: Assign tasks to VMs (Strategy) ✓ COMPLETE
6. **VM Execution**: Execute VM time steps ✓ COMPLETE
7. **Task Execution**: Track task progress and analyze results ✓ COMPLETE
8. **Energy Calculation**: Aggregate energy, PUE, carbon footprint, costs ✓ COMPLETE
9. **Metrics Collection**: SLA compliance, percentiles, JSON summary ✓ COMPLETE
10. **Reporting**: Generate CSV reports ✓ COMPLETE

### InitializationStep (Complete)

The `InitializationStep` creates all simulation entities from an `ExperimentConfiguration`:

```java
// Load configuration from file
FileConfigParser parser = new FileConfigParser();
ExperimentConfiguration config = parser.parse("configs/sample-experiment.cosc");

// Create InitializationStep and execute
SimulationEngine engine = new SimulationEngine();
engine.addStep(new InitializationStep(config));
engine.run();

// Access created entities
SimulationContext context = engine.getContext();
System.out.println("Datacenters: " + context.getTotalDatacenterCount());
System.out.println("Hosts: " + context.getTotalHostCount());
System.out.println("Users: " + context.getUsers().size());
System.out.println("VMs: " + context.getTotalVMCount());
System.out.println("Tasks: " + context.getTotalTaskCount());
```

**What InitializationStep creates:**
- **CloudDatacenters** with name, host capacity, and power limits
- **Hosts** with CPU/GPU specs and power models from `PowerModelFactory`
- **Users** with datacenter preferences (maps datacenter names to IDs)
- **VMs** linked to their owner users
- **Tasks** linked to their owner users

**Metrics recorded:**
- `initialization.datacenters`: Number of datacenters created
- `initialization.hosts`: Number of hosts created
- `initialization.users`: Number of users created
- `initialization.vms`: Number of VMs created
- `initialization.tasks`: Number of tasks created

### HostPlacementStep (Complete)

The `HostPlacementStep` assigns hosts to datacenters using a configurable placement strategy. This is the second step in the simulation pipeline.

```java
// Using default FirstFit strategy
HostPlacementStep step = new HostPlacementStep();

// Using custom strategy
HostPlacementStep step = new HostPlacementStep(new PowerAwareConsolidatingHostPlacementStrategy());

// Execute the step
step.execute(context);

// Check results
System.out.println("Hosts placed: " + step.getHostsPlaced());
System.out.println("Hosts failed: " + step.getHostsFailed());
```

**Available Host Placement Strategies:**

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `FirstFitHostPlacementStrategy` | Places hosts in the first datacenter with capacity | Simple, fast, default choice |
| `PowerBasedBestFitHostPlacementStrategy` | Minimizes remaining power budget after placement | Power resource optimization |
| `SlotBasedBestFitHostPlacementStrategy` | Minimizes remaining host slots after placement | Capacity utilization |
| `PowerAwareConsolidatingHostPlacementStrategy` | Consolidates hosts into fewer datacenters | Green computing, energy savings |
| `PowerAwareLoadBalancingHostPlacementStrategy` | Balances power load across datacenters | Fault tolerance, even distribution |

**Strategy Details:**

1. **First Fit**: Sequential scan, places host in first datacenter that has capacity and power budget. O(n) time complexity.

2. **Power-Based Best Fit**: Scans all datacenters, selects the one where placing the host leaves the smallest remaining power budget. Reduces power fragmentation.

3. **Slot-Based Best Fit**: Scans all datacenters, selects the one with the fewest remaining host slots after placement. Maximizes capacity utilization.

4. **Power-Aware Consolidating**: Prioritizes the most utilized datacenters first. Packs hosts into minimum number of datacenters, allowing unused ones to be powered off. Ideal for green cloud computing.

5. **Power-Aware Load Balancing**: Selects the datacenter with the lowest power utilization. Distributes load evenly to prevent hotspots and ensure fault tolerance.

**Metrics recorded:**
- `hostPlacement.hostsPlaced`: Number of hosts successfully placed
- `hostPlacement.hostsFailed`: Number of hosts that couldn't be placed
- `hostPlacement.strategy`: Name of the strategy used
- `hostPlacement.datacenter.<name>.hostCount`: Host count per datacenter

### UserDatacenterMappingStep (Complete)

The `UserDatacenterMappingStep` validates and finalizes user-datacenter relationships after hosts have been placed. This is the third step in the simulation pipeline.

```java
// Create and execute the step
UserDatacenterMappingStep mappingStep = new UserDatacenterMappingStep();
mappingStep.execute(context);

// Check results
System.out.println("Users processed: " + mappingStep.getUsersProcessed());
System.out.println("Valid mappings: " + mappingStep.getValidMappings());
System.out.println("Reassigned users: " + mappingStep.getReassignedUsers());
```

**What UserDatacenterMappingStep does:**

| Action | Description |
|--------|-------------|
| **Validate Preferences** | Removes datacenters with no hosts from user preferences |
| **Random Reassignment** | If user has no valid DCs, randomly assigns one using experiment seed |
| **Resource Calculation** | Calculates total resource requirements per user (vCPUs, GPUs, RAM) |
| **Session Start** | Calls `user.startSession(timestamp)` to mark activation |
| **Error Handling** | Throws RuntimeException if no datacenter has any hosts |

**Example Scenario:**
```
Before: Alice prefers DC-East (2 hosts), DC-West (0 hosts)
After:  Alice prefers DC-East only (DC-West removed - no hosts)

Before: Bob prefers DC-Empty (0 hosts)
After:  Bob randomly assigned to DC-East (only available option)
```

**Resource Requirements Tracking:**
```java
UserResourceRequirements req = mappingStep.getUserResourceRequirements().get("Alice");
System.out.println("VMs: " + req.vmCount);
System.out.println("vCPUs: " + req.totalVcpus);
System.out.println("GPUs: " + req.totalGpus);
System.out.println("RAM: " + req.totalRamMB + "MB");
```

**Metrics recorded:**
- `userMapping.usersProcessed`: Number of users processed
- `userMapping.validMappings`: Users with at least one valid DC
- `userMapping.reassignedUsers`: Users randomly assigned to available DC
- `userMapping.insufficientResources`: Users whose requirements may not be met
- `userMapping.datacenter.<name>.userCount`: User count per datacenter
- `userMapping.totalRequiredVcpus`: Total vCPUs required by all users
- `userMapping.totalRequiredGpus`: Total GPUs required by all users
- `userMapping.totalRequiredRamMB`: Total RAM required by all users

### VMPlacementStep (Complete)

The `VMPlacementStep` assigns VMs to hosts using a configurable placement strategy. This is the fourth step in the simulation pipeline.

```java
// Using default FirstFit strategy
VMPlacementStep step = new VMPlacementStep();

// Using custom strategy
VMPlacementStep step = new VMPlacementStep(new PowerAwareVMPlacementStrategy());

// Execute the step
step.execute(context);

// Check results
System.out.println("VMs placed: " + step.getVmsPlaced());
System.out.println("VMs failed: " + step.getVmsFailed());
System.out.println("Active hosts: " + step.getActiveHostCount());
```

**Placement Constraints Enforced:**

| Constraint | Description |
|------------|-------------|
| **User Datacenter Preferences** | VMs are only placed on hosts in datacenters the user has selected |
| **Compute Type Compatibility** | CPU_ONLY VMs → CPU_ONLY or MIXED hosts; GPU_ONLY VMs → GPU_ONLY or MIXED hosts |
| **Resource Capacity** | Host must have sufficient vCPUs, GPUs, RAM, storage, and bandwidth |

**Available VM Placement Strategies:**

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `FirstFitVMPlacementStrategy` | Places VMs on the first host with capacity | Simple, fast, default choice |
| `BestFitVMPlacementStrategy` | Minimizes remaining capacity (tightest fit) | Maximize resource utilization |
| `LoadBalancingVMPlacementStrategy` | Distributes VMs to least utilized hosts | Fault tolerance, even distribution |
| `PowerAwareVMPlacementStrategy` | Consolidates VMs to minimize active hosts | Green computing, energy savings |

**Strategy Details:**

1. **First Fit**: Sequential scan, places VM on first host with sufficient capacity. O(n) time complexity. Simple and fast but may lead to uneven distribution.

2. **Best Fit**: Scans all eligible hosts, selects the one where placing the VM leaves the smallest remaining capacity. Reduces resource fragmentation and maximizes packing efficiency.

3. **Load Balancing**: Scans all eligible hosts, selects the one with the lowest current utilization. **CPU and GPU utilization are considered separately** based on VM requirements:
   - CPU-only VMs: Prioritizes hosts with lowest CPU utilization
   - GPU-only VMs: Prioritizes hosts with lowest GPU utilization
   - Mixed VMs: Considers both CPU and GPU utilization equally

4. **Power Aware (Consolidation Heuristic)**: Minimizes power consumption by consolidating VMs into fewer hosts. Uses a consolidation heuristic that does not require power model calculations:

   **Algorithm:**
   ```
   1. Categorize hosts into:
      - "Active" hosts: Already have ≥1 VM assigned
      - "Inactive" hosts: Have 0 VMs assigned

   2. For each VM to place:
      a. First, try to place on ACTIVE hosts only
         - Among eligible active hosts, select the one with
           HIGHEST current utilization (most packed)
         - This maximizes consolidation within already-running hosts

      b. If no active host has capacity:
         - Select an INACTIVE host with the SMALLEST total capacity
         - Smaller capacity hosts typically have lower idle power consumption

   3. Utilization score = (allocatedCpus / totalCpus) + (allocatedRam / totalRam)
      Higher score = more packed = preferred for consolidation
   ```

   **Rationale:**
   - A host with 0 VMs can be powered off → 0 power consumption
   - A host with 1+ VMs consumes idle power + utilization power
   - Packing VMs into fewer hosts allows unused hosts to remain off

   **Example Scenario:**
   ```
   Hosts available:
   - Host A: 32 cores, 64GB RAM, currently 75% utilized (has 3 VMs)
   - Host B: 16 cores, 32GB RAM, currently 0% utilized (no VMs)
   - Host C: 8 cores, 16GB RAM, currently 0% utilized (no VMs)

   VM to place: needs 4 cores, 8GB RAM

   Strategy decision:
   1. Check active hosts first → Host A is active
   2. Host A has capacity? Yes (8 cores free, 16GB free)
   3. Place VM on Host A (consolidate into already-running host)

   If Host A was full:
   1. No active hosts have capacity
   2. Choose smallest inactive host → Host C (8 cores < 16 cores)
   3. Place VM on Host C (turns on the smallest host)
   ```

**Metrics recorded:**
- `vmPlacement.vmsPlaced`: Number of VMs successfully placed
- `vmPlacement.vmsFailed`: Number of VMs that couldn't be placed
- `vmPlacement.strategy`: Name of the strategy used
- `vmPlacement.activeHosts`: Number of hosts with at least one VM
- `vmPlacement.host.<id>.vmCount`: VM count per host
- `vmPlacement.datacenter.<name>.vmCount`: VM count per datacenter

### TaskAssignmentStep (Complete)

The `TaskAssignmentStep` assigns tasks to VMs using a configurable assignment strategy. This is the fifth step in the simulation pipeline.

```java
// Using default FirstAvailable strategy
TaskAssignmentStep step = new TaskAssignmentStep();

// Using greedy strategy
TaskAssignmentStep step = new TaskAssignmentStep(new WorkloadAwareTaskAssignmentStrategy());

// Using NSGA-II multi-objective optimization
NSGA2Configuration config = NSGA2Configuration.builder()
    .populationSize(100)
    .addObjective(new MakespanObjective())
    .addObjective(new EnergyObjective())
    .terminationCondition(new GenerationCountTermination(200))
    .build();
TaskAssignmentStep step = new TaskAssignmentStep(new NSGA2TaskSchedulingStrategy(config));

// Execute the step
step.execute(context);

// Check results
System.out.println("Tasks assigned: " + step.getTasksAssigned());
System.out.println("Tasks failed: " + step.getTasksFailed());
```

**Assignment Constraints Enforced:**

| Constraint | Description |
|------------|-------------|
| **User Ownership** | Tasks can only be assigned to VMs owned by the same user |
| **Compute Type Compatibility** | GPU workloads require GPU-capable VMs; CPU workloads require CPU-capable VMs |
| **VM State** | Tasks can only be assigned to VMs that are in RUNNING state |

**Available Task Assignment Strategies:**

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `FirstAvailableTaskAssignmentStrategy` | Assigns to first compatible VM | Simple baseline, fast |
| `ShortestQueueTaskAssignmentStrategy` | Assigns to VM with fewest pending tasks | Balance task count |
| `WorkloadAwareTaskAssignmentStrategy` | Minimizes estimated completion time | Heterogeneous VMs, varying task sizes |
| `NSGA2TaskSchedulingStrategy` | Multi-objective optimization (Pareto front) | Trade-off analysis, research |

**Strategy Details:**

1. **First Available**: Sequential scan, assigns task to first compatible VM found. O(m) per task where m=VMs. Simple but may create imbalanced queues.

2. **Shortest Queue**: Assigns each task to the VM with the fewest pending tasks. Balances task count but ignores task size and VM processing power.

3. **Workload Aware**: Considers task instruction length, VM processing power (IPS), and current queue workload. Calculates estimated completion time and assigns to minimize it. Greedy heuristic for makespan minimization.

4. **NSGA-II (Multi-Objective)**: Uses Non-dominated Sorting Genetic Algorithm II to optimize multiple objectives simultaneously. Returns entire Pareto front of trade-off solutions.

**NSGA-II Multi-Objective Optimization:**

The NSGA-II strategy solves the combined task assignment + scheduling problem:
- **Assignment**: Which VM each task is assigned to
- **Ordering**: In what order tasks execute within each VM

```java
// Configure NSGA-II
NSGA2Configuration config = NSGA2Configuration.builder()
    .populationSize(100)
    .crossoverRate(0.9)
    .mutationRate(0.1)
    .addObjective(new MakespanObjective())      // Minimize total completion time
    .addObjective(new EnergyObjective())         // Minimize energy consumption
    .terminationCondition(CompositeTermination.or(
        new GenerationCountTermination(200),
        TimeLimitTermination.seconds(60)
    ))
    .randomSeed(42L)
    .verboseLogging(true)
    .build();

NSGA2TaskSchedulingStrategy strategy = new NSGA2TaskSchedulingStrategy(config);

// Get entire Pareto front
ParetoFront front = strategy.optimize(tasks, vms);

// Access trade-off solutions
System.out.println("Pareto front size: " + front.size());
SchedulingSolution bestMakespan = front.getBestForObjective(0);
SchedulingSolution bestEnergy = front.getBestForObjective(1);
SchedulingSolution kneePoint = front.getKneePoint();  // Balanced trade-off
```

**Available Objectives:**

| Objective | Description | Unit |
|-----------|-------------|------|
| `MakespanObjective` | Minimizes time for all tasks to complete | seconds |
| `EnergyObjective` | Minimizes total energy consumption | joules |

*Future objectives (easily addable): WaitingTimeObjective, LoadBalanceObjective, ThroughputObjective*

**Termination Conditions:**

| Condition | Description |
|-----------|-------------|
| `GenerationCountTermination` | Stop after N generations |
| `FitnessEvaluationsTermination` | Stop after N objective evaluations |
| `TimeLimitTermination` | Stop after specified time (ms/seconds/minutes) |
| `TargetFitnessTermination` | Stop when target value is reached |
| `CompositeTermination` | Combine conditions with AND/OR logic |

**Repair Strategy for Invalid Solutions:**

NSGA-II uses a repair operator to fix invalid solutions:
1. Pre-compute valid VMs for each task (same user + compatible compute type)
2. If a task is assigned to an invalid VM, randomly select from valid VMs
3. Rebuild task ordering after repair

**Metrics recorded:**
- `taskAssignment.tasksAssigned`: Number of tasks successfully assigned
- `taskAssignment.tasksFailed`: Number of tasks that couldn't be assigned
- `taskAssignment.strategy`: Name of the strategy used
- `taskAssignment.vm.<id>.taskCount`: Task count per VM
- `taskAssignment.user.<name>.taskCount`: Task count per user
- `taskAssignment.distribution.maxTasksPerVM`: Maximum tasks on any VM
- `taskAssignment.distribution.minTasksPerVM`: Minimum tasks on any VM
- `taskAssignment.distribution.avgTasksPerVM`: Average tasks per VM

### VMExecutionStep (Complete)

The `VMExecutionStep` orchestrates the main time-stepped simulation loop where all VMs execute their assigned tasks. This is the sixth step in the simulation pipeline.

```java
// Create and execute the step
VMExecutionStep step = new VMExecutionStep();
step.execute(context);

// Check results
System.out.println("Total simulation time: " + step.getTotalSimulationSeconds() + " seconds");
System.out.println("Tasks completed: " + step.getTasksCompleted());
System.out.println("VM utilization: " + step.getVmUtilizationRatio());
```

**Execution Flow Per Tick:**

```
WHILE (assigned tasks remain incomplete):
    1. Track peak concurrent tasks
    2. For each VM assigned to a host and in RUNNING state:
       - Call vm.executeOneSecond(currentTime)
       - Update VM state (active seconds, idle time)
       - Track execution vs idle time
    3. For each Host with assigned datacenter:
       - Call host.updateState()
       - Update power consumption and energy tracking
    4. Advance simulation clock by 1 second
    5. Log progress every 100 ticks
```

**Progress Logging:**

Real-time progress is logged every 100 simulation ticks:
```
[Tick 100] Progress: 25.0% (5/20 tasks completed, 3 executing)
[Tick 200] Progress: 50.0% (10/20 tasks completed, 2 executing)
[Tick 300] Progress: 75.0% (15/20 tasks completed, 1 executing)
```

**Termination Conditions:**
- All assigned tasks are completed
- No assigned tasks exist (immediate termination)

**Metrics recorded:**
- `vmExecution.totalSimulationSeconds`: Total simulation time in seconds
- `vmExecution.vmSecondsExecuted`: Total VM-seconds spent executing tasks
- `vmExecution.vmSecondsIdle`: Total VM-seconds spent idle
- `vmExecution.tasksCompleted`: Number of tasks completed during execution
- `vmExecution.peakConcurrentTasks`: Maximum concurrent executing tasks
- `vmExecution.vmUtilizationRatio`: Ratio of executing time to total time
- `vmExecution.simulationStartTime`: Timestamp when simulation started
- `vmExecution.simulationEndTime`: Timestamp when simulation ended

### TaskExecutionStep (Complete)

The `TaskExecutionStep` performs post-simulation analysis of task completion. This is the seventh step in the simulation pipeline, executed after VMExecutionStep.

```java
// Create and execute the step
TaskExecutionStep step = new TaskExecutionStep();
step.execute(context);

// Check results
System.out.println("Makespan: " + step.getMakespan() + " seconds");
System.out.println("Avg turnaround time: " + step.getAverageTurnaroundTime() + " seconds");
System.out.println("Throughput: " + step.getThroughput() + " tasks/second");
System.out.println("Users completed: " + step.getUsersCompleted());
```

**Analysis Performed:**

| Analysis | Description |
|----------|-------------|
| **Task Metrics** | Makespan, waiting time, turnaround time, execution time |
| **Throughput** | Tasks completed per second |
| **User Session Finalization** | Marks users as complete when all their tasks finish |
| **Per-User Statistics** | Completion rate, avg times per user |
| **Per-Workload Statistics** | Completion rate, avg execution time per workload type |

**Key Metrics Calculated:**

| Metric | Formula | Description |
|--------|---------|-------------|
| **Makespan** | `lastTaskEnd - firstTaskStart` | Total time span from first task start to last task completion |
| **Waiting Time** | `taskExecStart - taskCreation` | Time a task waited before execution began |
| **Turnaround Time** | `taskExecEnd - taskCreation` | Total time from task creation to completion |
| **Throughput** | `completedTasks / makespan` | Task completion rate |

**User Session Finalization:**

```
For each user:
  IF all user's assigned tasks are completed:
    - Find the last task completion timestamp
    - Call user.finishSession(lastCompletionTime)
    - Mark user as completed
```

**Per-User Statistics:**

```java
TaskExecutionStep.UserTaskStatistics userStats = step.getUserStatistics().get("Alice");
System.out.println("Total tasks: " + userStats.totalTasks);
System.out.println("Completed: " + userStats.completedTasks);
System.out.println("Completion rate: " + userStats.getCompletionRate());
System.out.println("Avg turnaround: " + userStats.getAverageTurnaroundTime() + " sec");
```

**Per-Workload Statistics:**

```java
TaskExecutionStep.WorkloadStatistics stats = step.getWorkloadStatistics().get(WorkloadType.SEVEN_ZIP);
System.out.println("Total: " + stats.totalTasks);
System.out.println("Completed: " + stats.completedTasks);
System.out.println("Avg execution time: " + stats.getAverageExecutionTime() + " sec");
```

**Metrics recorded:**
- `taskExecution.completedTasks`: Number of tasks completed
- `taskExecution.failedTasks`: Number of tasks that failed (assigned but not completed)
- `taskExecution.unassignedTasks`: Number of tasks never assigned to a VM
- `taskExecution.makespan`: Total makespan in seconds
- `taskExecution.avgWaitingTime`: Average task waiting time
- `taskExecution.avgTurnaroundTime`: Average task turnaround time
- `taskExecution.avgExecutionTime`: Average task execution time
- `taskExecution.throughput`: Tasks per second
- `taskExecution.usersCompleted`: Number of users who completed all tasks
- `taskExecution.user.<name>.total`: Total tasks for user
- `taskExecution.user.<name>.completed`: Completed tasks for user
- `taskExecution.user.<name>.avgWaitingTime`: Average waiting time for user
- `taskExecution.user.<name>.avgTurnaroundTime`: Average turnaround time for user
- `taskExecution.workload.<type>.total`: Total tasks of workload type
- `taskExecution.workload.<type>.completed`: Completed tasks of workload type
- `taskExecution.workload.<type>.avgExecutionTime`: Average execution time for workload type

### EnergyCalculationStep (Complete)

The `EnergyCalculationStep` aggregates and analyzes energy consumption data from all hosts and datacenters after simulation completes. This is the eighth step in the simulation pipeline.

```java
// Create and execute the step
EnergyCalculationStep step = new EnergyCalculationStep();
step.setPUE(1.5);                                    // Power Usage Effectiveness
step.setCarbonIntensity(CarbonIntensityRegion.EU_AVERAGE);  // Carbon region
step.setElectricityCostPerKWh(0.12);                 // Cost per kWh
step.execute(context);

// Check results
System.out.println("Total IT Energy: " + step.getTotalITEnergyKWh() + " kWh");
System.out.println("Total Facility Energy: " + step.getTotalFacilityEnergyKWh() + " kWh");
System.out.println("Carbon Footprint: " + step.getCarbonFootprintKg() + " kg CO2");
System.out.println("Estimated Cost: $" + step.getEstimatedCostDollars());
```

**Available Carbon Intensity Regions:**

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
| `RENEWABLE_ONLY` | 0.00 | 100% renewable datacenter |

**Key Metrics Calculated:**

| Metric | Description | Unit |
|--------|-------------|------|
| **Total IT Energy** | Sum of all host energy consumption | kWh, Joules |
| **Total Facility Energy** | IT Energy × PUE | kWh, Joules |
| **Per-Datacenter Energy** | Energy breakdown per datacenter | kWh |
| **Carbon Footprint** | Facility Energy × Carbon Intensity | kg CO2 |
| **Electricity Cost** | Facility Energy × Cost/kWh | $ |
| **Energy per Task** | Total Energy / Completed Tasks | Joules |
| **Energy Efficiency** | Instructions / Energy | IPS/Watt |

**Metrics recorded:**
- `energy.totalITEnergyJoules`: IT equipment energy consumption
- `energy.totalFacilityEnergyKWh`: Total facility energy with PUE
- `energy.pue`: Configured PUE value
- `energy.carbonFootprintKg`: CO2 emissions
- `energy.estimatedCostDollars`: Electricity cost
- `energy.datacenter.<name>.energyKWh`: Per-datacenter energy
- `energy.host.<id>.energyJoules`: Per-host energy

### MetricsCollectionStep (Complete)

The `MetricsCollectionStep` collects, aggregates, and organizes all simulation metrics into a comprehensive `SimulationSummary` object. This is the ninth step in the simulation pipeline.

```java
// Create and execute the step
MetricsCollectionStep step = new MetricsCollectionStep();
step.setPrimarySLAThreshold(3600);   // 1 hour SLA
step.addSLAThreshold(1800);          // 30 min SLA
step.addSLAThreshold(7200);          // 2 hour SLA
step.execute(context);

// Access the summary
SimulationSummary summary = step.getSummary();
System.out.println("SLA Compliance: " + summary.getSla().slaCompliancePercent + "%");
System.out.println("P90 Turnaround: " + summary.getPerformance().p90TurnaroundTimeSeconds + "s");
System.out.println("Load Balance Index: " + summary.getPerformance().loadBalanceIndex);

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
├── performance (makespan, throughput, avgTurnaroundTime, percentiles)
├── sla (slaCompliancePercent, complianceByThreshold)
├── datacenters[] (per-datacenter summaries)
├── hosts[] (per-host summaries)
├── users[] (per-user summaries)
└── workloads[] (per-workload type summaries)
```

**SLA Compliance Features:**
- Multiple configurable thresholds
- Compliance calculated for each threshold
- Percentile metrics: P50, P90, P99 turnaround times
- Per-user SLA tracking

**Load Balance Index:**
- Coefficient of variation of task distribution across VMs
- 0 = perfect balance, higher values = more imbalance

**Metrics recorded:**
- `metricsCollection.infrastructure.*`: Infrastructure metrics
- `metricsCollection.tasks.completionRate`: Task completion rate
- `metricsCollection.performance.p90TurnaroundTimeSeconds`: 90th percentile turnaround
- `metricsCollection.performance.p99TurnaroundTimeSeconds`: 99th percentile turnaround
- `metricsCollection.performance.loadBalanceIndex`: Load balance metric
- `metricsCollection.sla.primaryCompliancePercent`: Primary SLA compliance
- `metricsCollection.sla.compliance.<threshold>s`: Per-threshold compliance

### ReportingStep (Complete)

The `ReportingStep` generates CSV reports from simulation results. This is the tenth and final step in the simulation pipeline. Reports are organized in experiment folders with timestamped naming.

```java
// Create and execute the step
ReportingStep step = new ReportingStep();
step.setBaseOutputDirectory("./reports");
step.setCustomPrefix("my_experiment");        // Optional custom prefix
step.enableReport(ReportingStep.ReportType.TASKS);
step.enableReport(ReportingStep.ReportType.HOSTS);
step.execute(context);

// Access results
System.out.println("Output directory: " + step.getOutputDirectory());
System.out.println("Total files: " + step.getTotalFilesGenerated());
System.out.println("Total size: " + step.getTotalBytesWritten() + " bytes");
```

**Experiment Folder Naming:**
```
{prefix}_{DATE}_{TIME}_{UNIQUEID}/
Examples:
  experiment_20241209_143025_a1b2c3/
  my_experiment_20241209_143025_d4e5f6/
```

**Available Report Types:**

| Report Type | Filename | Description |
|-------------|----------|-------------|
| `SUMMARY` | `{simId}_summary.csv` | One-row overview of entire simulation |
| `DATACENTERS` | `{simId}_datacenters.csv` | Per-datacenter infrastructure and energy |
| `HOSTS` | `{simId}_hosts.csv` | Per-host resources, utilization, energy |
| `VMS` | `{simId}_vms.csv` | Per-VM tasks, execution, utilization |
| `TASKS` | `{simId}_tasks.csv` | Per-task timing and completion details |
| `USERS` | `{simId}_users.csv` | Per-user VMs, tasks, session metrics |

**Summary Report Columns:**
- Metadata: simulation_id, timestamp, random_seed, config_file, duration
- Infrastructure: datacenter_count, host_count, vm_count, user_count, cpu_cores, gpus
- Tasks: total_tasks, completed_tasks, failed_tasks, completion_rate
- Performance: makespan, throughput, avg_waiting_time, avg_turnaround_time, p90, p99
- Energy: total_energy_kwh, pue, carbon_footprint_kg, estimated_cost_dollars
- SLA: sla_threshold_seconds, tasks_within_sla, sla_compliance_percent

**Tasks Report Columns:**
- Identity: task_id, task_name, user_id, workload_type
- Execution: instruction_length, instructions_executed, progress_percent, vm_id
- Timing: creation_time_s, assignment_time_s, exec_start_time_s, exec_end_time_s
- Derived: waiting_time_s, execution_time_s, turnaround_time_s
- Status: status, is_completed, is_assigned

**Selective Report Generation:**

```java
ReportingStep step = new ReportingStep();
step.disableAllReports();                     // Disable all first
step.enableReport(ReportingStep.ReportType.SUMMARY);
step.enableReport(ReportingStep.ReportType.TASKS);
step.execute(context);  // Only generates 2 reports
```

**Reporter Package Structure:**

```
com.cloudsimulator.reporter
├── CSVReporter.java              # Interface for CSV generation
├── AbstractCSVReporter.java      # Base class with streaming writes
├── SummaryReporter.java          # Simulation overview report
├── DatacenterReporter.java       # Per-datacenter report
├── HostReporter.java             # Per-host report
├── VMReporter.java               # Per-VM report
├── TaskReporter.java             # Per-task report (streaming)
└── UserReporter.java             # Per-user report
```

**Streaming Writes:**

The TaskReporter uses streaming writes for memory efficiency with large task counts:
```java
// Writes row-by-row instead of building full content in memory
for (Task task : tasks) {
    writer.writeRow(task.getId(), task.getName(), ...);
}
```

**Metrics recorded:**
- `reporting.filesGenerated`: Number of files created
- `reporting.totalBytesWritten`: Total size of all reports
- `reporting.outputDirectory`: Path to experiment folder
- `reporting.durationMs`: Time taken to generate reports
- `reporting.<reportType>.success`: Whether report was generated
- `reporting.<reportType>.rowCount`: Number of data rows
- `reporting.<reportType>.bytesWritten`: Size of report file

---

# What's Remaining (Future Enhancements)

## Simulation Steps ✓ ALL COMPLETE

All 10 simulation steps are implemented and tested:

- [x] InitializationStep: Create entities from ExperimentConfiguration ✓
- [x] HostPlacementStep: Assign hosts to datacenters with 5 strategies ✓
- [x] UserDatacenterMappingStep: Validate/assign users to datacenters ✓
- [x] VMPlacementStep: Assign VMs to hosts with 4 strategies ✓
- [x] TaskAssignmentStep: Assign tasks to VMs with 3 strategies + NSGA-II ✓
- [x] VMExecutionStep: Time-stepped simulation loop with progress logging ✓
- [x] TaskExecutionStep: Post-simulation analysis and user session finalization ✓
- [x] EnergyCalculationStep: Energy aggregation, PUE, carbon footprint, costs ✓
- [x] MetricsCollectionStep: SLA compliance, percentiles, JSON summary ✓
- [x] ReportingStep: Generate CSV reports with 6 report types ✓

## Strategy Pattern Implementations ✓ ALL COMPLETE

**Host Placement Strategies (5):** ✓
- FirstFitHostPlacementStrategy, PowerBasedBestFitHostPlacementStrategy
- SlotBasedBestFitHostPlacementStrategy, PowerAwareConsolidatingHostPlacementStrategy
- PowerAwareLoadBalancingHostPlacementStrategy

**VM Placement Strategies (4):** ✓
- FirstFitVMPlacementStrategy, BestFitVMPlacementStrategy
- LoadBalancingVMPlacementStrategy, PowerAwareVMPlacementStrategy

**Task Assignment Strategies (4):** ✓
- FirstAvailableTaskAssignmentStrategy, ShortestQueueTaskAssignmentStrategy
- WorkloadAwareTaskAssignmentStrategy, NSGA2TaskSchedulingStrategy

## Testing & Validation (~80% complete)

- [x] ConfigTest: Basic configuration loading
- [x] InitializationStepTest: Verifies entity creation from configuration
- [x] HostPlacementStepTest: Verifies all 5 host placement strategies
- [x] UserDatacenterMappingStepTest: Verifies user-datacenter validation and reassignment
- [x] VMPlacementStepTest: Verifies all 4 VM placement strategies with limited resources
- [x] TaskAssignmentStepTest: Verifies all 3 basic strategies + NSGA-II multi-objective optimization
- [x] ExecutionStepsTest: Verifies VMExecutionStep and TaskExecutionStep with full pipeline test
- [x] ReportingStepTest: Verifies ReportingStep with selective reports, custom prefix, and full pipeline
- [x] CloudDatacenterTest: Verifies CloudDatacenter class with 22 unit tests (constructors, host/customer management, power limits, energy tracking, host filtering)
- [x] HostTest: Verifies Host class with 26 unit tests (constructors, capacity management, VM assignment, resource allocation, power/energy tracking, utilization history)
- [x] VMTest: Verifies VM class with 29 unit tests (constructors, task assignment/execution, state management, compute type compatibility, utilization tracking, completion tracking)
- [ ] Unit tests for remaining model classes (Task, User)
- [ ] Integration tests for simulation steps
- [ ] End-to-end simulation tests
- [ ] Performance benchmarks

## Additional Features (Potential Future Work)

- [ ] Real-time simulation visualization
- [ ] REST API for simulation control
- [ ] Multi-threaded execution support
- [ ] Checkpoint/resume functionality
- [ ] Advanced power models (dynamic voltage/frequency scaling)
- [ ] Network latency modeling
- [ ] VM migration support
- [ ] Fault tolerance and recovery scenarios

---

# Development

## Build Commands

```bash
# Clean build
rm -rf out

# Compile all sources (excluding GUI which requires JavaFX)
find src/main/java -name "*.java" ! -path "*/gui/*" | xargs javac -d out

# Run configuration test
java -cp out com.cloudsimulator.ConfigTest

# Run InitializationStep test
java -cp out com.cloudsimulator.InitializationStepTest

# Run HostPlacementStep test
java -cp out com.cloudsimulator.HostPlacementStepTest

# Run UserDatacenterMappingStep test
java -cp out com.cloudsimulator.UserDatacenterMappingStepTest

# Run VMPlacementStep test
java -cp out com.cloudsimulator.VMPlacementStepTest

# Run TaskAssignmentStep test
java -cp out com.cloudsimulator.TaskAssignmentStepTest

# Run ExecutionSteps test (VMExecutionStep + TaskExecutionStep)
java -cp out com.cloudsimulator.ExecutionStepsTest

# Run ReportingStep test
java -cp out com.cloudsimulator.ReportingStepTest

# Run CloudDatacenter test
java -cp out com.cloudsimulator.CloudDatacenterTest

# Run Host test
java -cp out com.cloudsimulator.HostTest

# Run VM test
java -cp out com.cloudsimulator.VMTest

# Run simulation example
java -cp out com.cloudsimulator.SimulationExample
```

## Project Structure

```
JavaCloudSimulatorCosmos/
├── src/main/java/com/cloudsimulator/
│   ├── model/              # Domain models
│   ├── enums/              # Enumerations
│   ├── engine/             # Simulation engine
│   ├── utils/              # Utilities
│   ├── factory/            # Factories
│   ├── config/             # Configuration system ✓
│   ├── steps/              # Simulation steps (10/10 complete) ✓
│   │   ├── InitializationStep.java        # Entity creation from config ✓
│   │   ├── HostPlacementStep.java         # Host-to-datacenter assignment ✓
│   │   ├── UserDatacenterMappingStep.java # User-datacenter validation ✓
│   │   ├── VMPlacementStep.java           # VM-to-host assignment ✓
│   │   ├── TaskAssignmentStep.java        # Task-to-VM assignment ✓
│   │   ├── VMExecutionStep.java           # Time-stepped simulation loop ✓
│   │   ├── TaskExecutionStep.java         # Post-simulation analysis ✓
│   │   ├── EnergyCalculationStep.java     # Energy/carbon/cost metrics ✓
│   │   ├── MetricsCollectionStep.java     # SLA compliance, percentiles ✓
│   │   └── ReportingStep.java             # CSV report generation ✓
│   ├── strategy/           # Placement & assignment strategies ✓
│   │   ├── HostPlacementStrategy.java                    # Host strategy interface
│   │   ├── FirstFitHostPlacementStrategy.java            # First Fit algorithm
│   │   ├── PowerBasedBestFitHostPlacementStrategy.java   # Power-based Best Fit
│   │   ├── SlotBasedBestFitHostPlacementStrategy.java    # Slot-based Best Fit
│   │   ├── PowerAwareConsolidatingHostPlacementStrategy.java  # Consolidating
│   │   ├── PowerAwareLoadBalancingHostPlacementStrategy.java  # Load Balancing
│   │   ├── VMPlacementStrategy.java                      # VM strategy interface
│   │   ├── FirstFitVMPlacementStrategy.java              # First Fit for VMs
│   │   ├── BestFitVMPlacementStrategy.java               # Best Fit for VMs
│   │   ├── LoadBalancingVMPlacementStrategy.java         # Load Balancing for VMs
│   │   ├── PowerAwareVMPlacementStrategy.java            # Power Aware for VMs
│   │   └── task/                                         # Task assignment strategies ✓
│   │       ├── TaskAssignmentStrategy.java               # Task strategy interface
│   │       ├── FirstAvailableTaskAssignmentStrategy.java # First Available
│   │       ├── ShortestQueueTaskAssignmentStrategy.java  # Shortest Queue
│   │       ├── WorkloadAwareTaskAssignmentStrategy.java  # Workload Aware
│   │       └── metaheuristic/                            # NSGA-II framework ✓
│   │           ├── SchedulingSolution.java               # Solution chromosome
│   │           ├── SchedulingObjective.java              # Objective interface
│   │           ├── NSGA2Configuration.java               # Algorithm config
│   │           ├── NSGA2Algorithm.java                   # Core NSGA-II
│   │           ├── NSGA2TaskSchedulingStrategy.java      # Strategy wrapper
│   │           ├── ParetoFront.java                      # Pareto front container
│   │           ├── objectives/                           # Objective implementations
│   │           │   ├── MakespanObjective.java            # Minimize makespan
│   │           │   └── EnergyObjective.java              # Minimize energy
│   │           ├── termination/                          # Termination conditions
│   │           │   ├── TerminationCondition.java         # Condition interface
│   │           │   ├── AlgorithmStatistics.java          # Runtime statistics
│   │           │   ├── GenerationCountTermination.java   # Stop after N gens
│   │           │   ├── FitnessEvaluationsTermination.java # Stop after N evals
│   │           │   ├── TimeLimitTermination.java         # Stop after time
│   │           │   ├── TargetFitnessTermination.java     # Stop at target
│   │           │   └── CompositeTermination.java         # AND/OR logic
│   │           └── operators/                            # Genetic operators
│   │               ├── RepairOperator.java               # Solution repair
│   │               ├── CrossoverOperator.java            # Recombination
│   │               └── MutationOperator.java             # Mutation
│   ├── calculator/         # Calculators (integrated into steps) ✓
│   ├── reporter/           # CSV report generators ✓
│   │   ├── CSVReporter.java              # Interface for CSV generation
│   │   ├── AbstractCSVReporter.java      # Base class with streaming writes
│   │   ├── SummaryReporter.java          # Simulation overview report
│   │   ├── DatacenterReporter.java       # Per-datacenter report
│   │   ├── HostReporter.java             # Per-host report
│   │   ├── VMReporter.java               # Per-VM report
│   │   ├── TaskReporter.java             # Per-task report
│   │   └── UserReporter.java             # Per-user report
│   ├── gui/                # JavaFX Configuration Generator ✓
│   ├── ConfigTest.java                  # Config system test
│   ├── InitializationStepTest.java      # InitializationStep test ✓
│   ├── HostPlacementStepTest.java       # HostPlacementStep test ✓
│   ├── UserDatacenterMappingStepTest.java  # UserDatacenterMappingStep test ✓
│   ├── VMPlacementStepTest.java         # VMPlacementStep test ✓
│   ├── TaskAssignmentStepTest.java      # TaskAssignmentStep test ✓
│   ├── ExecutionStepsTest.java          # VMExecutionStep + TaskExecutionStep test ✓
│   ├── ReportingStepTest.java           # ReportingStep test ✓
│   └── SimulationExample.java           # Basic example
├── configs/
│   └── sample-experiment.cosc  # Example configuration
├── out/                    # Compiled classes
└── README.md
```

## Example Configuration

See `configs/sample-experiment.cosc` for a complete example with:
- 3 datacenters (DC-East, DC-West, DC-Central)
- 5 hosts with varied compute types
- 2 users (Alice, Bob)
- 6 VMs (2 GPU, 3 CPU, 1 Mixed)
- 10 tasks across 5 workload types

---

# License

This is an educational simulation framework developed for cloud computing research.
