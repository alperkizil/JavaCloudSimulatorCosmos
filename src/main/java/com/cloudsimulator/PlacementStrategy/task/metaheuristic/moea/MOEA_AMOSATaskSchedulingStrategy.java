package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;
import com.cloudsimulator.PlacementStrategy.task.MultiObjectiveTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.Analyzer;
import org.moeaframework.Executor;
import org.moeaframework.Instrumenter;
import org.moeaframework.analysis.collector.Observations;
import org.moeaframework.analysis.plot.Plot;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;

import javax.swing.JFrame;
import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Task scheduling strategy using MOEA Framework's Executor to run AMOSA.
 *
 * AMOSA (Archived Multi-Objective Simulated Annealing) is a simulated annealing-based
 * multi-objective optimization algorithm that incorporates an archive mechanism
 * to maintain a diverse set of non-dominated solutions (Pareto front). Key features:
 * - Single-solution based search (different from population-based EAs)
 * - Temperature-controlled acceptance of worse solutions for escaping local optima
 * - Archive for storing non-dominated solutions discovered during search
 * - Hill climbing refinement phase
 * - Clustering-based archive truncation
 *
 * AMOSA provides an alternative approach to evolutionary algorithms, particularly useful when:
 * - Memory constraints limit population size
 * - A gradual refinement process is preferred
 * - The problem landscape requires controlled exploration
 *
 * References:
 * - Bandyopadhyay, S., Saha, S., Maulik, U., Deb, K. (2008). A Simulated Annealing-Based
 *   Multiobjective Optimization Algorithm: AMOSA. IEEE Transactions on Evolutionary
 *   Computation, vol. 12, no. 3, pp. 269-283.
 *
 * Usage:
 * <pre>
 * NSGA2Configuration config = NSGA2Configuration.builder()
 *     .populationSize(100)  // Used as soft archive limit
 *     .addObjective(new MakespanObjective())
 *     .addObjective(new EnergyObjective())
 *     .terminationCondition(new GenerationCountTermination(200))
 *     .build();
 *
 * MOEA_AMOSATaskSchedulingStrategy strategy = new MOEA_AMOSATaskSchedulingStrategy(config);
 * Map&lt;Task, VM&gt; assignments = strategy.assignAll(tasks, vms, currentTime);
 * </pre>
 *
 * @see Executor
 * @see Analyzer
 */
public class MOEA_AMOSATaskSchedulingStrategy implements MultiObjectiveTaskSchedulingStrategy {

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

    // AMOSA specific parameters
    private double gamma;
    private int softLimit;
    private int hardLimit;
    private double initialTemperature;
    private double stoppingTemperature;
    private double alpha;  // Cooling rate
    private int iterationsPerTemperature;
    private int hillClimbingIterations;

    // Cached results
    private ParetoFront lastParetoFront;
    private NondominatedPopulation lastMoeaResult;
    private SchedulingSolution selectedSolution;
    private int lastEvaluationCount;
    private Observations lastObservations;

    /**
     * Creates a MOEA Framework AMOSA task scheduling strategy using Executor.
     *
     * @param config Configuration (shared format with NSGA-II)
     */
    public MOEA_AMOSATaskSchedulingStrategy(NSGA2Configuration config) {
        this.config = config;
        this.selectionMethod = SolutionSelectionMethod.KNEE_POINT;
        this.selectionWeights = new double[]{0.5, 0.5};
        this.useDistributedEvaluation = false;
        this.collectRuntimeData = false;

        // AMOSA default parameters
        this.gamma = 2.0;              // Archive size scaling factor
        this.softLimit = config.getPopulationSize();  // Soft archive limit
        this.hardLimit = 10;           // Hard archive limit for clustering
        this.initialTemperature = 200.0;
        this.stoppingTemperature = 0.0000001;
        this.alpha = 0.8;              // Geometric cooling rate
        this.iterationsPerTemperature = 500;
        this.hillClimbingIterations = 20;
    }

    /**
     * Sets the method for selecting a single solution from the Pareto front.
     */
    public MOEA_AMOSATaskSchedulingStrategy setSelectionMethod(SolutionSelectionMethod method) {
        this.selectionMethod = method;
        return this;
    }

    /**
     * Sets weights for weighted sum selection.
     */
    public MOEA_AMOSATaskSchedulingStrategy setSelectionWeights(double[] weights) {
        this.selectionWeights = weights.clone();
        return this;
    }

    /**
     * Enables distributed evaluation across all CPU cores.
     * Note: AMOSA is inherently sequential, so this has limited benefit.
     */
    public MOEA_AMOSATaskSchedulingStrategy enableDistributedEvaluation() {
        this.useDistributedEvaluation = true;
        return this;
    }

    /**
     * Enables collection of runtime data via Instrumenter.
     * This allows tracking convergence, hypervolume over time, etc.
     */
    public MOEA_AMOSATaskSchedulingStrategy enableRuntimeDataCollection() {
        this.collectRuntimeData = true;
        return this;
    }

    /**
     * Sets the gamma parameter for archive size scaling.
     * The archive is initialized with gamma * softLimit solutions.
     *
     * @param gamma scaling factor (default: 2.0)
     * @return this strategy for method chaining
     */
    public MOEA_AMOSATaskSchedulingStrategy setGamma(double gamma) {
        this.gamma = gamma;
        return this;
    }

    /**
     * Gets the gamma parameter.
     */
    public double getGamma() {
        return gamma;
    }

    /**
     * Sets the soft archive limit.
     * When archive exceeds this limit, clustering is triggered.
     *
     * @param limit soft limit (default: population size)
     * @return this strategy for method chaining
     */
    public MOEA_AMOSATaskSchedulingStrategy setSoftLimit(int limit) {
        this.softLimit = limit;
        return this;
    }

    /**
     * Gets the soft limit.
     */
    public int getSoftLimit() {
        return softLimit;
    }

    /**
     * Sets the hard archive limit.
     * Archive is truncated to this size using clustering.
     *
     * @param limit hard limit (default: 10)
     * @return this strategy for method chaining
     */
    public MOEA_AMOSATaskSchedulingStrategy setHardLimit(int limit) {
        this.hardLimit = limit;
        return this;
    }

    /**
     * Gets the hard limit.
     */
    public int getHardLimit() {
        return hardLimit;
    }

    /**
     * Sets the initial temperature for simulated annealing.
     *
     * @param temperature initial temperature (default: 200.0)
     * @return this strategy for method chaining
     */
    public MOEA_AMOSATaskSchedulingStrategy setInitialTemperature(double temperature) {
        this.initialTemperature = temperature;
        return this;
    }

    /**
     * Gets the initial temperature.
     */
    public double getInitialTemperature() {
        return initialTemperature;
    }

    /**
     * Sets the stopping temperature (final temperature).
     *
     * @param temperature stopping temperature (default: 0.0000001)
     * @return this strategy for method chaining
     */
    public MOEA_AMOSATaskSchedulingStrategy setStoppingTemperature(double temperature) {
        this.stoppingTemperature = temperature;
        return this;
    }

    /**
     * Gets the stopping temperature.
     */
    public double getStoppingTemperature() {
        return stoppingTemperature;
    }

    /**
     * Sets the cooling rate (alpha) for geometric cooling schedule.
     * Temperature = alpha * Temperature after each temperature step.
     *
     * @param alpha cooling rate in (0, 1) (default: 0.8)
     * @return this strategy for method chaining
     */
    public MOEA_AMOSATaskSchedulingStrategy setAlpha(double alpha) {
        this.alpha = alpha;
        return this;
    }

    /**
     * Gets the cooling rate.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the number of iterations per temperature level.
     *
     * @param iterations iterations per temperature (default: 500)
     * @return this strategy for method chaining
     */
    public MOEA_AMOSATaskSchedulingStrategy setIterationsPerTemperature(int iterations) {
        this.iterationsPerTemperature = iterations;
        return this;
    }

    /**
     * Gets the iterations per temperature.
     */
    public int getIterationsPerTemperature() {
        return iterationsPerTemperature;
    }

    /**
     * Sets the number of hill climbing iterations for archive refinement.
     *
     * @param iterations hill climbing iterations (default: 20)
     * @return this strategy for method chaining
     */
    public MOEA_AMOSATaskSchedulingStrategy setHillClimbingIterations(int iterations) {
        this.hillClimbingIterations = iterations;
        return this;
    }

    /**
     * Gets the hill climbing iterations.
     */
    public int getHillClimbingIterations() {
        return hillClimbingIterations;
    }

    /**
     * Runs MOEA Framework's AMOSA using the Executor and returns the Pareto front.
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
            System.err.println("[MOEA-AMOSA] Problem is infeasible: some tasks have no valid VMs");
            return new ParetoFront(config.getObjectiveNames(), config.getMinimizationArray());
        }

        // Create MOEA Framework problem adapter
        TaskSchedulingProblem problem = new TaskSchedulingProblem(
            tasks, vms, config.getObjectives(), repairOperator
        );

        // Calculate max evaluations
        int maxEvaluations = calculateMaxEvaluations();

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-AMOSA] Starting optimization with Executor");
            System.out.println("[MOEA-AMOSA] Algorithm: AMOSA, Evaluations: " + maxEvaluations);
            System.out.println("[MOEA-AMOSA] Initial Temp: " + initialTemperature +
                ", Stopping Temp: " + stoppingTemperature +
                ", Alpha: " + alpha);
            System.out.println("[MOEA-AMOSA] Soft Limit: " + softLimit +
                ", Hard Limit: " + hardLimit +
                ", Gamma: " + gamma);
            System.out.println("[MOEA-AMOSA] Iterations/Temp: " + iterationsPerTemperature +
                ", Hill Climbing: " + hillClimbingIterations);
        }

        // Build and configure the Executor using AMOSA algorithm
        Executor executor = new Executor()
            .withProblem(problem)
            .withAlgorithm("AMOSA")
            .withMaxEvaluations(maxEvaluations)
            .withProperty("gamma", gamma)
            .withProperty("softLimit", softLimit)
            .withProperty("hardLimit", hardLimit)
            .withProperty("initialTemperature", initialTemperature)
            .withProperty("stoppingTemperature", stoppingTemperature)
            .withProperty("alpha", alpha)
            .withProperty("numberOfIterationsPerTemperature", iterationsPerTemperature)
            .withProperty("numberOfHillClimbingIterationsForRefinement", hillClimbingIterations)
            // Use polynomial mutation
            .withProperty("pm.rate", config.getMutationRate())
            .withProperty("pm.distributionIndex", 20.0);

        // Optional: distribute evaluation across cores (limited benefit for AMOSA)
        if (useDistributedEvaluation) {
            executor.distributeOnAllCores();
        }

        // Optional: collect runtime data
        Instrumenter instrumenter = null;
        if (collectRuntimeData) {
            instrumenter = new Instrumenter()
                .withProblem(problem)
                .withFrequency(iterationsPerTemperature)
                .attachElapsedTimeCollector()
                .attachGenerationalDistanceCollector()
                .attachHypervolumeCollector();
            executor.withInstrumenter(instrumenter);
        }

        // Run the optimization
        NondominatedPopulation result = executor.run();
        lastMoeaResult = result;
        lastEvaluationCount = maxEvaluations;

        // Store runtime observations if collected
        if (instrumenter != null) {
            lastObservations = instrumenter.getObservations();
        }

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-AMOSA] Optimization completed");
        }

        // Convert to our ParetoFront format
        lastParetoFront = convertToParetoFront(result, problem);

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-AMOSA] Pareto front contains " + lastParetoFront.size() + " solutions");
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
            System.err.println("[MOEA-AMOSA] Problem is infeasible");
            return null;
        }

        TaskSchedulingProblem problem = new TaskSchedulingProblem(
            tasks, vms, config.getObjectives(), repairOperator
        );

        int maxEvaluations = calculateMaxEvaluations();

        // Configure Executor for multiple seeds
        Executor executor = new Executor()
            .withProblem(problem)
            .withAlgorithm("AMOSA")
            .withMaxEvaluations(maxEvaluations)
            .withProperty("gamma", gamma)
            .withProperty("softLimit", softLimit)
            .withProperty("hardLimit", hardLimit)
            .withProperty("initialTemperature", initialTemperature)
            .withProperty("stoppingTemperature", stoppingTemperature)
            .withProperty("alpha", alpha)
            .withProperty("numberOfIterationsPerTemperature", iterationsPerTemperature)
            .withProperty("numberOfHillClimbingIterationsForRefinement", hillClimbingIterations)
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

        analyzer.addAll("AMOSA", results);

        return analyzer.getAnalysis();
    }

    /**
     * Compares AMOSA with NSGA-II using the Analyzer.
     * Runs both algorithms with multiple seeds and produces comparative metrics.
     *
     * @param tasks      Tasks to schedule
     * @param vms        Available VMs
     * @param numSeeds   Number of independent runs per algorithm
     * @return Analyzer results comparing both algorithms
     */
    public Analyzer.AnalyzerResults compareWithNSGAII(List<Task> tasks, List<VM> vms, int numSeeds) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return null;
        }

        RepairOperator repairOperator = new RepairOperator(tasks, vms, PRNG.getRandom());
        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[MOEA-AMOSA] Problem is infeasible");
            return null;
        }

        TaskSchedulingProblem problem = new TaskSchedulingProblem(
            tasks, vms, config.getObjectives(), repairOperator
        );

        int maxEvaluations = calculateMaxEvaluations();

        // Run AMOSA
        Executor amosaExecutor = new Executor()
            .withProblem(problem)
            .withAlgorithm("AMOSA")
            .withMaxEvaluations(maxEvaluations)
            .withProperty("gamma", gamma)
            .withProperty("softLimit", softLimit)
            .withProperty("hardLimit", hardLimit)
            .withProperty("initialTemperature", initialTemperature)
            .withProperty("stoppingTemperature", stoppingTemperature)
            .withProperty("alpha", alpha)
            .withProperty("numberOfIterationsPerTemperature", iterationsPerTemperature)
            .withProperty("numberOfHillClimbingIterationsForRefinement", hillClimbingIterations)
            .withProperty("pm.rate", config.getMutationRate());

        // Run NSGA-II
        Executor nsgaiiExecutor = new Executor()
            .withProblem(problem)
            .withAlgorithm("NSGAII")
            .withMaxEvaluations(maxEvaluations)
            .withProperty("populationSize", config.getPopulationSize())
            .withProperty("sbx.rate", config.getCrossoverRate())
            .withProperty("pm.rate", config.getMutationRate());

        List<NondominatedPopulation> amosaResults = amosaExecutor.runSeeds(numSeeds);
        List<NondominatedPopulation> nsgaiiResults = nsgaiiExecutor.runSeeds(numSeeds);

        // Analyze and compare
        Analyzer analyzer = new Analyzer()
            .withProblem(problem)
            .includeHypervolume()
            .includeGenerationalDistance()
            .includeInvertedGenerationalDistance()
            .includeSpacing()
            .showStatisticalSignificance();

        analyzer.addAll("AMOSA", amosaResults);
        analyzer.addAll("NSGAII", nsgaiiResults);

        return analyzer.getAnalysis();
    }

    /**
     * Compares AMOSA with our native Simulated Annealing (single-objective converted to weighted sum).
     * This helps evaluate the multi-objective vs weighted-sum approach.
     *
     * @param tasks      Tasks to schedule
     * @param vms        Available VMs
     * @param numSeeds   Number of independent runs
     * @return Analyzer results for AMOSA
     */
    public Analyzer.AnalyzerResults compareWithNativeSA(List<Task> tasks, List<VM> vms, int numSeeds) {
        // Note: Native SA is single-objective, so this just runs AMOSA for comparison baseline
        return runWithAnalyzer(tasks, vms, numSeeds);
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
                System.out.println("[MOEA-AMOSA] Using seed: " + seed);
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
        String desc = config.getTerminationCondition().getDescription();

        if (desc.contains("generation")) {
            String[] parts = desc.split(" ");
            try {
                int generations = Integer.parseInt(parts[0]);
                // For AMOSA, we map generations to temperature steps
                return generations * iterationsPerTemperature;
            } catch (NumberFormatException e) {
                // Fall back to default
            }
        }

        // Default: enough evaluations for a reasonable annealing schedule
        return 100 * iterationsPerTemperature;
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
        return showParetoFrontPlot("AMOSA Pareto Front");
    }

    /**
     * Displays the last Pareto front in an X-Y scatter plot with a custom title.
     *
     * @param title the title for the plot window
     * @return the JFrame window displaying the plot, or null if no results available
     */
    public JFrame showParetoFrontPlot(String title) {
        if (lastMoeaResult == null || lastMoeaResult.isEmpty()) {
            System.err.println("[MOEA-AMOSA] No Pareto front available to plot");
            return null;
        }

        String xLabel = config.getObjectiveNames().size() > 0 ? config.getObjectiveNames().get(0) : "Objective 1";
        String yLabel = config.getObjectiveNames().size() > 1 ? config.getObjectiveNames().get(1) : "Objective 2";

        Plot plot = new Plot()
            .add("AMOSA", lastMoeaResult)
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
            System.err.println("[MOEA-AMOSA] No Pareto front available to plot");
            return null;
        }

        List<String> names = config.getObjectiveNames();
        String xLabel = xObjective < names.size() ? names.get(xObjective) : "Objective " + (xObjective + 1);
        String yLabel = yObjective < names.size() ? names.get(yObjective) : "Objective " + (yObjective + 1);

        Plot plot = new Plot()
            .add("AMOSA", lastMoeaResult, xObjective, yObjective)
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
        saveParetoFrontPlot(new File(filename), "AMOSA Pareto Front");
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
            .add("AMOSA", lastMoeaResult)
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
            System.err.println("[MOEA-AMOSA] Optimization returned empty Pareto front");
            return assignments;
        }

        // Select a solution from the Pareto front
        selectedSolution = selectSolution(front);

        if (selectedSolution == null) {
            System.err.println("[MOEA-AMOSA] Could not select solution from Pareto front");
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
            System.out.println("[MOEA-AMOSA] Assigned " + assignments.size() + " tasks");
            System.out.println("[MOEA-AMOSA] Selected solution objectives: " +
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
        return "MOEA-AMOSA (Executor)";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("MOEA Framework AMOSA using Executor API. ");
        sb.append("Archived Multi-Objective Simulated Annealing. ");
        sb.append("Objectives: ");
        for (int i = 0; i < config.getObjectives().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(config.getObjectives().get(i).getName());
        }
        sb.append(". Selection: ").append(selectionMethod);
        sb.append(". Initial Temp: ").append(initialTemperature);
        sb.append(", Alpha: ").append(alpha);
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
     * Gets the configuration.
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
