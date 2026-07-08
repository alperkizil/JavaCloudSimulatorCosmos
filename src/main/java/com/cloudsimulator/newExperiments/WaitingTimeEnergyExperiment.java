package com.cloudsimulator.newExperiments;

import com.cloudsimulator.observer.ExperimentSpec;

/**
 * Avg. Waiting Time vs Energy — clean reproduction of
 * {@code WaitingTimeExperimentRunner}, on the observer/analyzer/reporter back-end.
 *
 * <p>Everything you'd tweak lives right here: the algorithm list, the
 * hyperparameters, and the infrastructure. Defaults are identical to the legacy
 * runner.</p>
 */
public final class WaitingTimeEnergyExperiment {

    public static void main(String[] args) {
        // 1. Algorithms — the 7 front-producing metaheuristics (same set across all
        //    three studies). Greedy heuristics are excluded (WorkloadAware/EnergyAware
        //    seed the metaheuristics only); the weighted single-objective GA/SA are
        //    excluded too (they emit a single point, not a Pareto set).
        String[] algorithms = {
            "GA_WaitingTime_Dominance", "GA_Energy_Dominance",
            "SA_WaitingTime_Dominance", "SA_Energy_Dominance",
            "NSGA-II", "SPEA-II", "AMOSA"
        };

        // 2. Algorithm hyperparameters — identical to the legacy runner; edit to deviate.
        //    Detailed console output is off; the campaign shows a one-line progress bar
        //    per run instead. Set params.verboseLogging = true to restore it.
        AlgorithmParameters params = AlgorithmParameters.defaults();

        // 3. Infrastructure — datacenter, hosts, VMs, user, workloads, scenarios, seeds.
        ExperimentConfig infra = ExperimentConfig.defaults();

        // 4. The study (objective pair) + run.
        ExperimentSpec spec = ExperimentSpec.waitingTime("WaitingTimeVsEnergy");
        new CampaignRunner(spec, PrimaryObjective.WAITING_TIME, infra, params, algorithms).run();
    }
}
