package com.moepus.byepregen.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class LightFuzzRun {
    private static final long LIGHT_FUZZ_SEED =
            ChunkyWorldGenDriver.longProperty("lightFuzzSeed", 0x59A11E71C0DEL);
    private static final String LIGHT_FUZZ_VARIANT = ChunkyWorldGenDriver.property("lightFuzzVariant", "default")
            .toLowerCase(Locale.ROOT);
    private static final boolean LIGHT_FUZZ_PROBES =
            ChunkyWorldGenDriver.booleanProperty("lightFuzzProbes", false);
    private static final int[] EDGE_XZ = {-17, -16, -1, 0, 15, 16, 31, 32};
    private static final int[] EDGE_Y = {
            -64, -63, -49, -48, -33, -32, -17, -16, -1, 0, 15, 16, 31, 32, 71, 72, 255, 256, 319
    };
    private static final int[] STRESS_XZ = {
            -47, -33, -32, -31, -17, -16, -15, -1, 0, 1, 14, 15, 16, 17, 30, 31, 32, 33, 47
    };
    private static final int[] STRESS_Y_OFFSETS = {-1, 0, 1, 14, 15, 16};
    private static final Direction[] STRESS_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
    };
    private static final Direction[] STRESS_HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int FUZZ_MIN_BLOCK = -48;
    private static final int FUZZ_MAX_BLOCK = 63;
    private static final int STRESS_CLEAR_COLUMNS = 56;
    private static final int STRESS_INITIAL_UPDATES = 520;
    private static final int STRESS_MUTATION_UPDATES = 420;
    private static final int DIRTY_COLUMN_MIN = -8;
    private static final int DIRTY_COLUMN_MAX = 7;
    private static final int DIRTY_COLUMN_HEIGHT_MARGIN = 96;
    private static final int DIRTY_COLUMN_ROUNDS = 64;

    private final MinecraftServer server;
    private final ServerLevel level;
    private List<ChunkPos> chunks = List.of();
    private CompletableFuture<Void> pendingLight;
    private String pendingStage;
    private int stage;
    private int dirtyColumnRound;
    private int blackoutRound;
    private final LightBlackoutFuzz blackoutFuzz;
    private final LightTorchLifecycleProbe torchLifecycleProbe;
    private boolean waitingForTorchLight;

    private LightFuzzRun(MinecraftServer server, ServerLevel level) {
        this.server = server;
        this.level = level;
        this.blackoutFuzz = runsBlackoutLightFuzz()
                ? new LightBlackoutFuzz(level, LIGHT_FUZZ_SEED)
                : null;
        this.torchLifecycleProbe = runsBlackoutLightFuzz()
                && ChunkyWorldGenDriver.yaLightEngineHolder(level) != null
                ? new LightTorchLifecycleProbe(level)
                : null;
    }

    static void start(MinecraftServer server) {
        if (!"minecraft:overworld".equals(ChunkyWorldGenDriver.WORLD)) {
            ChunkyWorldGenDriver.failAndStop(server,
                    "Light fuzz mode currently supports only minecraft:overworld, got "
                            + ChunkyWorldGenDriver.WORLD);
            return;
        }

        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            ChunkyWorldGenDriver.failAndStop(server, "Overworld is not loaded");
            return;
        }
        if (!isKnownLightFuzzVariant()) {
            ChunkyWorldGenDriver.failAndStop(server, "Unknown light fuzz variant: " + LIGHT_FUZZ_VARIANT);
            return;
        }

        ChunkyWorldGenDriver.LOGGER.info("Started light fuzz: world={} seed={} variant={}",
                ChunkyWorldGenDriver.WORLD, LIGHT_FUZZ_SEED, LIGHT_FUZZ_VARIANT);
        ChunkyWorldGenDriver.lightFuzzRun = new LightFuzzRun(server, level);
    }

    boolean owns(MinecraftServer server) {
        return this.server == server;
    }

    void tick() {
        if (ChunkyWorldGenDriver.isStopping()) {
            return;
        }
        try {
            if (this.pendingLight != null) {
                if (!this.pendingLight.isDone()) {
                    this.logPending();
                    return;
                }
                this.pendingLight.join();
                ChunkyWorldGenDriver.LOGGER.info("Light fuzz stage completed: {}", this.pendingStage);
                if (this.waitingForTorchLight) {
                    this.torchLifecycleProbe.lightWaitCompleted();
                    this.waitingForTorchLight = false;
                    this.pendingLight = null;
                    this.pendingStage = null;
                    return;
                }
                this.verifyBlackoutRound();
                this.logProbes();
                this.pendingLight = null;
                this.pendingStage = null;
                ++this.stage;
            }

            if (this.torchLifecycleProbe != null && !this.torchLifecycleProbe.isComplete()) {
                if (this.torchLifecycleProbe.advance()) {
                    this.waitingForTorchLight = true;
                    this.waitForTorchLight();
                }
                return;
            }

            switch (this.stage) {
                case 0 -> {
                    this.chunks = loadFuzzChunks(this.level);
                    this.waitForLight("initial chunk light");
                }
                case 1 -> {
                    clearFuzzVolume(this.level);
                    this.waitForLight("clear volume");
                }
                case 2 -> {
                    buildInitialFuzzFixture(this.level);
                    this.waitForLight("initial fixture");
                }
                case 3 -> {
                    if (runsBlackoutLightFuzz() && this.blackoutFuzz.reloadRoundTripWhenUnloaded()) {
                        this.waitForLight("round-trip reconciliation");
                    } else if (!runsBlackoutLightFuzz()) {
                        this.startMutationStage();
                    }
                }
                case 4 -> {
                    if (runsBlackoutLightFuzz() && this.blackoutFuzz.reloadRoundTripWhenUnloaded()) {
                        this.waitForLight("reconciled round-trip reload");
                    } else if (!runsBlackoutLightFuzz()) {
                        this.runNextUpdateRound();
                    }
                }
                case 5 -> {
                    if (runsBlackoutLightFuzz()) {
                        this.startMutationStage();
                    } else {
                        this.runNextUpdateRound();
                    }
                }
                case 6 -> this.runNextUpdateRound();
                default -> throw new IllegalStateException("Invalid light fuzz stage: " + this.stage);
            }
        } catch (Throwable throwable) {
            ChunkyWorldGenDriver.LOGGER.error("Light fuzz failed", throwable);
            ChunkyWorldGenDriver.lightFuzzRun = null;
            ChunkyWorldGenDriver.failAndStop(this.server, "Light fuzz failed: " + throwable.getMessage());
        }
    }

    private void waitForLight(String stageName) {
        ChunkyWorldGenDriver.LOGGER.info("Waiting for light fuzz stage: {}", stageName);
        CompletableFuture<?>[] futures = new CompletableFuture<?>[this.chunks.size()];
        for (int i = 0; i < this.chunks.size(); ++i) {
            ChunkPos chunk = this.chunks.get(i);
            futures[i] = this.level.getChunkSource().getLightEngine().waitForPendingTasks(chunk.x, chunk.z);
        }
        this.pendingStage = stageName;
        this.pendingLight = CompletableFuture.allOf(futures);
    }

    private void waitForTorchLight() {
        this.pendingStage = "torch lifecycle " + this.torchLifecycleProbe.pendingStage();
        ChunkyWorldGenDriver.LOGGER.info("Waiting for light fuzz stage: {}", this.pendingStage);
        this.pendingLight = this.torchLifecycleProbe.waitForLight();
    }

    private void startMutationStage() {
        mutateFuzzFixture(this.level);
        this.dirtyColumnRound = 1;
        this.blackoutRound = 1;
        this.waitForLight("mutated fixture");
    }

    private void runNextDirtyColumnRound() {
        if (!runsDirtyColumnLightFuzz() || this.dirtyColumnRound >= DIRTY_COLUMN_ROUNDS) {
            this.complete();
            return;
        }
        int round = this.dirtyColumnRound++;
        mutateDirtyColumnFuzzFixture(this.level, round);
        --this.stage;
        this.waitForLight("dirty column round " + round);
    }

    private void runNextUpdateRound() {
        if (!runsBlackoutLightFuzz()) {
            this.runNextDirtyColumnRound();
            return;
        }
        if (this.blackoutRound >= LightBlackoutFuzz.ROUNDS) {
            this.complete();
            return;
        }
        int round = this.blackoutRound++;
        this.blackoutFuzz.applyUpdate();
        --this.stage;
        this.waitForLight("blackout round " + round);
    }

    private void verifyBlackoutRound() {
        if (!runsBlackoutLightFuzz()) {
            return;
        }
        if (this.stage == 3) {
            this.blackoutFuzz.acceptReconciledRoundTrip();
            return;
        }
        if (this.stage == 4) {
            if (!this.blackoutFuzz.verifyRoundTrip()) {
                --this.stage;
            }
            return;
        }
        if (this.stage < 5) {
            return;
        }
        this.blackoutFuzz.verify(Math.max(0, this.blackoutRound - 1));
        this.blackoutFuzz.releaseLoadedChunks();
    }

    private void logPending() {
        long now = System.nanoTime();
        long next = ChunkyWorldGenDriver.NEXT_PROGRESS_LOG.get();
        if (now < next || !ChunkyWorldGenDriver.NEXT_PROGRESS_LOG.compareAndSet(
                next, now + ChunkyWorldGenDriver.PROGRESS_LOG_NANOS)) {
            return;
        }
        ChunkyWorldGenDriver.LOGGER.info("Still waiting for light fuzz stage: {}", this.pendingStage);
    }

    private void logProbes() {
        if (!LIGHT_FUZZ_PROBES) {
            return;
        }
        ChunkyWorldGenDriver.LOGGER.info(
                "Light fuzz probes after {}: glowstone={} glowstoneTail={} seaLantern={} "
                        + "redstoneLamp={} shroomlight={} caveVines={} center={}",
                this.pendingStage,
                this.blockLight(-2, 88, -11),
                this.blockLight(-2, 80, -17),
                this.blockLight(-12, 86, 1),
                this.blockLight(12, 87, 4),
                this.blockLight(7, 86, -7),
                this.blockLight(4, 90, 12),
                this.blockLight(0, 88, 0));
    }

    private int blockLight(int x, int y, int z) {
        return this.level.getChunkSource()
                .getLightEngine()
                .getLayerListener(LightLayer.BLOCK)
                .getLightValue(new BlockPos(x, y, z));
    }

    private void complete() {
        ChunkyWorldGenDriver.lightFuzzRun = null;
        ChunkyWorldGenDriver.LOGGER.info("Light fuzz completed: world={} seed={}",
                ChunkyWorldGenDriver.WORLD, LIGHT_FUZZ_SEED);
        ChunkyWorldGenDriver.succeedAndStop(
                this.server,
                "variant=" + LIGHT_FUZZ_VARIANT + "\nseed=" + LIGHT_FUZZ_SEED
        );
    }

    private static List<ChunkPos> loadFuzzChunks(ServerLevel level) {
        List<ChunkPos> chunks = new ArrayList<>();
        int radius = runsBlackoutLightFuzz() ? LightBlackoutFuzz.LOAD_RADIUS : 3;
        for (int chunkZ = -radius; chunkZ <= radius; ++chunkZ) {
            for (int chunkX = -radius; chunkX <= radius; ++chunkX) {
                level.setChunkForced(chunkX, chunkZ, true);
                level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return chunks;
    }

    private static void clearFuzzVolume(ServerLevel level) {
        if (runsBlackoutLightFuzz()) {
            active().blackoutFuzz.clearVolume();
        }
        if (runsDefaultLightFuzz()) {
            clearDefaultFuzzVolume(level);
        }
        if (runsEdgeLightFuzz()) {
            clearEdgeFuzzVolume(level);
        }
        if (runsStressLightFuzz()) {
            clearStressFuzzVolume(level);
        }
        if (runsDirtyColumnLightFuzz()) {
            clearDirtyColumnFuzzVolume(level);
        }
    }

    private static void clearDefaultFuzzVolume(ServerLevel level) {
        for (int y = 72; y <= 104; ++y) {
            for (int z = -18; z <= 18; ++z) {
                for (int x = -18; x <= 18; ++x) {
                    put(level, x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void buildInitialFuzzFixture(ServerLevel level) {
        if (runsBlackoutLightFuzz()) {
            active().blackoutFuzz.buildFixture();
        }
        if (runsDefaultLightFuzz()) {
            buildDefaultFuzzFixture(level);
        }
        if (runsEdgeLightFuzz()) {
            buildEdgeFuzzFixture(level);
        }
        if (runsStressLightFuzz()) {
            buildStressFuzzFixture(level);
        }
        if (runsDirtyColumnLightFuzz()) {
            buildDirtyColumnFuzzFixture(level);
        }
    }

    private static void buildDefaultFuzzFixture(ServerLevel level) {
        for (int z = -10; z <= 10; ++z) {
            put(level, 0, 84, z, Blocks.STONE.defaultBlockState());
            put(level, 0, 85, z, Blocks.STONE.defaultBlockState());
        }
        for (int x = -12; x <= 12; x += 2) {
            put(level, x, 82, -8,
                    Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            put(level, x, 83, 8,
                    Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        }

        put(level, -13, 86, 0, Blocks.SEA_LANTERN.defaultBlockState());
        put(level, -2, 88, -11, Blocks.GLOWSTONE.defaultBlockState());
        put(level, 12, 87, 4,
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, Boolean.TRUE));
        put(level, 4, 90, 12,
                Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVines.BERRIES, Boolean.TRUE));

        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
        BlockState[] palette = {
                Blocks.GLASS.defaultBlockState(),
                Blocks.TINTED_GLASS.defaultBlockState(),
                leaves,
                Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)
        };
        Random random = new Random(LIGHT_FUZZ_SEED);
        for (int i = 0; i < 160; ++i) {
            int x = random.nextInt(31) - 15;
            int y = 78 + random.nextInt(20);
            int z = random.nextInt(31) - 15;
            put(level, x, y, z, palette[random.nextInt(palette.length)]);
        }
    }

    private static void mutateFuzzFixture(ServerLevel level) {
        if (runsBlackoutLightFuzz()) {
            active().blackoutFuzz.applyUpdate();
        }
        if (runsDefaultLightFuzz()) {
            mutateDefaultFuzzFixture(level);
        }
        if (runsEdgeLightFuzz()) {
            mutateEdgeFuzzFixture(level);
        }
        if (runsStressLightFuzz()) {
            mutateStressFuzzFixture(level);
        }
        if (runsDirtyColumnLightFuzz()) {
            mutateDirtyColumnFuzzFixture(level, 0);
        }
    }

    private static void mutateDefaultFuzzFixture(ServerLevel level) {
        put(level, -13, 86, 0, Blocks.AIR.defaultBlockState());
        put(level, -12, 86, 1, Blocks.SEA_LANTERN.defaultBlockState());
        put(level, 12, 87, 4,
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, Boolean.FALSE));
        for (int z = -2; z <= 2; ++z) {
            put(level, 0, 84, z, Blocks.AIR.defaultBlockState());
            put(level, 0, 85, z, Blocks.AIR.defaultBlockState());
        }
        put(level, 12, 87, 4,
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, Boolean.TRUE));
        put(level, 7, 86, -7, Blocks.SHROOMLIGHT.defaultBlockState());
    }

    private static void clearEdgeFuzzVolume(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); ++y) {
            for (int x : EDGE_XZ) {
                for (int z : EDGE_XZ) {
                    put(level, x, y, z, air);
                }
            }
        }
        for (int y : EDGE_Y) {
            for (int dy = -1; dy <= 1; ++dy) {
                clearHorizontalEdgeSlice(level, y + dy, air);
            }
        }
    }

    private static void clearHorizontalEdgeSlice(ServerLevel level, int y, BlockState state) {
        if (!inBuildHeight(level, y)) {
            return;
        }
        for (int n = -20; n <= 36; ++n) {
            for (int offset = -2; offset <= 2; ++offset) {
                put(level, 15 + offset, y, n, state);
                put(level, n, y, 15 + offset, state);
            }
        }
    }

    private static void buildEdgeFuzzFixture(ServerLevel level) {
        buildEdgeWalls(level);
        buildEdgeSkyColumns(level);
        buildEdgeBlockSources(level);
        buildEdgePartialOccluders(level);
    }

    private static void buildEdgeWalls(ServerLevel level) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int y : EDGE_Y) {
            if (!inBuildHeight(level, y)) {
                continue;
            }
            for (int offset = -1; offset <= 1; ++offset) {
                for (int n = -1; n <= 16; ++n) {
                    put(level, 15 + offset, y, n, stone);
                    put(level, n, y, 15 + offset, stone);
                }
            }
        }
    }

    private static void buildEdgeSkyColumns(ServerLevel level) {
        int bottom = level.getMinBuildHeight();
        int top = level.getMaxBuildHeight() - 1;
        BlockState stone = Blocks.STONE.defaultBlockState();
        put(level, -1, top, -1, stone);
        put(level, 0, bottom, 0, stone);
        put(level, 15, 16, 15, stone);
        put(level, 16, 15, 16, stone);
        put(level, 31, 255, 0, stone);
        put(level, -16, 72, 15, stone);
    }

    private static void buildEdgeBlockSources(ServerLevel level) {
        BlockState litLamp = Blocks.REDSTONE_LAMP.defaultBlockState()
                .setValue(RedstoneLampBlock.LIT, Boolean.TRUE);
        put(level, -1, 15, 0, Blocks.SEA_LANTERN.defaultBlockState());
        put(level, 0, 16, 0, Blocks.GLOWSTONE.defaultBlockState());
        put(level, 15, -1, 16, Blocks.SHROOMLIGHT.defaultBlockState());
        put(level, 16, 0, 15, Blocks.SEA_LANTERN.defaultBlockState());
        put(level, 31, 255, 16, litLamp);
        put(level, 32, 256, 15, Blocks.SEA_LANTERN.defaultBlockState());
        put(level, -16, -63, -1, Blocks.GLOWSTONE.defaultBlockState());
        put(level, -17, -64, 0, Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static void buildEdgePartialOccluders(ServerLevel level) {
        BlockState topSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState bottomSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int z = -2; z <= 2; ++z) {
            put(level, 15, 16, z, topSlab);
            put(level, 16, 15, z, bottomSlab);
            put(level, -1, 0, z, stair);
            put(level, 0, -1, z, Blocks.TINTED_GLASS.defaultBlockState());
        }
        put(level, 31, 256, 16, Blocks.OAK_FENCE.defaultBlockState());
        put(level, 32, 255, 15, Blocks.GLASS.defaultBlockState());
    }

    private static void mutateEdgeFuzzFixture(ServerLevel level) {
        int bottom = level.getMinBuildHeight();
        int top = level.getMaxBuildHeight() - 1;
        put(level, -1, top, -1, Blocks.AIR.defaultBlockState());
        put(level, 0, top, 0, Blocks.STONE.defaultBlockState());
        put(level, 0, bottom, 0, Blocks.AIR.defaultBlockState());
        put(level, -1, bottom, -1, Blocks.STONE.defaultBlockState());
        mutateEdgeBlockSources(level);
        mutateEdgeWalls(level);
    }

    private static void mutateEdgeBlockSources(ServerLevel level) {
        BlockState unlitLamp = Blocks.REDSTONE_LAMP.defaultBlockState()
                .setValue(RedstoneLampBlock.LIT, Boolean.FALSE);
        BlockState litLamp = Blocks.REDSTONE_LAMP.defaultBlockState()
                .setValue(RedstoneLampBlock.LIT, Boolean.TRUE);
        put(level, -1, 15, 0, Blocks.AIR.defaultBlockState());
        put(level, 0, 15, 0, Blocks.SEA_LANTERN.defaultBlockState());
        put(level, 0, 16, 0, Blocks.AIR.defaultBlockState());
        put(level, 16, 16, 16, Blocks.GLOWSTONE.defaultBlockState());
        put(level, 31, 255, 16, unlitLamp);
        put(level, 31, 255, 16, litLamp);
        put(level, 32, 256, 15, Blocks.AIR.defaultBlockState());
        put(level, 31, 256, 15, Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static void mutateEdgeWalls(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int y : EDGE_Y) {
            if (!inBuildHeight(level, y)) {
                continue;
            }
            for (int n = -1; n <= 16; ++n) {
                put(level, 15, y, n, air);
                put(level, n, y, 15, air);
                put(level, 14, y, n, stone);
                put(level, n, y, 14, stone);
            }
        }
    }

    private static void clearStressFuzzVolume(ServerLevel level) {
        Random random = new Random(LIGHT_FUZZ_SEED ^ 0x6A09E667F3BCC909L);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < STRESS_CLEAR_COLUMNS; ++i) {
            clearStressColumn(level, stressXZ(random), stressXZ(random), air);
        }
    }

    private static void clearStressColumn(ServerLevel level, int x, int z, BlockState state) {
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); ++y) {
            put(level, x, y, z, state);
        }
    }

    private static void buildStressFuzzFixture(ServerLevel level) {
        Random random = new Random(LIGHT_FUZZ_SEED ^ 0xBB67AE8584CAA73BL);
        buildStressSkyColumns(level, random);
        applyStressUpdates(level, random, STRESS_INITIAL_UPDATES, false);
    }

    private static void buildStressSkyColumns(ServerLevel level, Random random) {
        int bottom = level.getMinBuildHeight();
        int top = level.getMaxBuildHeight() - 1;
        for (int i = 0; i < 40; ++i) {
            int x = stressXZ(random);
            int z = stressXZ(random);
            put(level, x, top, z, stressOpaque(random));
            put(level, x, bottom, z, stressOpaque(random));
            put(level, x, stressY(level, random), z, stressBlockState(random));
        }
    }

    private static void applyStressUpdates(ServerLevel level, Random random, int updates, boolean mutation) {
        for (int i = 0; i < updates; ++i) {
            int x = stressXZ(random);
            int y = stressY(level, random);
            int z = stressXZ(random);
            put(level, x, y, z,
                    mutation && i % 5 == 0 ? Blocks.AIR.defaultBlockState() : stressBlockState(random));
            if (i % 11 == 0) {
                moveStressSource(level, random, x, y, z);
            }
        }
    }

    private static void moveStressSource(ServerLevel level, Random random, int x, int y, int z) {
        Direction direction = STRESS_DIRECTIONS[random.nextInt(STRESS_DIRECTIONS.length)];
        int toX = x + direction.getStepX();
        int toY = y + direction.getStepY();
        int toZ = z + direction.getStepZ();
        if (!inBuildHeight(level, toY) || !inFuzzBlockArea(toX, toZ)) {
            return;
        }
        put(level, x, y, z, Blocks.AIR.defaultBlockState());
        put(level, toX, toY, toZ, stressSource(random));
    }

    private static void mutateStressFuzzFixture(ServerLevel level) {
        Random random = new Random(LIGHT_FUZZ_SEED ^ 0x3C6EF372FE94F82AL);
        applyStressUpdates(level, random, STRESS_MUTATION_UPDATES, true);
    }

    private static void clearDirtyColumnFuzzVolume(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int z = DIRTY_COLUMN_MIN; z <= DIRTY_COLUMN_MAX; ++z) {
            for (int x = DIRTY_COLUMN_MIN; x <= DIRTY_COLUMN_MAX; ++x) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); ++y) {
                    put(level, x, y, z, air);
                }
            }
        }
    }

    private static void buildDirtyColumnFuzzFixture(ServerLevel level) {
        int lowY = level.getMinBuildHeight() + DIRTY_COLUMN_HEIGHT_MARGIN;
        int highY = level.getMaxBuildHeight() - DIRTY_COLUMN_HEIGHT_MARGIN;
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int z = DIRTY_COLUMN_MIN; z <= DIRTY_COLUMN_MAX; ++z) {
            for (int x = DIRTY_COLUMN_MIN; x <= DIRTY_COLUMN_MAX; ++x) {
                put(level, x, lowY, z, stone);
                if (((x ^ z) & 1) == 0) {
                    put(level, x, highY, z, stone);
                }
            }
        }
    }

    private static void mutateDirtyColumnFuzzFixture(ServerLevel level, int round) {
        int highY = level.getMaxBuildHeight() - DIRTY_COLUMN_HEIGHT_MARGIN;
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int highParity = (round + 1) & 1;
        for (int z = DIRTY_COLUMN_MIN; z <= DIRTY_COLUMN_MAX; ++z) {
            for (int x = DIRTY_COLUMN_MIN; x <= DIRTY_COLUMN_MAX; ++x) {
                put(level, x, highY, z, ((x ^ z) & 1) == highParity ? stone : air);
            }
        }
    }

    private static BlockState stressBlockState(Random random) {
        return switch (random.nextInt(12)) {
            case 0 -> Blocks.AIR.defaultBlockState();
            case 1 -> stressOpaque(random);
            case 2 -> Blocks.GLASS.defaultBlockState();
            case 3 -> Blocks.TINTED_GLASS.defaultBlockState();
            case 4 -> Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
            case 5 -> Blocks.OAK_FENCE.defaultBlockState();
            case 6 -> stressSlab(random);
            case 7 -> Blocks.OAK_STAIRS.defaultBlockState().setValue(
                    StairBlock.FACING,
                    STRESS_HORIZONTAL_DIRECTIONS[random.nextInt(STRESS_HORIZONTAL_DIRECTIONS.length)]);
            default -> stressSource(random);
        };
    }

    private static BlockState stressOpaque(Random random) {
        return random.nextBoolean()
                ? Blocks.STONE.defaultBlockState()
                : Blocks.DEEPSLATE.defaultBlockState();
    }

    private static BlockState stressSlab(Random random) {
        SlabType type = random.nextBoolean() ? SlabType.TOP : SlabType.BOTTOM;
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState stressSource(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> Blocks.SEA_LANTERN.defaultBlockState();
            case 1 -> Blocks.GLOWSTONE.defaultBlockState();
            case 2 -> Blocks.SHROOMLIGHT.defaultBlockState();
            default -> Blocks.REDSTONE_LAMP.defaultBlockState()
                    .setValue(RedstoneLampBlock.LIT, Boolean.TRUE);
        };
    }

    private static int stressXZ(Random random) {
        return STRESS_XZ[random.nextInt(STRESS_XZ.length)] + random.nextInt(3) - 1;
    }

    private static int stressY(ServerLevel level, Random random) {
        if (random.nextInt(8) == 0) {
            return random.nextBoolean() ? level.getMinBuildHeight() : level.getMaxBuildHeight() - 1;
        }
        int section = level.getMinSection() + random.nextInt(level.getSectionsCount());
        int y = (section << 4) + STRESS_Y_OFFSETS[random.nextInt(STRESS_Y_OFFSETS.length)];
        return Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, y));
    }

    private static void put(ServerLevel level, int x, int y, int z, BlockState state) {
        // Use client notification only: LevelChunk#setBlockState still queues light
        // checks, while neighbor physics cannot break intentionally unsupported
        // fixture states such as free-floating cave vines.
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    private static boolean inBuildHeight(ServerLevel level, int y) {
        return y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight();
    }

    private static boolean inFuzzBlockArea(int x, int z) {
        return x >= FUZZ_MIN_BLOCK && x <= FUZZ_MAX_BLOCK
                && z >= FUZZ_MIN_BLOCK && z <= FUZZ_MAX_BLOCK;
    }

    private static boolean isKnownLightFuzzVariant() {
        return runsBlackoutLightFuzz()
                || runsDefaultLightFuzz()
                || runsEdgeLightFuzz()
                || runsStressLightFuzz()
                || runsDirtyColumnLightFuzz();
    }

    private static boolean runsDefaultLightFuzz() {
        return "default".equals(LIGHT_FUZZ_VARIANT) || "all".equals(LIGHT_FUZZ_VARIANT);
    }

    private static boolean runsBlackoutLightFuzz() {
        return "blackout".equals(LIGHT_FUZZ_VARIANT);
    }

    private static boolean runsEdgeLightFuzz() {
        return "edges".equals(LIGHT_FUZZ_VARIANT) || "all".equals(LIGHT_FUZZ_VARIANT);
    }

    private static boolean runsStressLightFuzz() {
        return "stress".equals(LIGHT_FUZZ_VARIANT) || "all".equals(LIGHT_FUZZ_VARIANT);
    }

    private static boolean runsDirtyColumnLightFuzz() {
        return "dirty_columns".equals(LIGHT_FUZZ_VARIANT);
    }

    private static LightFuzzRun active() {
        return ChunkyWorldGenDriver.lightFuzzRun;
    }
}
