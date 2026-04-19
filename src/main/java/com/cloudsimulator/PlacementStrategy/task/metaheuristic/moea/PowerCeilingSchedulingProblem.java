package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.PowerCeilingEnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.problem.AbstractProblem;

import java.util.List;

/**
 * Constrained variant of {@link TaskSchedulingProblem}: identical encoding,
 * repair, and objective evaluation, but additionally reports a single
 * constraint — max(0, peakAggregateDcPower − P_cap) — for each evaluated
 * solution.
 *
 * MOEA Framework's NSGA-II (and other algorithms that honor constraint
 * values) automatically apply Deb's constrained-domination rules when
 * numberOfConstraints > 0:
 *   feasible ≻ infeasible
 *   both infeasible → smaller violation wins
 *   both feasible   → standard Pareto on objectives
 *
 * Peak-power measurement reuses the sweep-line in
 * {@link PowerCeilingEnergyObjective}. The meter instance is held by this
 * Problem, evaluated independently of the objective list, so it does not
 * interfere with however the caller chose to configure their objectives.
 */
public class PowerCeilingSchedulingProblem extends AbstractProblem {

    private final List<Task> tasks;
    private final List<VM> vms;
    private final List<SchedulingObjective> objectives;
    private final RepairOperator repairOperator;
    private final PowerCeilingEnergyObjective meter;
    private final double powerCapWatts;

    public PowerCeilingSchedulingProblem(List<Task> tasks,
                                         List<VM> vms,
                                         List<SchedulingObjective> objectives,
                                         RepairOperator repairOperator,
                                         List<Host> hosts,
                                         double powerCapWatts) {
        super(tasks.size(), objectives.size(), 1);
        this.tasks = tasks;
        this.vms = vms;
        this.objectives = objectives;
        this.repairOperator = repairOperator;
        this.powerCapWatts = powerCapWatts;
        this.meter = new PowerCeilingEnergyObjective(powerCapWatts);
        if (hosts != null) this.meter.setHosts(hosts);
    }

    @Override
    public String getName() {
        return "CloudTaskSchedulingPowerCeiling";
    }

    @Override
    public Solution newSolution() {
        Solution solution = new Solution(numberOfVariables, numberOfObjectives, numberOfConstraints);
        for (int i = 0; i < numberOfVariables; i++) {
            solution.setVariable(i, new RealVariable(0, vms.size() - 1));
        }
        return solution;
    }

    @Override
    public void evaluate(Solution solution) {
        SchedulingSolution schedulingSolution = decode(solution);
        repairOperator.repair(schedulingSolution);

        for (int i = 0; i < objectives.size(); i++) {
            double value = objectives.get(i).evaluate(schedulingSolution, tasks, vms);
            solution.setObjective(i, value);
        }

        // Power-ceiling constraint: violation magnitude in Watts, 0 if feasible.
        // Objectives above may already include EnergyObjective, so use the
        // sweep-only meter path to avoid recomputing energy a second time.
        meter.computePowerProfileOnly(schedulingSolution, tasks, vms);
        double violation = Math.max(0.0, meter.getLastPeakPower() - powerCapWatts);
        solution.setConstraint(0, violation);

        // Store the repaired assignment back so encode/decode stays consistent.
        int[] assignment = schedulingSolution.getTaskAssignment();
        for (int i = 0; i < assignment.length; i++) {
            ((RealVariable) solution.getVariable(i)).setValue(assignment[i]);
        }
    }

    public SchedulingSolution decode(Solution solution) {
        SchedulingSolution schedulingSolution = new SchedulingSolution(
            tasks.size(), vms.size(), objectives.size()
        );
        int[] assignment = new int[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            RealVariable var = (RealVariable) solution.getVariable(i);
            int vmIndex = (int) Math.round(var.getValue());
            vmIndex = Math.max(0, Math.min(vms.size() - 1, vmIndex));
            assignment[i] = vmIndex;
        }
        schedulingSolution.setTaskAssignment(assignment);
        schedulingSolution.rebuildTaskOrdering();
        return schedulingSolution;
    }

    public Solution encode(SchedulingSolution schedulingSolution) {
        Solution solution = newSolution();
        int[] assignment = schedulingSolution.getTaskAssignment();
        for (int i = 0; i < assignment.length; i++) {
            ((RealVariable) solution.getVariable(i)).setValue(assignment[i]);
        }
        return solution;
    }

    public List<Task> getTasks()               { return tasks; }
    public List<VM>   getVms()                 { return vms; }
    public List<SchedulingObjective> getObjectives() { return objectives; }
    public RepairOperator getRepairOperator()  { return repairOperator; }
    public double getPowerCapWatts()           { return powerCapWatts; }
    public PowerCeilingEnergyObjective getMeter() { return meter; }
}
