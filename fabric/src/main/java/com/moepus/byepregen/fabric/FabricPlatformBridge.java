package com.moepus.byepregen.fabric;

import com.moepus.byepregen.integration.platform.PlatformBridge;
import java.nio.file.Path;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatformBridge implements PlatformBridge {
    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        Objects.requireNonNull(modId, "modId");
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean supportsGcFreeRawChunkIo() {
        return true;
    }

    @Override
    public boolean canUseGcFreeRawChunkSave() {
        return FabricChunkSaveHookGate.canUseRawSave();
    }

    @Override
    public void writeChunkAttachments(ChunkAttachmentContext context) {
        FabricChunkAttachments.write(context);
    }

    @Override
    public void writeLevelChunkExtras(LevelChunkExtrasContext context) {
    }
}
