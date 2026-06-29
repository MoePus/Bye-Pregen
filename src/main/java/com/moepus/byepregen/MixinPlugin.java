package com.moepus.byepregen;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class MixinPlugin implements IMixinConfigPlugin {
    private static final String C2ME_COMPAT_MIXIN =
            "com.moepus.byepregen.mixin.compat.C2MEServerBlockTickingMixin";
    private static final String C2ME_HOOK_COMPATIBILITY_MIXIN =
            "com.moepus.byepregen.mixin.compat.C2MEHookCompatibilityMixin";
    private static final String VANILLA_CHUNK_STATUS_PRENORM_MIXIN =
            "com.moepus.byepregen.mixin.ChunkStatusPostProcessingPreNormMixin";
    private static final String FASTNOISE_COMPAT_MIXIN =
            "com.moepus.byepregen.mixin.compat.FastNoiseFastChunkSectionMixin";
    private static final String FASTNOISE_BIOME_COMPAT_MIXIN =
            "com.moepus.byepregen.mixin.compat.FastNoiseFastBiomeGenMixin";
    private static final String VOXY_WORLD_CONVERSION_FACTORY_MIXIN =
            "com.moepus.byepregen.mixin.compat.VoxyWorldConversionFactoryMixin";
    private static final String SABLE_NATURAL_SPAWNER_MIXIN =
            "com.moepus.byepregen.mixin.compat.SableNaturalSpawnerMixin";
    private static final String PLACED_FEATURE_MIXIN =
            "com.moepus.byepregen.mixin.PlacedFeatureMixin";
    private static final String CHUNK_ACCESS_ARENA_MIXIN =
            "com.moepus.byepregen.mixin.ChunkAccessArenaMixin";
    private static final String LEVEL_CHUNK_ARENA_MIXIN =
            "com.moepus.byepregen.mixin.LevelChunkArenaMixin";
    private static final String CHUNK_SERIALIZER_ARENA_READ_MIXIN =
            "com.moepus.byepregen.mixin.ChunkSerializerArenaReadMixin";
    private static final String ARCHITECTURY_EVENT_ACCESSOR =
            "com.moepus.byepregen.mixin.accessor.ArchitecturyEventImplAccessor";
    private static final String C2ME_SERIALIZER_ACCESS =
            "com.ishland.c2me.base.common.registry.SerializerAccess";
    private static final String C2ME_HOOK_COMPATIBILITY =
            "com.ishland.c2me.base.common.util.HookCompatibility";
    private static final String SABLE_ACTIVE_COMPANION =
            "dev.ryanhcode.sable.companion.ActiveSableCompanion";
    private static final String GC_FREE_MIXIN_PREFIX =
            "com.moepus.byepregen.mixin.gcfree.";
    private static final String CHUNK_MAP_GC_FREE_SAVE_MIXIN =
            "com.moepus.byepregen.mixin.gcfree.ChunkMapGcFreeSaveMixin";
    private static final String CHUNK_STORAGE_RAW_MIXIN =
            "com.moepus.byepregen.mixin.gcfree.ChunkStorageRawMixin";
    private static final String IO_WORKER_RAW_MIXIN =
            "com.moepus.byepregen.mixin.gcfree.IOWorkerRawMixin";
    private static final String CHUNK_STORAGE_ACCESSOR =
            "com.moepus.byepregen.mixin.accessor.ChunkStorageAccessor";
    private static final String IO_WORKER_PENDING_STORE_ACCESSOR =
            "com.moepus.byepregen.mixin.accessor.IOWorkerPendingStoreAccessor";
    private static final String REGION_FILE_STORAGE_ACCESSOR =
            "com.moepus.byepregen.mixin.accessor.RegionFileStorageAccessor";
    private static final String PALETTED_CONTAINER_NO_LITHIUM =
            "com.moepus.byepregen.mixin.PalettedContainerNoLithiumMixin";
    private static final String LITHIUM_HASH_PALETTE_MIXIN =
            "com.moepus.byepregen.mixin.compat.LithiumHashPaletteMixin";
    private static final String SODIUM_LIGHT_DATA_ACCESS_YA_LIGHT_MIXIN =
            "com.moepus.byepregen.mixin.compat.SodiumLightDataAccessYALightMixin";
    private static final String SABLE_SERVER_LEVEL_PLOT_YA_LIGHT_MIXIN =
            "com.moepus.byepregen.mixin.yalight.compat.SableServerLevelPlotYALightMixin";
    private static final String SERVER_CHUNK_CACHE_TICK_CHUNKS_MIXIN =
            "com.moepus.byepregen.mixin.ServerChunkCacheTickChunksMixin";
    private static final String YA_LIGHT_MIXIN_PREFIX =
            "com.moepus.byepregen.mixin.yalight.";
    private static final String CLIENT_OPTIMIZATION_MIXIN_PREFIX =
            "com.moepus.byepregen.mixin.client.";

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
        boolean gcFreeSaveEnabled = config.enableGcFreeWorldgenSave;
        boolean arenaEnabled = config.enableArenaPalette;
        boolean serverRuntimeArena = arenaEnabled && config.enableServerRuntimeArenaPalette;
        boolean clientOptimizationsEnabled = config.enableClientOptimizations;
        boolean clientArena = clientOptimizationsEnabled && arenaEnabled && config.enableClientArenaPalette;
        return switch (mixinClassName) {
            case C2ME_COMPAT_MIXIN ->
                    hasClass("com.ishland.c2me.rewrites.chunksystem.common.statuses.ServerBlockTicking");
            case C2ME_HOOK_COMPATIBILITY_MIXIN ->
                    gcFreeSaveEnabled && hasClass(C2ME_HOOK_COMPATIBILITY);
            case VANILLA_CHUNK_STATUS_PRENORM_MIXIN ->
                    !hasClass("com.ishland.c2me.rewrites.chunksystem.common.statuses.ServerBlockTicking");
            case FASTNOISE_COMPAT_MIXIN ->
                    arenaEnabled && hasClass("org.codeberg.zenxarch.fastnoise.noise.FastChunkSection");
            case FASTNOISE_BIOME_COMPAT_MIXIN ->
                    hasClass("org.codeberg.zenxarch.fastnoise.noise.FastBiomeGen");
            case VOXY_WORLD_CONVERSION_FACTORY_MIXIN ->
                    clientArena && hasClass("me.cortex.voxy.common.voxelization.WorldConversionFactory");
            case SABLE_NATURAL_SPAWNER_MIXIN ->
                    hasClass(SABLE_ACTIVE_COMPANION);
            case PLACED_FEATURE_MIXIN ->
                    config.enablePlacedFeatureMixin;
            case SERVER_CHUNK_CACHE_TICK_CHUNKS_MIXIN ->
                    config.enableFastTickChunks;
            case CHUNK_ACCESS_ARENA_MIXIN, CHUNK_SERIALIZER_ARENA_READ_MIXIN ->
                    arenaEnabled;
            case LEVEL_CHUNK_ARENA_MIXIN ->
                    arenaEnabled && !serverRuntimeArena;
            case ARCHITECTURY_EVENT_ACCESSOR ->
                    gcFreeSaveEnabled && hasClass("dev.architectury.event.EventFactory$EventImpl");
            case CHUNK_MAP_GC_FREE_SAVE_MIXIN,
                 CHUNK_STORAGE_RAW_MIXIN,
                 IO_WORKER_RAW_MIXIN,
                 CHUNK_STORAGE_ACCESSOR,
                 IO_WORKER_PENDING_STORE_ACCESSOR,
                 REGION_FILE_STORAGE_ACCESSOR ->
                    gcFreeSaveEnabled && !hasClass(C2ME_SERIALIZER_ACCESS);
            case PALETTED_CONTAINER_NO_LITHIUM ->
                    !isModExist("lithium");
            case LITHIUM_HASH_PALETTE_MIXIN ->
                    isModExist("lithium") && hasClass("net.caffeinemc.mods.lithium.common.world.chunk.LithiumHashPalette");
            case SODIUM_LIGHT_DATA_ACCESS_YA_LIGHT_MIXIN ->
                    config.enableYALightEngine && isModExist("sodium")
                            && hasClass("net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess");
            case SABLE_SERVER_LEVEL_PLOT_YA_LIGHT_MIXIN ->
                    config.enableYALightEngine && isModExist("sable");
            default ->
                    (mixinClassName.startsWith(YA_LIGHT_MIXIN_PREFIX) ? config.enableYALightEngine :
                            mixinClassName.startsWith(CLIENT_OPTIMIZATION_MIXIN_PREFIX) ? clientOptimizationsEnabled :
                            !mixinClassName.startsWith(GC_FREE_MIXIN_PREFIX) || gcFreeSaveEnabled);
        };
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
