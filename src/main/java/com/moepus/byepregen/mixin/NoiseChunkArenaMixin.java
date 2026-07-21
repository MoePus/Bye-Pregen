package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PaletteContainer.ArenaPelette.Layout;
import com.moepus.byepregen.worldgen.ArenaCellCacheAccess;
import com.moepus.byepregen.worldgen.ArenaNoiseChunkAccess;
import com.moepus.byepregen.worldgen.ArenaNoiseInterpolatorAccess;
import java.util.List;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkArenaMixin implements ArenaNoiseChunkAccess {
    @Unique private static final double byepregen$PAGE_STEP_SCALE = 0.25D;

    @Shadow @Final private int cellCountY;
    @Shadow @Final private int cellNoiseMinY;
    @Shadow @Final private int firstCellZ;
    @Shadow @Final private int cellWidth;
    @Shadow @Final private int cellHeight;
    @Shadow @Final private List<NoiseChunk.NoiseInterpolator> interpolators;
    @Shadow @Final private List<?> cellCaches;
    @Shadow private boolean interpolating;
    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;
    @Shadow private long interpolationCounter;

    @Shadow
    public abstract void initializeForFirstCellX();

    @Shadow
    public abstract void advanceCellX(int increment);

    @Shadow
    public abstract void stopInterpolation();

    @Shadow
    public abstract void swapSlices();

    @Unique private NoiseChunk.NoiseInterpolator[] byepregen$arenaInterpolators;
    @Unique private double[][] byepregen$arenaXZBaseSteps;

    @Unique
    @Override
    public void byepregen$initializeArenaInterpolation(double inverseCellWidth) {
        if (this.interpolating) {
            throw new IllegalStateException("Starting interpolation twice");
        }
        this.byepregen$allocateArenaState(inverseCellWidth);
        try {
            this.initializeForFirstCellX();
        } catch (Throwable throwable) {
            try {
                this.byepregen$releaseArenaInterpolation();
            } finally {
                if (this.interpolating) {
                    this.stopInterpolation();
                }
            }
            throw throwable;
        }
    }

    @Unique
    private void byepregen$allocateArenaState(double inverseCellWidth) {
        NoiseChunk.NoiseInterpolator[] arenaInterpolators =
                new NoiseChunk.NoiseInterpolator[this.interpolators.size()];
        double[][] arenaXZBaseSteps = new double[arenaInterpolators.length][];
        int edgeValueCount = (this.cellCountY + 1) * 2;
        for (int i = 0; i < arenaInterpolators.length; ++i) {
            NoiseChunk.NoiseInterpolator interpolator = this.interpolators.get(i);
            interpolator.valueXZ11 = inverseCellWidth;
            arenaInterpolators[i] = interpolator;
            arenaXZBaseSteps[i] = new double[edgeValueCount];
        }
        this.byepregen$arenaInterpolators = arenaInterpolators;
        this.byepregen$arenaXZBaseSteps = arenaXZBaseSteps;
        for (Object cellCache : this.cellCaches) {
            ((ArenaCellCacheAccess) cellCache).byepregen$setArenaPassthrough(true);
        }
    }

    @Unique
    @Override
    public void byepregen$advanceArenaCellX(int cellX) {
        this.advanceCellX(cellX);
    }

    @Unique
    @Override
    public void byepregen$prepareArenaCellXZ(int cellZ, int blockX, double deltaX) {
        this.cellStartBlockZ = (this.firstCellZ + cellZ) * this.cellWidth;
        this.inCellX = blockX - this.cellStartBlockX;
        for (int i = 0; i < this.byepregen$arenaInterpolators.length; ++i) {
            ((ArenaNoiseInterpolatorAccess) this.byepregen$arenaInterpolators[i])
                    .byepregen$prepareArenaXZ(cellZ, deltaX, this.byepregen$arenaXZBaseSteps[i]);
        }
    }

    @Unique
    @Override
    public void byepregen$beginArenaColumn(int blockZ) {
        this.inCellZ = blockZ - this.cellStartBlockZ;
        for (int i = 0; i < this.byepregen$arenaInterpolators.length; ++i) {
            NoiseChunk.NoiseInterpolator interpolator = this.byepregen$arenaInterpolators[i];
            interpolator.valueXZ00 = byepregen$sampleArenaXZ(
                    this.byepregen$arenaXZBaseSteps[i], this.cellCountY, this.inCellZ
            );
        }
    }

    @Unique
    @Override
    public void byepregen$selectArenaColumnCellY(int cellY) {
        this.cellStartBlockY = (this.cellNoiseMinY + cellY) * this.cellHeight;
        this.inCellY = this.cellHeight;
        for (int i = 0; i < this.byepregen$arenaInterpolators.length; ++i) {
            NoiseChunk.NoiseInterpolator interpolator = this.byepregen$arenaInterpolators[i];
            double lower = byepregen$sampleArenaXZ(this.byepregen$arenaXZBaseSteps[i], cellY, this.inCellZ);
            double upper = interpolator.valueXZ00;
            interpolator.valueXZ00 = lower;
            interpolator.valueXZ01 = upper - lower;
            interpolator.value = upper;
        }
    }

    @Unique
    @Override
    public void byepregen$startArenaPage() {
        --this.inCellY;
        ++this.interpolationCounter;
        int pageStart = this.inCellY - (Layout.PAGE_HEIGHT - 1);
        boolean bottomPage = pageStart == 0;
        double lowerDelta = bottomPage ? 0.0D : (double) pageStart / this.cellHeight;
        for (NoiseChunk.NoiseInterpolator interpolator : this.byepregen$arenaInterpolators) {
            double cellLower = interpolator.valueXZ00;
            double pageLower = bottomPage
                    ? cellLower
                    : cellLower + lowerDelta * interpolator.valueXZ01;
            double pageStep = (pageLower - interpolator.value) * byepregen$PAGE_STEP_SCALE;
            interpolator.valueZ0 = pageLower;
            interpolator.valueZ1 = pageStep;
            interpolator.value += pageStep;
        }
    }

    @Unique
    @Override
    public void byepregen$advanceArenaPageY() {
        --this.inCellY;
        ++this.interpolationCounter;
        for (NoiseChunk.NoiseInterpolator interpolator : this.byepregen$arenaInterpolators) {
            interpolator.value += interpolator.valueZ1;
        }
    }

    @Unique
    @Override
    public void byepregen$setArenaPageLowerStepY() {
        --this.inCellY;
        ++this.interpolationCounter;
        for (NoiseChunk.NoiseInterpolator interpolator : this.byepregen$arenaInterpolators) {
            interpolator.value = interpolator.valueZ0 - interpolator.valueZ1;
        }
    }

    @Unique
    @Override
    public void byepregen$setArenaPageLowerY() {
        --this.inCellY;
        ++this.interpolationCounter;
        for (NoiseChunk.NoiseInterpolator interpolator : this.byepregen$arenaInterpolators) {
            interpolator.value = interpolator.valueZ0;
        }
    }

    @Unique
    private static double byepregen$sampleArenaXZ(double[] zBaseSteps, int pointY, int inCellZ) {
        int edgeIndex = pointY * 2;
        return zBaseSteps[edgeIndex] + inCellZ * zBaseSteps[edgeIndex + 1];
    }

    @Unique
    @Override
    public void byepregen$finishArenaCellX() {
        this.swapSlices();
    }

    @Unique
    @Override
    public void byepregen$releaseArenaInterpolation() {
        for (Object cellCache : this.cellCaches) {
            ((ArenaCellCacheAccess) cellCache).byepregen$setArenaPassthrough(false);
        }
        this.byepregen$arenaInterpolators = null;
        this.byepregen$arenaXZBaseSteps = null;
    }
}
