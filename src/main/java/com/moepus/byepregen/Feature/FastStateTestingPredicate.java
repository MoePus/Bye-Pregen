package com.moepus.byepregen.Feature;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

public interface FastStateTestingPredicate {
    Vec3i bpg$getOffset();

    boolean bpg$testState(BlockState state);
}
