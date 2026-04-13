package com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.List;
import java.util.Random;

/**
 * Mutation operator for modifying scheduling solutions.
 *
 * Implements multiple mutation strategies:
 * 1. Reassignment Mutation: Change which VM a task is assigned to
 * 2. Swap Mutation: Swap execution order of two tasks on the same VM
 * 3. Move Mutation: Move a task from one VM to another
 *
 * Mutations may create invalid solutions that require repair.
 */
public class MutationOperator {

    /**
     * Type of mutation to perform.
     */
    public enum MutationType {
        REASSIGN,     // Reassign task to different VM
        SWAP_ORDER,   // Swap order of two tasks on same VM
        MOVE,         // Move task to different position on same/different VM
        COMBINED      // Randomly choose between mutation types
    }

    private final Random random;
    private final MutationType type;
    private final int numVMs;
    private final RepairOperator repairOperator;

    /**
     * Creates a mutation operator.
     *
     * @param type           Type of mutation
     * @param numVMs         Number of VMs in the problem
     * @param repairOperator Repair operator for getting valid VM options
     * @param random         Random number generator
     */
    public MutationOperator(MutationType type, int numVMs, RepairOperator repairOperator, Random random) {
        this.type = type;
        this.numVMs = numVMs;
        this.repairOperator = repairOperator;
        this.random = random;
    }

    /**
     * Creates a mutation operator with combined mutation type.
     *
     * @param numVMs         Number of VMs in the problem
     * @param repairOperator Repair operator for getting valid VM options
     * @param random         Random number generator
     */
    public MutationOperator(int numVMs, RepairOperator repairOperator, Random random) {
        this(MutationType.COMBINED, numVMs, repairOperator, random);
    }

    /**
     * Mutates a solution with the specified mutation rate.
     * Each task has a probability equal to mutationRate of being mutated.
     *
     * @param solution     The solution to mutate (modified in place)
     * @param mutationRate Probability of mutating each task (0.0 to 1.0)
     * @return true if any mutations were applied
     */
    public boolean mutate(SchedulingSolution solution, double mutationRate) {
        boolean mutated = false;
        int numTasks = solution.getNumTasks();

        for (int taskIdx = 0; taskIdx < numTasks; taskIdx++) {
            if (random.nextDouble() < mutationRate) {
                MutationType actualType = type;
                if (type == MutationType.COMBINED) {
                    // Randomly choose mutation type
                    actualType = random.nextBoolean() ? MutationType.REASSIGN : MutationType.SWAP_ORDER;
                }

                switch (actualType) {
                    case REASSIGN:
                        mutated |= reassignMutation(solution, taskIdx);
                        break;
                    case SWAP_ORDER:
                        mutated |= swapOrderMutation(solution, taskIdx);
                        break;
                    case MOVE:
                        mutated |= moveMutation(solution, taskIdx);
                        break;
                    default:
                        mutated |= reassignMutation(solution, taskIdx);
                }
            }
        }

        if (mutated) {
            solution.invalidate();
        }

        return mutated;
    }

    /**
     * Reassignment mutation: Assign a task to a different (valid) VM.
     */
    private boolean reassignMutation(SchedulingSolution solution, int taskIdx) {
        List<Integer> validVms = repairOperator.getValidVmsForTask(taskIdx);

        if (validVms.size() <= 1) {
            return false; // Only one valid VM, can't reassign
        }

        int currentVm = solution.getAssignedVM(taskIdx);
        int newVm;

        // Select a different valid VM
        do {
            newVm = validVms.get(random.nextInt(validVms.size()));
        } while (newVm == currentVm && validVms.size() > 1);

        if (newVm != currentVm) {
            solution.setAssignedVM(taskIdx, newVm);
            solution.rebuildTaskOrdering();
            return true;
        }

        return false;
    }

    /**
     * Swap order mutation: Swap the execution order of two tasks on the same VM.
     */
    private boolean swapOrderMutation(SchedulingSolution solution, int taskIdx) {
        int vmIdx = solution.getAssignedVM(taskIdx);
        List<Integer> vmTasks = solution.getTaskOrderForVM(vmIdx);

        if (vmTasks.size() <= 1) {
            return false; // Only one task on this VM, can't swap
        }

        // Find position of this task in the VM's order
        int pos1 = vmTasks.indexOf(taskIdx);
        if (pos1 == -1) {
            return false;
        }

        // Select a different position to swap with
        int pos2;
        do {
            pos2 = random.nextInt(vmTasks.size());
        } while (pos2 == pos1);

        // Swap positions
        Integer temp = vmTasks.get(pos1);
        vmTasks.set(pos1, vmTasks.get(pos2));
        vmTasks.set(pos2, temp);

        return true;
    }

    /**
     * Move mutation: Move a task to a different position (possibly different VM).
     */
    private boolean moveMutation(SchedulingSolution solution, int taskIdx) {
        // Try reassignment first
        if (random.nextBoolean()) {
            return reassignMutation(solution, taskIdx);
        } else {
            return swapOrderMutation(solution, taskIdx);
        }
    }

    /**
     * Applies a single random mutation to the solution.
     * Useful for small perturbations.
     *
     * @param solution The solution to mutate
     * @return true if mutation was applied
     */
    public boolean mutateSingle(SchedulingSolution solution) {
        int taskIdx = random.nextInt(solution.getNumTasks());

        MutationType actualType = type;
        if (type == MutationType.COMBINED) {
            actualType = random.nextBoolean() ? MutationType.REASSIGN : MutationType.SWAP_ORDER;
        }

        boolean mutated;
        switch (actualType) {
            case REASSIGN:
                mutated = reassignMutation(solution, taskIdx);
                break;
            case SWAP_ORDER:
                mutated = swapOrderMutation(solution, taskIdx);
                break;
            case MOVE:
                mutated = moveMutation(solution, taskIdx);
                break;
            default:
                mutated = reassignMutation(solution, taskIdx);
        }

        if (mutated) {
            solution.invalidate();
        }
        return mutated;
    }

    /**
     * Applies multiple sequential single-mutations to the solution.
     * Used for temperature-scaled perturbation in SA: at high temperatures,
     * larger perturbations (more mutations) allow bigger jumps in the search space.
     *
     * @param solution The solution to mutate
     * @param count    Number of mutations to apply (each on a random task)
     * @return true if any mutation was applied
     */
    public boolean mutateMultiple(SchedulingSolution solution, int count) {
        boolean anyMutated = false;
        for (int i = 0; i < count; i++) {
            int taskIdx = random.nextInt(solution.getNumTasks());

            MutationType actualType = type;
            if (type == MutationType.COMBINED) {
                actualType = random.nextBoolean() ? MutationType.REASSIGN : MutationType.SWAP_ORDER;
            }

            boolean mutated;
            switch (actualType) {
                case REASSIGN:
                    mutated = reassignMutation(solution, taskIdx);
                    break;
                case SWAP_ORDER:
                    mutated = swapOrderMutation(solution, taskIdx);
                    break;
                case MOVE:
                    mutated = moveMutation(solution, taskIdx);
                    break;
                default:
                    mutated = reassignMutation(solution, taskIdx);
            }
            anyMutated |= mutated;
        }

        if (anyMutated) {
            solution.invalidate();
        }
        return anyMutated;
    }

    public MutationType getType() {
        return type;
    }
}
