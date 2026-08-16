package com.moepus.byepregen.chunksave.serialize;

/*
 * Portions adapted from C2ME's GC-free chunk serializer.
 *
 * MIT License
 * Copyright (c) 2021-2024 ishland
 */

import com.moepus.byepregen.chunksave.compat.ChunkSaveHookGate;
import com.moepus.byepregen.chunksave.storage.RawChunkData;
import com.moepus.byepregen.integration.c2me.C2MEAsyncSerializationCompat;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkType;
import org.slf4j.Logger;

public final class GcFreeChunkSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String C2ME_ASYNC_SERIALIZATION_MANAGER =
            "com.ishland.c2me.rewrites.chunksystem.common.async_chunkio.AsyncSerializationManager";
    private static final C2meAsyncAvailability C2ME_ASYNC_AVAILABILITY = detectC2meAsyncAvailability();

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
            byte[] bytes = writer.toByteArray();
            return new RawChunkData(bytes, bytes.length);
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
        if (C2ME_ASYNC_AVAILABILITY.lookupFailed() || !ChunkSaveHookGate.CAN_USE_RAW_SAVE) {
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
        return C2ME_ASYNC_AVAILABILITY.available();
    }

    private static boolean hasBlockEntities(ChunkAccess chunk) {
        if (hasC2MEAsyncSerializationManager()) {
            return C2MEAsyncSerializationCompat.hasBlockEntities(chunk);
        }
        return !chunk.getBlockEntitiesPos().isEmpty();
    }

    private static C2meAsyncAvailability detectC2meAsyncAvailability() {
        try {
            return new C2meAsyncAvailability(
                    ModEnvironment.isClassAvailable(C2ME_ASYNC_SERIALIZATION_MANAGER),
                    false
            );
        } catch (RuntimeException | LinkageError throwable) {
            LOGGER.warn("Disabling GC-free chunk serialization: C2ME class lookup failed", throwable);
            return new C2meAsyncAvailability(false, true);
        }
    }

    private record C2meAsyncAvailability(boolean available, boolean lookupFailed) {
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
