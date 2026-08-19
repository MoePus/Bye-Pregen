/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.runtime;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.Type;

/** Mutable execution state owned and reused by one NoiseChunk. */
public final class ColumnEvaluationContext {
    /** Canonical cache-miss payload shared by the column runtime and generated helpers. */
    public static final long MEMO_MISS_BITS = 0x7ffd_db97_2d48_6a4fL;
    public static final double MEMO_MISS = Double.longBitsToDouble(MEMO_MISS_BITS);
    public static final String METHOD_DESC = Type.getMethodDescriptor(
            Type.VOID_TYPE, Type.getType(ColumnEvaluationContext.class));

    private final MutablePointContext point = new MutablePointContext();
    private double[] memoizedValues = new double[0];
    private boolean[] memoizedReady = new boolean[0];
    private double[][] interpolationColumns = new double[0][];
    private double[][] scratchArrays = new double[4][];
    private InterpolationProvider interpolationProvider;
    private double[] output;
    private int memoizedCount;
    private int interpolationCount;
    private int scratchDepth;
    private int blockX;
    private int blockZ;
    private int minY;
    private int cellHeight;
    private boolean active;

    public void prepare(double[] output, int blockX, int blockZ, int minY, int cellHeight,
                        InterpolationProvider interpolationProvider) {
        if (this.active) throw new IllegalStateException("Density column context is already active");
        this.output = Objects.requireNonNull(output, "output");
        this.interpolationProvider = Objects.requireNonNull(interpolationProvider, "interpolationProvider");
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
        if (this.memoizedValues.length < count) this.memoizedValues = new double[count];
        if (this.memoizedReady.length < count) this.memoizedReady = new boolean[count];
        Arrays.fill(this.memoizedValues, 0, count, MEMO_MISS);
        Arrays.fill(this.memoizedReady, 0, count, false);
        this.memoizedCount = count;
    }

    public void prepareInterpolationCount(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative interpolation count");
        if (this.interpolationColumns.length < count) this.interpolationColumns = new double[count][];
        this.interpolationCount = count;
    }

    public double memoizedValue(int index) {
        this.checkMemoizedIndex(index);
        return this.memoizedValues[index];
    }

    public boolean memoizedValueReady(int index) {
        this.checkMemoizedIndex(index);
        return this.memoizedReady[index];
    }

    /**
     * Tests the raw-bit sentinel without treating an already computed sentinel-valued result
     * as a miss. Generated code uses this method so the miss protocol remains explicit in the
     * reachable bytecode graph.
     */
    public boolean memoizedValueMiss(int index) {
        this.checkMemoizedIndex(index);
        return !this.memoizedReady[index]
                && Double.doubleToRawLongBits(this.memoizedValues[index]) == MEMO_MISS_BITS;
    }

    public double setMemoizedValue(int index, double value) {
        this.checkMemoizedIndex(index);
        this.memoizedValues[index] = value;
        this.memoizedReady[index] = true;
        return value;
    }

    public double interpolatedValue(int index, DensityFunction source, int blockY) {
        int delta = blockY - this.minY;
        if (delta % this.cellHeight != 0) throw outsideColumn(blockY);
        int valueIndex = delta / this.cellHeight;
        double[] values = this.interpolatedColumn(index, source);
        if (valueIndex < 0 || valueIndex >= values.length) throw outsideColumn(blockY);
        return values[valueIndex];
    }

    public void copyInterpolatedColumn(int index, DensityFunction source, double[] target) {
        double[] values = this.interpolatedColumn(index, source);
        if (target.length != values.length) {
            throw new IllegalArgumentException("Interpolation target length mismatch");
        }
        System.arraycopy(values, 0, target, 0, values.length);
    }

    public void copyInterpolatedColumnRange(int index, DensityFunction source, double[] target,
                                            int fromInclusive, int toExclusive) {
        double[] values = this.interpolatedColumn(index, source);
        if (target.length != values.length) {
            throw new IllegalArgumentException("Interpolation target length mismatch");
        }
        if (fromInclusive < 0 || toExclusive < fromInclusive || toExclusive > values.length) {
            throw new IndexOutOfBoundsException("Interpolation column range: "
                    + fromInclusive + ".." + toExclusive);
        }
        System.arraycopy(values, fromInclusive, target, fromInclusive, toExclusive - fromInclusive);
    }

    public double delegateValue(DensityFunction function, int x, int y, int z) {
        return function.compute(this.point.at(x, y, z));
    }

    public double flatValue(DensityFunction function, int x, int y, int z) {
        DensityFunction.FunctionContext context = this.point.at(x, y, z);
        return function instanceof FlatCacheAccess access
                ? access.byepregen$sampleFlatCache(x, z, context)
                : function.compute(context);
    }

    public double[] borrowDoubleArray(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative scratch length");
        if (this.scratchDepth == this.scratchArrays.length) {
            this.scratchArrays = Arrays.copyOf(this.scratchArrays, this.scratchDepth * 2);
        }
        double[] result = this.scratchArrays[this.scratchDepth];
        if (result == null || result.length != length) result = new double[length];
        this.scratchArrays[this.scratchDepth++] = null;
        return result;
    }

    public void recycleDoubleArray(double[] array) {
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

    public double[] output() { return this.output; }
    public int x() { return this.blockX; }
    public int z() { return this.blockZ; }
    public int minY() { return this.minY; }
    public int cellHeight() { return this.cellHeight; }

    public void clear() {
        if (!this.active) return;
        if (this.scratchDepth != 0) throw new IllegalStateException("Leaked density column scratch arrays");
        Arrays.fill(this.interpolationColumns, 0, this.interpolationCount, null);
        this.memoizedCount = 0;
        this.interpolationCount = 0;
        this.interpolationProvider = null;
        this.output = null;
        this.active = false;
    }

    private double[] interpolatedColumn(int index, DensityFunction source) {
        if (index < 0 || index >= this.interpolationCount) {
            throw new IndexOutOfBoundsException("Column interpolation index: " + index);
        }
        double[] existing = this.interpolationColumns[index];
        if (existing != null) return existing;
        double[] resolved = Objects.requireNonNull(
                this.interpolationProvider.byepregen$getColumn(source),
                "interpolationProvider returned null");
        if (resolved.length != this.output.length) {
            throw new IllegalArgumentException("Interpolation source length mismatch");
        }
        this.interpolationColumns[index] = resolved;
        return resolved;
    }

    private void checkMemoizedIndex(int index) {
        if (index < 0 || index >= this.memoizedCount) {
            throw new IndexOutOfBoundsException("Column memoized index: " + index);
        }
    }

    private static IllegalArgumentException outsideColumn(int y) {
        return new IllegalArgumentException("Y is outside the active density column: " + y);
    }

    public interface InterpolationProvider {
        double[] byepregen$getColumn(DensityFunction source);
    }

    private static final class MutablePointContext implements DensityFunction.FunctionContext {
        private int x;
        private int y;
        private int z;

        private MutablePointContext at(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override public int blockX() { return this.x; }
        @Override public int blockY() { return this.y; }
        @Override public int blockZ() { return this.z; }
    }
}
