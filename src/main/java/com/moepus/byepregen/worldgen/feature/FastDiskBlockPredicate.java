package com.moepus.byepregen.worldgen.feature;

import net.minecraft.core.BlockPos;

public interface FastDiskBlockPredicate {
    boolean byepregen$test(FastDiskStateCursor cursor, BlockPos pos);
}
