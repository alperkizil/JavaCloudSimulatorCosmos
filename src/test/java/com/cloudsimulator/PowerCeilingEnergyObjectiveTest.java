package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.MeasurementBasedPowerModel;
import com.cloudsimulator.model.Task;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.EnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.PowerCeilingEnergyObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.objectives.PowerCeilingViolationObjective;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone verification for PowerCeilingEnergyObjective.
 *
 * Confirms four properties:
 *  1. evaluate() returns an energy value within 1e-9 of the base EnergyObjective
 *     (peak-power tracking must not disturb the energy number).
 *  2. Peak power on a concurrent schedule matches the hand-computed value.
 *  3. Overflow seconds match the expected duration once a cap is set below peak.
 *  4. Serial (non-overlapping) tasks on one VM never exceed single-task power.
 *
 * Run via:
 *   find src/main/java -name "*.java" -not -path "GUI-excluded" | xargs javac -cp "lib" -d target/classes
 *   javac -cp "target/classes:lib" -d target/test-classes src/test/java/com/cloudsimulator/PowerCeilingEnergyObjectiveTest.java
 *   java  -cp "target/test-classes:target/classes:lib" com.cloudsimulator.PowerCeilingEnergyObjectiveTest
 */
public class PowerCeilingEnergyObjectiveTest {

    private static int failures = 0;

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("  PowerCeilingEnergyObjective verification");
        System.out.println("================================================================");
        System.out.println();

        testEnergyMatchesBaseObjective();
        testConcurrentTasksPeak();
        testOverflowSecondsAboveCap();
        testSerialTasksNeverExceedSinglePeak();
        testViolationObjectiveModes();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL TESTS PASSED");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }

    // ----------------------------------------------------------------------
    // Test 1: evaluate() energy result is identical to EnergyObjective
    // ----------------------------------------------------------------------
    private static void testEnergyMatchesBaseObjective() {
        System.out.println("[1] evaluate() reproduces EnergyObjective energy value");
        Fixture f = buildTwoVmFixture();

        EnergyObjective base = new EnergyObjective();
        PowerCeilingEnergyObjective withCap =
            new PowerCeilingEnergyObjective(Double.POSITIVE_INFINITY);

        double baseEnergy = base.evaluate(f.solution, f.tasks, f.vms);
        double capEnergy  = withCap.evaluate(f.solution, f.tasks, f.vms);

        expectNear("energy (kWh)", baseEnergy, capEnergy, 1e-9);
    }

    // ----------------------------------------------------------------------
    // Test 2: two concurrent VMs → peak is ~ idle + 2 × incremental
    // ----------------------------------------------------------------------
    private static void testConcurrentTasksPeak() {
        System.out.println("[2] Peak power with two concurrent CPU-heavy tasks");
        Fixture f = buildTwoVmFixture();

        PowerCeilingEnergyObjective obj = new PowerCeilingEnergyObjective(Double.POSITIVE_INFINITY);
        obj.evaluate(f.solution, f.tasks, f.vms);

        MeasurementBasedPowerModel pm = obj.getPowerModel();
        double idle = pm.getScaledIdlePower();
        // Reference hand-computed peak: both VMs run concurrently for the first
        // F.SHORT_TICKS seconds, then VM0 runs its second task alone.
        double inc0 = pm.calculateIncrementalPowerWithSpeedScaling(
            WorkloadType.SEVEN_ZIP, 1.0, 0.0, f.vm0.getTotalRequestedIps());
        double inc1 = pm.calculateIncrementalPowerWithSpeedScaling(
            WorkloadType.SEVEN_ZIP, 1.0, 0.0, f.vm1.getTotalRequestedIps());
        // DC aggregate baseline counts every active host once.
        double expectedPeak = idle * f.activeHostCount + inc0 + inc1;

        double reported = obj.getLastPeakPower();
        System.out.printf("    idle/host=%.2f W, activeHosts=%d, inc0=%.2f W, inc1=%.2f W%n",
            idle, f.activeHostCount, inc0, inc1);
        System.out.printf("    expected peak=%.4f W, reported=%.4f W%n", expectedPeak, reported);

        expectNear("peak power (W)", expectedPeak, reported, 1e-6);

        // Sanity: peak should be strictly greater than idle baseline
        expectTrue("peak > idle baseline",
            reported > idle * f.activeHostCount);
    }

    // ----------------------------------------------------------------------
    // Test 3: overflow seconds equals duration both VMs overlap
    // ----------------------------------------------------------------------
    private static void testOverflowSecondsAboveCap() {
        System.out.println("[3] Overflow seconds when cap < peak");
        Fixture f = buildTwoVmFixture();

        // First, get the uncapped peak
        PowerCeilingEnergyObjective probe = new PowerCeilingEnergyObjective(Double.POSITIVE_INFINITY);
        probe.evaluate(f.solution, f.tasks, f.vms);
        double peak = probe.getLastPeakPower();

        MeasurementBasedPowerModel pm = probe.getPowerModel();
        double idle = pm.getScaledIdlePower();
        double inc0 = pm.calculateIncrementalPowerWithSpeedScaling(
            WorkloadType.SEVEN_ZIP, 1.0, 0.0, f.vm0.getTotalRequestedIps());

        // Cap just below aggregate peak, but well above (idle + single-VM inc).
        double cap = idle * f.activeHostCount + inc0 + 1.0;
        PowerCeilingEnergyObjective capped = new PowerCeilingEnergyObjective(cap);
        capped.evaluate(f.solution, f.tasks, f.vms);

        System.out.printf("    peak=%.4f W, cap=%.4f W%n", peak, cap);
        System.out.printf("    overflow seconds reported=%.4f (expected=%d)%n",
            capped.getLastOverflowSeconds(), f.overlapTicks);

        expectTrue("peak > cap during overlap", peak > cap);
        expectNear("overflow seconds", f.overlapTicks, capped.getLastOverflowSeconds(), 1e-6);
        expectTrue("capped.wasLastFeasible()==false", !capped.wasLastFeasible());
        expectNear("violation watts", peak - cap, capped.getLastViolationWatts(), 1e-6);
    }

    // ----------------------------------------------------------------------
    // Test 4: a single VM running two tasks sequentially never doubles power
    // ----------------------------------------------------------------------
    private static void testSerialTasksNeverExceedSinglePeak() {
        System.out.println("[4] Serial tasks on one VM stay at single-task peak");

        VM vm = new VM("u", 1_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY);
        vm.setAssignedHostId(7L);
        List<VM> vms = List.of(vm);

        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task("t0", "u", 2_000_000_000L, WorkloadType.SEVEN_ZIP));
        tasks.add(new Task("t1", "u", 2_000_000_000L, WorkloadType.SEVEN_ZIP));
        // Assign both to VM 0 so they run serially
        SchedulingSolution solution = new SchedulingSolution(2, 1, 2);
        solution.setAssignedVM(0, 0);
        solution.setAssignedVM(1, 0);
        solution.rebuildTaskOrdering();

        PowerCeilingEnergyObjective obj = new PowerCeilingEnergyObjective(Double.POSITIVE_INFINITY);
        obj.evaluate(solution, tasks, vms);

        MeasurementBasedPowerModel pm = obj.getPowerModel();
        double idle = pm.getScaledIdlePower();
        double inc = pm.calculateIncrementalPowerWithSpeedScaling(
            WorkloadType.SEVEN_ZIP, 1.0, 0.0, vm.getTotalRequestedIps());
        double expectedPeak = idle + inc;

        System.out.printf("    expected serial peak=%.4f W, reported=%.4f W%n",
            expectedPeak, obj.getLastPeakPower());
        expectNear("serial peak equals single-task peak",
            expectedPeak, obj.getLastPeakPower(), 1e-6);
    }

    // ----------------------------------------------------------------------
    // Test 5: PowerCeilingViolationObjective matches the sweep-line results
    //         and returns zero under a generous cap.
    // ----------------------------------------------------------------------
    private static void testViolationObjectiveModes() {
        System.out.println("[5] PowerCeilingViolationObjective WATTS_OVER_CAP / OVERFLOW_SECONDS / OVERFLOW_JOULES");
        Fixture f = buildTwoVmFixture();

        // Probe to learn the peak
        PowerCeilingEnergyObjective probe = new PowerCeilingEnergyObjective(Double.POSITIVE_INFINITY);
        probe.evaluate(f.solution, f.tasks, f.vms);
        double peak = probe.getLastPeakPower();
        double idle = probe.getPowerModel().getScaledIdlePower();
        double inc0 = probe.getPowerModel().calculateIncrementalPowerWithSpeedScaling(
            WorkloadType.SEVEN_ZIP, 1.0, 0.0, f.vm0.getTotalRequestedIps());

        // Generous cap: must be feasible, all three modes must return 0
        double generousCap = peak + 1_000.0;
        PowerCeilingViolationObjective feasW = new PowerCeilingViolationObjective(
            generousCap, PowerCeilingViolationObjective.Mode.WATTS_OVER_CAP);
        PowerCeilingViolationObjective feasS = new PowerCeilingViolationObjective(
            generousCap, PowerCeilingViolationObjective.Mode.OVERFLOW_SECONDS);
        PowerCeilingViolationObjective feasJ = new PowerCeilingViolationObjective(
            generousCap, PowerCeilingViolationObjective.Mode.OVERFLOW_JOULES);
        expectNear("feasible WATTS_OVER_CAP",   0.0, feasW.evaluate(f.solution, f.tasks, f.vms), 1e-9);
        expectNear("feasible OVERFLOW_SECONDS", 0.0, feasS.evaluate(f.solution, f.tasks, f.vms), 1e-9);
        expectNear("feasible OVERFLOW_JOULES",  0.0, feasJ.evaluate(f.solution, f.tasks, f.vms), 1e-9);

        // Tight cap: (idle·hosts + inc0 + 1) — overlap window violates by
        //   watts: peak - cap     (should equal inc1 - 1, ≈ 230.6 W)
        //   seconds: f.overlapTicks
        //   joules: (peak - cap) * overlapTicks
        double tightCap = idle * f.activeHostCount + inc0 + 1.0;

        PowerCeilingViolationObjective tightW = new PowerCeilingViolationObjective(
            tightCap, PowerCeilingViolationObjective.Mode.WATTS_OVER_CAP);
        PowerCeilingViolationObjective tightS = new PowerCeilingViolationObjective(
            tightCap, PowerCeilingViolationObjective.Mode.OVERFLOW_SECONDS);
        PowerCeilingViolationObjective tightJ = new PowerCeilingViolationObjective(
            tightCap, PowerCeilingViolationObjective.Mode.OVERFLOW_JOULES);

        double vW = tightW.evaluate(f.solution, f.tasks, f.vms);
        double vS = tightS.evaluate(f.solution, f.tasks, f.vms);
        double vJ = tightJ.evaluate(f.solution, f.tasks, f.vms);

        double expectedW = peak - tightCap;
        double expectedS = f.overlapTicks;
        double expectedJ = expectedW * expectedS;

        System.out.printf("    peak=%.4f W  cap=%.4f W  overlap=%d s%n", peak, tightCap, f.overlapTicks);
        expectNear("tight WATTS_OVER_CAP",   expectedW, vW, 1e-6);
        expectNear("tight OVERFLOW_SECONDS", expectedS, vS, 1e-6);
        expectNear("tight OVERFLOW_JOULES",  expectedJ, vJ, 1e-6);

        // Sanity: minimization + units
        expectTrue("isMinimization() == true", tightW.isMinimization());
        expectTrue("unit W", "W".equals(tightW.getUnit()));
        expectTrue("unit s", "s".equals(tightS.getUnit()));
        expectTrue("unit J", "J".equals(tightJ.getUnit()));
    }

    // ----------------------------------------------------------------------
    // Fixture: 2 VMs on 2 distinct hosts. VM0 gets 2 CPU tasks, VM1 gets 1.
    //   VM0 task durations: SHORT_TICKS + LONG_TICKS
    //   VM1 task duration:  SHORT_TICKS
    // So both VMs run concurrently for the first SHORT_TICKS seconds, then
    // VM0 runs solo for LONG_TICKS seconds.
    // ----------------------------------------------------------------------
    static class Fixture {
        SchedulingSolution solution;
        List<Task> tasks;
        List<VM> vms;
        VM vm0, vm1;
        int activeHostCount;
        long overlapTicks;
    }

    private static Fixture buildTwoVmFixture() {
        Fixture f = new Fixture();

        f.vm0 = new VM("u", 1_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY);
        f.vm0.setAssignedHostId(101L);
        f.vm1 = new VM("u", 1_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY);
        f.vm1.setAssignedHostId(202L);
        f.activeHostCount = 2;
        f.vms = List.of(f.vm0, f.vm1);

        // VM IPS = 4 vCPU × 1 GIPS = 4e9 IPS.
        // Short task: 4e9 instructions → 1 tick. Long task: 1.2e10 → 3 ticks.
        long shortLen = 4_000_000_000L;
        long longLen  = 12_000_000_000L;
        f.overlapTicks = 1L; // both VMs run during tick 0

        f.tasks = new ArrayList<>();
        f.tasks.add(new Task("vm0_short", "u", shortLen, WorkloadType.SEVEN_ZIP));
        f.tasks.add(new Task("vm0_long",  "u", longLen,  WorkloadType.SEVEN_ZIP));
        f.tasks.add(new Task("vm1_short", "u", shortLen, WorkloadType.SEVEN_ZIP));

        f.solution = new SchedulingSolution(3, 2, 2);
        f.solution.setAssignedVM(0, 0); // vm0_short -> VM0
        f.solution.setAssignedVM(1, 0); // vm0_long  -> VM0
        f.solution.setAssignedVM(2, 1); // vm1_short -> VM1
        f.solution.rebuildTaskOrdering();

        return f;
    }

    // ----------------------------------------------------------------------
    // Tiny assertion helpers
    // ----------------------------------------------------------------------
    private static void expectNear(String label, double expected, double actual, double tol) {
        if (Math.abs(expected - actual) > tol) {
            System.out.printf("    FAIL %s: expected %.9f, got %.9f (|d|=%.9f > tol=%.1e)%n",
                label, expected, actual, Math.abs(expected - actual), tol);
            failures++;
        } else {
            System.out.printf("    PASS %s: %.6f%n", label, actual);
        }
    }

    private static void expectTrue(String label, boolean condition) {
        if (!condition) {
            System.out.printf("    FAIL %s%n", label);
            failures++;
        } else {
            System.out.printf("    PASS %s%n", label);
        }
    }
}
