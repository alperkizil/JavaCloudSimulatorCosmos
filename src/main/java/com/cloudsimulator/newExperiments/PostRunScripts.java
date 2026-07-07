package com.cloudsimulator.newExperiments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Runs the Python post-processing pipeline from {@code scripts/} against a
 * finished experiment's results directory:
 *
 * <ol>
 *   <li>{@code recompute_hv.py} — scenario-fixed reference-point quality
 *       indicators (HV_fixed), which the plotters prefer when present;</li>
 *   <li>{@code plot_scenario_pareto.py} — the publication PNG figures;</li>
 *   <li>{@code statistical_tests.py} — Friedman/Nemenyi + Wilcoxon summary;</li>
 *   <li>{@code plot_power_ceiling.py} — only when the directory contains
 *       {@code feasibility_summary.csv} (PowerCeiling studies);</li>
 *   <li>{@code generate_interactive_report.py} — the self-contained
 *       {@code interactive_report.html}.</li>
 * </ol>
 *
 * <p><b>Never fails the experiment.</b> A missing Python interpreter, missing
 * matplotlib/pandas/numpy, or a script error only logs a warning — the CSVs are
 * already on disk and every script can be re-run manually later, e.g.:
 * {@code java -cp "target/classes:lib/*"
 * com.cloudsimulator.newExperiments.PostRunScripts results/<experimentId>}.</p>
 */
public final class PostRunScripts {

    /** Scripts folder, resolved relative to the working directory (repo root). */
    private static final String SCRIPTS_DIR = "scripts";

    /** Hard per-script timeout so a wedged interpreter cannot hang a campaign. */
    private static final long TIMEOUT_MINUTES = 30;

    private PostRunScripts() {
    }

    /**
     * Runs the applicable scripts against {@code resultsDir}. Best-effort:
     * failures are logged and the remaining scripts still run. Never throws —
     * a campaign that has already written its CSVs must not die here.
     */
    public static void runAll(Path resultsDir) {
        try {
            runAllUnsafe(resultsDir);
        } catch (RuntimeException e) {
            warn("unexpected error while generating reports: " + e);
        }
    }

    private static void runAllUnsafe(Path resultsDir) {
        System.out.println();
        System.out.println("=== Post-run reports (Python) ===");
        Path scripts = Paths.get(SCRIPTS_DIR);
        if (!Files.isDirectory(scripts)) {
            warn("scripts/ directory not found under " + Paths.get("").toAbsolutePath()
                + " — skipping report generation. Run the scripts manually from the repo root.");
            return;
        }
        String python = resolvePython();
        if (python == null) {
            warn("No python3/python interpreter on PATH — skipping report generation. "
                + "Install Python 3 with matplotlib, pandas and numpy, then run: "
                + "python3 scripts/generate_interactive_report.py " + resultsDir);
            return;
        }

        List<String> names = new ArrayList<>();
        names.add("recompute_hv.py");
        names.add("plot_scenario_pareto.py");
        names.add("statistical_tests.py");
        if (Files.exists(resultsDir.resolve("feasibility_summary.csv"))) {
            names.add("plot_power_ceiling.py");
        }
        names.add("generate_interactive_report.py");

        int ok = 0;
        for (String name : names) {
            if (Thread.currentThread().isInterrupted()) {
                warn("thread interrupted — skipping the remaining report scripts.");
                break;
            }
            Path script = scripts.resolve(name);
            if (!Files.exists(script)) {
                warn(name + " not found in " + SCRIPTS_DIR + "/ — skipped.");
                continue;
            }
            if (runOne(python, script, resultsDir)) {
                ok++;
            }
        }
        System.out.printf(Locale.US, "Post-run reports: %d/%d scripts succeeded.%n", ok, names.size());
        Path html = resultsDir.resolve("interactive_report.html");
        if (Files.exists(html)) {
            System.out.println("Interactive report: " + html.toAbsolutePath());
        }
    }

    /** Runs one script, streaming its output to this console. */
    private static boolean runOne(String python, Path script, Path resultsDir) {
        System.out.println("--- " + python + " " + script + " " + resultsDir + " ---");
        Process p = null;
        try {
            p = new ProcessBuilder(python, script.toString(), resultsDir.toString())
                .inheritIO()
                .start();
            if (!p.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                warn(script.getFileName() + " timed out after " + TIMEOUT_MINUTES + " minutes.");
                return false;
            }
            int code = p.exitValue();
            if (code != 0) {
                warn(script.getFileName() + " exited with code " + code + ".");
                return false;
            }
            return true;
        } catch (IOException e) {
            warn(script.getFileName() + " could not be started: " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            if (p != null) {
                p.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            warn(script.getFileName() + " interrupted; child process killed.");
            return false;
        }
    }

    /** First working interpreter out of {@code python3}, {@code python}; null if none. */
    private static String resolvePython() {
        for (String candidate : new String[] {"python3", "python"}) {
            try {
                Process p = new ProcessBuilder(candidate, "--version")
                    .redirectErrorStream(true)
                    .start();
                if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
                p.destroyForcibly();
            } catch (IOException e) {
                // not on PATH — try the next candidate
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static void warn(Object message) {
        System.err.println("[PostRunScripts] WARNING: " + message);
    }

    /** Manual entry point: regenerate all reports for an existing results directory. */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java com.cloudsimulator.newExperiments.PostRunScripts <resultsDir>");
            System.exit(1);
        }
        Path dir = Paths.get(args[0]);
        if (!Files.isDirectory(dir)) {
            System.err.println("Not a directory: " + dir);
            System.exit(1);
        }
        runAll(dir);
    }
}
