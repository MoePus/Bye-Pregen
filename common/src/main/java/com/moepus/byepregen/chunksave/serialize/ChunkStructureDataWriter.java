package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

final class ChunkStructureDataWriter {
    private static final byte[] STRUCTURES = NbtWriter.asciiName("structures");
    private static final byte[] STARTS = NbtWriter.asciiName("starts");
    private static final byte[] REFERENCES = NbtWriter.asciiName("References");
    private ChunkStructureDataWriter() {
    }

    static void write(NbtWriter writer, ServerLevel level, ChunkPos pos, ChunkAccess chunk) {
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        writer.startCompound(STRUCTURES);
        writeStarts(writer, context, registry, pos, chunk.getAllStarts());
        writeReferences(writer, registry, chunk.getAllReferences());
        writer.finishCompound();
    }

    private static void writeStarts(
            NbtWriter writer, StructurePieceSerializationContext context, Registry<Structure> registry,
            ChunkPos pos, Map<Structure, StructureStart> starts) {
        writer.startCompound(STARTS);
        for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
            Identifier id = registry.getKey(entry.getKey());
            writer.putTag(NbtWriter.asciiName(id.toString()), entry.getValue().createTag(context, pos));
        }
        writer.finishCompound();
    }

    private static void writeReferences(
            NbtWriter writer, Registry<Structure> registry, Map<Structure, LongSet> references) {
        writer.startCompound(REFERENCES);
        for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                Identifier id = registry.getKey(entry.getKey());
                writer.putLongArray(NbtWriter.asciiName(id.toString()), entry.getValue());
            }
        }
        writer.finishCompound();
    }
}
