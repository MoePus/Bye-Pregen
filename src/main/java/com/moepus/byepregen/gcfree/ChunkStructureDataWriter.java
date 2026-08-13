package com.moepus.byepregen.gcfree;

import com.moepus.byepregen.mixin.accessor.chunksave.StructurePieceSaveAccessor;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

final class ChunkStructureDataWriter {
    private static final byte[] STRUCTURES = NbtWriter.asciiName("structures");
    private static final byte[] STARTS = NbtWriter.asciiName("starts");
    private static final byte[] REFERENCES = NbtWriter.asciiName("References");
    private static final byte[] ID = NbtWriter.asciiName("id");
    private static final byte[] CHUNK_X = NbtWriter.asciiName("ChunkX");
    private static final byte[] CHUNK_Z = NbtWriter.asciiName("ChunkZ");
    private static final byte[] SMALL_REFERENCES = NbtWriter.asciiName("references");
    private static final byte[] CHILDREN = NbtWriter.asciiName("Children");
    private static final String INVALID = "INVALID";

    private ChunkStructureDataWriter() {
    }

    static void write(NbtWriter writer, ServerLevel level, ChunkPos pos, ChunkAccess chunk) {
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
        Registry<Structure> registry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
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
            ResourceLocation id = registry.getKey(entry.getKey());
            if (id != null) {
                writer.startCompound(NbtWriter.asciiName(id.toString()));
                writeStart(writer, context, pos, entry.getValue(), id);
                writer.finishCompound();
            }
        }
        writer.finishCompound();
    }

    private static void writeStart(
            NbtWriter writer, StructurePieceSerializationContext context, ChunkPos pos,
            StructureStart start, ResourceLocation id) {
        if (start.getPieces().isEmpty()) {
            writer.putString(ID, INVALID);
            return;
        }

        writer.putString(ID, id.toString());
        writer.putInt(CHUNK_X, pos.x);
        writer.putInt(CHUNK_Z, pos.z);
        writer.putInt(SMALL_REFERENCES, start.getReferences());
        writer.startFixedList(CHILDREN, start.getPieces().size(), Tag.TAG_COMPOUND);
        for (StructurePiece piece : start.getPieces()) {
            writer.putTagEntry(createPieceTag(context, piece));
        }
    }

    private static CompoundTag createPieceTag(StructurePieceSerializationContext context, StructurePiece piece) {
        CompoundTag tag = new CompoundTag();
        ResourceLocation typeId = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType());
        tag.putString("id", typeId == null ? INVALID : typeId.toString());
        tag.putIntArray("BB", boundingBoxArray(piece.getBoundingBox()));
        Direction orientation = piece.getOrientation();
        tag.putInt("O", orientation == null ? -1 : orientation.get2DDataValue());
        tag.putInt("GD", piece.getGenDepth());
        ((StructurePieceSaveAccessor) piece).byepregen$addAdditionalSaveData(context, tag);
        return tag;
    }

    private static int[] boundingBoxArray(BoundingBox box) {
        return new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
    }

    private static void writeReferences(
            NbtWriter writer, Registry<Structure> registry, Map<Structure, LongSet> references) {
        writer.startCompound(REFERENCES);
        for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                ResourceLocation id = registry.getKey(entry.getKey());
                if (id != null) {
                    writer.putLongArray(NbtWriter.asciiName(id.toString()), entry.getValue());
                }
            }
        }
        writer.finishCompound();
    }
}
