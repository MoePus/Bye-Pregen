package com.moepus.byepregen.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConfigManager {
    private static volatile Config config;

    private ConfigManager() {
    }

    public static synchronized Config initialize(Path configPath, Map<String, Set<String>> conflicts) {
        if (config != null) {
            return config;
        }
        Config loaded = load(Objects.requireNonNull(configPath, "configPath"));
        config = ConfigLoader.applyConflicts(loaded, conflicts);
        return config;
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
