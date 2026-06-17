package com.cloudsimulator.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single physical GPU installed in a {@link Host}.
 *
 * A GPU is bound to <b>at most one</b> VM at a time (exclusive 1:1 binding,
 * modeling GPU passthrough). The binding is established by the owning host when
 * a VM is placed and released when the VM is removed.
 */
public class Gpu {
    private static final AtomicLong idGenerator = new AtomicLong(0);

    private final long id;
    private Long hostId;      // Owning host (null until installed)
    private Long boundVmId;   // VM currently bound to this GPU (null = free)

    public Gpu() {
        this.id = idGenerator.incrementAndGet();
        this.hostId = null;
        this.boundVmId = null;
    }

    public Gpu(Long hostId) {
        this();
        this.hostId = hostId;
    }

    public long getId() {
        return id;
    }

    public Long getHostId() {
        return hostId;
    }

    public void setHostId(Long hostId) {
        this.hostId = hostId;
    }

    public Long getBoundVmId() {
        return boundVmId;
    }

    public boolean isBound() {
        return boundVmId != null;
    }

    public boolean isFree() {
        return boundVmId == null;
    }

    /**
     * Binds this GPU to a VM. Rebinding to the same VM is a no-op; binding while
     * already bound to a different VM is illegal (enforces exclusive 1:1 use).
     */
    public void bindTo(long vmId) {
        if (boundVmId != null && boundVmId != vmId) {
            throw new IllegalStateException(
                "GPU " + id + " is already bound to VM " + boundVmId +
                "; cannot bind to VM " + vmId);
        }
        this.boundVmId = vmId;
    }

    /**
     * Releases this GPU's binding, returning it to the free pool.
     */
    public void unbind() {
        this.boundVmId = null;
    }

    @Override
    public String toString() {
        return "Gpu{id=" + id + ", hostId=" + hostId + ", boundVmId=" + boundVmId + '}';
    }
}
