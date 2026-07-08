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
        // 1. Algorithms — the 7 front-producing metaheuristics (same set across all
        //    three studies), Makespan-flavoured. Greedy heuristics excluded
        //    (WorkloadAware/EnergyAware seed the metaheuristics only); the weighted
        //    single-objective GA/SA excluded too (single point, not a Pareto set).
        String[] algorithms = {
            "GA_Makespan_Dominance", "GA_Energy_Dominance",
            "SA_Makespan_Dominance", "SA_Energy_Dominance",
            "NSGA-II", "SPEA-II", "AMOSA"
        };

        // 2. Hyperparameters — identical defaults. Detailed console output is off;
        //    the campaign shows a one-line progress bar per run instead. Set
        //    params.verboseLogging = true to restore the legacy detailed output.
        AlgorithmParameters params = AlgorithmParameters.defaults();

        // 3. Infrastructure — identical defaults.
        ExperimentConfig infra = ExperimentConfig.defaults();

        // 4. Study + run.
        ExperimentSpec spec = ExperimentSpec.scenarioComparison("MakespanVsEnergy");
        new CampaignRunner(spec, PrimaryObjective.MAKESPAN, infra, params, algorithms).run();
    }
}
