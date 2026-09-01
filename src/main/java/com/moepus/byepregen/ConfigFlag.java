package com.moepus.byepregen;

import com.moepus.byepregen.config.Config;
import java.util.Objects;
import java.util.function.Predicate;

public enum ConfigFlag {
    ALWAYS(config -> true),
    DISABLE_WORLDGEN_FEATURES(config -> config.debug().disableWorldgenFeatures()),
    PLACED_FEATURES(config -> config.worldgen().placedFeatures().enabled()),
    PLACED_FEATURE_LOCAL_OPTIMIZATIONS(config -> config.worldgen().placedFeatures().enabled()
            || config.worldgen().placedFeatures().localOptimizations()),
    FLAT_CACHE_ACCESS(config -> config.worldgen().misc().flatCacheAccess()),
    FAST_CHUNK_TICKING(config -> config.server().fastChunkTicking().enabled()),
    MATERIALIZE_ARENA_LEVEL_CHUNK(
            config -> !config.worldgen().arena().runtime().server()
    );

    private final Predicate<Config> enabled;

    ConfigFlag(Predicate<Config> enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled(Config config) {
        return this.enabled.test(Objects.requireNonNull(config, "config"));
    }
}
