package com.moepus.byepregen.worldgen.arena;

import com.moepus.byepregen.dfc.FinalDensityColumnProvider;
import com.moepus.byepregen.dfc.column.ColumnEvaluationContext;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;

public final class C2meDfcColumnAdapter {
    private final FinalDensityColumnProvider provider;
    private final ColumnBounds bounds;
    private final SourceColumns sourceColumns;
    private final double[] output;

    private C2meDfcColumnAdapter(
            FinalDensityColumnProvider provider,
            ColumnBounds bounds,
            SourceColumns sourceColumns
    ) {
        this.provider = provider;
        this.bounds = bounds;
        this.sourceColumns = sourceColumns;
        this.output = new double[bounds.length()];
    }

    public static C2meDfcColumnAdapter tryCreate(
            Object owner,
            ColumnBounds bounds,
            InterpolationSources sources
    ) {
        if (!(owner instanceof FinalDensityColumnProvider provider)) {
            return null;
        }
        if (!provider.byepregen$hasFinalDensityColumn()) {
            return null;
        }
        SourceColumns sourceColumns = new SourceColumns(sources, bounds.length());
        return new C2meDfcColumnAdapter(provider, bounds, sourceColumns);
    }

    public double[] output() {
        return this.output;
    }

    public void evaluate(int blockX, int blockZ, int inCellZ) {
        this.sourceColumns.prepare(inCellZ);
        this.provider.byepregen$evalFinalDensityColumn(
                this.output,
                blockX,
                blockZ,
                this.bounds.minY(),
                this.bounds.cellHeight(),
                this.sourceColumns
        );
    }

    private static void materializeBoundaries(double[] zBaseSteps, double[] output, int inCellZ) {
        // Arena stores (zBase, zStep) for each Y cell boundary. A fixed X/Z column
        // only needs one value from every pair as an Interpolated column source.
        for (int pointY = 0; pointY < output.length; ++pointY) {
            int edgeIndex = pointY * 2;
            output[pointY] = zBaseSteps[edgeIndex] + inCellZ * zBaseSteps[edgeIndex + 1];
        }
    }

    private static final class SourceColumns implements ColumnEvaluationContext.InterpolationProvider {
        private final InterpolationSources sources;
        private final double[][] values;
        private final int[] materializedGeneration;
        private final Map<DensityFunction, Integer> indices = new IdentityHashMap<>();
        private final int columnLength;
        private int inCellZ;
        private int generation;

        private SourceColumns(InterpolationSources sources, int columnLength) {
            this.sources = sources;
            this.values = new double[sources.interpolators().length][];
            this.materializedGeneration = new int[sources.interpolators().length];
            this.columnLength = columnLength;
            for (int i = 0; i < sources.interpolators().length; ++i) {
                this.indices.put(sources.interpolators()[i], i);
            }
        }

        private void prepare(int inCellZ) {
            this.inCellZ = inCellZ;
            if (++this.generation == 0) {
                Arrays.fill(this.materializedGeneration, 0);
                this.generation = 1;
            }
        }

        @Override
        public double[] byepregen$getColumn(DensityFunction source) {
            Integer index = this.indices.get(source);
            if (index == null) {
                throw new IllegalArgumentException("Unknown interpolated DFC column source: "
                        + source.getClass().getName());
            }
            double[] result = this.values[index];
            if (result == null) {
                result = new double[this.columnLength];
                this.values[index] = result;
            }
            if (this.materializedGeneration[index] != this.generation) {
                materializeBoundaries(this.sources.zBaseSteps()[index], result, this.inCellZ);
                this.materializedGeneration[index] = this.generation;
            }
            return result;
        }
    }

    public record ColumnBounds(int minY, int maxY, int cellHeight) {
        public ColumnBounds {
            int height = maxY - minY;
            if (cellHeight <= 0 || height < 0 || height % cellHeight != 0) {
                throw new IllegalArgumentException("Column bounds must align to a positive cell height");
            }
        }

        int length() {
            return (this.maxY - this.minY) / this.cellHeight + 1;
        }
    }

    public record InterpolationSources(
            NoiseChunk.NoiseInterpolator[] interpolators,
            double[][] zBaseSteps
    ) {
        public InterpolationSources {
            Objects.requireNonNull(interpolators, "interpolators");
            Objects.requireNonNull(zBaseSteps, "zBaseSteps");
            if (interpolators.length != zBaseSteps.length) {
                throw new IllegalArgumentException("Interpolator source arrays must have the same length");
            }
        }
    }
}
