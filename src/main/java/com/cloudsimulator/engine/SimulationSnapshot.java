package com.cloudsimulator.engine;

import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.model.*;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.ParetoFront;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingObjective;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.steps.InitializationStep;

import java.io.*;
import java.util.*;

/**
 * Captures and restores simulation state for subprocess isolation in Pareto front simulation.
 *
 * This class serializes:
 * - The experiment configuration (for recreating entities)
 * - Host-to-Datacenter mappings (from step 2)
 * - User-to-Datacenter mappings (from step 3)
 * - VM-to-Host mappings (from step 4)
 * - The Pareto front with all solutions
 * - Objective definitions
 *
 * Each subprocess can restore a fresh SimulationContext with steps 1-4 already applied,
 * then apply a specific Pareto solution and run steps 5-10 independently.
 */
public class SimulationSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    // Configuration for recreating entities
    private final ExperimentConfiguration config;

    // Pareto front data
    private final ParetoFront paretoFront;
    private final List<String> objectiveNames;
    private final boolean[] objectiveMinimization;

    // Placement mappings (captured after steps 2-4)
    // Maps host ID -> datacenter name
    private final Map<Long, String> hostDatacenterMapping;

    // Maps VM ID -> host ID
    private final Map<Long, Long> vmHostMapping;

    // Maps user name -> set of datacenter IDs
    private final Map<String, Set<Long>> userDatacenterMapping;

    // Maps VM ID -> user name (for ownership tracking)
    private final Map<Long, String> vmUserMapping;

    /**
     * Creates a snapshot from the current simulation context.
     *
     * @param context     The simulation context after steps 1-4
     * @param config      The experiment configuration
     * @param paretoFront The Pareto front from NSGA-II optimization
     * @param objectives  The objectives used in optimization
     */
    public SimulationSnapshot(SimulationContext context,
                               ExperimentConfiguration config,
                               ParetoFront paretoFront,
                               List<SchedulingObjective> objectives) {
        this.config = config.clone();  // Deep copy
        this.paretoFront = paretoFront;

        // Store objective metadata
        this.objectiveNames = new ArrayList<>(paretoFront.getObjectiveNames());
        this.objectiveMinimization = new boolean[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            this.objectiveMinimization[i] = objectives.get(i).isMinimization();
        }

        // Capture host placements (step 2)
        this.hostDatacenterMapping = captureHostPlacements(context);

        // Capture user mappings (step 3)
        this.userDatacenterMapping = captureUserMappings(context);

        // Capture VM placements (step 4)
        this.vmHostMapping = captureVMPlacements(context);

        // Capture VM ownership
        this.vmUserMapping = captureVMOwnership(context);
    }

    private Map<Long, String> captureHostPlacements(SimulationContext context) {
        Map<Long, String> mapping = new HashMap<>();
        for (CloudDatacenter dc : context.getDatacenters()) {
            for (Host host : dc.getHosts()) {
                mapping.put(host.getId(), dc.getName());
            }
        }
        return mapping;
    }

    private Map<String, Set<Long>> captureUserMappings(SimulationContext context) {
        Map<String, Set<Long>> mapping = new HashMap<>();
        for (User user : context.getUsers()) {
            mapping.put(user.getName(), new HashSet<>(user.getUserSelectedDatacenters()));
        }
        return mapping;
    }

    private Map<Long, Long> captureVMPlacements(SimulationContext context) {
        Map<Long, Long> mapping = new HashMap<>();
        for (VM vm : context.getVms()) {
            if (vm.getAssignedHostId() != null) {
                mapping.put(vm.getId(), vm.getAssignedHostId());
            }
        }
        return mapping;
    }

    private Map<Long, String> captureVMOwnership(SimulationContext context) {
        Map<Long, String> mapping = new HashMap<>();
        for (VM vm : context.getVms()) {
            mapping.put(vm.getId(), vm.getUserId());
        }
        return mapping;
    }

    /**
     * Restores a fresh SimulationContext with steps 1-4 already applied.
     * The context is ready for task assignment (step 5).
     *
     * @return A new SimulationContext with infrastructure set up
     */
    public SimulationContext restoreContext() {
        SimulationContext context = new SimulationContext();

        // Step 1: Create entities from config
        InitializationStep initStep = new InitializationStep(config);
        initStep.execute(context);

        // Build lookup maps for restored entities
        Map<String, CloudDatacenter> datacenterByName = new HashMap<>();
        for (CloudDatacenter dc : context.getDatacenters()) {
            datacenterByName.put(dc.getName(), dc);
        }

        Map<Long, Host> hostById = new HashMap<>();
        for (Host host : context.getHosts()) {
            hostById.put(host.getId(), host);
        }

        Map<Long, VM> vmById = new HashMap<>();
        for (VM vm : context.getVms()) {
            vmById.put(vm.getId(), vm);
        }

        // Step 2: Restore host placements
        for (Host host : context.getHosts()) {
            String dcName = hostDatacenterMapping.get(host.getId());
            if (dcName != null) {
                CloudDatacenter dc = datacenterByName.get(dcName);
                if (dc != null) {
                    dc.addHost(host);
                    host.activate(0, dc.getId());
                }
            }
        }

        // Step 3: Restore user mappings
        for (User user : context.getUsers()) {
            Set<Long> dcIds = userDatacenterMapping.get(user.getName());
            if (dcIds != null) {
                for (Long dcId : dcIds) {
                    user.addSelectedDatacenter(dcId);
                }
            }
            user.startSession(0);
        }

        // Step 4: Restore VM placements
        for (VM vm : context.getVms()) {
            Long hostId = vmHostMapping.get(vm.getId());
            if (hostId != null) {
                Host host = hostById.get(hostId);
                if (host != null) {
                    host.assignVM(vm);
                    vm.start();
                }
            }
        }

        return context;
    }

    /**
     * Saves this snapshot to a file.
     */
    public void saveToFile(String path) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(path)))) {
            oos.writeObject(this);
        }
    }

    /**
     * Loads a snapshot from a file.
     */
    public static SimulationSnapshot loadFromFile(String path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(path)))) {
            return (SimulationSnapshot) ois.readObject();
        }
    }

    // Getters

    public ExperimentConfiguration getConfig() {
        return config;
    }

    public ParetoFront getParetoFront() {
        return paretoFront;
    }

    public List<String> getObjectiveNames() {
        return objectiveNames;
    }

    public boolean[] getObjectiveMinimization() {
        return objectiveMinimization;
    }

    public int getNumSolutions() {
        return paretoFront.size();
    }

    public SchedulingSolution getSolution(int index) {
        return paretoFront.getSolutions().get(index);
    }
}
