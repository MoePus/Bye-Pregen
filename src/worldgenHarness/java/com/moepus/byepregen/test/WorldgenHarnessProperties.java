package com.moepus.byepregen.test;

final class WorldgenHarnessProperties {
    private static final String ENABLED_PROPERTY = "byepregen.testWorldGen";
    private static final String PREFIX = "byepregen.testWorldGen.";

    private WorldgenHarnessProperties() {
    }

    static String get(String name, String fallback) {
        String property = PREFIX + name;
        String value = System.getProperty(property, fallback);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Invalid -D" + property + "; value must not be blank");
        }
        return value;
    }

    static boolean isEnabled() {
        String value = System.getProperty(ENABLED_PROPERTY);
        return value != null && parseBoolean(ENABLED_PROPERTY, value);
    }

    static double getDouble(String name, double fallback) {
        String value = get(name, Double.toString(fallback));
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw invalid(name, value, null);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid(name, value, exception);
        }
    }

    static long getLong(String name, long fallback) {
        String value = get(name, Long.toString(fallback));
        try {
            return Long.decode(value);
        } catch (NumberFormatException exception) {
            throw invalid(name, value, exception);
        }
    }

    static boolean getBoolean(String name, boolean fallback) {
        return parseBoolean(PREFIX + name, get(name, Boolean.toString(fallback)));
    }

    private static boolean parseBoolean(String property, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Invalid -D" + property + "=" + value + "; expected true or false"
        );
    }

    private static IllegalArgumentException invalid(
            String name,
            String value,
            NumberFormatException cause
    ) {
        return new IllegalArgumentException(
                "Invalid -D" + PREFIX + name + "=" + value + "; expected a numeric value",
                cause
        );
    }
}
