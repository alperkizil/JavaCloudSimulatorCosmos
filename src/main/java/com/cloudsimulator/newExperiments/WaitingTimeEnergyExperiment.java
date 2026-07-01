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
        // 1. Algorithms — names + order (controls CSV/plot order). Edit freely.
        String[] algorithms = {
            "FirstAvailable", "ShortestQueue", "WorkloadAware", "EnergyAware", "RoundRobin",
            "GA_WaitingTime", "GA_Energy", "SA_WaitingTime", "SA_Energy",
            "GA_WaitingTime_Dominance", "GA_Energy_Dominance",
            "SA_WaitingTime_Dominance", "SA_Energy_Dominance",
            "NSGA-II", "SPEA-II", "AMOSA"
        };

        // 2. Algorithm hyperparameters — identical to the legacy runner; edit to deviate.
        AlgorithmParameters params = AlgorithmParameters.defaults();

        // 3. Infrastructure — datacenter, hosts, VMs, user, workloads, scenarios, seeds.
        ExperimentConfig infra = ExperimentConfig.defaults();

        // 4. The study (objective pair) + run.
        ExperimentSpec spec = ExperimentSpec.waitingTime("WaitingTimeVsEnergy");
        new CampaignRunner(spec, PrimaryObjective.WAITING_TIME, infra, params, algorithms).run();
    }
}
