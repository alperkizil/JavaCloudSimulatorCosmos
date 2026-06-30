package com.cloudsimulator.observer;

import com.cloudsimulator.engine.MultiObjectiveSimulationResult;
import com.cloudsimulator.engine.MultiObjectiveSimulationResult.SolutionSimulationResult;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationEngine;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.model.SimulationSummary;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.MultiObjectiveTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.steps.TaskAssignmentStep;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tests for the ExperimentObserver subsystem:
 *   1. ParetoAnalyzer indicators on a known 2-point front (hand-checked).
 *   2. ExperimentObserver capture from a synthetic MultiObjectiveSimulationResult.
 *   3. Engine-hook integration: a real SimulationEngine drives runMultiObjective()
 *      and run() through a minimal pipeline, and the observer captures the fronts.
 *
 * Main-method test (no JUnit on the classpath); exits non-zero on failure.
 */
public class ExperimentObserverTest {

    private static final double TOL = 1e-6;
    private static int failures = 0;

    public static void main(String[] args) {
        System.out.println("=== ExperimentObserver Subsystem Test ===\n");

        testKnownFrontIndicators();
        testObserverCaptureFromMOResult();
        testEngineHookMultiObjective();
        testEngineHookSingleRun();
        testAlgorithmAggregateFront();
        testReporterWritesAlgorithmFronts();
        testExperimentNaming();
        testWriteExperimentFolder();

        System.out.println();
        if (failures == 0) {
            System.out.println("=== All ExperimentObserver tests PASSED ===");
        } else {
            System.out.println("=== ExperimentObserver tests FAILED: " + failures + " assertion(s) ===");
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // 1. ParetoAnalyzer on a known front (hand-checked)
    // -------------------------------------------------------------------------

    private static void testKnownFrontIndicators() {
        System.out.println("[1] ParetoAnalyzer indicators on a known front");

        // universal = {(1,4),(2,2),(4,1)}; run = {(2,2),(4,1)} (subset, missing (1,4)).
        List<double[]> universal = Arrays.asList(
            new double[] {1, 4}, new double[] {2, 2}, new double[] {4, 1});
        List<double[]> run = Arrays.asList(new double[] {2, 2}, new double[] {4, 1});

        // Union bounds over all points (incl. universal): ideal (1,1), nadir (4,4).
        List<double[]> all = new ArrayList<>();
        all.addAll(run);
        all.addAll(universal);
        double[] bounds = ParetoAnalyzer.unionBounds(all);
        assertClose("bounds.idealX", 1.0, bounds[0]);
        assertClose("bounds.idealY", 1.0, bounds[1]);
        assertClose("bounds.nadirX", 4.0, bounds[2]);
        assertClose("bounds.nadirY", 4.0, bounds[3]);

        ParetoAnalyzer.FixedIndicators fixed = ParetoAnalyzer.computeFixedIndicators(run, universal, bounds);
        // HV_fixed = (529/900 + 1/30) / 1.21 = (559/900)/1.21 = 559/1089.
        assertClose("HV_fixed", 559.0 / 1089.0, fixed.hvFixed);
        // eps+ = 1/3 (distance from universal's (1,4) to the run).
        assertClose("EpsPlus", 1.0 / 3.0, fixed.epsilonPlus);
        // contribution = 2 of 3 universal points matched = 66.666...%
        assertClose("ParetoContribution_pct", 200.0 / 3.0, fixed.contributionPct);
        assertEqualsInt("nSolutions", 2, fixed.nSolutions);

        List<double[]> nonDom = ParetoAnalyzer.filterToNonDominated(run);
        ParetoAnalyzer.LegacyIndicators legacy = ParetoAnalyzer.computeLegacyIndicators(nonDom, universal);
        // Legacy HV with reference (1,1) in PerformanceMetrics' own normalized frame.
        assertClose("legacy HV", 4.0 / 9.0, legacy.hv);
        assertClose("legacy GD", 0.0, legacy.gd);
        assertClose("legacy IGD", Math.sqrt(5.0) / 9.0, legacy.igd);
        assertClose("legacy Spacing", 0.0, legacy.spacing);

        // Contribution count (distinct universal points matched) = 2.
        assertEqualsInt("contribution count", 2, ParetoAnalyzer.paretoContributionCount(run, universal));
    }

    // -------------------------------------------------------------------------
    // 2. Observer capture from a synthetic MO result
    // -------------------------------------------------------------------------

    private static void testObserverCaptureFromMOResult() {
        System.out.println("[2] Observer capture from a synthetic MultiObjectiveSimulationResult");

        double[] waiting = {4.84, 4.20};
        double[] energy = {0.537162202, 0.517524118};
        double[] peak = {180_000.0, 200_000.0};

        ParetoFront pf = new ParetoFront(
            Arrays.asList("WaitingTime", "Energy"), new boolean[] {true, true});
        MultiObjectiveSimulationResult mo = new MultiObjectiveSimulationResult(
            pf, Arrays.asList("WaitingTime", "Energy"));

        for (int i = 0; i < waiting.length; i++) {
            SimulationSummary summary = new SimulationSummary();
            summary.getPerformance().avgWaitingTimeSeconds = waiting[i];
            summary.getEnergy().totalITEnergyKWh = energy[i];
            summary.getEnergy().peakPowerWatts = peak[i];
            mo.addSolutionResult(new SolutionSimulationResult(
                i, null, new double[] {waiting[i], energy[i]}, summary, 10L));
        }

        ExperimentObserver observer = new ExperimentObserver(ExperimentSpec.powerCeiling());
        observer.beginRun("FAKE", 1, "Synthetic", 42L);
        observer.onMultiObjectiveComplete(mo);

        List<AlgorithmRunResult> results = observer.getResults();
        assertEqualsInt("captured run count", 1, results.size());
        AlgorithmRunResult run = results.get(0);
        assertEquals("label", "FAKE", run.getLabel());
        assertEqualsInt("scenario number", 1, run.getScenarioNumber());
        assertEqualsLong("seed", 42L, run.getSeed());
        assertEqualsInt("front size", 2, run.getFront().size());

        for (int i = 0; i < waiting.length; i++) {
            double[] pt = run.getFront().get(i);
            assertClose("front[" + i + "].waiting", waiting[i], pt[0]);
            assertClose("front[" + i + "].energy", energy[i], pt[1]);
        }
        List<Double> aux = run.getAuxPeakPowerWatts();
        assertTrue("aux peaks present", aux != null && aux.size() == 2);
        if (aux != null && aux.size() == 2) {
            assertClose("aux peak[0]", 180_000.0, aux.get(0));
            assertClose("aux peak[1]", 200_000.0, aux.get(1));
        }
    }

    // -------------------------------------------------------------------------
    // 3a. Engine hook: runMultiObjective()
    // -------------------------------------------------------------------------

    private static void testEngineHookMultiObjective() {
        System.out.println("[3a] Engine hook fires on runMultiObjective()");

        double[] waiting = {3.0, 5.0};
        double[] energy = {0.4, 0.6};

        SimulationEngine engine = new SimulationEngine();
        engine.setDebugEnabled(false);
        engine.addStep(new TaskAssignmentStep(new FakeMOStrategy(2)));
        engine.addStep(new SummaryStep(waiting, energy));

        ExperimentObserver observer = new ExperimentObserver(ExperimentSpec.waitingTime());
        engine.addListener(observer);
        observer.beginRun("FAKE_MO", 1, "Balanced", 7L);
        engine.runMultiObjective();

        List<AlgorithmRunResult> results = observer.getResults();
        assertEqualsInt("MO captured run count", 1, results.size());
        AlgorithmRunResult run = results.get(0);
        assertEqualsInt("MO front size", 2, run.getFront().size());
        for (int i = 0; i < waiting.length; i++) {
            double[] pt = run.getFront().get(i);
            assertClose("MO front[" + i + "].waiting", waiting[i], pt[0]);
            assertClose("MO front[" + i + "].energy", energy[i], pt[1]);
        }
    }

    // -------------------------------------------------------------------------
    // 3b. Engine hook: run()
    // -------------------------------------------------------------------------

    private static void testEngineHookSingleRun() {
        System.out.println("[3b] Engine hook fires on run() (single-point front)");

        SimulationEngine engine = new SimulationEngine();
        engine.setDebugEnabled(false);
        engine.addStep(new SummaryStep(new double[] {7.0}, new double[] {0.9}));

        ExperimentObserver observer = new ExperimentObserver(ExperimentSpec.waitingTime());
        engine.addListener(observer);
        observer.beginRun("BASELINE", 1, "Balanced", 1L);
        engine.run();

        List<AlgorithmRunResult> results = observer.getResults();
        assertEqualsInt("run() captured count", 1, results.size());
        AlgorithmRunResult run = results.get(0);
        assertEqualsInt("run() front size", 1, run.getFront().size());
        assertClose("run() waiting", 7.0, run.getFront().get(0)[0]);
        assertClose("run() energy", 0.9, run.getFront().get(0)[1]);
    }

    // -------------------------------------------------------------------------
    // 4. Per-algorithm aggregate front (union over an algorithm's seeds)
    // -------------------------------------------------------------------------

    private static void testAlgorithmAggregateFront() {
        System.out.println("[4] Per-algorithm aggregate Pareto front");

        // NSGA-II over two seeds; pooled points include dominated ones (5,5),(3,3).
        AlgorithmRunResult nsga100 = makeRun("NSGA-II", 100L,
            new double[][] {{2, 2}, {4, 1}, {5, 5}});
        AlgorithmRunResult nsga101 = makeRun("NSGA-II", 101L,
            new double[][] {{1, 4}, {3, 3}});
        AlgorithmRunResult spea = makeRun("SPEA-II", 100L,
            new double[][] {{0, 9}, {9, 0}});

        // NSGA-II aggregate front = non-dominated of all its points = (1,4),(2,2),(4,1).
        List<double[]> nsgaFront = ParetoAnalyzer.computeAlgorithmFront(Arrays.asList(nsga100, nsga101));
        assertFrontEquals("NSGA-II aggregate front",
            new double[][] {{1, 4}, {2, 2}, {4, 1}}, nsgaFront);

        // analyzeScenario should expose per-algorithm fronts for every label.
        List<AlgorithmRunResult> scenario = Arrays.asList(nsga100, nsga101, spea);
        ParetoAnalyzer.ScenarioAnalysis analysis = ParetoAnalyzer.analyzeScenario(scenario);
        assertTrue("algorithmFronts has NSGA-II", analysis.algorithmFronts.containsKey("NSGA-II"));
        assertTrue("algorithmFronts has SPEA-II", analysis.algorithmFronts.containsKey("SPEA-II"));
        assertFrontEquals("analysis NSGA-II front",
            new double[][] {{1, 4}, {2, 2}, {4, 1}}, analysis.algorithmFronts.get("NSGA-II"));
        assertFrontEquals("analysis SPEA-II front",
            new double[][] {{0, 9}, {9, 0}}, analysis.algorithmFronts.get("SPEA-II"));
        // Universal front spans all algorithms.
        assertFrontEquals("universal front",
            new double[][] {{0, 9}, {1, 4}, {2, 2}, {4, 1}, {9, 0}}, analysis.universalFront);
    }

    // -------------------------------------------------------------------------
    // 5. Reporter writes the additive per-algorithm CSV
    // -------------------------------------------------------------------------

    private static void testReporterWritesAlgorithmFronts() {
        System.out.println("[5] ExperimentReporter writes scenario_N_algorithm_pareto_fronts.csv");

        AlgorithmRunResult nsga = makeRun("NSGA-II", 100L, new double[][] {{1, 4}, {2, 2}, {4, 1}});
        AlgorithmRunResult spea = makeRun("SPEA-II", 100L, new double[][] {{0, 9}, {9, 0}});
        List<AlgorithmRunResult> scenario = Arrays.asList(nsga, spea);
        ParetoAnalyzer.ScenarioAnalysis analysis = ParetoAnalyzer.analyzeScenario(scenario);

        Map<String, List<AlgorithmRunResult>> byLabel = new LinkedHashMap<>();
        for (AlgorithmRunResult r : scenario) {
            byLabel.computeIfAbsent(r.getLabel(), k -> new ArrayList<>()).add(r);
        }
        ExperimentReporter.ScenarioReport report = new ExperimentReporter.ScenarioReport(
            1, "Balanced", Arrays.asList("Makespan", "Energy"),
            byLabel, analysis.universalFront, analysis.universalHV, analysis.algorithmFronts);

        try {
            Path tmp = Files.createTempDirectory("obs-report-test");
            new ExperimentReporter().writeAll(tmp.toString(), Arrays.asList(report));
            Path csv = tmp.resolve("scenario_1_algorithm_pareto_fronts.csv");
            assertTrue("algorithm fronts CSV exists", Files.exists(csv));

            List<String> lines = Files.readAllLines(csv);
            assertEquals("header", "Algorithm,Makespan,Energy", lines.get(0));
            // NSGA-II: 3 rows, SPEA-II: 2 rows => 5 data rows.
            assertEqualsInt("data row count", 5, lines.size() - 1);
            assertEquals("first data row", "NSGA-II,1.000000,4.000000000", lines.get(1));
            assertEquals("first SPEA-II row", "SPEA-II,0.000000,9.000000000", lines.get(4));
        } catch (Exception e) {
            System.out.println("  FAIL: reporter threw " + e);
            failures++;
        }
    }

    // -------------------------------------------------------------------------
    // 6. Experiment naming + id resolution
    // -------------------------------------------------------------------------

    private static void testExperimentNaming() {
        System.out.println("[6] Experiment naming + id resolution");

        LocalDateTime when = LocalDateTime.of(2026, 1, 2, 13, 5, 9);
        String ts = "02_01_2026_13_05_09"; // dd_MM_yyyy_HH_mm_ss

        // Named experiment -> "<name>_<timestamp>".
        ExperimentSpec named = ExperimentSpec.waitingTime("paper-run");
        assertEquals("named getName", "paper-run", named.getName());
        assertTrue("named hasName", named.hasName());
        assertEquals("named buildExperimentId", "paper-run_" + ts,
            named.buildExperimentId(ts, "exp-IGNORED"));
        assertEquals("named resolveExperimentId", "paper-run_" + ts,
            named.resolveExperimentId(when));

        // Unnamed experiment -> "<generatedId>_<timestamp>".
        ExperimentSpec unnamed = ExperimentSpec.waitingTime();
        assertTrue("unnamed has no name", !unnamed.hasName() && unnamed.getName() == null);
        assertEquals("unnamed buildExperimentId", "exp-abcd1234_" + ts,
            unnamed.buildExperimentId(ts, "exp-abcd1234"));
        String resolved = unnamed.resolveExperimentId(when);
        assertTrue("unnamed resolveExperimentId pattern",
            resolved.startsWith("exp-") && resolved.endsWith("_" + ts));

        // withName preserves objectives.
        ExperimentSpec renamed = ExperimentSpec.waitingTime().withName("x");
        assertEquals("withName getName", "x", renamed.getName());
        assertEquals("withName objectives", "[WaitingTime, Energy]", renamed.getObjectiveNames().toString());

        // Blank name is treated as unnamed.
        ExperimentSpec blank = ExperimentSpec.scenarioComparison("   ");
        assertTrue("blank treated as unnamed", !blank.hasName());
        assertTrue("blank falls back to generated id",
            blank.resolveExperimentId(when).startsWith("exp-"));
    }

    // -------------------------------------------------------------------------
    // 7. writeExperiment creates <root>/<experimentId>/ and writes results there
    // -------------------------------------------------------------------------

    private static void testWriteExperimentFolder() {
        System.out.println("[7] ExperimentReporter.writeExperiment creates <root>/<id>/ folder");

        AlgorithmRunResult nsga = makeRun("NSGA-II", 100L, new double[][] {{1, 4}, {2, 2}, {4, 1}});
        List<AlgorithmRunResult> scenario = Arrays.asList(nsga);
        ParetoAnalyzer.ScenarioAnalysis analysis = ParetoAnalyzer.analyzeScenario(scenario);
        Map<String, List<AlgorithmRunResult>> byLabel = new LinkedHashMap<>();
        byLabel.put("NSGA-II", scenario);
        ExperimentReporter.ScenarioReport report = new ExperimentReporter.ScenarioReport(
            1, "Balanced", Arrays.asList("Makespan", "Energy"),
            byLabel, analysis.universalFront, analysis.universalHV, analysis.algorithmFronts);

        try {
            Path tmpRoot = Files.createTempDirectory("obs-results");
            String expId = "paper-run_02_01_2026_13_05_09"; // explicit id => deterministic
            Path expDir = new ExperimentReporter()
                .writeExperiment(tmpRoot.toString(), expId, Arrays.asList(report));

            assertTrue("experiment folder created", Files.isDirectory(expDir));
            assertEquals("folder named by experiment id", expId, expDir.getFileName().toString());
            assertTrue("folder nested under results root", expDir.getParent().equals(tmpRoot));
            assertTrue("graph CSV in folder",
                Files.exists(expDir.resolve("scenario_1_pareto_graph_data.csv")));
            assertTrue("algorithm-fronts CSV in folder",
                Files.exists(expDir.resolve("scenario_1_algorithm_pareto_fronts.csv")));
            assertTrue("performance-metrics CSV in folder",
                Files.exists(expDir.resolve("scenario_1_performance_metrics.csv")));
            assertTrue("experiment summary in folder",
                Files.exists(expDir.resolve("experiment_summary.csv")));
            assertTrue("plot options in folder",
                Files.exists(expDir.resolve("plot_options.json")));
        } catch (Exception e) {
            System.out.println("  FAIL: writeExperiment threw " + e);
            failures++;
        }
    }

    private static AlgorithmRunResult makeRun(String label, long seed, double[][] points) {
        List<double[]> front = new ArrayList<>();
        for (double[] p : points) {
            front.add(p);
        }
        return new AlgorithmRunResult(label, 1, "Balanced", seed,
            Arrays.asList("Makespan", "Energy"), front, null, 100L);
    }

    private static void assertFrontEquals(String name, double[][] expected, List<double[]> actual) {
        if (actual.size() != expected.length) {
            System.out.println("  FAIL: " + name + " size expected=" + expected.length
                + " actual=" + actual.size());
            failures++;
            return;
        }
        for (int i = 0; i < expected.length; i++) {
            if (Math.abs(expected[i][0] - actual.get(i)[0]) > TOL
                || Math.abs(expected[i][1] - actual.get(i)[1]) > TOL) {
                System.out.println("  FAIL: " + name + "[" + i + "] expected=("
                    + expected[i][0] + "," + expected[i][1] + ") actual=("
                    + actual.get(i)[0] + "," + actual.get(i)[1] + ")");
                failures++;
                return;
            }
        }
        System.out.println("  ok:   " + name + " (" + actual.size() + " points)");
    }

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    /** A SimulationStep that stamps a known summary per invocation. */
    private static final class SummaryStep implements SimulationStep {
        private final double[] waiting;
        private final double[] energy;
        private int index = 0;

        SummaryStep(double[] waiting, double[] energy) {
            this.waiting = waiting;
            this.energy = energy;
        }

        @Override
        public void execute(SimulationContext context) {
            SimulationSummary summary = new SimulationSummary();
            int i = Math.min(index, waiting.length - 1);
            summary.getPerformance().avgWaitingTimeSeconds = waiting[i];
            summary.getEnergy().totalITEnergyKWh = energy[i];
            context.setSimulationSummary(summary);
            index++;
        }

        @Override
        public String getStepName() {
            return "SummaryStep";
        }
    }

    /** Minimal multi-objective strategy that returns a fixed Pareto front. */
    private static final class FakeMOStrategy implements MultiObjectiveTaskSchedulingStrategy {
        private final ParetoFront front;

        FakeMOStrategy(int numSolutions) {
            List<SchedulingSolution> solutions = new ArrayList<>();
            for (int i = 0; i < numSolutions; i++) {
                SchedulingSolution s = new SchedulingSolution(1, 1, 2);
                s.setObjectiveValues(new double[] {i + 1.0, i + 2.0});
                solutions.add(s);
            }
            this.front = new ParetoFront(solutions,
                Arrays.asList("WaitingTime", "Energy"), new boolean[] {true, true});
        }

        @Override
        public ParetoFront optimizeAndGetParetoFront(List<Task> tasks, List<VM> vms) {
            return front;
        }

        @Override
        public ParetoFront getLastParetoFront() {
            return front;
        }

        @Override
        public Map<Task, VM> applySolution(SchedulingSolution solution, List<Task> tasks,
                                           List<VM> vms, long currentTime) {
            return new LinkedHashMap<>();
        }

        @Override
        public List<String> getObjectiveNames() {
            return Arrays.asList("WaitingTime", "Energy");
        }

        @Override
        public boolean[] getObjectiveMinimization() {
            return new boolean[] {true, true};
        }

        @Override
        public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
            return Optional.empty();
        }

        @Override
        public String getStrategyName() {
            return "FakeMOStrategy";
        }

        @Override
        public String getDescription() {
            return "Test double returning a fixed Pareto front.";
        }
    }

    // -------------------------------------------------------------------------
    // Assertions
    // -------------------------------------------------------------------------

    private static void assertClose(String name, double expected, double actual) {
        if (Double.isNaN(expected) ? !Double.isNaN(actual) : Math.abs(expected - actual) > TOL) {
            System.out.println("  FAIL: " + name + " expected=" + expected + " actual=" + actual);
            failures++;
        } else {
            System.out.println("  ok:   " + name + " = " + actual);
        }
    }

    private static void assertEqualsInt(String name, int expected, int actual) {
        if (expected != actual) {
            System.out.println("  FAIL: " + name + " expected=" + expected + " actual=" + actual);
            failures++;
        } else {
            System.out.println("  ok:   " + name + " = " + actual);
        }
    }

    private static void assertEqualsLong(String name, long expected, long actual) {
        if (expected != actual) {
            System.out.println("  FAIL: " + name + " expected=" + expected + " actual=" + actual);
            failures++;
        } else {
            System.out.println("  ok:   " + name + " = " + actual);
        }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            System.out.println("  FAIL: " + name + " expected=" + expected + " actual=" + actual);
            failures++;
        } else {
            System.out.println("  ok:   " + name + " = " + actual);
        }
    }

    private static void assertTrue(String name, boolean condition) {
        if (!condition) {
            System.out.println("  FAIL: " + name);
            failures++;
        } else {
            System.out.println("  ok:   " + name);
        }
    }
}
