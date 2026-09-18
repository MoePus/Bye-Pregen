/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.runtime;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** The 26.2 lazy memo/scratch state, owned by one column session rather than a NoiseChunk. */
public final class ColumnEvaluationContext {
    private static final int MEMO_MISS_BITS = 0x7fc1_6a4f;
    private static final float MEMO_MISS = Float.intBitsToFloat(MEMO_MISS_BITS);
    private final SamplingWorkspace workspace;
    private SamplerContext pointContext;

    public ColumnEvaluationContext(SamplingWorkspace workspace) { this.workspace = workspace; }
    private float[] memoizedValues = new float[0];
    private boolean[] memoizedReady = new boolean[0];
    private float[][] scratchArrays = new float[4][];
    private float[] output;
    private int memoizedCount;
    private int scratchDepth;
    private int blockX;
    private int blockZ;
    private int minY;
    private int cellHeight;
    private boolean active;

    public void prepare(float[] output, int blockX, int blockZ, int minY, int cellHeight) {
        if (this.active) throw new IllegalStateException("Density column context is already active");
        this.output = Objects.requireNonNull(output, "output");
        if (output.length == 0) throw new IllegalArgumentException("Column output must not be empty");
        if (cellHeight <= 0) throw new IllegalArgumentException("Column cell height must be positive");
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.minY = minY;
        this.cellHeight = cellHeight;
        this.active = true;
    }

    public void prepareMemoizedCount(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative memoized count");
        if (this.memoizedValues.length < count) this.memoizedValues = new float[count];
        if (this.memoizedReady.length < count) this.memoizedReady = new boolean[count];
        Arrays.fill(this.memoizedValues, 0, count, MEMO_MISS);
        Arrays.fill(this.memoizedReady, 0, count, false);
        this.memoizedCount = count;
    }

    public float memoizedValue(int index) {
        this.checkMemoizedIndex(index);
        return this.memoizedValues[index];
    }

    /**
     * Tests the raw-bit sentinel without treating an already computed sentinel-valued result
     * as a miss. Generated code uses this method so the miss protocol remains explicit in the
     * reachable bytecode graph.
     */
    public boolean memoizedValueMiss(int index) {
        this.checkMemoizedIndex(index);
        return !this.memoizedReady[index]
                && Float.floatToRawIntBits(this.memoizedValues[index]) == MEMO_MISS_BITS;
    }

    public float setMemoizedValue(int index, float value) {
        this.checkMemoizedIndex(index);
        this.memoizedValues[index] = value;
        this.memoizedReady[index] = true;
        return value;
    }

    public float delegateValue(DensitySampler sampler, int x, int y, int z) {
        return this.pointContext == null ? this.workspace.value(sampler, x, y, z)
                : sampler.sampleValue(this.pointContext, x, y, z);
    }

    public void copySourceColumn(DensitySampler sampler, float[] target, int from, int to) {
        this.workspace.copyColumn(sampler, this.blockX, this.blockZ, target, from, to);
    }

    public float[] borrowFloatArray(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative scratch length");
        if (this.scratchDepth == this.scratchArrays.length) {
            this.scratchArrays = Arrays.copyOf(this.scratchArrays, this.scratchDepth * 2);
        }
        float[] result = this.scratchArrays[this.scratchDepth];
        if (result == null || result.length != length) result = new float[length];
        this.scratchArrays[this.scratchDepth++] = null;
        return result;
    }

    public void recycleFloatArray(float[] array) {
        Objects.requireNonNull(array, "array");
        if (this.scratchDepth == 0) throw new IllegalStateException("Scratch pool underflow");
        this.scratchArrays[--this.scratchDepth] = array;
    }

    public void resetScratchAfterFailure() {
        this.scratchDepth = 0;
    }

    /** Called once by generated evalColumn before accessing the active column state. */
    public void assertActive() {
        if (!this.active) throw new IllegalStateException("Density column context is not active");
    }

    public float[] output() { return this.output; }
    public int x() { return this.blockX; }
    public int z() { return this.blockZ; }
    public int minY() { return this.minY; }
    public int cellHeight() { return this.cellHeight; }

    public void clear() {
        if (!this.active) return;
        if (this.scratchDepth != 0) throw new IllegalStateException("Leaked density column scratch arrays");
        this.memoizedCount = 0;
        this.output = null;
        this.active = false;
    }

    private void checkMemoizedIndex(int index) {
        if (index < 0 || index >= this.memoizedCount) {
            throw new IndexOutOfBoundsException("Column memoized index: " + index);
        }
    }

    public void preparePoint(SamplerContext context, int x, int y, int z) {
        if (this.active) throw new IllegalStateException("Point context is already active");
        this.pointContext = context;
        this.blockX = x;
        this.minY = y;
        this.blockZ = z;
        this.active = true;
    }

    public void clearPoint() {
        this.pointContext = null;
        this.memoizedCount = 0;
        this.active = false;
    }
}
