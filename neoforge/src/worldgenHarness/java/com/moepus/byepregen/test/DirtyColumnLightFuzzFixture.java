package com.moepus.byepregen.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class DirtyColumnLightFuzzFixture implements LightFuzzFixture {
    private static final int MIN = -8;
    private static final int MAX = 7;
    private static final int HEIGHT_MARGIN = 96;
    private static final int ROUNDS = 64;

    private final ServerLevel level;

    DirtyColumnLightFuzzFixture(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void clearVolume() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int z = MIN; z <= MAX; ++z) {
            for (int x = MIN; x <= MAX; ++x) {
                for (int y = this.level.getMinY(); y <= this.level.getMaxY(); ++y) {
                    this.put(x, y, z, air);
                }
            }
        }
    }

    @Override
    public void buildFixture() {
        int lowY = this.level.getMinY() + HEIGHT_MARGIN;
        int highY = this.level.getMaxY() + 1 - HEIGHT_MARGIN;
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int z = MIN; z <= MAX; ++z) {
            for (int x = MIN; x <= MAX; ++x) {
                this.put(x, lowY, z, stone);
                if (((x ^ z) & 1) == 0) {
                    this.put(x, highY, z, stone);
                }
            }
        }
    }

    @Override
    public void applyUpdate(int round) {
        int highY = this.level.getMaxY() + 1 - HEIGHT_MARGIN;
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int highParity = (round + 1) & 1;
        for (int z = MIN; z <= MAX; ++z) {
            for (int x = MIN; x <= MAX; ++x) {
                this.put(x, highY, z, ((x ^ z) & 1) == highParity ? stone : air);
            }
        }
    }

    @Override
    public int updateRounds() {
        return ROUNDS;
    }

    @Override
    public String updateStageName(int round) {
        return "dirty column round " + round;
    }

    private void put(int x, int y, int z, BlockState state) {
        LightFuzzBlocks.put(this.level, new BlockPos(x, y, z), state);
    }
}
