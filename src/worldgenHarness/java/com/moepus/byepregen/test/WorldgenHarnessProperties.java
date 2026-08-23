package com.moepus.byepregen.test;

import com.moepus.byepregen.harness.HarnessProperties;

final class WorldgenHarnessProperties {
    private static final String ENABLED_PROPERTY = "byepregen.testWorldGen";
    private static final String PREFIX = "byepregen.testWorldGen.";

    private WorldgenHarnessProperties() {
    }

    static String get(String name, String fallback) {
        return HarnessProperties.get(PREFIX + name, fallback);
    }

    static boolean isEnabled() {
        String value = System.getProperty(ENABLED_PROPERTY);
        return value != null && HarnessProperties.getBoolean(ENABLED_PROPERTY, false);
    }

    static double getDouble(String name, double fallback) {
        return HarnessProperties.getDouble(PREFIX + name, fallback);
    }

    static long getLong(String name, long fallback) {
        return HarnessProperties.getLong(PREFIX + name, fallback);
    }

    static boolean getBoolean(String name, boolean fallback) {
        return HarnessProperties.getBoolean(PREFIX + name, fallback);
    }
}
