package com.moepus.byepregen.mixin.arena;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.palette.arena.Layout;
import com.moepus.byepregen.worldgen.arena.ArenaNoiseChunkAccess;
import com.moepus.byepregen.worldgen.arena.ArenaNoiseInterpolatorAccess;
import com.moepus.byepregen.worldgen.arena.DensityColumnAdapter;
import java.util.List;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@MixinGate(feature = MixinFeature.ARENA, conflictingMods = "reterraforged")
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkArenaMixin implements ArenaNoiseChunkAccess {
    @Unique private static final double byepregen$PAGE_STEP_SCALE = 1.0D / Layout.PAGE_HEIGHT;

    @Shadow @Final private int cellCountY;
    @Shadow @Final private int cellNoiseMinY;
    @Shadow @Final private int firstCellZ;
    @Shadow @Final private int cellWidth;
    @Shadow @Final private int cellHeight;
    @Shadow @Final private List<NoiseChunk.NoiseInterpolator> interpolators;
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
    @Unique private NoiseChunk.BlockStateFiller byepregen$aquiferMaterialRule;
    @Unique private double[] byepregen$arenaDensityColumn;
    @Unique private DensityColumnAdapter byepregen$dfcColumnAdapter;
    @Unique private double byepregen$densityCellLower;
    @Unique private double byepregen$densityCellDifference;
    @Unique private double byepregen$densityValue;
    @Unique private double byepregen$densityPageLower;
    @Unique private double byepregen$densityPageStep;

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
                    ordinal = 0
            ),
            index = 0,
            require = 1
    )
    private Object byepregen$captureAquiferMaterialRule(Object rule) {
        this.byepregen$aquiferMaterialRule = rule instanceof NoiseChunk.BlockStateFiller candidate
                && byepregen$isNoiseChunkLambda(candidate)
                ? candidate
                : null;
        return rule;
    }

    @Unique
    private static boolean byepregen$isNoiseChunkLambda(NoiseChunk.BlockStateFiller rule) {
        Class<?> type = rule.getClass();
        // The vanilla Aquifer filler is the only hidden lambda created by NoiseChunk
        // at this add call. Replacements from another mixin have that mixin as nest host.
        return type.isHidden() && type.isSynthetic() && type.getNestHost() == NoiseChunk.class;
    }

    @Unique
    @Override
    public NoiseChunk.BlockStateFiller byepregen$getAquiferMaterialRule() {
        return this.byepregen$aquiferMaterialRule;
    }

    @Unique
    @Override
    public void byepregen$initializeArenaInterpolation(double inverseCellWidth) {
        if (this.interpolating) {
            throw new IllegalStateException("Starting interpolation twice");
        }
        try {
            this.byepregen$allocateArenaState(inverseCellWidth);
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
        this.byepregen$allocateColumnState();
    }

    @Unique
    private void byepregen$allocateColumnState() {
        int minY = this.cellNoiseMinY * this.cellHeight;
        int maxY = (this.cellNoiseMinY + this.cellCountY) * this.cellHeight;
        this.byepregen$dfcColumnAdapter = DensityColumnAdapter.tryCreate(
                this,
                new DensityColumnAdapter.ColumnBounds(minY, maxY, this.cellHeight),
                new DensityColumnAdapter.InterpolationSources(
                        this.byepregen$arenaInterpolators,
                        this.byepregen$arenaXZBaseSteps
                )
        );
        if (this.byepregen$dfcColumnAdapter != null) {
            this.byepregen$arenaDensityColumn = this.byepregen$dfcColumnAdapter.output();
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
    public boolean byepregen$prepareArenaDensityColumn(int blockX, int blockZ) {
        if (this.byepregen$dfcColumnAdapter == null) {
            return false;
        }
        this.byepregen$dfcColumnAdapter.evaluate(blockX, blockZ, this.inCellZ);
        return true;
    }

    @Unique
    @Override
    public double byepregen$getArenaDensity(int blockY) {
        int expectedY = this.cellStartBlockY + this.inCellY;
        if (blockY != expectedY) {
            throw new IllegalArgumentException("Unexpected Arena density Y: " + blockY + " != " + expectedY);
        }
        return this.byepregen$densityValue;
    }

    @Unique
    @Override
    public void byepregen$selectArenaColumnCellY(int cellY) {
        // evalColumn produces the same cell boundary samples as vanilla. Block values
        // are still generated by the existing top-to-bottom cell/page interpolation.
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
        if (this.byepregen$arenaDensityColumn != null) {
            double lower = this.byepregen$arenaDensityColumn[cellY];
            double upper = this.byepregen$arenaDensityColumn[cellY + 1];
            this.byepregen$densityCellLower = lower;
            this.byepregen$densityCellDifference = upper - lower;
            this.byepregen$densityValue = upper;
        }
    }

    @Unique
    @Override
    public void byepregen$startArenaPage() {
        // Arena pages are traversed from high Y to low Y. Keep the subtraction-based
        // step and the four explicit transitions to match the existing rounding order.
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
        if (this.byepregen$arenaDensityColumn != null) {
            double pageLower = bottomPage
                    ? this.byepregen$densityCellLower
                    : this.byepregen$densityCellLower + lowerDelta * this.byepregen$densityCellDifference;
            double pageStep = (pageLower - this.byepregen$densityValue) * byepregen$PAGE_STEP_SCALE;
            this.byepregen$densityPageLower = pageLower;
            this.byepregen$densityPageStep = pageStep;
            this.byepregen$densityValue += pageStep;
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
        if (this.byepregen$arenaDensityColumn != null) {
            this.byepregen$densityValue += this.byepregen$densityPageStep;
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
        if (this.byepregen$arenaDensityColumn != null) {
            this.byepregen$densityValue = this.byepregen$densityPageLower - this.byepregen$densityPageStep;
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
        if (this.byepregen$arenaDensityColumn != null) {
            this.byepregen$densityValue = this.byepregen$densityPageLower;
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
        this.byepregen$arenaInterpolators = null;
        this.byepregen$arenaXZBaseSteps = null;
        this.byepregen$arenaDensityColumn = null;
        this.byepregen$dfcColumnAdapter = null;
    }
}
