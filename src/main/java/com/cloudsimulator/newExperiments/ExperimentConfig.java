package com.cloudsimulator.newExperiments;

import com.cloudsimulator.config.DatacenterConfig;
import com.cloudsimulator.config.ExperimentConfiguration;
import com.cloudsimulator.config.HostConfig;
import com.cloudsimulator.config.TaskConfig;
import com.cloudsimulator.config.UserConfig;
import com.cloudsimulator.config.VMConfig;
import com.cloudsimulator.enums.ComputeType;
import com.cloudsimulator.enums.WorkloadType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editable infrastructure configuration — datacenter, hosts, VMs, user,
 * workloads, per-scenario task counts, and seeds. {@link #defaults()} returns
 * values <b>identical</b> to {@code buildScenarioConfig()} in the legacy runners
 * (shared byte-for-byte across all three studies). Tweak any field in a Main.
 *
 * <p>{@link #toExperimentConfiguration(int, long)} follows the runner's
 * {@code buildScenarioConfig(scenario, seed)}, and <b>guarantees every host
 * uses the measurement-based power model</b> (it throws otherwise unless you
 * explicitly opt out). One deliberate deviation from the legacy runners:
 * {@code generateTasks} decouples the workload-type and instruction-length
 * cycles (the legacy shared-index cycling created a type-size confound — see
 * the method javadoc).</p>
 */
public final class ExperimentConfig {

    /** A group of identical hosts. */
    public static final class HostGroup {
        public int count;
        public long ipsPerSecond;
        public int cpuCores;
        public ComputeType computeType;
        public int gpus;
        public int ramMB;
        public int networkMbps;
        public int storageMB;
        public String powerModel;

        public HostGroup(int count, long ipsPerSecond, int cpuCores, ComputeType computeType,
                         int gpus, int ramMB, int networkMbps, int storageMB, String powerModel) {
            this.count = count;
            this.ipsPerSecond = ipsPerSecond;
            this.cpuCores = cpuCores;
            this.computeType = computeType;
            this.gpus = gpus;
            this.ramMB = ramMB;
            this.networkMbps = networkMbps;
            this.storageMB = storageMB;
            this.powerModel = powerModel;
        }
    }

    /** A group of identical VMs. */
    public static final class VMGroup {
        public int count;
        public long ipsPerVCPU;
        public int vcpus;
        public int gpus;
        public int ramMB;
        public int storageMB;
        public int bandwidthMbps;
        public ComputeType computeType;

        public VMGroup(int count, long ipsPerVCPU, int vcpus, int gpus, int ramMB,
                       int storageMB, int bandwidthMbps, ComputeType computeType) {
            this.count = count;
            this.ipsPerVCPU = ipsPerVCPU;
            this.vcpus = vcpus;
            this.gpus = gpus;
            this.ramMB = ramMB;
            this.storageMB = storageMB;
            this.bandwidthMbps = bandwidthMbps;
            this.computeType = computeType;
        }
    }

    // ---- Datacenter ----
    public String datacenterName = "DC-Experiment";
    public int datacenterMaxHostCapacity = 50;
    public double datacenterMaxPowerDrawWatts = 400_000.0;

    // ---- User ----
    public String userName = "ExperimentUser";
    public int userMaxVMs = 5;
    public int userMaxConcurrentTasks = 5;
    public int userMaxSessions = 5;

    // ---- Hosts / VMs ----
    public List<HostGroup> hostGroups = new ArrayList<>();
    public List<VMGroup> vmGroups = new ArrayList<>();

    // ---- Workloads / tasks ----
    public WorkloadType[] cpuWorkloads = {
        WorkloadType.SEVEN_ZIP, WorkloadType.DATABASE, WorkloadType.LLM_CPU,
        WorkloadType.IMAGE_GEN_CPU, WorkloadType.CINEBENCH, WorkloadType.VERACRYPT
    };
    public WorkloadType[] gpuWorkloads = {
        WorkloadType.FURMARK, WorkloadType.IMAGE_GEN_GPU, WorkloadType.LLM_GPU
    };
    public long[] instructionLengths = {
        100_000_000L, 200_000_000L, 300_000_000L, 500_000_000L,
        100_000_000L, 200_000_000L, 300_000_000L, 500_000_000L,
        25_000_000_000L, 40_000_000_000L
    };
    public int[][] scenarioTaskCounts = {
        {250, 250},  // Scenario 1: Balanced
        {100, 400},  // Scenario 2: GPU Stress
        {400, 100}   // Scenario 3: CPU Stress
    };
    public String[] scenarioNames = {"Balanced", "GPU_Stress", "CPU_Stress"};

    // ---- Seeds ----
    public long baseSeed = 200L;
    public int numRuns = 10;

    /**
     * If false (default), {@link #toExperimentConfiguration} throws when any host
     * group is not on the measurement-based power model. Leave this false.
     */
    public boolean allowNonMeasurementPowerModel = false;

    /** Configuration identical to the legacy runners (1 DC, 40 hosts, 60 VMs, 1 user). */
    public static ExperimentConfig defaults() {
        ExperimentConfig c = new ExperimentConfig();
        // 40 hosts: 16 CPU, 12 GPU, 12 MIXED
        c.hostGroups.add(new HostGroup(16, 2_500_000_000L, 16, ComputeType.CPU_ONLY, 0,
            65536, 20000, 2097152, "MeasurementBasedPowerModel"));
        c.hostGroups.add(new HostGroup(12, 2_800_000_000L, 8, ComputeType.GPU_ONLY, 4,
            65536, 20000, 2097152, "MeasurementBasedPowerModel"));
        c.hostGroups.add(new HostGroup(12, 3_000_000_000L, 32, ComputeType.CPU_GPU_MIXED, 4,
            131072, 20000, 2097152, "MeasurementBasedPowerModel"));

        // 60 VMs: 20 CPU + 20 GPU + 20 MIXED, each 8 fast / 8 medium / 4 slow
        c.vmGroups.add(new VMGroup(8, 5_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));
        c.vmGroups.add(new VMGroup(8, 2_000_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));
        c.vmGroups.add(new VMGroup(4, 500_000_000L, 4, 0, 4096, 102400, 1000, ComputeType.CPU_ONLY));

        c.vmGroups.add(new VMGroup(8, 5_000_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.GPU_ONLY));
        c.vmGroups.add(new VMGroup(8, 2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));
        c.vmGroups.add(new VMGroup(4, 500_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.GPU_ONLY));

        c.vmGroups.add(new VMGroup(8, 5_000_000_000L, 4, 2, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));
        c.vmGroups.add(new VMGroup(8, 2_000_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));
        c.vmGroups.add(new VMGroup(4, 500_000_000L, 4, 1, 4096, 102400, 1000, ComputeType.CPU_GPU_MIXED));
        return c;
    }

    /** Number of scenarios this config defines. */
    public int scenarioCount() {
        return scenarioNames.length;
    }

    /**
     * Builds the {@link ExperimentConfiguration} for a (1-based) scenario and seed,
     * exactly as {@code buildScenarioConfig(scenario, seed)} did.
     */
    public ExperimentConfiguration toExperimentConfiguration(int scenario, long seed) {
        ExperimentConfiguration config = new ExperimentConfiguration();
        config.setRandomSeed(seed);

        config.addDatacenterConfig(new DatacenterConfig(
            datacenterName, datacenterMaxHostCapacity, datacenterMaxPowerDrawWatts));

        for (HostGroup g : hostGroups) {
            requireMeasurementModel(g.powerModel);
            for (int i = 0; i < g.count; i++) {
                config.addHostConfig(new HostConfig(
                    g.ipsPerSecond, g.cpuCores, g.computeType, g.gpus,
                    g.ramMB, g.networkMbps, g.storageMB, g.powerModel));
            }
        }

        for (VMGroup g : vmGroups) {
            for (int i = 0; i < g.count; i++) {
                config.addVMConfig(new VMConfig(userName,
                    g.ipsPerVCPU, g.vcpus, g.gpus, g.ramMB, g.storageMB, g.bandwidthMbps, g.computeType));
            }
        }

        int idx = scenario - 1;
        int cpuCount = scenarioTaskCounts[idx][0];
        int gpuCount = scenarioTaskCounts[idx][1];

        List<TaskConfig> tasks = new ArrayList<>();
        tasks.addAll(generateTasks(cpuCount, cpuWorkloads, "CPU", scenario));
        tasks.addAll(generateTasks(gpuCount, gpuWorkloads, "GPU", scenario));
        for (TaskConfig tc : tasks) {
            config.addTaskConfig(tc);
        }

        Map<WorkloadType, Integer> taskCounts = new HashMap<>();
        for (TaskConfig tc : tasks) {
            taskCounts.merge(tc.getWorkloadType(), 1, Integer::sum);
        }
        config.addUserConfig(new UserConfig(userName,
            List.of(datacenterName), userMaxVMs, userMaxConcurrentTasks, userMaxSessions, taskCounts));

        return config;
    }

    /**
     * Generates the task list, cycling through the workload types and
     * instruction lengths with DECOUPLED phases.
     *
     * With the legacy shared index ({@code types[i % T]},
     * {@code lengths[i % L]}), type-length pairs whose indices differ in
     * residue mod gcd(T, L) never occur: with 6 CPU types and 10 lengths only
     * same-parity pairs exist, so the huge 25G/40G lengths always land on a
     * fixed subset of workload types — a type-size confound (workload type
     * sets the power profile, length sets the duration). Advancing the length
     * phase by one after every lcm(T, L) tasks visits every (type, length)
     * pair exactly once per T*L tasks: deterministic, identical for every
     * seed and algorithm arm, and uniformly balanced.
     */
    private List<TaskConfig> generateTasks(int count, WorkloadType[] types, String prefix, int scenario) {
        List<TaskConfig> tasks = new ArrayList<>();
        int block = lcm(types.length, instructionLengths.length);
        for (int i = 0; i < count; i++) {
            WorkloadType wt = types[i % types.length];
            long instrLen = instructionLengths[(i + i / block) % instructionLengths.length];
            String name = "S" + scenario + "_" + prefix + "_" + wt.name() + "_" + i;
            tasks.add(new TaskConfig(name, userName, instrLen, wt));
        }
        return tasks;
    }

    private static int lcm(int a, int b) {
        return a / gcd(a, b) * b;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    private void requireMeasurementModel(String powerModel) {
        if (allowNonMeasurementPowerModel) {
            return;
        }
        if (powerModel == null || !powerModel.startsWith("MeasurementBasedPowerModel")) {
            throw new IllegalStateException(
                "Host power model must be MeasurementBasedPowerModel (was: " + powerModel
                + "). Set allowNonMeasurementPowerModel=true to override deliberately.");
        }
    }
}
