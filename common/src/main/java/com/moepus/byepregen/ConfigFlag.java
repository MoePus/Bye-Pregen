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
    // Removing PalettedContainer's lock has to agree with Lithium's chunk.no_locking option: with
    // Lithium installed the LockedPalette mixin is skipped instead, so the two never disagree.
    PALETTE_LOCK(config -> config.worldgen().misc().paletteLock()),
    LEAF_WORLDGEN_TICK(config -> config.worldgen().misc().leafWorldgenTick()),
    FAST_CHUNK_TICKING(config -> config.server().fastChunkTicking().enabled()),
    MATERIALIZE_ARENA_LEVEL_CHUNK(
            config -> !config.worldgen().arena().runtime().server()
    ),
    CLIENT_ARENA(config -> config.worldgen().arena().runtime().client());

    private final Predicate<Config> enabled;

    ConfigFlag(Predicate<Config> enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled(Config config) {
        return this.enabled.test(Objects.requireNonNull(config, "config"));
    }
}
