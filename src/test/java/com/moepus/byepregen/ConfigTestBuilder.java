package com.moepus.byepregen;

import com.moepus.byepregen.config.Config;

final class ConfigTestBuilder {
    private boolean disableWorldgenFeatures;
    private boolean placedFeatures;
    private boolean memoizedDiskPlan = true;
    private boolean arena = true;
    private boolean densityColumnCompiler = true;
    private boolean serverRuntimeArena;
    private boolean surfaceBiomeCache = true;
    private boolean fastChunkTicking;
    private boolean gcFreeWorldgen = true;
    private boolean retainBuffer = true;
    private boolean yaLight;

    ConfigTestBuilder disableWorldgenFeatures(boolean value) {
        this.disableWorldgenFeatures = value;
        return this;
    }

    ConfigTestBuilder placedFeatures(boolean value) {
        this.placedFeatures = value;
        return this;
    }

    ConfigTestBuilder arena(boolean value) {
        this.arena = value;
        return this;
    }

    ConfigTestBuilder densityColumnCompiler(boolean value) {
        this.densityColumnCompiler = value;
        return this;
    }

    ConfigTestBuilder serverRuntimeArena(boolean value) {
        this.serverRuntimeArena = value;
        return this;
    }

    ConfigTestBuilder surfaceBiomeCache(boolean value) {
        this.surfaceBiomeCache = value;
        return this;
    }

    ConfigTestBuilder gcFreeWorldgen(boolean value) {
        this.gcFreeWorldgen = value;
        return this;
    }

    ConfigTestBuilder yaLight(boolean value) {
        this.yaLight = value;
        return this;
    }

    Config build() {
        Config.Worldgen worldgen = new Config.Worldgen(
                new Config.PlacedFeatures(this.placedFeatures, this.memoizedDiskPlan),
                new Config.Arena(this.arena, this.densityColumnCompiler,
                        new Config.ArenaRuntime(this.serverRuntimeArena)),
                new Config.Surface(this.surfaceBiomeCache)
        );
        return Config.builder()
                .debug(new Config.Debug(this.disableWorldgenFeatures))
                .worldgen(worldgen)
                .server(new Config.Server(new Config.FastChunkTicking(this.fastChunkTicking)))
                .chunkSaving(new Config.ChunkSaving(this.gcFreeWorldgen, this.retainBuffer))
                .lighting(new Config.Lighting(new Config.Ya(this.yaLight)))
                .build();
    }
}
