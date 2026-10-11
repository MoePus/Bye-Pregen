package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.engine.YABlockLightEngine;
import com.moepus.byepregen.yalight.engine.YALightEngine;
import com.moepus.byepregen.yalight.sky.YASkyLightEngine;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

import java.util.HashSet;
import java.util.Set;

/** Real YA propagation with a client render sink; no graphical client or live-world edits. */
final class YALightRenderProbe {
    private static final int TORCH_LIGHT = 14;
    private static final int FLOOR_Y = 31;
    private static final int CEILING_Y = 48;
    private static final int MIN_X = 4;
    private static final int MAX_X = 27;
    private static final int MIN_Z = 7;
    private static final int MAX_Z = 9;
    private static final int CYCLES = 3;

    private YALightRenderProbe() {
    }

    static void verify(ServerLevel level) {
        verifyTunnel(level, FLOOR_Y, new BlockPos(8, 33, MIN_Z), true);
        verifyTunnel(level, CEILING_Y - 4, new BlockPos(15, 46, MIN_Z), true);
        verifyTunnel(level, FLOOR_Y, new BlockPos(8, 33, MIN_Z), false);
        verifyPacketPublication(level);
        System.out.println("YA render dependency probe passed: floor/ceiling, chunk edge, server control, packets");
    }

    private static void verifyTunnel(ServerLevel level, int floor, BlockPos source, boolean client) {
        Fixture fixture = client ? new ClientFixture(level, floor) : new Fixture(level, floor);
        YABlockLightEngine block = new YABlockLightEngine(fixture);
        YALightEngine engine = new YALightEngine(fixture, block, null, client);
        for (LevelChunk chunk : fixture.chunks) {
            block.setLightEnabled(chunk, true);
        }
        for (int cycle = 0; cycle < CYCLES; ++cycle) {
            for (boolean placed : new boolean[]{true, false}) {
                fixture.notifications.clear();
                fixture.onRender = () -> assertLightField(block, floor, source, placed);
                fixture.put(source, placed
                        ? Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH)
                        : Blocks.AIR.defaultBlockState());
                block.checkBlock(source);
                require(fixture.notifications.isEmpty(), "notified before light execution");
                engine.runLightUpdates();
                assertLightField(block, floor, source, placed);
                Set<Long> expected = expectedSections(floor, source, client);
                require(expected.equals(fixture.notifications), "mesh dependency mismatch client=" + client
                        + " floor=" + floor + " expected=" + expected + " actual=" + fixture.notifications);
            }
        }
    }

    private static void assertLightField(YABlockLightEngine block, int floor, BlockPos source, boolean placed) {
        for (int y = floor + 1; y <= floor + 3; ++y) {
            for (int z = MIN_Z; z <= MAX_Z; ++z) {
                for (int x = MIN_X; x <= MAX_X; ++x) {
                    BlockPos pos = new BlockPos(x, y, z);
                    int expected = placed ? Math.max(0, TORCH_LIGHT - source.distManhattan(pos)) : 0;
                    require(block.getLightValue(pos) == expected, "light not published at " + pos + " expected=" + expected);
                }
            }
        }
    }

    private static Set<Long> expectedSections(int floor, BlockPos source, boolean client) {
        Set<Long> result = new HashSet<>();
        for (int y = floor + 1; y <= floor + 3; ++y) {
            for (int z = MIN_Z; z <= MAX_Z; ++z) {
                for (int x = MIN_X; x <= MAX_X; ++x) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (source.distManhattan(pos) < TORCH_LIGHT) {
                        addSamplingHalo(result, pos, client ? 1 : 0);
                    }
                }
            }
        }
        return result;
    }

    private static void addSamplingHalo(Set<Long> result, BlockPos pos, int radius) {
        for (int dy = -radius; dy <= radius; ++dy) {
            for (int dz = -radius; dz <= radius; ++dz) {
                for (int dx = -radius; dx <= radius; ++dx) {
                    result.add(SectionPos.of(pos.offset(dx, dy, dz)).asLong());
                }
            }
        }
    }

    private static void verifyPacketPublication(ServerLevel level) {
        ClientFixture fixture = new ClientFixture(level, FLOOR_Y);
        YABlockLightEngine block = new YABlockLightEngine(fixture);
        YASkyLightEngine sky = new YASkyLightEngine(fixture);
        YALightEngine engine = new YALightEngine(fixture, block, sky, true);
        for (LevelChunk chunk : fixture.chunks) {
            block.setLightEnabled(chunk, true);
        }
        SectionPos section = SectionPos.of(0, 2, 0);
        BlockPos sample = new BlockPos(8, 32, 8);
        BlockPos localTorch = new BlockPos(24, 33, MIN_Z);
        fixture.onRender = () -> {
            require(block.getLightValue(sample) == 12, "packet block data not published");
            require(sky.getLightValue(sample) == 7, "packet sky data not published");
            require(block.getLightValue(localTorch) == TORCH_LIGHT, "local work not published with packet");
        };
        engine.queueOwnedSectionBytes(LightLayer.BLOCK, section, bytesAt(sample, 12));
        engine.queueOwnedSectionBytes(LightLayer.SKY, section, bytesAt(sample, 7));
        fixture.put(localTorch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        block.checkBlock(localTorch);
        require(fixture.notifications.isEmpty(), "packet invalidated meshes before publication");
        engine.runLightUpdates();
        require(fixture.notifications.size() == 27, "packet halos not merged across layers");
        fixture.notifications.clear();
        fixture.onRender = () -> require(block.getLightValue(sample) == 0, "empty packet not published");
        engine.queueZeroSectionData(LightLayer.BLOCK, section);
        engine.runLightUpdates();
        require(fixture.notifications.size() == 27, "empty packet missed neighbors");
        fixture.notifications.clear();
        engine.runLightUpdates();
        require(fixture.notifications.isEmpty(), "idle pass repeated notifications");
    }

    private static byte[] bytesAt(BlockPos pos, int value) {
        byte[] bytes = new byte[YANibbleArray.SIZE];
        int index = YANibbleArray.index(pos.getX(), pos.getY(), pos.getZ());
        bytes[index >>> 1] = (byte)(value << ((index & 1) * 4));
        return bytes;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("YA render probe: " + message);
        }
    }

    private static class Fixture implements LightChunkGetter {
        private final ServerLevel level;
        final LevelChunk[] chunks;
        final Set<Long> notifications = new HashSet<>();
        Runnable onRender = () -> {};

        Fixture(ServerLevel level, int floor) {
            this.level = level;
            this.chunks = new LevelChunk[]{new LevelChunk(level, new ChunkPos(0, 0)),
                    new LevelChunk(level, new ChunkPos(1, 0))};
            for (int y = floor; y <= floor + 4; ++y) {
                this.fillSlice(y, floor);
            }
        }

        private void fillSlice(int y, int floor) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 32; ++x) {
                    boolean air = y > floor && y < floor + 4 && x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z;
                    this.put(new BlockPos(x, y, z), air ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
            }
        }

        void put(BlockPos pos, BlockState state) {
            LevelChunk chunk = this.chunks[pos.getX() >> 4];
            chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(
                    pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, false);
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
            this.notifications.add(pos.asLong());
        }
    }

    private static final class ClientFixture extends Fixture {
        private ClientFixture(ServerLevel level, int floor) {
            super(level, floor);
        }

        @Override
        public void onLightUpdate(LightLayer layer, SectionPos pos) {
            this.onRender.run();
            require(this.notifications.add(pos.asLong()), "duplicate mesh notification");
        }
    }
}
