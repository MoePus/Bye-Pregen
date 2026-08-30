package com.moepus.byepregen;

import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import java.util.Objects;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MixinFeatureEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger("ByePregen Mixin Features");
    private static final String BLUEPRINT_MOD_ID = "blueprint";
    private static final String C2ME_SERIALIZER_ACCESS =
            "com.ishland.c2me.base.common.registry.SerializerAccess";

    private final Predicate<String> modExists;
    private final Predicate<String> classExists;

    MixinFeatureEvaluator(
            Predicate<String> modExists,
            Predicate<String> classExists
    ) {
        this.modExists = Objects.requireNonNull(modExists, "modExists");
        this.classExists = Objects.requireNonNull(classExists, "classExists");
    }

    static MixinFeatureEvaluator createDefault() {
        return new MixinFeatureEvaluator(
                ModEnvironment::isModLoaded,
                ModEnvironment::isClassAvailable
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
            case SURFACE_RULE_COMPILER -> config.worldgen().surface().ruleCompiler()
                    && !this.modExists.test(BLUEPRINT_MOD_ID);
            case YA_LIGHT -> config.lighting().ya().enabled()
                    && !this.modExists.test(YALightCompatibility.SCALABLELUX_MOD_ID);
        };
    }

    private boolean rawChunkIoEnabled(Config config) {
        if (!config.chunkSaving().gcFreeWorldgen()) {
            return false;
        }
        try {
            return !this.classExists.test(C2ME_SERIALIZER_ACCESS);
        } catch (RuntimeException | LinkageError throwable) {
            LOGGER.warn("Disabling raw GC-free chunk I/O: cannot inspect C2ME serializer", throwable);
            return false;
        }
    }
}
