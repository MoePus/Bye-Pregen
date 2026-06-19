package com.moepus.byepregen.test;

import com.mojang.logging.LogUtils;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.api.ChunkyAPI;
import org.popcraft.chunky.api.event.task.GenerationCompleteEvent;
import org.popcraft.chunky.api.event.task.GenerationProgressEvent;
import org.slf4j.Logger;

final class ChunkyWorldGenDriver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = property("world", "minecraft:overworld");
    private static final String SHAPE = property("shape", "circle");
    private static final String PATTERN = property("pattern", "concentric");
    private static final double CENTER_X = doubleProperty("centerX", 0.0D);
    private static final double CENTER_Z = doubleProperty("centerZ", 0.0D);
    private static final double RADIUS = doubleProperty("radius", 700.0D);
    private static final long PROGRESS_LOG_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean STOPPING = new AtomicBoolean();
    private static final AtomicLong NEXT_PROGRESS_LOG = new AtomicLong();

    private ChunkyWorldGenDriver() {
    }

    static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ChunkyWorldGenDriver::onServerStarted);
        LOGGER.info("Registered ByePregen Chunky worldgen test driver");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Chunky chunky = ChunkyProvider.get();
        if (chunky == null) {
            failAndStop(server, "Chunky provider is not ready");
            return;
        }

        ChunkyAPI api = chunky.getApi();
        api.onGenerationProgress(ChunkyWorldGenDriver::onGenerationProgress);
        api.onGenerationComplete(done -> onGenerationComplete(server, done));
        if (!api.startTask(WORLD, SHAPE, CENTER_X, CENTER_Z, RADIUS, RADIUS, PATTERN)) {
            failAndStop(server, "Chunky refused to start the worldgen task for " + WORLD);
            return;
        }

        LOGGER.info("Started Chunky worldgen test: world={} shape={} center=({}, {}) radius={} pattern={}",
                WORLD, SHAPE, CENTER_X, CENTER_Z, RADIUS, PATTERN);
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
        if (!WORLD.equals(event.world()) || !STOPPING.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info("Chunky worldgen completed for {}; saving and stopping server", event.world());
        server.executeIfPossible(() -> stopServer(server));
    }

    private static void failAndStop(MinecraftServer server, String message) {
        LOGGER.error("ByePregen Chunky worldgen test failed: {}", message);
        if (STOPPING.compareAndSet(false, true)) {
            server.executeIfPossible(() -> server.halt(false));
        }
    }

    private static void stopServer(MinecraftServer server) {
        server.halt(false);
    }

    private static String property(String name, String fallback) {
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
}
