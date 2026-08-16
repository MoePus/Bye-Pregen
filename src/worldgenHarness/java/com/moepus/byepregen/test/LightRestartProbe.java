package com.moepus.byepregen.test;

import com.ishland.c2me.notickvd.common.NoTickSystem;
import com.moepus.byepregen.yalight.access.YALightEngineHolder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

final class LightRestartProbe {
    static final String MODE = "light_restart";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    static final ChunkPos CENTER = new ChunkPos(0, 0);
    static final ChunkPos RELIGHT = new ChunkPos(1, 0);
    static final ChunkPos BOUNDARY = new ChunkPos(0, 1);
    static final ChunkPos MIXED = new ChunkPos(-2, -2);
    static final int LOAD_RADIUS = 2;
    static final int ROOF_Y = 95;
    static final int BOUNDARY_ROOF_Y = 96;
    static final int CAVE_Y = 94;
    static final BlockPos OPENING = new BlockPos(-24, ROOF_Y, -24);
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(90L);
    private static Run active;

    private LightRestartProbe() {
    }

    static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(LightRestartProbe::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(LightRestartProbe::onServerStarted);
        NeoForge.EVENT_BUS.addListener(LightRestartProbe::onServerTickPost);
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        event.getServer().getWorldData().getGameRules()
                .getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            fail(server, "Overworld is unavailable");
            return;
        }
        try {
            active = new Run(server, level, Phase.parse(property("phase", "verify")));
        } catch (Throwable throwable) {
            LOGGER.error("Failed to start YA light restart probe", throwable);
            fail(server, throwable.getMessage());
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        Run run = active;
        if (run == null || run.server != event.getServer()) {
            return;
        }
        run.tick();
    }

    private static String property(String name, String fallback) {
        return System.getProperty("byepregen.testWorldGen." + name, fallback);
    }

    private static void fail(MinecraftServer server, String message) {
        writeResult("FAIL " + message);
        server.executeIfPossible(() -> stop(server));
    }

    private static void writeResult(String value) {
        Path result = Path.of(property("result", "light-restart.result"));
        try {
            Files.writeString(result, value + "\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.error("Failed to write light restart result {}", result, exception);
        }
    }

    private static void stop(MinecraftServer server) {
        try {
            server.saveAllChunks(false, true, true);
        } catch (Throwable throwable) {
            LOGGER.error("Failed to save light restart fixture", throwable);
        }
        server.halt(false);
    }

    private enum Phase {
        PREPARE,
        VERIFY;

        private static Phase parse(String value) {
            return Phase.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        }
    }

    private enum Stage {
        WAIT_LOAD,
        WAIT_LIGHT,
        INSPECT,
        COMPLETE
    }

    private static final class Run {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final Phase phase;
        private final NoTickSystem noTick;
        private final Path baseline;
        private final long deadline = System.nanoTime() + TIMEOUT_NANOS;
        private Stage stage = Stage.WAIT_LOAD;
        private CompletableFuture<Void> lightBarrier;
        private boolean normalized;
        private int ticks;

        private Run(MinecraftServer server, ServerLevel level, Phase phase) {
            this.server = server;
            this.level = level;
            this.phase = phase;
            this.baseline = Path.of(property("baseline", "light-restart.baseline"));
            this.noTick = new NoTickSystem(level.getChunkSource().chunkMap);
            this.noTick.setNoTickViewDistance(LOAD_RADIUS);
            this.noTick.addPlayerSource(CENTER);
            LOGGER.info("Started YA light restart probe phase={} baseline={}", phase, this.baseline);
        }

        private void tick() {
            if (this.stage == Stage.COMPLETE) {
                return;
            }
            try {
                this.driveNoTick();
                if (System.nanoTime() > this.deadline) {
                    throw new IllegalStateException("Timed out in stage " + this.stage);
                }
                switch (this.stage) {
                    case WAIT_LOAD -> this.waitForLoad();
                    case WAIT_LIGHT -> this.waitForLight();
                    case INSPECT -> this.inspect();
                    case COMPLETE -> {
                    }
                }
            } catch (Throwable throwable) {
                LOGGER.error("YA light restart probe failed in phase={} stage={}", this.phase, this.stage, throwable);
                this.complete("FAIL " + throwable.getMessage());
            }
        }

        private void driveNoTick() {
            this.noTick.beforeTicketTicks();
            this.noTick.afterTicketTicks();
            this.noTick.tick();
            ++this.ticks;
        }

        private void waitForLoad() {
            if (this.ticks < 3 || !this.allChunksLoaded()) {
                return;
            }
            if (this.phase == Phase.PREPARE) {
                this.buildRoof();
            }
            this.lightBarrier = this.createLightBarrier();
            this.stage = Stage.WAIT_LIGHT;
        }

        private boolean allChunksLoaded() {
            for (int z = -LOAD_RADIUS; z <= LOAD_RADIUS; ++z) {
                for (int x = -LOAD_RADIUS; x <= LOAD_RADIUS; ++x) {
                    if (this.level.getChunkSource().getChunkNow(x, z) == null) {
                        return false;
                    }
                }
            }
            return true;
        }

        private void buildRoof() {
            int min = -LOAD_RADIUS << 4;
            int max = ((LOAD_RADIUS + 1) << 4) - 1;
            for (int z = min; z <= max; ++z) {
                for (int x = min; x <= max; ++x) {
                    int roofY = isInChunk(x, z, BOUNDARY) ? BOUNDARY_ROOF_Y : ROOF_Y;
                    BlockPos pos = new BlockPos(x, roofY, z);
                    this.level.setBlock(pos, pos.equals(OPENING)
                            ? Blocks.AIR.defaultBlockState()
                            : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        private static boolean isInChunk(int blockX, int blockZ, ChunkPos chunk) {
            return blockX >> 4 == chunk.x && blockZ >> 4 == chunk.z;
        }

        private CompletableFuture<Void> createLightBarrier() {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (int z = -LOAD_RADIUS; z <= LOAD_RADIUS; ++z) {
                for (int x = -LOAD_RADIUS; x <= LOAD_RADIUS; ++x) {
                    futures.add(this.level.getChunkSource().getLightEngine().waitForPendingTasks(x, z));
                }
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        }

        private void waitForLight() {
            if (!this.lightBarrier.isDone()) {
                return;
            }
            this.lightBarrier.join();
            if (this.phase == Phase.PREPARE && !this.normalized) {
                this.normalizeRoofSections();
                this.normalized = true;
                this.lightBarrier = this.createLightBarrier();
                return;
            }
            this.stage = Stage.INSPECT;
        }

        private void normalizeRoofSections() {
            LevelLightEngine lightEngine = this.level.getChunkSource().getLightEngine();
            var engine = ((YALightEngineHolder)lightEngine).byepregen$getYALightEngine();
            engine.queueZeroSectionData(LightLayer.SKY, SectionPos.of(CENTER, ROOF_Y >> 4));
            engine.queueZeroSectionData(LightLayer.SKY, SectionPos.of(RELIGHT, ROOF_Y >> 4));
            engine.queueZeroSectionData(LightLayer.SKY, SectionPos.of(BOUNDARY, ROOF_Y >> 4));
        }

        private void inspect() throws IOException {
            LightRestartSnapshot snapshot = LightRestartSnapshot.capture(this.level);
            LightRestartVerifier.verifyRuntimeState(this.level, snapshot.samples());
            if (this.phase == Phase.PREPARE) {
                LightRestartVerifier.verifyCompressedFixture(this.level);
                snapshot.write(this.baseline);
                this.invalidateRelightFixture();
                this.complete("PREPARED");
                return;
            }
            LightRestartSnapshot expected = LightRestartSnapshot.read(this.baseline);
            String difference = expected.firstDifference(snapshot);
            if (difference != null) {
                throw new IllegalStateException(difference);
            }
            this.complete("PASS");
        }

        private void invalidateRelightFixture() {
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(RELIGHT.x, RELIGHT.z);
            chunk.setLightCorrect(false);
            chunk.setUnsaved(true);
            LightRestartVerifier.verifyRelightFixtureInvalid(this.level);
        }

        private void complete(String result) {
            this.stage = Stage.COMPLETE;
            active = null;
            this.noTick.close();
            writeResult(result);
            this.server.executeIfPossible(() -> stop(this.server));
        }
    }
}
