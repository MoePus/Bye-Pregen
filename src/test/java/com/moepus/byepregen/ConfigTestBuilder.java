package com.moepus.byepregen;

import com.moepus.byepregen.config.Config;

final class ConfigTestBuilder {
    private boolean placedFeatures;
    private boolean arena = true;
    private boolean densityColumnCompiler = true;
    private boolean serverRuntimeArena;
    private boolean clientArena;
    private boolean surfaceRuleCompiler = true;
    private boolean surfaceBiomeCache = true;
    private boolean gcFreeWorldgen = true;
    private boolean yaLight;

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

    ConfigTestBuilder clientArena(boolean value) {
        this.clientArena = value;
        return this;
    }

    ConfigTestBuilder surfaceRuleCompiler(boolean value) {
        this.surfaceRuleCompiler = value;
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
                new Config.PlacedFeatures(this.placedFeatures, true, true),
                new Config.Arena(this.arena, this.densityColumnCompiler,
                        new Config.ArenaRuntime(this.serverRuntimeArena, this.clientArena)),
                new Config.Surface(this.surfaceRuleCompiler, this.surfaceBiomeCache),
                new Config.Misc(true)
        );
        return Config.builder()
                .debug(new Config.Debug(false))
                .worldgen(worldgen)
                .server(new Config.Server(new Config.FastChunkTicking(false)))
                .chunkSaving(new Config.ChunkSaving(this.gcFreeWorldgen, true))
                .lighting(new Config.Lighting(new Config.Ya(this.yaLight)))
                .build();
    }
}
