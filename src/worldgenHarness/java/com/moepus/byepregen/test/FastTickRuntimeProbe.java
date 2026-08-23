package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.HarnessResultFile;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import com.moepus.byepregen.server.tick.ChunkTickPermutationIterator;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;

final class FastTickRuntimeProbe {
    private static final String RESULT_PROPERTY = "byepregen.fastTickRuntimeResult";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static String verificationSummary;

    private FastTickRuntimeProbe() {
    }

    static boolean verifyBeforeWorldgen() {
        if (!requested()) {
            return true;
        }
        try {
            require(ConfigManager.getConfig().server().fastChunkTicking().enabled(),
                    "server.fast-chunk-ticking.enabled is false");
            require(ModEnvironment.isModLoaded("c2me"), "C2ME is not loaded");
            require(ModEnvironment.isModLoaded("lithium"), "Lithium is not loaded");
            require(!ModEnvironment.isModLoaded("servercore"), "ServerCore is unexpectedly loaded");
            Field permutation = ServerChunkCache.class.getDeclaredField("byepregen$chunkTickPermutation");
            require(permutation.getType() == ChunkTickPermutationIterator.class,
                    "chunk tick permutation field has the wrong type");
            Field tickingChunks = ServerChunkCache.class.getDeclaredField("byepregen$cachedTickingChunks");
            require(tickingChunks.getType() == ArrayList.class,
                    "tick list fallback field has the wrong type");
            Field currentChunk = ServerLevel.class.getDeclaredField("byepregen$currentTickChunk");
            require(currentChunk.getType() == LevelChunk.class, "weather scope field has the wrong type");
            Method cacheSeed = ServerChunkCache.class.getDeclaredMethod(
                    "byepregen$storeInCache", long.class, ChunkAccess.class, ChunkStatus.class);
            require(cacheSeed.getReturnType() == void.class, "cache seed invoker has the wrong return type");
            require(hasMethodPrefix(ServerLevel.class, "tickChunk$mixinlite$scope$"),
                    "tickChunk MethodScope was not applied");
            require(hasMethodContaining(NaturalSpawner.class, "byepregen$seedSpawnChunkCache"),
                    "spawnForChunk cache seed injection was not applied");
            verificationSummary = "c2me=true,lithium=true,servercore=false,iterationMixin=true,weatherScope=true,"
                    + "cacheSeeding=true,fallbackList=true";
            LOGGER.info("BYEPREGEN_FAST_TICK_RUNTIME_APPLIED {}", verificationSummary);
            return true;
        } catch (Throwable throwable) {
            writeFailure(throwable);
            LOGGER.error("BYEPREGEN_FAST_TICK_RUNTIME_FAIL", throwable);
            return false;
        }
    }

    static boolean completeAfterWorldgen() {
        if (!requested()) {
            return true;
        }
        try {
            require(verificationSummary != null, "fast tick mixins were not verified before worldgen");
            HarnessResultFile.write(RESULT_PROPERTY, "PASS\n" + verificationSummary + "\nradius=16\n");
            LOGGER.info("BYEPREGEN_FAST_TICK_RUNTIME_PASS");
            return true;
        } catch (Throwable throwable) {
            writeFailure(throwable);
            LOGGER.error("BYEPREGEN_FAST_TICK_RUNTIME_FAIL", throwable);
            return false;
        }
    }

    private static boolean requested() {
        String result = System.getProperty(RESULT_PROPERTY);
        return result != null && !result.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean hasMethodPrefix(Class<?> type, String prefix) {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> method.getName().startsWith(prefix));
    }

    private static boolean hasMethodContaining(Class<?> type, String fragment) {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> method.getName().contains(fragment));
    }

    private static void writeFailure(Throwable throwable) {
        HarnessResultFile.writeFailure(RESULT_PROPERTY, throwable);
    }
}
