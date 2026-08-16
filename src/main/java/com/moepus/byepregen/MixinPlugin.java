package com.moepus.byepregen;

import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigParser;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class MixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ByePregen Mixin Plugin");
    private static final String MIXIN_PACKAGE = "com.moepus.byepregen.mixin.";
    private static final String SURFACE_BIOME_CACHE_PROPERTY = "byepregen.surfaceBiomeCache";
    private static final String C2ME_DFC_MODULE_ENTRYPOINT =
            "com.ishland.c2me.opts.dfc.ModuleEntryPoint";
    private static final String C2ME_DFC_ENABLED_FIELD = "enabled";

    private static final String CHUNK_STORAGE_ACCESSOR =
            MIXIN_PACKAGE + "accessor.chunksave.ChunkStorageAccessor";
    private static final String IO_WORKER_PENDING_STORE_ACCESSOR =
            MIXIN_PACKAGE + "accessor.chunksave.IOWorkerPendingStoreAccessor";
    private static final String REGION_FILE_STORAGE_ACCESSOR =
            MIXIN_PACKAGE + "accessor.chunksave.RegionFileStorageAccessor";
    private static final String C2ME_SERIALIZER_ACCESS =
            "com.ishland.c2me.base.common.registry.SerializerAccess";
    private static final String LEVEL_CHUNK_ARENA_MIXIN =
            MIXIN_PACKAGE + "arena.LevelChunkArenaMixin";
    private static final String VOXY_ARENA_MIXIN =
            MIXIN_PACKAGE + "arena.compat.voxy.VoxyWorldConversionFactoryMixin";
    private static final Set<String> RAW_GC_FREE_MIXINS = Set.of(
            MIXIN_PACKAGE + "chunkio.ChunkMapGcFreeSaveMixin",
            MIXIN_PACKAGE + "chunkio.ChunkStorageRawMixin",
            MIXIN_PACKAGE + "chunkio.IOWorkerRawMixin",
            CHUNK_STORAGE_ACCESSOR,
            IO_WORKER_PENDING_STORE_ACCESSOR,
            REGION_FILE_STORAGE_ACCESSOR
    );
    private static final MixinGateEvaluator MIXIN_GATE_EVALUATOR = MixinGateEvaluator.createDefault();
    private boolean c2meDfcEnabled;

    public MixinPlugin() {
    }

    MixinPlugin(boolean c2meDfcEnabled) {
        this.c2meDfcEnabled = c2meDfcEnabled;
    }

    @Override
    public void onLoad(String mixinPackage) {
        this.c2meDfcEnabled = readC2meDfcEnabled();
        LOGGER.info("C2ME DFC module is {}", this.c2meDfcEnabled ? "enabled" : "disabled");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Config config = ConfigParser.getConfig();
        MixinGateEvaluator.GateEvaluation gate = MIXIN_GATE_EVALUATOR.evaluate(
                targetClassName, mixinClassName, config);
        FeatureGateContext context = new FeatureGateContext(
                config, ModEnvironment::isClassAvailable, ModEnvironment::isModLoaded);
        return gate.annotationEnabled()
                && this.passesFeatureGate(gate.feature(), mixinClassName, context);
    }

    boolean passesFeatureGate(
            MixinFeature feature,
            String mixinClassName,
            FeatureGateContext context
    ) {
        Config config = context.config();
        return switch (feature) {
            case NONE -> true;
            case ARENA -> passesArenaGate(mixinClassName, config, context.modExists());
            case DFC -> config.enableArenaPalette && this.c2meDfcEnabled;
            case GC_FREE_CHUNK_SAVE -> passesGcFreeGate(
                    mixinClassName, config, context.classExists());
            case SURFACE_BIOME_CACHE -> Boolean.parseBoolean(
                    System.getProperty(SURFACE_BIOME_CACHE_PROPERTY, "true"));
            case SURFACE_RULE_COMPILER -> config.enableSurfaceRuleCompiler;
            case YA_LIGHT -> config.enableYALightEngine;
        };
    }

    private static boolean readC2meDfcEnabled() {
        try {
            Class<?> entryPoint = Class.forName(C2ME_DFC_MODULE_ENTRYPOINT);
            Field enabled = entryPoint.getDeclaredField(C2ME_DFC_ENABLED_FIELD);
            enabled.setAccessible(true);
            return enabled.getBoolean(null);
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException throwable) {
            LOGGER.warn("Disabling C2ME DFC integration: cannot read its module state", throwable);
            return false;
        }
    }

    private static boolean passesGcFreeGate(
            String mixinClassName,
            Config config,
            Predicate<String> classExists
    ) {
        if (!config.enableGcFreeWorldgenSave) {
            return false;
        }
        if (!RAW_GC_FREE_MIXINS.contains(mixinClassName)) {
            return true;
        }
        try {
            return !classExists.test(C2ME_SERIALIZER_ACCESS);
        } catch (RuntimeException | LinkageError throwable) {
            LOGGER.warn("Disabling raw GC-free chunk-save mixins: cannot inspect C2ME serializer", throwable);
            return false;
        }
    }

    private static boolean passesArenaGate(
            String mixinClassName,
            Config config,
            Predicate<String> modExists
    ) {
        if (!config.enableArenaPalette) {
            return false;
        }
        if (modExists.test("confluence")) {
            return false;
        }
        if (LEVEL_CHUNK_ARENA_MIXIN.equals(mixinClassName)) {
            return !config.enableServerRuntimeArenaPalette;
        }
        if (VOXY_ARENA_MIXIN.equals(mixinClassName)) {
            return config.enableClientArenaPalette;
        }
        return true;
    }

    record FeatureGateContext(
            Config config,
            Predicate<String> classExists,
            Predicate<String> modExists
    ) {
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
