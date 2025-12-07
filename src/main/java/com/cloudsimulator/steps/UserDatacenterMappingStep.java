package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.model.CloudDatacenter;
import com.cloudsimulator.model.Host;
import com.cloudsimulator.model.User;
import com.cloudsimulator.model.VM;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UserDatacenterMappingStep validates and finalizes user-datacenter relationships
 * after hosts have been placed in datacenters.
 *
 * This is the third step in the simulation pipeline, executed after HostPlacementStep.
 *
 * The step performs the following actions:
 * 1. Validates user datacenter preferences (removes DCs with no hosts)
 * 2. If a user has no valid DCs, randomly assigns one using the experiment seed
 * 3. Calculates estimated resource requirements per user
 * 4. Starts user sessions (sets start timestamp)
 * 5. Records metrics about user-datacenter mappings
 *
 * If no datacenter has any hosts, the step throws a RuntimeException to terminate
 * the simulation, as it cannot proceed without available resources.
 */
public class UserDatacenterMappingStep implements SimulationStep {

    private int usersProcessed;
    private int validMappings;
    private int reassignedUsers;
    private int invalidMappings;
    private Map<String, Integer> usersPerDatacenter;
    private Map<String, UserResourceRequirements> userResourceRequirements;

    public UserDatacenterMappingStep() {
        this.usersProcessed = 0;
        this.validMappings = 0;
        this.reassignedUsers = 0;
        this.invalidMappings = 0;
        this.usersPerDatacenter = new HashMap<>();
        this.userResourceRequirements = new HashMap<>();
    }

    @Override
    public void execute(SimulationContext context) {
        List<User> users = context.getUsers();
        List<CloudDatacenter> datacenters = context.getDatacenters();

        if (users == null || users.isEmpty()) {
            recordMetrics(context);
            return;
        }

        // Find all datacenters that have hosts (valid targets)
        List<CloudDatacenter> datacentersWithHosts = datacenters.stream()
                .filter(dc -> !dc.getHosts().isEmpty())
                .collect(Collectors.toList());

        // If no datacenter has hosts, we cannot proceed
        if (datacentersWithHosts.isEmpty()) {
            throw new RuntimeException(
                "Simulation cannot proceed: No datacenter has any hosts assigned. " +
                "Check HostPlacementStep configuration and power/capacity constraints."
            );
        }

        // Initialize user count per datacenter
        for (CloudDatacenter dc : datacenters) {
            usersPerDatacenter.put(dc.getName(), 0);
        }

        // Process each user
        long currentTime = context.getCurrentTime();
        for (User user : users) {
            processUser(user, datacenters, datacentersWithHosts, currentTime);
            usersProcessed++;
        }

        // Record metrics
        recordMetrics(context);
    }

    /**
     * Processes a single user: validates preferences, reassigns if needed,
     * calculates resource requirements, and starts their session.
     */
    private void processUser(User user, List<CloudDatacenter> allDatacenters,
                            List<CloudDatacenter> datacentersWithHosts, long currentTime) {

        // Get user's current datacenter preferences
        List<Long> currentPreferences = new ArrayList<>(user.getUserSelectedDatacenters());

        // Filter to only datacenters that have hosts
        List<Long> validDatacenterIds = currentPreferences.stream()
                .filter(dcId -> datacentersWithHosts.stream()
                        .anyMatch(dc -> dc.getId() == dcId))
                .collect(Collectors.toList());

        // Remove invalid datacenters from user's list
        List<Long> invalidDcIds = currentPreferences.stream()
                .filter(dcId -> !validDatacenterIds.contains(dcId))
                .collect(Collectors.toList());

        for (Long invalidId : invalidDcIds) {
            user.removeSelectedDatacenter(invalidId);
        }

        // If user has no valid datacenters, randomly assign one
        if (validDatacenterIds.isEmpty()) {
            CloudDatacenter randomDc = RandomGenerator.getInstance()
                    .randomElement(datacentersWithHosts);
            user.addSelectedDatacenter(randomDc.getId());
            randomDc.addCustomer(user.getName());
            validDatacenterIds.add(randomDc.getId());
            reassignedUsers++;
        } else {
            validMappings++;
        }

        // Update user count per datacenter
        for (Long dcId : user.getUserSelectedDatacenters()) {
            for (CloudDatacenter dc : allDatacenters) {
                if (dc.getId() == dcId) {
                    usersPerDatacenter.merge(dc.getName(), 1, Integer::sum);
                    break;
                }
            }
        }

        // Calculate resource requirements for this user
        UserResourceRequirements requirements = calculateResourceRequirements(user);
        userResourceRequirements.put(user.getName(), requirements);

        // Check if user's requirements can be met by their datacenters
        boolean canBeMet = checkResourceAvailability(user, allDatacenters, requirements);
        if (!canBeMet) {
            invalidMappings++;
        }

        // Start user session
        user.startSession(currentTime);
    }

    /**
     * Calculates the total resource requirements for a user based on their VMs.
     */
    private UserResourceRequirements calculateResourceRequirements(User user) {
        long totalRamMB = 0;
        int totalVcpus = 0;
        int totalGpus = 0;
        long totalStorageMB = 0;
        long totalBandwidthMbps = 0;

        for (VM vm : user.getVirtualMachines()) {
            totalRamMB += vm.getRequestedRamMB();
            totalVcpus += vm.getRequestedVcpuCount();
            totalGpus += vm.getRequestedGpuCount();
            totalStorageMB += vm.getRequestedStorageMB();
            totalBandwidthMbps += vm.getRequestedBandwidthMbps();
        }

        return new UserResourceRequirements(
            user.getName(),
            user.getVirtualMachines().size(),
            totalVcpus,
            totalGpus,
            totalRamMB,
            totalStorageMB,
            totalBandwidthMbps
        );
    }

    /**
     * Checks if the user's resource requirements can potentially be met
     * by their selected datacenters.
     */
    private boolean checkResourceAvailability(User user, List<CloudDatacenter> allDatacenters,
                                              UserResourceRequirements requirements) {
        // Get total available resources across user's selected datacenters
        long availableRam = 0;
        int availableCpus = 0;
        int availableGpus = 0;

        for (Long dcId : user.getUserSelectedDatacenters()) {
            for (CloudDatacenter dc : allDatacenters) {
                if (dc.getId() == dcId) {
                    for (Host host : dc.getHosts()) {
                        availableRam += host.getAvailableRamMB();
                        availableCpus += host.getAvailableCpuCores();
                        availableGpus += host.getAvailableGpus();
                    }
                    break;
                }
            }
        }

        // Check if requirements can be met
        return availableRam >= requirements.totalRamMB &&
               availableCpus >= requirements.totalVcpus &&
               availableGpus >= requirements.totalGpus;
    }

    /**
     * Records metrics about user-datacenter mapping.
     */
    private void recordMetrics(SimulationContext context) {
        context.recordMetric("userMapping.usersProcessed", usersProcessed);
        context.recordMetric("userMapping.validMappings", validMappings);
        context.recordMetric("userMapping.reassignedUsers", reassignedUsers);
        context.recordMetric("userMapping.insufficientResources", invalidMappings);

        // Record users per datacenter
        for (Map.Entry<String, Integer> entry : usersPerDatacenter.entrySet()) {
            context.recordMetric("userMapping.datacenter." + entry.getKey() + ".userCount",
                                entry.getValue());
        }

        // Record resource requirements summary
        long totalRequiredRam = 0;
        int totalRequiredVcpus = 0;
        int totalRequiredGpus = 0;
        for (UserResourceRequirements req : userResourceRequirements.values()) {
            totalRequiredRam += req.totalRamMB;
            totalRequiredVcpus += req.totalVcpus;
            totalRequiredGpus += req.totalGpus;
        }
        context.recordMetric("userMapping.totalRequiredRamMB", totalRequiredRam);
        context.recordMetric("userMapping.totalRequiredVcpus", totalRequiredVcpus);
        context.recordMetric("userMapping.totalRequiredGpus", totalRequiredGpus);
    }

    @Override
    public String getStepName() {
        return "User-Datacenter Mapping";
    }

    // Getters for test verification

    public int getUsersProcessed() {
        return usersProcessed;
    }

    public int getValidMappings() {
        return validMappings;
    }

    public int getReassignedUsers() {
        return reassignedUsers;
    }

    public int getInvalidMappings() {
        return invalidMappings;
    }

    public Map<String, Integer> getUsersPerDatacenter() {
        return usersPerDatacenter;
    }

    public Map<String, UserResourceRequirements> getUserResourceRequirements() {
        return userResourceRequirements;
    }

    /**
     * Inner class to hold user resource requirements.
     */
    public static class UserResourceRequirements {
        public final String userName;
        public final int vmCount;
        public final int totalVcpus;
        public final int totalGpus;
        public final long totalRamMB;
        public final long totalStorageMB;
        public final long totalBandwidthMbps;

        public UserResourceRequirements(String userName, int vmCount, int totalVcpus,
                                        int totalGpus, long totalRamMB,
                                        long totalStorageMB, long totalBandwidthMbps) {
            this.userName = userName;
            this.vmCount = vmCount;
            this.totalVcpus = totalVcpus;
            this.totalGpus = totalGpus;
            this.totalRamMB = totalRamMB;
            this.totalStorageMB = totalStorageMB;
            this.totalBandwidthMbps = totalBandwidthMbps;
        }

        @Override
        public String toString() {
            return String.format("UserResourceRequirements{user=%s, vms=%d, vcpus=%d, gpus=%d, ram=%dMB}",
                userName, vmCount, totalVcpus, totalGpus, totalRamMB);
        }
    }
}
