package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ConstrainedNonDominatedArchive;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.PowerCeilingEnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;

import org.moeaframework.core.Solution;
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
    private final ConstrainedNonDominatedArchive publicationArchive;

    public PowerCeilingSchedulingProblem(List<Task> tasks,
                                         List<VM> vms,
                                         List<SchedulingObjective> objectives,
                                         RepairOperator repairOperator,
                                         List<Host> hosts,
                                         double powerCapWatts) {
        this(tasks, vms, objectives, repairOperator, hosts, powerCapWatts, null);
    }

    /**
     * Variant that additionally publishes every genuinely-evaluated solution
     * (with its constraint violation) to an external
     * {@link ConstrainedNonDominatedArchive} — the same Deb-rule archive the
     * GA/SA PowerCeiling-with-Dominance arms publish, so the constrained MOEA
     * arms expose their full evaluation stream instead of only the final
     * search-state snapshot. Passive; pass {@code null} (or use the 6-arg
     * constructor) for the previous behavior.
     */
    public PowerCeilingSchedulingProblem(List<Task> tasks,
                                         List<VM> vms,
                                         List<SchedulingObjective> objectives,
                                         RepairOperator repairOperator,
                                         List<Host> hosts,
                                         double powerCapWatts,
                                         ConstrainedNonDominatedArchive publicationArchive) {
        // numTasks assignment variables + 1 dispatch-order permutation
        // (see TaskSchedulingProblem for the encoding)
        super(tasks.size() + 1, objectives.size(), 1);
        this.tasks = tasks;
        this.vms = vms;
        this.objectives = objectives;
        this.repairOperator = repairOperator;
        this.powerCapWatts = powerCapWatts;
        this.meter = new PowerCeilingEnergyObjective(powerCapWatts);
        if (hosts != null) this.meter.setHosts(hosts);
        this.publicationArchive = publicationArchive;
    }

    @Override
    public String getName() {
        return "CloudTaskSchedulingPowerCeiling";
    }

    @Override
    public Solution newSolution() {
        return TaskSchedulingProblem.newShell(
            tasks.size(), vms.size(), numberOfObjectives, numberOfConstraints);
    }

    @Override
    public void evaluate(Solution solution) {
        SchedulingSolution schedulingSolution = decode(solution);
        repairOperator.repair(schedulingSolution);

        // Evaluate each objective. The values are written onto the decoded
        // SchedulingSolution as well as the MOEA Solution: the publication
        // archive compares candidates by getObjectiveValues(), which otherwise
        // stays at its all-zero constructor default.
        double[] rawValues = new double[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            rawValues[i] = objectives.get(i).evaluate(schedulingSolution, tasks, vms);
            solution.setObjective(i, rawValues[i]);
        }
        schedulingSolution.setObjectiveValues(rawValues);

        // Power-ceiling constraint: violation magnitude in Watts, 0 if feasible.
        // Objectives above may already include EnergyObjective, so use the
        // sweep-only meter path to avoid recomputing energy a second time.
        meter.computePowerProfileOnly(schedulingSolution, tasks, vms);
        double violation = Math.max(0.0, meter.getLastPeakPower() - powerCapWatts);
        solution.setConstraint(0, violation);

        // Store the repaired assignment and ordering back so the genotype
        // stays in sync with the evaluated phenotype.
        TaskSchedulingProblem.encodeInto(solution, schedulingSolution);

        // Offer this genuinely-evaluated candidate (with its violation) to the
        // publication archive, matching the GA/SA PowerCeiling arms' contract.
        if (publicationArchive != null) {
            publicationArchive.offer(schedulingSolution, violation);
        }
    }

    public SchedulingSolution decode(Solution solution) {
        return TaskSchedulingProblem.decodeSolution(
            solution, tasks.size(), vms.size(), objectives.size());
    }

    public Solution encode(SchedulingSolution schedulingSolution) {
        Solution solution = newSolution();
        TaskSchedulingProblem.encodeInto(solution, schedulingSolution);
        return solution;
    }

    public List<Task> getTasks()               { return tasks; }
    public List<VM>   getVms()                 { return vms; }
    public List<SchedulingObjective> getObjectives() { return objectives; }
    public RepairOperator getRepairOperator()  { return repairOperator; }
    public double getPowerCapWatts()           { return powerCapWatts; }
    public PowerCeilingEnergyObjective getMeter() { return meter; }
}
