package com.moepus.byepregen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.config.Config;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MixinPluginPolicyTest {
    private static final String MIXIN_PREFIX = "com.moepus.byepregen.mixin.";
    private static final String CORE_ARENA = MIXIN_PREFIX + "arena.NoiseChunkArenaMixin";
    private static final String LEVEL_CHUNK_ARENA = MIXIN_PREFIX + "arena.LevelChunkArenaMixin";
    private static final String VOXY_ARENA =
            MIXIN_PREFIX + "arena.compat.voxy.VoxyWorldConversionFactoryMixin";
    private static final String RAW_CHUNK_SAVE = MIXIN_PREFIX + "chunkio.ChunkStorageRawMixin";
    private static final String ORDINARY_CHUNK_SAVE =
            MIXIN_PREFIX + "chunkio.ChunkSerializerWorldgenStateMixin";

    @Test
    void noneFeatureIsIndependentOfPackageName() {
        Config disabled = disabledConfig();

        assertTrue(passes(MixinFeature.NONE, MIXIN_PREFIX + "yalight.ExampleMixin", disabled));
        assertTrue(passes(MixinFeature.NONE, MIXIN_PREFIX + "dfc.ExampleMixin", disabled));
        assertTrue(passes(MixinFeature.NONE, MIXIN_PREFIX + "surface.ExampleMixin", disabled));
    }

    @Test
    void featureSwitchesControlTheirMixins() {
        Config config = disabledConfig();

        assertFalse(passes(MixinFeature.ARENA, CORE_ARENA, config));
        assertFalse(passes(MixinFeature.DFC, MIXIN_PREFIX + "dfc.DfcNoiseChunkMixin", config, true));
        assertFalse(passes(MixinFeature.GC_FREE_CHUNK_SAVE, ORDINARY_CHUNK_SAVE, config));
        assertFalse(passes(MixinFeature.SURFACE_RULE_COMPILER,
                MIXIN_PREFIX + "surface.SurfaceRulesLookupMixin", config));
        assertFalse(passes(MixinFeature.YA_LIGHT,
                MIXIN_PREFIX + "yalight.LevelLightEngineYAMixin", config));
    }

    @Test
    void dfcRequiresArenaAndCompilerConfigButNotC2me() {
        String mixin = MIXIN_PREFIX + "dfc.DensityNoiseChunkMixin";

        assertTrue(passes(MixinFeature.DFC, mixin, new Config(), false));
        assertTrue(passes(MixinFeature.DFC, mixin, new Config(), true));
        Config noCompiler = new ConfigTestBuilder().densityColumnCompiler(false).build();
        assertFalse(passes(MixinFeature.DFC, mixin, noCompiler, true));
        Config noArena = new ConfigTestBuilder().arena(false).build();
        assertFalse(passes(MixinFeature.DFC, mixin, noArena, true));
    }

    @Test
    void arenaPreservesConfluenceAndRuntimeExceptions() {
        Config defaults = new Config();

        assertFalse(passes(new PolicyRequest(
                MixinFeature.ARENA, CORE_ARENA, defaults, false, Set.of("confluence"))));
        assertTrue(passes(MixinFeature.ARENA, LEVEL_CHUNK_ARENA, defaults));
        Config serverRuntime = new ConfigTestBuilder().serverRuntimeArena(true).build();
        assertFalse(passes(MixinFeature.ARENA, LEVEL_CHUNK_ARENA, serverRuntime));
        assertFalse(passes(MixinFeature.ARENA, VOXY_ARENA, defaults));
        Config client = new ConfigTestBuilder().clientArena(true).build();
        assertTrue(passes(MixinFeature.ARENA, VOXY_ARENA, client));
    }

    @Test
    void gcFreeRawMixinsYieldToC2meSerializer() {
        Config config = new Config();

        assertTrue(passes(MixinFeature.GC_FREE_CHUNK_SAVE, RAW_CHUNK_SAVE, config));
        assertFalse(passes(MixinFeature.GC_FREE_CHUNK_SAVE, RAW_CHUNK_SAVE, config, true));
        assertTrue(passes(MixinFeature.GC_FREE_CHUNK_SAVE, ORDINARY_CHUNK_SAVE, config, true));
    }

    @Test
    void gcFreeRawMixinsFailClosedWhenClassLookupFails() {
        MixinPlugin plugin = new MixinPlugin(false);
        MixinPlugin.FeatureGateContext context = new MixinPlugin.FeatureGateContext(
                new Config(),
                ignored -> {
                    throw new IllegalStateException("lookup failed");
                },
                ignored -> false
        );

        assertFalse(plugin.passesFeatureGate(MixinFeature.GC_FREE_CHUNK_SAVE, RAW_CHUNK_SAVE, context));
        assertTrue(plugin.passesFeatureGate(MixinFeature.GC_FREE_CHUNK_SAVE, ORDINARY_CHUNK_SAVE, context));
    }

    @Test
    void surfaceBiomeCacheUsesWorldgenSurfaceConfig() {
        Config disabled = new ConfigTestBuilder().surfaceBiomeCache(false).build();
        assertFalse(passes(MixinFeature.SURFACE_BIOME_CACHE,
                MIXIN_PREFIX + "surface.biome.SurfaceSystemBiomeCacheMixin", disabled));
        assertTrue(passes(MixinFeature.SURFACE_BIOME_CACHE,
                MIXIN_PREFIX + "accessor.surface.BiomeManagerAccessor", new Config()));
    }

    private static boolean passes(MixinFeature feature, String mixin, Config config) {
        return passes(new PolicyRequest(feature, mixin, config, false, Set.of()));
    }

    private static boolean passes(MixinFeature feature, String mixin, Config config, boolean external) {
        return passes(new PolicyRequest(feature, mixin, config, external, Set.of()));
    }

    private static boolean passes(PolicyRequest request) {
        MixinPlugin plugin = new MixinPlugin(request.external());
        MixinPlugin.FeatureGateContext context = new MixinPlugin.FeatureGateContext(
                request.config(), ignored -> request.external(), request.mods()::contains);
        return plugin.passesFeatureGate(request.feature(), request.mixin(), context);
    }

    private static Config disabledConfig() {
        return new ConfigTestBuilder()
                .arena(false)
                .gcFreeWorldgen(false)
                .surfaceRuleCompiler(false)
                .yaLight(false)
                .build();
    }

    private record PolicyRequest(
            MixinFeature feature,
            String mixin,
            Config config,
            boolean external,
            Set<String> mods
    ) {
    }
}
