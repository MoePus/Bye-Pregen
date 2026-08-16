package com.moepus.byepregen.test;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

final class WorldgenHarnessProperties {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "byepregen.testWorldGen.";

    private WorldgenHarnessProperties() {
    }

    static String get(String name, String fallback) {
        return System.getProperty(PREFIX + name, fallback);
    }

    static double getDouble(String name, double fallback) {
        String value = get(name, Double.toString(fallback));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            LOGGER.warn("Invalid {}{}={}, using {}", PREFIX, name, value, fallback);
            return fallback;
        }
    }

    static long getLong(String name, long fallback) {
        String value = get(name, Long.toString(fallback));
        try {
            return Long.decode(value);
        } catch (NumberFormatException exception) {
            LOGGER.warn("Invalid {}{}={}, using {}", PREFIX, name, value, fallback);
            return fallback;
        }
    }

    static boolean getBoolean(String name, boolean fallback) {
        return Boolean.parseBoolean(get(name, Boolean.toString(fallback)));
    }
}
