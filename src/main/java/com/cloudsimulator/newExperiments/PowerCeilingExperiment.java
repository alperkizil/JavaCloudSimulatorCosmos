package com.cloudsimulator.newExperiments;

import com.cloudsimulator.observer.ExperimentSpec;

/**
 * PowerCeiling: Avg. Waiting Time vs Energy under a power cap — clean reproduction
 * of {@code PowerCeilingWaitingTimeExperimentRunner}, on the
 * observer/analyzer/reporter back-end.
 *
 * <p>Two-phase, driven by {@link CampaignRunner} (triggered by the spec's aux peak):
 * <b>Phase 1</b> runs the 7 base metaheuristics <em>uncapped</em>, captures each
 * solution's <em>coincident</em> Step-8 peak, and derives the power-cap tiers from
 * that distribution ({@link PowerCapCalibrator}: peak percentiles for the
 * ~90/60/30% feasibility targets, pooled globally across scenarios).
 * <b>Phase 2</b> re-runs each base arm as a constrained {@code _PC<tier>} variant
 * under each derived cap. The combined report (uncapped baselines + constrained
 * arms) plus the {@code PowerCapFeasibility} CSVs
 * ({@code feasibility_summary.csv} / {@code pareto_3d_feasible.csv} /
 * {@code pareto_3d_all.csv}) are written against the derived caps.</p>
 *
 * <p>Everything you'd tweak lives right here: the algorithm list, the
 * hyperparameters, and the infrastructure.</p>
 */
public final class PowerCeilingExperiment {

    public static void main(String[] args) {
        // 1. Algorithms — the 7 base metaheuristics. They run uncapped in Phase 1
        //    (to derive the caps), then the runner re-runs each as a constrained
        //    _PC<tier> variant under every derived cap in Phase 2. No fixed cap
        //    values are baked in — they come from the observed peak distribution.
        String[] algorithms = {
            // Dominance-archive variants of GA/SA
            "GA_WaitingTime_Dominance", "GA_Energy_Dominance",
            "SA_WaitingTime_Dominance", "SA_Energy_Dominance",
            // Multi-objective metaheuristics
            "NSGA-II", "SPEA-II", "AMOSA"
        };

        // 2. Algorithm hyperparameters — identical to the legacy runner; edit to deviate.
        //    Detailed console output is off; the campaign shows a one-line progress bar
        //    per run instead. Set params.verboseLogging = true to restore it.
        AlgorithmParameters params = AlgorithmParameters.defaults();

        // 3. Infrastructure — datacenter, hosts, VMs, user, workloads, scenarios, seeds.
        ExperimentConfig infra = ExperimentConfig.defaults();

        // 4. The study (objective pair + aux coincident peak + cap feasibility) + run.
        ExperimentSpec spec = ExperimentSpec.powerCeiling("PowerCeilingWaitingTimeVsEnergy");
        new CampaignRunner(spec, PrimaryObjective.WAITING_TIME, infra, params, algorithms).run();
    }
}
