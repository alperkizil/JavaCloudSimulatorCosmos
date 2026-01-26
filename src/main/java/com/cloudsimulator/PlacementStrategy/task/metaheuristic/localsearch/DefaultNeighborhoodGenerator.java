package com.cloudsimulator.PlacementStrategy.task.metaheuristic.localsearch;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;
import com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators.RepairOperator;
import com.cloudsimulator.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Default implementation of NeighborhoodGenerator using standard move operators.
 *
 * Generates neighbors based on the neighborhood type:
 * - REASSIGN: Each neighbor differs from current by one task's VM assignment
 * - SWAP_ORDER: Each neighbor differs by swapping two tasks' order on the same VM
 * - COMBINED: Union of REASSIGN and SWAP_ORDER neighborhoods
 *
 * All generated neighbors are guaranteed to be valid (respecting user/VM constraints)
 * through the use of RepairOperator's valid VM mappings.
 */
public class DefaultNeighborhoodGenerator implements NeighborhoodGenerator {

    private final RepairOperator repairOperator;
    private final NeighborhoodType type;
    private final RandomGenerator random;
    private final int numVMs;

    /**
     * Creates a neighborhood generator.
     *
     * @param repairOperator Repair operator with valid VM mappings
     * @param type           Type of neighborhood to generate
     * @param numVMs         Number of VMs in the problem
     */
    public DefaultNeighborhoodGenerator(RepairOperator repairOperator, NeighborhoodType type, int numVMs) {
        this.repairOperator = repairOperator;
        this.type = type;
        this.numVMs = numVMs;
        this.random = RandomGenerator.getInstance();
    }

    /**
     * Creates a neighborhood generator with a specific random source.
     *
     * @param repairOperator Repair operator with valid VM mappings
     * @param type           Type of neighborhood to generate
     * @param numVMs         Number of VMs in the problem
     * @param random         Random number generator
     */
    public DefaultNeighborhoodGenerator(RepairOperator repairOperator, NeighborhoodType type,
                                        int numVMs, RandomGenerator random) {
        this.repairOperator = repairOperator;
        this.type = type;
        this.numVMs = numVMs;
        this.random = random;
    }

    @Override
    public List<SchedulingSolution> generateAll(SchedulingSolution solution) {
        List<SchedulingSolution> neighbors = new ArrayList<>();

        if (type == NeighborhoodType.REASSIGN || type == NeighborhoodType.COMBINED) {
            neighbors.addAll(generateReassignNeighbors(solution));
        }

        if (type == NeighborhoodType.SWAP_ORDER || type == NeighborhoodType.COMBINED) {
            neighbors.addAll(generateSwapOrderNeighbors(solution));
        }

        return neighbors;
    }

    @Override
    public Iterator<SchedulingSolution> generateIterator(SchedulingSolution solution) {
        return new NeighborhoodIterator(solution);
    }

    @Override
    public SchedulingSolution generateRandom(SchedulingSolution solution) {
        int neighborhoodSize = getNeighborhoodSize(solution);
        if (neighborhoodSize == 0) {
            return solution.copy(); // No neighbors possible
        }

        // Decide which type of neighbor to generate
        boolean useReassign;
        if (type == NeighborhoodType.COMBINED) {
            int reassignSize = countReassignNeighbors(solution);
            int swapSize = countSwapOrderNeighbors(solution);
            int total = reassignSize + swapSize;
            useReassign = random.nextInt(total) < reassignSize;
        } else {
            useReassign = (type == NeighborhoodType.REASSIGN);
        }

        if (useReassign) {
            return generateRandomReassignNeighbor(solution);
        } else {
            return generateRandomSwapOrderNeighbor(solution);
        }
    }

    @Override
    public int getNeighborhoodSize(SchedulingSolution solution) {
        int size = 0;

        if (type == NeighborhoodType.REASSIGN || type == NeighborhoodType.COMBINED) {
            size += countReassignNeighbors(solution);
        }

        if (type == NeighborhoodType.SWAP_ORDER || type == NeighborhoodType.COMBINED) {
            size += countSwapOrderNeighbors(solution);
        }

        return size;
    }

    @Override
    public String getName() {
        return "Default (" + type.name() + ")";
    }

    /**
     * Generates all REASSIGN neighbors.
     * Each neighbor has one task reassigned to a different valid VM.
     */
    private List<SchedulingSolution> generateReassignNeighbors(SchedulingSolution solution) {
        List<SchedulingSolution> neighbors = new ArrayList<>();
        int numTasks = solution.getNumTasks();

        for (int taskIdx = 0; taskIdx < numTasks; taskIdx++) {
            int currentVm = solution.getAssignedVM(taskIdx);
            List<Integer> validVms = repairOperator.getValidVmsForTask(taskIdx);

            for (int newVm : validVms) {
                if (newVm != currentVm) {
                    SchedulingSolution neighbor = solution.copy();
                    neighbor.setAssignedVM(taskIdx, newVm);
                    neighbor.rebuildTaskOrdering();
                    neighbor.invalidate();
                    neighbors.add(neighbor);
                }
            }
        }

        return neighbors;
    }

    /**
     * Generates all SWAP_ORDER neighbors.
     * Each neighbor has two tasks on the same VM with swapped execution order.
     */
    private List<SchedulingSolution> generateSwapOrderNeighbors(SchedulingSolution solution) {
        List<SchedulingSolution> neighbors = new ArrayList<>();

        for (int vmIdx = 0; vmIdx < numVMs; vmIdx++) {
            List<Integer> vmTasks = solution.getTaskOrderForVM(vmIdx);
            int numTasksOnVm = vmTasks.size();

            // Generate all pairs for swapping
            for (int i = 0; i < numTasksOnVm - 1; i++) {
                for (int j = i + 1; j < numTasksOnVm; j++) {
                    SchedulingSolution neighbor = solution.copy();
                    List<Integer> neighborVmTasks = neighbor.getTaskOrderForVM(vmIdx);

                    // Swap positions i and j
                    Integer temp = neighborVmTasks.get(i);
                    neighborVmTasks.set(i, neighborVmTasks.get(j));
                    neighborVmTasks.set(j, temp);

                    neighbor.invalidate();
                    neighbors.add(neighbor);
                }
            }
        }

        return neighbors;
    }

    /**
     * Generates a single random REASSIGN neighbor.
     */
    private SchedulingSolution generateRandomReassignNeighbor(SchedulingSolution solution) {
        int numTasks = solution.getNumTasks();

        // Find a task that can be reassigned (has more than one valid VM)
        List<Integer> reassignableTasks = new ArrayList<>();
        for (int taskIdx = 0; taskIdx < numTasks; taskIdx++) {
            List<Integer> validVms = repairOperator.getValidVmsForTask(taskIdx);
            if (validVms.size() > 1) {
                reassignableTasks.add(taskIdx);
            }
        }

        if (reassignableTasks.isEmpty()) {
            return solution.copy();
        }

        int taskIdx = reassignableTasks.get(random.nextInt(reassignableTasks.size()));
        int currentVm = solution.getAssignedVM(taskIdx);
        List<Integer> validVms = repairOperator.getValidVmsForTask(taskIdx);

        // Select a different VM
        int newVm;
        do {
            newVm = validVms.get(random.nextInt(validVms.size()));
        } while (newVm == currentVm && validVms.size() > 1);

        SchedulingSolution neighbor = solution.copy();
        neighbor.setAssignedVM(taskIdx, newVm);
        neighbor.rebuildTaskOrdering();
        neighbor.invalidate();

        return neighbor;
    }

    /**
     * Generates a single random SWAP_ORDER neighbor.
     */
    private SchedulingSolution generateRandomSwapOrderNeighbor(SchedulingSolution solution) {
        // Find VMs with at least 2 tasks
        List<Integer> swappableVMs = new ArrayList<>();
        for (int vmIdx = 0; vmIdx < numVMs; vmIdx++) {
            if (solution.getTaskOrderForVM(vmIdx).size() >= 2) {
                swappableVMs.add(vmIdx);
            }
        }

        if (swappableVMs.isEmpty()) {
            return solution.copy();
        }

        int vmIdx = swappableVMs.get(random.nextInt(swappableVMs.size()));
        List<Integer> vmTasks = solution.getTaskOrderForVM(vmIdx);

        // Select two different positions to swap
        int pos1 = random.nextInt(vmTasks.size());
        int pos2;
        do {
            pos2 = random.nextInt(vmTasks.size());
        } while (pos2 == pos1);

        SchedulingSolution neighbor = solution.copy();
        List<Integer> neighborVmTasks = neighbor.getTaskOrderForVM(vmIdx);

        Integer temp = neighborVmTasks.get(pos1);
        neighborVmTasks.set(pos1, neighborVmTasks.get(pos2));
        neighborVmTasks.set(pos2, temp);

        neighbor.invalidate();

        return neighbor;
    }

    /**
     * Counts the number of possible REASSIGN neighbors.
     */
    private int countReassignNeighbors(SchedulingSolution solution) {
        int count = 0;
        int numTasks = solution.getNumTasks();

        for (int taskIdx = 0; taskIdx < numTasks; taskIdx++) {
            List<Integer> validVms = repairOperator.getValidVmsForTask(taskIdx);
            // Can reassign to any valid VM except current one
            count += Math.max(0, validVms.size() - 1);
        }

        return count;
    }

    /**
     * Counts the number of possible SWAP_ORDER neighbors.
     */
    private int countSwapOrderNeighbors(SchedulingSolution solution) {
        int count = 0;

        for (int vmIdx = 0; vmIdx < numVMs; vmIdx++) {
            int n = solution.getTaskOrderForVM(vmIdx).size();
            // Number of unique pairs = n choose 2 = n*(n-1)/2
            count += n * (n - 1) / 2;
        }

        return count;
    }

    /**
     * Iterator for lazy neighborhood generation.
     * Generates neighbors one at a time to support First Improvement strategy.
     */
    private class NeighborhoodIterator implements Iterator<SchedulingSolution> {
        private final SchedulingSolution solution;
        private int currentTaskIdx = 0;
        private int currentVmOptionIdx = 0;
        private int currentSwapVmIdx = 0;
        private int currentSwapI = 0;
        private int currentSwapJ = 1;
        private boolean inReassignPhase;
        private boolean inSwapPhase;
        private List<Integer> currentValidVms;

        NeighborhoodIterator(SchedulingSolution solution) {
            this.solution = solution;
            this.inReassignPhase = (type == NeighborhoodType.REASSIGN || type == NeighborhoodType.COMBINED);
            this.inSwapPhase = (type == NeighborhoodType.SWAP_ORDER || type == NeighborhoodType.COMBINED);

            if (inReassignPhase) {
                advanceToNextValidReassign();
            } else if (inSwapPhase) {
                advanceToNextValidSwap();
            }
        }

        @Override
        public boolean hasNext() {
            return inReassignPhase || inSwapPhase;
        }

        @Override
        public SchedulingSolution next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            SchedulingSolution neighbor;

            if (inReassignPhase) {
                neighbor = generateCurrentReassignNeighbor();
                advanceReassign();
            } else {
                neighbor = generateCurrentSwapNeighbor();
                advanceSwap();
            }

            return neighbor;
        }

        private SchedulingSolution generateCurrentReassignNeighbor() {
            int newVm = currentValidVms.get(currentVmOptionIdx);
            SchedulingSolution neighbor = solution.copy();
            neighbor.setAssignedVM(currentTaskIdx, newVm);
            neighbor.rebuildTaskOrdering();
            neighbor.invalidate();
            return neighbor;
        }

        private SchedulingSolution generateCurrentSwapNeighbor() {
            SchedulingSolution neighbor = solution.copy();
            List<Integer> vmTasks = neighbor.getTaskOrderForVM(currentSwapVmIdx);

            Integer temp = vmTasks.get(currentSwapI);
            vmTasks.set(currentSwapI, vmTasks.get(currentSwapJ));
            vmTasks.set(currentSwapJ, temp);

            neighbor.invalidate();
            return neighbor;
        }

        private void advanceReassign() {
            currentVmOptionIdx++;
            advanceToNextValidReassign();
        }

        private void advanceToNextValidReassign() {
            while (currentTaskIdx < solution.getNumTasks()) {
                if (currentValidVms == null) {
                    currentValidVms = new ArrayList<>(repairOperator.getValidVmsForTask(currentTaskIdx));
                    int currentVm = solution.getAssignedVM(currentTaskIdx);
                    currentValidVms.remove(Integer.valueOf(currentVm));
                    currentVmOptionIdx = 0;
                }

                if (currentVmOptionIdx < currentValidVms.size()) {
                    return; // Found a valid reassign option
                }

                // Move to next task
                currentTaskIdx++;
                currentValidVms = null;
            }

            // Done with reassign phase
            inReassignPhase = false;
            if (type == NeighborhoodType.COMBINED) {
                inSwapPhase = true;
                advanceToNextValidSwap();
            }
        }

        private void advanceSwap() {
            currentSwapJ++;
            advanceToNextValidSwap();
        }

        private void advanceToNextValidSwap() {
            while (currentSwapVmIdx < numVMs) {
                List<Integer> vmTasks = solution.getTaskOrderForVM(currentSwapVmIdx);
                int n = vmTasks.size();

                while (currentSwapI < n - 1) {
                    if (currentSwapJ < n) {
                        return; // Found a valid swap
                    }
                    currentSwapI++;
                    currentSwapJ = currentSwapI + 1;
                }

                // Move to next VM
                currentSwapVmIdx++;
                currentSwapI = 0;
                currentSwapJ = 1;
            }

            // Done with swap phase
            inSwapPhase = false;
        }
    }
}
