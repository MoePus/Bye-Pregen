package com.moepus.byepregen.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface FastRuleBasedBlockStateProvider {
    BlockState byepregen$getState(RandomSource random, BlockPos pos, FastDiskStateCursor cursor);
}
