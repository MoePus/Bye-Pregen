package com.moepus.byepregen.test;

import com.moepus.byepregen.lighttest.DynamicLightTestBlock;
import com.moepus.byepregen.lighttest.DynamicLightTestView;
import com.moepus.byepregen.yalight.access.YAImmediateChunkAccess;
import com.moepus.byepregen.yalight.engine.YABlockLightEngine;
import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import com.moepus.byepregen.yalight.engine.YALightEngine;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

/** Dynamic light must survive source scans, checks and a neighbor's decrease without changing state. */
final class YADynamicLightProbe {
    private static final int FIRST_LIGHT = 14;
    private static final int SECOND_LIGHT = 7;
    private static final int SOURCE_Z = -8;
    private static final int FIRST_X = -1;
    private static final int SECOND_X = 4;
    private static final int SAMPLE_RADIUS = 2;

    private YADynamicLightProbe() {
    }

    static void verify(ServerLevel level) {
        BlockState state = DynamicLightTestBlock.state();
        require(state.getLightEmission() == 0 && state.hasDynamicLightEmission(), "invalid emission fixture");
        require(!state.getBlock().hasDynamicShape() && !state.emissiveRendering(), "fixture has another slow reason");
        require(Block.getId(state) >= 0 && Block.stateById(Block.getId(state)) == state, "fixture is not registered");
        require(state.getLightDampening() == 0 && !state.canOcclude(), "fixture is not transparent");
        require(YABlockStateLightClass.fromRawId(Block.getId(state)) == YABlockStateLightClass.SLOW,
                "dynamic emitter was classified as non-emissive");
        for (int y : new int[]{level.getMinY() + 1, -17, -16, level.getMaxY() - 1}) {
            verifyCase(new PaletteFixture(level), y, state);
            verifyCase(new Fixture(level, true), y, state);
        }
        System.out.println("YA dynamic light server probe passed: palette/map, classification, source scan, "
                + "sourceLight, checks, neighbor decrease re-emission, unchanged state 14 -> 7 -> 0");
    }

    private static void verifyCase(Fixture fixture, int y, BlockState state) {
        BlockPos first = new BlockPos(FIRST_X, y, SOURCE_Z);
        BlockPos second = new BlockPos(SECOND_X, y, SOURCE_Z);
        fixture.put(first, state, FIRST_LIGHT);
        fixture.put(second, state, SECOND_LIGHT);
        YABlockLightEngine block = new YABlockLightEngine(fixture);
        YALightEngine engine = new YALightEngine(fixture, block, null);
        engine.updateSectionStatus(SectionPos.of(first), false);
        engine.updateSectionStatus(SectionPos.of(second), false);
        require(block.sourceLight(first.asLong(), block.blockAccess().blockAt(FIRST_X, y, SOURCE_Z)) == FIRST_LIGHT,
                "sourceLight ignored world data");
        require(block.sourceLight(second.asLong(), block.blockAccess().blockAt(SECOND_X, y, SOURCE_Z)) == SECOND_LIGHT,
                "sourceLight ignored position");
        for (ChunkAccess chunk : fixture.chunks) {
            engine.propagateLightSources(chunk.getPos());
        }
        engine.runLightUpdates();
        assertField(fixture, block, first, second, state);
        for (int emission : new int[]{SECOND_LIGHT, 0, FIRST_LIGHT, 0}) {
            fixture.view.setEmission(first, emission);
            engine.checkBlock(first);
            engine.runLightUpdates();
            assertField(fixture, block, first, second, state);
        }
        fixture.view.setEmission(second, 0);
        engine.checkBlock(second);
        engine.runLightUpdates();
        assertField(fixture, block, first, second, state);
        require(!engine.hasLightWork(), "light work did not drain");
    }

    private static void assertField(Fixture fixture, YABlockLightEngine block,
                                    BlockPos first, BlockPos second, BlockState state) {
        require(fixture.view.getBlockState(first) == state && fixture.view.getBlockState(second) == state,
                "world data update replaced BlockState");
        require(fixture.chunks[0].getBlockState(first) == state && fixture.chunks[1].getBlockState(second) == state,
                "chunk storage replaced BlockState");
        for (int y = first.getY() - SAMPLE_RADIUS; y <= first.getY() + SAMPLE_RADIUS; ++y) {
            if (y >= fixture.view.getMinY() && y <= fixture.view.getMaxY()) {
                assertSlice(fixture, block, first, second, y);
            }
        }
    }

    private static void assertSlice(Fixture fixture, YABlockLightEngine block,
                                    BlockPos first, BlockPos second, int y) {
        for (int z = SOURCE_Z - SAMPLE_RADIUS; z <= SOURCE_Z + SAMPLE_RADIUS; ++z) {
            for (int x = FIRST_X - SAMPLE_RADIUS; x <= SECOND_X + SAMPLE_RADIUS; ++x) {
                BlockPos pos = new BlockPos(x, y, z);
                int expected = Math.max(0, Math.max(fixture.view.emissionAt(first) - first.distManhattan(pos),
                        fixture.view.emissionAt(second) - second.distManhattan(pos)));
                require(block.getLightValue(pos) == expected, "at " + pos + " expected=" + expected
                        + " actual=" + block.getLightValue(pos));
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("YA dynamic server light: " + message);
        }
    }

    private static class Fixture implements LightChunkGetter {
        final DynamicLightTestView view;
        final ChunkAccess[] chunks = new ChunkAccess[2];
        private final boolean virtual;

        Fixture(ServerLevel level, boolean virtual) {
            this.view = new DynamicLightTestView(level);
            this.virtual = virtual;
            for (int i = 0; i < this.chunks.length; ++i) {
                this.chunks[i] = new DynamicChunk(level, new ChunkPos(i - 1, -1), this.view, virtual);
            }
        }

        void put(BlockPos pos, BlockState state, int light) {
            this.view.put(pos, state, light);
            if (!this.virtual) {
                ChunkAccess chunk = this.chunks[(pos.getX() >> 4) + 1];
                chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(
                        pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, false);
            }
        }

        @Override public BlockGetter getLevel() { return this.view; }
        @Override public LightChunk getChunkForLighting(int x, int z) {
            return z == -1 && x >= -1 && x <= 0 ? this.chunks[x + 1] : null;
        }
        @Override public void onLightUpdate(LightLayer layer, SectionPos pos) {
            // Detached fixture: do not notify or mutate the live world.
        }
    }

    private static final class PaletteFixture extends Fixture implements YAImmediateChunkAccess {
        PaletteFixture(ServerLevel level) { super(level, false); }
        @Override public ChunkAccess byepregen$getAnyChunkNow(int x, int z) {
            return (ChunkAccess)this.getChunkForLighting(x, z);
        }
    }

    private static final class DynamicChunk extends LevelChunk implements DynamicLightTestBlock.Source {
        private final DynamicLightTestView view;
        private final boolean virtual;

        DynamicChunk(ServerLevel level, ChunkPos pos, DynamicLightTestView view, boolean virtual) {
            super(level, pos);
            this.view = view;
            this.virtual = virtual;
        }

        @Override public int emissionAt(BlockPos pos) { return this.view.emissionAt(pos); }
        @Override public BlockState getBlockState(BlockPos pos) {
            return this.virtual ? this.view.getBlockState(pos) : super.getBlockState(pos);
        }
        @Override public void findBlocks(Predicate<BlockState> roughFilter, BiPredicate<BlockState, BlockPos> filter,
                                         BiConsumer<BlockPos, BlockState> output) {
            if (!this.virtual) {
                super.findBlocks(roughFilter, filter, output);
                return;
            }
            this.view.forEachBlock((pos, state) -> {
                if (ChunkPos.containing(pos).equals(this.getPos()) && roughFilter.test(state) && filter.test(state, pos)) {
                    output.accept(pos, state);
                }
            });
        }
    }
}
