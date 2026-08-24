package com.moepus.byepregen.test;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

final class EdgeLightFuzzFixture implements LightFuzzFixture {
    private static final int[] EDGE_XZ = {-17, -16, -1, 0, 15, 16, 31, 32};
    private static final int[] EDGE_Y = {
            -64, -63, -49, -48, -33, -32, -17, -16, -1, 0, 15, 16, 31, 32, 71, 72, 255, 256, 319
    };

    private final ServerLevel level;

    EdgeLightFuzzFixture(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void clearVolume() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = this.level.getMinY(); y <= this.level.getMaxY(); ++y) {
            for (int x : EDGE_XZ) {
                for (int z : EDGE_XZ) {
                    this.put(x, y, z, air);
                }
            }
        }
        for (int y : EDGE_Y) {
            for (int dy = -1; dy <= 1; ++dy) {
                this.clearHorizontalSlice(y + dy, air);
            }
        }
    }

    private void clearHorizontalSlice(int y, BlockState state) {
        if (!LightFuzzBlocks.inBuildHeight(this.level, y)) {
            return;
        }
        for (int n = -20; n <= 36; ++n) {
            for (int offset = -2; offset <= 2; ++offset) {
                this.put(15 + offset, y, n, state);
                this.put(n, y, 15 + offset, state);
            }
        }
    }

    @Override
    public void buildFixture() {
        this.buildWalls();
        this.buildSkyColumns();
        this.buildBlockSources();
        this.buildPartialOccluders();
    }

    private void buildWalls() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int y : EDGE_Y) {
            if (!LightFuzzBlocks.inBuildHeight(this.level, y)) {
                continue;
            }
            for (int offset = -1; offset <= 1; ++offset) {
                for (int n = -1; n <= 16; ++n) {
                    this.put(15 + offset, y, n, stone);
                    this.put(n, y, 15 + offset, stone);
                }
            }
        }
    }

    private void buildSkyColumns() {
        int bottom = this.level.getMinY();
        int top = this.level.getMaxY();
        BlockState stone = Blocks.STONE.defaultBlockState();
        this.put(-1, top, -1, stone);
        this.put(0, bottom, 0, stone);
        this.put(15, 16, 15, stone);
        this.put(16, 15, 16, stone);
        this.put(31, 255, 0, stone);
        this.put(-16, 72, 15, stone);
    }

    private void buildBlockSources() {
        BlockState litLamp = Blocks.REDSTONE_LAMP.defaultBlockState()
                .setValue(RedstoneLampBlock.LIT, Boolean.TRUE);
        this.put(-1, 15, 0, Blocks.SEA_LANTERN.defaultBlockState());
        this.put(0, 16, 0, Blocks.GLOWSTONE.defaultBlockState());
        this.put(15, -1, 16, Blocks.SHROOMLIGHT.defaultBlockState());
        this.put(16, 0, 15, Blocks.SEA_LANTERN.defaultBlockState());
        this.put(31, 255, 16, litLamp);
        this.put(32, 256, 15, Blocks.SEA_LANTERN.defaultBlockState());
        this.put(-16, -63, -1, Blocks.GLOWSTONE.defaultBlockState());
        this.put(-17, -64, 0, Blocks.SEA_LANTERN.defaultBlockState());
    }

    private void buildPartialOccluders() {
        BlockState topSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState bottomSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int z = -2; z <= 2; ++z) {
            this.put(15, 16, z, topSlab);
            this.put(16, 15, z, bottomSlab);
            this.put(-1, 0, z, stair);
            this.put(0, -1, z, Blocks.TINTED_GLASS.defaultBlockState());
        }
        this.put(31, 256, 16, Blocks.OAK_FENCE.defaultBlockState());
        this.put(32, 255, 15, Blocks.GLASS.defaultBlockState());
    }

    @Override
    public void applyUpdate(int round) {
        int bottom = this.level.getMinY();
        int top = this.level.getMaxY();
        this.put(-1, top, -1, Blocks.AIR.defaultBlockState());
        this.put(0, top, 0, Blocks.STONE.defaultBlockState());
        this.put(0, bottom, 0, Blocks.AIR.defaultBlockState());
        this.put(-1, bottom, -1, Blocks.STONE.defaultBlockState());
        this.mutateBlockSources();
        this.mutateWalls();
    }

    private void mutateBlockSources() {
        BlockState unlitLamp = Blocks.REDSTONE_LAMP.defaultBlockState()
                .setValue(RedstoneLampBlock.LIT, Boolean.FALSE);
        BlockState litLamp = Blocks.REDSTONE_LAMP.defaultBlockState()
                .setValue(RedstoneLampBlock.LIT, Boolean.TRUE);
        this.put(-1, 15, 0, Blocks.AIR.defaultBlockState());
        this.put(0, 15, 0, Blocks.SEA_LANTERN.defaultBlockState());
        this.put(0, 16, 0, Blocks.AIR.defaultBlockState());
        this.put(16, 16, 16, Blocks.GLOWSTONE.defaultBlockState());
        this.put(31, 255, 16, unlitLamp);
        this.put(31, 255, 16, litLamp);
        this.put(32, 256, 15, Blocks.AIR.defaultBlockState());
        this.put(31, 256, 15, Blocks.SEA_LANTERN.defaultBlockState());
    }

    private void mutateWalls() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int y : EDGE_Y) {
            if (!LightFuzzBlocks.inBuildHeight(this.level, y)) {
                continue;
            }
            for (int n = -1; n <= 16; ++n) {
                this.put(15, y, n, air);
                this.put(n, y, 15, air);
                this.put(14, y, n, stone);
                this.put(n, y, 14, stone);
            }
        }
    }

    private void put(int x, int y, int z, BlockState state) {
        LightFuzzBlocks.put(this.level, new BlockPos(x, y, z), state);
    }
}
