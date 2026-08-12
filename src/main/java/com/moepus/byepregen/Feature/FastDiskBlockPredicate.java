package com.moepus.byepregen.Feature;

import net.minecraft.core.BlockPos;

public interface FastDiskBlockPredicate {
    boolean bpg$test(FastDiskStateCursor cursor, BlockPos pos);
}
