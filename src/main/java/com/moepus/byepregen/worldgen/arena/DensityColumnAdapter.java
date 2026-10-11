package com.moepus.byepregen.worldgen.arena;

import com.moepus.byepregen.dfc.runtime.ColumnEvaluationContext;
import com.moepus.byepregen.dfc.runtime.FinalDensityColumnProvider;
import com.moepus.byepregen.dfc.runtime.InterpolatedColumn;
import com.moepus.byepregen.palette.arena.Layout;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;

public final class DensityColumnAdapter {
    private static final int BLOCK_STEP = 1;
    private final FinalDensityColumnProvider provider;
    private final ColumnBounds bounds;
    private final SourceColumns sourceColumns;
    private final double[] output;

    private DensityColumnAdapter(
            FinalDensityColumnProvider provider,
            ColumnBounds bounds,
            SourceColumns sourceColumns
    ) {
        this.provider = provider;
        this.bounds = bounds;
        this.sourceColumns = sourceColumns;
        this.output = new double[bounds.blockCount()];
    }

    public static DensityColumnAdapter tryCreate(
            Object owner,
            ColumnBounds bounds,
            InterpolationSources sources
    ) {
        if (!(owner instanceof FinalDensityColumnProvider provider)
                || !provider.byepregen$hasFinalDensityColumn()) {
            return null;
        }
        return new DensityColumnAdapter(provider, bounds,
                new SourceColumns(sources, bounds));
    }

    public double[] output() {
        return this.output;
    }

    public void evaluate(int blockX, int blockZ, int inCellZ) {
        this.sourceColumns.prepare(inCellZ);
        this.provider.byepregen$evalFinalDensityColumn(this.output, blockX, blockZ,
                this.bounds.minY(), BLOCK_STEP, this.sourceColumns);
    }

    private static final class SourceColumns implements ColumnEvaluationContext.InterpolationProvider {
        private final Map<DensityFunction, ArenaInterpolationColumn> lookup = new IdentityHashMap<>();
        private int inCellZ;
        private long generation;

        private SourceColumns(InterpolationSources sources, ColumnBounds bounds) {
            for (int i = 0; i < sources.interpolators().length; ++i) {
                ArenaInterpolationColumn column = new ArenaInterpolationColumn(
                        sources.zBaseSteps()[i], bounds.cellHeight(), bounds.blockCount());
                this.lookup.put(sources.interpolators()[i], column);
            }
        }

        private void prepare(int inCellZ) {
            this.inCellZ = inCellZ;
            this.generation++;
        }

        @Override
        public InterpolatedColumn byepregen$getColumn(DensityFunction source) {
            ArenaInterpolationColumn column = this.lookup.get(source);
            if (column == null) {
                throw new IllegalArgumentException("Unknown interpolated density column source: "
                        + source.getClass().getName());
            }
            column.prepareIfChanged(this.inCellZ, this.generation);
            return column;
        }
    }

    public record ColumnBounds(int minY, int maxY, int cellHeight) {
        public ColumnBounds {
            int height = maxY - minY;
            if (cellHeight <= 0 || cellHeight % Layout.PAGE_HEIGHT != 0 || height <= 0 || height % cellHeight != 0) {
                throw new IllegalArgumentException("Column bounds must align to a positive cell height");
            }
        }

        int blockCount() {
            return this.maxY - this.minY;
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
