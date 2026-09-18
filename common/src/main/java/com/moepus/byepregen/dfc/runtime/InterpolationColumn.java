package com.moepus.byepregen.dfc.runtime;

import java.util.function.IntFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** Cell geometry only locates boundaries. XZ coefficients and output scratch span the entire column. */
final class InterpolationColumn {
    private static final int SHORT_CELL = 4;
    private static final int TALL_CELL = 8;
    private final float[] grid, plane, edges, steps, output;
    private final int cellSizeXz, cellSizeY, gridHeight, planeSize;
    private final float inverseXz, inverseY;

    InterpolationColumn(float[] grid, float[] output, DensityVolume gridVolume, ColumnPlan plan,
                        IntFunction<? extends DensityBuffer> acquire) {
        this.grid = grid;
        this.output = output;
        this.cellSizeXz = plan.cellSizeXz();
        this.cellSizeY = plan.cellSizeY();
        this.inverseXz = 1.0F / this.cellSizeXz;
        this.inverseY = 1.0F / this.cellSizeY;
        this.gridHeight = gridVolume.sizeY();
        this.planeSize = gridVolume.sizeX() * this.gridHeight;
        this.plane = acquire.apply(this.planeSize).values;
        this.edges = acquire.apply(this.gridHeight).values;
        this.steps = acquire.apply(this.gridHeight - 1).values;
    }

    void prepareZ(int z) {
        int base = z / this.cellSizeXz * this.planeSize;
        float alpha = (z % this.cellSizeXz) * this.inverseXz;
        for (int i = 0; i < this.planeSize; ++i) {
            this.plane[i] = Mth.lerp(alpha, this.grid[base + i], this.grid[base + this.planeSize + i]);
        }
    }

    void fill(int x) {
        int base = x / this.cellSizeXz * this.gridHeight;
        float alpha = (x % this.cellSizeXz) * this.inverseXz;
        for (int i = 0; i < this.gridHeight; ++i) {
            this.edges[i] = Mth.lerp(alpha, this.plane[base + i], this.plane[base + this.gridHeight + i]);
        }
        for (int i = 0; i < this.gridHeight - 1; ++i) {
            this.steps[i] = (this.edges[i + 1] - this.edges[i]) * this.inverseY;
        }
        int cells = this.gridHeight - 1;
        switch (this.cellSizeY) {
            case SHORT_CELL -> expandShort(this.edges, this.steps, this.output, cells);
            case TALL_CELL -> expandTall(this.edges, this.steps, this.output, cells);
            default -> expand(this.edges, this.steps, this.output, cells, this.cellSizeY);
        }
    }

    // Constant inner bounds let the JVM unroll native heights without specializing the rest of the plan.
    private static void expandShort(float[] edges, float[] steps, float[] output, int cells) {
        for (int cell = 0; cell < cells; ++cell) {
            float step = steps[cell], value = edges[cell] + step * 0.0F;
            for (int y = 0; y < SHORT_CELL; ++y) {
                output[cell * SHORT_CELL + y] = value;
                value += step;
            }
        }
    }

    private static void expandTall(float[] edges, float[] steps, float[] output, int cells) {
        for (int cell = 0; cell < cells; ++cell) {
            float step = steps[cell], value = edges[cell] + step * 0.0F;
            for (int y = 0; y < TALL_CELL; ++y) {
                output[cell * TALL_CELL + y] = value;
                value += step;
            }
        }
    }

    private static void expand(float[] edges, float[] steps, float[] output, int cells, int cellHeight) {
        for (int cell = 0; cell < cells; ++cell) {
            // Keep the native y0 initialization even at zero (signed zero and non-finite values).
            float step = steps[cell], value = edges[cell] + step * 0.0F;
            for (int y = 0; y < cellHeight; ++y) {
                output[cell * cellHeight + y] = value;
                value += step;
            }
        }
    }
}
