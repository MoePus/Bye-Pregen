package com.moepus.byepregen.test;
import com.moepus.byepregen.mixin.ChunkMapAccessor;
import com.moepus.byepregen.yalight.YAChunkLightAccess;
import com.moepus.byepregen.yalight.YAChunkLightData;
import com.moepus.byepregen.yalight.YALightEngineHolder;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
final class LightBlackoutFuzz {
    static final int ROUNDS = 1024;
    static final int LOAD_RADIUS = 9;
    private static final int UPDATES_PER_ROUND = 8;
    private static final int BOUNDARY_UPDATES_PER_ROUND = 4;
    private static final int MIN_CHUNK = -8;
    private static final int MAX_CHUNK = 8;
    private static final int TEST_HEIGHT_ABOVE_TERRAIN = 16;
    private static final long UNLOAD_TIMEOUT_NANOS = 30_000_000_000L;
    private static final List<ChunkPos> ROUND_TRIP_CHUNKS = List.of(
            new ChunkPos(MIN_CHUNK, MIN_CHUNK),
            new ChunkPos(MAX_CHUNK, MIN_CHUNK),
            new ChunkPos(0, 0),
            new ChunkPos(MIN_CHUNK, MAX_CHUNK),
            new ChunkPos(MAX_CHUNK, MAX_CHUNK)
    );
    private final ServerLevel level;
    private final Random random;
    private ChunkPos lastChunk = new ChunkPos(0, 0);
    private final Set<ChunkPos> updatedThisRound = new HashSet<>();
    private final Map<ChunkPos, byte[]> expectedSky = new HashMap<>();
    private final Map<ChunkPos, LightChunkSnapshot> roundTripSnapshots = new HashMap<>();
    private final LightEdgeRepairProbe edgeRepairProbe;
    private String lastBatch = "initial fixture";
    private int roofY;
    private int updateBatch;
    private long unloadDeadline;
    private final Set<ChunkPos> forcedThisRound = new HashSet<>();
    private boolean initialAreaForced = true;

    LightBlackoutFuzz(ServerLevel level, long seed) {
        this.level = level;
        this.random = new Random(seed ^ 0xA54FF53A5F1D36F1L);
        this.edgeRepairProbe = new LightEdgeRepairProbe(level);
    }
    void clearVolume() {
        this.roofY = this.findRoofY();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int z = MIN_CHUNK << 4; z < (MAX_CHUNK + 1) << 4; ++z) {
            for (int x = MIN_CHUNK << 4; x < (MAX_CHUNK + 1) << 4; ++x) {
                this.put(new BlockPos(x, this.roofY - 1, z), air);
            }
        }
        for (int chunkZ = MIN_CHUNK; chunkZ <= MAX_CHUNK; ++chunkZ) {
            for (int chunkX = MIN_CHUNK; chunkX <= MAX_CHUNK; ++chunkX) {
                this.clearSourceArea(new ChunkPos(chunkX, chunkZ), air);
            }
        }
    }

    void buildFixture() {
        for (int chunkZ = -LOAD_RADIUS; chunkZ <= LOAD_RADIUS; ++chunkZ) {
            for (int chunkX = -LOAD_RADIUS; chunkX <= LOAD_RADIUS; ++chunkX) {
                this.buildRoof(new ChunkPos(chunkX, chunkZ));
            }
        }
        for (int chunkZ = MIN_CHUNK; chunkZ <= MAX_CHUNK; ++chunkZ) {
            for (int chunkX = MIN_CHUNK; chunkX <= MAX_CHUNK; ++chunkX) {
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                this.put(glowstone(chunk), Blocks.GLOWSTONE.defaultBlockState());
            }
        }
    }

    boolean reloadRoundTripWhenUnloaded() {
        if (this.unloadDeadline == 0L) {
            this.captureRoundTripBaseline();
            this.setInitialAreaForced(false);
            this.initialAreaForced = false;
            this.unloadDeadline = System.nanoTime() + UNLOAD_TIMEOUT_NANOS;
            return false;
        }
        if (!this.roundTripChunksUnloaded()) {
            if (System.nanoTime() > this.unloadDeadline) {
                throw new IllegalStateException("Timed out unloading round-trip chunks");
            }
            return false;
        }
        this.level.getChunkSource().save(true);
        for (ChunkPos chunk : ROUND_TRIP_CHUNKS) {
            this.loadNeighborhood(chunk);
        }
        return true;
    }

    void verifyRoundTrip() {
        boolean strictSnapshot = this.level.getChunkSource().getLightEngine() instanceof YALightEngineHolder;
        for (ChunkPos chunk : ROUND_TRIP_CHUNKS) {
            LightChunkSnapshot expected = this.roundTripSnapshots.get(chunk);
            LightChunkSnapshot actual = this.snapshot(chunk);
            if (strictSnapshot && !expected.matches(actual)) {
                throw new IllegalStateException("Light round-trip mismatch in " + chunk
                        + " expected=" + expected.summary() + " actual=" + actual.summary()
                        + " difference=" + expected.differenceSummary(actual));
            }
        }
        this.releaseLoadedChunks();
    }

    void acceptReconciledRoundTrip() {
        this.edgeRepairProbe.verifyRepaired();
        for (ChunkPos chunk : ROUND_TRIP_CHUNKS) {
            LightChunkSnapshot expected = this.roundTripSnapshots.get(chunk);
            expected.verifyNoBlockLightLoss(this.snapshot(chunk), chunk);
            this.verifyChunk(0, chunk);
        }
        this.releaseLoadedChunks();
        this.unloadDeadline = 0L;
    }

    private void captureRoundTripBaseline() {
        if (this.expectedSky.isEmpty()) {
            for (int chunkZ = MIN_CHUNK; chunkZ <= MAX_CHUNK; ++chunkZ) {
                for (int chunkX = MIN_CHUNK; chunkX <= MAX_CHUNK; ++chunkX) {
                    ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                    this.expectedSky.put(chunk, this.readSky(chunk));
                }
            }
        }
        for (ChunkPos chunk : ROUND_TRIP_CHUNKS) {
            this.roundTripSnapshots.put(chunk, this.snapshot(chunk));
        }
        this.edgeRepairProbe.injectOnce(this.roofY);
    }

    private boolean roundTripChunksUnloaded() {
        var chunkSource = this.level.getChunkSource();
        var pending = ((ChunkMapAccessor)chunkSource.chunkMap).byepregen$getPendingUnloads();
        for (ChunkPos chunk : ROUND_TRIP_CHUNKS) {
            if (chunkSource.getChunkNow(chunk.x, chunk.z) != null || pending.containsKey(chunk.toLong())) {
                return false;
            }
        }
        return true;
    }

    void applyUpdate() {
        StringJoiner batch = new StringJoiner(", ", "[", "]");
        this.applyBoundaryUpdates(batch);
        for (int i = BOUNDARY_UPDATES_PER_ROUND; i < UPDATES_PER_ROUND; ++i) {
            this.applyRandomUpdate(batch);
        }
        ++this.updateBatch;
        this.lastBatch = batch.toString();
    }

    void releaseLoadedChunks() {
        if (this.initialAreaForced) {
            this.setInitialAreaForced(false);
            this.initialAreaForced = false;
        }
        this.releaseForcedChunks();
        this.updatedThisRound.clear();
    }

    private void releaseForcedChunks() {
        for (ChunkPos chunk : this.forcedThisRound) {
            this.level.setChunkForced(chunk.x, chunk.z, false);
        }
        this.forcedThisRound.clear();
    }

    void verify(int round) {
        for (ChunkPos chunk : this.updatedThisRound) {
            this.verifyChunk(round, chunk);
        }
    }

    private void verifyChunk(int round, ChunkPos chunk) {
        BlockPos glowstone = glowstone(chunk);
        int source = this.light(LightLayer.BLOCK, glowstone);
        int tail = this.light(LightLayer.BLOCK, glowstone.east());
        byte[] sky = this.readSky(chunk);
        byte[] expected = this.expectedSky.get(chunk);
        if (expected == null) {
            throw new IllegalStateException("Missing initial sky baseline for " + chunk);
        }
        int skyMismatches = mismatchCount(expected, sky);
        int expectedLitSky = nonZeroCount(expected);
        int actualLitSky = nonZeroCount(sky);
        int directSky = sky[17] & 15;
        if (source == 15 && tail > 0 && directSky == 15 && skyMismatches == 0) {
            return;
        }
        throw new IllegalStateException("blackout fuzz round=" + round + " chunk=" + chunk
                + " updates=" + this.lastBatch
                + " glowstone=" + source + " tail=" + tail + " directSky=" + directSky
                + " litSky=" + actualLitSky + "/" + expectedLitSky + " skyMismatches=" + skyMismatches
                + " diagnostics=" + this.diagnostics(glowstone));
    }

    private byte[] readSky(ChunkPos chunk) {
        byte[] values = new byte[256];
        int index = 0;
        for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); ++z) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); ++x) {
                values[index++] = (byte)this.light(LightLayer.SKY, new BlockPos(x, this.roofY - 1, z));
            }
        }
        return values;
    }

    private int randomChunkCoordinate() {
        return MIN_CHUNK + this.random.nextInt(MAX_CHUNK - MIN_CHUNK + 1);
    }

    private void clearSourceArea(ChunkPos chunk, BlockState air) {
        BlockPos source = this.glowstone(chunk);
        for (int y = source.getY() - 1; y <= source.getY() + 1; ++y) {
            for (int z = source.getZ() - 2; z <= source.getZ() + 2; ++z) {
                for (int x = source.getX() - 2; x <= source.getX() + 2; ++x) {
                    this.put(new BlockPos(x, y, z), air);
                }
            }
        }
    }

    private void buildRoof(ChunkPos chunk) {
        BlockPos opening = new BlockPos(chunk.getMinBlockX() + 1, this.roofY, chunk.getMinBlockZ() + 1);
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); ++z) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); ++x) {
                BlockPos pos = new BlockPos(x, this.roofY, z);
                this.put(pos, pos.equals(opening) ? Blocks.AIR.defaultBlockState() : stone);
            }
        }
    }

    private static int mismatchCount(byte[] expected, byte[] actual) {
        int mismatches = 0;
        for (int i = 0; i < expected.length; ++i) {
            if (expected[i] != actual[i]) {
                ++mismatches;
            }
        }
        return mismatches;
    }

    private static int nonZeroCount(byte[] values) {
        int count = 0;
        for (byte value : values) {
            if (value != 0) {
                ++count;
            }
        }
        return count;
    }

    private BlockPos glowstone(ChunkPos chunk) {
        return new BlockPos(chunk.getMinBlockX() + 8, this.roofY - 10, chunk.getMinBlockZ() + 8);
    }

    private void applyBoundaryUpdates(StringJoiner batch) {
        int span = MAX_CHUNK - MIN_CHUNK + 1;
        int chunkX = MIN_CHUNK + Math.floorMod(this.updateBatch * 5, span - 1);
        int chunkZ = MIN_CHUNK + Math.floorMod(this.updateBatch * 7, span);
        ChunkPos west = new ChunkPos(chunkX, chunkZ);
        ChunkPos east = new ChunkPos(chunkX + 1, chunkZ);
        int sectionBoundaryY = Math.floorDiv(this.roofY - 3, 16) << 4;
        int z = west.getMinBlockZ() + 8;
        this.applyBoundaryUpdate(west, new BlockPos(west.getMaxBlockX(), sectionBoundaryY, z), 0, batch);
        this.applyBoundaryUpdate(east, new BlockPos(east.getMinBlockX(), sectionBoundaryY, z), 1, batch);
        this.applyBoundaryUpdate(west, new BlockPos(west.getMinBlockX(), sectionBoundaryY - 1, z), 2, batch);
        this.applyBoundaryUpdate(east, new BlockPos(east.getMaxBlockX(), sectionBoundaryY - 1, z), 3, batch);
    }

    private void applyBoundaryUpdate(ChunkPos chunk, BlockPos pos, int offset, StringJoiner batch) {
        this.updatedThisRound.add(chunk);
        this.loadNeighborhood(chunk);
        BlockState state = this.opticalState(this.updateBatch * BOUNDARY_UPDATES_PER_ROUND + offset);
        this.put(pos, state);
        batch.add(pos + "=" + state);
    }

    private void applyRandomUpdate(StringJoiner batch) {
        this.lastChunk = new ChunkPos(this.randomChunkCoordinate(), this.randomChunkCoordinate());
        this.updatedThisRound.add(this.lastChunk);
        this.loadNeighborhood(this.lastChunk);
        BlockPos source = this.glowstone(this.lastChunk);
        BlockPos pos;
        do {
            pos = new BlockPos(
                    this.lastChunk.getMinBlockX() + this.random.nextInt(16),
                    this.roofY - 18 + this.random.nextInt(17),
                    this.lastChunk.getMinBlockZ() + this.random.nextInt(16));
        } while (pos.closerThan(source, 2.0D));
        BlockState state = this.randomState();
        this.put(pos, state);
        batch.add(pos + "=" + state);
    }

    private void loadNeighborhood(ChunkPos center) {
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                if (this.forcedThisRound.add(chunk)) {
                    this.level.setChunkForced(chunk.x, chunk.z, true);
                    this.level.getChunk(chunk.x, chunk.z);
                }
            }
        }
    }

    private void setInitialAreaForced(boolean forced) {
        for (int chunkZ = -LOAD_RADIUS; chunkZ <= LOAD_RADIUS; ++chunkZ) {
            for (int chunkX = -LOAD_RADIUS; chunkX <= LOAD_RADIUS; ++chunkX) {
                this.level.setChunkForced(chunkX, chunkZ, forced);
            }
        }
    }

    private BlockState randomState() {
        return this.opticalState(this.random.nextInt(13));
    }

    private BlockState opticalState(int value) {
        return switch (Math.floorMod(value, 13)) {
            case 0 -> Blocks.AIR.defaultBlockState();
            case 1 -> Blocks.IRON_BLOCK.defaultBlockState();
            case 2 -> Blocks.STONE.defaultBlockState();
            case 3 -> Blocks.DEEPSLATE.defaultBlockState();
            case 4 -> Blocks.GLASS.defaultBlockState();
            case 5 -> Blocks.TINTED_GLASS.defaultBlockState();
            case 6 -> Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            case 7 -> Blocks.WATER.defaultBlockState();
            case 8 -> Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            case 9 -> Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            case 10 -> Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true);
            case 11 -> Blocks.GLOWSTONE.defaultBlockState();
            default -> Blocks.SEA_LANTERN.defaultBlockState();
        };
    }

    private int findRoofY() {
        int highest = this.level.getMinBuildHeight();
        for (int chunkZ = MIN_CHUNK; chunkZ <= MAX_CHUNK; ++chunkZ) {
            for (int chunkX = MIN_CHUNK; chunkX <= MAX_CHUNK; ++chunkX) {
                int x = (chunkX << 4) + 8;
                int z = (chunkZ << 4) + 8;
                highest = Math.max(highest, this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
            }
        }
        return Math.min(highest + TEST_HEIGHT_ABOVE_TERRAIN, this.level.getMaxBuildHeight() - 2);
    }

    private int light(LightLayer layer, BlockPos pos) {
        return this.level.getChunkSource().getLightEngine().getLayerListener(layer).getLightValue(pos);
    }

    private String diagnostics(BlockPos source) {
        var chunk = this.level.getChunkAt(source);
        if (!(chunk instanceof YAChunkLightAccess access)) {
            return "{state=" + this.level.getBlockState(source)
                    + ",lightCorrect=" + chunk.isLightCorrect() + "}";
        }
        YAChunkLightData block = access.byepregen$blockLightData();
        YAChunkLightData sky = access.byepregen$skyLightData();
        return "{state=" + this.level.getBlockState(source)
                + ",lightCorrect=" + chunk.isLightCorrect()
                + ",blockData=" + (block != null)
                + ",blockEnabled=" + (block != null && block.lightEnabled())
                + ",skyData=" + (sky != null)
                + ",skyEnabled=" + (sky != null && sky.lightEnabled()) + "}";
    }

    private LightChunkSnapshot snapshot(ChunkPos pos) {
        this.level.getChunk(pos.x, pos.z);
        return LightChunkSnapshot.capture(this.level, pos);
    }

    private void put(BlockPos pos, BlockState state) {
        this.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
