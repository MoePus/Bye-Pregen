package com.moepus.byepregen.test;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class TestWorldGen {
    public static final String ENABLED_PROPERTY = "byepregen.testWorldGen";
    private static final Logger LOGGER = LogUtils.getLogger();

    private TestWorldGen() {
    }

    public static void registerIfEnabled() {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            return;
        }
        LOGGER.info("ByePregen test worldgen is disabled in the 1.20.1 backport");
    }
}
