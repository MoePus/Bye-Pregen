/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.column;

import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.Type;

/**
 * Mutable execution state reused by one noise chunk's final-density column path.
 */
public final class ColumnEvaluationContext {

    static final String METHOD_DESC = Type.getMethodDescriptor(
            Type.VOID_TYPE, Type.getType(ColumnEvaluationContext.class));

    private final DfcObjectCache objectCache;
    private double[] memoizedValues = new double[0];
    private double[][] interpolationColumns = new double[0][];
    private InterpolationProvider interpolationProvider;
    private double[] output;
    private int memoizedCount;
    private int interpolationCount;
    private int blockX;
    private int blockZ;
    private int minY;
    private int cellHeight;
    private boolean active;

    public ColumnEvaluationContext(DfcObjectCache objectCache) {
        this.objectCache = Objects.requireNonNull(objectCache, "objectCache");
    }

    public void prepare(double[] output, int blockX, int blockZ, int minY, int cellHeight,
                        InterpolationProvider interpolationProvider) {
        if (this.active) {
            throw new IllegalStateException("DFC column context is already active");
        }
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(interpolationProvider, "interpolationProvider");
        if (output.length == 0) {
            throw new IllegalArgumentException("Column output must not be empty");
        }
        if (cellHeight <= 0) {
            throw new IllegalArgumentException("Column cell height must be positive");
        }
        this.output = output;
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.minY = minY;
        this.cellHeight = cellHeight;
        this.interpolationProvider = interpolationProvider;
        this.active = true;
    }

    public void prepareMemoizedCount(int count) {
        this.requireActive();
        if (count < 0) {
            throw new IllegalArgumentException("Column memoized count must not be negative");
        }
        if (this.memoizedValues.length < count) {
            this.memoizedValues = new double[count];
        }
        Arrays.fill(this.memoizedValues, 0, count,
                Double.longBitsToDouble(IFastCacheLike.CACHE_MISS_NAN_BITS));
        this.memoizedCount = count;
    }

    public void prepareInterpolationCount(int count) {
        this.requireActive();
        if (count < 0) {
            throw new IllegalArgumentException("Column interpolation count must not be negative");
        }
        if (this.interpolationColumns.length < count) {
            this.interpolationColumns = new double[count][];
        }
        this.interpolationCount = count;
    }

    public void setMemoizedValue(int index, double value) {
        this.checkMemoizedIndex(index);
        this.memoizedValues[index] = value;
    }

    public double memoizedValue(int index) {
        this.checkMemoizedIndex(index);
        return this.memoizedValues[index];
    }

    public void copyInterpolatedColumn(int index, IFastCacheLike source, double[] destination) {
        double[] values = this.interpolatedColumn(index, source);
        if (destination.length != values.length) {
            throw new IllegalArgumentException("Interpolation destination length mismatch");
        }
        System.arraycopy(values, 0, destination, 0, values.length);
    }

    public double interpolatedValue(int index, IFastCacheLike source, int blockY) {
        this.requireActive();
        int delta = blockY - this.minY;
        if (delta % this.cellHeight != 0) {
            throw new IllegalArgumentException("Y is outside the active DFC column: " + blockY);
        }
        int valueIndex = delta / this.cellHeight;
        double[] values = this.interpolatedColumn(index, source);
        if (valueIndex < 0 || valueIndex >= values.length) {
            throw new IllegalArgumentException("Y is outside the active DFC column: " + blockY);
        }
        return values[valueIndex];
    }

    public double[] output() {
        this.requireActive();
        return this.output;
    }

    public int x() {
        this.requireActive();
        return this.blockX;
    }

    public int z() {
        this.requireActive();
        return this.blockZ;
    }

    public int minY() {
        this.requireActive();
        return this.minY;
    }

    public int cellHeight() {
        this.requireActive();
        return this.cellHeight;
    }

    public DfcObjectCache objectCache() {
        this.requireActive();
        return this.objectCache;
    }


    public void clear() {
        if (!this.active) {
            return;
        }
        Arrays.fill(this.interpolationColumns, 0, this.interpolationCount, null);
        this.memoizedCount = 0;
        this.interpolationCount = 0;
        this.interpolationProvider = null;
        this.output = null;
        this.blockX = 0;
        this.blockZ = 0;
        this.minY = 0;
        this.cellHeight = 0;
        this.active = false;
    }

    private double[] interpolatedColumn(int index, IFastCacheLike source) {
        this.requireActive();
        if (index < 0 || index >= this.interpolationCount) {
            throw new IndexOutOfBoundsException("Column interpolation index: " + index);
        }
        double[] existing = this.interpolationColumns[index];
        if (existing != null) {
            return existing;
        }
        // Generated source slots avoid an identity-map lookup in every scalar branch lane.
        double[] resolved = Objects.requireNonNull(
                Objects.requireNonNull(this.interpolationProvider, "interpolationProvider")
                        .byepregen$getColumn(source),
                "interpolationProvider returned null"
        );
        if (resolved.length != this.output.length) {
            throw new IllegalArgumentException("Interpolation source length mismatch");
        }
        this.interpolationColumns[index] = resolved;
        return resolved;
    }

    private void checkMemoizedIndex(int index) {
        this.requireActive();
        if (index < 0 || index >= this.memoizedCount) {
            throw new IndexOutOfBoundsException("Column memoized index: " + index);
        }
    }

    private void requireActive() {
        if (!this.active) {
            throw new IllegalStateException("DFC column context is not active");
        }
    }

    public interface InterpolationProvider {
        double[] byepregen$getColumn(DensityFunction source);
    }
}
