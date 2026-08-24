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
        assertFalse(enabled(MixinFeature.DFC, config));
        assertFalse(enabled(MixinFeature.GC_FREE_CHUNK_SAVE, config));
        assertFalse(enabled(MixinFeature.GC_FREE_RAW_CHUNK_IO, config));
        assertFalse(enabled(MixinFeature.SURFACE_RULE_COMPILER, config));
        assertFalse(enabled(MixinFeature.YA_LIGHT, config));
    }

    @Test
    void dfcRequiresArenaAndCompilerConfigButNotC2me() {
        assertTrue(enabled(MixinFeature.DFC, new Config()));
        assertTrue(evaluator(Set.of("c2me"), true).isEnabled(MixinFeature.DFC, new Config()));
        Config noCompiler = new ConfigTestBuilder().densityColumnCompiler(false).build();
        assertFalse(enabled(MixinFeature.DFC, noCompiler));
        Config noArena = new ConfigTestBuilder().arena(false).build();
        assertFalse(enabled(MixinFeature.DFC, noArena));
    }

    @Test
    void arenaPreservesConfluencePolicy() {
        assertTrue(enabled(MixinFeature.ARENA, new Config()));
        MixinFeatureEvaluator evaluator = evaluator(Set.of("confluence"), true);
        assertFalse(evaluator.isEnabled(MixinFeature.ARENA, new Config()));
    }

    @Test
    void arenaRuntimeFlagsExpressMixinSpecificPolicies() {
        Config defaults = new Config();
        assertTrue(ConfigFlag.MATERIALIZE_ARENA_LEVEL_CHUNK.isEnabled(defaults));
        assertFalse(ConfigFlag.CLIENT_ARENA.isEnabled(defaults));

        Config serverRuntime = new ConfigTestBuilder().serverRuntimeArena(true).build();
        assertFalse(ConfigFlag.MATERIALIZE_ARENA_LEVEL_CHUNK.isEnabled(serverRuntime));
        Config clientRuntime = new ConfigTestBuilder().clientArena(true).build();
        assertTrue(ConfigFlag.CLIENT_ARENA.isEnabled(clientRuntime));
    }

    @Test
    void rawChunkIoUsesDirectStorageWithC2me() {
        Config config = new Config();

        MixinFeatureEvaluator evaluator = evaluator(Set.of("c2me"), true);
        assertTrue(evaluator.isEnabled(MixinFeature.GC_FREE_RAW_CHUNK_IO, config));
        assertTrue(evaluator.isEnabled(MixinFeature.GC_FREE_CHUNK_SAVE, config));
    }

    @Test
    void rawChunkIoRequiresPlatformSupport() {
        MixinFeatureEvaluator evaluator = new MixinFeatureEvaluator(
                ignored -> false,
                () -> false
        );

        assertFalse(evaluator.isEnabled(MixinFeature.GC_FREE_RAW_CHUNK_IO, new Config()));
        assertTrue(evaluator.isEnabled(MixinFeature.GC_FREE_CHUNK_SAVE, new Config()));
    }

    @Test
    void surfaceBiomeCacheUsesWorldgenSurfaceConfig() {
        Config disabled = new ConfigTestBuilder().surfaceBiomeCache(false).build();
        assertFalse(enabled(MixinFeature.SURFACE_BIOME_CACHE, disabled));
        assertTrue(enabled(MixinFeature.SURFACE_BIOME_CACHE, new Config()));
    }

    private static boolean enabled(MixinFeature feature, Config config) {
        return evaluator(Set.of(), true).isEnabled(feature, config);
    }

    private static MixinFeatureEvaluator evaluator(Set<String> mods, boolean rawIoSupported) {
        return new MixinFeatureEvaluator(mods::contains, () -> rawIoSupported);
    }

    private static Config disabledConfig() {
        return new ConfigTestBuilder()
                .arena(false)
                .gcFreeWorldgen(false)
                .surfaceRuleCompiler(false)
                .yaLight(false)
                .build();
    }
}
