package com.moepus.byepregen;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class MixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "com.moepus.byepregen.mixin.";
    private static final String YA_LIGHT_MIXIN_PREFIX = MIXIN_PACKAGE + "yalight.";
    private static final String GC_FREE_MIXIN_PREFIX = MIXIN_PACKAGE + "gcfree.";

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
    private static final String CHUNK_ACCESS_ARENA_MIXIN = MIXIN_PACKAGE + "ChunkAccessArenaMixin";
    private static final String CHUNK_SERIALIZER_ARENA_READ_MIXIN =
            MIXIN_PACKAGE + "ChunkSerializerArenaReadMixin";
    private static final String LEVEL_CHUNK_ARENA_MIXIN = MIXIN_PACKAGE + "LevelChunkArenaMixin";
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
            CHUNK_STORAGE_ACCESSOR,
            IO_WORKER_PENDING_STORE_ACCESSOR,
            REGION_FILE_STORAGE_ACCESSOR
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
        boolean featureEnabled = passesFeatureGate(mixinClassName, config, MixinPlugin::hasClass);
        boolean annotationEnabled = MIXIN_GATE_EVALUATOR.shouldApply(targetClassName, mixinClassName, config);
        return featureEnabled && annotationEnabled;
    }

    static boolean passesFeatureGate(
            String mixinClassName,
            Config config,
            Predicate<String> classExists
    ) {
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
        if (LEVEL_CHUNK_ARENA_MIXIN.equals(mixinClassName)) {
            return !config.enableServerRuntimeArenaPalette;
        }
        if (VOXY_ARENA_MIXIN.equals(mixinClassName)) {
            return config.enableClientArenaPalette;
        }
        return true;
    }

    private static ModFileInfo getModFile(String modId) {
        LoadingModList modList = LoadingModList.get();
        ModFileInfo modFile = modList.getModFileById(modId);
        if (modFile != null) {
            return modFile;
        }

        return modList.getPlugins().stream()
                .filter(ModFileInfo.class::isInstance)
                .map(ModFileInfo.class::cast)
                .filter(file -> file.getMods().stream().anyMatch(mod -> mod.getModId().equals(modId)))
                .findFirst()
                .orElse(null);
    }

    public static boolean isModExist(String modId) {
        return getModFile(modId) != null;
    }

    public static boolean hasClass(String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className);
            return true;
        } catch (Throwable ignored) {
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
}
