package com.cloudsimulator;

import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.model.CpuCore;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.VM;

import java.util.HashSet;
import java.util.Set;

/**
 * Verifies the 1 physical core → 1 vCPU binding invariant enforced by the
 * Processor / CpuCore identity ledger. Bookkeeping only: it must NOT change
 * admission (still strict 1:1, no oversubscription).
 */
public class CoreBindingCheck {

    public static void main(String[] args) {
        boolean ok = true;

        Host host = new Host(2_000_000_000L, 16, ComputeType.CPU_ONLY, 0);
        host.setRamCapacityMB(1_000_000);

        VM a = new VM("u", 2_000_000_000L, 4, 0, 4096, 1024, 100, ComputeType.CPU_ONLY);
        VM b = new VM("u", 2_000_000_000L, 2, 0, 4096, 1024, 100, ComputeType.CPU_ONLY);
        VM c = new VM("u", 2_000_000_000L, 3, 0, 4096, 1024, 100, ComputeType.CPU_ONLY);
        host.assignVM(a);
        host.assignVM(b);
        host.assignVM(c);

        // 1. Each VM is bound to exactly vcpuCount distinct cores.
        ok &= eq("VM A bound cores", a.getBoundCoreCount(), 4);
        ok &= eq("VM B bound cores", b.getBoundCoreCount(), 2);
        ok &= eq("VM C bound cores", c.getBoundCoreCount(), 3);

        // 2. No core is bound to two VMs; ids are unique and "<hostId>-<index>".
        int bound = 0;
        Set<String> seen = new HashSet<>();
        for (CpuCore core : host.getCpuCores()) {
            if (core.getBoundVmId() != null) {
                bound++;
                ok &= check("core " + core.getId() + " unique", seen.add(core.getId()));
                ok &= check("core " + core.getId() + " id format",
                    core.getId().equals(host.getId() + "-" + core.getCoreIndex()));
            }
        }
        ok &= eq("total bound cores", bound, 9);

        // 3. Bookkeeping mirror agrees with the (unchanged) admission count path.
        ok &= eq("free cores (ledger)", host.getAvailableCoreCount(), 7);
        ok &= eq("free cores (count path)", host.getAvailableCpuCores(), 7);

        // 4. Removing a VM frees exactly its cores.
        host.removeVM(b);
        ok &= eq("free after removing B", host.getAvailableCoreCount(), 9);
        ok &= eq("B ledger cleared", b.getBoundCoreCount(), 0);

        // 5. Over-capacity is still rejected by admission, before any binding.
        VM tooBig = new VM("u", 2_000_000_000L, 12, 0, 4096, 1024, 100, ComputeType.CPU_ONLY);
        boolean threw = false;
        try {
            host.assignVM(tooBig);
        } catch (IllegalStateException e) {
            threw = true;
        }
        ok &= check("over-capacity rejected", threw);
        ok &= eq("tooBig got no cores", tooBig.getBoundCoreCount(), 0);

        System.out.println(ok
            ? "\nCORE BINDING OK: strict 1 core -> 1 vCPU enforced; admission unchanged"
            : "\nCORE BINDING FAILED");
        if (!ok) {
            System.exit(1);
        }
    }

    private static boolean eq(String label, long got, long want) {
        boolean ok = got == want;
        System.out.printf("  %-26s %d (want %d) %s%n", label, got, want, ok ? "OK" : "FAIL");
        return ok;
    }

    private static boolean check(String label, boolean cond) {
        System.out.printf("  %-26s %s%n", label, cond ? "OK" : "FAIL");
        return cond;
    }
}
