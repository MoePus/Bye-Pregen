package com.moepus.byepregen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorldgenHarnessPropertiesTest {
    private static final String ENABLED = "byepregen.testWorldGen";
    private static final String BOOLEAN = "byepregen.testWorldGen.lightFuzzProbes";
    private static final String DOUBLE = "byepregen.testWorldGen.radius";
    private static final String LONG = "byepregen.testWorldGen.lightFuzzSeed";
    private static final List<String> PROPERTIES = List.of(ENABLED, BOOLEAN, DOUBLE, LONG);
    private final Map<String, String> originalValues = new HashMap<>();

    @BeforeEach
    void rememberProperties() {
        PROPERTIES.forEach(property -> this.originalValues.put(property, System.getProperty(property)));
        PROPERTIES.forEach(System::clearProperty);
    }

    @AfterEach
    void restoreProperties() {
        PROPERTIES.forEach(this::restoreProperty);
    }

    private void restoreProperty(String property) {
        String original = this.originalValues.get(property);
        if (original == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, original);
        }
    }

    @Test
    void disabledWhenMasterPropertyIsMissing() {
        assertFalse(WorldgenHarnessProperties.isEnabled());
    }

    @Test
    void parsesExplicitBooleanValuesCaseInsensitively() {
        System.setProperty(ENABLED, "TRUE");
        System.setProperty(BOOLEAN, "False");

        assertTrue(WorldgenHarnessProperties.isEnabled());
        assertFalse(WorldgenHarnessProperties.getBoolean("lightFuzzProbes", true));
    }

    @Test
    void parsesDecimalAndDecodedIntegerValues() {
        System.setProperty(DOUBLE, "32.5");
        System.setProperty(LONG, "0x10");

        assertEquals(32.5D, WorldgenHarnessProperties.getDouble("radius", 1.0D));
        assertEquals(16L, WorldgenHarnessProperties.getLong("lightFuzzSeed", 1L));
    }

}
