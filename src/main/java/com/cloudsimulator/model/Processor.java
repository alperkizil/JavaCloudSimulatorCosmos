package com.cloudsimulator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A physical processor (CPU socket) installed in a host. The framework models one
 * processor per host by default, with its id equal to the owning host's id
 * (Processor&lt;hostId&gt; belongs to host&lt;hostId&gt;). It holds the host's
 * physical {@link CpuCore}s, whose ids are derived from this processor's id
 * ("&lt;processorId&gt;-&lt;coreIndex&gt;").
 *
 * Pure identity/topology bookkeeping — present to make the 1 core → 1 vCPU
 * binding explicit and self-checking; it does not affect any calculation.
 */
public class Processor {

    private final long id;                                // equals the owning host's id
    private final List<CpuCore> cores = new ArrayList<>();

    public Processor(long id, int coreCount) {
        this.id = id;
        for (int i = 0; i < coreCount; i++) {
            cores.add(new CpuCore(id, i));
        }
    }

    public long getId() {
        return id;
    }

    public List<CpuCore> getCores() {
        return cores;
    }

    public int getCoreCount() {
        return cores.size();
    }

    @Override
    public String toString() {
        return "Processor{id=" + id + ", cores=" + cores.size() + '}';
    }
}
