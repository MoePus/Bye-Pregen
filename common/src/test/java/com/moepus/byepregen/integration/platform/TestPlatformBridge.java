package com.moepus.byepregen.integration.platform;

import java.nio.file.Path;

public final class TestPlatformBridge implements PlatformBridge {
    @Override
    public Path configDirectory() {
        return Path.of("build", "test-config");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return false;
    }

    @Override
    public boolean supportsGcFreeRawChunkIo() {
        return false;
    }

    @Override
    public boolean canUseGcFreeRawChunkSave() {
        return false;
    }

    @Override
    public void writeChunkAttachments(ChunkAttachmentContext context) {
    }

    @Override
    public void writeLevelChunkExtras(LevelChunkExtrasContext context) {
    }
}
