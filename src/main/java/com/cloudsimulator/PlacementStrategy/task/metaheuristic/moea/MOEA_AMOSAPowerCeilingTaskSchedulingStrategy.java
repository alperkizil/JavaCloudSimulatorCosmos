package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;
import com.cloudsimulator.PlacementStrategy.task.MultiObjectiveTaskSchedulingStrategy;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.NSGA2Configuration;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.MutationOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.FitnessEvaluationsTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.GenerationCountTermination;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.termination.TerminationCondition;

import org.moeaframework.core.Initialization;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.initialization.InjectedInitialization;
import org.moeaframework.core.initialization.RandomInitialization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AMOSA variant that enforces a global power ceiling (P_cap). Mirrors the
 * construction pattern of {@link MOEA_AMOSATaskSchedulingStrategy} but
 * instantiates {@link PowerCeilingSchedulingProblem} and runs
 * {@link FixedAMOSAConstrained}, which chains
 * {@link org.moeaframework.core.comparator.AggregateConstraintComparator}
 * ahead of Pareto so Deb's constrained-domination rules bias acceptance and
 * archive admission toward feasible (peakPower ≤ cap) solutions.
 *
 * Additive: the base MOEA_AMOSATaskSchedulingStrategy is untouched.
 */
public class MOEA_AMOSAPowerCeilingTaskSchedulingStrategy implements MultiObjectiveTaskSchedulingStrategy {

    private final NSGA2Configuration config;
    private final double powerCapWatts;
    private final List<Host> hosts;

    // AMOSA parameters (defaults copied from base strategy)
    private double gamma = 2.0;
    private int softLimit;
    private int hardLimit = 10;
    private double initialTemperature = 200.0;
    private double stoppingTemperature = 0.0000001;
    private double alpha = 0.8;
    private int iterationsPerTemperature = 500;
    private int hillClimbingIterations = 20;

    private ParetoFront lastParetoFront;
    private NondominatedPopulation lastMoeaResult;
    private SchedulingSolution selectedSolution;
    private int lastEvaluationCount;

    public MOEA_AMOSAPowerCeilingTaskSchedulingStrategy(NSGA2Configuration config,
                                                        double powerCapWatts,
                                                        List<Host> hosts) {
        this.config = config;
        this.powerCapWatts = powerCapWatts;
        this.hosts = hosts;
        this.softLimit = config.getPopulationSize();
    }

    public ParetoFront optimize(List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            return new ParetoFront(config.getObjectiveNames(), config.getMinimizationArray());
        }

        propagateSeed();

        RepairOperator repairOperator = new RepairOperator(tasks, vms, PRNG.getRandom());
        if (!repairOperator.isProblemFeasible()) {
            System.err.println("[MOEA-AMOSA-PC] Problem is infeasible: some tasks have no valid VMs");
            return new ParetoFront(config.getObjectiveNames(), config.getMinimizationArray());
        }

        PowerCeilingSchedulingProblem problem = new PowerCeilingSchedulingProblem(
            tasks, vms, config.getObjectives(), repairOperator, hosts, powerCapWatts
        );

        int maxEvaluations = calculateMaxEvaluations();

        if (config.isVerboseLogging()) {
            System.out.println("[MOEA-AMOSA-PC] Starting optimization");
            System.out.println("[MOEA-AMOSA-PC] Cap: " + powerCapWatts + " W, Evaluations: " + maxEvaluations);
            System.out.println("[MOEA-AMOSA-PC] T0: " + initialTemperature + ", alpha: " + alpha);
        }

        // Domain-specific REASSIGN mutation (same choice as base strategy —
        // every mutation must change objectives in AMOSA's trajectory search).
        MutationOperator domainMutation = new MutationOperator(
            MutationOperator.MutationType.REASSIGN, vms.size(), repairOperator, PRNG.getRandom());
        TaskSchedulingMutation mutation = new TaskSchedulingMutation(
            domainMutation, repairOperator, config.getMutationRate(),
            tasks.size(), vms.size());

        Initialization initialization = buildInitialization(problem, tasks, vms);

        FixedAMOSAConstrained amosa = new FixedAMOSAConstrained(problem,
            initialization,
            mutation,
            gamma,
            softLimit,
            hardLimit,
            stoppingTemperature,
            initialTemperature,
            alpha,
            iterationsPerTemperature,
            hillClimbingIterations);
        amosa.setMaxEvaluations(maxEvaluations);

        while (!amosa.isTerminated() && amosa.getNumberOfEvaluations() < maxEvaluations) {
            amosa.step();
        }
        if (!amosa.isTerminated()) {
            amosa.terminate();
        }

        NondominatedPopulation result = amosa.getResult();
        lastMoeaResult = result;
        lastEvaluationCount = amosa.getNumberOfEvaluations();

        lastParetoFront = convertToParetoFront(result, problem);

        if (config.isVerboseLogging()) {
            long feasibleCount = 0;
            for (Solution s : result) if (!s.violatesConstraints()) feasibleCount++;
            System.out.println("[MOEA-AMOSA-PC] Done. Final archive: "
                + result.size() + " (" + feasibleCount + " feasible at cap "
                + powerCapWatts + " W)");
        }

        return lastParetoFront;
    }

    private Initialization buildInitialization(PowerCeilingSchedulingProblem problem,
                                                List<Task> tasks, List<VM> vms) {
        List<int[]> seeds = config.getSeedAssignments();
        if (seeds == null || seeds.isEmpty()) {
            return new RandomInitialization(problem);
        }

        int numTasks = tasks.size();
        List<Solution> injected = new ArrayList<>();
        for (int[] seed : seeds) {
            if (seed == null || seed.length != numTasks) continue;
            SchedulingSolution s = new SchedulingSolution(
                numTasks, vms.size(), config.getNumObjectives());
            s.setTaskAssignment(seed.clone());
            s.rebuildTaskOrdering();
            injected.add(problem.encode(s));
        }

        if (injected.isEmpty()) return new RandomInitialization(problem);
        return new InjectedInitialization(problem, injected);
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

        if (termination instanceof FitnessEvaluationsTermination) {
            return (int) ((FitnessEvaluationsTermination) termination).getMaxEvaluations();
        }
        if (termination instanceof GenerationCountTermination) {
            return ((GenerationCountTermination) termination).getMaxGenerations()
                * iterationsPerTemperature;
        }
        String desc = termination.getDescription();
        if (desc.contains("generation")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(desc);
            if (m.find()) {
                try { return Integer.parseInt(m.group()) * iterationsPerTemperature; }
                catch (NumberFormatException ignored) {}
            }
        }
        return 100 * iterationsPerTemperature;
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
            System.err.println("[MOEA-AMOSA-PC] Empty Pareto front");
            return assignments;
        }
        selectedSolution = front.getKneePoint();
        if (selectedSolution == null) selectedSolution = front.getSolutions().get(0);
        return applySolution(selectedSolution, tasks, vms, currentTime);
    }

    @Override
    public String getStrategyName() {
        return "MOEA-AMOSA-PowerCeiling (" + (long) powerCapWatts + "W)";
    }

    @Override
    public String getDescription() {
        return "MOEA AMOSA with Deb constrained-domination enforcing P_cap = "
            + powerCapWatts + " W";
    }

    @Override
    public boolean isBatchOptimizing() { return true; }

    public double getPowerCapWatts() { return powerCapWatts; }
    public int getLastEvaluationCount() { return lastEvaluationCount; }
    public NondominatedPopulation getLastMoeaResult() { return lastMoeaResult; }
    public SchedulingSolution getSelectedSolution() { return selectedSolution; }

    // ---- parameter setters (mirrors MOEA_AMOSATaskSchedulingStrategy) ----
    public void setGamma(double gamma) { this.gamma = gamma; }
    public void setSoftLimit(int softLimit) { this.softLimit = softLimit; }
    public void setHardLimit(int hardLimit) { this.hardLimit = hardLimit; }
    public void setInitialTemperature(double t0) { this.initialTemperature = t0; }
    public void setStoppingTemperature(double t) { this.stoppingTemperature = t; }
    public void setAlpha(double alpha) { this.alpha = alpha; }
    public void setIterationsPerTemperature(int n) { this.iterationsPerTemperature = n; }
    public void setHillClimbingIterations(int n) { this.hillClimbingIterations = n; }
}
