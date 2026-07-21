package com.moepus.byepregen.mixin;

import com.moepus.byepregen.worldgen.ArenaNoiseInterpolatorAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public abstract class NoiseInterpolatorArenaMixin implements ArenaNoiseInterpolatorAccess {
    @Shadow private double noise000;
    @Shadow private double noise001;
    @Shadow private double noise100;
    @Shadow private double noise101;
    @Shadow private double noise010;
    @Shadow private double noise011;
    @Shadow private double noise110;
    @Shadow private double noise111;
    @Unique private double[] byepregen$arenaGrid;
    @Unique private int byepregen$arenaPointCountXZ;

    @Unique
    @Override
    public void byepregen$allocateArenaGrid(int pointCountY, int pointCountXZ) {
        this.byepregen$arenaPointCountXZ = pointCountXZ;
        this.byepregen$arenaGrid = new double[pointCountY * pointCountXZ * pointCountXZ];
    }

    @Unique
    @Override
    public void byepregen$storeArenaColumn(int pointX, int pointZ, double[] values) {
        double[] grid = this.byepregen$arenaGrid;
        int pointCountXZ = this.byepregen$arenaPointCountXZ;
        int planeStride = pointCountXZ * pointCountXZ;
        int index = pointZ * pointCountXZ + pointX;
        for (int pointY = 0; pointY < values.length; ++pointY) {
            grid[index] = values[pointY];
            index += planeStride;
        }
    }

    @Unique
    @Override
    public void byepregen$selectArenaCell(int cellX, int cellY, int cellZ) {
        double[] grid = this.byepregen$arenaGrid;
        int pointCountXZ = this.byepregen$arenaPointCountXZ;
        int planeStride = pointCountXZ * pointCountXZ;
        int lower = cellY * planeStride + cellZ * pointCountXZ + cellX;
        int upper = lower + planeStride;
        int zOffset = pointCountXZ;

        this.noise000 = grid[lower];
        this.noise001 = grid[lower + zOffset];
        this.noise100 = grid[lower + 1];
        this.noise101 = grid[lower + zOffset + 1];
        this.noise010 = grid[upper];
        this.noise011 = grid[upper + zOffset];
        this.noise110 = grid[upper + 1];
        this.noise111 = grid[upper + zOffset + 1];
    }

    @Unique
    @Override
    public void byepregen$releaseArenaGrid() {
        this.byepregen$arenaGrid = null;
        this.byepregen$arenaPointCountXZ = 0;
    }

}
