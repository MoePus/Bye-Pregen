package com.moepus.byepregen.test;

import com.moepus.byepregen.worldgen.surface.SurfaceScalarMetrics;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import net.minecraft.server.MinecraftServer;
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
        ChunkyRuntime chunky = ChunkyRuntime.load();
        if (chunky == null) {
            this.controller.failAndStop(this.server, "Chunky provider is not ready");
            return;
        }

        Options options = Options.fromSystemProperties();
        chunky.onGenerationProgress(this::onProgress);
        chunky.onGenerationComplete(this::onComplete);
        this.startedNanos = System.nanoTime();
        this.startedCpuNanos = processCpuNanos();
        if (!chunky.startTask(new ChunkyRuntime.Task(
                this.controller.world(),
                new ChunkyRuntime.Shape(options.taskSpec().shape(), options.taskSpec().pattern()),
                new ChunkyRuntime.Area(
                        new ChunkyRuntime.Center(options.center().x(), options.center().z()),
                        options.radius()
                )
        ))) {
            this.controller.failAndStop(
                    this.server,
                    "Chunky refused to start the worldgen task for " + this.controller.world()
            );
            return;
        }
        LOGGER.info("Started Chunky worldgen test: world={} shape={} center=({}, {}) radius={} pattern={}",
                this.controller.world(), options.taskSpec().shape(), options.center().x(), options.center().z(),
                options.radius(), options.taskSpec().pattern());
    }

    private void onProgress(ChunkyRuntime.Event event) {
        if (!this.controller.world().equals(event.world()) || !this.controller.acquireProgressLog()) {
            return;
        }
        LOGGER.info("Chunky worldgen progress: world={} progress={} chunks={} rate={} chunk=({}, {}) eta={}h {}m {}s",
                event.world(), event.value("progress"), event.value("chunks"), event.value("rate"),
                event.value("x"), event.value("z"), event.value("hours"), event.value("minutes"),
                event.value("seconds"));
    }

    private void onComplete(ChunkyRuntime.Event event) {
        if (!this.controller.world().equals(event.world())) {
            LOGGER.warn("Ignoring Chunky generation completion for {}, expected {}",
                    event.world(), this.controller.world());
            return;
        }
        if (!FastTickRuntimeProbe.completeAfterWorldgen()) {
            this.controller.failAndStop(this.server, "Fast tick runtime probe failed after worldgen");
            return;
        }
        String densityColumnMetrics;
        try {
            densityColumnMetrics = DensityColumnRuntimeProbe.verify();
        } catch (RuntimeException throwable) {
            this.controller.failAndStop(this.server,
                    "Density column runtime probe failed: " + throwable.getMessage());
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
        this.controller.succeedAndStop(this.server,
                "world=" + event.world() + "\ndensityColumn=" + densityColumnMetrics
        );
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

    private record Options(
            TaskSpec taskSpec,
            Center center,
            double radius
    ) {
        private static Options fromSystemProperties() {
            return new Options(
                    new TaskSpec(
                            WorldgenHarnessProperties.get("shape", "circle"),
                            WorldgenHarnessProperties.get("pattern", "concentric")
                    ),
                    new Center(
                            WorldgenHarnessProperties.getDouble("centerX", 0.0D),
                            WorldgenHarnessProperties.getDouble("centerZ", 0.0D)
                    ),
                    WorldgenHarnessProperties.getDouble("radius", 700.0D)
            );
        }
    }

    private record TaskSpec(String shape, String pattern) {
    }

    private record Center(double x, double z) {
    }
}
