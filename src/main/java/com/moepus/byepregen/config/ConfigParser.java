package com.moepus.byepregen.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class ConfigParser {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "byepregen.json";

    private static Config config;
    private final Path path;

    public ConfigParser(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public static Config getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }

    public static void loadConfig() {
        config = runtimeParser().load();
    }

    public static void saveConfig() {
        Config value = config;
        if (value == null) {
            value = getConfig();
        }
        runtimeParser().save(value);
    }

    public static void setConfig(Config replacement) {
        config = Objects.requireNonNull(replacement, "replacement");
    }

    public Config load() {
        if (Files.notExists(this.path)) {
            Config defaults = new Config();
            this.save(defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(this.path, StandardCharsets.UTF_8)) {
            Config loaded = GSON.fromJson(reader, Config.class);
            Config result = loaded == null ? new Config() : loaded;
            this.save(result);
            return result;
        } catch (JsonIOException | JsonSyntaxException | IOException exception) {
            LOGGER.warn("Failed to load ByePregen config {}; using defaults", this.path, exception);
            return new Config();
        }
    }

    public boolean save(Config value) {
        Objects.requireNonNull(value, "value");
        try {
            Path parent = this.path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to create ByePregen config directory for {}", this.path, exception);
            return false;
        }
        try (Writer writer = Files.newBufferedWriter(this.path, StandardCharsets.UTF_8)) {
            PRETTY_GSON.toJson(value, writer);
            return true;
        } catch (JsonIOException | IOException exception) {
            LOGGER.warn("Failed to save ByePregen config {}", this.path, exception);
            return false;
        }
    }

    public Path path() {
        return this.path;
    }

    private static ConfigParser runtimeParser() {
        return new ConfigParser(FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME));
    }
}
