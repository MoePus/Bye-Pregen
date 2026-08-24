package com.moepus.byepregen.config;

import java.util.Objects;

public final class Config {
    private final Debug debug;
    private final Worldgen worldgen;
    private final Server server;
    private final ChunkSaving chunkSaving;
    private final Lighting lighting;

    public Config() {
        this(new Builder());
    }

    private Config(Builder builder) {
        this.debug = Objects.requireNonNull(builder.debug, "debug");
        this.worldgen = Objects.requireNonNull(builder.worldgen, "worldgen");
        this.server = Objects.requireNonNull(builder.server, "server");
        this.chunkSaving = Objects.requireNonNull(builder.chunkSaving, "chunkSaving");
        this.lighting = Objects.requireNonNull(builder.lighting, "lighting");
    }

    public static Config defaults() {
        return new Config();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Debug debug() {
        return this.debug;
    }

    public Worldgen worldgen() {
        return this.worldgen;
    }

    public Server server() {
        return this.server;
    }

    public ChunkSaving chunkSaving() {
        return this.chunkSaving;
    }

    public Lighting lighting() {
        return this.lighting;
    }

    public static final class Builder {
        private Debug debug = new Debug();
        private Worldgen worldgen = new Worldgen();
        private Server server = new Server();
        private ChunkSaving chunkSaving = new ChunkSaving();
        private Lighting lighting = new Lighting();

        private Builder() {
        }

        public Builder debug(Debug value) {
            this.debug = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder worldgen(Worldgen value) {
            this.worldgen = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder server(Server value) {
            this.server = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder chunkSaving(ChunkSaving value) {
            this.chunkSaving = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder lighting(Lighting value) {
            this.lighting = Objects.requireNonNull(value, "value");
            return this;
        }

        public Config build() {
            return new Config(this);
        }
    }

    public record Debug(BooleanSetting disableWorldgenFeaturesSetting) {
        public Debug {
            Objects.requireNonNull(disableWorldgenFeaturesSetting, "disableWorldgenFeaturesSetting");
        }

        public Debug(boolean value) {
            this(BooleanSetting.explicit(value));
        }

        public Debug() {
            this(BooleanSetting.DEFAULT);
        }

        public boolean disableWorldgenFeatures() {
            return this.disableWorldgenFeaturesSetting.resolve(false);
        }
    }

    public record Worldgen(PlacedFeatures placedFeatures, Arena arena, Surface surface) {
        public Worldgen {
            Objects.requireNonNull(placedFeatures, "placedFeatures");
            Objects.requireNonNull(arena, "arena");
            Objects.requireNonNull(surface, "surface");
        }

        public Worldgen() {
            this(new PlacedFeatures(), new Arena(), new Surface());
        }
    }

    public record PlacedFeatures(BooleanSetting enabledSetting, BooleanSetting memoizedDiskPlanSetting) {
        public PlacedFeatures {
            Objects.requireNonNull(enabledSetting, "enabledSetting");
            Objects.requireNonNull(memoizedDiskPlanSetting, "memoizedDiskPlanSetting");
        }

        public PlacedFeatures(boolean enabled, boolean memoizedDiskPlan) {
            this(BooleanSetting.explicit(enabled), BooleanSetting.explicit(memoizedDiskPlan));
        }

        public PlacedFeatures() {
            this(BooleanSetting.DEFAULT, BooleanSetting.DEFAULT);
        }

        public boolean enabled() {
            return this.enabledSetting.resolve(false);
        }

        public boolean memoizedDiskPlan() {
            return this.memoizedDiskPlanSetting.resolve(true);
        }
    }

    public record Arena(
            BooleanSetting enabledSetting,
            BooleanSetting densityColumnCompilerSetting,
            ArenaRuntime runtime
    ) {
        public Arena {
            Objects.requireNonNull(enabledSetting, "enabledSetting");
            Objects.requireNonNull(densityColumnCompilerSetting, "densityColumnCompilerSetting");
            Objects.requireNonNull(runtime, "runtime");
        }

        public Arena(boolean enabled, boolean densityColumnCompiler, ArenaRuntime runtime) {
            this(BooleanSetting.explicit(enabled), BooleanSetting.explicit(densityColumnCompiler), runtime);
        }

        public Arena() {
            this(BooleanSetting.DEFAULT, BooleanSetting.DEFAULT, new ArenaRuntime());
        }

        public boolean enabled() {
            return this.enabledSetting.resolve(true);
        }

        public boolean densityColumnCompiler() {
            return this.densityColumnCompilerSetting.resolve(true);
        }
    }

    public record ArenaRuntime(BooleanSetting serverSetting, BooleanSetting clientSetting) {
        public ArenaRuntime {
            Objects.requireNonNull(serverSetting, "serverSetting");
            Objects.requireNonNull(clientSetting, "clientSetting");
        }

        public ArenaRuntime(boolean server, boolean client) {
            this(BooleanSetting.explicit(server), BooleanSetting.explicit(client));
        }

        public ArenaRuntime() {
            this(BooleanSetting.DEFAULT, BooleanSetting.DEFAULT);
        }

        public boolean server() {
            return this.serverSetting.resolve(false);
        }

        public boolean client() {
            return this.clientSetting.resolve(false);
        }
    }

    public record Surface(BooleanSetting ruleCompilerSetting, BooleanSetting biomeCacheSetting) {
        public Surface {
            Objects.requireNonNull(ruleCompilerSetting, "ruleCompilerSetting");
            Objects.requireNonNull(biomeCacheSetting, "biomeCacheSetting");
        }

        public Surface(boolean ruleCompiler, boolean biomeCache) {
            this(BooleanSetting.explicit(ruleCompiler), BooleanSetting.explicit(biomeCache));
        }

        public Surface() {
            this(BooleanSetting.DEFAULT, BooleanSetting.DEFAULT);
        }

        public boolean ruleCompiler() {
            return this.ruleCompilerSetting.resolve(true);
        }

        public boolean biomeCache() {
            return this.biomeCacheSetting.resolve(true);
        }
    }

    public record Server(FastChunkTicking fastChunkTicking) {
        public Server {
            Objects.requireNonNull(fastChunkTicking, "fastChunkTicking");
        }

        public Server() {
            this(new FastChunkTicking());
        }
    }

    public record FastChunkTicking(BooleanSetting enabledSetting) {
        public FastChunkTicking {
            Objects.requireNonNull(enabledSetting, "enabledSetting");
        }

        public FastChunkTicking(boolean enabled) {
            this(BooleanSetting.explicit(enabled));
        }

        public FastChunkTicking() {
            this(BooleanSetting.DEFAULT);
        }

        public boolean enabled() {
            return this.enabledSetting.resolve(false);
        }
    }

    public record ChunkSaving(BooleanSetting gcFreeWorldgenSetting, BooleanSetting retainBufferSetting) {
        public ChunkSaving {
            Objects.requireNonNull(gcFreeWorldgenSetting, "gcFreeWorldgenSetting");
            Objects.requireNonNull(retainBufferSetting, "retainBufferSetting");
        }

        public ChunkSaving(boolean gcFreeWorldgen, boolean retainBuffer) {
            this(BooleanSetting.explicit(gcFreeWorldgen), BooleanSetting.explicit(retainBuffer));
        }

        public ChunkSaving() {
            this(BooleanSetting.DEFAULT, BooleanSetting.DEFAULT);
        }

        public boolean gcFreeWorldgen() {
            return this.gcFreeWorldgenSetting.resolve(true);
        }

        public boolean retainBuffer() {
            return this.retainBufferSetting.resolve(true);
        }
    }

    public record Lighting(Ya ya) {
        public Lighting {
            Objects.requireNonNull(ya, "ya");
        }

        public Lighting() {
            this(new Ya());
        }
    }

    public record Ya(BooleanSetting enabledSetting) {
        public Ya {
            Objects.requireNonNull(enabledSetting, "enabledSetting");
        }

        public Ya(boolean enabled) {
            this(BooleanSetting.explicit(enabled));
        }

        public Ya() {
            this(BooleanSetting.DEFAULT);
        }

        public boolean enabled() {
            return this.enabledSetting.resolve(false);
        }
    }
}
