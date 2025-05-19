package dev.anye.core.math;

import java.util.HashMap;
import java.util.Random;

public class _WeightRandom<T> {
    private final T[] items;
    private final int[] prefixSum;
    private final Random random;

    public _WeightRandom(HashMap<T, Integer> map) {
        if (map.isEmpty()) {
            throw new IllegalArgumentException("error:: the length of items and weights must be equal and not empty");
        }
        this.items = (T[]) new Object[map.size()];
        this.prefixSum = new int[map.size()];
        this.random = new Random();

        int[] a = {0};
        int[] weights = new int[map.size()];
        map.forEach((t, integer) -> {

            items[a[0]] = t;
            weights[a[0]] = integer;
            a[0]++;
        });
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] < 0) {
                throw new IllegalArgumentException("error:: weight cannot be negative");
            }
            sum += weights[i];
            prefixSum[i] = sum;
        }
    }
    public _WeightRandom(T[] items, int[] weights) {
        if (items.length != weights.length || items.length == 0) {
            throw new IllegalArgumentException("error:: the length of items and weights must be equal and not empty");
        }
        this.items = items;
        this.prefixSum = new int[weights.length];
        this.random = new Random();
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] < 0) {
                throw new IllegalArgumentException("error:: weight cannot be negative");
            }
            sum += weights[i];
            prefixSum[i] = sum;
        }
    }

    public T getRandom() {
        int totalWeight = prefixSum[prefixSum.length - 1];
        int randomValue = random.nextInt(totalWeight);
        int index = binarySearch(randomValue);
        return items[index];
    }

    private int binarySearch(int value) {
        int left = 0, right = prefixSum.length - 1;
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (value < prefixSum[mid]) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        return left;
    }
}
