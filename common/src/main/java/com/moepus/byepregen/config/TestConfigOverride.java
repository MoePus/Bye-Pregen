package com.moepus.byepregen.config;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;

final class TestConfigOverride {
    static final String ENABLE_PROPERTY = "byepregen.testConfig";
    static final String OPTION_PREFIX = ENABLE_PROPERTY + ".";

    private TestConfigOverride() {
    }

    static Optional<Config> load() {
        Properties properties = System.getProperties();
        Map<String, String> values = optionValues(properties);
        String enabledValue = properties.getProperty(ENABLE_PROPERTY);
        if (enabledValue == null) {
            requireNoValuesWithoutMarker(values);
            return Optional.empty();
        }

        boolean enabled = parseEnabled(enabledValue);
        if (!enabled) {
            requireNoValuesWithoutMarker(values);
            return Optional.empty();
        }
        return Optional.of(ConfigLoader.fromOverrides(values));
    }

    private static Map<String, String> optionValues(Properties properties) {
        Map<String, String> values = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(OPTION_PREFIX)) {
                values.put(name.substring(OPTION_PREFIX.length()), properties.getProperty(name));
            }
        }
        return values;
    }

    private static boolean parseEnabled(String value) {
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        throw new IllegalArgumentException(ENABLE_PROPERTY + " must be true or false");
    }

    private static void requireNoValuesWithoutMarker(Map<String, String> values) {
        if (!values.isEmpty()) {
            throw new IllegalArgumentException(
                    "ByePregen test config options require -D" + ENABLE_PROPERTY + "=true");
        }
    }
}
