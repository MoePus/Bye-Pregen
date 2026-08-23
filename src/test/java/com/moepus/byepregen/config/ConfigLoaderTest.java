package com.moepus.byepregen.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConfigLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsMissingConfigWithCanonicalDefaultsAndComments() throws IOException {
        Path path = this.temporaryDirectory.resolve("config").resolve("byepregen.toml");

        Config config = new ConfigLoader(path).load();
        String output = Files.readString(path, StandardCharsets.UTF_8);

        assertTrue(config.worldgen().arena().enabled());
        assertTrue(config.worldgen().arena().densityColumnCompiler());
        assertTrue(config.worldgen().placedFeatures().memoizedDiskPlan());
        assertTrue(output.contains("[worldgen.arena]"));
        assertEquals(13, output.lines().filter(line -> line.endsWith("= \"Default\"")).count());
        assertEquals(13, output.lines().map(String::strip)
                .filter(line -> line.equals("# Default: True") || line.equals("# Default: False"))
                .count());
        assertTrue(output.contains("# Default: True"));
        assertTrue(output.contains("# Compiles final-density graphs"));
        assertTrue(output.contains("# All options in this file are read at startup and require a game restart."));
        assertTrue(output.contains("Performance patches for Minecraft's vanilla block-light and sky-light engines."));
        assertTrue(output.contains("making light"));
        assertTrue(output.contains("# also provide a small improvement to 1% low FPS."));
        assertTrue(output.contains("density-column-compiler = \"Default\""));
    }

    @Test
    void readsNestedValuesAndCanonicalizesDocument() throws IOException {
        Path path = this.temporaryDirectory.resolve("custom.toml");
        Files.writeString(path, """
                # custom comment
                unknown = true

                [worldgen.arena]
                enabled = "False"
                density-column-compiler = "True"
                client = "true"

                [worldgen.surface]
                biome-cache = false
                """, StandardCharsets.UTF_8);

        Config config = new ConfigLoader(path).load();
        String firstOutput = Files.readString(path, StandardCharsets.UTF_8);

        assertFalse(config.worldgen().arena().enabled());
        assertTrue(config.worldgen().arena().runtime().client());
        assertFalse(config.worldgen().surface().biomeCache());
        assertFalse(firstOutput.contains("custom comment"));
        assertFalse(firstOutput.contains("unknown ="));
        assertTrue(firstOutput.contains("[lighting.ya]"));
        assertTrue(firstOutput.contains("enabled = \"False\""));
        assertTrue(firstOutput.contains("density-column-compiler = \"True\""));
        assertTrue(firstOutput.contains("client = \"True\""));
        assertTrue(firstOutput.contains("biome-cache = \"False\""));

        new ConfigLoader(path).load();
        assertEquals(firstOutput, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void replacesWrongTypesAndMissingValuesWithDefaults() throws IOException {
        Path path = this.temporaryDirectory.resolve("wrong-type.toml");
        Files.writeString(path, """
                [worldgen.arena]
                enabled = "not-a-boolean"

                [chunk-saving]
                gc-free-worldgen = false
                """, StandardCharsets.UTF_8);

        Config config = new ConfigLoader(path).load();
        String output = Files.readString(path, StandardCharsets.UTF_8);

        assertTrue(config.worldgen().arena().enabled());
        assertFalse(config.chunkSaving().gcFreeWorldgen());
        assertTrue(config.chunkSaving().retainBuffer());
        assertTrue(output.contains("enabled = \"Default\""));
        assertTrue(output.contains("gc-free-worldgen = \"False\""));
        assertTrue(output.contains("retain-buffer = \"Default\""));
        assertFalse(output.contains("not-a-boolean"));
    }

    @Test
    void leavesInvalidTomlUntouchedAndUsesDefaults() throws IOException {
        Path path = this.temporaryDirectory.resolve("invalid.toml");
        String invalid = "[worldgen.arena\nenabled = false";
        Files.writeString(path, invalid, StandardCharsets.UTF_8);

        Config config = new ConfigLoader(path).load();

        assertTrue(config.worldgen().arena().enabled());
        assertEquals(invalid, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void ignoresLegacyJson() throws IOException {
        Path json = this.temporaryDirectory.resolve("byepregen.json");
        Path toml = this.temporaryDirectory.resolve("byepregen.toml");
        String legacy = "{\"enableArenaPalette\":false}";
        Files.writeString(json, legacy, StandardCharsets.UTF_8);

        Config config = new ConfigLoader(toml).load();

        assertTrue(config.worldgen().arena().enabled());
        assertTrue(Files.exists(toml));
        assertEquals(legacy, Files.readString(json, StandardCharsets.UTF_8));
    }

    @Test
    void readsAndWritesUtf8Paths() throws IOException {
        Path path = this.temporaryDirectory.resolve("配置.toml");

        assertTrue(new ConfigLoader(path).save(Config.defaults()));
        assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains("[chunk-saving]"));
    }

    @Test
    void reportsWriteFailureAndRemovesTemporaryFile() throws IOException {
        Path directory = Files.createDirectory(this.temporaryDirectory.resolve("directory-target"));

        assertFalse(new ConfigLoader(directory).save(Config.defaults()));
        assertFalse(Files.exists(this.temporaryDirectory.resolve("directory-target.tmp")));
    }
}
