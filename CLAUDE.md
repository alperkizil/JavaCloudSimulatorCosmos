# Claude Code Instructions for JavaCloudSimulatorCosmos

## Build Environment Notes

### Do NOT Use Maven
Maven (`mvn`) commands will fail due to network restrictions. Use `javac` directly instead:

```bash
# Compile (excluding GUI; MOEA deps are in lib/)
find src/main/java -name "*.java" -not -path "*/gui/*" | xargs javac -cp "lib/*" -d target/classes

# Or for quick syntax checking of specific files
javac -cp "lib/*" -d target/classes path/to/YourFile.java
```

### MOEA Framework and Dependencies

All external JARs (including MOEA Framework) are in the `lib/` folder. To compile code that uses MOEA (e.g. `*/moea/*`, `FinalExperiment/`), include them on the classpath:

```bash
# Compile everything (including MOEA) except GUI
find src/main/java -name "*.java" -not -path "*/gui/*" | xargs javac -cp "lib/*" -d target/classes

# Run an experiment
java -cp "target/classes:lib/*" com.cloudsimulator.FinalExperiment.ScenarioComparisonExperimentRunner
```

Key JARs in `lib/`:
- `moeaframework-4.5.jar` — MOEA Framework (NSGA-II, SPEA-II, AMOSA)
- `commons-math3-3.6.1.jar` — Required by MOEA Framework
- `commons-lang3-3.15.0.jar`, `commons-cli-1.8.0.jar`, `commons-io-2.16.1.jar`, `commons-text-1.12.0.jar`
- `jfreechart-1.5.5.jar`, `jcommon-1.0.24.jar` — Charting (used by GUI)

### Packages to Exclude from Compilation

| Package/Files | Reason |
|---------------|--------|
| `*/gui/*` | Requires JavaFX runtime (not available) |

### What Works

The core simulation framework compiles and works fine:
- All model classes (`model/`)
- Simulation engine (`engine/`)
- Placement strategies (`PlacementStrategy/`) including MOEA (with `lib/*` on classpath)
- Metaheuristic algorithms: GA, NSGA-II (native), **Simulated Annealing**, MOEA Framework (NSGA-II, SPEA-II, AMOSA)
- Configuration system (`config/`)
- Reporters (`reporter/`)
- All utility classes (`utils/`)

### Large Files

`README.md` exceeds the default read token limit. Always use `offset` and `limit` parameters when reading it:

```
Read(file_path="README.md", offset=0, limit=100)
```

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
