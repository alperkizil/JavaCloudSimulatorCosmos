package com.cloudsimulator.observer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parity test: verifies that {@link ParetoAnalyzer}'s fixed-reference indicators
 * reproduce the {@code scripts/recompute_hv.py} oracle.
 *
 * <p>For each scenario it loads {@code scenario_N_pareto_graph_data.csv}, takes
 * the {@code Universal_Pareto} rows as the universal front, computes union
 * ideal/nadir over <em>all</em> rows (matching the Python script), then for each
 * (Algorithm, Seed) group computes {@code HV_fixed}, {@code EpsPlus} and
 * {@code ParetoContribution_pct} and compares them against
 * {@code scenario_N_quality_indicators.csv}.</p>
 *
 * <p>Tolerances: HV {@code < 1e-3}, Eps+ {@code < 1e-6}, contribution {@code < 1e-6}.</p>
 *
 * <p>Usage: {@code java ... ParetoAnalyzerParityTest [reportsDir]} (default
 * {@code reports/new}).</p>
 */
public class ParetoAnalyzerParityTest {

    private static final double HV_TOL = 1e-3;
    private static final double EPS_TOL = 1e-6;
    private static final double CONTRIB_TOL = 1e-6;
    private static final int MAX_SCENARIOS = 6;

    private static int failures = 0;
    private static int comparisons = 0;

    public static void main(String[] args) throws IOException {
        String reportsDir = args.length > 0 ? args[0] : "reports/new";
        System.out.println("=== ParetoAnalyzer parity test vs recompute_hv.py oracle ===");
        System.out.println("Reports dir: " + reportsDir + "\n");

        int scenariosChecked = 0;
        for (int s = 1; s <= MAX_SCENARIOS; s++) {
            Path graph = Paths.get(reportsDir, "scenario_" + s + "_pareto_graph_data.csv");
            Path oracle = Paths.get(reportsDir, "scenario_" + s + "_quality_indicators.csv");
            if (!Files.exists(graph) || !Files.exists(oracle)) {
                continue;
            }
            scenariosChecked++;
            checkScenario(s, graph, oracle);
        }

        System.out.println();
        if (scenariosChecked == 0) {
            System.out.println("FAIL: no scenario CSVs found under " + reportsDir);
            System.exit(1);
        }
        System.out.println("Scenarios checked: " + scenariosChecked + ", comparisons: " + comparisons);
        if (failures == 0) {
            System.out.println("=== Parity test PASSED ===");
        } else {
            System.out.println("=== Parity test FAILED: " + failures + " mismatch(es) ===");
            System.exit(1);
        }
    }

    private static void checkScenario(int scenario, Path graphFile, Path oracleFile) throws IOException {
        System.out.println("--- Scenario " + scenario + " ---");

        List<String> graphLines = Files.readAllLines(graphFile);
        List<double[]> universal = new ArrayList<>();
        List<double[]> allPoints = new ArrayList<>();
        // Preserve insertion order of (Algorithm|Seed) groups.
        Map<String, List<double[]>> runGroups = new LinkedHashMap<>();

        for (int i = 1; i < graphLines.size(); i++) { // skip header
            String line = graphLines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] tok = line.split(",");
            if (tok.length < 4) {
                continue;
            }
            String algorithm = tok[0].trim();
            String seed = tok[1].trim();
            double x = Double.parseDouble(tok[2].trim());
            double y = Double.parseDouble(tok[3].trim());
            double[] point = new double[] {x, y};

            allPoints.add(point); // union bounds use ALL rows, incl. Universal_Pareto
            if (algorithm.equals("Universal_Pareto")) {
                universal.add(point);
            } else {
                runGroups.computeIfAbsent(algorithm + "|" + seed, k -> new ArrayList<>()).add(point);
            }
        }

        double[] bounds = ParetoAnalyzer.unionBounds(allPoints);

        // Compute indicators per run group.
        Map<String, double[]> computed = new LinkedHashMap<>(); // key -> {hvFixed, epsPlus, contribPct}
        for (Map.Entry<String, List<double[]>> entry : runGroups.entrySet()) {
            ParetoAnalyzer.FixedIndicators fixed =
                ParetoAnalyzer.computeFixedIndicators(entry.getValue(), universal, bounds);
            computed.put(entry.getKey(),
                new double[] {fixed.hvFixed, fixed.epsilonPlus, fixed.contributionPct});
        }

        // Compare against the oracle.
        List<String> oracleLines = Files.readAllLines(oracleFile);
        // Header: Scenario,Algorithm,Seed,HV_fixed,EpsPlus,ParetoContribution_pct,nSolutions,ref_x_raw,ref_y_raw
        for (int i = 1; i < oracleLines.size(); i++) {
            String line = oracleLines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] tok = line.split(",");
            if (tok.length < 6) {
                continue;
            }
            String algorithm = tok[1].trim();
            String seed = tok[2].trim();
            double oracleHv = Double.parseDouble(tok[3].trim());
            double oracleEps = parseMaybeNaN(tok[4].trim());
            double oracleContrib = parseMaybeNaN(tok[5].trim());

            String key = algorithm + "|" + seed;
            double[] mine = computed.get(key);
            if (mine == null) {
                fail(scenario, key, "no computed group for oracle row");
                continue;
            }
            compare(scenario, key, "HV_fixed", oracleHv, mine[0], HV_TOL);
            compare(scenario, key, "EpsPlus", oracleEps, mine[1], EPS_TOL);
            compare(scenario, key, "Contribution", oracleContrib, mine[2], CONTRIB_TOL);
        }
    }

    private static double parseMaybeNaN(String s) {
        if (s.isEmpty() || s.equalsIgnoreCase("nan")) {
            return Double.NaN;
        }
        return Double.parseDouble(s);
    }

    private static void compare(int scenario, String key, String metric,
                                double expected, double actual, double tol) {
        comparisons++;
        boolean ok;
        if (Double.isNaN(expected) || Double.isNaN(actual)) {
            ok = Double.isNaN(expected) && Double.isNaN(actual);
        } else {
            ok = Math.abs(expected - actual) <= tol;
        }
        if (!ok) {
            System.out.printf("  FAIL [s%d %s] %s: oracle=%.10f mine=%.10f (|d|=%.3e > %.0e)%n",
                scenario, key, metric, expected, actual, Math.abs(expected - actual), tol);
            failures++;
        }
    }

    private static void fail(int scenario, String key, String msg) {
        System.out.println("  FAIL [s" + scenario + " " + key + "] " + msg);
        failures++;
    }
}
