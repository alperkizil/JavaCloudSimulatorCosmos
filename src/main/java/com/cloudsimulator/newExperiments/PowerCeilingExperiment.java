package com.cloudsimulator.newExperiments;

import com.cloudsimulator.observer.ExperimentSpec;

/**
 * PowerCeiling: Avg. Waiting Time vs Energy under a power cap — clean reproduction
 * of {@code PowerCeilingWaitingTimeExperimentRunner}, on the
 * observer/analyzer/reporter back-end.
 *
 * <p>The [WaitingTime, Energy] fronts are produced by the same path as the
 * WaitingTime study, so they byte-match the legacy PowerCeiling runner. On top of
 * that, each solution's <em>coincident</em> Step-8 peak power is captured and a
 * {@code PowerCapFeasibility} report is emitted against {220, 190, 120} kW
 * ({@code feasibility_summary.csv} / {@code pareto_3d_feasible.csv} /
 * {@code pareto_3d_all.csv}).</p>
 *
 * <p>The capped optimizers ({@code *_PC_*}) keep their intrinsic search-time power
 * constraint; only the reported peak/feasibility uses the coincident value — so a
 * solution the optimizer believed feasible may read as over-cap once judged on the
 * real coincident peak (an honest finding).</p>
 *
 * <p>Everything you'd tweak lives right here: the algorithm list, the
 * hyperparameters, and the infrastructure. Defaults are identical to the legacy
 * runner.</p>
 */
public final class PowerCeilingExperiment {

    public static void main(String[] args) {
        // 1. Algorithms — names + order (controls CSV/plot order). Matches the legacy
        //    PowerCeiling default set exactly: 7 base + 16 constrained (_PC_) variants.
        String[] algorithms = {
            // Dominance-archive variants of GA/SA
            "GA_WaitingTime_Dominance", "GA_Energy_Dominance",
            "SA_WaitingTime_Dominance", "SA_Energy_Dominance",
            // Multi-objective metaheuristics
            "NSGA-II", "SPEA-II", "AMOSA",
            // Constrained-domination MOEA arms at the calibrated cap tiers
            "NSGA-II_PC_190kW", "NSGA-II_PC_120kW",
            "SPEA-II_PC_190kW", "SPEA-II_PC_120kW",
            "AMOSA_PC_190kW",   "AMOSA_PC_120kW",
            // Constrained-domination archive variants — native GA/SA
            "GA_WaitingTime_Dominance_PC_190kW", "GA_WaitingTime_Dominance_PC_120kW",
            "GA_Energy_Dominance_PC_190kW",      "GA_Energy_Dominance_PC_120kW",
            "SA_WaitingTime_Dominance_PC_190kW", "SA_WaitingTime_Dominance_PC_120kW",
            "SA_Energy_Dominance_PC_190kW",      "SA_Energy_Dominance_PC_120kW",
            // Runtime admission-control decorator (wraps WorkloadAware)
            "WorkloadAware_Admission_PC_190kW",  "WorkloadAware_Admission_PC_120kW"
        };

        // 2. Algorithm hyperparameters — identical to the legacy runner; edit to deviate.
        AlgorithmParameters params = AlgorithmParameters.defaults();

        // 3. Infrastructure — datacenter, hosts, VMs, user, workloads, scenarios, seeds.
        ExperimentConfig infra = ExperimentConfig.defaults();

        // 4. The study (objective pair + aux coincident peak + cap feasibility) + run.
        ExperimentSpec spec = ExperimentSpec.powerCeiling("PowerCeilingWaitingTimeVsEnergy");
        new CampaignRunner(spec, PrimaryObjective.WAITING_TIME, infra, params, algorithms).run();
    }
}
