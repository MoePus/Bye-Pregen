package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.access.YALightEngineHolder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

final class LightRelightRun {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LightRelightRun() {
    }

    static void run(MinecraftServer server, WorldgenHarnessController controller) {
        if (!"minecraft:overworld".equals(controller.world())) {
            controller.failAndStop(server,
                    "Relight mode currently supports only minecraft:overworld, got "
                            + controller.world());
            return;
        }

        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            controller.failAndStop(server, "Overworld is not loaded");
            return;
        }

        Path chunkList = server.getWorldPath(LevelResource.ROOT)
                .resolve(LightGoldenPrepareRelight.CHUNK_LIST_FILE);
        List<ChunkPos> chunks;
        try {
            chunks = readChunkList(chunkList);
        } catch (IOException exception) {
            LOGGER.error("Failed to read light golden relight chunk list {}", chunkList, exception);
            controller.failAndStop(server, "Failed to read relight chunk list: " + chunkList);
            return;
        }

        if (chunks.isEmpty()) {
            controller.failAndStop(server, "Relight chunk list is empty: " + chunkList);
            return;
        }

        LOGGER.info("Started light golden relight: world={} chunks={} list={}",
                controller.world(), chunks.size(), chunkList);
        RunContext context = new RunContext(server, level, controller);
        if (context.yaLight() != null) {
            context.yaLight().byepregen$getYALightEngine().setDeclaredFreshOwnerDomain(chunks);
        }
        loadChunks(context, chunks);
    }

    private static void loadChunks(RunContext context, List<ChunkPos> chunks) {
        int completed = 0;
        long startedNanos = System.nanoTime();
        long getChunkCompletedNanos = startedNanos;
        long cleanupStartedNanos;
        long cleanupCompletedNanos;
        try {
            for (ChunkPos pos : chunks) {
                context.level().getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
                if (completed + 1 == chunks.size()) {
                    getChunkCompletedNanos = System.nanoTime();
                }
                ++completed;
                context.logProgress(completed, chunks.size(), pos);
            }
        } catch (Throwable throwable) {
            LOGGER.error("Light golden relight failed after {}/{} chunks", completed, chunks.size(), throwable);
            context.controller().failAndStop(context.server(),
                    "Relight failed after " + completed + "/" + chunks.size() + " chunks");
            return;
        } finally {
            cleanupStartedNanos = System.nanoTime();
            if (context.yaLight() != null) {
                context.yaLight().byepregen$getYALightEngine().clearDeclaredFreshOwnerDomain();
            }
            cleanupCompletedNanos = System.nanoTime();
        }

        RelightTimings timings = new RelightTimings(
                startedNanos,
                getChunkCompletedNanos,
                new CleanupTiming(cleanupStartedNanos, cleanupCompletedNanos)
        );
        logCompletion(context.controller().world(), chunks.size(), timings);
        context.controller().succeedAndStop(context.server(), "chunks=" + chunks.size());
    }

    private static List<ChunkPos> readChunkList(Path chunkList) throws IOException {
        List<ChunkPos> chunks = new ArrayList<>();
        for (String line : Files.readAllLines(chunkList)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length != 2) {
                throw new IOException("Invalid chunk entry in " + chunkList + ": " + line);
            }
            try {
                chunks.add(new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid chunk coordinates in " + chunkList + ": " + line, exception);
            }
        }
        return chunks;
    }

    private static void logCompletion(String world, int chunks, RelightTimings timings) {
        long lifecycleCompletedNanos = System.nanoTime();
        double getChunkSeconds = (timings.getChunkCompletedNanos() - timings.startedNanos())
                / 1_000_000_000.0D;
        double cleanupSeconds = (timings.cleanup().completedNanos() - timings.cleanup().startedNanos())
                / 1_000_000_000.0D;
        double lifecycleSeconds = (lifecycleCompletedNanos - timings.startedNanos()) / 1_000_000_000.0D;
        LOGGER.info(
                "Light golden relight completed: world={} chunks={} getChunkSeconds={} "
                        + "cleanupSeconds={} lifecycleSeconds={}",
                world,
                chunks,
                getChunkSeconds,
                cleanupSeconds,
                lifecycleSeconds
        );
    }

    private record RelightTimings(
            long startedNanos,
            long getChunkCompletedNanos,
            CleanupTiming cleanup
    ) {
    }

    private record CleanupTiming(long startedNanos, long completedNanos) {
    }

    private static final class RunContext {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final WorldgenHarnessController controller;
        private final YALightEngineHolder yaLight;

        private RunContext(
                MinecraftServer server,
                ServerLevel level,
                WorldgenHarnessController controller
        ) {
            this.server = server;
            this.level = level;
            this.controller = controller;
            Object lightEngine = level.getChunkSource().getLightEngine();
            this.yaLight = lightEngine instanceof YALightEngineHolder holder ? holder : null;
        }

        private MinecraftServer server() {
            return this.server;
        }

        private ServerLevel level() {
            return this.level;
        }

        private WorldgenHarnessController controller() {
            return this.controller;
        }

        private YALightEngineHolder yaLight() {
            return this.yaLight;
        }

        private void logProgress(int completed, int total, ChunkPos pos) {
            if (completed < total && !this.controller.acquireProgressLog()) {
                return;
            }
            LOGGER.info("Light golden relight progress: world={} progress={} chunks={}/{} chunk=({}, {})",
                    this.controller.world(), completed * 100.0D / total, completed, total, pos.x, pos.z);
        }
    }
}
