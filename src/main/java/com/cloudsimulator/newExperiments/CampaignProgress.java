package com.cloudsimulator.newExperiments;

import java.io.Console;
import java.io.PrintStream;
import java.util.Locale;

/**
 * Single-line console progress display for a campaign. Each run paints one
 * status line — experiment name, scenario, algorithm label, seed, and the
 * remaining/total run count — plus a completion bar:
 *
 * <pre>[############............]  84/210 done | MakespanVsEnergy | S1:Balanced | SA_Energy_Dominance | seed=204 | remaining 126/210</pre>
 *
 * <p>Writes to the {@link PrintStream} captured at construction, so the bar
 * stays visible while {@link CampaignRunner} silences {@code System.out} for
 * the duration of each quiet run. On an interactive terminal the line is
 * redrawn in place ({@code \r}); when output is redirected to a file, or when
 * verbose logging is on, every update goes on its own plain line so logs stay
 * readable.</p>
 */
final class CampaignProgress {

    private static final int BAR_WIDTH = 24;

    private final PrintStream out;
    private final boolean inPlace;
    private final String experimentName;
    private int total;
    private int done;
    private int lastLineLength;

    /**
     * @param out            stream to draw on (capture {@code System.out} before
     *                       any redirection)
     * @param experimentName display name of the study (e.g. the experiment id)
     * @param totalRuns      planned number of runs; adjustable via {@link #setTotal}
     * @param inPlaceAllowed false forces plain-line mode (e.g. verbose logging
     *                       would interleave with an in-place bar)
     */
    CampaignProgress(PrintStream out, String experimentName, int totalRuns, boolean inPlaceAllowed) {
        this.out = out;
        this.experimentName = experimentName;
        this.total = totalRuns;
        this.inPlace = inPlaceAllowed && stdoutIsTerminal();
    }

    /** Re-plans the total run count (PowerCeiling Phase 2 is sized once caps are derived). */
    void setTotal(int totalRuns) {
        this.total = totalRuns;
    }

    /** Paints the bar for the run that is about to execute. */
    void beginRun(int scenarioNum, String scenarioName, String algorithm, long seed) {
        int remaining = total - done;
        paint(String.format(Locale.US, "%s %d/%d done | %s | S%d:%s | %s | seed=%d | remaining %d/%d",
            bar(), done, total, experimentName, scenarioNum, scenarioName, algorithm, seed,
            remaining, total));
    }

    /** Marks the run painted by the last {@link #beginRun} as finished. */
    void endRun() {
        done++;
    }

    /**
     * Erases the in-place bar line so a normal full-width line (scenario header,
     * summary table, ...) can be printed. No-op in plain-line mode.
     */
    void clearLine() {
        if (inPlace && lastLineLength > 0) {
            out.print('\r' + " ".repeat(lastLineLength) + '\r');
            out.flush();
            lastLineLength = 0;
        }
    }

    private String bar() {
        int filled = (total > 0) ? (int) Math.round((double) done * BAR_WIDTH / total) : 0;
        filled = Math.max(0, Math.min(BAR_WIDTH, filled));
        return "[" + "#".repeat(filled) + ".".repeat(BAR_WIDTH - filled) + "]";
    }

    private void paint(String line) {
        if (inPlace) {
            // Pad with spaces so a shorter line fully overwrites the previous one.
            int pad = Math.max(0, lastLineLength - line.length());
            out.print('\r' + line + " ".repeat(pad));
            out.flush();
            lastLineLength = line.length();
        } else {
            out.println(line);
        }
    }

    /**
     * True when stdout is an interactive terminal. On JDK &lt;22 a non-null
     * {@link System#console()} implies a terminal; JDK 22+ returns a Console
     * even when redirected, so ask {@code Console.isTerminal()} reflectively
     * (the method does not exist on 21, which this project compiles under).
     */
    private static boolean stdoutIsTerminal() {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        try {
            return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
