package com.moepus.byepregen.config;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

enum ConfigOption {
    DEBUG_DISABLE_WORLDGEN_FEATURES("debug.disable-worldgen-features"),
    PLACED_FEATURES_ENABLED("worldgen.placed-features.enabled"),
    MEMOIZED_DISK_PLAN("worldgen.placed-features.memoized-disk-plan"),
    PLACED_FEATURE_LOCAL_OPTIMIZATIONS("worldgen.placed-features.local-optimizations"),
    ARENA_ENABLED("worldgen.arena.enabled"),
    DENSITY_COLUMN_COMPILER("worldgen.arena.density-column-compiler"),
    SERVER_RUNTIME_ARENA("worldgen.arena.server-runtime"),
    CLIENT_ARENA("worldgen.arena.client"),
    SURFACE_RULE_COMPILER("worldgen.surface.rule-compiler"),
    SURFACE_BIOME_CACHE("worldgen.surface.biome-cache"),
    FLAT_CACHE_ACCESS("worldgen.misc.flat-cache-access"),
    FAST_CHUNK_TICKING("server.fast-chunk-ticking.enabled"),
    GC_FREE_WORLDGEN("chunk-saving.gc-free-worldgen"),
    RETAIN_BUFFER("chunk-saving.retain-buffer"),
    YA_LIGHT("lighting.ya.enabled");

    private static final Map<String, ConfigOption> BY_PATH = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ConfigOption::path, Function.identity()));

    private final String path;

    ConfigOption(String path) {
        this.path = path;
    }

    static ConfigOption fromPath(String path) {
        ConfigOption option = BY_PATH.get(path);
        if (option == null) {
            throw new IllegalArgumentException("Unknown ByePregen config option: " + path);
        }
        return option;
    }

    String path() {
        return this.path;
    }
}
