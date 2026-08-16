package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.HarnessResultFile;
import com.moepus.byepregen.config.ConfigParser;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
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
            require(ConfigParser.getConfig().enableFastTickChunks, "enableFastTickChunks is false");
            require(ModEnvironment.isModLoaded("c2me"), "C2ME is not loaded");
            require(ModEnvironment.isModLoaded("lithium"), "Lithium is not loaded");
            Field tickingChunks = ServerChunkCache.class.getDeclaredField("byepregen$tickingChunks");
            require(tickingChunks.getType() == LevelChunk[].class, "fast tick field has the wrong type");
            Method weather = ServerLevel.class.getDeclaredMethod(
                    "byepregen$tickPrecipitation", LevelChunk.class, BlockPos.class);
            require(weather.getReturnType() == void.class, "weather redirect helper has the wrong return type");
            verificationSummary = "c2me=true,lithium=true,chunkTickMixin=true,weatherMixin=true";
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

    private static void writeFailure(Throwable throwable) {
        HarnessResultFile.writeFailure(RESULT_PROPERTY, throwable);
    }
}
