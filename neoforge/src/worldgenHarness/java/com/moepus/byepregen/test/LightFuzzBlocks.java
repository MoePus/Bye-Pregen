package com.moepus.byepregen.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class LightFuzzBlocks {
    private static final int MIN_BLOCK = -48;
    private static final int MAX_BLOCK = 63;

    private LightFuzzBlocks() {
    }

    static void put(ServerLevel level, BlockPos pos, BlockState state) {
        // Client notification queues light checks without applying fixture-breaking neighbor physics.
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    static boolean inBuildHeight(ServerLevel level, int y) {
        return y >= level.getMinY() && y <= level.getMaxY();
    }

    static boolean inFuzzBlockArea(int x, int z) {
        return x >= MIN_BLOCK && x <= MAX_BLOCK && z >= MIN_BLOCK && z <= MAX_BLOCK;
    }
}
