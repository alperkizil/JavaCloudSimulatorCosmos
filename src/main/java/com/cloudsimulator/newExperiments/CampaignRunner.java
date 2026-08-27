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

import com.cloudsimulator.model.Task;

import com.cloudsimulator.observer.AlgorithmRunResult;
import com.cloudsimulator.observer.ExperimentReporter;
import com.cloudsimulator.observer.ExperimentSpec;
import com.cloudsimulator.observer.ParetoAnalyzer;
import com.cloudsimulator.observer.SolutionDetailsCollector;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Captures per-solution schedule details during the Pareto re-simulation
     * loop (see {@link SolutionDetailsCollector}); null when disabled via
     * {@link ExperimentConfig#exportSolutionDetails}. Additive only: it reads
     * getters off the already-executed steps and never touches the RNG.
     */
    private SolutionDetailsCollector detailsCollector;

    /**
     * Identifies how cap tiers are derived, persisted in the calibration manifest so a
     * tier name cannot be read as the same treatment across schemes.
     */
    private static final String CAP_SCHEME_ID = "anchored-pref-v1";

    /**
     * Sink for each run's verbose algorithm output when
     * {@link ExperimentConfig#captureAlgorithmLog} is on; null when disabled, in which
     * case the output is discarded as before. See {@link #runOne}.
     */
    private PrintStream algorithmLog;

    /** The caller's verbose setting, i.e. whether run output should also reach the console. */
    private boolean consoleVerbose;

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
     * <b>Phase 1</b> runs the base arms uncapped and derives each scenario's cap tiers
     * from that scenario's own coincident peaks; <b>Phase 2</b> re-runs each base
     * arm as a constrained {@code _PC<tier>} variant under each derived cap. The final
     * report combines the uncapped baselines with all constrained arms. Studies
     * without an aux peak run Phase 1 only.</p>
     */
    public Path run(String experimentId) {
        AlgorithmRegistry registry = new AlgorithmRegistry(params, primary);
        boolean twoPhase = spec.hasAuxPeak();
        int scenarioCount = infra.scenarioCount();
        double[] targets = PowerCapCalibrator.DEFAULT_ANCHOR_FRACTIONS;

        ConsoleReporter.printBanner(spec, primary, infra, labels, experimentId);

        // Verbose algorithm output is otherwise thrown away to keep the progress bar
        // readable. Open the experiment folder now (writeExperiment creates it too, and
        // createDirectories is idempotent) so per-run diagnostics can be streamed into
        // it as the campaign proceeds rather than lost with the console scrollback.
        Path resultsDir = Paths.get(ExperimentReporter.DEFAULT_RESULTS_ROOT, experimentId);
        consoleVerbose = params.verboseLogging;
        if (infra.captureAlgorithmLog) {
            try {
                Files.createDirectories(resultsDir);
                algorithmLog = new PrintStream(
                    Files.newOutputStream(resultsDir.resolve("algorithm_log.txt")), true);
                // The algorithms only emit detail when their config says verbose, so
                // turn it on for them; consoleVerbose still governs the console.
                params.verboseLogging = true;
            } catch (IOException e) {
                System.err.println("  WARNING: cannot write algorithm_log.txt: " + e.getMessage());
                algorithmLog = null;
            }
        }
        try {

        detailsCollector = infra.exportSolutionDetails
            ? new SolutionDetailsCollector(spec.getObjectiveNames()) : null;

        // One progress-bar line per run (in place of the detailed per-run output,
        // which quiet mode swallows — see runOne). Phase 2 is planned at one
        // constrained re-run per derived cap tier; re-sized if that differs.
        int phase1Runs = scenarioCount * labels.size() * infra.numRuns;
        int plannedTotal = phase1Runs + (twoPhase ? phase1Runs * targets.length : 0);
        CampaignProgress progress = new CampaignProgress(
            System.out, experimentId, plannedTotal, !params.verboseLogging);

        // ---- Phase 1: uncapped pass (all scenarios); each scenario's peaks feed its own caps ----
        List<List<AlgorithmRunResult>> perScenarioRuns = new ArrayList<>();
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
        }

        // ---- Phase 2 (PowerCeiling): derive caps, re-run constrained under each ----
        // Caps are anchored to P_ref (the peak drawn by the latency-optimal schedule),
        // which is a per-scenario quantity — so they are derived inside the scenario
        // loop rather than once from the pooled distribution. A tier then states the
        // same physical demand ("run at 80% of what the fastest schedule wants") in
        // every scenario.
        double[][] capsByScenario = null;
        double[] referencePeaks = new double[scenarioCount];
        if (twoPhase) {
            capsByScenario = new double[scenarioCount][];
            java.util.Arrays.fill(referencePeaks, Double.NaN);
            progress.clearLine();
            System.out.printf(java.util.Locale.US,
                "Phase 2: constrained re-run of %d arms under %d derived caps per scenario.%n",
                labels.size(), targets.length);
            for (int s = 0; s < scenarioCount; s++) {
                int scenarioNum = s + 1;
                String scenarioName = infra.scenarioNames[s];
                List<AlgorithmRunResult> scenarioRuns = perScenarioRuns.get(s);

                double referencePeak = PowerCapCalibrator.referencePeakWatts(scenarioRuns);
                double[] caps = PowerCapCalibrator.capsFromAnchor(referencePeak, targets);
                capsByScenario[s] = caps;
                referencePeaks[s] = referencePeak;
                progress.clearLine();
                logDerivedCaps(scenarioNum, scenarioName, referencePeak, caps, targets);
                if (caps.length == 0) {
                    // No peaks captured for this scenario: nothing to constrain against.
                    plannedTotal -= labels.size() * infra.numRuns * targets.length;
                    progress.setTotal(plannedTotal);
                    continue;
                }
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
        // Quality indicators are computed on FEASIBLE solutions only. The constrained
        // archive deliberately publishes least-violating solutions when a run found
        // nothing feasible, so scoring the raw front would award hypervolume to a run
        // that produced no admissible schedule at all. Such a run becomes an empty
        // front here, which the analyzer reports as NaN rather than as a good score.
        // The feasibility CSVs below keep the UNFILTERED runs, since feasibility rates
        // are only meaningful against everything an arm actually published.
        progress.clearLine();
        List<ExperimentReporter.ScenarioReport> feasibilityReports = new ArrayList<>();
        for (int s = 0; s < scenarioCount; s++) {
            feasibilityReports.add(new ExperimentReporter.ScenarioReport(
                s + 1, infra.scenarioNames[s], spec.getObjectiveNames(),
                groupByLabel(perScenarioRuns.get(s)),
                new ArrayList<>(), Double.NaN, new LinkedHashMap<>()));
        }

        List<ExperimentReporter.ScenarioReport> reports = new ArrayList<>();
        for (int s = 0; s < scenarioCount; s++) {
            List<AlgorithmRunResult> scenarioRuns = restrictToFeasible(
                perScenarioRuns.get(s), capByLabel(capsByScenario, s, targets));
            perScenarioRuns.set(s, scenarioRuns);
            ParetoAnalyzer.ScenarioAnalysis analysis = ParetoAnalyzer.analyzeScenario(scenarioRuns);
            ExperimentReporter.ScenarioReport report = new ExperimentReporter.ScenarioReport(
                s + 1, infra.scenarioNames[s], spec.getObjectiveNames(), groupByLabel(scenarioRuns),
                analysis.universalFront, analysis.universalHV, analysis.algorithmFronts,
                analysis.seedCollaboration, analysis.universalHvFixed);
            reports.add(report);
            ConsoleReporter.printScenarioSummary(report);
        }

        try {
            ExperimentReporter reporter = new ExperimentReporter();
            Path dir = reporter.writeExperiment(ExperimentReporter.DEFAULT_RESULTS_ROOT, experimentId, reports);
            if (detailsCollector != null) {
                detailsCollector.writeAll(dir, experimentId);
            }
            if (twoPhase) {
                // Feasibility of every arm (uncapped + constrained) against the caps
                // derived for that arm's own scenario.
                Map<Integer, double[]> capsByScenarioNumber = new LinkedHashMap<>();
                for (int s = 0; s < scenarioCount; s++) {
                    if (capsByScenario[s] != null && capsByScenario[s].length > 0) {
                        capsByScenarioNumber.put(s + 1, capsByScenario[s]);
                    }
                }
                PowerCeilingFeasibilityReporter.writeReports(
                    dir.toString(), feasibilityReports, capsByScenarioNumber);
                writeCalibrationManifest(dir, capsByScenario, referencePeaks, targets);

                // Per-cap-tier analysis (additive *_by_cap.csv files): every indicator,
                // universal front and collaboration table recomputed strictly within each
                // tier via analyzed copies — the global CSVs above are untouched.
                for (int s = 0; s < scenarioCount; s++) {
                    double[] caps = capsByScenario[s];
                    if (caps == null || caps.length == 0) {
                        continue;
                    }
                    Map<String, Double> capWattsByTier = new LinkedHashMap<>();
                    for (int c = 0; c < caps.length; c++) {
                        capWattsByTier.put(
                            "PC" + String.format(java.util.Locale.US, "%.0f", targets[c]), caps[c]);
                    }
                    List<ParetoAnalyzer.TierAnalysis> tiers =
                        ParetoAnalyzer.analyzeScenarioByTier(perScenarioRuns.get(s));
                    reporter.writeByCapReports(dir, s + 1, spec.getObjectiveNames(), tiers,
                        capWattsByTier);
                }
            }
            ConsoleReporter.printDone(dir);
            if (algorithmLog != null) {
                System.out.println("  Wrote: algorithm_log.txt");
            }
            PostRunScripts.runAll(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write experiment output: " + e.getMessage(), e);
        }
        } finally {
            params.verboseLogging = consoleVerbose;
            if (algorithmLog != null) {
                algorithmLog.close();
                algorithmLog = null;
            }
        }
    }


    /**
     * Label &rarr; cap (Watts) for one scenario's constrained arms. Uncapped arms are
     * absent, so {@link #restrictToFeasible} leaves them untouched.
     */
    private Map<String, Double> capByLabel(double[][] capsByScenario, int scenarioIndex, double[] targets) {
        Map<String, Double> byLabel = new LinkedHashMap<>();
        if (capsByScenario == null || capsByScenario[scenarioIndex] == null) {
            return byLabel;
        }
        double[] caps = capsByScenario[scenarioIndex];
        for (int c = 0; c < caps.length && c < targets.length; c++) {
            String tier = "_PC" + String.format(java.util.Locale.US, "%.0f", targets[c]);
            for (String label : labels) {
                byLabel.put(label + tier, caps[c]);
            }
        }
        return byLabel;
    }

    /**
     * Copies of the given runs keeping only cap-feasible solutions. A run whose label
     * carries no cap is returned unchanged; a constrained run that found nothing
     * feasible becomes an empty front, which is how a zero-feasible run is recorded
     * (the analyzer yields NaN indicators for it rather than scoring infeasible points).
     */
    private List<AlgorithmRunResult> restrictToFeasible(List<AlgorithmRunResult> runs,
                                                        Map<String, Double> capByLabel) {
        if (capByLabel.isEmpty()) {
            return runs;
        }
        List<AlgorithmRunResult> out = new ArrayList<>(runs.size());
        for (AlgorithmRunResult run : runs) {
            Double cap = capByLabel.get(run.getLabel());
            List<Double> peaks = run.getAuxPeakPowerWatts();
            if (cap == null || peaks == null) {
                out.add(run);
                continue;
            }
            List<double[]> front = run.getFront();
            List<double[]> keptFront = new ArrayList<>();
            List<Double> keptPeaks = new ArrayList<>();
            int n = Math.min(front.size(), peaks.size());
            for (int i = 0; i < n; i++) {
                Double peak = peaks.get(i);
                if (peak != null && peak <= cap) {
                    keptFront.add(front.get(i));
                    keptPeaks.add(peak);
                }
            }
            if (keptFront.size() == front.size()) {
                out.add(run);
                continue;
            }
            out.add(new AlgorithmRunResult(run.getLabel(), run.getScenarioNumber(),
                run.getScenarioName(), run.getSeed(), run.getObjectiveNames(),
                keptFront, keptPeaks, run.getRuntimeMs()));
        }
        return out;
    }


    /**
     * Records how this campaign's cap tiers were derived, as
     * {@code power_cap_calibration.csv}.
     *
     * <p>A tier name alone is ambiguous across campaigns: {@code PC90} meant "a cap
     * admitting ~90% of observed peaks" under the percentile scheme and means "90% of
     * P_ref" under the anchored one. Without this file two result folders can be
     * compared under identical tier names that denote different treatments, so the
     * scheme, the reference peak and the resulting Watts are persisted next to the
     * results rather than left to the console log.</p>
     */
    private void writeCalibrationManifest(Path dir, double[][] capsByScenario,
                                          double[] referencePeaks, double[] targets) {
        Path file = dir.resolve("power_cap_calibration.csv");
        try (java.io.PrintWriter w = new java.io.PrintWriter(
                java.nio.file.Files.newBufferedWriter(file))) {
            w.println("Scheme,Scenario,ScenarioName,Tier,AnchorPercentOfPref,"
                + "ReferencePeakWatts,CapWatts");
            for (int s = 0; s < capsByScenario.length; s++) {
                double[] caps = capsByScenario[s];
                if (caps == null || caps.length == 0) {
                    continue;
                }
                for (int c = 0; c < caps.length && c < targets.length; c++) {
                    w.printf(java.util.Locale.US, "%s,%d,%s,PC%.0f,%.1f,%.3f,%.3f%n",
                        CAP_SCHEME_ID, s + 1, infra.scenarioNames[s], targets[c],
                        targets[c], referencePeaks[s], caps[c]);
                }
            }
            System.out.println("  Wrote: power_cap_calibration.csv");
        } catch (IOException e) {
            System.err.println("  ERROR writing power_cap_calibration.csv: " + e.getMessage());
        }
    }


    /** Writes every byte to two streams, so verbose output can reach console and file. */
    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        TeeOutputStream(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int byteValue) throws IOException {
            a.write(byteValue);
            b.write(byteValue);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {
            a.write(buf, off, len);
            b.write(buf, off, len);
        }

        @Override
        public void flush() throws IOException {
            a.flush();
            b.flush();
        }
    }

    /** Prints one scenario's P_ref-anchored power-cap tiers. */
    private void logDerivedCaps(int scenarioNum, String scenarioName,
                                double referencePeakWatts, double[] caps, double[] targets) {
        if (caps.length == 0) {
            System.out.printf(java.util.Locale.US,
                "Scenario %d (%s): no coincident peaks captured - skipping constrained arms.%n",
                scenarioNum, scenarioName);
            return;
        }
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.US,
            "Scenario %d (%s): P_ref = %.3f kW (latency-optimal schedule); derived tiers:",
            scenarioNum, scenarioName, referencePeakWatts / 1000.0));
        for (int i = 0; i < caps.length; i++) {
            sb.append(String.format(java.util.Locale.US, "%n    PC%.0f = %.0f%% of P_ref -> %.3f kW",
                targets[i], targets[i], caps[i] / 1000.0));
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
        PrintStream saved = System.out;
        PrintStream target = null;
        if (algorithmLog != null) {
            algorithmLog.printf(java.util.Locale.US,
                "%n===== scenario=%d (%s) algorithm=%s seed=%d =====%n",
                scenarioNum, scenarioName, label, seed);
            // Also echo to the console when the caller asked for verbose output.
            target = consoleVerbose ? new PrintStream(new TeeOutputStream(saved, algorithmLog), true)
                                    : algorithmLog;
        } else if (!consoleVerbose) {
            target = SINK;
        }
        if (target != null) {
            System.setOut(target);
        }
        try {
            return doRunOne(label, factory, baseConfig, scenarioNum, scenarioName, seed);
        } finally {
            if (target != null) {
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
            if (detailsCollector != null) {
                detailsCollector.beginRun(scenarioNum, scenarioName, label, seed);
            }
            front = simulateAllParetoSolutions(strategy, context, peaks);
            if (detailsCollector != null) {
                detailsCollector.endRun();
            }
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

            Map<Task, VM> assignment = moStrategy.applySolution(
                solution, context.getTasks(), runningVMs, context.getCurrentTime());

            new VMExecutionStep().execute(context);
            TaskExecutionStep taskExec = new TaskExecutionStep();
            taskExec.execute(context);
            EnergyCalculationStep energyCalc = new EnergyCalculationStep();
            energyCalc.execute(context);

            double primaryValue = primary.extract(taskExec);
            double energyKWh = energyCalc.getTotalITEnergyKWh();
            simulatedResults.add(new double[] {primaryValue, energyKWh});
            if (peaksOut != null) {
                peaksOut.add(energyCalc.getPeakTotalPowerWatts());
            }
            if (detailsCollector != null) {
                detailsCollector.record(context, runningVMs, solution, assignment,
                    taskExec, energyCalc, primaryValue, energyKWh);
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
