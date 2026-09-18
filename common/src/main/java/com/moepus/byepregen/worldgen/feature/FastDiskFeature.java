package com.moepus.byepregen.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.DiskFeature;

public interface FastDiskFeature {
    boolean byepregen$placeColumn(ColumnContext context);

    record ColumnContext(
            DiskFeature config,
            WorldGenLevel level,
            RandomSource random,
            int maximumY,
            int minimumY,
            BlockPos.MutableBlockPos pos
    ) {
    }
}
