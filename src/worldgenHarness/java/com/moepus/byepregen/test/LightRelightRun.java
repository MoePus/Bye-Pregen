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

    static void run(MinecraftServer server) {
        if (!"minecraft:overworld".equals(ChunkyWorldGenDriver.WORLD)) {
            ChunkyWorldGenDriver.failAndStop(server,
                    "Relight mode currently supports only minecraft:overworld, got "
                            + ChunkyWorldGenDriver.WORLD);
            return;
        }

        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            ChunkyWorldGenDriver.failAndStop(server, "Overworld is not loaded");
            return;
        }

        Path chunkList = server.getWorldPath(LevelResource.ROOT)
                .resolve(LightGoldenPrepareRelight.CHUNK_LIST_FILE);
        List<ChunkPos> chunks;
        try {
            chunks = readChunkList(chunkList);
        } catch (IOException exception) {
            LOGGER.error("Failed to read light golden relight chunk list {}", chunkList, exception);
            ChunkyWorldGenDriver.failAndStop(server, "Failed to read relight chunk list: " + chunkList);
            return;
        }

        if (chunks.isEmpty()) {
            ChunkyWorldGenDriver.failAndStop(server, "Relight chunk list is empty: " + chunkList);
            return;
        }

        LOGGER.info("Started light golden relight: world={} chunks={} list={}",
                ChunkyWorldGenDriver.WORLD, chunks.size(), chunkList);
        YALightEngineHolder yaLight = ChunkyWorldGenDriver.yaLightEngineHolder(level);
        if (yaLight != null) {
            yaLight.byepregen$getYALightEngine().setDeclaredFreshOwnerDomain(chunks);
        }
        loadChunks(server, level, chunks, yaLight);
    }

    private static void loadChunks(
            MinecraftServer server,
            ServerLevel level,
            List<ChunkPos> chunks,
            YALightEngineHolder yaLight
    ) {
        int completed = 0;
        long startedNanos = System.nanoTime();
        long getChunkCompletedNanos = startedNanos;
        long cleanupStartedNanos;
        long cleanupCompletedNanos;
        try {
            for (ChunkPos pos : chunks) {
                level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
                if (completed + 1 == chunks.size()) {
                    getChunkCompletedNanos = System.nanoTime();
                }
                ++completed;
                logProgress(completed, chunks.size(), pos);
            }
        } catch (Throwable throwable) {
            LOGGER.error("Light golden relight failed after {}/{} chunks", completed, chunks.size(), throwable);
            ChunkyWorldGenDriver.failAndStop(server,
                    "Relight failed after " + completed + "/" + chunks.size() + " chunks");
            return;
        } finally {
            cleanupStartedNanos = System.nanoTime();
            if (yaLight != null) {
                yaLight.byepregen$getYALightEngine().clearDeclaredFreshOwnerDomain();
            }
            cleanupCompletedNanos = System.nanoTime();
        }

        logCompletion(chunks.size(), startedNanos, getChunkCompletedNanos,
                cleanupStartedNanos, cleanupCompletedNanos);
        ChunkyWorldGenDriver.succeedAndStop(server, "chunks=" + chunks.size());
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
            chunks.add(new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
        }
        return chunks;
    }

    private static void logProgress(int completed, int total, ChunkPos pos) {
        long now = System.nanoTime();
        long next = ChunkyWorldGenDriver.NEXT_PROGRESS_LOG.get();
        if (completed < total && (now < next || !ChunkyWorldGenDriver.NEXT_PROGRESS_LOG.compareAndSet(
                next, now + ChunkyWorldGenDriver.PROGRESS_LOG_NANOS))) {
            return;
        }
        LOGGER.info("Light golden relight progress: world={} progress={} chunks={}/{} chunk=({}, {})",
                ChunkyWorldGenDriver.WORLD, completed * 100.0D / total, completed, total, pos.x, pos.z);
    }

    private static void logCompletion(
            int chunks,
            long startedNanos,
            long getChunkCompletedNanos,
            long cleanupStartedNanos,
            long cleanupCompletedNanos
    ) {
        long lifecycleCompletedNanos = System.nanoTime();
        double getChunkSeconds = (getChunkCompletedNanos - startedNanos) / 1_000_000_000.0D;
        double cleanupSeconds = (cleanupCompletedNanos - cleanupStartedNanos) / 1_000_000_000.0D;
        double lifecycleSeconds = (lifecycleCompletedNanos - startedNanos) / 1_000_000_000.0D;
        LOGGER.info(
                "Light golden relight completed: world={} chunks={} getChunkSeconds={} "
                        + "cleanupSeconds={} lifecycleSeconds={}",
                ChunkyWorldGenDriver.WORLD,
                chunks,
                getChunkSeconds,
                cleanupSeconds,
                lifecycleSeconds
        );
    }
}
