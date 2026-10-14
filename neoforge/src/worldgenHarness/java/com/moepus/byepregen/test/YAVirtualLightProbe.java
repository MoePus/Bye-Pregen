package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.access.YAImmediateChunkAccess;
import com.moepus.byepregen.yalight.engine.YABlockLightEngine;
import com.moepus.byepregen.yalight.engine.YALightEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Contraption-like map storage versus ordinary and wrapped palette-backed chunks. */
final class YAVirtualLightProbe {
    private static final int LIGHT = 14;
    private static final int MIN_X = -5;
    private static final int MAX_X = 5;
    private static final int Z = -8;

    private YAVirtualLightProbe() {
    }

    static void verify(ServerLevel level) {
        for (int y : new int[]{level.getMinY() + 1, -17, -16, 80, level.getMaxY() - 1}) {
            verifyTunnel(new Fixture(level, true, false), y);
            verifyTunnel(new PaletteFixture(level, false), y);
            verifyTunnel(new PaletteFixture(level, true), y);
        }
        System.out.println("YA virtual light probe passed: map/palette/imposter, negative coordinates, "
                + "chunk/section edges, source scan + checks, wall changes, source removal");
    }

    private static void verifyTunnel(Fixture fixture, int y) {
        YABlockLightEngine block = new YABlockLightEngine(fixture);
        YALightEngine engine = new YALightEngine(fixture, block, null);
        for (int x = MIN_X; x <= MAX_X; ++x) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dz = -1; dz <= 1; ++dz) {
                    boolean wall = x == MIN_X || x == MAX_X || dy != 0 || dz != 0;
                    put(fixture, engine, new BlockPos(x, y + dy, Z + dz),
                            wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos source = new BlockPos(-1, y, Z);
        BlockPos wall = new BlockPos(1, y, Z);
        put(fixture, engine, source, Blocks.TORCH.defaultBlockState());
        put(fixture, engine, wall, Blocks.STONE.defaultBlockState());
        for (ChunkAccess chunk : fixture.chunks) {
            engine.propagateLightSources(chunk.getPos());
        }
        engine.runLightUpdates();
        assertTunnel(block, y, true, true);
        put(fixture, engine, wall, Blocks.AIR.defaultBlockState());
        engine.runLightUpdates();
        assertTunnel(block, y, true, false);
        put(fixture, engine, wall, Blocks.STONE.defaultBlockState());
        engine.runLightUpdates();
        assertTunnel(block, y, true, true);
        put(fixture, engine, source, Blocks.AIR.defaultBlockState());
        engine.runLightUpdates();
        assertTunnel(block, y, false, true);
        require(!engine.hasLightWork(), "light work did not drain");
        require(engine.getLayerListener(LightLayer.SKY).getLightValue(source) == 0,
                "block-only virtual engine acquired sky light");
    }

    private static void put(Fixture fixture, YALightEngine engine, BlockPos pos, BlockState state) {
        fixture.put(pos, state);
        engine.updateSectionStatus(SectionPos.of(pos), false);
        engine.checkBlock(pos);
    }

    private static void assertTunnel(YABlockLightEngine block, int y, boolean lit, boolean closed) {
        for (int x = MIN_X; x <= MAX_X; ++x) {
            BlockPos pos = new BlockPos(x, y, Z);
            boolean dark = !lit || x == MIN_X || x == MAX_X || (closed && x >= 1);
            int expected = dark ? 0 : LIGHT - Math.abs(x + 1);
            require(block.getLightValue(pos) == expected, "at " + pos + " expected=" + expected
                    + " actual=" + block.getLightValue(pos) + " lit=" + lit + " closed=" + closed);
            require(block.getLightValue(pos.above()) == 0, "light passed into ceiling at " + pos);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("YA virtual light probe: " + message);
        }
    }

    private static class Fixture implements LightChunkGetter {
        private final ServerLevel level;
        final ChunkAccess[] chunks = new ChunkAccess[2];
        private final Map<BlockPos, BlockState> blocks;

        Fixture(ServerLevel level, boolean virtual, boolean wrapped) {
            this.level = level;
            this.blocks = virtual ? new HashMap<>() : null;
            for (int i = 0; i < this.chunks.length; ++i) {
                ChunkPos pos = new ChunkPos(i - 1, -1);
                LevelChunk chunk = virtual ? new MapChunk(level, pos, this.blocks) : new LevelChunk(level, pos);
                this.chunks[i] = wrapped ? new ImposterProtoChunk(chunk, false) : chunk;
            }
        }

        void put(BlockPos pos, BlockState state) {
            if (this.blocks != null) {
                this.blocks.put(pos.immutable(), state);
                return;
            }
            ChunkAccess chunk = this.chunks[(pos.getX() >> 4) + 1];
            chunk.getSections()[chunk.getSectionIndex(pos.getY())].setBlockState(
                    pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, false);
        }

        @Override
        public BlockGetter getLevel() {
            return this.level;
        }

        @Override
        public LightChunk getChunkForLighting(int x, int z) {
            return z == -1 && x >= -1 && x <= 0 ? this.chunks[x + 1] : null;
        }

        @Override
        public void onLightUpdate(LightLayer layer, SectionPos pos) {
            // Detached fixture: no live-world packets or renderer notifications.
        }
    }

    private static final class PaletteFixture extends Fixture implements YAImmediateChunkAccess {
        PaletteFixture(ServerLevel level, boolean wrapped) {
            super(level, false, wrapped);
        }

        @Override
        public ChunkAccess byepregen$getAnyChunkNow(int x, int z) {
            return (ChunkAccess)this.getChunkForLighting(x, z);
        }
    }

    private static final class MapChunk extends LevelChunk {
        private final Map<BlockPos, BlockState> blocks;

        MapChunk(ServerLevel level, ChunkPos pos, Map<BlockPos, BlockState> blocks) {
            super(level, pos);
            this.blocks = blocks;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            require(!this.isOutsideBuildHeight(pos), "queried virtual blocks outside build height");
            return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public void findBlocks(Predicate<BlockState> roughFilter, BiPredicate<BlockState, BlockPos> filter,
                               BiConsumer<BlockPos, BlockState> output) {
            this.blocks.forEach((pos, state) -> {
                if (ChunkPos.containing(pos).equals(this.getPos()) && roughFilter.test(state) && filter.test(state, pos)) {
                    output.accept(pos, state);
                }
            });
        }
    }
}
