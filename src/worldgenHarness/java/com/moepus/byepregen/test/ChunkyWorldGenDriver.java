package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.HarnessResultFile;
import com.moepus.byepregen.worldgen.surface.SurfaceScalarMetrics;
import com.moepus.byepregen.yalight.access.YALightEngineHolder;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.api.ChunkyAPI;
import org.popcraft.chunky.api.event.task.GenerationCompleteEvent;
import org.popcraft.chunky.api.event.task.GenerationProgressEvent;
import org.slf4j.Logger;

final class ChunkyWorldGenDriver {
    static final Logger LOGGER = LogUtils.getLogger();
    static final String WORLD = property("world", "minecraft:overworld");
    static final long PROGRESS_LOG_NANOS = TimeUnit.SECONDS.toNanos(30L);
    static final AtomicLong NEXT_PROGRESS_LOG = new AtomicLong();

    private static final String MODE = property("mode", "chunky");
    private static final String SHAPE = property("shape", "circle");
    private static final String PATTERN = property("pattern", "concentric");
    private static final double CENTER_X = doubleProperty("centerX", 0.0D);
    private static final double CENTER_Z = doubleProperty("centerZ", 0.0D);
    private static final double RADIUS = doubleProperty("radius", 700.0D);
    private static final String RUN_RESULT_PROPERTY = "byepregen.testWorldGen.runResult";
    private static final long SHUTDOWN_WATCHDOG_SECONDS = 60L;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean STOPPING = new AtomicBoolean();
    private static long worldgenStartedNanos;
    private static long worldgenStartedCpuNanos;
    static LightFuzzRun lightFuzzRun;

    private ChunkyWorldGenDriver() {
    }

    static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ChunkyWorldGenDriver::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(ChunkyWorldGenDriver::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ChunkyWorldGenDriver::onServerTickPost);
        LOGGER.info("Registered ByePregen Chunky worldgen test driver");
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // Smoke tests should measure Chunky-driven worldgen, not vanilla spawn preloading.
        // Pass null to avoid firing the rule listener before the overworld is constructed.
        GameRules gameRules = event.getServer().getWorldData().getGameRules();
        gameRules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
        if ("light_fuzz".equals(MODE)) {
            gameRules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        }
        LOGGER.info("Disabled spawn chunk preloading for ByePregen Chunky worldgen test");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!FastTickRuntimeProbe.verifyBeforeWorldgen()) {
            failAndStop(server, "Fast tick runtime probe failed before worldgen");
            return;
        }
        if ("relight".equals(MODE)) {
            LightRelightRun.run(server);
            return;
        }
        if ("light_fuzz".equals(MODE)) {
            LightFuzzRun.start(server);
            return;
        }
        if (!"chunky".equals(MODE)) {
            failAndStop(server, "Unknown ByePregen worldgen test mode: " + MODE);
            return;
        }
        startChunky(server);
    }

    private static void startChunky(MinecraftServer server) {
        Chunky chunky = ChunkyProvider.get();
        if (chunky == null) {
            failAndStop(server, "Chunky provider is not ready");
            return;
        }

        ChunkyAPI api = chunky.getApi();
        api.onGenerationProgress(ChunkyWorldGenDriver::onGenerationProgress);
        api.onGenerationComplete(done -> onGenerationComplete(server, done));
        worldgenStartedNanos = System.nanoTime();
        worldgenStartedCpuNanos = processCpuNanos();
        if (!api.startTask(WORLD, SHAPE, CENTER_X, CENTER_Z, RADIUS, RADIUS, PATTERN)) {
            failAndStop(server, "Chunky refused to start the worldgen task for " + WORLD);
            return;
        }

        LOGGER.info("Started Chunky worldgen test: world={} shape={} center=({}, {}) radius={} pattern={}",
                WORLD, SHAPE, CENTER_X, CENTER_Z, RADIUS, PATTERN);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        LightFuzzRun run = lightFuzzRun;
        if (run != null && run.owns(event.getServer())) {
            run.tick();
        }
    }

    private static void onGenerationProgress(GenerationProgressEvent event) {
        if (!WORLD.equals(event.world())) {
            return;
        }
        long now = System.nanoTime();
        long next = NEXT_PROGRESS_LOG.get();
        if (now < next || !NEXT_PROGRESS_LOG.compareAndSet(next, now + PROGRESS_LOG_NANOS)) {
            return;
        }
        LOGGER.info("Chunky worldgen progress: world={} progress={} chunks={} rate={} chunk=({}, {}) eta={}h {}m {}s",
                event.world(), event.progress(), event.chunks(), event.rate(), event.x(), event.z(),
                event.hours(), event.minutes(), event.seconds());
    }

    private static void onGenerationComplete(MinecraftServer server, GenerationCompleteEvent event) {
        if (!WORLD.equals(event.world())) {
            LOGGER.warn("Ignoring Chunky generation completion for {}, expected {}", event.world(), WORLD);
            return;
        }
        if (!FastTickRuntimeProbe.completeAfterWorldgen()) {
            failAndStop(server, "Fast tick runtime probe failed after worldgen");
            return;
        }
        double wallSeconds = (System.nanoTime() - worldgenStartedNanos) / 1_000_000_000.0D;
        double processCpuSeconds = (processCpuNanos() - worldgenStartedCpuNanos) / 1_000_000_000.0D;
        LOGGER.info(
                "Chunky worldgen completed for {}; wallSeconds={} processCpuSeconds={}; saving and stopping server",
                event.world(),
                wallSeconds,
                processCpuSeconds
        );
        logSurfaceScalarMetrics();
        succeedAndStop(server, "world=" + event.world());
    }

    private static void logSurfaceScalarMetrics() {
        SurfaceScalarMetrics.Snapshot metrics = SurfaceScalarMetrics.snapshot();
        LOGGER.info(
                "Surface scalar metrics: compiled={} rejected={} bindings={} bindFailures={} "
                        + "outputComparisons={} outputMismatches={} classBytes={} regions={}",
                metrics.compiled(),
                metrics.rejected(),
                metrics.bindings(),
                metrics.bindFailures(),
                metrics.outputComparisons(),
                metrics.outputMismatches(),
                metrics.latestClassBytes(),
                metrics.latestRegions()
        );
    }

    private static long processCpuNanos() {
        return ProcessHandle.current().info().totalCpuDuration().map(Duration::toNanos).orElse(0L);
    }

    static YALightEngineHolder yaLightEngineHolder(ServerLevel level) {
        Object lightEngine = level.getChunkSource().getLightEngine();
        return lightEngine instanceof YALightEngineHolder holder ? holder : null;
    }

    static boolean isStopping() {
        return STOPPING.get();
    }

    static void failAndStop(MinecraftServer server, String message) {
        LOGGER.error("ByePregen Chunky worldgen test failed: {}", message);
        finishAndStop(server, RunCompletion.failure(message));
    }

    static void succeedAndStop(MinecraftServer server, String detail) {
        finishAndStop(server, RunCompletion.success(detail));
    }

    private static void finishAndStop(MinecraftServer server, RunCompletion completion) {
        if (STOPPING.compareAndSet(false, true)) {
            server.executeIfPossible(() -> stopServer(server, completion));
        }
    }

    private static void stopServer(MinecraftServer server, RunCompletion requestedCompletion) {
        RunCompletion completion = requestedCompletion;
        try {
            server.saveAllChunks(false, true, true);
        } catch (Throwable throwable) {
            LOGGER.error("Failed to save ByePregen test world before shutdown", throwable);
            completion = completion.withSaveFailure(throwable);
        }
        try {
            HarnessResultFile.write(RUN_RESULT_PROPERTY, completion.format());
        } catch (Throwable throwable) {
            LOGGER.error("Failed to write ByePregen worldgen result", throwable);
        }
        server.halt(false);
        forceExitIfShutdownStalls();
    }

    private static void forceExitIfShutdownStalls() {
        Thread watchdog = new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(SHUTDOWN_WATCHDOG_SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            LOGGER.warn("ByePregen Chunky worldgen test did not exit after shutdown request; forcing JVM exit");
            System.exit(1);
        }, "ByePregen test shutdown watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    static String property(String name, String fallback) {
        return System.getProperty("byepregen.testWorldGen." + name, fallback);
    }

    private static double doubleProperty(String name, double fallback) {
        String value = property(name, Double.toString(fallback));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            LOGGER.warn("Invalid byepregen.testWorldGen.{}={}, using {}", name, value, fallback);
            return fallback;
        }
    }

    static long longProperty(String name, long fallback) {
        String value = property(name, Long.toString(fallback));
        try {
            return Long.decode(value);
        } catch (NumberFormatException exception) {
            LOGGER.warn("Invalid byepregen.testWorldGen.{}={}, using {}", name, value, fallback);
            return fallback;
        }
    }

    static boolean booleanProperty(String name, boolean fallback) {
        return Boolean.parseBoolean(property(name, Boolean.toString(fallback)));
    }

    private record RunCompletion(boolean success, String detail) {
        static RunCompletion success(String detail) {
            return new RunCompletion(true, detail);
        }

        static RunCompletion failure(String message) {
            return new RunCompletion(false, "message=" + message);
        }

        RunCompletion withSaveFailure(Throwable throwable) {
            String saveFailure = "saveFailure=" + throwable;
            return new RunCompletion(false, this.detail + "\n" + saveFailure);
        }

        String format() {
            return (this.success ? "PASS" : "FAIL") + "\nmode=" + MODE + "\n" + this.detail + "\n";
        }
    }
}
