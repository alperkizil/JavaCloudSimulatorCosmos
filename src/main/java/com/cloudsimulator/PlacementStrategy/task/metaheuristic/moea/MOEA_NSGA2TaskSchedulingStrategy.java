package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;
import com.cloudsimulator.PlacementStrategy.task.MultiObjectiveTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.TerminationCondition;

import org.moeaframework.Analyzer;
import org.moeaframework.Executor;
import org.moeaframework.Instrumenter;
import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.analysis.collector.Observations;
import org.moeaframework.analysis.plot.Plot;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.NondominatedSortingPopulation;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variation;
import org.moeaframework.core.initialization.InjectedInitialization;
import org.moeaframework.core.operator.CompoundVariation;
import org.moeaframework.core.operator.real.PM;
import org.moeaframework.core.operator.real.SBX;

import javax.swing.JFrame;
import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Task scheduling strategy using MOEA Framework's Executor to run NSGA-II.
 *
 * This strategy uses the MOEA Framework's Executor class for a cleaner, more
 * declarative approach to running evolutionary algorithms. Benefits include:
 * - Fluent API for configuration
 * - Built-in support for progress tracking via Instrumenter
 * - Easy algorithm switching (just change the algorithm name)
 * - Optional multi-threaded evaluation with distributeOnAllCores()
 * - Checkpointing support for long-running optimizations
 *
 * The strategy can optionally use the Analyzer class for computing quality
 * indicators (hypervolume, generational distance, etc.) on the results.
 *
 * Usage:
 * <pre>
 * NSGA2Configuration config = NSGA2Configuration.builder()
 *     .populationSize(100)
 *     .addObjective(new MakespanObjective())
 *     .addObjective(new EnergyObjective())
 *     .terminationCondition(new GenerationCountTermination(200))
 *     .build();
 *
 * MOEA_NSGA2TaskSchedulingStrategy strategy = new MOEA_NSGA2TaskSchedulingStrategy(config);
 * Map&lt;Task, VM&gt; assignments = strategy.assignAll(tasks, vms, currentTime);
 * </pre>
 *
 * @see Executor
 * @see Analyzer
 */
public class MOEA_NSGA2TaskSchedulingStrategy implements MultiObjectiveTaskSchedulingStrategy {

    /**
     * Selection method for choosing a single solution from the Pareto front.
     */
    public enum SolutionSelectionMethod {
        KNEE_POINT,       // Use knee point (balanced trade-off)
        BEST_MAKESPAN,    // Best makespan (objective 0)
        BEST_ENERGY,      // Best energy (objective 1)
        WEIGHTED_SUM,     // Weighted sum with specified weights
        FIRST             // First solution in front
    }

    private final NSGA2Configuration config;
    private SolutionSelectionMethod selectionMethod;
    private double[] selectionWeights;
    private boolean useDistributedEvaluation;
    private boolean collectRuntimeData;

    // Cached results
    private ParetoFront lastParetoFront;
    private NondominatedPopulation lastMoeaResult;
    private SchedulingSolution selectedSolution;
    private int lastEvaluationCount;
    private Observations lastObservations;

    /**
     * Creates a MOEA Framework NSGA-II task scheduling strategy using Executor.
     *
     * @param config NSGA-II configuration
     */
    public MOEA_NSGA2TaskSchedulingStrategy(NSGA2Configuration config) {
        this.config = config;
        this.selectionMethod = SolutionSelectionMethod.KNEE_POINT;
        this.selectionWeights = new double[]{0.5, 0.5};
        this.useDistributedEvaluation = false;
        this.collectRuntimeData = false;
    }

    /**
     * Sets the method for selecting a single solution from the Pareto front.
     */
    public MOEA_NSGA2TaskSchedulingStrategy setSelectionMethod(SolutionSelectionMethod method) {
        this.selectionMethod = method;
        return this;
    }

    /**
     * Sets weights for weighted sum selection.
     */
    public MOEA_NSGA2TaskSchedulingStrategy setSelectionWeights(double[] weights) {
        this.selectionWeights = weights.clone();
        return this;
    }

    /**
     * Enables distributed evaluation across all CPU cores.
     * Useful for expensive objective function evaluations.
     */
    public MOEA_NSGA2TaskSchedulingStrategy enableDistributedEvaluation() {
        this.useDistributedEvaluation = true;
        return this;
    }

    /**
     * Enables collection of runtime data via Instrumenter.
     * This allows tracking convergence, hypervolume over time, etc.
     */
    public MOEA_NSGA2TaskSchedulingStrategy enableRuntimeDataCollection() {
        this.collectRuntimeData = true;
        return this;
    }

    /**
     * Runs MOEA Framework's NSGA-II using the Executor and returns the Pareto front.
     *
     * @param tasks Tasks to schedule
     * @param vms   Available VMs
     * @return Pareto front of non-dominated solutions
     */
    public ParetoFront optimize(List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return new ParetoFront(config.getObjectiveNames(), config.getMinimizationArray());
        }

        // Propagate seed for reproducibility
        propagateSeed();

        // Create repair operator for feasibility checking
        RepairOperator repairOperator = new RepairOperator(tasks, vms, PRNG.getRandom());

        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[MOEA-NSGA-II] Problem is infeasible: some tasks have no valid VMs");
            return new ParetoFront(config.getObjectiveNames(), config.getMinimizationArray());
        }

        // Create MOEA Framework problem adapter
        TaskSchedulingProblem problem = new TaskSchedulingProblem(
            tasks, vms, config.getObjectives(), repairOperator
        );

        // Calculate max evaluations
        int maxEvaluations = calculateMaxEvaluations();

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-NSGA-II] Starting optimization with Executor");
            System.out.println("[MOEA-NSGA-II] Algorithm: NSGAII, Evaluations: " + maxEvaluations);
            System.out.println("[MOEA-NSGA-II] Population: " + config.getPopulationSize() +
                ", Crossover: " + config.getCrossoverRate() +
                ", Mutation: " + config.getMutationRate());
        }

        // Seeded path: bypass the Executor so we can pass InjectedInitialization.
        if (!config.getSeedAssignments().isEmpty()) {
            return runSeeded(tasks, vms, problem, maxEvaluations);
        }

        // Build and configure the Executor
        Executor executor = new Executor()
            .withProblem(problem)
            .withAlgorithm("NSGAII")
            .withMaxEvaluations(maxEvaluations)
            .withProperty("populationSize", config.getPopulationSize())
            .withProperty("sbx.rate", config.getCrossoverRate())
            .withProperty("sbx.distributionIndex", 5.0)
            .withProperty("pm.rate", config.getMutationRate())
            .withProperty("pm.distributionIndex", 5.0);

        // Optional: distribute evaluation across cores
        if (useDistributedEvaluation) {
            executor.distributeOnAllCores();
        }

        // Optional: collect runtime data
        Instrumenter instrumenter = null;
        if (collectRuntimeData) {
            instrumenter = new Instrumenter()
                .withProblem(problem)
                .withFrequency(config.getPopulationSize())
                .attachElapsedTimeCollector()
                .attachGenerationalDistanceCollector()
                .attachHypervolumeCollector();
            executor.withInstrumenter(instrumenter);
        }

        // Run the optimization
        NondominatedPopulation result = executor.run();
        lastMoeaResult = result;  // Store MOEA result for plotting
        lastEvaluationCount = maxEvaluations;

        // Store runtime observations if collected
        if (instrumenter != null) {
            lastObservations = instrumenter.getObservations();
        }

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-NSGA-II] Optimization completed");
        }

        // Convert to our ParetoFront format
        lastParetoFront = convertToParetoFront(result, problem);

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-NSGA-II] Pareto front contains " + lastParetoFront.size() + " solutions");
        }

        return lastParetoFront;
    }

    /**
     * Runs NSGA-II directly (bypassing the Executor) so that heuristic seed
     * solutions can be injected into the initial population via
     * {@link InjectedInitialization}. The Executor doesn't expose the
     * Initialization hook.
     */
    private ParetoFront runSeeded(List<Task> tasks, List<VM> vms,
                                   TaskSchedulingProblem problem, int maxEvaluations) {
        List<int[]> seeds = config.getSeedAssignments();
        int numTasks = tasks.size();

        // Encode each heuristic seed as a MOEA Solution.
        List<Solution> injected = new ArrayList<>();
        for (int[] seed : seeds) {
            if (seed == null || seed.length != numTasks) continue;
            SchedulingSolution s = new SchedulingSolution(
                numTasks, vms.size(), config.getNumObjectives());
            s.setTaskAssignment(seed.clone());
            s.rebuildTaskOrdering();
            injected.add(problem.encode(s));
        }

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-NSGA-II] Seeded run: injecting "
                + injected.size() + " heuristic solution(s) into initial population");
        }

        // Mirror Executor's default variation operators (SBX + PM) with the
        // rates/distribution indices configured via properties elsewhere.
        Variation variation = new CompoundVariation(
            new SBX(config.getCrossoverRate(), 5.0),
            new PM(config.getMutationRate(), 5.0)
        );

        InjectedInitialization initialization = new InjectedInitialization(problem, injected);

        // selection=null reproduces Deb's original binary tournament without
        // replacement, matching MOEA's default NSGA-II behaviour.
        NSGAII algorithm = new NSGAII(
            problem,
            config.getPopulationSize(),
            new NondominatedSortingPopulation(),
            null,
            null,
            variation,
            initialization
        );

        while (algorithm.getNumberOfEvaluations() < maxEvaluations) {
            algorithm.step();
        }

        NondominatedPopulation result = algorithm.getResult();
        lastMoeaResult = result;
        lastEvaluationCount = algorithm.getNumberOfEvaluations();

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-NSGA-II] Seeded run completed after "
                + lastEvaluationCount + " evaluations");
        }

        lastParetoFront = convertToParetoFront(result, problem);

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-NSGA-II] Pareto front contains "
                + lastParetoFront.size() + " solutions");
        }

        return lastParetoFront;
    }

    /**
     * Runs the algorithm multiple times with different seeds and uses Analyzer
     * to compare results with quality indicators.
     *
     * @param tasks      Tasks to schedule
     * @param vms        Available VMs
     * @param numSeeds   Number of independent runs
     * @return Analyzer results with quality metrics
     */
    public Analyzer.AnalyzerResults runWithAnalyzer(List<Task> tasks, List<VM> vms, int numSeeds) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return null;
        }

        RepairOperator repairOperator = new RepairOperator(tasks, vms, PRNG.getRandom());
        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[MOEA-NSGA-II] Problem is infeasible");
            return null;
        }

        TaskSchedulingProblem problem = new TaskSchedulingProblem(
            tasks, vms, config.getObjectives(), repairOperator
        );

        int maxEvaluations = calculateMaxEvaluations();

        // Configure Executor for multiple seeds
        Executor executor = new Executor()
            .withProblem(problem)
            .withAlgorithm("NSGAII")
            .withMaxEvaluations(maxEvaluations)
            .withProperty("populationSize", config.getPopulationSize())
            .withProperty("sbx.rate", config.getCrossoverRate())
            .withProperty("pm.rate", config.getMutationRate());

        // Run multiple seeds
        List<NondominatedPopulation> results = executor.runSeeds(numSeeds);

        // Analyze results
        Analyzer analyzer = new Analyzer()
            .withProblem(problem)
            .includeHypervolume()
            .includeGenerationalDistance()
            .includeInvertedGenerationalDistance()
            .includeSpacing()
            .showStatisticalSignificance();

        analyzer.addAll("NSGAII", results);

        return analyzer.getAnalysis();
    }

    /**
     * Propagates the seed from our RandomGenerator to MOEA Framework's PRNG.
     */
    private void propagateSeed() {
        try {
            long seed;
            if (config.hasRandomSeed()) {
                seed = config.getRandomSeed();
            } else {
                seed = RandomGenerator.getInstance().getSeed();
            }
            PRNG.setSeed(seed);

            if (config.isVerboseLogging()) {
                System.out.println("[MOEA-NSGA-II] Using seed: " + seed);
            }
        } catch (IllegalStateException e) {
            if (config.hasRandomSeed()) {
                PRNG.setSeed(config.getRandomSeed());
            }
        }
    }

    /**
     * Calculates maximum evaluations from the termination condition.
     */
    private int calculateMaxEvaluations() {
        TerminationCondition termination = config.getTerminationCondition();

        // Direct access to generation count if using GenerationCountTermination
        if (termination instanceof GenerationCountTermination) {
            int generations = ((GenerationCountTermination) termination).getMaxGenerations();
            return generations * config.getPopulationSize();
        }

        // Fallback: try to extract from description for other termination types
        String desc = termination.getDescription();
        if (desc.contains("generation")) {
            // Pattern: "Terminate after X generations"
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(desc);
            if (matcher.find()) {
                try {
                    int generations = Integer.parseInt(matcher.group());
                    return generations * config.getPopulationSize();
                } catch (NumberFormatException e) {
                    // Fall back to default
                }
            }
        }

        return 100 * config.getPopulationSize();
    }

    /**
     * Converts MOEA Framework's NondominatedPopulation to our ParetoFront.
     */
    private ParetoFront convertToParetoFront(NondominatedPopulation population, TaskSchedulingProblem problem) {
        ParetoFront front = new ParetoFront(
            config.getObjectiveNames(),
            config.getMinimizationArray()
        );

        for (Solution solution : population) {
            SchedulingSolution schedulingSolution = problem.decode(solution);

            double[] objectives = new double[config.getNumObjectives()];
            for (int i = 0; i < objectives.length; i++) {
                objectives[i] = solution.getObjective(i);
            }
            schedulingSolution.setObjectiveValues(objectives);
            schedulingSolution.setRank(0);

            front.addSolution(schedulingSolution);
        }

        return front;
    }

    /**
     * Gets the Pareto front from the last optimization run.
     */
    public ParetoFront getLastParetoFront() {
        return lastParetoFront;
    }

    /**
     * Gets the selected solution from the last optimization.
     */
    public SchedulingSolution getSelectedSolution() {
        return selectedSolution;
    }

    /**
     * Gets the number of function evaluations from the last run.
     */
    public int getLastEvaluationCount() {
        return lastEvaluationCount;
    }

    /**
     * Gets the runtime observations from the last run (if collectRuntimeData was enabled).
     */
    public Observations getLastObservations() {
        return lastObservations;
    }

    /**
     * Gets the MOEA Framework's NondominatedPopulation from the last optimization run.
     * This can be used directly with MOEA Framework's Plot class for visualization.
     *
     * @return the last MOEA result, or null if no optimization has been run
     */
    public NondominatedPopulation getLastMoeaResult() {
        return lastMoeaResult;
    }

    /**
     * Displays the last Pareto front in an X-Y scatter plot using MOEA Framework's Plot.
     * For bi-objective problems, automatically uses objective 0 for X and objective 1 for Y.
     *
     * @return the JFrame window displaying the plot, or null if no results available
     */
    public JFrame showParetoFrontPlot() {
        return showParetoFrontPlot("NSGA-II Pareto Front");
    }

    /**
     * Displays the last Pareto front in an X-Y scatter plot with a custom title.
     *
     * @param title the title for the plot window
     * @return the JFrame window displaying the plot, or null if no results available
     */
    public JFrame showParetoFrontPlot(String title) {
        if (lastMoeaResult == null || lastMoeaResult.isEmpty()) {
            System.err.println("[MOEA-NSGA-II] No Pareto front available to plot");
            return null;
        }

        String xLabel = config.getObjectiveNames().size() > 0 ? config.getObjectiveNames().get(0) : "Objective 1";
        String yLabel = config.getObjectiveNames().size() > 1 ? config.getObjectiveNames().get(1) : "Objective 2";

        Plot plot = new Plot()
            .add("NSGA-II", lastMoeaResult)
            .setTitle(title)
            .setXLabel(xLabel)
            .setYLabel(yLabel);

        return plot.show();
    }

    /**
     * Displays the last Pareto front with custom axis selection for many-objective problems.
     *
     * @param title the title for the plot window
     * @param xObjective the objective index for X axis (0-based)
     * @param yObjective the objective index for Y axis (0-based)
     * @return the JFrame window displaying the plot, or null if no results available
     */
    public JFrame showParetoFrontPlot(String title, int xObjective, int yObjective) {
        if (lastMoeaResult == null || lastMoeaResult.isEmpty()) {
            System.err.println("[MOEA-NSGA-II] No Pareto front available to plot");
            return null;
        }

        List<String> names = config.getObjectiveNames();
        String xLabel = xObjective < names.size() ? names.get(xObjective) : "Objective " + (xObjective + 1);
        String yLabel = yObjective < names.size() ? names.get(yObjective) : "Objective " + (yObjective + 1);

        Plot plot = new Plot()
            .add("NSGA-II", lastMoeaResult, xObjective, yObjective)
            .setTitle(title)
            .setXLabel(xLabel)
            .setYLabel(yLabel);

        return plot.show();
    }

    /**
     * Saves the last Pareto front plot to a file (PNG, JPG, or SVG).
     *
     * @param filename the output filename (extension determines format)
     * @throws IOException if the file cannot be written
     */
    public void saveParetoFrontPlot(String filename) throws IOException {
        saveParetoFrontPlot(new File(filename), "NSGA-II Pareto Front");
    }

    /**
     * Saves the last Pareto front plot to a file with a custom title.
     *
     * @param file the output file
     * @param title the title for the plot
     * @throws IOException if the file cannot be written
     */
    public void saveParetoFrontPlot(File file, String title) throws IOException {
        if (lastMoeaResult == null || lastMoeaResult.isEmpty()) {
            throw new IOException("No Pareto front available to save");
        }

        // Create parent directories if they don't exist
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        String xLabel = config.getObjectiveNames().size() > 0 ? config.getObjectiveNames().get(0) : "Objective 1";
        String yLabel = config.getObjectiveNames().size() > 1 ? config.getObjectiveNames().get(1) : "Objective 2";

        new Plot()
            .add("NSGA-II", lastMoeaResult)
            .setTitle(title)
            .setXLabel(xLabel)
            .setYLabel(yLabel)
            .save(file);
    }

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        if (candidateVMs == null || candidateVMs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidateVMs.get(0));
    }

    @Override
    public Map<Task, VM> assignAll(List<Task> tasks, List<VM> vms, long currentTime) {
        Map<Task, VM> assignments = new LinkedHashMap<>();

        if (tasks.isEmpty() || vms.isEmpty()) {
            return assignments;
        }

        List<Task> taskList = new ArrayList<>(tasks);
        List<VM> vmList = new ArrayList<>(vms);

        Map<Integer, Task> taskByIndex = new LinkedHashMap<>();
        Map<Integer, VM> vmByIndex = new LinkedHashMap<>();

        for (int i = 0; i < taskList.size(); i++) {
            taskByIndex.put(i, taskList.get(i));
        }
        for (int i = 0; i < vmList.size(); i++) {
            vmByIndex.put(i, vmList.get(i));
        }

        // Run optimization using Executor
        ParetoFront front = optimize(taskList, vmList);

        if (front.isEmpty()) {
            System.err.println("[MOEA-NSGA-II] Optimization returned empty Pareto front");
            return assignments;
        }

        // Select a solution from the Pareto front
        selectedSolution = selectSolution(front);

        if (selectedSolution == null) {
            System.err.println("[MOEA-NSGA-II] Could not select solution from Pareto front");
            return assignments;
        }

        // Apply the selected solution
        int[] taskAssignment = selectedSolution.getTaskAssignment();
        List<List<Integer>> vmTaskOrder = selectedSolution.getVmTaskOrder();

        for (int vmIdx = 0; vmIdx < vmTaskOrder.size(); vmIdx++) {
            VM vm = vmByIndex.get(vmIdx);
            if (vm == null) continue;

            List<Integer> taskOrder = vmTaskOrder.get(vmIdx);
            for (int taskIdx : taskOrder) {
                Task task = taskByIndex.get(taskIdx);
                if (task == null) continue;

                if (taskAssignment[taskIdx] != vmIdx) {
                    continue;
                }

                task.assignToVM(vm.getId(), currentTime);
                vm.assignTask(task);
                assignments.put(task, vm);
            }
        }

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-NSGA-II] Assigned " + assignments.size() + " tasks");
            System.out.println("[MOEA-NSGA-II] Selected solution objectives: " +
                java.util.Arrays.toString(selectedSolution.getObjectiveValues()));
        }

        return assignments;
    }

    /**
     * Selects a single solution from the Pareto front based on the configured method.
     */
    private SchedulingSolution selectSolution(ParetoFront front) {
        switch (selectionMethod) {
            case KNEE_POINT:
                return front.getKneePoint();
            case BEST_MAKESPAN:
                return front.getBestForObjective(0);
            case BEST_ENERGY:
                return front.getBestForObjective(1);
            case WEIGHTED_SUM:
                return front.getByWeightedSum(selectionWeights);
            case FIRST:
            default:
                List<SchedulingSolution> solutions = front.getSolutions();
                return solutions.isEmpty() ? null : solutions.get(0);
        }
    }

    @Override
    public String getStrategyName() {
        return "MOEA-NSGA-II (Executor)";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("MOEA Framework NSGA-II using Executor API. ");
        sb.append("Objectives: ");
        for (int i = 0; i < config.getObjectives().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(config.getObjectives().get(i).getName());
        }
        sb.append(". Selection: ").append(selectionMethod);
        if (useDistributedEvaluation) {
            sb.append(" (distributed)");
        }
        return sb.toString();
    }

    @Override
    public boolean isBatchOptimizing() {
        return true;
    }

    /**
     * Gets the NSGA-II configuration.
     */
    public NSGA2Configuration getConfiguration() {
        return config;
    }

    // ==================== MultiObjectiveTaskSchedulingStrategy Interface ====================

    @Override
    public ParetoFront optimizeAndGetParetoFront(List<Task> tasks, List<VM> vms) {
        return optimize(tasks, vms);
    }

    @Override
    public Map<Task, VM> applySolution(SchedulingSolution solution, List<Task> tasks,
                                        List<VM> vms, long currentTime) {
        Map<Task, VM> assignments = new LinkedHashMap<>();

        if (solution == null || tasks.isEmpty() || vms.isEmpty()) {
            return assignments;
        }

        List<Task> taskList = new ArrayList<>(tasks);
        List<VM> vmList = new ArrayList<>(vms);

        Map<Integer, Task> taskByIndex = new LinkedHashMap<>();
        Map<Integer, VM> vmByIndex = new LinkedHashMap<>();

        for (int i = 0; i < taskList.size(); i++) {
            taskByIndex.put(i, taskList.get(i));
        }
        for (int i = 0; i < vmList.size(); i++) {
            vmByIndex.put(i, vmList.get(i));
        }

        int[] taskAssignment = solution.getTaskAssignment();
        List<List<Integer>> vmTaskOrder = solution.getVmTaskOrder();

        for (int vmIdx = 0; vmIdx < vmTaskOrder.size(); vmIdx++) {
            VM vm = vmByIndex.get(vmIdx);
            if (vm == null) continue;

            List<Integer> taskOrder = vmTaskOrder.get(vmIdx);
            for (int taskIdx : taskOrder) {
                if (taskIdx < 0 || taskIdx >= taskList.size()) continue;

                Task task = taskByIndex.get(taskIdx);
                if (task == null) continue;

                if (taskAssignment[taskIdx] != vmIdx) {
                    continue;
                }

                task.assignToVM(vm.getId(), currentTime);
                vm.assignTask(task);
                assignments.put(task, vm);
            }
        }

        return assignments;
    }

    @Override
    public List<String> getObjectiveNames() {
        return config.getObjectiveNames();
    }

    @Override
    public boolean[] getObjectiveMinimization() {
        return config.getMinimizationArray();
    }
}
