package com.cloudsimulator.utils;

import java.util.List;
import java.util.Random;

/**
 * Centralized random number generator with seed support for experiment repeatability.
 * Singleton pattern to ensure all random operations use the same seed.
 */
public class RandomGenerator {
    private static RandomGenerator instance;
    private Random random;
    private long seed;

    private RandomGenerator(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * Initialize the random generator with a seed.
     * Must be called before any random operations.
     */
    public static void initialize(long seed) {
        instance = new RandomGenerator(seed);
    }

    /**
     * Get the singleton instance.
     */
    public static RandomGenerator getInstance() {
        if (instance == null) {
            throw new IllegalStateException("RandomGenerator not initialized. Call initialize(seed) first.");
        }
        return instance;
    }

    /**
     * Reset with a new seed.
     */
    public void reset(long newSeed) {
        this.seed = newSeed;
        this.random = new Random(newSeed);
    }

    public long getSeed() {
        return seed;
    }

    // Random number generation methods

    public int nextInt() {
        return random.nextInt();
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public long nextLong() {
        return random.nextLong();
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    public float nextFloat() {
        return random.nextFloat();
    }

    public double nextGaussian() {
        return random.nextGaussian();
    }

    /**
     * Returns a random integer between min (inclusive) and max (inclusive).
     */
    public int nextInt(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /**
     * Returns a random double between min (inclusive) and max (exclusive).
     */
    public double nextDouble(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    /**
     * Returns a random element from an array.
     */
    public <T> T randomElement(T[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array is null or empty");
        }
        return array[random.nextInt(array.length)];
    }

    /**
     * Returns a random element from a list.
     */
    public <T> T randomElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List is null or empty");
        }
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Shuffles a list in place.
     */
    public <T> void shuffle(List<T> list) {
        java.util.Collections.shuffle(list, random);
    }
}
