package com.moepus.byepregen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.config.Config;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

final class MixinPluginPolicyTest {
    private static final String MIXIN_PREFIX = "com.moepus.byepregen.mixin.";
    private static final Method PASSES_FEATURE_GATE = featureGateMethod();

    @Test
    void gcFreeChunksaveMixinsFollowFeatureSwitch() throws Exception {
        Config config = new Config();
        String mixin = MIXIN_PREFIX + "chunksave.ChunkSerializerWorldgenStateMixin";

        assertTrue(passes(mixin, config, ignored -> false));
        config.enableGcFreeWorldgenSave = false;
        assertFalse(passes(mixin, config, ignored -> false));
    }

    @Test
    void rawChunksaveMixinsYieldToC2meSerializer() throws Exception {
        Config config = new Config();
        String mixin = MIXIN_PREFIX + "chunksave.ChunkStorageRawMixin";

        assertTrue(passes(mixin, config, ignored -> false));
        assertFalse(passes(mixin, config, ignored -> true));
    }

    @Test
    void independentChunksaveMixinsIgnoreGcFreeSwitch() throws Exception {
        Config config = new Config();
        config.enableGcFreeWorldgenSave = false;

        assertTrue(passes(
                MIXIN_PREFIX + "chunksave.RegionFileCompressionMixin", config, ignored -> true));
        assertTrue(passes(
                MIXIN_PREFIX + "chunksave.ChunkSerializerLowStatusHeightmapMixin", config, ignored -> true));
    }

    private static boolean passes(
            String mixinClassName, Config config, Predicate<String> classExists) throws Exception {
        return (boolean) PASSES_FEATURE_GATE.invoke(new MixinPlugin(), mixinClassName, config, classExists);
    }

    private static Method featureGateMethod() {
        try {
            Method method = MixinPlugin.class.getDeclaredMethod(
                    "passesFeatureGate", String.class, Config.class, Predicate.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
