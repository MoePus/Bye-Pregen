package com.moepus.byepregen.worldgen.feature;

import java.util.BitSet;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.DiskFeature;

public final class FastDiskPlacement {
    private static final int UPDATE_CLIENTS = 2;

    /** What one position contributed: nothing, a placed block, or a kept-but-unreplaced position. */
    enum PositionResult {
        NOT_REPLACED,
        PLACED,
        PRESERVED
    }

    private final DiskFeature config;
    private final FastRuleBasedBlockStateProvider stateProvider;
    private final WorldGenLevel level;
    private final RandomSource random;
    private final FastDiskStateCursor cursor;
    private final KnownFalseDiskPredicateCache knownFalse;
    private final ColumnFallback fallback;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public FastDiskPlacement(
            DiskFeature config,
            FastRuleBasedBlockStateProvider stateProvider,
            WorldGenLevel level,
            RandomSource random,
            FastDiskStateCursor cursor,
            KnownFalseDiskPredicateCache knownFalse,
            ColumnFallback fallback
    ) {
        this.config = config;
        this.stateProvider = stateProvider;
        this.level = level;
        this.random = random;
        this.cursor = cursor;
        this.knownFalse = knownFalse;
        this.fallback = fallback;
    }

    public boolean placeOrigin(int originX, int originY, int originZ) {
        int radius = this.config.radius().sample(this.random);
        int radiusSquared = radius * radius;
        boolean placed = false;
        for (int z = originZ - radius; z <= originZ + radius; z++) {
            int dz = z - originZ;
            for (int x = originX - radius; x <= originX + radius; x++) {
                int dx = x - originX;
                if (dx * dx + dz * dz <= radiusSquared) {
                    placed |= this.placeColumn(x, z, originY);
                }
            }
        }
        return placed;
    }

    private boolean placeColumn(int x, int z, int originY) {
        int maximumY = originY + this.config.halfHeight();
        int minimumY = originY - this.config.halfHeight();
        if (!this.cursor.selectColumn(x, z)) {
            if (this.knownFalse != null) {
                this.knownFalse.clear();
            }
            this.pos.set(x, maximumY, z);
            return this.fallback.place(new FastDiskFeature.ColumnContext(
                    this.config, this.level, this.random, maximumY, minimumY, this.pos
            ));
        }

        boolean columnPlaced = false;
        boolean placedAbove = false;
        BitSet knownFalseColumn = this.knownFalse == null ? null : this.knownFalse.selectColumn(x, z);
        for (int y = maximumY; y >= minimumY; y--) {
            this.pos.set(x, y, z);
            this.cursor.beginPosition(y);
            PositionResult positionResult = this.placePosition(knownFalseColumn);
            if (startsPlacementRun(placedAbove, positionResult)) {
                this.markAbove(x, y, z);
            }
            columnPlaced |= positionResult == PositionResult.PLACED;
            placedAbove = nextPlacedAboveState(placedAbove, positionResult);
        }
        return columnPlaced;
    }

    private PositionResult placePosition(BitSet knownFalseColumn) {
        int x = this.pos.getX();
        int y = this.pos.getY();
        int z = this.pos.getZ();
        if (this.knownFalse != null && this.knownFalse.contains(knownFalseColumn, y)) {
            return PositionResult.NOT_REPLACED;
        }
        if (!DiskBlockPredicateEvaluator.test(this.config.target(), this.cursor, this.pos)) {
            if (this.knownFalse != null) {
                this.knownFalse.add(knownFalseColumn, y);
            }
            return PositionResult.NOT_REPLACED;
        }

        BlockState state = this.stateProvider.byepregen$getState(this.random, this.pos, this.cursor);
        if (state == null) {
            return PositionResult.PRESERVED;
        }
        this.level.setBlock(this.pos, state, UPDATE_CLIENTS);
        if (this.knownFalse != null) {
            this.knownFalse.invalidate(x, y, z);
        }
        return PositionResult.PLACED;
    }

    static boolean startsPlacementRun(boolean placedAbove, PositionResult result) {
        return result == PositionResult.PLACED && !placedAbove;
    }

    static boolean nextPlacedAboveState(boolean placedAbove, PositionResult result) {
        return switch (result) {
            case PLACED -> true;
            case PRESERVED -> placedAbove;
            case NOT_REPLACED -> false;
        };
    }

    private void markAbove(int x, int y, int z) {
        if (this.markFirstAbove(x, y, z)) {
            this.markSecondAbove(x, y, z);
        }
    }

    private boolean markFirstAbove(int x, int y, int z) {
        int aboveY = y + 1;
        this.pos.set(x, aboveY, z);
        if (this.cursor.getState(x, aboveY, z).isAir()) {
            return false;
        }
        this.cursor.markForPostprocessing(this.pos);
        return true;
    }

    private void markSecondAbove(int x, int y, int z) {
        int aboveY = y + 2;
        this.pos.set(x, aboveY, z);
        if (!this.cursor.getState(x, aboveY, z).isAir()) {
            this.cursor.markForPostprocessing(this.pos);
        }
    }

    @FunctionalInterface
    public interface ColumnFallback {
        boolean place(FastDiskFeature.ColumnContext context);
    }

}
