package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.model.Host;
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

import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.NondominatedSortingPopulation;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variation;
import org.moeaframework.core.initialization.InjectedInitialization;
import org.moeaframework.core.initialization.RandomInitialization;
import org.moeaframework.core.operator.CompoundVariation;
import org.moeaframework.core.operator.real.PM;
import org.moeaframework.core.operator.real.SBX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NSGA-II variant that enforces a global power ceiling (P_cap) via
 * MOEA Framework's native constraint handling (Deb's constrained-domination
 * rules).
 *
 * Mirrors the search/operators/seeding of {@link MOEA_NSGA2TaskSchedulingStrategy}
 * but instantiates {@link PowerCeilingSchedulingProblem} — which reports a single
 * constraint value = max(0, peakPower − cap). When that value is non-zero,
 * NSGA-II's tournament selection and environmental survival automatically
 * prefer feasible points; among infeasibles, lower violation wins; among
 * feasibles, normal Pareto dominance on the optimization objectives applies.
 *
 * Additive: the base MOEA_NSGA2TaskSchedulingStrategy is untouched.
 */
public class MOEA_NSGA2PowerCeilingTaskSchedulingStrategy implements MultiObjectiveTaskSchedulingStrategy {

    private final NSGA2Configuration config;
    private final double powerCapWatts;
    private final List<Host> hosts;

    private ParetoFront lastParetoFront;
    private NondominatedPopulation lastMoeaResult;
    private SchedulingSolution selectedSolution;
    private int lastEvaluationCount;

    public MOEA_NSGA2PowerCeilingTaskSchedulingStrategy(NSGA2Configuration config,
                                                        double powerCapWatts,
                                                        List<Host> hosts) {
        this.config = config;
        this.powerCapWatts = powerCapWatts;
        this.hosts = hosts;
    }

    public ParetoFront optimize(List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return new ParetoFront(config.getObjectiveNames(), config.getMinimizationArray());
        }

        propagateSeed();

        RepairOperator repairOperator = new RepairOperator(tasks, vms, PRNG.getRandom());
        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[MOEA-NSGA-II-PC] Problem is infeasible: some tasks have no valid VMs");
            return new ParetoFront(config.getObjectiveNames(), config.getMinimizationArray());
        }

        PowerCeilingSchedulingProblem problem = new PowerCeilingSchedulingProblem(
            tasks, vms, config.getObjectives(), repairOperator, hosts, powerCapWatts
        );

        int maxEvaluations = calculateMaxEvaluations();

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-NSGA-II-PC] Starting optimization");
            System.out.println("[MOEA-NSGA-II-PC] Cap: " + powerCapWatts + " W, Evaluations: " + maxEvaluations);
            System.out.println("[MOEA-NSGA-II-PC] Population: " + config.getPopulationSize()
                + ", Crossover: " + config.getCrossoverRate()
                + ", Mutation: " + config.getMutationRate());
        }

        Variation variation = new CompoundVariation(
            new SBX(config.getCrossoverRate(), 5.0),
            new PM(config.getMutationRate(), 5.0)
        );

        // MOEA's Executor doesn't expose the initialization hook needed to
        // inject heuristic seeds or to attach constraints properly, so we
        // construct NSGAII directly — this mirrors the seeded-path in the
        // non-constrained strategy, also used for the non-seeded case here.
        List<int[]> seeds = config.getSeedAssignments();
        List<Solution> injected = new ArrayList<>();
        for (int[] seed : seeds) {
            if (seed == null || seed.length != tasks.size()) continue;
            SchedulingSolution s = new SchedulingSolution(
                tasks.size(), vms.size(), config.getNumObjectives());
            s.setTaskAssignment(seed.clone());
            s.rebuildTaskOrdering();
            injected.add(problem.encode(s));
        }

        org.moeaframework.core.Initialization initialization = injected.isEmpty()
            ? new RandomInitialization(problem)
            : new InjectedInitialization(problem, injected);

        NSGAII algorithm = new NSGAII(
            problem,
            config.getPopulationSize(),
            new NondominatedSortingPopulation(),
            null,    // default binary tournament (constraint-aware when Problem reports constraints)
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

        lastParetoFront = convertToParetoFront(result, problem);

        if (config.isVerboseLogging()) {
            long feasibleCount = 0;
            for (Solution s : result) if (!s.violatesConstraints()) feasibleCount++;
            System.out.println("[MOEA-NSGA-II-PC] Done. Final population: "
                + result.size() + " (" + feasibleCount + " feasible at cap "
                + powerCapWatts + " W)");
        }

        return lastParetoFront;
    }

    private void propagateSeed() {
        try {
            long seed = config.hasRandomSeed()
                ? config.getRandomSeed()
                : RandomGenerator.getInstance().getSeed();
            PRNG.setSeed(seed);
        } catch (IllegalStateException e) {
            if (config.hasRandomSeed()) PRNG.setSeed(config.getRandomSeed());
        }
    }

    private int calculateMaxEvaluations() {
        TerminationCondition termination = config.getTerminationCondition();
        if (termination instanceof GenerationCountTermination) {
            return ((GenerationCountTermination) termination).getMaxGenerations()
                * config.getPopulationSize();
        }
        String desc = termination.getDescription();
        if (desc.contains("generation")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(desc);
            if (m.find()) {
                try { return Integer.parseInt(m.group()) * config.getPopulationSize(); }
                catch (NumberFormatException ignored) {}
            }
        }
        return 100 * config.getPopulationSize();
    }

    private ParetoFront convertToParetoFront(NondominatedPopulation population,
                                              PowerCeilingSchedulingProblem problem) {
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

    // ---- MultiObjectiveTaskSchedulingStrategy ----

    @Override
    public ParetoFront optimizeAndGetParetoFront(List<Task> tasks, List<VM> vms) {
        return optimize(tasks, vms);
    }

    @Override
    public ParetoFront getLastParetoFront() {
        return lastParetoFront;
    }

    @Override
    public Map<Task, VM> applySolution(SchedulingSolution solution, List<Task> tasks,
                                        List<VM> vms, long currentTime) {
        Map<Task, VM> assignments = new LinkedHashMap<>();
        if (solution == null || tasks.isEmpty() || vms.isEmpty()) return assignments;

        int[] taskAssignment = solution.getTaskAssignment();
        List<List<Integer>> vmTaskOrder = solution.getVmTaskOrder();

        for (int vmIdx = 0; vmIdx < vmTaskOrder.size(); vmIdx++) {
            if (vmIdx >= vms.size()) continue;
            VM vm = vms.get(vmIdx);
            if (vm == null) continue;

            for (int taskIdx : vmTaskOrder.get(vmIdx)) {
                if (taskIdx < 0 || taskIdx >= tasks.size()) continue;
                Task task = tasks.get(taskIdx);
                if (task == null || taskAssignment[taskIdx] != vmIdx) continue;
                task.assignToVM(vm.getId(), currentTime);
                vm.assignTask(task);
                assignments.put(task, vm);
            }
        }
        return assignments;
    }

    @Override
    public List<String> getObjectiveNames() { return config.getObjectiveNames(); }

    @Override
    public boolean[] getObjectiveMinimization() { return config.getMinimizationArray(); }

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        if (candidateVMs == null || candidateVMs.isEmpty()) return Optional.empty();
        return Optional.of(candidateVMs.get(0));
    }

    @Override
    public Map<Task, VM> assignAll(List<Task> tasks, List<VM> vms, long currentTime) {
        Map<Task, VM> assignments = new LinkedHashMap<>();
        if (tasks.isEmpty() || vms.isEmpty()) return assignments;

        ParetoFront front = optimize(tasks, vms);
        if (front.isEmpty()) {
            System.err.println("[MOEA-NSGA-II-PC] Empty Pareto front");
            return assignments;
        }
        selectedSolution = front.getKneePoint();
        if (selectedSolution == null) selectedSolution = front.getSolutions().get(0);
        return applySolution(selectedSolution, tasks, vms, currentTime);
    }

    @Override
    public String getStrategyName() {
        return "MOEA-NSGA-II-PowerCeiling (" + (long) powerCapWatts + "W)";
    }

    @Override
    public String getDescription() {
        return "MOEA NSGA-II with Deb constrained-domination enforcing P_cap = "
            + powerCapWatts + " W";
    }

    @Override
    public boolean isBatchOptimizing() { return true; }

    public double getPowerCapWatts() { return powerCapWatts; }
    public int getLastEvaluationCount() { return lastEvaluationCount; }
    public NondominatedPopulation getLastMoeaResult() { return lastMoeaResult; }
    public SchedulingSolution getSelectedSolution() { return selectedSolution; }
}
