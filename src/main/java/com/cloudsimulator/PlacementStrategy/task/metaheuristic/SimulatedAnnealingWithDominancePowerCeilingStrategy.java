package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.PlacementStrategy.task.MultiObjectiveTaskSchedulingStrategy;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SA-with-dominance facade extended with a global power ceiling (P_cap).
 *
 * Underlying scalar-fitness SA dynamics are unchanged; the archive is swapped
 * for a {@link ConstrainedNonDominatedArchive} applying Deb's constrained-
 * domination against a peak-power violation computed per evaluation. The
 * returned Pareto front prefers feasible solutions; infeasible points survive
 * only when no feasible incumbent is found.
 *
 * Additive: the base {@link SimulatedAnnealingWithDominanceStrategy} is untouched.
 */
public class SimulatedAnnealingWithDominancePowerCeilingStrategy implements MultiObjectiveTaskSchedulingStrategy {

    private final SAConfiguration config;
    private final double powerCapWatts;
    private final List<Host> hosts;

    private ParetoFront lastParetoFront;
    private SchedulingSolution lastBestSolution;
    private SAStatistics lastStatistics;

    public SimulatedAnnealingWithDominancePowerCeilingStrategy(SAConfiguration config,
                                                               double powerCapWatts,
                                                               List<Host> hosts) {
        this.config = config;
        this.powerCapWatts = powerCapWatts;
        this.hosts = hosts;
    }

    public SAConfiguration getConfiguration() { return config; }
    public double getPowerCapWatts() { return powerCapWatts; }
    public SchedulingSolution getLastBestSolution() { return lastBestSolution; }
    public SAStatistics getLastStatistics() { return lastStatistics; }

    @Override
    public ParetoFront optimizeAndGetParetoFront(List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            lastParetoFront = new ParetoFront(getObjectiveNames(), getObjectiveMinimization());
            return lastParetoFront;
        }

        SimulatedAnnealingPowerCeilingAlgorithm algorithm =
            new SimulatedAnnealingPowerCeilingAlgorithm(config, tasks, vms, powerCapWatts, hosts);
        lastBestSolution = algorithm.run();
        lastStatistics = algorithm.getStatistics();

        List<SchedulingSolution> archive = algorithm.getArchive();
        lastParetoFront = new ParetoFront(
            archive,
            getObjectiveNames(),
            getObjectiveMinimization()
        );

        if (config.isVerboseLogging()) {
            int feasible = algorithm.getFeasibleArchive().size();
            System.out.println("[SA-Dominance-PC] Archive: " + lastParetoFront.size()
                + " non-dominated solution(s), " + feasible + " feasible at cap "
                + powerCapWatts + " W");
        }

        return lastParetoFront;
    }

    @Override
    public ParetoFront getLastParetoFront() { return lastParetoFront; }

    @Override
    public Map<Task, VM> applySolution(SchedulingSolution solution, List<Task> tasks,
                                        List<VM> vms, long currentTime) {
        Map<Task, VM> assignments = new LinkedHashMap<>();
        if (solution == null || tasks.isEmpty() || vms.isEmpty()) return assignments;

        List<Task> taskList = new ArrayList<>(tasks);
        List<VM> vmList = new ArrayList<>(vms);

        int[] taskAssignment = solution.getTaskAssignment();
        List<List<Integer>> vmTaskOrder = solution.getVmTaskOrder();

        for (int vmIdx = 0; vmIdx < vmTaskOrder.size(); vmIdx++) {
            if (vmIdx >= vmList.size()) continue;
            VM vm = vmList.get(vmIdx);
            if (vm == null) continue;

            for (int taskIdx : vmTaskOrder.get(vmIdx)) {
                if (taskIdx < 0 || taskIdx >= taskList.size()) continue;
                Task task = taskList.get(taskIdx);
                if (task == null || taskAssignment[taskIdx] != vmIdx) continue;
                task.assignToVM(vm.getId(), currentTime);
                vm.assignTask(task);
                assignments.put(task, vm);
            }
        }
        return assignments;
    }

    @Override
    public List<String> getObjectiveNames() {
        List<String> names = new ArrayList<>();
        for (SchedulingObjective obj : config.getObjectives()) names.add(obj.getName());
        return names;
    }

    @Override
    public boolean[] getObjectiveMinimization() {
        List<SchedulingObjective> objectives = config.getObjectives();
        boolean[] mins = new boolean[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) mins[i] = objectives.get(i).isMinimization();
        return mins;
    }

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        if (candidateVMs == null || candidateVMs.isEmpty()) return Optional.empty();
        return Optional.of(candidateVMs.get(0));
    }

    @Override
    public Map<Task, VM> assignAll(List<Task> tasks, List<VM> vms, long currentTime) {
        Map<Task, VM> assignments = new LinkedHashMap<>();
        if (tasks.isEmpty() || vms.isEmpty()) return assignments;

        ParetoFront front = optimizeAndGetParetoFront(tasks, vms);
        if (front.isEmpty()) {
            if (lastBestSolution != null) {
                return applySolution(lastBestSolution, tasks, vms, currentTime);
            }
            System.err.println("[SA-Dominance-PC] Optimization produced no solutions");
            return assignments;
        }

        SchedulingSolution selected = front.getKneePoint();
        if (selected == null) selected = front.getSolutions().get(0);

        return applySolution(selected, tasks, vms, currentTime);
    }

    @Override
    public String getStrategyName() {
        return "Simulated Annealing Dominance-PowerCeiling (" + (long) powerCapWatts + "W)";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("SA with constrained-domination archive (Deb rules). ");
        sb.append(config.getCoolingSchedule().getName()).append(" cooling. ");
        if (config.isWeightedSum()) {
            sb.append("Weighted-sum objectives: ");
            for (int i = 0; i < config.getObjectives().size(); i++) {
                if (i > 0) sb.append(", ");
                SchedulingObjective obj = config.getObjectives().get(i);
                sb.append(obj.getName()).append(":")
                  .append(String.format("%.2f", config.getObjectiveWeight(obj)));
            }
        } else {
            sb.append("Single-objective: ").append(config.getPrimaryObjective().getName());
        }
        sb.append(". P_cap = ").append(powerCapWatts).append(" W");
        return sb.toString();
    }
}
