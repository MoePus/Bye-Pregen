package com.moepus.byepregen.integration.neoforge;

import com.moepus.byepregen.integration.platform.PlatformBridge;
import java.nio.file.Path;
import java.util.Objects;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;

public final class NeoForgePlatformBridge implements PlatformBridge {
    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        Objects.requireNonNull(modId, "modId");
        LoadingModList modList = LoadingModList.get();
        if (modList.getModFileById(modId) != null) {
            return true;
        }
        return modList.getPlugins().stream()
                .filter(ModFileInfo.class::isInstance)
                .map(ModFileInfo.class::cast)
                .anyMatch(file -> file.getMods().stream().anyMatch(mod -> mod.getModId().equals(modId)));
    }

    @Override
    public boolean supportsGcFreeRawChunkIo() {
        return true;
    }

    @Override
    public boolean canUseGcFreeRawChunkSave() {
        return NeoForgeChunkSaveHookGate.canUseRawSave();
    }

    @Override
    public void writeChunkAttachments(ChunkAttachmentContext context) {
        NeoForgeChunkAttachments.write(context);
    }

    @Override
    public void writeLevelChunkExtras(LevelChunkExtrasContext context) {
        NeoForgeLevelChunkExtras.write(context);
    }
}
