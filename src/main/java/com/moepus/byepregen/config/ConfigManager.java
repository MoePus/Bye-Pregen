package com.moepus.byepregen.config;

import java.nio.file.Path;
import java.util.Objects;

public final class ConfigManager {
    private static volatile Config config;
    private static volatile Path path;

    private ConfigManager() {
    }

    public static synchronized Config initialize(Path configPath) {
        Path normalized = Objects.requireNonNull(configPath, "configPath").toAbsolutePath().normalize();
        if (config != null) {
            if (!path.equals(normalized)) {
                throw new IllegalStateException("ByePregen config already loaded from " + path);
            }
            return config;
        }
        Config loaded = load(normalized);
        path = normalized;
        config = loaded;
        return loaded;
    }

    static Config load(Path configPath) {
        return TestConfigOverride.load()
                .orElseGet(() -> new ConfigLoader(configPath).load());
    }

    public static Config getConfig() {
        Config current = config;
        if (current == null) {
            throw new IllegalStateException("ByePregen config has not been initialized");
        }
        return current;
    }

    public static Path path() {
        Path current = path;
        if (current == null) {
            throw new IllegalStateException("ByePregen config has not been initialized");
        }
        return current;
    }
}
