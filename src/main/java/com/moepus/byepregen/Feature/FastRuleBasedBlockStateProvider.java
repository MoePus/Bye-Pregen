package com.moepus.byepregen.Feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface FastRuleBasedBlockStateProvider {
    BlockState bpg$getState(RandomSource random, BlockPos pos, FastDiskStateCursor cursor);
}
