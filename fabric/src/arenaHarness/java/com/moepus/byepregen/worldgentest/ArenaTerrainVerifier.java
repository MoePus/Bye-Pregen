package com.moepus.byepregen.worldgentest;

import com.moepus.byepregen.arenatest.ArenaMetadataVerifier;
import com.moepus.byepregen.worldgen.terrain.ArenaTerrainFiller;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

final class ArenaTerrainVerifier {
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 128;
    private static final ChunkPos POS = new ChunkPos(-3, 2);
    private static final Constructor<?> WRITER;
    private static final Method WRITE;
    private static final Method WRITE_COLUMN;
    private static final Method FINISH;
    private static final Method SUPPORTED;
    private static final List<BlockState> STATES = states();
    private static final ArenaTerrainFiller.DebugState DEBUG = (x, y, z, state) ->
            (x + y + z) % 11 == 0 ? Blocks.OAK_LEAVES.defaultBlockState() : state;

    static {
        try {
            Class<?> writer = Class.forName("com.moepus.byepregen.worldgen.terrain.ArenaTerrainWriter");
            WRITER = writer.getDeclaredConstructor(ChunkAccess.class, DensityVolume.class, BlockState.class,
                    Aquifer.class, ArenaTerrainFiller.DebugState.class);
            WRITE = writer.getDeclaredMethod("write", DensityBuffer.class);
            WRITE_COLUMN = writer.getDeclaredMethod("writeColumn", DensityBuffer.class, int.class, int.class);
            FINISH = writer.getDeclaredMethod("finish");
            SUPPORTED = ArenaTerrainFiller.class.getDeclaredMethod("freshTargets", ChunkAccess.class, DensityVolume.class);
            WRITER.setAccessible(true);
            WRITE.setAccessible(true);
            WRITE_COLUMN.setAccessible(true);
            FINISH.setAccessible(true);
            SUPPORTED.setAccessible(true);
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }

    static int verify(ServerLevel level) throws Exception {
        int cases = 0;
        for (int mode = 0; mode < 7; mode++) {
            for (DensityVolume volume : new DensityVolume[]{volume(MIN_Y, HEIGHT), volume(-61, 87)}) {
                verifyFill(level, mode, volume);
                cases++;
            }
        }
        verifyEligibility(level);
        return cases;
    }

    private static void verifyFill(ServerLevel level, int mode, DensityVolume volume) throws Exception {
        ProtoChunk actual = chunk(level), expected = chunk(level);
        var a = new TestAquifer(mode);
        var b = new TestAquifer(mode);
        DensityBuffer buffer = DensityBuffer.createUnpooled(volume.size());
        for (int i = 0; i < buffer.size(); i++) buffer.set(i, ((i * 37) % 101 - 50) * 0.01f);
        ArenaTerrainFiller.DebugState debug = mode == 6 ? DEBUG : (x, y, z, state) -> state;
        Object writer = WRITER.newInstance(actual, volume, Blocks.STONE.defaultBlockState(), a, debug);
        WRITE.invoke(writer, buffer);
        fillNative(expected, volume, buffer, b, debug);
        if (a.trace != b.trace) throw new AssertionError("Arena changed aquifer call order / density values");
        compare(actual, expected);
        ArenaMetadataVerifier.verify(actual);
        ProtoChunk streamed = chunk(level);
        var streamingAquifer = new TestAquifer(mode);
        fillStreamed(streamed, volume, buffer, streamingAquifer, debug);
        if (streamingAquifer.trace != b.trace) throw new AssertionError("Streaming changed aquifer call order / density values");
        compare(streamed, expected);
        ArenaMetadataVerifier.verify(streamed);
    }

    private static void fillStreamed(ProtoChunk chunk, DensityVolume volume, DensityBuffer density,
                                     TestAquifer aquifer, ArenaTerrainFiller.DebugState debug) throws Exception {
        Object writer = WRITER.newInstance(chunk, volume, Blocks.STONE.defaultBlockState(), aquifer, debug);
        DensityBuffer column = DensityBuffer.createUnpooled(volume.sizeY());
        for (int z = 0; z < volume.sizeZ(); ++z) {
            for (int x = 0; x < volume.sizeX(); ++x) {
                for (int y = 0; y < volume.sizeY(); ++y) {
                    column.set(y, density.get(volume.indexUnchecked(x, y, z)));
                }
                WRITE_COLUMN.invoke(writer, column, x, z);
            }
        }
        FINISH.invoke(writer);
    }

    private static void fillNative(ProtoChunk chunk, DensityVolume volume, DensityBuffer density,
                                   TestAquifer aquifer, ArenaTerrainFiller.DebugState debug) {
        var floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        var surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int x = 0; x < volume.sizeX(); x++) fillNativeColumn(chunk, volume, density, aquifer, debug, floor, surface, x, z);
        }
    }

    private static void fillNativeColumn(ProtoChunk chunk, DensityVolume volume, DensityBuffer density,
            TestAquifer aquifer, ArenaTerrainFiller.DebugState debug, Heightmap floor, Heightmap surface, int x, int z) {
        int worldX = volume.blockX(x), worldZ = volume.blockZ(z);
        for (int y = volume.sizeY() - 1; y >= 0; y--) {
            int worldY = volume.blockY(y);
            var state = aquifer.computeSubstance(worldX, worldY, worldZ, density.get(volume.indexUnchecked(x, y, z)));
            state = debug.apply(worldX, worldY, worldZ, state == null ? Blocks.STONE.defaultBlockState() : state);
            if (state == Blocks.AIR.defaultBlockState()) continue;
            chunk.getSection(chunk.getSectionIndex(worldY)).setBlockState(x, worldY & 15, z, state, false);
            floor.update(x, worldY, z, state);
            surface.update(x, worldY, z, state);
            if (aquifer.shouldScheduleFluidUpdate() && !state.getFluidState().isEmpty()) {
                chunk.markPosForPostProcessing(new BlockPos(worldX, worldY, worldZ));
            }
        }
    }

    private static void compare(ProtoChunk actual, ProtoChunk expected) {
        for (int section = 0; section < actual.getSectionsCount(); section++) {
            var a = actual.getSection(section);
            var b = expected.getSection(section);
            if (!Arrays.equals(ArenaMetadataVerifier.counts(a), ArenaMetadataVerifier.counts(b))) {
                throw new AssertionError("Section count mismatch: " + section);
            }
            for (int index = 0; index < 4096; index++) {
                if (a.getBlockState(index & 15, index >>> 8, (index >>> 4) & 15)
                        != b.getBlockState(index & 15, index >>> 8, (index >>> 4) & 15)) {
                    throw new AssertionError("Arena voxel mismatch at " + section + '/' + index);
                }
            }
        }
        for (var type : List.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG)) {
            if (!Arrays.equals(actual.getOrCreateHeightmapUnprimed(type).getRawData(),
                    expected.getOrCreateHeightmapUnprimed(type).getRawData())) throw new AssertionError("Heightmap: " + type);
        }
        if (!Arrays.equals(actual.getPostProcessing(), expected.getPostProcessing())) {
            throw new AssertionError("Fluid postprocessing queue changed");
        }
    }

    private static void verifyEligibility(ServerLevel level) throws Exception {
        var chunk = chunk(level);
        DensityVolume partial = volume(-61, 87);
        if (!(boolean) SUPPORTED.invoke(null, chunk, partial)) throw new AssertionError("Fresh partial volume rejected");
        chunk.getSection(chunk.getSectionIndex(60)).setBlockState(0, 60 & 15, 0, Blocks.STONE.defaultBlockState());
        if ((boolean) SUPPORTED.invoke(null, chunk, partial)) throw new AssertionError("Prefilled outer section was accepted");
        var fresh = chunk(level);
        var stepped = new DensityVolume(4, 8, 4, POS.getMinBlockX(), MIN_Y, POS.getMinBlockZ(), 4, 4, 4);
        if ((boolean) SUPPORTED.invoke(null, fresh, stepped)) throw new AssertionError("Sparse volume was accepted");
    }

    private static ProtoChunk chunk(ServerLevel level) {
        return new ProtoChunk(POS, UpgradeData.EMPTY, LevelHeightAccessor.create(MIN_Y, HEIGHT),
                PalettedContainerFactory.create(level.registryAccess()), null);
    }

    private static DensityVolume volume(int minY, int height) {
        return new DensityVolume(16, height, 16, POS.getMinBlockX(), minY, POS.getMinBlockZ());
    }

    private static List<BlockState> states() {
        List<BlockState> states = new ArrayList<>();
        Blocks.CARPET.forEach(block -> states.add(block.defaultBlockState()));
        states.add(Blocks.CAVE_AIR.defaultBlockState());
        states.add(Blocks.WATER.defaultBlockState());
        states.add(Blocks.LAVA.defaultBlockState());
        states.add(Blocks.OAK_LEAVES.defaultBlockState());
        states.add(Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        return states;
    }

    private static final class TestAquifer implements Aquifer {
        private final int mode;
        private long trace;
        private boolean schedule;

        TestAquifer(int mode) { this.mode = mode; }

        public BlockState computeSubstance(int x, int y, int z, double density) {
            this.trace = this.trace * 31 + x;
            this.trace = this.trace * 31 + y;
            this.trace = this.trace * 31 + z;
            this.trace = this.trace * 31 + Double.doubleToLongBits(density);
            this.schedule = (y & 3) == 0;
            return switch (this.mode) {
                case 0 -> Blocks.AIR.defaultBlockState();
                case 1 -> null;
                case 2 -> Blocks.WATER.defaultBlockState();
                case 3 -> Blocks.LAVA.defaultBlockState();
                case 4 -> Blocks.CAVE_AIR.defaultBlockState();
                default -> density > 0 ? null : STATES.get(Math.floorMod(x + y * 7 + z * 11, STATES.size()));
            };
        }

        public boolean shouldScheduleFluidUpdate() { return this.schedule; }
    }
}
