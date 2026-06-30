package com.cloudsimulator.newExperiments;

/**
 * All metaheuristic hyperparameters, mutable so a Main file can tweak them.
 * {@link #defaults()} returns values <b>identical</b> to the constants in the
 * legacy {@code *ExperimentRunner} classes — change a field after calling it to
 * deviate.
 *
 * <p>These three studies share the same default parameter block.</p>
 */
public final class AlgorithmParameters {

    // ---- Shared search budget ----
    public int populationSize = 200;
    public int iterationCount = 40_000;
    public double tiebreakerWeight = 0.001;
    public boolean verboseLogging = true;

    // ---- GA ----
    public double crossoverRate = 0.95;
    public double mutationRate = 0.05;
    public double gaElitePercentage = 0.20;
    public int gaTournamentSize = 5;

    // ---- SA ----
    public boolean saAutoTemperature = true;
    public double saInitialAcceptanceProbability = 0.8;
    public int saTemperatureSampleSize = 100;
    public int saIterationsPerTemp = 200;
    public boolean saReheatEnabled = true;
    public double saReheatFactor = 5.0;
    public int saReheatStagnationThreshold = 15;
    public int saMaxReheats = 3;
    public boolean saAdaptiveIterations = true;
    public int saMinItersPerTemp = 50;
    public int saMaxItersPerTemp = 400;
    public boolean saScaledPerturbation = true;
    public int saMaxPerturbationMutations = 4;
    // AdaptiveCoolingSchedule(coolingRate, minRate, maxRate, lowAccept, highAccept)
    public double saCoolingBaseRate = 0.5;
    public double saCoolingMinRate = 0.15;
    public double saCoolingMaxRate = 0.90;
    public double saCoolingLowAccept = 0.97;
    public double saCoolingHighAccept = 0.995;
    // adaptiveIterationsThresholds(highAccept, lowAccept)
    public double saAdaptiveItersHighThreshold = 0.7;
    public double saAdaptiveItersLowThreshold = 0.1;

    // ---- AMOSA ----
    public double amosaInitialTemperature = 15.0;
    public double amosaAlpha = 0.95;
    public int amosaHardLimit = 50;
    public int amosaSoftLimit = 100;
    public int amosaIterationsPerTemp = 200;
    public int amosaHillClimbingIters = 50;
    public double amosaGamma = 2.0;
    public double amosaMutationRate = 0.05;

    /** Returns parameters identical to the legacy runner constants. */
    public static AlgorithmParameters defaults() {
        return new AlgorithmParameters();
    }
}
