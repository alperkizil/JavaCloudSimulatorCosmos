# Claude Code Instructions for JavaCloudSimulatorCosmos

## Build Environment Notes

### Do NOT Use Maven
Maven (`mvn`) commands will fail due to network restrictions. Use `javac` directly instead:

```bash
# Compile (excluding GUI and MOEA packages)
find src/main/java -name "*.java" -not -path "*/gui/*" -not -path "*/moea/*" -not -name "BatchExperimentMain*.java" | xargs javac -d target/classes

# Or for quick syntax checking of specific files
javac -d target/classes path/to/YourFile.java
```

### Packages to Exclude from Compilation

| Package/Files | Reason |
|---------------|--------|
| `*/gui/*` | Requires JavaFX runtime (not available) |
| `*/moea/*` | Requires MOEA Framework library (external dependency) |
| `BatchExperimentMain*.java` | Depends on MOEA package |

### What Works

The core simulation framework compiles and works fine:
- All model classes (`model/`)
- Simulation engine (`engine/`)
- Placement strategies (`PlacementStrategy/`) except MOEA
- Metaheuristic algorithms: GA, NSGA-II (native), **Simulated Annealing**
- Configuration system (`config/`)
- Reporters (`reporter/`)
- All utility classes (`utils/`)

## Project Architecture

- **Metaheuristics location:** `src/main/java/com/cloudsimulator/PlacementStrategy/task/metaheuristic/`
- **Cooling schedules:** `metaheuristic/cooling/` (for Simulated Annealing)
- **Termination conditions:** `metaheuristic/termination/`
- **Genetic operators:** `metaheuristic/operators/`
- **Objectives:** `metaheuristic/objectives/`

## Patterns to Follow

When implementing new metaheuristic algorithms:
1. Follow the `GAConfiguration` / `SAConfiguration` builder pattern
2. Follow the `GAStatistics` / `SAStatistics` tracking pattern
3. Implement `TaskAssignmentStrategy` interface for the facade
4. Reuse existing operators (`MutationOperator`, `RepairOperator`) where applicable
5. Use `RandomGenerator.getInstance()` for reproducibility
