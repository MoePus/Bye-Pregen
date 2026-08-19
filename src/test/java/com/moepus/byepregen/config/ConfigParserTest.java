package com.moepus.byepregen.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConfigParserTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsMissingConfigWithDefaults() throws IOException {
        Path path = this.temporaryDirectory.resolve("config").resolve("byepregen.json");

        Config config = new ConfigParser(path).load();

        assertTrue(config.enableArenaPalette);
        assertTrue(config.enableDensityColumnCompiler);
        assertTrue(Files.exists(path));
        assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains("enableArenaPalette"));
    }

    @Test
    void replacesEmptyConfigWithDefaults() throws IOException {
        Path path = this.temporaryDirectory.resolve("empty.json");
        Files.writeString(path, "", StandardCharsets.UTF_8);

        Config config = new ConfigParser(path).load();

        assertTrue(config.enableGcFreeWorldgenSave);
        assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains("enableGcFreeWorldgenSave"));
    }

    @Test
    void fallsBackToDefaultsForInvalidJsonWithoutOverwritingInput() throws IOException {
        Path path = this.temporaryDirectory.resolve("invalid.json");
        String invalid = "{not-json";
        Files.writeString(path, invalid, StandardCharsets.UTF_8);

        Config config = new ConfigParser(path).load();

        assertTrue(config.enableSurfaceRuleCompiler);
        assertTrue(Files.readString(path, StandardCharsets.UTF_8).equals(invalid));
    }

    @Test
    void readsAndWritesUtf8() throws IOException {
        Path path = this.temporaryDirectory.resolve("配置.json");
        Files.writeString(path, "{\n  \"说明\": \"配置\",\n  \"enableArenaPalette\": false\n}",
                StandardCharsets.UTF_8);

        Config config = new ConfigParser(path).load();

        assertFalse(config.enableArenaPalette);
        assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains("enableArenaPalette"));
    }

    @Test
    void reportsWriteFailure() throws IOException {
        Path directory = Files.createDirectory(this.temporaryDirectory.resolve("directory-target"));

        assertFalse(new ConfigParser(directory).save(new Config()));
    }
}
