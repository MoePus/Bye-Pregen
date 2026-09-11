package com.moepus.byepregen.config;

import java.nio.file.Path;
import java.util.Objects;

public final class ConfigManager {
    private static volatile Config config;

    private ConfigManager() {
    }

    public static synchronized Config initialize(Path configPath) {
        if (config != null) {
            return config;
        }
        Config loaded = load(Objects.requireNonNull(configPath, "configPath"));
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
}
