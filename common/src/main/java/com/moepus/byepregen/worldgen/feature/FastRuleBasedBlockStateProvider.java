package com.moepus.byepregen.worldgen.feature;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface FastRuleBasedBlockStateProvider {
    @Nullable
    BlockState byepregen$getState(RandomSource random, BlockPos pos, FastDiskStateCursor cursor);
}
