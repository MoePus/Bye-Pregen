package com.moepus.byepregen.integration.platform;

import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

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
    public boolean hasDynamicLightEmission(BlockState state) {
        return false;
    }

    @Override
    public int lightEmission(BlockState state, BlockGetter world, BlockPos pos) {
        return state.getLightEmission();
    }

    @Override
    public void writeChunkAttachments(ChunkAttachmentContext context) {
    }

    @Override
    public void writeLevelChunkExtras(LevelChunkExtrasContext context) {
    }
}
