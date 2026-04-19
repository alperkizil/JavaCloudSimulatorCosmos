package com.cloudsimulator.PlacementStrategy.task.metaheuristic;

import com.cloudsimulator.PlacementStrategy.task.MultiObjectiveTaskSchedulingStrategy;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generational GA facade that exposes a non-dominated archive as a Pareto front.
 *
 * The underlying search is still single-objective (scalar fitness or weighted sum),
 * so selection is unchanged. An external archive records every non-dominated
 * objective vector encountered across the run — including injected heuristic
 * seeds — and is returned as a Pareto front so the experiment runner can simulate
 * every surviving candidate via {@link MultiObjectiveTaskSchedulingStrategy}.
 *
 * This is a deliberate duplicate of {@link GenerationalGATaskSchedulingStrategy}
 * rather than a replacement — the original strategy keeps its single-solution
 * contract for baselines that expect it.
 */
public class GenerationalGAwithDominanceStrategy implements MultiObjectiveTaskSchedulingStrategy {

    private final GAConfiguration config;

    private ParetoFront lastParetoFront;
    private SchedulingSolution lastBestSolution;
    private GAStatistics lastStatistics;

    public GenerationalGAwithDominanceStrategy(GAConfiguration config) {
        this.config = config;
    }

    public GAConfiguration getConfiguration() {
        return config;
    }

    public SchedulingSolution getLastBestSolution() {
        return lastBestSolution;
    }

    public GAStatistics getLastStatistics() {
        return lastStatistics;
    }

    @Override
    public ParetoFront optimizeAndGetParetoFront(List<Task> tasks, List<VM> vms) {
        if (tasks.isEmpty() || vms.isEmpty()) {
            lastParetoFront = new ParetoFront(getObjectiveNames(), getObjectiveMinimization());
            return lastParetoFront;
        }

        GenerationalGAAlgorithm algorithm = new GenerationalGAAlgorithm(config, tasks, vms);
        lastBestSolution = algorithm.run();
        lastStatistics = algorithm.getStatistics();

        List<SchedulingSolution> archive = algorithm.getArchive();
        lastParetoFront = new ParetoFront(
            archive,
            getObjectiveNames(),
            getObjectiveMinimization()
        );

        if (config.isVerboseLogging()) {
            System.out.println("[GA-Dominance] Archive contains " + lastParetoFront.size()
                + " non-dominated solution(s)");
        }

        return lastParetoFront;
    }

    @Override
    public ParetoFront getLastParetoFront() {
        return lastParetoFront;
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
        List<String> names = new ArrayList<>();
        for (SchedulingObjective obj : config.getObjectives()) {
            names.add(obj.getName());
        }
        return names;
    }

    @Override
    public boolean[] getObjectiveMinimization() {
        List<SchedulingObjective> objectives = config.getObjectives();
        boolean[] mins = new boolean[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            mins[i] = objectives.get(i).isMinimization();
        }
        return mins;
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

        ParetoFront front = optimizeAndGetParetoFront(tasks, vms);
        if (front.isEmpty()) {
            // Fall back to the scalar best if the archive somehow came up empty.
            if (lastBestSolution != null) {
                return applySolution(lastBestSolution, tasks, vms, currentTime);
            }
            System.err.println("[GA-Dominance] Optimization produced no solutions");
            return assignments;
        }

        SchedulingSolution selected = front.getKneePoint();
        if (selected == null) {
            selected = front.getSolutions().get(0);
        }

        return applySolution(selected, tasks, vms, currentTime);
    }

    @Override
    public String getStrategyName() {
        return "Generational GA (Dominance Archive)";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Generational Genetic Algorithm with non-dominated archive. ");

        if (config.isWeightedSum()) {
            sb.append("Weighted-sum search with objectives: ");
            for (int i = 0; i < config.getObjectives().size(); i++) {
                if (i > 0) sb.append(", ");
                SchedulingObjective obj = config.getObjectives().get(i);
                sb.append(obj.getName());
                sb.append(":");
                sb.append(String.format("%.2f", config.getObjectiveWeight(obj)));
            }
        } else {
            sb.append("Single-objective search: ");
            sb.append(config.getPrimaryObjective().getName());
        }

        sb.append(". Archive records non-dominated objective vectors across the run");
        if (!config.getSeedAssignments().isEmpty()) {
            sb.append(" (").append(config.getSeedAssignments().size()).append(" heuristic seed(s) injected)");
        }

        return sb.toString();
    }
}
