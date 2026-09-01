package com.moepus.byepregen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.harness.ChunkBounds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LightDiffOptionsTest {
    private static final String PREFIX = "byepregen.lightGolden.";
    private static final List<String> PROPERTIES = List.of(
            "maxMismatches", "minComparedLayers", "missingAsZero", "requireCompleteChunks",
            "minChunkCoverage", "minChunkX", "maxChunkX", "minChunkZ", "maxChunkZ"
    );
    private final Map<String, String> originalValues = new HashMap<>();

    @BeforeEach
    void rememberProperties() {
        PROPERTIES.forEach(name -> this.originalValues.put(name, System.getProperty(PREFIX + name)));
        PROPERTIES.forEach(name -> System.clearProperty(PREFIX + name));
    }

    @AfterEach
    void restoreProperties() {
        PROPERTIES.forEach(this::restoreProperty);
    }

    @Test
    void retainsExistingDefaults() {
        LightDiffOptions options = LightDiffOptions.fromProperties();

        assertEquals(50, options.maxIssues());
        assertEquals(1, options.minComparedLayers());
        assertFalse(options.missingAsZero());
        assertFalse(options.requireCompleteChunks());
        assertEquals(0.8D, options.minChunkCoverage());
        assertEquals(
                new ChunkBounds(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE),
                options.chunkBounds()
        );
    }

    @Test
    void parsesExplicitValuesAndBounds() {
        System.setProperty(PREFIX + "maxMismatches", "0x20");
        System.setProperty(PREFIX + "minComparedLayers", "4");
        System.setProperty(PREFIX + "missingAsZero", "TRUE");
        System.setProperty(PREFIX + "requireCompleteChunks", "True");
        System.setProperty(PREFIX + "minChunkCoverage", "0.75");
        System.setProperty(PREFIX + "minChunkX", "-2");
        System.setProperty(PREFIX + "maxChunkX", "2");
        System.setProperty(PREFIX + "minChunkZ", "-3");
        System.setProperty(PREFIX + "maxChunkZ", "3");

        LightDiffOptions options = LightDiffOptions.fromProperties();

        assertEquals(32, options.maxIssues());
        assertEquals(4, options.minComparedLayers());
        assertTrue(options.missingAsZero());
        assertTrue(options.requireCompleteChunks());
        assertEquals(0.75D, options.minChunkCoverage());
        assertEquals(new ChunkBounds(-2, 2, -3, 3), options.chunkBounds());
    }

    private void restoreProperty(String name) {
        String property = PREFIX + name;
        String original = this.originalValues.get(name);
        if (original == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, original);
        }
    }
}
