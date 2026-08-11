package com.moepus.byepregen.gcfree;

/*
 * Portions adapted from C2ME's GC-free chunk serializer.
 *
 * MIT License
 * Copyright (c) 2021-2024 ishland
 */

import com.moepus.byepregen.MixinPlugin;
import com.moepus.byepregen.compat.C2MEAsyncSerializationCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkType;

public final class GcFreeChunkSerializer {
    private static final int MAX_RAW_BUFFER_SLACK_BYTES = 8192;
    private static final String C2ME_ASYNC_SERIALIZATION_MANAGER =
            "com.ishland.c2me.rewrites.chunksystem.common.async_chunkio.AsyncSerializationManager";
    private static final boolean HAS_C2ME_ASYNC_SERIALIZATION_MANAGER =
            MixinPlugin.hasClass(C2ME_ASYNC_SERIALIZATION_MANAGER);

    private GcFreeChunkSerializer() {
    }

    public static byte[] serializeRaw(ServerLevel level, ChunkAccess chunk) {
        try (ChunkSavingNbtWriterCache.Lease lease = ChunkSavingNbtWriterCache.acquire()) {
            NbtWriter writer = lease.writer();
            writeRaw(level, chunk, writer);
            return writer.toByteArray();
        }
    }

    public static RawChunkData serializeRawData(ServerLevel level, ChunkAccess chunk) {
        try (ChunkSavingNbtWriterCache.Lease lease = ChunkSavingNbtWriterCache.acquire()) {
            NbtWriter writer = lease.writer();
            writeRaw(level, chunk, writer);
            return writer.toRawChunkData(MAX_RAW_BUFFER_SLACK_BYTES);
        }
    }

    private static void writeRaw(ServerLevel level, ChunkAccess chunk, NbtWriter writer) {
        writer.startRootCompound();
        ChunkDataSerializer.write(level, chunk, writer);
        writer.finishCompound();
        if (chunk instanceof WorldgenChunkState state) {
            state.byepregen$setFreshWorldgenChunk(false);
        }
    }

    public static boolean shouldUseGcFree(ChunkAccess chunk) {
        if (!ChunkSaveHookGate.CAN_USE_RAW_SAVE) {
            return false;
        }
        return isEligible(chunk);
    }

    private static boolean isEligible(ChunkAccess chunk) {
        ChunkType chunkType = chunk.getPersistedStatus().getChunkType();
        if (chunkType == ChunkType.PROTOCHUNK) {
            return true;
        }
        if (isFreshLevelChunk(chunk, chunkType)) {
            return true;
        }
        return !hasBlockEntities(chunk);
    }

    private static boolean isFreshLevelChunk(ChunkAccess chunk, ChunkType chunkType) {
        return chunkType == ChunkType.LEVELCHUNK
                && chunk instanceof WorldgenChunkState state
                && state.byepregen$isFreshWorldgenChunk();
    }

    static boolean hasC2MEAsyncSerializationManager() {
        return HAS_C2ME_ASYNC_SERIALIZATION_MANAGER;
    }

    private static boolean hasBlockEntities(ChunkAccess chunk) {
        if (hasC2MEAsyncSerializationManager()) {
            return C2MEAsyncSerializationCompat.hasBlockEntities(chunk);
        }
        return !chunk.getBlockEntitiesPos().isEmpty();
    }

    public record SerializedChunk(CompoundTag tag, byte[] rawBytes) {
        public static SerializedChunk vanilla(CompoundTag tag) {
            return new SerializedChunk(tag, null);
        }

        public static SerializedChunk raw(byte[] rawBytes) {
            return new SerializedChunk(null, rawBytes);
        }

        public boolean isRaw() {
            return this.rawBytes != null;
        }
    }
}
