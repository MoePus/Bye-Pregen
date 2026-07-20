package com.moepus.byepregen;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class MixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "com.moepus.byepregen.mixin.";
    private static final String C2ME_SERVER_BLOCK_TICKING =
            "com.ishland.c2me.rewrites.chunksystem.common.statuses.ServerBlockTicking";
    private static final String C2ME_SERIALIZER_ACCESS =
            "com.ishland.c2me.base.common.registry.SerializerAccess";
    private static final String YA_LIGHT_MIXIN_PREFIX = MIXIN_PACKAGE + "yalight.";
    private static final String GC_FREE_MIXIN_PREFIX = MIXIN_PACKAGE + "gcfree.";
    private static final String FASTNOISE_COMPAT_MIXIN =
            "com.moepus.byepregen.mixin.compat.FastNoiseFastChunkSectionMixin";
    private static final String FASTNOISE_BIOME_COMPAT_MIXIN =
            "com.moepus.byepregen.mixin.compat.FastNoiseFastBiomeGenMixin";
    private static final String PLACED_FEATURE_MIXIN =
            "com.moepus.byepregen.mixin.PlacedFeatureMixin";
    private static final String PLACEMENT_MODIFIER_MIXIN =
            "com.moepus.byepregen.mixin.PlacementModifierMixin";
    private static final String PLACEMENT_MIXIN_PREFIX =
            "com.moepus.byepregen.mixin.placement.";
    private static final String PALETTED_CONTAINER_NO_LITHIUM =
            MIXIN_PACKAGE + "PalettedContainerNoLithiumMixin";
    private static final String VANILLA_CHUNK_STATUS_PRENORM_MIXIN =
            MIXIN_PACKAGE + "ChunkStatusPostProcessingPreNormMixin";
    private static final String C2ME_COMPAT_MIXIN =
            MIXIN_PACKAGE + "compat.C2MEServerBlockTickingMixin";
    private static final String C2ME_HOOK_COMPATIBILITY_MIXIN =
            MIXIN_PACKAGE + "compat.C2MEHookCompatibilityMixin";
    private static final String ARCHITECTURY_EVENT_ACCESSOR =
            MIXIN_PACKAGE + "accessor.ArchitecturyEventImplAccessor";
    private static final String CHUNK_ACCESS_ARENA_MIXIN =
            MIXIN_PACKAGE + "ChunkAccessArenaMixin";
    private static final String CHUNK_SERIALIZER_ARENA_READ_MIXIN =
            MIXIN_PACKAGE + "ChunkSerializerArenaReadMixin";
    private static final String LEVEL_CHUNK_ARENA_MIXIN =
            MIXIN_PACKAGE + "LevelChunkArenaMixin";
    private static final String FASTNOISE_ARENA_MIXIN =
            MIXIN_PACKAGE + "compat.FastNoiseFastChunkSectionMixin";
    private static final String VOXY_ARENA_MIXIN =
            MIXIN_PACKAGE + "compat.VoxyWorldConversionFactoryMixin";

    private static final Set<String> GC_FREE_SATELLITE_MIXINS = Set.of(
            C2ME_HOOK_COMPATIBILITY_MIXIN,
            ARCHITECTURY_EVENT_ACCESSOR
    );
    private static final Set<String> RAW_GC_FREE_MIXINS = Set.of(
            GC_FREE_MIXIN_PREFIX + "ChunkMapGcFreeSaveMixin",
            GC_FREE_MIXIN_PREFIX + "ChunkStorageRawMixin",
            GC_FREE_MIXIN_PREFIX + "IOWorkerRawMixin",
            MIXIN_PACKAGE + "accessor.IOWorkerPendingStoreAccessor",
            MIXIN_PACKAGE + "accessor.RegionFileStorageAccessor"
    );
    private static final Set<String> ARENA_MIXINS = Set.of(
            CHUNK_ACCESS_ARENA_MIXIN,
            CHUNK_SERIALIZER_ARENA_READ_MIXIN,
            LEVEL_CHUNK_ARENA_MIXIN,
            FASTNOISE_ARENA_MIXIN,
            VOXY_ARENA_MIXIN
    );

    private static final MixinGateEvaluator MIXIN_GATE_EVALUATOR = MixinGateEvaluator.createDefault();

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Config config = ConfigParser.getConfig();
        return passesFeatureGate(mixinClassName, config, MixinPlugin::hasClass)
                && passesCompatibilityGate(mixinClassName, MixinPlugin::hasClass)
                && MIXIN_GATE_EVALUATOR.shouldApply(targetClassName, mixinClassName, config);
    }

    static boolean passesFeatureGate(
            String mixinClassName,
            Config config,
            Predicate<String> classExists
    ) {
        if (mixinClassName.startsWith(YA_LIGHT_MIXIN_PREFIX)) {
            return config.enableYALightEngine;
        }
        if (mixinClassName.equals(PLACED_FEATURE_MIXIN)
                || mixinClassName.equals(PLACEMENT_MODIFIER_MIXIN)
                || mixinClassName.startsWith(PLACEMENT_MIXIN_PREFIX)) {
            return config.enablePlacedFeatureMixin;
        }
        if (isGcFreeMixin(mixinClassName)) {
            return passesGcFreeGate(mixinClassName, config, classExists);
        }
        if (ARENA_MIXINS.contains(mixinClassName)) {
            return passesArenaGate(mixinClassName, config);
        }
        return true;
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
        if (isModExist("confluence")) {
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

    private static boolean passesCompatibilityGate(String mixinClassName, Predicate<String> classExists) {
        boolean hasC2MEChunkSystem = classExists.test(C2ME_SERVER_BLOCK_TICKING);
        if (VANILLA_CHUNK_STATUS_PRENORM_MIXIN.equals(mixinClassName)) {
            return !hasC2MEChunkSystem;
        }
        if (C2ME_COMPAT_MIXIN.equals(mixinClassName)) {
            return hasC2MEChunkSystem;
        }

        return switch (mixinClassName) {
            case FASTNOISE_COMPAT_MIXIN ->
                    classExists.test("org.codeberg.zenxarch.fastnoise.noise.FastChunkSection");
            case FASTNOISE_BIOME_COMPAT_MIXIN ->
                    classExists.test("org.codeberg.zenxarch.fastnoise.noise.FastBiomeGen");
            case PALETTED_CONTAINER_NO_LITHIUM ->
                    !isModExist("lithium");
            default -> true;
        };
    }

    public static boolean isModExist(String modId) {
        return isRuntimeModLoaded(modId) || isLoadingModPresent(modId);
    }

    private static boolean isRuntimeModLoaded(String modId) {
        try {
            Class<?> modListClass = Class.forName("net.minecraftforge.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            return (Boolean) modListClass.getMethod("isLoaded", String.class).invoke(modList, modId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isLoadingModPresent(String modId) {
        try {
            Class<?> loadingModListClass = Class.forName("net.minecraftforge.fml.loading.LoadingModList");
            Object modList = loadingModListClass.getMethod("get").invoke(null);
            return loadingModListClass.getMethod("getModFileById", String.class).invoke(modList, modId) != null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
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

    public static boolean hasClass(String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
