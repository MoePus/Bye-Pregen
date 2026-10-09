package com.moepus.byepregen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.config.Config;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

final class BiolithMixinCompatibilityTest {
    private static final String MIXIN_PACKAGE = "com.moepus.byepregen.mixin.";

    @ParameterizedTest
    @ValueSource(strings = {
            "accessor.worldgen.biome.MultiNoiseBiomeSourceAccessor",
            "climate.ClimateParameterListColumnMixin",
            "climate.ClimateRTreeColumnMixin",
            "climate.ClimateRTreeNodeCacheMixin",
            "dfc.BiomeDensityNoiseChunkMixin",
            "dfc.BiomeDensityRandomStateMixin",
            "worldgen.biome.NoiseBasedChunkGeneratorBiomeColumnMixin"
    })
    void biomeColumnsYieldToBiolith(String mixin) {
        assertTrue(enabled(mixin, Set.of(), Config.defaults()));
        assertFalse(enabled(mixin, Set.of("biolith"), Config.defaults()));
        assertFalse(enabled(mixin, Set.of("biolith", "nomansland"), Config.defaults()));
        assertFalse(enabled(mixin, Set.of(), new ConfigTestBuilder().arena(false).build()));
        assertFalse(enabled(mixin, Set.of(),
                new ConfigTestBuilder().densityColumnCompiler(false).build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "arena.ChunkAccessArenaMixin",
            "arena.NoiseBasedChunkGeneratorArenaMixin",
            "dfc.DensityRandomStateMixin",
            "dfc.DensityNoiseChunkMixin",
            "climate.ClimateRTreeBuildMixin",
            "climate.ClimateRTreeSearchMixin",
            "climate.ClimateParameterListSearchMixin",
            "surface.SurfaceSystemRuleCompilerMixin",
            "surface.biome.SurfaceSystemBiomeCacheMixin"
    })
    void biolithKeepsIndependentOptimizations(String mixin) {
        assertTrue(enabled(mixin, Set.of(), Config.defaults()));
        assertTrue(enabled(mixin, Set.of("biolith", "nomansland"), Config.defaults()));
    }

    private static boolean enabled(String mixin, Set<String> mods, Config config) {
        MixinGateEvaluator gates = new MixinGateEvaluator(
                BiolithMixinCompatibilityTest::readClassNode, mods::contains, ignored -> true);
        MixinGateEvaluator.GateEvaluation gate =
                gates.evaluate("unused.Target", MIXIN_PACKAGE + mixin, config);
        MixinFeatureEvaluator features = new MixinFeatureEvaluator(mods::contains, ignored -> false);
        return gate.annotationEnabled() && features.isEnabled(gate.feature(), config);
    }

    private static ClassNode readClassNode(String className) throws IOException {
        String resource = "/" + className.replace('.', '/') + ".class";
        try (InputStream input = BiolithMixinCompatibilityTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
