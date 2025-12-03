# JavaCloudSimulatorCosmos

A comprehensive cloud VM task scheduling simulation framework in Java, following object-oriented design principles and Gang of Four design patterns.

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
├── config/         # Configuration classes (planned for .cosc file support)
├── steps/          # Simulation step implementations (planned)
├── strategy/       # Strategy implementations for placement/scheduling (planned)
├── calculator/     # Energy calculators (planned)
└── reporter/       # Result reporters (planned)
```

## Features

- **Time-Stepped Simulation**: Fixed Δt = 1 second
- **Random Seed Support**: For experiment repeatability
- **Power & Energy Modeling**: Configurable power models with real-time tracking
- **Multi-Tenancy**: User-datacenter preferences and resource isolation
- **Comprehensive Workloads**: CPU, GPU, and mixed workload types
- **Extensible Architecture**: Plugin-based simulation steps

## Quick Start

```bash
# Compile
javac -d out src/main/java/com/cloudsimulator/**/*.java

# Run example
java -cp out com.cloudsimulator.SimulationExample
```

See full documentation and examples in the expanded README below.

---

# Full Documentation

## JavaCloudSimulatorCosmos