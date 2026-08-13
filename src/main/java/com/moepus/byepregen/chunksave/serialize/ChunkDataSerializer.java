package com.moepus.byepregen.chunksave.serialize;

/* Adapted from C2ME's GC-free chunk serializer. MIT License, copyright (c) 2021-2024 ishland. */

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class ChunkDataSerializer {
    private static final byte[] DATA_VERSION = NbtWriter.asciiName("DataVersion");
    private static final byte[] X_POS = NbtWriter.asciiName("xPos");
    private static final byte[] Y_POS = NbtWriter.asciiName("yPos");
    private static final byte[] Z_POS = NbtWriter.asciiName("zPos");
    private static final byte[] LAST_UPDATE = NbtWriter.asciiName("LastUpdate");
    private static final byte[] INHABITED_TIME = NbtWriter.asciiName("InhabitedTime");
    private static final byte[] STATUS = NbtWriter.asciiName("Status");
    private static final byte[] IS_LIGHT_ON = NbtWriter.asciiName("isLightOn");
    private static final ConcurrentHashMap<ChunkStatus, byte[]> STATUS_NAMES = new ConcurrentHashMap<>();

    private ChunkDataSerializer() {
    }

    public static void write(ServerLevel level, ChunkAccess chunk, NbtWriter writer) {
        ChunkSectionSerializationContext sectionContext = new ChunkSectionSerializationContext();
        ChunkPos pos = chunk.getPos();
        writer.putInt(DATA_VERSION, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        writer.putInt(X_POS, pos.x);
        writer.putInt(Y_POS, chunk.getMinSection());
        writer.putInt(Z_POS, pos.z);
        writer.putLong(LAST_UPDATE, level.getGameTime());
        writer.putLong(INHABITED_TIME, chunk.getInhabitedTime());
        writer.putString(STATUS, statusName(chunk.getPersistedStatus()));
        ChunkInlineDataWriter.write(writer, chunk);
        ChunkSectionDataWriter.write(writer, level, chunk, pos, sectionContext);
        if (chunk.isLightCorrect()) {
            writer.putBoolean(IS_LIGHT_ON, true);
        }
        ChunkBlockEntityDataWriter.write(writer, level, chunk);
        ChunkTypeExtrasWriter.write(writer, level, chunk, pos);
        ChunkTickPostProcessWriter.writeTicks(writer, level, chunk);
        ChunkTickPostProcessWriter.writePostProcessing(writer, chunk.getPostProcessing());
        ChunkTickPostProcessWriter.writeHeightmaps(writer, chunk);
        ChunkAttachmentWriter.write(writer, level, chunk);
        ChunkStructureDataWriter.write(writer, level, pos, chunk);
    }

    private static byte[] statusName(ChunkStatus status) {
        return STATUS_NAMES.computeIfAbsent(
                status, key -> NbtWriter.stringBytes(BuiltInRegistries.CHUNK_STATUS.getKey(key).toString()));
    }
}
