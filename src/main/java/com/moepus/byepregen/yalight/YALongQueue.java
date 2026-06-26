package com.moepus.byepregen.yalight;

import java.util.Arrays;

final class YALongQueue {
    private static final int INITIAL_CAPACITY = 4096;
    private static final int MAX_RETAINED_CAPACITY = 1 << 17;

    private long[] values;
    private final int maxRetainedCapacity;
    private int readIndex;
    private int writeIndex;

    YALongQueue() {
        this(INITIAL_CAPACITY, MAX_RETAINED_CAPACITY);
    }

    YALongQueue(int initialCapacity) {
        this(initialCapacity, Math.max(initialCapacity, 1));
    }

    private YALongQueue(int initialCapacity, int maxRetainedCapacity) {
        int capacity = Math.max(initialCapacity, 1);
        this.values = new long[capacity];
        this.maxRetainedCapacity = Math.max(maxRetainedCapacity, capacity);
    }

    void add(long value) {
        if (this.writeIndex >= this.values.length) {
            this.compactConsumed();
        }
        if (this.writeIndex >= this.values.length) {
            this.values = Arrays.copyOf(this.values, this.values.length << 1);
        }
        this.values[this.writeIndex++] = value;
    }

    boolean isEmpty() {
        return this.readIndex >= this.writeIndex;
    }

    long poll() {
        return this.values[this.readIndex++];
    }

    void clear() {
        if (this.values.length > this.maxRetainedCapacity) {
            this.values = new long[this.maxRetainedCapacity];
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
            System.arraycopy(this.values, this.readIndex, this.values, 0, remaining);
        }
        this.readIndex = 0;
        this.writeIndex = remaining;
    }
}
