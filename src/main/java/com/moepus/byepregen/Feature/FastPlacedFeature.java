package com.moepus.byepregen.Feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public interface FastPlacedFeature {
    boolean byepregen$placeWithContext(FastPlacementContext context, RandomSource random, BlockPos pos);
}
