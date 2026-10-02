package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.engine.YABlockLightEngine;
import com.moepus.byepregen.yalight.engine.YALightEngine;
import com.moepus.byepregen.yalight.engine.YALightLayerEngine;
import com.moepus.byepregen.yalight.engine.YALightMath;
import com.moepus.byepregen.yalight.sky.YASkyLightEngine;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

/** Deterministically orders a neighbor's removal before an opaque block's own check. */
final class YAOpaqueLightDecreaseProbe {
    private static final int OLD_LIGHT = 15;
    private static final int INTERIOR_X = 8;
    private static final int CHUNK_EDGE_X = 15;
    private static final int TEST_Z = 8;
    private static final int NORMAL_Y = 80;

    private YAOpaqueLightDecreaseProbe() {
    }

    static void verify(ServerLevel level) {
        int[] heights = {level.getMinBuildHeight(), level.getMinBuildHeight() + 1, NORMAL_Y};
        int cases = 0;
        for (LightLayer layer : LightLayer.values()) {
            for (int y : heights) {
                verifyCase(level, layer, new BlockPos(INTERIOR_X, y, TEST_Z));
                verifyCase(level, layer, new BlockPos(CHUNK_EDGE_X, y, TEST_Z));
                cases += 2;
            }
        }
        System.out.println("YA opaque-block decrease probe passed: " + cases + " cases");
    }

    private static void verifyCase(ServerLevel level, LightLayer kind, BlockPos blocker) {
        Fixture fixture = new Fixture(level, blocker);
        YABlockLightEngine block = kind == LightLayer.BLOCK ? new YABlockLightEngine(fixture) : null;
        YASkyLightEngine sky = kind == LightLayer.SKY ? new YASkyLightEngine(fixture) : null;
        YALightLayerEngine layer = block == null ? sky : block;
        YALightEngine engine = new YALightEngine(fixture, block, sky);
        for (LevelChunk chunk : fixture.chunks) {
            layer.setLightEnabled(chunk, true);
        }
        // Seed the last published light before the blocker became opaque. The fixture is
        // detached from the live world, so no background task can change this ordering.
        for (int distance = 0; distance <= 2; ++distance) {
            fixture.seed(layer, blocker.east(distance), OLD_LIGHT - distance);
        }
        engine.runLightUpdates();
        fixture.setBlock(blocker, Blocks.STONE.defaultBlockState());
        layer.propagateDecrease(blocker.west().asLong(), YALightMath.meta(1, YALightMath.only(Direction.EAST), 0));
        require(fixture.light(layer, blocker) == 0, kind, blocker, "opaque cell was not cleared");
        require(!layer.decreaseQueue().isEmpty(), kind, blocker, "old removal seed was lost");
        require(layer.decreaseQueue().first() == blocker.asLong()
                        && YALightMath.level(layer.decreaseQueue().second()) == OLD_LIGHT,
                kind, blocker, "removal seed did not preserve the old level");
        require(YALightMath.directions(layer.decreaseQueue().second()) == YALightMath.withoutOpposite(Direction.EAST.ordinal()),
                kind, blocker, "removal directions changed");
        // Its own delayed check sees zero and can only enqueue a weak level-1 removal.
        layer.checkBlockInternal(blocker.asLong());
        engine.runLightUpdates();
        for (int distance = 0; distance <= 2; ++distance) {
            BlockPos pos = blocker.east(distance);
            require(layer.getLightValue(pos) == 0, kind, blocker, "stale published light at " + pos);
        }
        require(!layer.hasLightWork(), kind, blocker, "light work did not drain");
    }

    private static void require(boolean condition, LightLayer layer, BlockPos pos, String message) {
        if (!condition) {
            throw new IllegalStateException(layer + " opaque-decrease fixture at " + pos + ": " + message);
        }
    }

    private static final class Fixture implements LightChunkGetter {
        private final ServerLevel level;
        private final LevelChunk[] chunks;

        private Fixture(ServerLevel level, BlockPos blocker) {
            this.level = level;
            this.chunks = new LevelChunk[]{new LevelChunk(level, new ChunkPos(0, 0)), new LevelChunk(level, new ChunkPos(1, 0))};
            for (int y = Math.max(level.getMinBuildHeight(), blocker.getY() - 1); y <= blocker.getY() + 1; ++y) {
                this.fillSlice(y);
            }
            for (int offset = -1; offset <= 2; ++offset) {
                this.setBlock(blocker.east(offset), Blocks.AIR.defaultBlockState());
            }
            for (LevelChunk chunk : this.chunks) {
                chunk.initializeLightSources();
            }
        }

        private void fillSlice(int y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 32; ++x) {
                    this.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                }
            }
        }

        private void setBlock(BlockPos pos, BlockState state) {
            LevelChunk chunk = this.chunks[pos.getX() >> 4];
            // Write sections directly: these detached chunks must not enqueue live-world checks.
            chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(
                    pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, false);
        }

        private void seed(YALightLayerEngine layer, BlockPos pos, int value) {
            YAChunkLightData data = layer.storage().data(this.chunks[pos.getX() >> 4]);
            YANibbleArray nibble = data.getOrCreateUpdatingSection(pos.getY() >> 4);
            nibble.setUpdating(pos.getX(), pos.getY(), pos.getZ(), value);
            layer.storage().markDirtySection(data, pos.getY() >> 4);
        }

        private int light(YALightLayerEngine layer, BlockPos pos) {
            return layer.storage().data(this.chunks[pos.getX() >> 4])
                    .getOrCreateUpdatingSection(pos.getY() >> 4).getUpdating(pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public BlockGetter getLevel() {
            return this.level;
        }

        @Override
        public LightChunk getChunkForLighting(int x, int z) {
            return z == 0 && x >= 0 && x < this.chunks.length ? this.chunks[x] : null;
        }

        @Override
        public void onLightUpdate(LightLayer layer, SectionPos pos) {
            // Isolated fixture: do not send packets or dirty real chunks.
        }
    }
}
