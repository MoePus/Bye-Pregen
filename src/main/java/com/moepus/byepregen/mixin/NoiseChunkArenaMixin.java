package com.moepus.byepregen.mixin;

import com.moepus.byepregen.worldgen.ArenaCellCacheAccess;
import com.moepus.byepregen.worldgen.ArenaNoiseChunkAccess;
import com.moepus.byepregen.worldgen.ArenaNoiseInterpolatorAccess;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkArenaMixin implements ArenaNoiseChunkAccess {
    @Shadow @Final private int cellCountXZ;
    @Shadow @Final private int cellCountY;
    @Shadow @Final private int cellNoiseMinY;
    @Shadow @Final private int firstCellX;
    @Shadow @Final private int firstCellZ;
    @Shadow @Final private int cellWidth;
    @Shadow @Final private int cellHeight;
    @Shadow @Final private List<NoiseChunk.NoiseInterpolator> interpolators;
    @Shadow @Final private List<?> cellCaches;
    @Shadow @Final private DensityFunction.ContextProvider sliceFillingContextProvider;
    @Shadow private boolean interpolating;
    @Shadow private boolean fillingCell;
    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;
    @Shadow private long interpolationCounter;
    @Shadow private long arrayInterpolationCounter;

    @Shadow
    public abstract void stopInterpolation();

    @Unique private NoiseChunk.NoiseInterpolator[] byepregen$arenaInterpolators;

    @Unique
    @Override
    public void byepregen$initializeArenaInterpolation() {
        if (this.interpolating) {
            throw new IllegalStateException("Starting interpolation twice");
        }
        this.interpolating = true;
        this.interpolationCounter = 0L;
        try {
            this.byepregen$allocateArenaGrids();
            this.byepregen$fillArenaGrids();
        } catch (Throwable throwable) {
            try {
                this.byepregen$releaseArenaInterpolation();
            } finally {
                this.stopInterpolation();
            }
            throw throwable;
        }
    }

    @Unique
    private void byepregen$allocateArenaGrids() {
        int pointCountY = this.cellCountY + 1;
        int pointCountXZ = this.cellCountXZ + 1;
        NoiseChunk.NoiseInterpolator[] arenaInterpolators =
                new NoiseChunk.NoiseInterpolator[this.interpolators.size()];
        for (int i = 0; i < arenaInterpolators.length; ++i) {
            arenaInterpolators[i] = this.interpolators.get(i);
        }
        this.byepregen$arenaInterpolators = arenaInterpolators;
        for (NoiseChunk.NoiseInterpolator interpolator : arenaInterpolators) {
            ((ArenaNoiseInterpolatorAccess) interpolator)
                    .byepregen$allocateArenaGrid(pointCountY, pointCountXZ);
        }
    }

    @Unique
    private void byepregen$fillArenaGrids() {
        int pointCountXZ = this.cellCountXZ + 1;
        double[] column = new double[this.cellCountY + 1];
        // Density sampling dominates grid stores; keep Z inner to match FlatCache.values[x][z].
        for (int pointX = 0; pointX < pointCountXZ; ++pointX) {
            for (int pointZ = 0; pointZ < pointCountXZ; ++pointZ) {
                this.byepregen$fillArenaColumn(pointX, pointZ, column);
            }
        }
        ++this.arrayInterpolationCounter;
    }

    @Unique
    private void byepregen$fillArenaColumn(int pointX, int pointZ, double[] column) {
        this.cellStartBlockX = (this.firstCellX + pointX) * this.cellWidth;
        this.cellStartBlockZ = (this.firstCellZ + pointZ) * this.cellWidth;
        this.inCellX = 0;
        this.inCellZ = 0;
        ++this.arrayInterpolationCounter;
        for (NoiseChunk.NoiseInterpolator interpolator : this.byepregen$arenaInterpolators) {
            interpolator.fillArray(column, this.sliceFillingContextProvider);
            ((ArenaNoiseInterpolatorAccess) interpolator)
                    .byepregen$storeArenaColumn(pointX, pointZ, column);
        }
    }

    @Unique
    @Override
    public void byepregen$selectArenaCell(int cellX, int cellY, int cellZ) {
        this.byepregen$setArenaCellPosition(cellX, cellY, cellZ);
        for (NoiseChunk.NoiseInterpolator interpolator : this.byepregen$arenaInterpolators) {
            ((ArenaNoiseInterpolatorAccess) interpolator)
                    .byepregen$selectArenaCell(cellX, cellY, cellZ);
        }
        this.fillingCell = true;
        ++this.arrayInterpolationCounter;
        try {
            NoiseChunk noiseChunk = (NoiseChunk) (Object) this;
            for (Object cache : this.cellCaches) {
                ((ArenaCellCacheAccess) cache).byepregen$fillArenaCache(noiseChunk);
            }
        } finally {
            ++this.arrayInterpolationCounter;
            this.fillingCell = false;
        }
    }

    @Unique
    private void byepregen$setArenaCellPosition(int cellX, int cellY, int cellZ) {
        this.cellStartBlockX = (this.firstCellX + cellX) * this.cellWidth;
        this.cellStartBlockY = (this.cellNoiseMinY + cellY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + cellZ) * this.cellWidth;
        this.inCellX = 0;
        this.inCellY = 0;
        this.inCellZ = 0;
    }

    @Unique
    @Override
    public void byepregen$updateArenaForX(int blockX, double deltaX) {
        this.inCellX = blockX - this.cellStartBlockX;
        for (NoiseChunk.NoiseInterpolator interpolator : this.byepregen$arenaInterpolators) {
            interpolator.valueXZ00 = Mth.lerp(deltaX, interpolator.noise000, interpolator.noise100);
            interpolator.valueXZ01 = Mth.lerp(deltaX, interpolator.noise001, interpolator.noise101);
            interpolator.valueXZ10 = Mth.lerp(deltaX, interpolator.noise010, interpolator.noise110);
            interpolator.valueXZ11 = Mth.lerp(deltaX, interpolator.noise011, interpolator.noise111);
        }
    }

    @Unique
    @Override
    public void byepregen$updateArenaForZ(int blockZ, double deltaZ) {
        this.inCellZ = blockZ - this.cellStartBlockZ;
        for (NoiseChunk.NoiseInterpolator interpolator : this.byepregen$arenaInterpolators) {
            interpolator.valueZ0 = Mth.lerp(deltaZ, interpolator.valueXZ00, interpolator.valueXZ01);
            interpolator.valueZ1 = Mth.lerp(deltaZ, interpolator.valueXZ10, interpolator.valueXZ11);
        }
    }

    @Unique
    @Override
    public void byepregen$updateArenaForY(int blockY, double deltaY) {
        this.inCellY = blockY - this.cellStartBlockY;
        ++this.interpolationCounter;
        for (NoiseChunk.NoiseInterpolator interpolator : this.byepregen$arenaInterpolators) {
            interpolator.value = Mth.lerp(deltaY, interpolator.valueZ0, interpolator.valueZ1);
        }
    }

    @Unique
    @Override
    public void byepregen$releaseArenaInterpolation() {
        NoiseChunk.NoiseInterpolator[] arenaInterpolators = this.byepregen$arenaInterpolators;
        if (arenaInterpolators == null) {
            return;
        }
        for (NoiseChunk.NoiseInterpolator interpolator : arenaInterpolators) {
            ((ArenaNoiseInterpolatorAccess) interpolator).byepregen$releaseArenaGrid();
        }
        this.byepregen$arenaInterpolators = null;
    }
}
