package com.cloudsimulator.steps;

import com.cloudsimulator.engine.SimulationContext;
import com.cloudsimulator.engine.SimulationStep;
import com.cloudsimulator.enums.VmState;
import com.cloudsimulator.enums.WorkloadType;
import com.cloudsimulator.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MetricsCollectionStep collects, aggregates, and organizes all simulation metrics
 * into a comprehensive SimulationSummary suitable for analysis and reporting.
 *
 * This is the ninth step in the simulation pipeline, executed after EnergyCalculationStep.
 * It aggregates metrics from all previous steps and calculates additional derived metrics
 * including SLA compliance and load balance indices.
 *
 * Features:
 * - Infrastructure metrics (hosts, VMs, utilization)
 * - Task metrics (completion, timing, throughput)
 * - Energy metrics (consumption, cost, carbon)
 * - Performance metrics (makespan, waiting time, turnaround)
 * - SLA compliance (configurable thresholds, percentiles)
 * - Per-entity summaries (datacenter, host, user, workload)
 * - JSON-serializable SimulationSummary output
 *
 * Usage:
 * <pre>
 * MetricsCollectionStep step = new MetricsCollectionStep();
 * step.addSLAThreshold(3600);   // 1 hour SLA
 * step.addSLAThreshold(7200);   // 2 hour SLA
 * step.execute(context);
 *
 * SimulationSummary summary = step.getSummary();
 * System.out.println(summary.toJson());
 * </pre>
 */
public class MetricsCollectionStep implements SimulationStep {

    // SLA thresholds (in seconds)
    private List<Long> slaThresholds;
    private long primarySLAThreshold = 3600; // Default: 1 hour

    // Collected summary
    private SimulationSummary summary;

    // Aggregated metrics
    private Map<String, Object> aggregatedMetrics;

    // Timing
    private long stepStartTimeMs;
    private long stepEndTimeMs;

    public MetricsCollectionStep() {
        this.slaThresholds = new ArrayList<>();
        this.slaThresholds.add(3600L);  // 1 hour
        this.slaThresholds.add(7200L);  // 2 hours
        this.aggregatedMetrics = new HashMap<>();
    }

    @Override
    public void execute(SimulationContext context) {
        stepStartTimeMs = System.currentTimeMillis();

        logInfo("Collecting and aggregating simulation metrics...");

        aggregatedMetrics.clear();
        summary = new SimulationSummary();

        // Set metadata
        collectMetadata(context);

        // Collect infrastructure metrics
        collectInfrastructureMetrics(context);

        // Collect task metrics
        collectTaskMetrics(context);

        // Collect energy metrics
        collectEnergyMetrics(context);

        // Collect performance metrics
        collectPerformanceMetrics(context);

        // Calculate SLA compliance
        calculateSLACompliance(context);

        // Collect per-datacenter summaries
        collectDatacenterSummaries(context);

        // Collect per-host summaries
        collectHostSummaries(context);

        // Collect per-user summaries
        collectUserSummaries(context);

        // Collect per-workload summaries
        collectWorkloadSummaries(context);

        // Store raw metrics
        summary.setRawMetrics(new HashMap<>(context.getAllMetrics()));

        stepEndTimeMs = System.currentTimeMillis();
        summary.setWallClockDurationMs(stepEndTimeMs - stepStartTimeMs);

        // Record aggregated metrics to context
        recordMetrics(context);

        // Log summary
        logSummary();
    }

    /**
     * Collects simulation metadata.
     */
    private void collectMetadata(SimulationContext context) {
        summary.setSimulationId(UUID.randomUUID().toString().substring(0, 8));

        // Get simulation duration from vmExecution metrics
        Object simDuration = context.getMetric("vmExecution.totalSimulationSeconds");
        if (simDuration != null) {
            summary.setSimulationDurationSeconds(((Number) simDuration).longValue());
        }
    }

    /**
     * Collects infrastructure metrics.
     */
    private void collectInfrastructureMetrics(SimulationContext context) {
        SimulationSummary.InfrastructureSummary infra = summary.getInfrastructure();

        List<CloudDatacenter> datacenters = context.getDatacenters();
        List<Host> hosts = context.getHosts();
        List<VM> vms = context.getVms();
        List<User> users = context.getUsers();

        infra.datacenterCount = datacenters != null ? datacenters.size() : 0;
        infra.hostCount = hosts != null ? hosts.size() : 0;
        infra.vmCount = vms != null ? vms.size() : 0;
        infra.userCount = users != null ? users.size() : 0;

        if (hosts != null && !hosts.isEmpty()) {
            // Count active vs idle hosts
            infra.activeHostCount = (int) hosts.stream()
                .filter(h -> !h.getAssignedVMs().isEmpty())
                .count();
            infra.idleHostCount = infra.hostCount - infra.activeHostCount;

            // Resource totals
            infra.totalCpuCores = hosts.stream().mapToInt(Host::getNumberOfCpuCores).sum();
            infra.totalGpus = hosts.stream().mapToInt(Host::getNumberOfGpus).sum();
            infra.totalRamMB = hosts.stream().mapToLong(Host::getRamCapacityMB).sum();
            infra.totalStorageMB = hosts.stream().mapToLong(Host::getHardDriveCapacityMB).sum();

            // Calculate utilization
            double totalCpuUtil = 0;
            double totalGpuUtil = 0;
            int activeCount = 0;

            for (Host host : hosts) {
                if (!host.getAssignedVMs().isEmpty()) {
                    totalCpuUtil += host.getResourceUtilization();
                    // GPU utilization approximation
                    if (host.getNumberOfGpus() > 0) {
                        int allocatedGpus = host.getNumberOfGpus() - host.getAvailableGpus();
                        totalGpuUtil += (double) allocatedGpus / host.getNumberOfGpus();
                    }
                    activeCount++;
                }
            }

            if (activeCount > 0) {
                infra.avgCpuUtilization = totalCpuUtil / activeCount;
                infra.avgGpuUtilization = totalGpuUtil / activeCount;
            }

            // VMs per active host
            if (infra.activeHostCount > 0) {
                infra.vmsPerActiveHost = (double) infra.vmCount / infra.activeHostCount;
            }

            // Consolidation ratio (VMs / total hosts)
            infra.consolidationRatio = (double) infra.vmCount / infra.hostCount;
        }

        aggregatedMetrics.put("infrastructure.datacenterCount", infra.datacenterCount);
        aggregatedMetrics.put("infrastructure.hostCount", infra.hostCount);
        aggregatedMetrics.put("infrastructure.activeHostCount", infra.activeHostCount);
        aggregatedMetrics.put("infrastructure.vmCount", infra.vmCount);
        aggregatedMetrics.put("infrastructure.avgCpuUtilization", infra.avgCpuUtilization);
    }

    /**
     * Collects task metrics.
     */
    private void collectTaskMetrics(SimulationContext context) {
        SimulationSummary.TaskSummary tasks = summary.getTasks();
        List<Task> taskList = context.getTasks();

        if (taskList != null) {
            tasks.totalTasks = taskList.size();
            tasks.completedTasks = (int) taskList.stream().filter(Task::isCompleted).count();
            tasks.failedTasks = (int) taskList.stream()
                .filter(t -> t.isAssigned() && !t.isCompleted())
                .count();
            tasks.unassignedTasks = (int) taskList.stream()
                .filter(t -> !t.isAssigned())
                .count();

            if (tasks.totalTasks > 0) {
                tasks.completionRate = (double) tasks.completedTasks / tasks.totalTasks;
            }

            tasks.totalInstructionsExecuted = taskList.stream()
                .filter(Task::isCompleted)
                .mapToLong(Task::getInstructionLength)
                .sum();
        }

        aggregatedMetrics.put("tasks.completionRate", tasks.completionRate);
        aggregatedMetrics.put("tasks.totalInstructionsExecuted", tasks.totalInstructionsExecuted);
    }

    /**
     * Collects energy metrics from EnergyCalculationStep.
     */
    private void collectEnergyMetrics(SimulationContext context) {
        SimulationSummary.EnergySummary energy = summary.getEnergy();

        // Get energy metrics from context
        Object itEnergy = context.getMetric("energy.totalITEnergyKWh");
        Object facilityEnergy = context.getMetric("energy.totalFacilityEnergyKWh");
        Object pue = context.getMetric("energy.pue");
        Object avgPower = context.getMetric("energy.averagePowerWatts");
        Object peakPower = context.getMetric("energy.peakPowerWatts");
        Object carbonRegion = context.getMetric("energy.carbonRegion");
        Object carbonIntensity = context.getMetric("energy.carbonIntensityKgPerKWh");
        Object carbonFootprint = context.getMetric("energy.carbonFootprintKg");
        Object costPerKWh = context.getMetric("energy.electricityCostPerKWh");
        Object totalCost = context.getMetric("energy.estimatedCostDollars");
        Object energyPerTask = context.getMetric("energy.energyPerTaskJoules");
        Object efficiency = context.getMetric("energy.energyEfficiencyIpsPerWatt");

        if (itEnergy != null) energy.totalITEnergyKWh = ((Number) itEnergy).doubleValue();
        if (facilityEnergy != null) energy.totalFacilityEnergyKWh = ((Number) facilityEnergy).doubleValue();
        if (pue != null) energy.pue = ((Number) pue).doubleValue();
        if (avgPower != null) energy.averagePowerWatts = ((Number) avgPower).doubleValue();
        if (peakPower != null) energy.peakPowerWatts = ((Number) peakPower).doubleValue();
        if (carbonRegion != null) energy.carbonRegion = carbonRegion.toString();
        if (carbonIntensity != null) energy.carbonIntensityKgPerKWh = ((Number) carbonIntensity).doubleValue();
        if (carbonFootprint != null) energy.carbonFootprintKg = ((Number) carbonFootprint).doubleValue();
        if (costPerKWh != null) energy.electricityCostPerKWh = ((Number) costPerKWh).doubleValue();
        if (totalCost != null) energy.estimatedCostDollars = ((Number) totalCost).doubleValue();
        if (energyPerTask != null) energy.energyPerTaskJoules = ((Number) energyPerTask).doubleValue();
        if (efficiency != null) energy.energyEfficiencyIpsPerWatt = ((Number) efficiency).doubleValue();
    }

    /**
     * Collects performance metrics and calculates percentiles.
     */
    private void collectPerformanceMetrics(SimulationContext context) {
        SimulationSummary.PerformanceSummary perf = summary.getPerformance();
        List<Task> taskList = context.getTasks();

        // Get basic metrics from TaskExecutionStep
        Object makespan = context.getMetric("taskExecution.makespan");
        Object throughput = context.getMetric("taskExecution.throughput");
        Object avgWait = context.getMetric("taskExecution.avgWaitingTime");
        Object avgTurnaround = context.getMetric("taskExecution.avgTurnaroundTime");
        Object avgExec = context.getMetric("taskExecution.avgExecutionTime");

        if (makespan != null) perf.makespanSeconds = ((Number) makespan).longValue();
        if (throughput != null) perf.throughputTasksPerSecond = ((Number) throughput).doubleValue();
        if (avgWait != null) perf.avgWaitingTimeSeconds = ((Number) avgWait).doubleValue();
        if (avgTurnaround != null) perf.avgTurnaroundTimeSeconds = ((Number) avgTurnaround).doubleValue();
        if (avgExec != null) perf.avgExecutionTimeSeconds = ((Number) avgExec).doubleValue();

        // Calculate percentiles from task data
        if (taskList != null && !taskList.isEmpty()) {
            List<Long> turnaroundTimes = taskList.stream()
                .filter(Task::isCompleted)
                .map(Task::getTurnaroundTime)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

            List<Long> waitingTimes = taskList.stream()
                .filter(Task::isCompleted)
                .map(Task::getWaitingTime)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

            if (!turnaroundTimes.isEmpty()) {
                perf.minTurnaroundTimeSeconds = turnaroundTimes.get(0);
                perf.maxTurnaroundTimeSeconds = turnaroundTimes.get(turnaroundTimes.size() - 1);
                perf.p50TurnaroundTimeSeconds = getPercentile(turnaroundTimes, 50);
                perf.p90TurnaroundTimeSeconds = getPercentile(turnaroundTimes, 90);
                perf.p99TurnaroundTimeSeconds = getPercentile(turnaroundTimes, 99);
            }

            if (!waitingTimes.isEmpty()) {
                perf.minWaitingTimeSeconds = waitingTimes.get(0);
                perf.maxWaitingTimeSeconds = waitingTimes.get(waitingTimes.size() - 1);
            }

            // Calculate load balance index (standard deviation of tasks per VM)
            perf.loadBalanceIndex = calculateLoadBalanceIndex(context);
            perf.taskDistributionStdDev = calculateTaskDistributionStdDev(context);
        }

        aggregatedMetrics.put("performance.p90TurnaroundTimeSeconds", perf.p90TurnaroundTimeSeconds);
        aggregatedMetrics.put("performance.p99TurnaroundTimeSeconds", perf.p99TurnaroundTimeSeconds);
        aggregatedMetrics.put("performance.loadBalanceIndex", perf.loadBalanceIndex);
    }

    /**
     * Calculates SLA compliance for configured thresholds.
     *
     * SLA compliance is calculated against ALL tasks, not just completed ones.
     * Unassigned and failed tasks count as SLA violations since they were
     * never successfully completed within the threshold.
     */
    private void calculateSLACompliance(SimulationContext context) {
        SimulationSummary.SLASummary sla = summary.getSla();
        List<Task> taskList = context.getTasks();

        sla.slaThresholdSeconds = primarySLAThreshold;

        if (taskList == null || taskList.isEmpty()) {
            return;
        }

        int totalTasks = taskList.size();

        List<Task> completedTasks = taskList.stream()
            .filter(Task::isCompleted)
            .collect(Collectors.toList());

        // Count unassigned tasks (these are SLA violations - never completed)
        int unassignedTasks = (int) taskList.stream()
            .filter(t -> !t.isAssigned())
            .count();

        // Count failed tasks (assigned but not completed - also SLA violations)
        int failedTasks = (int) taskList.stream()
            .filter(t -> t.isAssigned() && !t.isCompleted())
            .count();

        // Calculate compliance for primary threshold
        int withinSLA = 0;
        int beyondSLA = 0;

        for (Task task : completedTasks) {
            Long turnaround = task.getTurnaroundTime();
            if (turnaround != null) {
                if (turnaround <= primarySLAThreshold) {
                    withinSLA++;
                } else {
                    beyondSLA++;
                }
            }
        }

        // Unassigned and failed tasks count as beyond SLA (never completed)
        beyondSLA += unassignedTasks + failedTasks;

        sla.tasksWithinSLA = withinSLA;
        sla.tasksBeyondSLA = beyondSLA;

        // SLA compliance calculated against ALL tasks, not just completed ones
        sla.slaCompliancePercent = totalTasks == 0 ? 0 :
            (double) withinSLA / totalTasks * 100.0;

        // Calculate compliance for all thresholds (also against total tasks)
        for (Long threshold : slaThresholds) {
            int within = 0;
            for (Task task : completedTasks) {
                Long turnaround = task.getTurnaroundTime();
                if (turnaround != null && turnaround <= threshold) {
                    within++;
                }
            }
            double compliance = (double) within / totalTasks * 100.0;
            sla.complianceByThreshold.put(threshold, compliance);
        }

        aggregatedMetrics.put("sla.primaryCompliancePercent", sla.slaCompliancePercent);
        aggregatedMetrics.put("sla.unassignedTasks", unassignedTasks);
        aggregatedMetrics.put("sla.failedTasks", failedTasks);
        for (Long threshold : slaThresholds) {
            aggregatedMetrics.put("sla.compliance." + threshold + "s",
                sla.complianceByThreshold.get(threshold));
        }
    }

    /**
     * Collects per-datacenter summaries.
     */
    private void collectDatacenterSummaries(SimulationContext context) {
        List<CloudDatacenter> datacenters = context.getDatacenters();
        if (datacenters == null) return;

        for (CloudDatacenter dc : datacenters) {
            SimulationSummary.DatacenterSummary dcSummary = new SimulationSummary.DatacenterSummary();
            dcSummary.name = dc.getName();
            dcSummary.hostCount = dc.getHosts().size();
            dcSummary.vmCount = dc.getHosts().stream()
                .mapToInt(h -> h.getAssignedVMs().size())
                .sum();
            dcSummary.userCount = dc.getCustomers().size();
            dcSummary.energyKWh = dc.getTotalEnergyConsumedKWh();
            dcSummary.peakPowerWatts = dc.getTotalMomentaryPowerDraw();

            // Calculate average utilization
            if (!dc.getHosts().isEmpty()) {
                dcSummary.avgUtilization = dc.getHosts().stream()
                    .mapToDouble(Host::getResourceUtilization)
                    .average()
                    .orElse(0.0);
            }

            summary.getDatacenters().add(dcSummary);
        }
    }

    /**
     * Collects per-host summaries.
     */
    private void collectHostSummaries(SimulationContext context) {
        List<Host> hosts = context.getHosts();
        List<CloudDatacenter> datacenters = context.getDatacenters();
        if (hosts == null) return;

        // Create datacenter lookup
        Map<Long, String> dcNames = new HashMap<>();
        if (datacenters != null) {
            for (CloudDatacenter dc : datacenters) {
                dcNames.put(dc.getId(), dc.getName());
            }
        }

        for (Host host : hosts) {
            SimulationSummary.HostSummary hostSummary = new SimulationSummary.HostSummary();
            hostSummary.id = host.getId();
            hostSummary.datacenterName = dcNames.get(host.getAssignedDatacenterId());
            hostSummary.vmCount = host.getAssignedVMs().size();
            hostSummary.cpuCores = host.getNumberOfCpuCores();
            hostSummary.gpus = host.getNumberOfGpus();
            hostSummary.cpuUtilization = host.getResourceUtilization();
            hostSummary.energyKWh = host.getTotalEnergyConsumedKWh();
            hostSummary.activeSeconds = host.getActiveSeconds();
            hostSummary.idleSeconds = host.getSecondsIDLE();

            summary.getHosts().add(hostSummary);
        }
    }

    /**
     * Collects per-user summaries.
     */
    private void collectUserSummaries(SimulationContext context) {
        List<User> users = context.getUsers();
        List<Task> tasks = context.getTasks();
        if (users == null) return;

        // Group tasks by user
        Map<String, List<Task>> tasksByUser = tasks != null ?
            tasks.stream().collect(Collectors.groupingBy(Task::getUserId)) :
            new HashMap<>();

        for (User user : users) {
            SimulationSummary.UserSummary userSummary = new SimulationSummary.UserSummary();
            userSummary.name = user.getName();
            userSummary.vmCount = user.getVirtualMachines().size();

            List<Task> userTasks = tasksByUser.getOrDefault(user.getName(), new ArrayList<>());
            userSummary.taskCount = userTasks.size();
            userSummary.completedTasks = (int) userTasks.stream().filter(Task::isCompleted).count();

            if (userSummary.taskCount > 0) {
                userSummary.completionRate = (double) userSummary.completedTasks / userSummary.taskCount;
            }

            // Average turnaround time
            double avgTurnaround = userTasks.stream()
                .filter(Task::isCompleted)
                .map(Task::getTurnaroundTime)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            userSummary.avgTurnaroundTimeSeconds = avgTurnaround;

            // Session duration
            if (user.getFinishTimestamp() != null && user.getStartTimestamp() != null) {
                userSummary.sessionDurationSeconds = user.getFinishTimestamp() - user.getStartTimestamp();
            }

            summary.getUsers().add(userSummary);
        }
    }

    /**
     * Collects per-workload type summaries.
     */
    private void collectWorkloadSummaries(SimulationContext context) {
        List<Task> tasks = context.getTasks();
        if (tasks == null || tasks.isEmpty()) return;

        // Group tasks by workload type
        Map<WorkloadType, List<Task>> tasksByWorkload = tasks.stream()
            .collect(Collectors.groupingBy(Task::getWorkloadType));

        for (WorkloadType type : WorkloadType.values()) {
            List<Task> workloadTasks = tasksByWorkload.getOrDefault(type, new ArrayList<>());
            if (workloadTasks.isEmpty()) continue;

            SimulationSummary.WorkloadSummary workloadSummary = new SimulationSummary.WorkloadSummary();
            workloadSummary.workloadType = type.name();
            workloadSummary.taskCount = workloadTasks.size();
            workloadSummary.completedTasks = (int) workloadTasks.stream().filter(Task::isCompleted).count();

            // Average execution time
            workloadSummary.avgExecutionTimeSeconds = workloadTasks.stream()
                .filter(Task::isCompleted)
                .mapToLong(Task::getTaskCpuExecTime)
                .average()
                .orElse(0.0);

            // Total instructions
            workloadSummary.totalInstructions = workloadTasks.stream()
                .mapToLong(Task::getInstructionLength)
                .sum();

            summary.getWorkloads().add(workloadSummary);
        }
    }

    /**
     * Calculates the percentile value from a sorted list.
     */
    private double getPercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * Calculates load balance index based on task distribution across VMs.
     * Lower values indicate better load balance (0 = perfect balance).
     */
    private double calculateLoadBalanceIndex(SimulationContext context) {
        List<VM> vms = context.getVms();
        if (vms == null || vms.isEmpty()) return 0;

        // Count tasks per VM (including completed tasks)
        List<Integer> tasksPerVM = vms.stream()
            .filter(vm -> vm.isAssignedToHost())
            .map(vm -> vm.getFinishedTasks().size() + vm.getAssignedTasks().size())
            .collect(Collectors.toList());

        if (tasksPerVM.isEmpty()) return 0;

        double mean = tasksPerVM.stream().mapToInt(Integer::intValue).average().orElse(0);
        if (mean == 0) return 0;

        double sumSquaredDiffs = tasksPerVM.stream()
            .mapToDouble(count -> Math.pow(count - mean, 2))
            .sum();

        double stdDev = Math.sqrt(sumSquaredDiffs / tasksPerVM.size());

        // Coefficient of variation (normalized standard deviation)
        return stdDev / mean;
    }

    /**
     * Calculates standard deviation of task distribution.
     */
    private double calculateTaskDistributionStdDev(SimulationContext context) {
        List<VM> vms = context.getVms();
        if (vms == null || vms.isEmpty()) return 0;

        List<Integer> tasksPerVM = vms.stream()
            .filter(vm -> vm.isAssignedToHost())
            .map(vm -> vm.getFinishedTasks().size() + vm.getAssignedTasks().size())
            .collect(Collectors.toList());

        if (tasksPerVM.isEmpty()) return 0;

        double mean = tasksPerVM.stream().mapToInt(Integer::intValue).average().orElse(0);

        double sumSquaredDiffs = tasksPerVM.stream()
            .mapToDouble(count -> Math.pow(count - mean, 2))
            .sum();

        return Math.sqrt(sumSquaredDiffs / tasksPerVM.size());
    }

    /**
     * Records aggregated metrics to the simulation context.
     */
    private void recordMetrics(SimulationContext context) {
        for (Map.Entry<String, Object> entry : aggregatedMetrics.entrySet()) {
            context.recordMetric("metricsCollection." + entry.getKey(), entry.getValue());
        }

        context.recordMetric("metricsCollection.stepDurationMs", stepEndTimeMs - stepStartTimeMs);
        context.recordMetric("metricsCollection.summaryGenerated", true);

        // Store the summary object in context for ReportingStep to access
        context.setSimulationSummary(summary);
    }

    /**
     * Logs a summary of metrics collection.
     */
    private void logSummary() {
        logInfo("Metrics Collection Complete");
        logInfo("  Simulation ID: " + summary.getSimulationId());
        logInfo("  Duration: " + summary.getSimulationDurationSeconds() + " seconds");

        SimulationSummary.InfrastructureSummary infra = summary.getInfrastructure();
        logInfo("  Infrastructure: " + infra.datacenterCount + " DCs, " +
            infra.hostCount + " hosts (" + infra.activeHostCount + " active), " +
            infra.vmCount + " VMs");

        SimulationSummary.TaskSummary tasks = summary.getTasks();
        logInfo("  Tasks: " + tasks.completedTasks + "/" + tasks.totalTasks +
            " completed (" + String.format("%.1f%%", tasks.completionRate * 100) + ")");

        SimulationSummary.PerformanceSummary perf = summary.getPerformance();
        logInfo("  Performance: makespan=" + perf.makespanSeconds + "s, " +
            "p90=" + String.format("%.1f", perf.p90TurnaroundTimeSeconds) + "s, " +
            "p99=" + String.format("%.1f", perf.p99TurnaroundTimeSeconds) + "s");

        SimulationSummary.SLASummary sla = summary.getSla();
        logInfo("  SLA Compliance (" + sla.slaThresholdSeconds + "s): " +
            String.format("%.1f%%", sla.slaCompliancePercent));

        logInfo("  Load Balance Index: " + String.format("%.4f", perf.loadBalanceIndex));
    }

    private void logInfo(String message) {
        System.out.println("[INFO] MetricsCollectionStep: " + message);
    }

    @Override
    public String getStepName() {
        return "Metrics Collection";
    }

    // Configuration methods

    /**
     * Sets the primary SLA threshold in seconds.
     */
    public void setPrimarySLAThreshold(long thresholdSeconds) {
        this.primarySLAThreshold = thresholdSeconds;
        if (!slaThresholds.contains(thresholdSeconds)) {
            slaThresholds.add(0, thresholdSeconds);
        }
    }

    /**
     * Adds an SLA threshold to track.
     */
    public void addSLAThreshold(long thresholdSeconds) {
        if (!slaThresholds.contains(thresholdSeconds)) {
            slaThresholds.add(thresholdSeconds);
            Collections.sort(slaThresholds);
        }
    }

    /**
     * Clears all SLA thresholds and sets new ones.
     */
    public void setSLAThresholds(long... thresholds) {
        slaThresholds.clear();
        for (long t : thresholds) {
            slaThresholds.add(t);
        }
        Collections.sort(slaThresholds);
        if (!slaThresholds.isEmpty()) {
            primarySLAThreshold = slaThresholds.get(0);
        }
    }

    // Getters

    /**
     * Gets the generated SimulationSummary.
     */
    public SimulationSummary getSummary() {
        return summary;
    }

    /**
     * Gets all aggregated metrics.
     */
    public Map<String, Object> getAggregatedMetrics() {
        return aggregatedMetrics;
    }

    /**
     * Gets a specific metric value.
     */
    public Object getMetric(String key) {
        return aggregatedMetrics.get(key);
    }

    /**
     * Gets SLA compliance percentage for the primary threshold.
     */
    public double getSLACompliancePercent() {
        return summary != null ? summary.getSla().slaCompliancePercent : 0;
    }

    /**
     * Gets load balance index.
     */
    public double getLoadBalanceIndex() {
        return summary != null ? summary.getPerformance().loadBalanceIndex : 0;
    }

    /**
     * Gets the JSON representation of the summary.
     */
    public String getSummaryJson() {
        return summary != null ? summary.toJson() : "{}";
    }
}
