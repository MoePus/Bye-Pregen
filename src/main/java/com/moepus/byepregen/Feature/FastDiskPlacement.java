package com.moepus.byepregen.Feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;

public final class FastDiskPlacement {
    private final DiskConfiguration config;
    private final WorldGenLevel level;
    private final RandomSource random;
    private final FastDiskStateCursor cursor;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final int maxY;
    private final int minY;

    public FastDiskPlacement(FeaturePlaceContext<DiskConfiguration> context, FastDiskStateCursor cursor) {
        this.config = context.config();
        this.level = context.level();
        this.random = context.random();
        this.cursor = cursor;
        int originY = context.origin().getY();
        this.maxY = originY + this.config.halfHeight();
        this.minY = originY - this.config.halfHeight();
    }

    public DiskConfiguration config() {
        return this.config;
    }

    public WorldGenLevel level() {
        return this.level;
    }

    public RandomSource random() {
        return this.random;
    }

    public FastDiskStateCursor cursor() {
        return this.cursor;
    }

    public BlockPos.MutableBlockPos pos() {
        return this.pos;
    }

    public int maxY() {
        return this.maxY;
    }

    public int minY() {
        return this.minY;
    }
}
