package com.cloudsimulator.PlacementStrategy.task;

import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.EmpiricalWorkloadProfile;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Runtime admission-control decorator for any non-batch {@link TaskAssignmentStrategy}.
 *
 * Step 3c of the power-ceiling feasibility study. Wraps an inner strategy
 * (e.g. {@link WorkloadAwareTaskAssignmentStrategy}) and enforces a hard
 * global DC power cap (P_cap, in Watts) by deferring each task's admission
 * until the projected instantaneous DC power across its execution window
 * stays below the cap.
 *
 * <p>Execution model mapping: this simulator runs {@code TaskAssignmentStep}
 * once per experiment rather than in a per-tick loop, and each VM is a FIFO
 * queue. A task's actual {@code taskExecStartTime} equals the sum of
 * execution times of tasks queued ahead of it. "Defer to a later timestep"
 * therefore maps to <b>inserting the task later in the admission order</b>,
 * which pushes it further down its VM's queue and naturally raises its
 * waiting time — the exact trade-off described by the research plan
 * ("trade waiting time for a hard guarantee").</p>
 *
 * <p>Algorithm (per call to {@link #assignAll}):
 * <ol>
 *   <li>Ask the inner strategy via {@link #selectVM} for each task's
 *       preferred VM (read-only; no state mutation).</li>
 *   <li>Walk tasks in FCFS order ({@code taskCreationTime} ascending) and
 *       build a projected power timeline on the fly.</li>
 *   <li>For each task, compute the incremental power its execution would
 *       add to its chosen VM and the earliest admission time at which
 *       cap + increment ≤ P_cap across the entire execution window.</li>
 *   <li>If the task's natural FCFS slot on its VM (= queue finish time
 *       among already-admitted tasks) is feasible, admit there; else push
 *       its admission forward to the earliest feasible moment. This only
 *       reorders tasks within the admission sequence — we do not swap VMs,
 *       since the inner strategy already chose placement.</li>
 *   <li>Once admission times are computed, finalize by calling
 *       {@code vm.assignTask} in ascending admission order so each VM's
 *       LinkedList queue reflects the deferred sequence.</li>
 * </ol>
 *
 * <p>Instantaneous DC power measurement (option A from the design):
 * the baseline power at t=0 is sum of {@code Host.getCurrentTotalPowerDraw()}
 * across all hosts at the moment the decorator is invoked. The projected
 * timeline adds/removes per-task incremental powers on top of this baseline.
 * Idle power stays constant (hosts stay powered on during admission delays),
 * so deferring does not introduce phantom "free" power budget — the cap
 * is applied against the full live load.</p>
 *
 * <p>Additive: no existing strategy or engine code is modified.</p>
 */
public class PowerCeilingAdmissionTaskAssignmentStrategy implements TaskAssignmentStrategy {

    private final TaskAssignmentStrategy inner;
    private final double powerCapWatts;
    private final List<Host> hosts;
    private final MeasurementBasedPowerModel powerModel;

    // Diagnostic counters (populated per assignAll call)
    private int tasksDeferredCount;
    private long totalDeferralTicks;
    private int admissionFailures;

    public PowerCeilingAdmissionTaskAssignmentStrategy(TaskAssignmentStrategy inner,
                                                       double powerCapWatts,
                                                       List<Host> hosts) {
        if (inner == null) throw new IllegalArgumentException("inner strategy cannot be null");
        if (inner.isBatchOptimizing()) {
            throw new IllegalArgumentException(
                "Admission decorator requires a non-batch (online) inner strategy; "
                + "batch strategies mutate state during assignAll and cannot be safely "
                + "queried for recommendations without side effects. Got: "
                + inner.getStrategyName());
        }
        this.inner = inner;
        this.powerCapWatts = powerCapWatts;
        this.hosts = hosts;
        this.powerModel = new MeasurementBasedPowerModel();
    }

    @Override
    public Optional<VM> selectVM(Task task, List<VM> candidateVMs) {
        // Per-task selection bypasses admission logic; use assignAll for full enforcement.
        return inner.selectVM(task, candidateVMs);
    }

    @Override
    public Map<Task, VM> assignAll(List<Task> tasks, List<VM> vms, long currentTime) {
        resetDiagnostics();
        Map<Task, VM> finalAssignments = new LinkedHashMap<>();
        if (tasks.isEmpty() || vms.isEmpty()) return finalAssignments;

        // Calibrate the speed-scaling reference to the actual VM fleet so
        // incremental-power estimates match EnergyObjective's accounting.
        powerModel.calculateReferenceIpsFromVMs(vms);

        double baselineWatts = currentHostPowerSum();

        // 1. Collect per-task VM recommendations from the inner strategy (read-only).
        List<TaskPlan> plans = new ArrayList<>();
        for (Task task : tasks) {
            List<VM> candidates = filterCandidates(task, vms);
            if (candidates.isEmpty()) continue;
            Optional<VM> pick = inner.selectVM(task, candidates);
            if (!pick.isPresent()) continue;
            VM vm = pick.get();
            long vmIps = vm.getTotalRequestedIps();
            if (vmIps <= 0) continue;

            long execTicks = Math.max(1L, (task.getInstructionLength() + vmIps - 1) / vmIps);
            double incr = incrementalPowerFor(task, vm);
            plans.add(new TaskPlan(task, vm, execTicks, incr));
        }

        // 2. FCFS ordering — ties broken by task id for determinism.
        plans.sort(Comparator
            .comparingLong((TaskPlan p) -> p.task.getTaskCreationTime())
            .thenComparingLong(p -> p.task.getId()));

        // 3. Projected power timeline as a sorted map of time -> delta.
        //    Baseline is represented implicitly as an additive offset.
        TreeMap<Long, Double> deltas = new TreeMap<>();
        Map<Long, Long> vmQueueFinish = new java.util.HashMap<>();
        for (VM vm : vms) vmQueueFinish.put(vm.getId(), 0L);

        for (TaskPlan plan : plans) {
            long vmId = plan.vm.getId();
            long queueFinish = vmQueueFinish.getOrDefault(vmId, 0L);
            long admissionTime = earliestFeasibleAdmission(
                deltas, baselineWatts, queueFinish, plan.incrementalWatts, plan.execTicks);

            if (admissionTime > queueFinish) {
                tasksDeferredCount++;
                totalDeferralTicks += (admissionTime - queueFinish);
            }
            plan.admissionTime = admissionTime;

            // Commit to the timeline so subsequent tasks see this one's footprint.
            deltas.merge(admissionTime, plan.incrementalWatts, Double::sum);
            deltas.merge(admissionTime + plan.execTicks, -plan.incrementalWatts, Double::sum);
            vmQueueFinish.put(vmId, admissionTime + plan.execTicks);
        }

        // 4. Finalize: assign in admission order so FIFO VM queues reflect deferrals.
        plans.sort(Comparator
            .comparingLong((TaskPlan p) -> p.admissionTime)
            .thenComparingLong(p -> p.vm.getId())
            .thenComparingLong(p -> p.task.getId()));

        for (TaskPlan plan : plans) {
            if (plan.admissionTime == Long.MAX_VALUE) {
                admissionFailures++;
                continue;
            }
            plan.task.assignToVM(plan.vm.getId(), currentTime);
            plan.vm.assignTask(plan.task);
            finalAssignments.put(plan.task, plan.vm);
        }
        return finalAssignments;
    }

    /**
     * Finds the earliest tick t ≥ {@code earliestCandidate} such that, during
     * the window [t, t + execTicks), the projected power curve
     * (baseline + cumulative deltas) plus {@code incrementalWatts} stays
     * ≤ the configured cap at every event boundary.
     *
     * Returns {@link Long#MAX_VALUE} if no feasible slot is found within a
     * bounded lookahead horizon (indicates the task's incremental power alone
     * exceeds the headroom forever — admission fails).
     */
    private long earliestFeasibleAdmission(TreeMap<Long, Double> deltas,
                                           double baselineWatts,
                                           long earliestCandidate,
                                           double incrementalWatts,
                                           long execTicks) {
        // If the task's own incremental power alone exceeds the cap even on
        // an empty DC, it can never be admitted. Short-circuit.
        if (baselineWatts + incrementalWatts > powerCapWatts + 1e-6) {
            return Long.MAX_VALUE;
        }

        // Build the ordered list of candidate admission times: the earliest
        // candidate itself plus every existing event time that sits within
        // a plausible search horizon. Headroom only changes at event
        // boundaries so checking those covers every distinct power level.
        List<Long> candidates = new ArrayList<>();
        candidates.add(earliestCandidate);
        for (Long t : deltas.keySet()) {
            if (t > earliestCandidate) candidates.add(t);
        }
        candidates.sort(Long::compareTo);

        for (long candidate : candidates) {
            if (isWindowFeasible(deltas, baselineWatts, candidate, execTicks, incrementalWatts)) {
                return candidate;
            }
        }
        return Long.MAX_VALUE;
    }

    /**
     * True iff {@code baseline + cumulativeDelta(t) + incrementalWatts ≤ cap}
     * for every t in [start, start + execTicks). Uses event-boundary
     * evaluation: the power curve is piecewise constant between events, so
     * sampling at each event inside the window plus the left endpoint
     * captures the maximum.
     */
    private boolean isWindowFeasible(TreeMap<Long, Double> deltas,
                                     double baselineWatts,
                                     long start,
                                     long execTicks,
                                     double incrementalWatts) {
        long end = start + execTicks; // exclusive
        double cumulative = 0.0;
        for (Map.Entry<Long, Double> e : deltas.entrySet()) {
            long t = e.getKey();
            if (t >= end) break;
            if (t <= start) {
                cumulative += e.getValue();
            } else {
                // First check power level over [start, t) with the previous
                // cumulative, then advance past this event.
                if (baselineWatts + cumulative + incrementalWatts > powerCapWatts + 1e-6) {
                    return false;
                }
                cumulative += e.getValue();
            }
        }
        // Tail interval [max(lastEventInsideWindow, start), end)
        return baselineWatts + cumulative + incrementalWatts <= powerCapWatts + 1e-6;
    }

    private double currentHostPowerSum() {
        if (hosts == null) return 0.0;
        double sum = 0.0;
        for (Host h : hosts) sum += h.getCurrentTotalPowerDraw();
        return sum;
    }

    private double incrementalPowerFor(Task task, VM vm) {
        double[] util = utilizationProfile(task.getWorkloadType());
        return powerModel.calculateIncrementalPowerWithSpeedScaling(
            task.getWorkloadType(), util[0], util[1], vm.getTotalRequestedIps());
    }

    private double[] utilizationProfile(WorkloadType workloadType) {
        if (powerModel.hasProfile(workloadType)) {
            EmpiricalWorkloadProfile profile = powerModel.getWorkloadProfile(workloadType);
            return new double[]{
                profile.getTypicalCpuUtilization(),
                profile.getTypicalGpuUtilization()
            };
        }
        // Fallback identical to EnergyAwareTaskAssignmentStrategy / PowerCeilingEnergyObjective
        switch (workloadType) {
            case SEVEN_ZIP:     return new double[]{1.0, 0.0};
            case DATABASE:      return new double[]{0.12, 0.0};
            case FURMARK:       return new double[]{0.08, 1.0};
            case IMAGE_GEN_CPU: return new double[]{0.80, 0.0};
            case IMAGE_GEN_GPU: return new double[]{0.30, 0.10};
            case LLM_CPU:       return new double[]{0.55, 0.0};
            case LLM_GPU:       return new double[]{0.12, 0.12};
            case CINEBENCH:     return new double[]{1.0, 0.0};
            case PRIME95SmallFFT: return new double[]{1.0, 0.0};
            case VERACRYPT:     return new double[]{0.03, 0.0};
            case IDLE:
            default:            return new double[]{0.0, 0.0};
        }
    }

    private List<VM> filterCandidates(Task task, List<VM> vms) {
        List<VM> out = new ArrayList<>();
        for (VM vm : vms) {
            if (!vm.getUserId().equals(task.getUserId())) continue;
            if (!vm.canAcceptTask(task)) continue;
            out.add(vm);
        }
        return out;
    }

    private void resetDiagnostics() {
        tasksDeferredCount = 0;
        totalDeferralTicks = 0L;
        admissionFailures = 0;
    }

    public int getTasksDeferredCount() { return tasksDeferredCount; }
    public long getTotalDeferralTicks() { return totalDeferralTicks; }
    public int getAdmissionFailures() { return admissionFailures; }
    public double getPowerCapWatts() { return powerCapWatts; }
    public TaskAssignmentStrategy getInner() { return inner; }

    @Override
    public String getStrategyName() {
        return inner.getStrategyName() + "+Admission(" + (long) powerCapWatts + "W)";
    }

    @Override
    public String getDescription() {
        return "Admission-control decorator enforcing hard DC peak-power cap = "
            + powerCapWatts + " W over inner strategy [" + inner.getStrategyName()
            + "]. Tasks whose admission would exceed the cap are deferred to "
            + "later slots in their VM queue, trading waiting time for a "
            + "guaranteed feasibility constraint.";
    }

    @Override
    public boolean isBatchOptimizing() {
        // We treat admission as a batch step (we reorder across all tasks),
        // so the TaskAssignmentStep routes through our assignAll.
        return true;
    }

    /** Internal planning record, one per task. */
    private static final class TaskPlan {
        final Task task;
        final VM vm;
        final long execTicks;
        final double incrementalWatts;
        long admissionTime;

        TaskPlan(Task task, VM vm, long execTicks, double incrementalWatts) {
            this.task = task;
            this.vm = vm;
            this.execTicks = execTicks;
            this.incrementalWatts = incrementalWatts;
        }
    }
}
