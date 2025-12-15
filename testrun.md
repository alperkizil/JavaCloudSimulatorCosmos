cd C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos; "JAVA_HOME=C:\\Program Files\\Apache NetBeans\\jdk" cmd /c "\"C:\\Program Files\\Apache NetBeans\\java\\maven\\bin\\mvn.cmd\" -Dexec.mainClass=com.cloudsimulator.BatchExperimentMain -Dexec.classpathScope=test -Dexec.vmArgs= -Dexec.appArgs= \"-Dmaven.ext.class.path=C:\\Program Files\\Apache NetBeans\\java\\maven-nblib\\netbeans-eventspy.jar\" --no-transfer-progress process-test-classes org.codehaus.mojo:exec-maven-plugin:3.1.0:java"
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::staticFieldBase has been called by com.google.inject.internal.aop.HiddenClassDefiner (file:/C:/Program%20Files/Apache%20NetBeans/java/maven/lib/guice-5.1.0-classes.jar)
WARNING: Please consider reporting this to the maintainers of class com.google.inject.internal.aop.HiddenClassDefiner
WARNING: sun.misc.Unsafe::staticFieldBase will be removed in a future release
Scanning for projects...

------------< com.cloudsimulator:JavaCloudSimulatorCosmos >-------------
Building JavaCloudSimulatorCosmos 1.0-SNAPSHOT
  from pom.xml
--------------------------------[ jar ]---------------------------------
6 problems were encountered while building the effective model for org.openjfx:javafx-controls:jar:21.0.1 during dependency collection step for project (use -X to see details)

--- resources:3.3.1:resources (default-resources) @ JavaCloudSimulatorCosmos ---
skip non existing resourceDirectory C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\src\main\resources

--- compiler:3.11.0:compile (default-compile) @ JavaCloudSimulatorCosmos ---
Changes detected - recompiling the module! :source
Compiling 110 source files with javac [debug target 17] to target\classes
location of system modules is not set in conjunction with -source 17
  not setting the location of system modules may lead to class files that cannot run on JDK 17
    --release 17 is recommended instead of -source 17 -target 17 because it sets the location of system modules automatically
/C:/Users/Alper/Documents/NetBeansProjects/JavaCloudSimulatorCosmos/src/main/java/com/cloudsimulator/gui/DatacenterPanel.java: Some input files use or override a deprecated API.
/C:/Users/Alper/Documents/NetBeansProjects/JavaCloudSimulatorCosmos/src/main/java/com/cloudsimulator/gui/DatacenterPanel.java: Recompile with -Xlint:deprecation for details.

--- resources:3.3.1:testResources (default-testResources) @ JavaCloudSimulatorCosmos ---
skip non existing resourceDirectory C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\src\test\resources

--- compiler:3.11.0:testCompile (default-testCompile) @ JavaCloudSimulatorCosmos ---
Changes detected - recompiling the module! :dependency
Compiling 21 source files with javac [debug target 17] to target\test-classes
location of system modules is not set in conjunction with -source 17
  not setting the location of system modules may lead to class files that cannot run on JDK 17
    --release 17 is recommended instead of -source 17 -target 17 because it sets the location of system modules automatically

--- exec:3.1.0:java (default-cli) @ JavaCloudSimulatorCosmos ---
========================================
BATCH EXPERIMENT FILE PROCESSOR
========================================
Selected folder: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\configs\sampleScenario_single_user

Found 10 .cosc file(s)

========================================
FILE SEEDS
========================================
  1. 10_20251213_194237_010.cosc                        Seed: 10
  2. 1_20251213_194237_001.cosc                         Seed: 1
  3. 2_20251213_194237_002.cosc                         Seed: 2
  4. 3_20251213_194237_003.cosc                         Seed: 3
  5. 4_20251213_194237_004.cosc                         Seed: 4
  6. 5_20251213_194237_005.cosc                         Seed: 5
  7. 6_20251213_194237_006.cosc                         Seed: 6
  8. 7_20251213_194237_007.cosc                         Seed: 7
  9. 8_20251213_194237_008.cosc                         Seed: 8
 10. 9_20251213_194237_009.cosc                         Seed: 9

========================================
SHARED CONFIGURATION PROPERTIES
(Same across all files in batch)
========================================

----------------------------------------
DATACENTERS (1)
----------------------------------------
#     Name                 Max Hosts       Max Power (W)  
-------------------------------------------------------------
1     London               100             90000.0        

----------------------------------------
HOSTS (12)
----------------------------------------
Count    IPS             Cores    Compute Type    GPUs   RAM (MB)     Power Model    
--------------------------------------------------------------------------------
5        2.0B            8        CPU_ONLY        0      103809024    MeasurementBasedPowerModel
5        3.0B            12       CPU_GPU_MIXED   1      103809024    MeasurementBasedPowerModel
2        4.0B            16       GPU_ONLY        1      103809024    MeasurementBasedPowerModel

----------------------------------------
USERS (1)
----------------------------------------
Name                 Datacenters               GPU VMs  CPU VMs  Mixed    Tasks     
--------------------------------------------------------------------------------
FreshFruits          London                    3        10       2        400       

Task breakdown by workload type:
  FreshFruits:
    LLM_GPU             : 100
    DATABASE            : 100
    IMAGE_GEN_GPU       : 100
    VERACRYPT           : 100

----------------------------------------
VMs (15)
----------------------------------------
Compute Type    Owner                vCPUs    GPUs   RAM (MB)   IPS/vCPU    
--------------------------------------------------------------------------------
CPU_ONLY        FreshFruits          2        0      2048       2.0B         (x10)
GPU_ONLY        FreshFruits          12       1      8096       4.0B         (x3)
CPU_GPU_MIXED   FreshFruits          4        1      4096       3.0B         (x2)

Summary by compute type:
  CPU_ONLY       : 10 VMs
  GPU_ONLY       : 3 VMs
  CPU_GPU_MIXED  : 2 VMs

----------------------------------------
TASKS (400)
----------------------------------------
Summary by workload type:
  DATABASE            :  100 tasks, Instructions: min=1.1B, max=9.9B, avg=5.7B
  IMAGE_GEN_GPU       :  100 tasks, Instructions: min=1.2B, max=10.0B, avg=5.8B
  LLM_GPU             :  100 tasks, Instructions: min=1.1B, max=10.0B, avg=5.4B
  VERACRYPT           :  100 tasks, Instructions: min=1.2B, max=9.6B, avg=5.7B

Tasks by user:
  FreshFruits         : 400 tasks

========================================
RUNNING EXPERIMENTS
========================================

Experiment 1/10 (Seed: 10)
----------------------------------------
[2025-12-15 13:10:40] [INFO] Loading configuration from object
[2025-12-15 13:10:40] [INFO] Configuration loaded. Random seed: 10
[2025-12-15 13:10:40] [INFO] ========================================
[2025-12-15 13:10:40] [INFO] Starting Multi-Objective Simulation
[2025-12-15 13:10:40] [INFO] Random Seed: 10
[2025-12-15 13:10:40] [INFO] ========================================
[2025-12-15 13:10:40] [INFO] Phase 1: Running setup steps...
[2025-12-15 13:10:40] [INFO]   Step 1/10: Initialization
[2025-12-15 13:10:40] [INFO]   Step 2/10: Host Placement (First Fit)
[2025-12-15 13:10:40] [INFO]   Step 3/10: User-Datacenter Mapping
[2025-12-15 13:10:40] [INFO]   Step 4/10: VM Placement (Best Fit)
[2025-12-15 13:10:40] [INFO] Phase 2: Running multi-objective optimization...
[2025-12-15 13:10:41] [INFO] Pareto front contains 7 solutions
[2025-12-15 13:10:41] [INFO] Phase 3: Simulating each Pareto front solution...
[2025-12-15 13:10:41] [INFO]   Simulating solution 1/7 (objectives: [51.00, 0.01])
VMExecutionStep: Starting VM Execution Loop
VMExecutionStep:   VMs assigned to hosts: 15
VMExecutionStep:   Tasks assigned to VMs: 400 / 400
VMExecutionStep: VM Execution Loop Completed
VMExecutionStep:   Total simulation time: 51 seconds
VMExecutionStep:   Tasks completed: 400 / 400
VMExecutionStep:   Peak concurrent tasks: 400
TaskExecutionStep: Analyzing task execution results...
TaskExecutionStep: Task Execution Analysis Complete
TaskExecutionStep:   Total tasks: 400
TaskExecutionStep:   Completed: 400
TaskExecutionStep:   Failed: 0
TaskExecutionStep:   Unassigned: 0
TaskExecutionStep:   Makespan: 51 seconds
TaskExecutionStep:   Avg waiting time: 19.97 seconds
TaskExecutionStep:   Avg turnaround time: 20.24 seconds
TaskExecutionStep:   Avg execution time: 0.27 seconds
TaskExecutionStep:   Throughput: 7.8431 tasks/second
TaskExecutionStep:   Users completed: 1 / 1
EnergyCalculationStep: Calculating energy consumption metrics...
EnergyCalculationStep: Energy Calculation Complete
EnergyCalculationStep:   Simulation duration: 51 seconds
EnergyCalculationStep:   IT Equipment Energy: 0.01 kWh (49214 Joules)
EnergyCalculationStep:   Total Facility Energy (PUE=1.50): 0.02 kWh
EnergyCalculationStep:   Average Power Draw: 964.98 W
EnergyCalculationStep:   Peak Power Draw: 909.48 W
EnergyCalculationStep:   Carbon Footprint (US_AVERAGE): 0.0086 kg CO2
EnergyCalculationStep:   Estimated Cost ($0.1000/kWh): $0.0021
EnergyCalculationStep:   Energy per Task: 184.55 Joules
EnergyCalculationStep:   Energy Efficiency: 4.61e+07 IPS/Watt
EnergyCalculationStep:   Per-Datacenter Energy:
EnergyCalculationStep:     London: 0.0137 kWh
MetricsCollectionStep: Collecting and aggregating simulation metrics...
MetricsCollectionStep: Metrics Collection Complete
MetricsCollectionStep:   Simulation ID: 23ae7032
MetricsCollectionStep:   Duration: 51 seconds
MetricsCollectionStep:   Infrastructure: 1 DCs, 12 hosts (8 active), 15 VMs
MetricsCollectionStep:   Tasks: 400/400 completed (100.0%)
MetricsCollectionStep:   Performance: makespan=51s, p90=41.0s, p99=49.0s
MetricsCollectionStep:   SLA Compliance (3600s): 100.0%
MetricsCollectionStep:   Load Balance Index: 0.6322
ReportingStep: Generating CSV reports...
ReportingStep: Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_44baa4
ReportingStep:   Generating summary report...
ReportingStep:     -> 23ae7032_summary.csv (1 rows, 926 B)
ReportingStep:   Generating datacenters report...
ReportingStep:     -> 23ae7032_datacenters.csv (1 rows, 413 B)
ReportingStep:   Generating hosts report...
ReportingStep:     -> 23ae7032_hosts.csv (12 rows, 2.23 KB)
ReportingStep:   Generating vms report...
ReportingStep:     -> 23ae7032_vms.csv (15 rows, 1.73 KB)
ReportingStep:   Generating tasks report...
ReportingStep:     -> 23ae7032_tasks.csv (400 rows, 44.46 KB)
ReportingStep:   Generating users report...
ReportingStep:     -> 23ae7032_users.csv (1 rows, 408 B)
ReportingStep: Report Generation Complete
ReportingStep:   Duration: 28 ms
ReportingStep:   Files generated: 6
ReportingStep:   Total size: 50.12 KB
ReportingStep:   Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_44baa4
ReportingStep:   Generated files:
ReportingStep:     - 23ae7032_summary.csv (1 rows)
ReportingStep:     - 23ae7032_datacenters.csv (1 rows)
ReportingStep:     - 23ae7032_hosts.csv (12 rows)
ReportingStep:     - 23ae7032_vms.csv (15 rows)
ReportingStep:     - 23ae7032_tasks.csv (400 rows)
ReportingStep:     - 23ae7032_users.csv (1 rows)
[2025-12-15 13:10:41] [INFO]   Solution 1: simulated makespan=51s, energy=0.0137 kWh
[2025-12-15 13:10:41] [INFO]   Simulating solution 2/7 (objectives: [45.00, 0.01])
VMExecutionStep: Starting VM Execution Loop
VMExecutionStep:   VMs assigned to hosts: 15
VMExecutionStep:   Tasks assigned to VMs: 400 / 400
VMExecutionStep: VM Execution Loop Completed
VMExecutionStep:   Total simulation time: 96 seconds
VMExecutionStep:   Tasks completed: 400 / 400
VMExecutionStep:   Peak concurrent tasks: 400
TaskExecutionStep: Analyzing task execution results...
TaskExecutionStep: Task Execution Analysis Complete
TaskExecutionStep:   Total tasks: 400
TaskExecutionStep:   Completed: 400
TaskExecutionStep:   Failed: 0
TaskExecutionStep:   Unassigned: 0
TaskExecutionStep:   Makespan: 45 seconds
TaskExecutionStep:   Avg waiting time: 19.10 seconds
TaskExecutionStep:   Avg turnaround time: 19.49 seconds
TaskExecutionStep:   Avg execution time: 0.38 seconds
TaskExecutionStep:   Throughput: 8.8889 tasks/second
TaskExecutionStep:   Users completed: 1 / 1
EnergyCalculationStep: Calculating energy consumption metrics...
EnergyCalculationStep: Energy Calculation Complete
EnergyCalculationStep:   Simulation duration: 96 seconds
EnergyCalculationStep:   IT Equipment Energy: 0.01 kWh (45316 Joules)
EnergyCalculationStep:   Total Facility Energy (PUE=1.50): 0.02 kWh
EnergyCalculationStep:   Average Power Draw: 472.04 W
EnergyCalculationStep:   Peak Power Draw: 909.48 W
EnergyCalculationStep:   Carbon Footprint (US_AVERAGE): 0.0079 kg CO2
EnergyCalculationStep:   Estimated Cost ($0.1000/kWh): $0.0019
EnergyCalculationStep:   Energy per Task: 169.93 Joules
EnergyCalculationStep:   Energy Efficiency: 5.01e+07 IPS/Watt
EnergyCalculationStep:   Per-Datacenter Energy:
EnergyCalculationStep:     London: 0.0126 kWh
MetricsCollectionStep: Collecting and aggregating simulation metrics...
MetricsCollectionStep: Metrics Collection Complete
MetricsCollectionStep:   Simulation ID: 7a336ed5
MetricsCollectionStep:   Duration: 96 seconds
MetricsCollectionStep:   Infrastructure: 1 DCs, 12 hosts (8 active), 15 VMs
MetricsCollectionStep:   Tasks: 400/400 completed (100.0%)
MetricsCollectionStep:   Performance: makespan=45s, p90=36.0s, p99=43.0s
MetricsCollectionStep:   SLA Compliance (3600s): 100.0%
MetricsCollectionStep:   Load Balance Index: 0.4770
ReportingStep: Generating CSV reports...
ReportingStep: Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_13f92a
ReportingStep:   Generating summary report...
ReportingStep:     -> 7a336ed5_summary.csv (1 rows, 925 B)
ReportingStep:   Generating datacenters report...
ReportingStep:     -> 7a336ed5_datacenters.csv (1 rows, 413 B)
ReportingStep:   Generating hosts report...
ReportingStep:     -> 7a336ed5_hosts.csv (12 rows, 2.23 KB)
ReportingStep:   Generating vms report...
ReportingStep:     -> 7a336ed5_vms.csv (15 rows, 1.73 KB)
ReportingStep:   Generating tasks report...
ReportingStep:     -> 7a336ed5_tasks.csv (400 rows, 44.49 KB)
ReportingStep:   Generating users report...
ReportingStep:     -> 7a336ed5_users.csv (1 rows, 408 B)
ReportingStep: Report Generation Complete
ReportingStep:   Duration: 21 ms
ReportingStep:   Files generated: 6
ReportingStep:   Total size: 50.15 KB
ReportingStep:   Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_13f92a
ReportingStep:   Generated files:
ReportingStep:     - 7a336ed5_summary.csv (1 rows)
ReportingStep:     - 7a336ed5_datacenters.csv (1 rows)
ReportingStep:     - 7a336ed5_hosts.csv (12 rows)
ReportingStep:     - 7a336ed5_vms.csv (15 rows)
ReportingStep:     - 7a336ed5_tasks.csv (400 rows)
ReportingStep:     - 7a336ed5_users.csv (1 rows)
[2025-12-15 13:10:41] [INFO]   Solution 2: simulated makespan=45s, energy=0.0126 kWh
[2025-12-15 13:10:41] [INFO]   Simulating solution 3/7 (objectives: [46.00, 0.01])
VMExecutionStep: Starting VM Execution Loop
VMExecutionStep:   VMs assigned to hosts: 15
VMExecutionStep:   Tasks assigned to VMs: 400 / 400
VMExecutionStep: [Tick 100] Progress: 10.3% (41/400 tasks completed, 359 executing)
VMExecutionStep: VM Execution Loop Completed
VMExecutionStep:   Total simulation time: 142 seconds
VMExecutionStep:   Tasks completed: 400 / 400
VMExecutionStep:   Peak concurrent tasks: 400
TaskExecutionStep: Analyzing task execution results...
TaskExecutionStep: Task Execution Analysis Complete
TaskExecutionStep:   Total tasks: 400
TaskExecutionStep:   Completed: 400
TaskExecutionStep:   Failed: 0
TaskExecutionStep:   Unassigned: 0
TaskExecutionStep:   Makespan: 46 seconds
TaskExecutionStep:   Avg waiting time: 19.16 seconds
TaskExecutionStep:   Avg turnaround time: 19.51 seconds
TaskExecutionStep:   Avg execution time: 0.35 seconds
TaskExecutionStep:   Throughput: 8.6957 tasks/second
TaskExecutionStep:   Users completed: 1 / 1
EnergyCalculationStep: Calculating energy consumption metrics...
EnergyCalculationStep: Energy Calculation Complete
EnergyCalculationStep:   Simulation duration: 142 seconds
EnergyCalculationStep:   IT Equipment Energy: 0.01 kWh (45832 Joules)
EnergyCalculationStep:   Total Facility Energy (PUE=1.50): 0.02 kWh
EnergyCalculationStep:   Average Power Draw: 322.76 W
EnergyCalculationStep:   Peak Power Draw: 909.48 W
EnergyCalculationStep:   Carbon Footprint (US_AVERAGE): 0.0080 kg CO2
EnergyCalculationStep:   Estimated Cost ($0.1000/kWh): $0.0019
EnergyCalculationStep:   Energy per Task: 171.87 Joules
EnergyCalculationStep:   Energy Efficiency: 4.95e+07 IPS/Watt
EnergyCalculationStep:   Per-Datacenter Energy:
EnergyCalculationStep:     London: 0.0127 kWh
MetricsCollectionStep: Collecting and aggregating simulation metrics...
MetricsCollectionStep: Metrics Collection Complete
MetricsCollectionStep:   Simulation ID: 5f99f27b
MetricsCollectionStep:   Duration: 142 seconds
MetricsCollectionStep:   Infrastructure: 1 DCs, 12 hosts (8 active), 15 VMs
MetricsCollectionStep:   Tasks: 400/400 completed (100.0%)
MetricsCollectionStep:   Performance: makespan=46s, p90=38.0s, p99=45.0s
MetricsCollectionStep:   SLA Compliance (3600s): 100.0%
MetricsCollectionStep:   Load Balance Index: 0.5132
ReportingStep: Generating CSV reports...
ReportingStep: Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_923625
ReportingStep:   Generating summary report...
ReportingStep:     -> 5f99f27b_summary.csv (1 rows, 926 B)
ReportingStep:   Generating datacenters report...
ReportingStep:     -> 5f99f27b_datacenters.csv (1 rows, 413 B)
ReportingStep:   Generating hosts report...
ReportingStep:     -> 5f99f27b_hosts.csv (12 rows, 2.23 KB)
ReportingStep:   Generating vms report...
ReportingStep:     -> 5f99f27b_vms.csv (15 rows, 1.73 KB)
ReportingStep:   Generating tasks report...
ReportingStep:     -> 5f99f27b_tasks.csv (400 rows, 44.48 KB)
ReportingStep:   Generating users report...
ReportingStep:     -> 5f99f27b_users.csv (1 rows, 408 B)
ReportingStep: Report Generation Complete
ReportingStep:   Duration: 20 ms
ReportingStep:   Files generated: 6
ReportingStep:   Total size: 50.15 KB
ReportingStep:   Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_923625
ReportingStep:   Generated files:
ReportingStep:     - 5f99f27b_summary.csv (1 rows)
ReportingStep:     - 5f99f27b_datacenters.csv (1 rows)
ReportingStep:     - 5f99f27b_hosts.csv (12 rows)
ReportingStep:     - 5f99f27b_vms.csv (15 rows)
ReportingStep:     - 5f99f27b_tasks.csv (400 rows)
ReportingStep:     - 5f99f27b_users.csv (1 rows)
[2025-12-15 13:10:41] [INFO]   Solution 3: simulated makespan=46s, energy=0.0127 kWh
[2025-12-15 13:10:41] [INFO]   Simulating solution 4/7 (objectives: [48.00, 0.01])
VMExecutionStep: Starting VM Execution Loop
VMExecutionStep:   VMs assigned to hosts: 15
VMExecutionStep:   Tasks assigned to VMs: 400 / 400
VMExecutionStep: VM Execution Loop Completed
VMExecutionStep:   Total simulation time: 190 seconds
VMExecutionStep:   Tasks completed: 400 / 400
VMExecutionStep:   Peak concurrent tasks: 400
TaskExecutionStep: Analyzing task execution results...
TaskExecutionStep: Task Execution Analysis Complete
TaskExecutionStep:   Total tasks: 400
TaskExecutionStep:   Completed: 400
TaskExecutionStep:   Failed: 0
TaskExecutionStep:   Unassigned: 0
TaskExecutionStep:   Makespan: 48 seconds
TaskExecutionStep:   Avg waiting time: 19.56 seconds
TaskExecutionStep:   Avg turnaround time: 19.88 seconds
TaskExecutionStep:   Avg execution time: 0.32 seconds
TaskExecutionStep:   Throughput: 8.3333 tasks/second
TaskExecutionStep:   Users completed: 1 / 1
EnergyCalculationStep: Calculating energy consumption metrics...
EnergyCalculationStep: Energy Calculation Complete
EnergyCalculationStep:   Simulation duration: 190 seconds
EnergyCalculationStep:   IT Equipment Energy: 0.01 kWh (47238 Joules)
EnergyCalculationStep:   Total Facility Energy (PUE=1.50): 0.02 kWh
EnergyCalculationStep:   Average Power Draw: 248.62 W
EnergyCalculationStep:   Peak Power Draw: 909.48 W
EnergyCalculationStep:   Carbon Footprint (US_AVERAGE): 0.0083 kg CO2
EnergyCalculationStep:   Estimated Cost ($0.1000/kWh): $0.0020
EnergyCalculationStep:   Energy per Task: 177.14 Joules
EnergyCalculationStep:   Energy Efficiency: 4.80e+07 IPS/Watt
EnergyCalculationStep:   Per-Datacenter Energy:
EnergyCalculationStep:     London: 0.0131 kWh
MetricsCollectionStep: Collecting and aggregating simulation metrics...
MetricsCollectionStep: Metrics Collection Complete
MetricsCollectionStep:   Simulation ID: 8a911978
MetricsCollectionStep:   Duration: 190 seconds
MetricsCollectionStep:   Infrastructure: 1 DCs, 12 hosts (8 active), 15 VMs
MetricsCollectionStep:   Tasks: 400/400 completed (100.0%)
MetricsCollectionStep:   Performance: makespan=48s, p90=39.0s, p99=46.0s
MetricsCollectionStep:   SLA Compliance (3600s): 100.0%
MetricsCollectionStep:   Load Balance Index: 0.5678
ReportingStep: Generating CSV reports...
ReportingStep: Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_a55ede
ReportingStep:   Generating summary report...
ReportingStep:     -> 8a911978_summary.csv (1 rows, 926 B)
ReportingStep:   Generating datacenters report...
ReportingStep:     -> 8a911978_datacenters.csv (1 rows, 413 B)
ReportingStep:   Generating hosts report...
ReportingStep:     -> 8a911978_hosts.csv (12 rows, 2.23 KB)
ReportingStep:   Generating vms report...
ReportingStep:     -> 8a911978_vms.csv (15 rows, 1.73 KB)
ReportingStep:   Generating tasks report...
ReportingStep:     -> 8a911978_tasks.csv (400 rows, 44.48 KB)
ReportingStep:   Generating users report...
ReportingStep:     -> 8a911978_users.csv (1 rows, 408 B)
ReportingStep: Report Generation Complete
ReportingStep:   Duration: 19 ms
ReportingStep:   Files generated: 6
ReportingStep:   Total size: 50.15 KB
ReportingStep:   Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_a55ede
ReportingStep:   Generated files:
ReportingStep:     - 8a911978_summary.csv (1 rows)
ReportingStep:     - 8a911978_datacenters.csv (1 rows)
ReportingStep:     - 8a911978_hosts.csv (12 rows)
ReportingStep:     - 8a911978_vms.csv (15 rows)
ReportingStep:     - 8a911978_tasks.csv (400 rows)
ReportingStep:     - 8a911978_users.csv (1 rows)
[2025-12-15 13:10:41] [INFO]   Solution 4: simulated makespan=48s, energy=0.0131 kWh
[2025-12-15 13:10:41] [INFO]   Simulating solution 5/7 (objectives: [49.00, 0.01])
VMExecutionStep: Starting VM Execution Loop
VMExecutionStep:   VMs assigned to hosts: 15
VMExecutionStep:   Tasks assigned to VMs: 400 / 400
VMExecutionStep: [Tick 200] Progress: 27.5% (110/400 tasks completed, 290 executing)
VMExecutionStep: VM Execution Loop Completed
VMExecutionStep:   Total simulation time: 239 seconds
VMExecutionStep:   Tasks completed: 400 / 400
VMExecutionStep:   Peak concurrent tasks: 400
TaskExecutionStep: Analyzing task execution results...
TaskExecutionStep: Task Execution Analysis Complete
TaskExecutionStep:   Total tasks: 400
TaskExecutionStep:   Completed: 400
TaskExecutionStep:   Failed: 0
TaskExecutionStep:   Unassigned: 0
TaskExecutionStep:   Makespan: 49 seconds
TaskExecutionStep:   Avg waiting time: 19.66 seconds
TaskExecutionStep:   Avg turnaround time: 19.95 seconds
TaskExecutionStep:   Avg execution time: 0.29 seconds
TaskExecutionStep:   Throughput: 8.1633 tasks/second
TaskExecutionStep:   Users completed: 1 / 1
EnergyCalculationStep: Calculating energy consumption metrics...
EnergyCalculationStep: Energy Calculation Complete
EnergyCalculationStep:   Simulation duration: 239 seconds
EnergyCalculationStep:   IT Equipment Energy: 0.01 kWh (47712 Joules)
EnergyCalculationStep:   Total Facility Energy (PUE=1.50): 0.02 kWh
EnergyCalculationStep:   Average Power Draw: 199.63 W
EnergyCalculationStep:   Peak Power Draw: 909.48 W
EnergyCalculationStep:   Carbon Footprint (US_AVERAGE): 0.0083 kg CO2
EnergyCalculationStep:   Estimated Cost ($0.1000/kWh): $0.0020
EnergyCalculationStep:   Energy per Task: 178.92 Joules
EnergyCalculationStep:   Energy Efficiency: 4.75e+07 IPS/Watt
EnergyCalculationStep:   Per-Datacenter Energy:
EnergyCalculationStep:     London: 0.0133 kWh
MetricsCollectionStep: Collecting and aggregating simulation metrics...
MetricsCollectionStep: Metrics Collection Complete
MetricsCollectionStep:   Simulation ID: a5bb5cec
MetricsCollectionStep:   Duration: 239 seconds
MetricsCollectionStep:   Infrastructure: 1 DCs, 12 hosts (8 active), 15 VMs
MetricsCollectionStep:   Tasks: 400/400 completed (100.0%)
MetricsCollectionStep:   Performance: makespan=49s, p90=40.0s, p99=47.0s
MetricsCollectionStep:   SLA Compliance (3600s): 100.0%
MetricsCollectionStep:   Load Balance Index: 0.5976
ReportingStep: Generating CSV reports...
ReportingStep: Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_bce168
ReportingStep:   Generating summary report...
ReportingStep:     -> a5bb5cec_summary.csv (1 rows, 926 B)
ReportingStep:   Generating datacenters report...
ReportingStep:     -> a5bb5cec_datacenters.csv (1 rows, 413 B)
ReportingStep:   Generating hosts report...
ReportingStep:     -> a5bb5cec_hosts.csv (12 rows, 2.23 KB)
ReportingStep:   Generating vms report...
ReportingStep:     -> a5bb5cec_vms.csv (15 rows, 1.73 KB)
ReportingStep:   Generating tasks report...
ReportingStep:     -> a5bb5cec_tasks.csv (400 rows, 44.46 KB)
ReportingStep:   Generating users report...
ReportingStep:     -> a5bb5cec_users.csv (1 rows, 408 B)
ReportingStep: Report Generation Complete
ReportingStep:   Duration: 18 ms
ReportingStep:   Files generated: 6
ReportingStep:   Total size: 50.12 KB
ReportingStep:   Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_bce168
ReportingStep:   Generated files:
ReportingStep:     - a5bb5cec_summary.csv (1 rows)
ReportingStep:     - a5bb5cec_datacenters.csv (1 rows)
ReportingStep:     - a5bb5cec_hosts.csv (12 rows)
ReportingStep:     - a5bb5cec_vms.csv (15 rows)
ReportingStep:     - a5bb5cec_tasks.csv (400 rows)
ReportingStep:     - a5bb5cec_users.csv (1 rows)
[2025-12-15 13:10:41] [INFO]   Solution 5: simulated makespan=49s, energy=0.0133 kWh
[2025-12-15 13:10:41] [INFO]   Simulating solution 6/7 (objectives: [47.00, 0.01])
VMExecutionStep: Starting VM Execution Loop
VMExecutionStep:   VMs assigned to hosts: 15
VMExecutionStep:   Tasks assigned to VMs: 400 / 400
VMExecutionStep: VM Execution Loop Completed
VMExecutionStep:   Total simulation time: 286 seconds
VMExecutionStep:   Tasks completed: 400 / 400
VMExecutionStep:   Peak concurrent tasks: 400
TaskExecutionStep: Analyzing task execution results...
TaskExecutionStep: Task Execution Analysis Complete
TaskExecutionStep:   Total tasks: 400
TaskExecutionStep:   Completed: 400
TaskExecutionStep:   Failed: 0
TaskExecutionStep:   Unassigned: 0
TaskExecutionStep:   Makespan: 47 seconds
TaskExecutionStep:   Avg waiting time: 19.53 seconds
TaskExecutionStep:   Avg turnaround time: 19.87 seconds
TaskExecutionStep:   Avg execution time: 0.34 seconds
TaskExecutionStep:   Throughput: 8.5106 tasks/second
TaskExecutionStep:   Users completed: 1 / 1
EnergyCalculationStep: Calculating energy consumption metrics...
EnergyCalculationStep: Energy Calculation Complete
EnergyCalculationStep:   Simulation duration: 286 seconds
EnergyCalculationStep:   IT Equipment Energy: 0.01 kWh (46503 Joules)
EnergyCalculationStep:   Total Facility Energy (PUE=1.50): 0.02 kWh
EnergyCalculationStep:   Average Power Draw: 162.60 W
EnergyCalculationStep:   Peak Power Draw: 909.48 W
EnergyCalculationStep:   Carbon Footprint (US_AVERAGE): 0.0081 kg CO2
EnergyCalculationStep:   Estimated Cost ($0.1000/kWh): $0.0019
EnergyCalculationStep:   Energy per Task: 174.38 Joules
EnergyCalculationStep:   Energy Efficiency: 4.88e+07 IPS/Watt
EnergyCalculationStep:   Per-Datacenter Energy:
EnergyCalculationStep:     London: 0.0129 kWh
MetricsCollectionStep: Collecting and aggregating simulation metrics...
MetricsCollectionStep: Metrics Collection Complete
MetricsCollectionStep:   Simulation ID: 0bdd9732
MetricsCollectionStep:   Duration: 286 seconds
MetricsCollectionStep:   Infrastructure: 1 DCs, 12 hosts (8 active), 15 VMs
MetricsCollectionStep:   Tasks: 400/400 completed (100.0%)
MetricsCollectionStep:   Performance: makespan=47s, p90=39.0s, p99=46.0s
MetricsCollectionStep:   SLA Compliance (3600s): 100.0%
MetricsCollectionStep:   Load Balance Index: 0.5511
ReportingStep: Generating CSV reports...
ReportingStep: Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_fa7d04
ReportingStep:   Generating summary report...
ReportingStep:     -> 0bdd9732_summary.csv (1 rows, 926 B)
ReportingStep:   Generating datacenters report...
ReportingStep:     -> 0bdd9732_datacenters.csv (1 rows, 413 B)
ReportingStep:   Generating hosts report...
ReportingStep:     -> 0bdd9732_hosts.csv (12 rows, 2.23 KB)
ReportingStep:   Generating vms report...
ReportingStep:     -> 0bdd9732_vms.csv (15 rows, 1.73 KB)
ReportingStep:   Generating tasks report...
ReportingStep:     -> 0bdd9732_tasks.csv (400 rows, 44.47 KB)
ReportingStep:   Generating users report...
ReportingStep:     -> 0bdd9732_users.csv (1 rows, 408 B)
ReportingStep: Report Generation Complete
ReportingStep:   Duration: 18 ms
ReportingStep:   Files generated: 6
ReportingStep:   Total size: 50.13 KB
ReportingStep:   Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_fa7d04
ReportingStep:   Generated files:
ReportingStep:     - 0bdd9732_summary.csv (1 rows)
ReportingStep:     - 0bdd9732_datacenters.csv (1 rows)
ReportingStep:     - 0bdd9732_hosts.csv (12 rows)
ReportingStep:     - 0bdd9732_vms.csv (15 rows)
ReportingStep:     - 0bdd9732_tasks.csv (400 rows)
ReportingStep:     - 0bdd9732_users.csv (1 rows)
[2025-12-15 13:10:41] [INFO]   Solution 6: simulated makespan=47s, energy=0.0129 kWh
[2025-12-15 13:10:41] [INFO]   Simulating solution 7/7 (objectives: [50.00, 0.01])
VMExecutionStep: Starting VM Execution Loop
VMExecutionStep:   VMs assigned to hosts: 15
VMExecutionStep:   Tasks assigned to VMs: 400 / 400
VMExecutionStep: [Tick 300] Progress: 38.5% (154/400 tasks completed, 246 executing)
VMExecutionStep: VM Execution Loop Completed
VMExecutionStep:   Total simulation time: 336 seconds
VMExecutionStep:   Tasks completed: 400 / 400
VMExecutionStep:   Peak concurrent tasks: 400
TaskExecutionStep: Analyzing task execution results...
TaskExecutionStep: Task Execution Analysis Complete
TaskExecutionStep:   Total tasks: 400
TaskExecutionStep:   Completed: 400
TaskExecutionStep:   Failed: 0
TaskExecutionStep:   Unassigned: 0
TaskExecutionStep:   Makespan: 50 seconds
TaskExecutionStep:   Avg waiting time: 19.72 seconds
TaskExecutionStep:   Avg turnaround time: 20.00 seconds
TaskExecutionStep:   Avg execution time: 0.29 seconds
TaskExecutionStep:   Throughput: 8.0000 tasks/second
TaskExecutionStep:   Users completed: 1 / 1
EnergyCalculationStep: Calculating energy consumption metrics...
EnergyCalculationStep: Energy Calculation Complete
EnergyCalculationStep:   Simulation duration: 336 seconds
EnergyCalculationStep:   IT Equipment Energy: 0.01 kWh (48523 Joules)
EnergyCalculationStep:   Total Facility Energy (PUE=1.50): 0.02 kWh
EnergyCalculationStep:   Average Power Draw: 144.41 W
EnergyCalculationStep:   Peak Power Draw: 909.48 W
EnergyCalculationStep:   Carbon Footprint (US_AVERAGE): 0.0085 kg CO2
EnergyCalculationStep:   Estimated Cost ($0.1000/kWh): $0.0020
EnergyCalculationStep:   Energy per Task: 181.96 Joules
EnergyCalculationStep:   Energy Efficiency: 4.68e+07 IPS/Watt
EnergyCalculationStep:   Per-Datacenter Energy:
EnergyCalculationStep:     London: 0.0135 kWh
MetricsCollectionStep: Collecting and aggregating simulation metrics...
MetricsCollectionStep: Metrics Collection Complete
MetricsCollectionStep:   Simulation ID: 4a004475
MetricsCollectionStep:   Duration: 336 seconds
MetricsCollectionStep:   Infrastructure: 1 DCs, 12 hosts (8 active), 15 VMs
MetricsCollectionStep:   Tasks: 400/400 completed (100.0%)
MetricsCollectionStep:   Performance: makespan=50s, p90=41.0s, p99=48.0s
MetricsCollectionStep:   SLA Compliance (3600s): 100.0%
MetricsCollectionStep:   Load Balance Index: 0.6108
ReportingStep: Generating CSV reports...
ReportingStep: Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_c6b1ce
ReportingStep:   Generating summary report...
ReportingStep:     -> 4a004475_summary.csv (1 rows, 926 B)
ReportingStep:   Generating datacenters report...
ReportingStep:     -> 4a004475_datacenters.csv (1 rows, 413 B)
ReportingStep:   Generating hosts report...
ReportingStep:     -> 4a004475_hosts.csv (12 rows, 2.23 KB)
ReportingStep:   Generating vms report...
ReportingStep:     -> 4a004475_vms.csv (15 rows, 1.73 KB)
ReportingStep:   Generating tasks report...
ReportingStep:     -> 4a004475_tasks.csv (400 rows, 44.45 KB)
ReportingStep:   Generating users report...
ReportingStep:     -> 4a004475_users.csv (1 rows, 408 B)
ReportingStep: Report Generation Complete
ReportingStep:   Duration: 19 ms
ReportingStep:   Files generated: 6
ReportingStep:   Total size: 50.11 KB
ReportingStep:   Output directory: C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\experiment_20251215_131041_c6b1ce
ReportingStep:   Generated files:
ReportingStep:     - 4a004475_summary.csv (1 rows)
ReportingStep:     - 4a004475_datacenters.csv (1 rows)
ReportingStep:     - 4a004475_hosts.csv (12 rows)
ReportingStep:     - 4a004475_vms.csv (15 rows)
ReportingStep:     - 4a004475_tasks.csv (400 rows)
ReportingStep:     - 4a004475_users.csv (1 rows)
[2025-12-15 13:10:41] [INFO]   Solution 7: simulated makespan=50s, energy=0.0135 kWh
[2025-12-15 13:10:41] [INFO] ========================================
[2025-12-15 13:10:41] [INFO] Multi-Objective Simulation Complete
[2025-12-15 13:10:41] [INFO] Solutions simulated: 7
[2025-12-15 13:10:41] [INFO] Total time: 1450 ms
[2025-12-15 13:10:41] [INFO] ========================================
[ParetoFrontReporter] Generated 2 files (1.22 KB) to C:\Users\Alper\Documents\NetBeansProjects\JavaCloudSimulatorCosmos\.\reports\pareto_10

MULTI-OBJECTIVE SIMULATION RESULTS
----------------------------------------
Pareto front size: 7 solutions
Total simulation time: 0 ms

Solution Results:
Index    Pred.Makespan   Sim.Makespan    Pred.Energy     Sim.Energy     
--------------------------------------------------------------------------------
0        51.00           51              0.0126          0.013671       
1        45.00           45              0.0129          0.012588       
2        46.00           46              0.0128          0.012731       
3        48.00           48              0.0127          0.013122       
4        49.00           49              0.0126          0.013253       
5        47.00           47              0.0127          0.012917       
6        50.00           50              0.0126          0.013479       

Best makespan: Solution 1 (45 seconds)
Best energy: Solution 1 (0.012588 kWh)

========================================
ALL EXPERIMENTS COMPLETED
========================================
------------------------------------------------------------------------
BUILD SUCCESS
------------------------------------------------------------------------
Total time:  7.509 s
Finished at: 2025-12-15T13:10:42+03:00
------------------------------------------------------------------------
