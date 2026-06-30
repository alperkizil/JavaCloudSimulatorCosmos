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

    /** Runs the campaign, writing into {@code results/<experimentId>/} (fixed id). */
    public Path run(String experimentId) {
        AlgorithmRegistry registry = new AlgorithmRegistry(params, primary);

        ConsoleReporter.printBanner(spec, primary, infra, labels, experimentId);

        List<ExperimentReporter.ScenarioReport> reports = new ArrayList<>();
        for (int s = 0; s < infra.scenarioCount(); s++) {
            int scenarioNum = s + 1;
            String scenarioName = infra.scenarioNames[s];
            ConsoleReporter.printScenarioHeader(scenarioNum, scenarioName, infra);

            List<AlgorithmRunResult> scenarioRuns = new ArrayList<>();
            for (String label : labels) {
                for (int run = 0; run < infra.numRuns; run++) {
                    long seed = infra.baseSeed + run;
                    ExperimentConfiguration config = infra.toExperimentConfiguration(scenarioNum, seed);
                    scenarioRuns.add(runOne(registry, label, config, scenarioNum, scenarioName, seed));
                }
            }

            ParetoAnalyzer.ScenarioAnalysis analysis = ParetoAnalyzer.analyzeScenario(scenarioRuns);
            ExperimentReporter.ScenarioReport report = new ExperimentReporter.ScenarioReport(
                scenarioNum, scenarioName, spec.getObjectiveNames(), groupByLabel(scenarioRuns),
                analysis.universalFront, analysis.universalHV, analysis.algorithmFronts);
            reports.add(report);
            ConsoleReporter.printScenarioSummary(report);
        }

        try {
            Path dir = new ExperimentReporter()
                .writeExperiment(ExperimentReporter.DEFAULT_RESULTS_ROOT, experimentId, reports);
            ConsoleReporter.printDone(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write experiment output: " + e.getMessage(), e);
        }
    }

    /**
     * Runs one (algorithm, seed) on one scenario through Steps 1-8 and returns the
     * captured front. Mirrors {@code runAlgorithm} exactly.
     */
    AlgorithmRunResult runOne(AlgorithmRegistry registry, String label,
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
        TaskAssignmentStrategy strategy = registry.create(label, context, seed);

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

        List<double[]> front;
        if (strategy instanceof MultiObjectiveTaskSchedulingStrategy) {
            front = simulateAllParetoSolutions(strategy, context);
            if (front.isEmpty()) {
                front = new ArrayList<>();
                front.add(new double[] {selectedPrimary, selectedEnergy});
            }
        } else {
            front = new ArrayList<>();
            front.add(new double[] {selectedPrimary, selectedEnergy});
        }

        long runtimeMs = System.currentTimeMillis() - startTime;
        return new AlgorithmRunResult(label, scenarioNum, scenarioName, seed,
            spec.getObjectiveNames(), front, null, runtimeMs);
    }

    /** Re-simulates every Pareto-front solution through Steps 6-8 (mirrors the runner). */
    private List<double[]> simulateAllParetoSolutions(TaskAssignmentStrategy strategy, SimulationContext context) {
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
