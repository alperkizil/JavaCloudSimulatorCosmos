package com.cloudsimulator.model;

/**
 * A physical CPU core installed in a {@link Processor}, identified by
 * "&lt;processorId&gt;-&lt;coreIndex&gt;" (e.g. "1-0") and bindable exclusively
 * (1:1) to a single VM vCPU.
 *
 * This is pure identity/bookkeeping whose job is to make the 1 physical core →
 * 1 vCPU mapping explicit and self-enforcing: a core can be bound to at most one
 * VM at a time, and {@link #bindTo} rejects a double-bind. A core's presence does
 * not change any timing, admission, or power calculation.
 */
public class CpuCore {

    private final String id;          // "<processorId>-<coreIndex>"
    private final long processorId;
    private final int coreIndex;
    private Long boundVmId;            // null when free

    public CpuCore(long processorId, int coreIndex) {
        this.processorId = processorId;
        this.coreIndex = coreIndex;
        this.id = processorId + "-" + coreIndex;
        this.boundVmId = null;
    }

    /** Whether this core is currently unbound. */
    public boolean isFree() {
        return boundVmId == null;
    }

    /** Exclusively binds this core to a VM. Throws if already bound (1:1 guard). */
    public void bindTo(long vmId) {
        if (boundVmId != null) {
            throw new IllegalStateException(
                "CpuCore " + id + " already bound to VM " + boundVmId);
        }
        this.boundVmId = vmId;
    }

    /** Releases this core's binding. */
    public void unbind() {
        this.boundVmId = null;
    }

    public String getId() {
        return id;
    }

    public long getProcessorId() {
        return processorId;
    }

    public int getCoreIndex() {
        return coreIndex;
    }

    /** The id of the VM this core is bound to, or null if free. */
    public Long getBoundVmId() {
        return boundVmId;
    }

    @Override
    public String toString() {
        return "CpuCore{id=" + id + ", boundVmId=" + boundVmId + '}';
    }
}
