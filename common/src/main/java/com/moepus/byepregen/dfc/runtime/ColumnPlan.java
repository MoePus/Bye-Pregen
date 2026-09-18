package com.moepus.byepregen.dfc.runtime;

import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/** Geometry checks apply to one interpolated source, never to an entire arithmetic graph. */
public record ColumnPlan(int cellSizeXz, int cellSizeY) {
    public boolean supportsVolume(DensityVolume volume) {
        return this.cellSizeXz > 1 && this.cellSizeY > 1
                && volume.stepBlockX() == 1 && volume.stepBlockY() == 1 && volume.stepBlockZ() == 1
                && aligned(volume.minBlockX(), volume.sizeX(), this.cellSizeXz)
                && aligned(volume.minBlockY(), volume.sizeY(), this.cellSizeY)
                && aligned(volume.minBlockZ(), volume.sizeZ(), this.cellSizeXz)
                && (long) volume.sizeX() * volume.sizeZ() <= Integer.MAX_VALUE / volume.sizeY();
    }
    private static boolean aligned(int minimum, int size, int cellSize) {
        return minimum % cellSize == 0 && size % cellSize == 0
                && (long) minimum + size <= Integer.MAX_VALUE;
    }
    public DensityVolume gridVolume(DensityVolume volume) {
        return new DensityVolume(volume.sizeX() / this.cellSizeXz + 1, volume.sizeY() / this.cellSizeY + 1,
                volume.sizeZ() / this.cellSizeXz + 1, volume.minBlockX(), volume.minBlockY(), volume.minBlockZ(),
                this.cellSizeXz, this.cellSizeY, this.cellSizeXz);
    }
}
