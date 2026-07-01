package com.cloudsimulator.newExperiments;

import com.cloudsimulator.observer.ExperimentSpec;

/**
 * Makespan vs Energy — clean reproduction of
 * {@code ScenarioComparisonExperimentRunner}, on the observer/analyzer/reporter
 * back-end. Differs from the WaitingTime study only in the primary objective and
 * the algorithm label names; infrastructure and parameters are identical.
 */
public final class MakespanEnergyExperiment {

    public static void main(String[] args) {
        // 1. Algorithms — Makespan-flavoured labels.
        String[] algorithms = {
            "FirstAvailable", "ShortestQueue", "WorkloadAware", "EnergyAware", "RoundRobin",
            "GA_Makespan", "GA_Energy", "SA_Makespan", "SA_Energy",
            "GA_Makespan_Dominance", "GA_Energy_Dominance",
            "SA_Makespan_Dominance", "SA_Energy_Dominance",
            "NSGA-II", "SPEA-II", "AMOSA"
        };

        // 2. Hyperparameters — identical defaults.
        AlgorithmParameters params = AlgorithmParameters.defaults();

        // 3. Infrastructure — identical defaults.
        ExperimentConfig infra = ExperimentConfig.defaults();

        // 4. Study + run.
        ExperimentSpec spec = ExperimentSpec.scenarioComparison("MakespanVsEnergy");
        new CampaignRunner(spec, PrimaryObjective.MAKESPAN, infra, params, algorithms).run();
    }
}
