package com.moepus.byepregen.integration.platform;

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public interface PlatformBridge {
    Path configDirectory();

    boolean isModLoaded(String modId);

    boolean supportsGcFreeRawChunkIo();

    boolean canUseGcFreeRawChunkSave();

    /** Whether light emission can vary while the BlockState stays unchanged. */
    boolean hasDynamicLightEmission(BlockState state);

    /** Uses the loader's world-aware light hook, or vanilla's state-only value on Fabric. */
    int lightEmission(BlockState state, BlockGetter world, BlockPos pos);

    void writeChunkAttachments(ChunkAttachmentContext context);

    void writeLevelChunkExtras(LevelChunkExtrasContext context);

    record ChunkAttachmentContext(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
    }

    record LevelChunkExtrasContext(NbtWriter writer, ServerLevel level, ChunkAccess chunk, ChunkPos pos) {
    }
}
