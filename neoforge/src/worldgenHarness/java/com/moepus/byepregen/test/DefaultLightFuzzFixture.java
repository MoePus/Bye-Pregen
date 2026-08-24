package com.moepus.byepregen.test;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

final class DefaultLightFuzzFixture implements LightFuzzFixture {
    private final ServerLevel level;
    private final long seed;

    DefaultLightFuzzFixture(ServerLevel level, long seed) {
        this.level = level;
        this.seed = seed;
    }

    @Override
    public void clearVolume() {
        for (int y = 72; y <= 104; ++y) {
            for (int z = -18; z <= 18; ++z) {
                for (int x = -18; x <= 18; ++x) {
                    this.put(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    @Override
    public void buildFixture() {
        for (int z = -10; z <= 10; ++z) {
            this.put(new BlockPos(0, 84, z), Blocks.STONE.defaultBlockState());
            this.put(new BlockPos(0, 85, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = -12; x <= 12; x += 2) {
            this.put(new BlockPos(x, 82, -8),
                    Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            this.put(new BlockPos(x, 83, 8),
                    Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        }

        this.put(new BlockPos(-13, 86, 0), Blocks.SEA_LANTERN.defaultBlockState());
        this.put(new BlockPos(-2, 88, -11), Blocks.GLOWSTONE.defaultBlockState());
        this.put(new BlockPos(12, 87, 4),
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, Boolean.TRUE));
        this.put(new BlockPos(4, 90, 12),
                Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVines.BERRIES, Boolean.TRUE));
        this.placeRandomBlocks();
    }

    private void placeRandomBlocks() {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
        BlockState[] palette = {
                Blocks.GLASS.defaultBlockState(),
                Blocks.TINTED_GLASS.defaultBlockState(),
                leaves,
                Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)
        };
        Random random = new Random(this.seed);
        for (int i = 0; i < 160; ++i) {
            int x = random.nextInt(31) - 15;
            int y = 78 + random.nextInt(20);
            int z = random.nextInt(31) - 15;
            this.put(new BlockPos(x, y, z), palette[random.nextInt(palette.length)]);
        }
    }

    @Override
    public void applyUpdate(int round) {
        this.put(new BlockPos(-13, 86, 0), Blocks.AIR.defaultBlockState());
        this.put(new BlockPos(-12, 86, 1), Blocks.SEA_LANTERN.defaultBlockState());
        this.put(new BlockPos(12, 87, 4),
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, Boolean.FALSE));
        for (int z = -2; z <= 2; ++z) {
            this.put(new BlockPos(0, 84, z), Blocks.AIR.defaultBlockState());
            this.put(new BlockPos(0, 85, z), Blocks.AIR.defaultBlockState());
        }
        this.put(new BlockPos(12, 87, 4),
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, Boolean.TRUE));
        this.put(new BlockPos(7, 86, -7), Blocks.SHROOMLIGHT.defaultBlockState());
    }

    private void put(BlockPos pos, BlockState state) {
        LightFuzzBlocks.put(this.level, pos, state);
    }
}
