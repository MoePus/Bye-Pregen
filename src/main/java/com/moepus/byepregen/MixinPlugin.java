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
    private static final String YA_LIGHT_MIXIN_PREFIX = MIXIN_PACKAGE + "yalight.";
    private static final String GC_FREE_MIXIN_PREFIX = MIXIN_PACKAGE + "gcfree.";
    private static final String DFC_MIXIN_PREFIX = MIXIN_PACKAGE + "dfc.";
    private static final String SURFACE_BIOME_CACHE_PROPERTY = "byepregen.surfaceBiomeCache";
    private static final String C2ME_DFC_MODULE_ENTRYPOINT =
            "com.ishland.c2me.opts.dfc.ModuleEntryPoint";
    private static final String C2ME_DFC_ENABLED_FIELD = "enabled";

    private static final String C2ME_HOOK_COMPATIBILITY_MIXIN =
            MIXIN_PACKAGE + "compat.C2MEHookCompatibilityMixin";
    private static final String ARCHITECTURY_EVENT_ACCESSOR =
            MIXIN_PACKAGE + "accessor.ArchitecturyEventImplAccessor";
    private static final String CHUNK_STORAGE_ACCESSOR =
            MIXIN_PACKAGE + "accessor.ChunkStorageAccessor";
    private static final String IO_WORKER_PENDING_STORE_ACCESSOR =
            MIXIN_PACKAGE + "accessor.IOWorkerPendingStoreAccessor";
    private static final String REGION_FILE_STORAGE_ACCESSOR =
            MIXIN_PACKAGE + "accessor.RegionFileStorageAccessor";
    private static final String C2ME_SERIALIZER_ACCESS =
            "com.ishland.c2me.base.common.registry.SerializerAccess";
    private static final String AQUIFER_FLUID_STATUS_ACCESSOR =
            MIXIN_PACKAGE + "AquiferFluidStatusAccessor";
    private static final String CHUNK_ACCESS_ARENA_MIXIN = MIXIN_PACKAGE + "ChunkAccessArenaMixin";
    private static final String CHUNK_SERIALIZER_ARENA_READ_MIXIN =
            MIXIN_PACKAGE + "ChunkSerializerArenaReadMixin";
    private static final String LEVEL_CHUNK_ARENA_MIXIN = MIXIN_PACKAGE + "LevelChunkArenaMixin";
    private static final String NOISE_CHUNK_ACCESSOR = MIXIN_PACKAGE + "NoiseChunkAccessor";
    private static final String NOISE_CHUNK_AQUIFER_SURFACE_MIXIN =
            MIXIN_PACKAGE + "NoiseChunkAquiferSurfaceMixin";
    private static final String NOISE_CHUNK_ARENA_MIXIN = MIXIN_PACKAGE + "NoiseChunkArenaMixin";
    private static final String NOISE_INTERPOLATOR_ARENA_MIXIN =
            MIXIN_PACKAGE + "NoiseInterpolatorArenaMixin";
    private static final String NOISE_BASED_AQUIFER_SURFACE_MIXIN =
            MIXIN_PACKAGE + "NoiseBasedAquiferSurfaceMixin";
    private static final String NOISE_GENERATOR_ARENA_MIXIN =
            MIXIN_PACKAGE + "NoiseBasedChunkGeneratorArenaMixin";
    private static final String PROTO_CHUNK_ARENA_HEIGHTMAP_MIXIN =
            MIXIN_PACKAGE + "ProtoChunkArenaHeightmapMixin";
    private static final String FASTNOISE_OCL_ARENA_MIXIN =
            MIXIN_PACKAGE + "compat.FastNoiseOpenCLArenaMixin";
    private static final String VOXY_ARENA_MIXIN =
            MIXIN_PACKAGE + "compat.VoxyWorldConversionFactoryMixin";
    private static final String SURFACE_BIOME_CACHE_MIXIN =
            MIXIN_PACKAGE + "surface.biome.SurfaceSystemBiomeCacheMixin";
    private static final String SURFACE_BIOME_MANAGER_ACCESSOR =
            MIXIN_PACKAGE + "accessor.surface.BiomeManagerAccessor";
    private static final String SURFACE_SCALAR_MIXIN_PREFIX = MIXIN_PACKAGE + "surface.";

    private static final Set<String> GC_FREE_SATELLITE_MIXINS = Set.of(
            C2ME_HOOK_COMPATIBILITY_MIXIN,
            ARCHITECTURY_EVENT_ACCESSOR
    );
    private static final Set<String> RAW_GC_FREE_MIXINS = Set.of(
            GC_FREE_MIXIN_PREFIX + "ChunkMapGcFreeSaveMixin",
            GC_FREE_MIXIN_PREFIX + "ChunkStorageRawMixin",
            GC_FREE_MIXIN_PREFIX + "IOWorkerRawMixin",
            CHUNK_STORAGE_ACCESSOR,
            IO_WORKER_PENDING_STORE_ACCESSOR,
            REGION_FILE_STORAGE_ACCESSOR
    );
    private static final Set<String> ARENA_MIXINS = Set.of(
            AQUIFER_FLUID_STATUS_ACCESSOR,
            CHUNK_ACCESS_ARENA_MIXIN,
            CHUNK_SERIALIZER_ARENA_READ_MIXIN,
            LEVEL_CHUNK_ARENA_MIXIN,
            NOISE_CHUNK_ACCESSOR,
            NOISE_CHUNK_AQUIFER_SURFACE_MIXIN,
            NOISE_CHUNK_ARENA_MIXIN,
            NOISE_INTERPOLATOR_ARENA_MIXIN,
            NOISE_BASED_AQUIFER_SURFACE_MIXIN,
            NOISE_GENERATOR_ARENA_MIXIN,
            PROTO_CHUNK_ARENA_HEIGHTMAP_MIXIN,
            FASTNOISE_OCL_ARENA_MIXIN,
            VOXY_ARENA_MIXIN
    );

    private static final MixinGateEvaluator MIXIN_GATE_EVALUATOR = MixinGateEvaluator.createDefault();
    private boolean c2meDfcEnabled;

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
        boolean featureEnabled = this.passesFeatureGate(
                mixinClassName, config, ModEnvironment::isClassAvailable);
        boolean annotationEnabled = MIXIN_GATE_EVALUATOR.shouldApply(targetClassName, mixinClassName, config);
        return featureEnabled && annotationEnabled;
    }

    private boolean passesFeatureGate(
            String mixinClassName,
            Config config,
            Predicate<String> classExists
    ) {
        if (SURFACE_BIOME_CACHE_MIXIN.equals(mixinClassName)
                || SURFACE_BIOME_MANAGER_ACCESSOR.equals(mixinClassName)) {
            return Boolean.parseBoolean(System.getProperty(SURFACE_BIOME_CACHE_PROPERTY, "true"));
        }
        if (mixinClassName.startsWith(SURFACE_SCALAR_MIXIN_PREFIX)) {
            return config.enableSurfaceRuleCompiler;
        }
        if (mixinClassName.startsWith(DFC_MIXIN_PREFIX)) {
            return config.enableArenaPalette && this.c2meDfcEnabled;
        }
        if (mixinClassName.startsWith(YA_LIGHT_MIXIN_PREFIX)) {
            return config.enableYALightEngine;
        }
        if (isGcFreeMixin(mixinClassName)) {
            return passesGcFreeGate(mixinClassName, config, classExists);
        }
        if (ARENA_MIXINS.contains(mixinClassName)) {
            return passesArenaGate(mixinClassName, config);
        }
        return true;
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

    private static boolean isGcFreeMixin(String mixinClassName) {
        return mixinClassName.startsWith(GC_FREE_MIXIN_PREFIX)
                || GC_FREE_SATELLITE_MIXINS.contains(mixinClassName)
                || RAW_GC_FREE_MIXINS.contains(mixinClassName);
    }

    private static boolean passesGcFreeGate(
            String mixinClassName,
            Config config,
            Predicate<String> classExists
    ) {
        if (!config.enableGcFreeWorldgenSave) {
            return false;
        }
        return !RAW_GC_FREE_MIXINS.contains(mixinClassName)
                || !classExists.test(C2ME_SERIALIZER_ACCESS);
    }

    private static boolean passesArenaGate(String mixinClassName, Config config) {
        if (!config.enableArenaPalette) {
            return false;
        }
        if (ModEnvironment.isModLoaded("confluence")) {
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
