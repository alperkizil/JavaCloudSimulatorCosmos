package com.cloudsimulator.newExperiments;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationEngine;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.VM;

import com.cloudsimulator.PlacementStrategy.hostPlacement.PowerAwareLoadBalancingHostPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.VMPlacement.BestFitVMPlacementStrategy;
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.MultiObjectiveTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import com.cloudsimulator.steps.InitializationStep;
import com.cloudsimulator.steps.HostPlacementStep;
import com.cloudsimulator.steps.UserDatacenterMappingStep;
import com.cloudsimulator.steps.VMPlacementStep;
import com.cloudsimulator.steps.TaskAssignmentStep;
import com.cloudsimulator.steps.VMExecutionStep;
import com.cloudsimulator.steps.TaskExecutionStep;
import com.cloudsimulator.steps.EnergyCalculationStep;

import com.cloudsimulator.observer.AlgorithmRunResult;
import com.cloudsimulator.observer.ExperimentReporter;
import com.cloudsimulator.observer.ExperimentSpec;
import com.cloudsimulator.observer.ParetoAnalyzer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives a full campaign over (scenario × algorithm × seed), faithfully
 * reproducing the legacy runners' {@code runAlgorithm} / {@code simulateAllParetoSolutions}
 * sequence — built from a {@link SimulationEngine} and the real simulation steps —
 * then feeds the captured fronts through {@link ParetoAnalyzer} and writes the
 * canonical CSVs via {@link ExperimentReporter} into {@code results/<experimentId>/}.
 *
 * <p>Reproducibility-critical details preserved exactly: per-algorithm-run
 * {@code RandomGenerator} re-seed, the {@code PowerAwareLoadBalancing} host +
 * {@code BestFit} VM placement, the metrics step is skipped (objectives are read
 * straight off the {@code TaskExecutionStep}/{@code EnergyCalculationStep}), and the
 * datacenter-stat reset before each re-simulated Pareto solution.</p>
 */
public final class CampaignRunner {

    /** Swallows per-run console output in quiet (non-verbose) mode. */
    private static final PrintStream SINK = new PrintStream(OutputStream.nullOutputStream());

    private final ExperimentSpec spec;
    private final PrimaryObjective primary;
    private final ExperimentConfig infra;
    private final AlgorithmParameters params;
    private final List<String> labels;

    public CampaignRunner(ExperimentSpec spec, PrimaryObjective primary, ExperimentConfig infra,
                          AlgorithmParameters params, String[] labels) {
        this.spec = spec;
        this.primary = primary;
        this.infra = infra;
        this.params = params;
        this.labels = (labels != null)
            ? new ArrayList<>(List.of(labels))
            : new AlgorithmRegistry(params, primary).defaultLabels();
    }

    /** Runs the campaign and writes all artifacts into {@code results/<resolvedId>/}. */
    public Path run() {
        return run(spec.resolveExperimentId());
    }

    /** Builds a strategy for a run, given the placed context and the run's seed. */
    @FunctionalInterface
    interface StrategyFactory {
        TaskAssignmentStrategy create(SimulationContext context, long seed);
    }

    /**
     * Runs the campaign, writing into {@code results/<experimentId>/} (fixed id).
     *
     * <p>For studies with an auxiliary peak (PowerCeiling) this is a two-phase run:
     * <b>Phase 1</b> runs the base arms uncapped and derives the cap tiers globally
     * from the observed coincident-peak distribution; <b>Phase 2</b> re-runs each base
     * arm as a constrained {@code _PC<tier>} variant under each derived cap. The final
     * report combines the uncapped baselines with all constrained arms. Studies
     * without an aux peak run Phase 1 only.</p>
     */
    public Path run(String experimentId) {
        AlgorithmRegistry registry = new AlgorithmRegistry(params, primary);
        boolean twoPhase = spec.hasAuxPeak();
        int scenarioCount = infra.scenarioCount();
        double[] targets = PowerCapCalibrator.DEFAULT_FEASIBILITY_TARGETS;

        ConsoleReporter.printBanner(spec, primary, infra, labels, experimentId);

        // One progress-bar line per run (in place of the detailed per-run output,
        // which quiet mode swallows — see runOne). Phase 2 is planned at one
        // constrained re-run per derived cap tier; re-sized if that differs.
        int phase1Runs = scenarioCount * labels.size() * infra.numRuns;
        int plannedTotal = phase1Runs + (twoPhase ? phase1Runs * targets.length : 0);
        CampaignProgress progress = new CampaignProgress(
            System.out, experimentId, plannedTotal, !params.verboseLogging);

        // ---- Phase 1: uncapped pass (all scenarios); pool peaks for cap derivation ----
        List<List<AlgorithmRunResult>> perScenarioRuns = new ArrayList<>();
        List<AlgorithmRunResult> allUncapped = new ArrayList<>();
        for (int s = 0; s < scenarioCount; s++) {
            int scenarioNum = s + 1;
            String scenarioName = infra.scenarioNames[s];
            progress.clearLine();
            ConsoleReporter.printScenarioHeader(scenarioNum, scenarioName, infra);

            List<AlgorithmRunResult> scenarioRuns = new ArrayList<>();
            for (String label : labels) {
                final String base = label;
                for (int run = 0; run < infra.numRuns; run++) {
                    long seed = infra.baseSeed + run;
                    ExperimentConfiguration config = infra.toExperimentConfiguration(scenarioNum, seed);
                    progress.beginRun(scenarioNum, scenarioName, base, seed);
                    AlgorithmRunResult r = runOne(base, (ctx, sd) -> registry.create(base, ctx, sd),
                        config, scenarioNum, scenarioName, seed);
                    progress.endRun();
                    if (r != null) scenarioRuns.add(r);
                }
            }
            perScenarioRuns.add(scenarioRuns);
            allUncapped.addAll(scenarioRuns);
        }

        // ---- Phase 2 (PowerCeiling): derive caps, re-run constrained under each ----
        double[] caps = null;
        if (twoPhase) {
            caps = PowerCapCalibrator.deriveCaps(allUncapped, targets);
            progress.clearLine();
            logDerivedCaps(caps, targets);
            System.out.printf(java.util.Locale.US,
                "Phase 2: constrained re-run of %d arms under %d derived caps.%n",
                labels.size(), caps.length);
            if (caps.length != targets.length) {
                progress.setTotal(phase1Runs + phase1Runs * caps.length);
            }
            for (int s = 0; s < scenarioCount; s++) {
                int scenarioNum = s + 1;
                String scenarioName = infra.scenarioNames[s];
                List<AlgorithmRunResult> scenarioRuns = perScenarioRuns.get(s);
                for (int c = 0; c < caps.length; c++) {
                    final double capWatts = caps[c];
                    String tier = String.format(java.util.Locale.US, "%.0f", targets[c]);
                    for (String label : labels) {
                        final String base = label;
                        String pcLabel = base + "_PC" + tier;
                        for (int run = 0; run < infra.numRuns; run++) {
                            long seed = infra.baseSeed + run;
                            ExperimentConfiguration config = infra.toExperimentConfiguration(scenarioNum, seed);
                            progress.beginRun(scenarioNum, scenarioName, pcLabel, seed);
                            AlgorithmRunResult r = runOne(pcLabel,
                                (ctx, sd) -> registry.createPowerCeiling(base, ctx, sd, capWatts),
                                config, scenarioNum, scenarioName, seed);
                            progress.endRun();
                            if (r != null) scenarioRuns.add(r);
                        }
                    }
                }
            }
        }

        // ---- Analyze + report (per scenario: uncapped baselines + constrained arms) ----
        progress.clearLine();
        List<ExperimentReporter.ScenarioReport> reports = new ArrayList<>();
        for (int s = 0; s < scenarioCount; s++) {
            List<AlgorithmRunResult> scenarioRuns = perScenarioRuns.get(s);
            ParetoAnalyzer.ScenarioAnalysis analysis = ParetoAnalyzer.analyzeScenario(scenarioRuns);
            ExperimentReporter.ScenarioReport report = new ExperimentReporter.ScenarioReport(
                s + 1, infra.scenarioNames[s], spec.getObjectiveNames(), groupByLabel(scenarioRuns),
                analysis.universalFront, analysis.universalHV, analysis.algorithmFronts,
                analysis.seedCollaboration, analysis.universalHvFixed);
            reports.add(report);
            ConsoleReporter.printScenarioSummary(report);
        }

        try {
            Path dir = new ExperimentReporter()
                .writeExperiment(ExperimentReporter.DEFAULT_RESULTS_ROOT, experimentId, reports);
            if (twoPhase) {
                // Feasibility of every arm (uncapped + constrained) against the derived caps.
                PowerCeilingFeasibilityReporter.writeReports(dir.toString(), reports, caps);
            }
            ConsoleReporter.printDone(dir);
            PostRunScripts.runAll(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write experiment output: " + e.getMessage(), e);
        }
    }

    /** Prints the dynamically derived power-cap tiers (global, pooled across scenarios). */
    private void logDerivedCaps(double[] caps, double[] targets) {
        StringBuilder sb = new StringBuilder(
            "Derived power-cap tiers (global, from uncapped coincident peaks):");
        for (int i = 0; i < caps.length; i++) {
            sb.append(String.format(java.util.Locale.US, "%n    ~%.0f%% feasible -> %.3f kW",
                targets[i], caps[i] / 1000.0));
        }
        System.out.println(sb);
    }

    /**
     * Runs one (algorithm, seed) on one scenario through Steps 1-8 and returns the
     * captured front. Mirrors {@code runAlgorithm} exactly. The {@code factory} builds
     * the strategy from the placed context (so it can compute warm-start seeds); the
     * {@code label} is only the display/CSV name. Returns {@code null} if the factory
     * yields no strategy (e.g. a label with no power-ceiling variant).
     *
     * <p>Unless {@code params.verboseLogging} is set, {@code System.out} is swallowed
     * for the duration of the run — the engine, steps, and strategies print detailed
     * per-run output that quiet campaigns replace with the progress bar. Output
     * control only; the simulation itself is untouched. {@code System.err} (used by
     * the strategies for real failures only) stays visible.</p>
     */
    AlgorithmRunResult runOne(String label, StrategyFactory factory,
                              ExperimentConfiguration baseConfig,
                              int scenarioNum, String scenarioName, long seed) {
        boolean quiet = !params.verboseLogging;
        PrintStream saved = System.out;
        if (quiet) {
            System.setOut(SINK);
        }
        try {
            return doRunOne(label, factory, baseConfig, scenarioNum, scenarioName, seed);
        } finally {
            if (quiet) {
                System.setOut(saved);
            }
        }
    }

    private AlgorithmRunResult doRunOne(String label, StrategyFactory factory,
                                        ExperimentConfiguration baseConfig,
                                        int scenarioNum, String scenarioName, long seed) {
        long startTime = System.currentTimeMillis();

        ExperimentConfiguration config = baseConfig.clone();

        // Engine creation: provides the context, logging, and the per-run RNG re-seed.
        SimulationEngine engine = new SimulationEngine();
        engine.setRandomSeed(seed); // RandomGenerator.initialize(seed)
        SimulationContext context = engine.getContext();

        // Steps 1-4: setup (init -> host placement -> user/DC mapping -> VM placement)
        new InitializationStep(config).execute(context);
        new HostPlacementStep(new PowerAwareLoadBalancingHostPlacementStrategy()).execute(context);
        new UserDatacenterMappingStep().execute(context);
        new VMPlacementStep(new BestFitVMPlacementStrategy()).execute(context);

        // Strategy is built AFTER placement so it can compute heuristic warm-start seeds.
        TaskAssignmentStrategy strategy = factory.create(context, seed);
        if (strategy == null) {
            return null;
        }

        // Step 5: task assignment (runs the optimizer for metaheuristics).
        new TaskAssignmentStep(strategy).execute(context);

        // Steps 6-8: execution + analysis + energy for the selected solution.
        new VMExecutionStep().execute(context);
        TaskExecutionStep taskExec = new TaskExecutionStep();
        taskExec.execute(context);
        EnergyCalculationStep energyCalc = new EnergyCalculationStep();
        energyCalc.execute(context);

        double selectedPrimary = primary.extract(taskExec);
        double selectedEnergy = energyCalc.getTotalITEnergyKWh();

        // Aux coincident peak (PowerCeiling only): the Step-8 coincident fleet peak for
        // the SELECTED solution, captured before any re-simulation. Equals
        // summary.getEnergy().peakPowerWatts. null for studies without an aux peak.
        boolean aux = spec.hasAuxPeak();
        double selectedPeak = aux ? energyCalc.getPeakTotalPowerWatts() : 0.0;
        List<Double> peaks = aux ? new ArrayList<>() : null;

        List<double[]> front;
        if (strategy instanceof MultiObjectiveTaskSchedulingStrategy) {
            front = simulateAllParetoSolutions(strategy, context, peaks);
            if (front.isEmpty()) {
                front = new ArrayList<>();
                front.add(new double[] {selectedPrimary, selectedEnergy});
                if (aux) {
                    peaks.clear();
                    peaks.add(selectedPeak);
                }
            }
        } else {
            front = new ArrayList<>();
            front.add(new double[] {selectedPrimary, selectedEnergy});
            if (aux) {
                peaks.add(selectedPeak);
            }
        }

        long runtimeMs = System.currentTimeMillis() - startTime;
        return new AlgorithmRunResult(label, scenarioNum, scenarioName, seed,
            spec.getObjectiveNames(), front, peaks, runtimeMs);
    }

    /**
     * Re-simulates every Pareto-front solution through Steps 6-8 (mirrors the runner).
     * When {@code peaksOut} is non-null, appends each solution's coincident Step-8 peak
     * ({@code energyCalc.getPeakTotalPowerWatts()}) in lock-step with the returned front
     * (used only by the PowerCeiling study). When null, behaviour is identical to before.
     */
    private List<double[]> simulateAllParetoSolutions(TaskAssignmentStrategy strategy, SimulationContext context,
                                                      List<Double> peaksOut) {
        MultiObjectiveTaskSchedulingStrategy moStrategy = (MultiObjectiveTaskSchedulingStrategy) strategy;
        ParetoFront front = moStrategy.getLastParetoFront();
        if (front == null || front.isEmpty()) {
            return new ArrayList<>();
        }

        List<SchedulingSolution> paretoSolutions = front.getSolutions();
        List<double[]> simulatedResults = new ArrayList<>();

        List<VM> runningVMs = new ArrayList<>();
        for (VM vm : context.getVms()) {
            if (vm.isAssignedToHost()) {
                runningVMs.add(vm);
            }
        }

        for (SchedulingSolution solution : paretoSolutions) {
            // Full state reset (preserves infrastructure placement) + datacenter-stat reset.
            context.resetForRescheduling();
            for (CloudDatacenter dc : context.getDatacenters()) {
                dc.setActiveSeconds(0);
                dc.setTotalMomentaryPowerDraw(0.0);
            }

            moStrategy.applySolution(solution, context.getTasks(), runningVMs, context.getCurrentTime());

            new VMExecutionStep().execute(context);
            TaskExecutionStep taskExec = new TaskExecutionStep();
            taskExec.execute(context);
            EnergyCalculationStep energyCalc = new EnergyCalculationStep();
            energyCalc.execute(context);

            simulatedResults.add(new double[] {primary.extract(taskExec), energyCalc.getTotalITEnergyKWh()});
            if (peaksOut != null) {
                peaksOut.add(energyCalc.getPeakTotalPowerWatts());
            }
        }

        return simulatedResults;
    }

    private Map<String, List<AlgorithmRunResult>> groupByLabel(List<AlgorithmRunResult> runs) {
        Map<String, List<AlgorithmRunResult>> byLabel = new LinkedHashMap<>();
        for (AlgorithmRunResult r : runs) {
            byLabel.computeIfAbsent(r.getLabel(), k -> new ArrayList<>()).add(r);
        }
        return byLabel;
    }
}
