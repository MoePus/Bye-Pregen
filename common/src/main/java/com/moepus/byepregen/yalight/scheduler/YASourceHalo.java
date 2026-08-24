package com.moepus.byepregen.yalight.scheduler;

import it.unimi.dsi.fastutil.HashCommon;

public final class YASourceHalo {
    public static final byte BLOCK_MASK = 1;
    public static final byte SKY_MASK = 2;

    private static final int HALO_CHUNKS_PER_SOURCE = 3 * 3;
    private static final int INITIAL_EXPECTED_KEYS =
            YAThreadedLightScheduler.BATCH_SIZE * HALO_CHUNKS_PER_SOURCE;
    private static final float LOAD_FACTOR = 0.75F;

    private long[] keys;
    private byte[] layerMasks;
    private int[] usedSlots;
    private int tableMask;
    private int maxFill;
    private int size;

    public YASourceHalo() {
        this.allocate(HashCommon.arraySize(INITIAL_EXPECTED_KEYS, LOAD_FACTOR));
    }

    public void add(long chunkKey, byte layerMask) {
        int slot = this.findSlot(chunkKey);
        if (this.layerMasks[slot] != 0) {
            this.layerMasks[slot] |= layerMask;
            return;
        }
        if (this.size >= this.maxFill) {
            this.rehash(this.keys.length << 1);
            slot = this.findSlot(chunkKey);
        }
        this.insert(slot, chunkKey, layerMask);
    }

    public int size() {
        return this.size;
    }

    public long keyAt(int index) {
        return this.keys[this.usedSlots[index]];
    }

    public byte layerMaskAt(int index) {
        return this.layerMasks[this.usedSlots[index]];
    }

    public void clear() {
        for (int i = 0; i < this.size; ++i) {
            this.layerMasks[this.usedSlots[i]] = 0;
        }
        this.size = 0;
    }

    private int findSlot(long chunkKey) {
        int slot = (int)HashCommon.mix(chunkKey) & this.tableMask;
        while (this.layerMasks[slot] != 0 && this.keys[slot] != chunkKey) {
            slot = slot + 1 & this.tableMask;
        }
        return slot;
    }

    private void insert(int slot, long chunkKey, byte layerMask) {
        this.keys[slot] = chunkKey;
        this.layerMasks[slot] = layerMask;
        this.usedSlots[this.size++] = slot;
    }

    private void allocate(int capacity) {
        this.keys = new long[capacity];
        this.layerMasks = new byte[capacity];
        this.usedSlots = new int[capacity];
        this.tableMask = capacity - 1;
        this.maxFill = HashCommon.maxFill(capacity, LOAD_FACTOR);
    }

    private void rehash(int capacity) {
        long[] oldKeys = this.keys;
        byte[] oldLayerMasks = this.layerMasks;
        int[] oldUsedSlots = this.usedSlots;
        int oldSize = this.size;
        this.allocate(capacity);
        this.size = 0;
        for (int i = 0; i < oldSize; ++i) {
            int oldSlot = oldUsedSlots[i];
            long chunkKey = oldKeys[oldSlot];
            this.insert(this.findSlot(chunkKey), chunkKey, oldLayerMasks[oldSlot]);
        }
    }
}
