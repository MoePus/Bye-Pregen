package com.moepus.byepregen.yalight;

import java.util.Arrays;

public final class YADLongQueue {
    private static final int INITIAL_CAPACITY = 2048;
    private static final int MAX_RETAINED_CAPACITY = 1 << 15;

    private long[] values;
    private final int maxRetainedCapacity;
    private int readIndex;
    private int writeIndex;

    YADLongQueue() {
        this(INITIAL_CAPACITY, MAX_RETAINED_CAPACITY);
    }

    private YADLongQueue(int initialCapacity, int maxRetainedCapacity) {
        int capacity = Math.max(initialCapacity, 1);
        this.values = new long[capacity << 1];
        this.maxRetainedCapacity = Math.max(maxRetainedCapacity, capacity);
    }

    void add(long first, long second) {
        if (this.writeIndex >= this.values.length >> 1) {
            this.compactConsumed();
        }
        if (this.writeIndex >= this.values.length >> 1) {
            this.values = Arrays.copyOf(this.values, this.values.length << 1);
        }
        int valueIndex = this.writeIndex++ << 1;
        this.values[valueIndex] = first;
        this.values[valueIndex + 1] = second;
    }

    boolean isEmpty() {
        return this.readIndex >= this.writeIndex;
    }

    long first() {
        return this.values[this.readIndex << 1];
    }

    long second() {
        return this.values[(this.readIndex << 1) + 1];
    }

    void remove() {
        ++this.readIndex;
    }

    void clear() {
        if (this.values.length > this.maxRetainedCapacity << 1) {
            this.values = new long[this.maxRetainedCapacity << 1];
        }
        this.readIndex = 0;
        this.writeIndex = 0;
    }

    private void compactConsumed() {
        if (this.readIndex == 0) {
            return;
        }
        int remaining = this.writeIndex - this.readIndex;
        if (remaining > 0) {
            System.arraycopy(this.values, this.readIndex << 1, this.values, 0, remaining << 1);
        }
        this.readIndex = 0;
        this.writeIndex = remaining;
    }
}
