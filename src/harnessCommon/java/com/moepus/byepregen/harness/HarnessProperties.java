package com.moepus.byepregen.harness;

public final class HarnessProperties {
    private HarnessProperties() {
    }

    public static String get(String property, String fallback) {
        String value = System.getProperty(property, fallback);
        if (value == null || value.isBlank()) {
            throw invalid(property, value, "a non-blank value", null);
        }
        return value;
    }

    public static boolean getBoolean(String property, boolean fallback) {
        String value = get(property, Boolean.toString(fallback));
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw invalid(property, value, "true or false", null);
    }

    public static int getInt(String property, int fallback) {
        String value = get(property, Integer.toString(fallback));
        try {
            return Integer.decode(value);
        } catch (NumberFormatException exception) {
            throw invalid(property, value, "an integer", exception);
        }
    }

    public static long getLong(String property, long fallback) {
        String value = get(property, Long.toString(fallback));
        try {
            return Long.decode(value);
        } catch (NumberFormatException exception) {
            throw invalid(property, value, "an integer", exception);
        }
    }

    public static double getDouble(String property, double fallback) {
        String value = get(property, Double.toString(fallback));
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw invalid(property, value, "a finite number", null);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid(property, value, "a finite number", exception);
        }
    }

    private static IllegalArgumentException invalid(
            String property,
            String value,
            String expected,
            Throwable cause
    ) {
        return new IllegalArgumentException(
                "Invalid -D" + property + "=" + value + "; expected " + expected,
                cause
        );
    }
}
