package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.integration.platform.PlatformBridge.LevelChunkExtrasContext;
import com.moepus.byepregen.integration.platform.PlatformServices;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkType;

final class ChunkTypeExtrasWriter {
    private static final byte[] ENTITIES = NbtWriter.asciiName("entities");
    private static final byte[] CARVING_MASK = NbtWriter.asciiName("carving_mask");

    private ChunkTypeExtrasWriter() {
    }

    static void write(NbtWriter writer, ServerLevel level, ChunkAccess chunk, ChunkPos pos) {
        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            writeProtoChunk(writer, (ProtoChunk) chunk);
            return;
        }
        PlatformServices.get().writeLevelChunkExtras(new LevelChunkExtrasContext(writer, level, chunk, pos));
    }

    private static void writeProtoChunk(NbtWriter writer, ProtoChunk chunk) {
        writer.startFixedList(ENTITIES, chunk.getEntities().size(), Tag.TAG_COMPOUND);
        for (Tag entity : chunk.getEntities()) {
            writer.putTagEntry(entity);
        }
        CarvingMask mask = chunk.getCarvingMask();
        if (mask != null) {
            writer.putLongArray(CARVING_MASK, mask.toArray());
        }
    }
}
