package com.moepus.byepregen;

import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.integration.platform.PlatformServices;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

final class MixinFeatureEvaluator {
    private final Predicate<String> modExists;
    private final BooleanSupplier rawChunkIoSupported;

    MixinFeatureEvaluator(Predicate<String> modExists) {
        this(modExists, () -> true);
    }

    MixinFeatureEvaluator(
            Predicate<String> modExists,
            BooleanSupplier rawChunkIoSupported
    ) {
        this.modExists = Objects.requireNonNull(modExists, "modExists");
        this.rawChunkIoSupported = Objects.requireNonNull(rawChunkIoSupported, "rawChunkIoSupported");
    }

    static MixinFeatureEvaluator createDefault() {
        return new MixinFeatureEvaluator(
                ModEnvironment::isModLoaded,
                () -> PlatformServices.get().supportsGcFreeRawChunkIo()
        );
    }

    boolean isEnabled(MixinFeature feature, Config config) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(config, "config");
        return switch (feature) {
            case NONE -> true;
            case ARENA -> config.worldgen().arena().enabled()
                    && !this.modExists.test("confluence");
            case DFC -> config.worldgen().arena().enabled()
                    && config.worldgen().arena().densityColumnCompiler();
            case GC_FREE_CHUNK_SAVE -> config.chunkSaving().gcFreeWorldgen();
            case GC_FREE_RAW_CHUNK_IO -> this.rawChunkIoEnabled(config);
            case SURFACE_BIOME_CACHE -> config.worldgen().surface().biomeCache();
            case SURFACE_RULE_COMPILER -> config.worldgen().surface().ruleCompiler();
            case YA_LIGHT -> config.lighting().ya().enabled()
                    && !this.modExists.test(YALightCompatibility.SCALABLELUX_MOD_ID);
        };
    }

    private boolean rawChunkIoEnabled(Config config) {
        return config.chunkSaving().gcFreeWorldgen() && this.rawChunkIoSupported.getAsBoolean();
    }
}
