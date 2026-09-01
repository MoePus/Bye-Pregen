package com.moepus.byepregen.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestConfigOverrideTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    @AfterEach
    void clearTestProperties() {
        List<String> names = System.getProperties().stringPropertyNames().stream()
                .filter(name -> name.equals(TestConfigOverride.ENABLE_PROPERTY)
                        || name.startsWith(TestConfigOverride.OPTION_PREFIX))
                .toList();
        names.forEach(System::clearProperty);
    }

    @Test
    void remainsInactiveWithoutMarkerOrValues() {
        assertTrue(TestConfigOverride.load().isEmpty());
    }

    @Test
    void activeEmptyOverrideUsesConfigDefaults() {
        enableTestConfig();

        Config config = TestConfigOverride.load().orElseThrow();

        assertTrue(config.worldgen().arena().enabled());
        assertFalse(config.lighting().ya().enabled());
        assertEquals(BooleanSetting.DEFAULT, config.worldgen().arena().enabledSetting());
    }

    @Test
    void appliesExplicitAndDefaultValuesThroughConfigLoader() {
        enableTestConfig();
        setOption("worldgen.arena.enabled", "False");
        setOption("worldgen.arena.density-column-compiler", "Default");
        setOption("worldgen.placed-features.local-optimizations", "False");
        setOption("worldgen.misc.flat-cache-access", "False");
        setOption("lighting.ya.enabled", "true");

        Config config = TestConfigOverride.load().orElseThrow();

        assertFalse(config.worldgen().arena().enabled());
        assertTrue(config.worldgen().arena().densityColumnCompiler());
        assertFalse(config.worldgen().placedFeatures().localOptimizations());
        assertFalse(config.worldgen().misc().flatCacheAccess());
        assertTrue(config.lighting().ya().enabled());
        assertEquals(BooleanSetting.DEFAULT, config.worldgen().arena().densityColumnCompilerSetting());
    }

    @Test
    void configManagerDoesNotReadOrRewriteFileInTestMode() throws Exception {
        enableTestConfig();
        setOption("server.fast-chunk-ticking.enabled", "True");
        Path path = this.temporaryDirectory.resolve("config").resolve("byepregen.toml");
        Files.createDirectories(path.getParent());
        String existing = "[server.fast-chunk-ticking]\nenabled = \"False\"\n";
        Files.writeString(path, existing);

        Config config = ConfigManager.load(path);

        assertTrue(config.server().fastChunkTicking().enabled());
        assertEquals(existing, Files.readString(path));
    }

    private static void enableTestConfig() {
        System.setProperty(TestConfigOverride.ENABLE_PROPERTY, "true");
    }

    private static void setOption(String path, String value) {
        System.setProperty(TestConfigOverride.OPTION_PREFIX + path, value);
    }
}
