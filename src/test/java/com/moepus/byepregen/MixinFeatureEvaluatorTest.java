package com.moepus.byepregen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.config.Config;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MixinFeatureEvaluatorTest {
    @Test
    void noneFeatureIsAlwaysEnabled() {
        assertTrue(enabled(MixinFeature.NONE, disabledConfig()));
    }

    @Test
    void featureSwitchesControlTheirMixins() {
        Config config = disabledConfig();

        assertFalse(enabled(MixinFeature.ARENA, config));
        assertFalse(enabled(MixinFeature.ARENA_OR_DFC, config));
        assertFalse(enabled(MixinFeature.DFC, config));
        assertFalse(enabled(MixinFeature.GC_FREE_CHUNK_SAVE, config));
        assertFalse(enabled(MixinFeature.GC_FREE_RAW_CHUNK_IO, config));
        assertFalse(enabled(MixinFeature.YA_LIGHT, config));
    }

    @Test
    void dfcRequiresArenaAndCompilerConfigButNotC2me() {
        assertTrue(enabledWithClass(MixinFeature.DFC, new Config(), false));
        assertTrue(enabledWithClass(MixinFeature.DFC, new Config(), true));
        Config noCompiler = new ConfigTestBuilder().densityColumnCompiler(false).build();
        assertFalse(enabled(MixinFeature.DFC, noCompiler));
        Config noArena = new ConfigTestBuilder().arena(false).build();
        assertFalse(enabled(MixinFeature.DFC, noArena));
    }

    @Test
    void cellCacheBypassRequiresArenaOrDfc() {
        assertTrue(enabled(MixinFeature.ARENA_OR_DFC, new Config()));
        Config noCompiler = new ConfigTestBuilder().densityColumnCompiler(false).build();
        assertTrue(enabled(MixinFeature.ARENA_OR_DFC, noCompiler));
        Config disabled = new ConfigTestBuilder().arena(false).build();
        assertFalse(enabled(MixinFeature.ARENA_OR_DFC, disabled));

        MixinFeatureEvaluator withConfluence = evaluator(false, Set.of("confluence"));
        assertTrue(withConfluence.isEnabled(MixinFeature.ARENA_OR_DFC, new Config()));
    }

    @Test
    void arenaPreservesConfluencePolicy() {
        assertTrue(enabled(MixinFeature.ARENA, new Config()));
        MixinFeatureEvaluator evaluator = evaluator(false, Set.of("confluence"));
        assertFalse(evaluator.isEnabled(MixinFeature.ARENA, new Config()));
    }

    @Test
    void arenaRuntimeFlagsExpressMixinSpecificPolicies() {
        Config defaults = new Config();
        assertTrue(ConfigFlag.MATERIALIZE_ARENA_LEVEL_CHUNK.isEnabled(defaults));

        Config serverRuntime = new ConfigTestBuilder().serverRuntimeArena(true).build();
        assertFalse(ConfigFlag.MATERIALIZE_ARENA_LEVEL_CHUNK.isEnabled(serverRuntime));
    }

    @Test
    void gcFreeChunkSavingDefaultsOff() {
        Config defaults = new Config();

        assertFalse(enabled(MixinFeature.GC_FREE_CHUNK_SAVE, defaults));
        assertFalse(enabled(MixinFeature.GC_FREE_RAW_CHUNK_IO, defaults));
    }

    @Test
    void rawChunkIoYieldsToC2meSerializer() {
        Config config = new ConfigTestBuilder().gcFreeWorldgen(true).build();

        assertTrue(enabledWithClass(MixinFeature.GC_FREE_RAW_CHUNK_IO, config, false));
        assertFalse(enabledWithClass(MixinFeature.GC_FREE_RAW_CHUNK_IO, config, true));
        assertTrue(enabledWithClass(MixinFeature.GC_FREE_CHUNK_SAVE, config, true));
    }

    @Test
    void rawChunkIoFailsClosedWhenClassLookupFails() {
        Config config = new ConfigTestBuilder().gcFreeWorldgen(true).build();
        MixinFeatureEvaluator evaluator = new MixinFeatureEvaluator(
                ignored -> false,
                ignored -> {
                    throw new IllegalStateException("lookup failed");
                }
        );

        assertFalse(evaluator.isEnabled(MixinFeature.GC_FREE_RAW_CHUNK_IO, config));
        assertTrue(evaluator.isEnabled(MixinFeature.GC_FREE_CHUNK_SAVE, config));
    }

    @Test
    void surfaceBiomeCacheUsesWorldgenSurfaceConfig() {
        Config disabled = new ConfigTestBuilder().surfaceBiomeCache(false).build();
        assertFalse(enabled(MixinFeature.SURFACE_BIOME_CACHE, disabled));
        assertTrue(enabled(MixinFeature.SURFACE_BIOME_CACHE, new Config()));
    }

    private static boolean enabled(MixinFeature feature, Config config) {
        return evaluator(false, Set.of()).isEnabled(feature, config);
    }

    private static boolean enabledWithClass(
            MixinFeature feature,
            Config config,
            boolean classExists
    ) {
        return evaluator(classExists, Set.of()).isEnabled(feature, config);
    }

    private static MixinFeatureEvaluator evaluator(boolean classExists, Set<String> mods) {
        return new MixinFeatureEvaluator(mods::contains, ignored -> classExists);
    }

    private static Config disabledConfig() {
        return new ConfigTestBuilder()
                .arena(false)
                .gcFreeWorldgen(false)
                .yaLight(false)
                .build();
    }
}
