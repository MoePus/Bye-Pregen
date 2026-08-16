package com.moepus.byepregen.test;

import com.moepus.byepregen.worldgen.surface.SurfaceScalarMetrics;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import net.minecraft.server.MinecraftServer;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.api.ChunkyAPI;
import org.popcraft.chunky.api.event.task.GenerationCompleteEvent;
import org.popcraft.chunky.api.event.task.GenerationProgressEvent;
import org.slf4j.Logger;

final class ChunkyGenerationRun {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;
    private final WorldgenHarnessController controller;
    private long startedNanos;
    private long startedCpuNanos;

    private ChunkyGenerationRun(MinecraftServer server, WorldgenHarnessController controller) {
        this.server = server;
        this.controller = controller;
    }

    static void start(MinecraftServer server, WorldgenHarnessController controller) {
        new ChunkyGenerationRun(server, controller).start();
    }

    private void start() {
        Chunky chunky = ChunkyProvider.get();
        if (chunky == null) {
            this.controller.failAndStop(this.server, "Chunky provider is not ready");
            return;
        }

        String shape = WorldgenHarnessProperties.get("shape", "circle");
        String pattern = WorldgenHarnessProperties.get("pattern", "concentric");
        double centerX = WorldgenHarnessProperties.getDouble("centerX", 0.0D);
        double centerZ = WorldgenHarnessProperties.getDouble("centerZ", 0.0D);
        double radius = WorldgenHarnessProperties.getDouble("radius", 700.0D);
        ChunkyAPI api = chunky.getApi();
        api.onGenerationProgress(this::onProgress);
        api.onGenerationComplete(this::onComplete);
        this.startedNanos = System.nanoTime();
        this.startedCpuNanos = processCpuNanos();
        if (!api.startTask(this.controller.world(), shape, centerX, centerZ, radius, radius, pattern)) {
            this.controller.failAndStop(
                    this.server,
                    "Chunky refused to start the worldgen task for " + this.controller.world()
            );
            return;
        }
        LOGGER.info("Started Chunky worldgen test: world={} shape={} center=({}, {}) radius={} pattern={}",
                this.controller.world(), shape, centerX, centerZ, radius, pattern);
    }

    private void onProgress(GenerationProgressEvent event) {
        if (!this.controller.world().equals(event.world()) || !this.controller.acquireProgressLog()) {
            return;
        }
        LOGGER.info("Chunky worldgen progress: world={} progress={} chunks={} rate={} chunk=({}, {}) eta={}h {}m {}s",
                event.world(), event.progress(), event.chunks(), event.rate(), event.x(), event.z(),
                event.hours(), event.minutes(), event.seconds());
    }

    private void onComplete(GenerationCompleteEvent event) {
        if (!this.controller.world().equals(event.world())) {
            LOGGER.warn("Ignoring Chunky generation completion for {}, expected {}",
                    event.world(), this.controller.world());
            return;
        }
        if (!FastTickRuntimeProbe.completeAfterWorldgen()) {
            this.controller.failAndStop(this.server, "Fast tick runtime probe failed after worldgen");
            return;
        }
        double wallSeconds = (System.nanoTime() - this.startedNanos) / 1_000_000_000.0D;
        double cpuSeconds = (processCpuNanos() - this.startedCpuNanos) / 1_000_000_000.0D;
        LOGGER.info(
                "Chunky worldgen completed for {}; wallSeconds={} processCpuSeconds={}; saving and stopping server",
                event.world(),
                wallSeconds,
                cpuSeconds
        );
        logSurfaceScalarMetrics();
        this.controller.succeedAndStop(this.server, "world=" + event.world());
    }

    private static void logSurfaceScalarMetrics() {
        SurfaceScalarMetrics.Snapshot metrics = SurfaceScalarMetrics.snapshot();
        LOGGER.info(
                "Surface scalar metrics: compiled={} rejected={} bindings={} bindFailures={} "
                        + "outputComparisons={} outputMismatches={} classBytes={} regions={}",
                metrics.compiled(), metrics.rejected(), metrics.bindings(), metrics.bindFailures(),
                metrics.outputComparisons(), metrics.outputMismatches(), metrics.latestClassBytes(),
                metrics.latestRegions()
        );
    }

    private static long processCpuNanos() {
        return ProcessHandle.current().info().totalCpuDuration().map(Duration::toNanos).orElse(0L);
    }
}
