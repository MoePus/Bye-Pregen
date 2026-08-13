package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.LevelChunkAuxiliaryLightManager;

final class ChunkTypeExtrasWriter {
    private static final byte[] ENTITIES = NbtWriter.asciiName("entities");
    private static final byte[] CARVING_MASKS = NbtWriter.asciiName("CarvingMasks");
    private static final byte[] AUX_LIGHT = NbtWriter.asciiName(LevelChunkAuxiliaryLightManager.LIGHT_NBT_KEY);
    private static final GenerationStep.Carving[] CARVINGS = GenerationStep.Carving.values();
    private static final byte[][] CARVING_MASK_NAMES = createCarvingMaskNames();

    private ChunkTypeExtrasWriter() {
    }

    static void write(NbtWriter writer, ServerLevel level, ChunkAccess chunk, ChunkPos pos) {
        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            writeProtoChunk(writer, (ProtoChunk) chunk);
        } else if (chunk instanceof LevelChunk levelChunk) {
            Tag lightTag = levelChunk.getAuxLightManager(pos).serializeNBT(level.registryAccess());
            if (lightTag != null) {
                writer.putTag(AUX_LIGHT, lightTag);
            }
        }
    }

    private static void writeProtoChunk(NbtWriter writer, ProtoChunk chunk) {
        writer.startFixedList(ENTITIES, chunk.getEntities().size(), Tag.TAG_COMPOUND);
        for (Tag entity : chunk.getEntities()) {
            writer.putTagEntry(entity);
        }
        writer.startCompound(CARVING_MASKS);
        for (GenerationStep.Carving carving : CARVINGS) {
            CarvingMask mask = chunk.getCarvingMask(carving);
            if (mask != null) {
                writer.putLongArray(CARVING_MASK_NAMES[carving.ordinal()], mask.toArray());
            }
        }
        writer.finishCompound();
    }

    private static byte[][] createCarvingMaskNames() {
        byte[][] names = new byte[CARVINGS.length][];
        for (int i = 0; i < CARVINGS.length; ++i) {
            names[i] = NbtWriter.asciiName(CARVINGS[i].toString());
        }
        return names;
    }
}
