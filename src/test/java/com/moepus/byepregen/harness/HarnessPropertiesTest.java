package com.moepus.byepregen.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HarnessPropertiesTest {
    private static final String BOOLEAN = "byepregen.harnessTest.boolean";
    private static final String INTEGER = "byepregen.harnessTest.integer";
    private static final String LONG = "byepregen.harnessTest.long";
    private static final String DOUBLE = "byepregen.harnessTest.double";
    private static final String STRING = "byepregen.harnessTest.string";
    private static final String BOUNDS_PREFIX = "byepregen.harnessTest.bounds";
    private static final List<String> PROPERTIES = List.of(
            BOOLEAN, INTEGER, LONG, DOUBLE, STRING,
            BOUNDS_PREFIX + ".minChunkX", BOUNDS_PREFIX + ".maxChunkX",
            BOUNDS_PREFIX + ".minChunkZ", BOUNDS_PREFIX + ".maxChunkZ"
    );
    private final Map<String, String> originalValues = new HashMap<>();

    @BeforeEach
    void rememberProperties() {
        PROPERTIES.forEach(key -> this.originalValues.put(key, System.getProperty(key)));
        PROPERTIES.forEach(System::clearProperty);
    }

    @AfterEach
    void restoreProperties() {
        PROPERTIES.forEach(this::restoreProperty);
    }

    @Test
    void returnsDefaultsWhenPropertiesAreMissing() {
        assertTrue(HarnessProperties.getBoolean(BOOLEAN, true));
        assertEquals(12, HarnessProperties.getInt(INTEGER, 12));
        assertEquals(23L, HarnessProperties.getLong(LONG, 23L));
        assertEquals(0.8D, HarnessProperties.getDouble(DOUBLE, 0.8D));
        assertEquals("default", HarnessProperties.get(STRING, "default"));
    }

    @Test
    void parsesSupportedValuesWithoutLosingIntegerDecodeBehavior() {
        System.setProperty(BOOLEAN, "False");
        System.setProperty(INTEGER, "0x20");
        System.setProperty(LONG, "-010");
        System.setProperty(DOUBLE, "1.25e-2");

        assertFalse(HarnessProperties.getBoolean(BOOLEAN, true));
        assertEquals(32, HarnessProperties.getInt(INTEGER, 0));
        assertEquals(-8L, HarnessProperties.getLong(LONG, 0L));
        assertEquals(0.0125D, HarnessProperties.getDouble(DOUBLE, 0.0D));
    }

    @Test
    void rejectsBlankAndInvalidValuesWithThePropertyName() {
        System.setProperty(STRING, " ");
        System.setProperty(BOOLEAN, "yes");
        System.setProperty(INTEGER, "1.5");

        assertInvalid(STRING, () -> HarnessProperties.get(STRING, "value"));
        assertInvalid(BOOLEAN, () -> HarnessProperties.getBoolean(BOOLEAN, false));
        assertInvalid(INTEGER, () -> HarnessProperties.getInt(INTEGER, 0));
    }

    @Test
    void rejectsNonFiniteAndMalformedNumbers() {
        System.setProperty(DOUBLE, "NaN");
        System.setProperty(LONG, "large");

        assertInvalid(DOUBLE, () -> HarnessProperties.getDouble(DOUBLE, 0.0D));
        assertInvalid(LONG, () -> HarnessProperties.getLong(LONG, 0L));

        System.setProperty(DOUBLE, "Infinity");
        assertInvalid(DOUBLE, () -> HarnessProperties.getDouble(DOUBLE, 0.0D));
    }

    @Test
    void chunkBoundsUseTheSameStrictIntegerParser() {
        System.setProperty(BOUNDS_PREFIX + ".minChunkX", "-0x10");
        System.setProperty(BOUNDS_PREFIX + ".maxChunkX", "15");
        System.setProperty(BOUNDS_PREFIX + ".minChunkZ", "-8");
        System.setProperty(BOUNDS_PREFIX + ".maxChunkZ", "07");

        assertEquals(new ChunkBounds(-16, 15, -8, 7), ChunkBounds.fromSystemProperties(BOUNDS_PREFIX));

        System.setProperty(BOUNDS_PREFIX + ".maxChunkX", "far");
        assertInvalid(
                BOUNDS_PREFIX + ".maxChunkX",
                () -> ChunkBounds.fromSystemProperties(BOUNDS_PREFIX)
        );
    }

    private static void assertInvalid(String property, Runnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action::run);
        assertTrue(exception.getMessage().contains("-D" + property + "="));
    }

    private void restoreProperty(String property) {
        String original = this.originalValues.get(property);
        if (original == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, original);
        }
    }
}
