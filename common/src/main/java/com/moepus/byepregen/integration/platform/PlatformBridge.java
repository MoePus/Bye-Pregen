package com.moepus.byepregen.integration.platform;

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import java.nio.file.Path;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

public interface PlatformBridge {
    Path configDirectory();

    boolean isModLoaded(String modId);

    boolean supportsGcFreeRawChunkIo();

    boolean canUseGcFreeRawChunkSave();

    void writeChunkAttachments(ChunkAttachmentContext context);

    void writeLevelChunkExtras(LevelChunkExtrasContext context);

    record ChunkAttachmentContext(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
    }

    record LevelChunkExtrasContext(NbtWriter writer, ServerLevel level, ChunkAccess chunk, ChunkPos pos) {
    }
}
