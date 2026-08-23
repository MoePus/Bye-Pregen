package com.moepus.byepregen.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;

public interface FastDiskFeature {
    boolean byepregen$placeColumn(ColumnContext context);

    record ColumnContext(
            DiskConfiguration config,
            WorldGenLevel level,
            RandomSource random,
            int maximumY,
            int minimumY,
            BlockPos.MutableBlockPos pos
    ) {
    }
}
