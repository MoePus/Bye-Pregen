package com.moepus.byepregen.test;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class TestWorldGen {
    public static final String ENABLED_PROPERTY = "byepregen.testWorldGen";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHUNKY_PROVIDER = "org.popcraft.chunky.ChunkyProvider";

    private TestWorldGen() {
    }

    public static void registerIfEnabled() {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            return;
        }
        String mode = System.getProperty("byepregen.testWorldGen.mode", "chunky");
        if (ArenaPaletteDifferential.MODE.equals(mode)) {
            ArenaPaletteDifferential.register();
            return;
        }
        if (ChunkNbtParity.MODE.equals(mode)) {
            ChunkNbtParity.register();
            return;
        }
        if (!hasClass(CHUNKY_PROVIDER)) {
            LOGGER.error("ByePregen test worldgen requested, but Chunky is not loaded");
            return;
        }
        ChunkyWorldGenDriver.register();
    }

    private static boolean hasClass(String className) {
        try {
            Class.forName(className, false, TestWorldGen.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
