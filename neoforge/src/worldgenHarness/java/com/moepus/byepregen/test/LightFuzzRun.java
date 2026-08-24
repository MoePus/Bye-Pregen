package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.access.YALightEngineHolder;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;

final class LightFuzzRun {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long PENDING_LIGHT_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(5L);

    private final MinecraftServer server;
    private final ServerLevel level;
    private final WorldgenHarnessController controller;
    private final LightFuzzOptions options;
    private final LightFuzzFixture fixture;
    private final BlackoutLightFuzzFixture blackout;
    private final LightTorchLifecycleProbe torchLifecycleProbe;
    private List<ChunkPos> chunks = List.of();
    private CompletableFuture<Void> pendingLight;
    private String pendingStageName;
    private long pendingLightDeadline;
    private Stage stage = Stage.LOAD_CHUNKS;
    private int nextUpdateRound;
    private boolean waitingForTorchLight;

    private LightFuzzRun(RunContext context, LightFuzzOptions options, LightFuzzFixture fixture) {
        this.server = context.server();
        this.level = context.level();
        this.controller = context.controller();
        this.options = options;
        this.fixture = fixture;
        this.blackout = fixture instanceof BlackoutLightFuzzFixture value ? value : null;
        this.torchLifecycleProbe = this.blackout != null && yaLightEngineHolder(level) != null
                ? new LightTorchLifecycleProbe(level)
                : null;
    }

    static LightFuzzRun start(
            MinecraftServer server,
            WorldgenHarnessController controller,
            LightFuzzOptions options
    ) {
        if (!"minecraft:overworld".equals(controller.world())) {
            controller.failAndStop(server,
                    "Light fuzz mode currently supports only minecraft:overworld, got " + controller.world());
            return null;
        }
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            controller.failAndStop(server, "Overworld is not loaded");
            return null;
        }
        LightFuzzFixture fixture = LightFuzzFixtures.create(level, options.variant(), options.seed());
        if (fixture == null) {
            controller.failAndStop(server, "Unknown light fuzz variant: " + options.variant());
            return null;
        }
        LOGGER.info("Started light fuzz: world={} seed={} variant={}",
                controller.world(), options.seed(), options.variant());
        return new LightFuzzRun(new RunContext(server, level, controller), options, fixture);
    }

    boolean owns(MinecraftServer server) {
        return this.server == server;
    }

    boolean isComplete() {
        return this.stage == Stage.COMPLETE;
    }

    void tick() {
        if (this.controller.isStopping()) {
            return;
        }
        try {
            if (!this.processPendingLight()) {
                return;
            }
            if (this.advanceTorchProbe()) {
                return;
            }
            this.runStage();
        } catch (Throwable throwable) {
            LOGGER.error("Light fuzz failed", throwable);
            this.stage = Stage.COMPLETE;
            this.controller.failAndStop(this.server, "Light fuzz failed: " + throwable.getMessage());
        }
    }

    private boolean processPendingLight() {
        if (this.pendingLight == null) {
            return true;
        }
        if (!this.pendingLight.isDone()) {
            if (System.nanoTime() > this.pendingLightDeadline) {
                throw new IllegalStateException("Timed out waiting for light fuzz stage: "
                        + this.pendingStageName);
            }
            this.logPending();
            return false;
        }
        this.pendingLight.join();
        LOGGER.info("Light fuzz stage completed: {}", this.pendingStageName);
        if (this.waitingForTorchLight) {
            this.torchLifecycleProbe.lightWaitCompleted();
            this.waitingForTorchLight = false;
            this.clearPendingLight();
            return false;
        }
        this.completeStageBarrier();
        this.logProbes();
        this.clearPendingLight();
        return true;
    }

    private boolean advanceTorchProbe() {
        if (this.torchLifecycleProbe == null || this.torchLifecycleProbe.isComplete()) {
            return false;
        }
        if (this.torchLifecycleProbe.advance()) {
            this.waitingForTorchLight = true;
            this.beginPendingLight(
                    "torch lifecycle " + this.torchLifecycleProbe.pendingStage(),
                    this.torchLifecycleProbe.waitForLight()
            );
        }
        return true;
    }

    private void runStage() {
        switch (this.stage) {
            case LOAD_CHUNKS -> {
                this.chunks = this.loadChunks();
                this.waitForLight("initial chunk light");
            }
            case CLEAR_VOLUME -> {
                this.fixture.clearVolume();
                this.waitForLight("clear volume");
            }
            case BUILD_FIXTURE -> {
                this.fixture.buildFixture();
                this.waitForLight("initial fixture");
            }
            case RECONCILE_ROUND_TRIP -> this.reconcileRoundTrip();
            case STABILIZE_ROUND_TRIP -> this.stabilizeRoundTrip();
            case MUTATE_FIXTURE -> this.startMutation();
            case UPDATE_ROUNDS -> this.startNextUpdate();
            case COMPLETE -> this.complete();
        }
    }

    private void completeStageBarrier() {
        this.stage = switch (this.stage) {
            case LOAD_CHUNKS -> Stage.CLEAR_VOLUME;
            case CLEAR_VOLUME -> Stage.BUILD_FIXTURE;
            case BUILD_FIXTURE -> this.blackout == null
                    ? Stage.MUTATE_FIXTURE
                    : Stage.RECONCILE_ROUND_TRIP;
            case RECONCILE_ROUND_TRIP -> {
                this.blackout.acceptReconciledRoundTrip();
                yield Stage.STABILIZE_ROUND_TRIP;
            }
            case STABILIZE_ROUND_TRIP -> this.blackout.verifyRoundTrip()
                    ? Stage.MUTATE_FIXTURE
                    : Stage.STABILIZE_ROUND_TRIP;
            case MUTATE_FIXTURE, UPDATE_ROUNDS -> {
                this.verifyUpdate();
                yield Stage.UPDATE_ROUNDS;
            }
            case COMPLETE -> Stage.COMPLETE;
        };
    }

    private void reconcileRoundTrip() {
        if (this.blackout.reloadRoundTripWhenUnloaded()) {
            this.waitForLight("round-trip reconciliation");
        }
    }

    private void stabilizeRoundTrip() {
        if (this.blackout.reloadRoundTripWhenUnloaded()) {
            this.waitForLight("reconciled round-trip reload");
        }
    }

    private void startMutation() {
        this.fixture.applyUpdate(0);
        this.nextUpdateRound = 1;
        this.waitForLight("mutated fixture");
    }

    private void startNextUpdate() {
        if (this.nextUpdateRound >= this.fixture.updateRounds()) {
            this.stage = Stage.COMPLETE;
            this.complete();
            return;
        }
        int round = this.nextUpdateRound++;
        this.fixture.applyUpdate(round);
        this.waitForLight(this.fixture.updateStageName(round));
    }

    private void verifyUpdate() {
        int completedRound = Math.max(0, this.nextUpdateRound - 1);
        this.fixture.verifyUpdate(completedRound);
        this.fixture.releaseLoadedChunks();
    }

    private List<ChunkPos> loadChunks() {
        List<ChunkPos> loaded = new ArrayList<>();
        int radius = this.fixture.loadRadius();
        for (int chunkZ = -radius; chunkZ <= radius; ++chunkZ) {
            for (int chunkX = -radius; chunkX <= radius; ++chunkX) {
                this.level.setChunkForced(chunkX, chunkZ, true);
                this.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
                loaded.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return loaded;
    }

    private void waitForLight(String stageName) {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[this.chunks.size()];
        for (int i = 0; i < this.chunks.size(); ++i) {
            ChunkPos chunk = this.chunks.get(i);
            futures[i] = this.level.getChunkSource().getLightEngine().waitForPendingTasks(chunk.x(), chunk.z());
        }
        this.beginPendingLight(stageName, CompletableFuture.allOf(futures));
    }

    private void beginPendingLight(String stageName, CompletableFuture<Void> pendingLight) {
        LOGGER.info("Waiting for light fuzz stage: {}", stageName);
        this.pendingStageName = stageName;
        this.pendingLight = pendingLight;
        this.pendingLightDeadline = System.nanoTime() + PENDING_LIGHT_TIMEOUT_NANOS;
    }

    private void clearPendingLight() {
        this.pendingLight = null;
        this.pendingStageName = null;
        this.pendingLightDeadline = 0L;
    }

    private void logPending() {
        if (this.controller.acquireProgressLog()) {
            LOGGER.info("Still waiting for light fuzz stage: {}", this.pendingStageName);
        }
    }

    private void logProbes() {
        if (!this.options.probes()) {
            return;
        }
        LOGGER.info(
                "Light fuzz probes after {}: glowstone={} glowstoneTail={} seaLantern={} "
                        + "redstoneLamp={} shroomlight={} caveVines={} center={}",
                this.pendingStageName,
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
        this.stage = Stage.COMPLETE;
        LOGGER.info("Light fuzz completed: world={} seed={}", this.controller.world(), this.options.seed());
        this.controller.succeedAndStop(
                this.server,
                "variant=" + this.options.variant() + "\nseed=" + this.options.seed()
        );
    }

    private static YALightEngineHolder yaLightEngineHolder(ServerLevel level) {
        Object lightEngine = level.getChunkSource().getLightEngine();
        return lightEngine instanceof YALightEngineHolder holder ? holder : null;
    }

    private enum Stage {
        LOAD_CHUNKS,
        CLEAR_VOLUME,
        BUILD_FIXTURE,
        RECONCILE_ROUND_TRIP,
        STABILIZE_ROUND_TRIP,
        MUTATE_FIXTURE,
        UPDATE_ROUNDS,
        COMPLETE
    }

    private record RunContext(
            MinecraftServer server,
            ServerLevel level,
            WorldgenHarnessController controller
    ) {
    }
}
