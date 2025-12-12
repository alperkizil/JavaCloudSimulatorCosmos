package com.cloudsimulator.PlacementStrategy.task.metaheuristic.operators;

import com.cloudsimulator.PlacementStrategy.task.metaheuristic.SchedulingSolution;

import java.util.Random;

/**
 * Crossover operator for combining two parent solutions into offspring.
 *
 * Implements multiple crossover strategies for the task scheduling problem:
 * 1. Uniform Crossover: Each gene (task assignment) is inherited from random parent
 * 2. Two-Point Crossover: Segments from parents are combined
 * 3. Order Crossover (OX): Preserves relative ordering from parents
 *
 * The offspring may be invalid after crossover and should be repaired.
 */
public class CrossoverOperator {

    /**
     * Type of crossover to perform.
     */
    public enum CrossoverType {
        UNIFORM,      // Each gene from random parent
        TWO_POINT,    // Two crossover points
        ORDER_BASED   // Order crossover for scheduling
    }

    private final Random random;
    private final CrossoverType type;
    private final int numObjectives;

    /**
     * Creates a crossover operator with specified type.
     *
     * @param type          Type of crossover
     * @param numObjectives Number of objectives in the problem
     * @param random        Random number generator
     */
    public CrossoverOperator(CrossoverType type, int numObjectives, Random random) {
        this.type = type;
        this.numObjectives = numObjectives;
        this.random = random;
    }

    /**
     * Creates a crossover operator with default uniform crossover.
     *
     * @param numObjectives Number of objectives in the problem
     * @param random        Random number generator
     */
    public CrossoverOperator(int numObjectives, Random random) {
        this(CrossoverType.UNIFORM, numObjectives, random);
    }

    /**
     * Performs crossover between two parent solutions.
     *
     * @param parent1 First parent solution
     * @param parent2 Second parent solution
     * @return Array of two offspring solutions
     */
    public SchedulingSolution[] crossover(SchedulingSolution parent1, SchedulingSolution parent2) {
        switch (type) {
            case TWO_POINT:
                return twoPointCrossover(parent1, parent2);
            case ORDER_BASED:
                return orderBasedCrossover(parent1, parent2);
            case UNIFORM:
            default:
                return uniformCrossover(parent1, parent2);
        }
    }

    /**
     * Uniform crossover: Each task assignment is inherited from a random parent.
     */
    private SchedulingSolution[] uniformCrossover(SchedulingSolution parent1, SchedulingSolution parent2) {
        int numTasks = parent1.getNumTasks();
        int numVMs = parent1.getNumVMs();

        SchedulingSolution offspring1 = new SchedulingSolution(numTasks, numVMs, numObjectives);
        SchedulingSolution offspring2 = new SchedulingSolution(numTasks, numVMs, numObjectives);

        int[] p1Assignment = parent1.getTaskAssignment();
        int[] p2Assignment = parent2.getTaskAssignment();

        for (int i = 0; i < numTasks; i++) {
            if (random.nextBoolean()) {
                offspring1.setAssignedVM(i, p1Assignment[i]);
                offspring2.setAssignedVM(i, p2Assignment[i]);
            } else {
                offspring1.setAssignedVM(i, p2Assignment[i]);
                offspring2.setAssignedVM(i, p1Assignment[i]);
            }
        }

        offspring1.rebuildTaskOrdering();
        offspring2.rebuildTaskOrdering();

        return new SchedulingSolution[]{offspring1, offspring2};
    }

    /**
     * Two-point crossover: Swaps a segment between two crossover points.
     */
    private SchedulingSolution[] twoPointCrossover(SchedulingSolution parent1, SchedulingSolution parent2) {
        int numTasks = parent1.getNumTasks();
        int numVMs = parent1.getNumVMs();

        if (numTasks < 2) {
            return uniformCrossover(parent1, parent2);
        }

        SchedulingSolution offspring1 = new SchedulingSolution(numTasks, numVMs, numObjectives);
        SchedulingSolution offspring2 = new SchedulingSolution(numTasks, numVMs, numObjectives);

        int[] p1Assignment = parent1.getTaskAssignment();
        int[] p2Assignment = parent2.getTaskAssignment();

        // Select two crossover points
        int point1 = random.nextInt(numTasks);
        int point2 = random.nextInt(numTasks);
        if (point1 > point2) {
            int temp = point1;
            point1 = point2;
            point2 = temp;
        }

        for (int i = 0; i < numTasks; i++) {
            if (i >= point1 && i <= point2) {
                // Inside crossover region: swap parents
                offspring1.setAssignedVM(i, p2Assignment[i]);
                offspring2.setAssignedVM(i, p1Assignment[i]);
            } else {
                // Outside: keep original
                offspring1.setAssignedVM(i, p1Assignment[i]);
                offspring2.setAssignedVM(i, p2Assignment[i]);
            }
        }

        offspring1.rebuildTaskOrdering();
        offspring2.rebuildTaskOrdering();

        return new SchedulingSolution[]{offspring1, offspring2};
    }

    /**
     * Order-based crossover: Preserves relative ordering of tasks within VMs.
     * Particularly useful for scheduling problems where task order matters.
     */
    private SchedulingSolution[] orderBasedCrossover(SchedulingSolution parent1, SchedulingSolution parent2) {
        int numTasks = parent1.getNumTasks();
        int numVMs = parent1.getNumVMs();

        SchedulingSolution offspring1 = new SchedulingSolution(numTasks, numVMs, numObjectives);
        SchedulingSolution offspring2 = new SchedulingSolution(numTasks, numVMs, numObjectives);

        int[] p1Assignment = parent1.getTaskAssignment();
        int[] p2Assignment = parent2.getTaskAssignment();

        // For each task, inherit assignment from one parent
        // but influence the ordering based on the other parent
        for (int i = 0; i < numTasks; i++) {
            // Randomly choose which parent provides the assignment
            if (random.nextBoolean()) {
                offspring1.setAssignedVM(i, p1Assignment[i]);
                offspring2.setAssignedVM(i, p2Assignment[i]);
            } else {
                offspring1.setAssignedVM(i, p2Assignment[i]);
                offspring2.setAssignedVM(i, p1Assignment[i]);
            }
        }

        // Rebuild ordering - could incorporate ordering information from parents
        // For now, use default ordering (task indices)
        offspring1.rebuildTaskOrdering();
        offspring2.rebuildTaskOrdering();

        // Optionally shuffle task order within each VM to explore ordering space
        shuffleVmTaskOrders(offspring1);
        shuffleVmTaskOrders(offspring2);

        return new SchedulingSolution[]{offspring1, offspring2};
    }

    /**
     * Shuffles the task execution order within each VM.
     * This explores the ordering dimension of the solution space.
     */
    private void shuffleVmTaskOrders(SchedulingSolution solution) {
        for (int vmIdx = 0; vmIdx < solution.getNumVMs(); vmIdx++) {
            java.util.List<Integer> order = solution.getTaskOrderForVM(vmIdx);
            if (order.size() > 1) {
                // Fisher-Yates shuffle
                for (int i = order.size() - 1; i > 0; i--) {
                    int j = random.nextInt(i + 1);
                    Integer temp = order.get(i);
                    order.set(i, order.get(j));
                    order.set(j, temp);
                }
            }
        }
    }

    public CrossoverType getType() {
        return type;
    }
}
