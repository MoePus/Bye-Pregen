package com.moepus.byepregen.worldgen.feature;

import java.util.BitSet;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;

public final class FastDiskPlacement {
    private static final int UPDATE_CLIENTS = 2;
    private static final int PLACED = 1;
    private static final int FIRST_ABOVE_MARKED = 2;

    private final DiskConfiguration config;
    private final WorldGenLevel level;
    private final RandomSource random;
    private final FastDiskStateCursor cursor;
    private final KnownFalseDiskPredicateCache knownFalse;
    private final ColumnFallback fallback;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public FastDiskPlacement(
            DiskConfiguration config,
            WorldGenLevel level,
            RandomSource random,
            FastDiskStateCursor cursor,
            KnownFalseDiskPredicateCache knownFalse,
            ColumnFallback fallback
    ) {
        this.config = config;
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
            this.clearMemoizedResults();
            this.pos.set(x, maximumY, z);
            return this.fallback.place(new FastDiskFeature.ColumnContext(
                    this.config, this.level, this.random, maximumY, minimumY, this.pos
            ));
        }

        boolean columnPlaced = false;
        boolean previousFirstMarked = false;
        BitSet knownFalseColumn = this.selectMemoizedColumn(x, z);
        for (int y = maximumY; y >= minimumY; y--) {
            this.pos.set(x, y, z);
            this.cursor.beginPosition(y);
            int result = this.placePosition(knownFalseColumn);
            boolean currentFirstMarked = (result & FIRST_ABOVE_MARKED) != 0;
            if (currentFirstMarked && !previousFirstMarked) {
                this.markSecondAbove(x, y, z);
            }
            columnPlaced |= (result & PLACED) != 0;
            previousFirstMarked = currentFirstMarked;
        }
        return columnPlaced;
    }

    private BitSet selectMemoizedColumn(int x, int z) {
        return this.knownFalse == null ? null : this.knownFalse.selectColumn(x, z);
    }

    private void clearMemoizedResults() {
        if (this.knownFalse != null) {
            this.knownFalse.clear();
        }
    }

    private int placePosition(BitSet knownFalseColumn) {
        int x = this.pos.getX();
        int y = this.pos.getY();
        int z = this.pos.getZ();
        if (this.knownFalse != null && this.knownFalse.contains(knownFalseColumn, y)) {
            return 0;
        }
        if (!DiskBlockPredicateEvaluator.test(this.config.target(), this.cursor, this.pos)) {
            if (this.knownFalse != null) {
                this.knownFalse.add(knownFalseColumn, y);
            }
            return 0;
        }

        BlockState state = ((FastRuleBasedBlockStateProvider) (Object) this.config.stateProvider())
                .byepregen$getState(this.random, this.pos, this.cursor);
        this.level.setBlock(this.pos, state, UPDATE_CLIENTS);
        if (this.knownFalse != null) {
            this.knownFalse.invalidate(x, y, z);
        }
        return this.markFirstAbove(x, y, z) ? PLACED | FIRST_ABOVE_MARKED : PLACED;
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
