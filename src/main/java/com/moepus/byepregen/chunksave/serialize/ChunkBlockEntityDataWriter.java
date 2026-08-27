package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.integration.c2me.C2MEAsyncSerializationCompat;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

final class ChunkBlockEntityDataWriter {
    private static final byte[] BLOCK_ENTITIES = NbtWriter.asciiName("block_entities");

    private ChunkBlockEntityDataWriter() {
    }

    static void write(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
        long listStart = writer.startList(BLOCK_ENTITIES, Tag.TAG_COMPOUND);
        int count = 0;
        for (BlockPos pos : blockEntityPositions(chunk)) {
            Tag tag = blockEntityNbtForSaving(chunk, pos);
            if (tag != null) {
                writer.putTagEntry(tag);
                ++count;
            }
        }
        writer.finishList(listStart, count);
    }

    private static Iterable<BlockPos> blockEntityPositions(ChunkAccess chunk) {
        if (GcFreeChunkSerializer.hasC2MEAsyncSerializationManager()) {
            return C2MEAsyncSerializationCompat.blockEntityPositions(chunk);
        }
        return chunk.getBlockEntitiesPos();
    }

    private static Tag blockEntityNbtForSaving(ChunkAccess chunk, BlockPos pos) {
        if (GcFreeChunkSerializer.hasC2MEAsyncSerializationManager()) {
            return C2MEAsyncSerializationCompat.blockEntityNbtForSaving(chunk, pos);
        }
        return chunk.getBlockEntityNbtForSaving(pos);
    }
}
