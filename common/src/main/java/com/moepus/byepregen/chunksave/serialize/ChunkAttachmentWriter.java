package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.integration.platform.PlatformBridge.ChunkAttachmentContext;
import com.moepus.byepregen.integration.platform.PlatformServices;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

final class ChunkAttachmentWriter {
    private ChunkAttachmentWriter() {
    }

    static void write(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
        PlatformServices.get().writeChunkAttachments(new ChunkAttachmentContext(writer, level, chunk));
    }
}
