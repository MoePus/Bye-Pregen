package com.moepus.byepregen.gcfree;

import com.moepus.byepregen.compat.C2MEAsyncSerializationCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

public final class GcFreeChunkSerializer {
    private static final int MAX_RAW_BUFFER_SLACK_BYTES = 8192;

    private GcFreeChunkSerializer() {
    }

    public static byte[] serializeRaw(ServerLevel level, ChunkAccess chunk) {
        NbtWriter writer = writeRaw(level, chunk);
        try {
            return writer.toByteArray();
        } finally {
            writer.release();
        }
    }

    public static RawChunkData serializeRawData(ServerLevel level, ChunkAccess chunk) {
        NbtWriter writer = writeRaw(level, chunk);
        try {
            return writer.toRawChunkData(MAX_RAW_BUFFER_SLACK_BYTES);
        } finally {
            writer.release();
        }
    }

    private static NbtWriter writeRaw(ServerLevel level, ChunkAccess chunk) {
        NbtWriter writer = new NbtWriter();
        try {
            writer.startRootCompound();
            ChunkDataSerializer.write(level, chunk, writer);
            writer.finishCompound();
            if (chunk instanceof WorldgenChunkState state) {
                state.byepregen$setFreshWorldgenChunk(false);
            }
            return writer;
        } catch (RuntimeException | Error throwable) {
            writer.release();
            throw throwable;
        }
    }

    public static boolean shouldUseGcFree(ChunkAccess chunk) {
        if (!ChunkSaveHookGate.CAN_USE_RAW_SAVE) {
            return false;
        }
        return isEligible(chunk);
    }

    private static boolean isEligible(ChunkAccess chunk) {
        ChunkStatus.ChunkType chunkType = chunk.getStatus().getChunkType();
        if (chunkType == ChunkStatus.ChunkType.PROTOCHUNK) {
            return true;
        }
        if (isFreshLevelChunk(chunk, chunkType)) {
            return true;
        }
        return !hasBlockEntities(chunk);
    }

    private static boolean isFreshLevelChunk(ChunkAccess chunk, ChunkStatus.ChunkType chunkType) {
        return chunkType == ChunkStatus.ChunkType.LEVELCHUNK
                && chunk instanceof WorldgenChunkState state
                && state.byepregen$isFreshWorldgenChunk();
    }

    static boolean hasC2MEAsyncSerializationManager() {
        return C2MEAsyncSerializationCompat.isAvailable();
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
