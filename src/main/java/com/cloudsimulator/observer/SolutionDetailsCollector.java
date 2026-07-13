package com.cloudsimulator.observer;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.steps.EnergyCalculationStep;
import com.cloudsimulator.steps.TaskExecutionStep;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Additive per-scenario export of schedule-level detail for EVERY solution in
 * each run's published Pareto front: task&rarr;VM assignments, per-VM queue
 * contents (dispatch order), per-task wait times, per-host energy, and the
 * aggregate figures (makespan, average wait, total energy, coincident peak).
 *
 * <p>Records are captured inside {@code CampaignRunner.simulateAllParetoSolutions}
 * &mdash; the existing re-simulation loop &mdash; via pure getter reads on the
 * live {@link TaskExecutionStep}/{@link EnergyCalculationStep} of each solution,
 * so the export adds no extra simulation work and touches no RNG. Written as
 * {@code scenario_N_solution_details.json.gz} next to the canonical CSVs; no
 * existing file changes shape because of it.</p>
 *
 * <p>Within one scenario the VM/host/task tables are structurally identical
 * across runs (fixed infrastructure config, deterministic placement, RNG-free
 * task generation), so they are captured once per scenario and solutions refer
 * to VMs/hosts/tasks by their stable list index, not by the run-specific ids.</p>
 */
public final class SolutionDetailsCollector {

    /** Schema version stamped into every file; bump on breaking changes. */
    public static final int SCHEMA_VERSION = 1;

    private static final double JOULES_PER_KWH = 3_600_000.0;

    /** One captured solution (primitive arrays keep the buffer compact). */
    private static final class Record {
        String label;
        long seed;
        int solutionIndex;
        double[] objectives;        // re-simulated values, CSV row order
        double makespanSeconds;
        double avgWaitSeconds;
        double totalEnergyKWh;
        double peakPowerWatts;
        int[] taskVmIndex;          // task position -> VM index (-1 unassigned)
        double[] taskWaitSeconds;   // task position -> wait (-1 never started)
        int[][] vmQueues;           // VM index -> ordered task positions
        double[] hostEnergyKWh;     // host index -> energy
        List<String> roles;         // best_<obj>/knee tags, null for most
    }

    private static final class ScenarioBucket {
        final int number;
        final String name;
        String vmTableJson;
        String hostTableJson;
        String taskTableJson;
        final List<Record> records = new ArrayList<>();

        ScenarioBucket(int number, String name) {
            this.number = number;
            this.name = name;
        }
    }

    private final List<String> objectiveNames;
    private final Map<Integer, ScenarioBucket> buckets = new LinkedHashMap<>();

    private ScenarioBucket current;
    private String currentLabel;
    private long currentSeed;
    private int runStartIndex;

    public SolutionDetailsCollector(List<String> objectiveNames) {
        this.objectiveNames = objectiveNames;
    }

    // -------------------------------------------------------------------------
    // Capture
    // -------------------------------------------------------------------------

    /** Marks the start of one (algorithm, seed) run's re-simulation loop. */
    public void beginRun(int scenarioNum, String scenarioName, String label, long seed) {
        current = buckets.computeIfAbsent(scenarioNum, n -> new ScenarioBucket(n, scenarioName));
        currentLabel = label;
        currentSeed = seed;
        runStartIndex = current.records.size();
    }

    /**
     * Captures one re-simulated front solution. Must be called after the
     * solution's {@code TaskExecutionStep}/{@code EnergyCalculationStep} have
     * executed; performs getter reads only.
     *
     * @param primaryValue the re-simulated primary objective (as published)
     * @param energyKWh    the re-simulated energy objective (as published)
     */
    public void record(SimulationContext context, List<VM> runningVMs,
                       SchedulingSolution solution, Map<Task, VM> assignment,
                       TaskExecutionStep taskExec, EnergyCalculationStep energyCalc,
                       double primaryValue, double energyKWh) {
        if (current == null) {
            return;
        }
        if (current.vmTableJson == null) {
            captureStaticTables(context, runningVMs);
        }

        Map<Long, Integer> vmIndexById = new LinkedHashMap<>();
        for (int i = 0; i < runningVMs.size(); i++) {
            vmIndexById.put(runningVMs.get(i).getId(), i);
        }

        List<Task> tasks = context.getTasks();
        Record r = new Record();
        r.label = currentLabel;
        r.seed = currentSeed;
        r.solutionIndex = current.records.size() - runStartIndex;
        r.objectives = new double[] {primaryValue, energyKWh};
        r.makespanSeconds = taskExec.getFractionalMakespan();
        r.avgWaitSeconds = taskExec.getAverageWaitingTime();
        r.totalEnergyKWh = energyCalc.getTotalITEnergyKWh();
        r.peakPowerWatts = energyCalc.getPeakTotalPowerWatts();

        r.taskVmIndex = new int[tasks.size()];
        r.taskWaitSeconds = new double[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            VM vm = assignment.get(t);
            Integer vmIdx = vm == null ? null : vmIndexById.get(vm.getId());
            r.taskVmIndex[i] = vmIdx == null ? -1 : vmIdx;
            Long wait = t.getWaitingTime();
            r.taskWaitSeconds[i] = wait == null ? -1 : wait;
        }

        List<List<Integer>> order = solution.getVmTaskOrder();
        r.vmQueues = new int[order.size()][];
        for (int j = 0; j < order.size(); j++) {
            List<Integer> q = order.get(j);
            int[] arr = new int[q.size()];
            for (int k = 0; k < q.size(); k++) {
                arr[k] = q.get(k);
            }
            r.vmQueues[j] = arr;
        }

        List<Host> hosts = context.getHosts();
        Map<Long, Double> joulesById = energyCalc.getHostEnergyJoules();
        r.hostEnergyKWh = new double[hosts.size()];
        for (int h = 0; h < hosts.size(); h++) {
            Double joules = joulesById.get(hosts.get(h).getId());
            r.hostEnergyKWh[h] = joules == null ? 0.0 : joules / JOULES_PER_KWH;
        }

        current.records.add(r);
    }

    /** Tags this run's representative solutions (best per objective + knee). */
    public void endRun() {
        if (current == null) {
            return;
        }
        List<Record> run = current.records.subList(runStartIndex, current.records.size());
        if (run.isEmpty()) {
            return;
        }
        int best0 = 0;
        int best1 = 0;
        for (int i = 1; i < run.size(); i++) {
            if (run.get(i).objectives[0] < run.get(best0).objectives[0]) {
                best0 = i;
            }
            if (run.get(i).objectives[1] < run.get(best1).objectives[1]) {
                best1 = i;
            }
        }
        addRole(run.get(best0), "best_" + objectiveNames.get(0));
        addRole(run.get(best1), "best_" + objectiveNames.get(1));
        int knee = kneeIndex(run, best0, best1);
        if (knee >= 0) {
            addRole(run.get(knee), "knee");
        }
    }

    private static void addRole(Record r, String role) {
        if (r.roles == null) {
            r.roles = new ArrayList<>(2);
        }
        if (!r.roles.contains(role)) {
            r.roles.add(role);
        }
    }

    /**
     * Max perpendicular distance from the (normalized) line between the two
     * per-objective extremes; -1 when the front is too small or degenerate.
     */
    private static int kneeIndex(List<Record> run, int best0, int best1) {
        if (run.size() < 3 || best0 == best1) {
            return -1;
        }
        double min0 = Double.MAX_VALUE;
        double max0 = -Double.MAX_VALUE;
        double min1 = Double.MAX_VALUE;
        double max1 = -Double.MAX_VALUE;
        for (Record r : run) {
            min0 = Math.min(min0, r.objectives[0]);
            max0 = Math.max(max0, r.objectives[0]);
            min1 = Math.min(min1, r.objectives[1]);
            max1 = Math.max(max1, r.objectives[1]);
        }
        double span0 = max0 - min0;
        double span1 = max1 - min1;
        if (span0 <= 0 || span1 <= 0) {
            return -1;
        }
        double ax = (run.get(best0).objectives[0] - min0) / span0;
        double ay = (run.get(best0).objectives[1] - min1) / span1;
        double bx = (run.get(best1).objectives[0] - min0) / span0;
        double by = (run.get(best1).objectives[1] - min1) / span1;
        double dx = bx - ax;
        double dy = by - ay;
        double norm = Math.hypot(dx, dy);
        if (norm == 0) {
            return -1;
        }
        int best = -1;
        double bestDist = 0;
        for (int i = 0; i < run.size(); i++) {
            if (i == best0 || i == best1) {
                continue;
            }
            double px = (run.get(i).objectives[0] - min0) / span0;
            double py = (run.get(i).objectives[1] - min1) / span1;
            double dist = Math.abs(dx * (ay - py) - (ax - px) * dy) / norm;
            if (dist > bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private void captureStaticTables(SimulationContext context, List<VM> runningVMs) {
        Map<Long, Integer> hostIndexById = new LinkedHashMap<>();
        List<Host> hosts = context.getHosts();
        for (int h = 0; h < hosts.size(); h++) {
            hostIndexById.put(hosts.get(h).getId(), h);
        }

        StringBuilder vms = new StringBuilder("[");
        for (int i = 0; i < runningVMs.size(); i++) {
            VM vm = runningVMs.get(i);
            Integer hostIdx = vm.getAssignedHostId() == null
                ? null : hostIndexById.get(vm.getAssignedHostId());
            if (i > 0) {
                vms.append(',');
            }
            vms.append(String.format(Locale.US,
                "{\"index\":%d,\"computeType\":\"%s\",\"vcpus\":%d,\"gpus\":%d,"
                    + "\"ipsPerVcpu\":%d,\"hostIndex\":%d}",
                i, vm.getComputeType().name(), vm.getRequestedVcpuCount(),
                vm.getRequestedGpuCount(), vm.getRequestedIpsPerVcpu(),
                hostIdx == null ? -1 : hostIdx));
        }
        current.vmTableJson = vms.append(']').toString();

        StringBuilder hs = new StringBuilder("[");
        for (int h = 0; h < hosts.size(); h++) {
            Host host = hosts.get(h);
            if (h > 0) {
                hs.append(',');
            }
            hs.append(String.format(Locale.US,
                "{\"index\":%d,\"computeType\":\"%s\",\"cores\":%d,\"gpus\":%d}",
                h, host.getComputeType().name(), host.getNumberOfCpuCores(),
                host.getNumberOfGpus()));
        }
        current.hostTableJson = hs.append(']').toString();

        List<Task> tasks = context.getTasks();
        StringBuilder ts = new StringBuilder("[");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            if (i > 0) {
                ts.append(',');
            }
            ts.append(String.format(Locale.US,
                "{\"index\":%d,\"lengthInstructions\":%d,\"workload\":\"%s\"}",
                i, t.getInstructionLength(), t.getWorkloadType().name()));
        }
        current.taskTableJson = ts.append(']').toString();
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /** Writes one {@code scenario_N_solution_details.json.gz} per scenario. */
    public void writeAll(Path dir, String experimentId) throws IOException {
        for (ScenarioBucket bucket : buckets.values()) {
            Path file = dir.resolve(
                "scenario_" + bucket.number + "_solution_details.json.gz");
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(file)),
                    StandardCharsets.UTF_8))) {
                writeScenario(w, bucket, experimentId);
            }
        }
    }

    private void writeScenario(BufferedWriter w, ScenarioBucket bucket,
                               String experimentId) throws IOException {
        w.write(String.format(Locale.US,
            "{\"schemaVersion\":%d,\"experimentId\":\"%s\",\"scenario\":%d,"
                + "\"scenarioName\":\"%s\",\"objectives\":[\"%s\",\"%s\"],"
                + "\"energyUnit\":\"kWh\",",
            SCHEMA_VERSION, experimentId, bucket.number, bucket.name,
            objectiveNames.get(0), objectiveNames.get(1)));
        w.write("\"vms\":" + orEmpty(bucket.vmTableJson) + ",");
        w.write("\"hosts\":" + orEmpty(bucket.hostTableJson) + ",");
        w.write("\"tasks\":" + orEmpty(bucket.taskTableJson) + ",");
        w.write("\"solutions\":[");
        for (int i = 0; i < bucket.records.size(); i++) {
            if (i > 0) {
                w.write(',');
            }
            w.write("\n");
            writeRecord(w, bucket.records.get(i));
        }
        w.write("\n]}");
    }

    private static String orEmpty(String json) {
        return json == null ? "[]" : json;
    }

    private void writeRecord(BufferedWriter w, Record r) throws IOException {
        StringBuilder sb = new StringBuilder(r.taskVmIndex.length * 16 + 512);
        sb.append(String.format(Locale.US,
            "{\"algorithm\":\"%s\",\"seed\":%d,\"solutionIndex\":%d,", r.label, r.seed, r.solutionIndex));
        if (r.roles != null) {
            sb.append("\"roles\":[");
            for (int i = 0; i < r.roles.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(r.roles.get(i)).append('"');
            }
            sb.append("],");
        }
        sb.append(String.format(Locale.US,
            "\"objectives\":[%.6f,%.9f],\"makespanSeconds\":%.6f,"
                + "\"avgWaitSeconds\":%.6f,\"totalEnergyKWh\":%.9f,"
                + "\"peakPowerWatts\":%.3f,",
            r.objectives[0], r.objectives[1], r.makespanSeconds,
            r.avgWaitSeconds, r.totalEnergyKWh, r.peakPowerWatts));

        int activeVms = 0;
        long queued = 0;
        for (int[] q : r.vmQueues) {
            if (q.length > 0) {
                activeVms++;
            }
            queued += q.length;
        }
        sb.append(String.format(Locale.US,
            "\"activeVmCount\":%d,\"avgQueueSizeAllVms\":%.4f,"
                + "\"avgQueueSizeActiveVms\":%.4f,",
            activeVms,
            r.vmQueues.length == 0 ? 0.0 : (double) queued / r.vmQueues.length,
            activeVms == 0 ? 0.0 : (double) queued / activeVms));

        appendIntArray(sb.append("\"taskVmIndex\":"), r.taskVmIndex).append(',');
        appendDoubleArray(sb.append("\"taskWaitSeconds\":"), r.taskWaitSeconds, "%.3f").append(',');
        sb.append("\"vmQueues\":[");
        for (int j = 0; j < r.vmQueues.length; j++) {
            if (j > 0) {
                sb.append(',');
            }
            appendIntArray(sb, r.vmQueues[j]);
        }
        sb.append("],");
        appendDoubleArray(sb.append("\"hostEnergyKWh\":"), r.hostEnergyKWh, "%.9f");
        sb.append('}');
        w.write(sb.toString());
    }

    private static StringBuilder appendIntArray(StringBuilder sb, int[] arr) {
        sb.append('[');
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(arr[i]);
        }
        return sb.append(']');
    }

    private static StringBuilder appendDoubleArray(StringBuilder sb, double[] arr, String fmt) {
        sb.append('[');
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.US, fmt, arr[i]));
        }
        return sb.append(']');
    }
}
