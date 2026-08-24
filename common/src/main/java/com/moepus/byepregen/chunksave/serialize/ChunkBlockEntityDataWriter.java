package com.moepus.byepregen.chunksave.serialize;

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
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            Tag tag = chunk.getBlockEntityNbtForSaving(pos, level.registryAccess());
            if (tag != null) {
                writer.putTagEntry(tag);
                ++count;
            }
        }
        writer.finishList(listStart, count);
    }
}
