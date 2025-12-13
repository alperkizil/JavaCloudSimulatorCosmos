package com.cloudsimulator.PlacementStrategy.task.metaheuristic.moea;

import org.moeaframework.algorithm.EpsilonMOEA;
import org.moeaframework.core.Algorithm;

/**
 * MOEA Framework ε-MOEA implementation for task scheduling.
 *
 * ε-MOEA (Epsilon-dominance MOEA) is a steady-state evolutionary algorithm
 * that uses epsilon-dominance for archive maintenance:
 * - ε-dominance: A relaxed dominance relation that divides objective space
 *   into hyperboxes of size ε
 * - ε-box archive: Maintains at most one solution per hyperbox, ensuring
 *   a well-distributed Pareto front approximation
 * - Steady-state evolution: Updates population one solution at a time
 *
 * Key advantage: The epsilon values control the resolution of the Pareto
 * front, providing guaranteed spacing and bounded archive size.
 *
 * Reference: Deb et al. (2003) "A Fast Multi-Objective Evolutionary
 * Algorithm for Finding Well-Spread Pareto-Optimal Solutions"
 *
 * This implementation wraps MOEA Framework's ε-MOEA and uses our
 * simulator's RandomGenerator seed for experiment reproducibility.
 */
public class MOEA_EpsilonMOEA extends AbstractMOEAStrategy {

    /**
     * Creates a new MOEA_EpsilonMOEA strategy with the given configuration.
     *
     * @param config Configuration parameters (must include epsilon values)
     */
    public MOEA_EpsilonMOEA(MOEAConfiguration config) {
        super(config);
    }

    /**
     * Creates a MOEA_EpsilonMOEA strategy with default configuration.
     * Default epsilons: [10.0, 0.001] for [Makespan(seconds), Energy(kWh)]
     */
    public MOEA_EpsilonMOEA() {
        super(MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.EPSILON_MOEA)
            .epsilons(10.0, 0.001) // 10 second resolution, 0.001 kWh resolution
            .build());
    }

    /**
     * Creates a MOEA_EpsilonMOEA strategy with custom epsilon values.
     *
     * @param epsilons Epsilon values for each objective
     */
    public MOEA_EpsilonMOEA(double... epsilons) {
        super(MOEAConfiguration.builder()
            .algorithm(MOEAConfiguration.Algorithm.EPSILON_MOEA)
            .epsilons(epsilons)
            .build());
    }

    @Override
    protected String getAlgorithmName() {
        return "MOEA_EpsilonMOEA";
    }

    @Override
    public String getStrategyName() {
        return "MOEA_EpsilonMOEA";
    }

    @Override
    protected Algorithm createAlgorithm(TaskSchedulingProblem problem) {
        // Get epsilon values from configuration
        double[] epsilons = config.getEpsilons();

        // Ensure we have epsilons for all objectives
        if (epsilons == null || epsilons.length != problem.getNumberOfObjectives()) {
            // Create default epsilons if not properly configured
            epsilons = new double[problem.getNumberOfObjectives()];
            for (int i = 0; i < epsilons.length; i++) {
                // Default: Makespan in seconds, Energy in kWh
                epsilons[i] = i == 0 ? 10.0 : 0.001;
            }
        }

        // Create ε-MOEA algorithm with epsilon archive
        EpsilonMOEA algorithm = new EpsilonMOEA(problem, epsilons);

        // Configure population size
        algorithm.setInitialPopulationSize(config.getPopulationSize());

        return algorithm;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("MOEA Framework ε-MOEA: Epsilon-dominance MOEA ")
          .append("with ε-box archive for controlled Pareto front resolution. ")
          .append("Population=").append(config.getPopulationSize())
          .append(", Epsilons=").append(java.util.Arrays.toString(config.getEpsilons()))
          .append(", MaxEvaluations=").append(config.getMaxEvaluations())
          .append(", Selection=").append(config.getSolutionSelection());
        return sb.toString();
    }
}
