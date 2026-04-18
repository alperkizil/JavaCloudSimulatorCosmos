package com.cloudsimulator.FinalExperiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runs a sequence of Python post-processing scripts, each invoked with the
 * reports directory as its single argument. Scripts are executed in the given
 * order; a failure in one is reported but does not abort the sequence.
 *
 * Typical usage at the end of an experiment runner:
 * <pre>
 *   PythonPostProcessor.run(REPORTS_DIR,
 *       "scripts/recompute_hv.py",
 *       "scripts/plot_scenario_pareto.py",
 *       "scripts/statistical_tests.py"
 *   );
 * </pre>
 */
public final class PythonPostProcessor {

    private PythonPostProcessor() {}

    public static void run(String reportsDir, String... scriptRelativePaths) {
        String[] pythonCommands = detectPythonCommands();
        for (String rel : scriptRelativePaths) {
            Path scriptPath = Paths.get(rel);
            System.out.println();
            if (!Files.exists(scriptPath)) {
                System.out.println("Script not found: " + scriptPath + "  (skipping)");
                System.out.println("  Run manually: python3 " + scriptPath + " " + reportsDir);
                continue;
            }
            System.out.println("Executing: " + scriptPath);
            if (!runOne(pythonCommands, scriptPath, reportsDir)) {
                System.out.println("  Could not run " + scriptPath + " automatically.");
                System.out.println("  Run manually: python3 " + scriptPath + " " + reportsDir);
            }
        }
    }

    private static String[] detectPythonCommands() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return new String[]{"python", "python3"};
        }
        return new String[]{"python3", "python"};
    }

    private static boolean runOne(String[] pythonCommands, Path scriptPath, String reportsDir) {
        for (String pythonCmd : pythonCommands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd, scriptPath.toString(), reportsDir);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("  [Python] " + line);
                    }
                }
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    System.out.println("  Done: " + scriptPath);
                    return true;
                }
                System.out.println("  " + scriptPath + " exited with code " + exitCode);
                return false;
            } catch (IOException e) {
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
