package com.moepus.byepregen.gcfree;

/*
 * Portions adapted from C2ME's GC-free chunk serializer.
 *
 * MIT License
 * Copyright (c) 2021-2024 ishland
 */

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

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
        if (!isWorldgenSave(chunk)) {
            return false;
        }
        return ChunkSaveHookGate.CAN_USE_RAW_SAVE;
    }

    private static boolean isWorldgenSave(ChunkAccess chunk) {
        return chunk.getPersistedStatus().getChunkType() == ChunkType.LEVELCHUNK
                && chunk instanceof WorldgenChunkState state
                && state.byepregen$isFreshWorldgenChunk();
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
