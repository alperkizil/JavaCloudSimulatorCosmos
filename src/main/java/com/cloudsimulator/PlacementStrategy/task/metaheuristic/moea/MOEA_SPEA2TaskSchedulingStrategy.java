package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;
import com.cloudsimulator.PlacementStrategy.task.TaskAssignmentStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.CrossoverOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.algorithm.SPEA2;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.initialization.RandomInitialization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Task scheduling strategy using MOEA Framework's SPEA2 implementation.
 *
 * SPEA2 (Strength Pareto Evolutionary Algorithm 2) uses a novel strength-based
 * measure of fitness for handling multiple objectives. Key features:
 * - Strength-based fitness assignment
 * - Fine-grained fitness based on distance to k-th nearest neighbor
 * - Environmental selection with truncation
 *
 * The strategy uses our existing:
 * - CrossoverOperator (uniform, two-point, or order-based)
 * - MutationOperator (reassign, swap, combined)
 * - RepairOperator (ensures valid task-to-VM assignments)
 * - SchedulingObjective (Makespan, Energy, etc.)
 *
 * IMPORTANT: Seeds from the simulator's RandomGenerator are propagated to
 * MOEA Framework's PRNG for reproducibility.
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
 * MOEA_SPEA2TaskSchedulingStrategy strategy = new MOEA_SPEA2TaskSchedulingStrategy(config);
 * Map<Task, VM> assignments = strategy.assignAll(tasks, vms, currentTime);
 * </pre>
 */
public class MOEA_SPEA2TaskSchedulingStrategy implements TaskAssignmentStrategy {

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
    private int kParameter;  // SPEA2's k parameter for niching

    // Cached results
    private ParetoFront lastParetoFront;
    private SchedulingSolution selectedSolution;
    private int lastEvaluationCount;

    /**
     * Creates a MOEA Framework SPEA2 task scheduling strategy with default k=1.
     *
     * @param config NSGA-II configuration (shared configuration format)
     */
    public MOEA_SPEA2TaskSchedulingStrategy(NSGA2Configuration config) {
        this(config, 1);  // k=1 is recommended for performance
    }

    /**
     * Creates a MOEA Framework SPEA2 task scheduling strategy.
     *
     * @param config NSGA-II configuration (shared configuration format)
     * @param kParameter niching parameter specifying that crowding is computed
     *                   using the k-th nearest neighbor (recommend k=1)
     */
    public MOEA_SPEA2TaskSchedulingStrategy(NSGA2Configuration config, int kParameter) {
        this.config = config;
        this.kParameter = kParameter;
        this.selectionMethod = SolutionSelectionMethod.KNEE_POINT;
        this.selectionWeights = new double[]{0.5, 0.5};
    }

    /**
     * Sets the method for selecting a single solution from the Pareto front.
     */
    public MOEA_SPEA2TaskSchedulingStrategy setSelectionMethod(SolutionSelectionMethod method) {
        this.selectionMethod = method;
        return this;
    }

    /**
     * Sets weights for weighted sum selection.
     */
    public MOEA_SPEA2TaskSchedulingStrategy setSelectionWeights(double[] weights) {
        this.selectionWeights = weights.clone();
        return this;
    }

    /**
     * Sets the k parameter for SPEA2's niching mechanism.
     * Higher values consider more neighbors, k=1 is recommended for performance.
     */
    public MOEA_SPEA2TaskSchedulingStrategy setKParameter(int k) {
        this.kParameter = k;
        return this;
    }

    /**
     * Runs MOEA Framework's SPEA2 and returns the Pareto front.
     *
     * @param tasks Tasks to schedule
     * @param vms   Available VMs
     * @return Pareto front of non-dominated solutions
     */
    public ParetoFront optimize(List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return new ParetoFront(config.getObjectiveNames(), config.getMinimizationArray());
        }

        // CRITICAL: Propagate seed from simulator's RandomGenerator to MOEA Framework
        propagateSeed();

        // Create our operators using MOEA's PRNG-backed Random
        Random moeaRandom = PRNG.getRandom();

        RepairOperator repairOperator = new RepairOperator(tasks, vms, moeaRandom);

        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[MOEA-SPEA2] Problem is infeasible: some tasks have no valid VMs");
            return new ParetoFront(config.getObjectiveNames(), config.getMinimizationArray());
        }

        CrossoverOperator crossoverOperator = new CrossoverOperator(
            config.getCrossoverType(),
            config.getNumObjectives(),
            moeaRandom
        );

        MutationOperator mutationOperator = new MutationOperator(
            vms.size(),
            repairOperator,
            moeaRandom
        );

        // Create MOEA Framework problem adapter
        TaskSchedulingProblem problem = new TaskSchedulingProblem(
            tasks, vms, config.getObjectives(), repairOperator
        );

        // Create our custom variation operator
        TaskSchedulingVariation variation = new TaskSchedulingVariation(
            crossoverOperator,
            mutationOperator,
            repairOperator,
            config.getCrossoverRate(),
            config.getMutationRate(),
            tasks.size(),
            vms.size(),
            config.getNumObjectives()
        );

        // Create and configure MOEA Framework's SPEA2
        SPEA2 algorithm = new SPEA2(
            problem,
            config.getPopulationSize(),
            new RandomInitialization(problem),
            variation,
            config.getPopulationSize(),  // numberOfOffspring = populationSize
            kParameter
        );

        // Calculate max evaluations from termination condition
        int maxEvaluations = calculateMaxEvaluations();

        // Run the algorithm
        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-SPEA2] Starting optimization with " + maxEvaluations + " evaluations");
            System.out.println("[MOEA-SPEA2] Population: " + config.getPopulationSize() +
                ", k=" + kParameter +
                ", Crossover: " + config.getCrossoverRate() +
                ", Mutation: " + config.getMutationRate());
        }

        algorithm.run(maxEvaluations);
        lastEvaluationCount = algorithm.getNumberOfEvaluations();

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-SPEA2] Completed " + lastEvaluationCount + " evaluations");
        }

        // Extract the non-dominated population (Pareto front)
        NondominatedPopulation result = algorithm.getResult();

        // Convert to our ParetoFront format
        lastParetoFront = convertToParetoFront(result, problem);

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-SPEA2] Pareto front contains " + lastParetoFront.size() + " solutions");
        }

        // Clean up (only if not already terminated)
        if (!algorithm.isTerminated()) {
            algorithm.terminate();
        }

        return lastParetoFront;
    }

    /**
     * Propagates the seed from our RandomGenerator to MOEA Framework's PRNG.
     * This ensures reproducibility across runs with the same seed.
     */
    private void propagateSeed() {
        try {
            long seed;
            if (config.hasRandomSeed()) {
                seed = config.getRandomSeed();
            } else {
                // Use the simulator's RandomGenerator seed
                seed = RandomGenerator.getInstance().getSeed();
            }
            PRNG.setSeed(seed);

            if (config.isVerboseLogging()) {
                System.out.println("[MOEA-SPEA2] Using seed: " + seed);
            }
        } catch (IllegalStateException e) {
            // RandomGenerator not initialized - use config seed or system time
            if (config.hasRandomSeed()) {
                PRNG.setSeed(config.getRandomSeed());
            }
            // Otherwise PRNG uses its default initialization
        }
    }

    /**
     * Calculates maximum evaluations from the termination condition.
     */
    private int calculateMaxEvaluations() {
        // Parse from termination condition description or use default
        // GenerationCountTermination(N) -> N * populationSize evaluations
        String desc = config.getTerminationCondition().getDescription();

        if (desc.contains("generation")) {
            // Extract number from description like "100 generations"
            String[] parts = desc.split(" ");
            try {
                int generations = Integer.parseInt(parts[0]);
                return generations * config.getPopulationSize();
            } catch (NumberFormatException e) {
                // Fall back to default
            }
        }

        // Default: 100 generations worth
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

            // Copy objective values
            double[] objectives = new double[config.getNumObjectives()];
            for (int i = 0; i < objectives.length; i++) {
                objectives[i] = solution.getObjective(i);
            }
            schedulingSolution.setObjectiveValues(objectives);
            schedulingSolution.setRank(0); // All are on front 0

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
     * Gets the k parameter used for SPEA2's niching.
     */
    public int getKParameter() {
        return kParameter;
    }

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        // For single-task selection, return first candidate
        // This strategy is designed for batch optimization
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

        // Build lookup maps
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

        // Run optimization
        ParetoFront front = optimize(taskList, vmList);

        if (front.isEmpty()) {
            System.err.println("[MOEA-SPEA2] Optimization returned empty Pareto front");
            return assignments;
        }

        // Select a solution from the Pareto front
        selectedSolution = selectSolution(front);

        if (selectedSolution == null) {
            System.err.println("[MOEA-SPEA2] Could not select solution from Pareto front");
            return assignments;
        }

        // Apply the selected solution
        int[] taskAssignment = selectedSolution.getTaskAssignment();
        List<List<Integer>> vmTaskOrder = selectedSolution.getVmTaskOrder();

        // Assign tasks according to the solution
        for (int vmIdx = 0; vmIdx < vmTaskOrder.size(); vmIdx++) {
            VM vm = vmByIndex.get(vmIdx);
            if (vm == null) continue;

            List<Integer> taskOrder = vmTaskOrder.get(vmIdx);
            for (int taskIdx : taskOrder) {
                Task task = taskByIndex.get(taskIdx);
                if (task == null) continue;

                if (taskAssignment[taskIdx] != vmIdx) {
                    continue; // Skip inconsistent assignments
                }

                task.assignToVM(vm.getId(), currentTime);
                vm.assignTask(task);
                assignments.put(task, vm);
            }
        }

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-SPEA2] Assigned " + assignments.size() + " tasks");
            System.out.println("[MOEA-SPEA2] Selected solution objectives: " +
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
        return "MOEA-SPEA2";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("MOEA Framework SPEA2 implementation (k=").append(kParameter).append("). ");
        sb.append("Objectives: ");
        for (int i = 0; i < config.getObjectives().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(config.getObjectives().get(i).getName());
        }
        sb.append(". Selection: ").append(selectionMethod);
        return sb.toString();
    }

    @Override
    public boolean isBatchOptimizing() {
        return true;
    }

    /**
     * Gets the NSGA-II configuration (shared configuration format).
     */
    public NSGA2Configuration getConfiguration() {
        return config;
    }
}
