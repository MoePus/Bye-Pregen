package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

final class ChunkTickPostProcessWriter {
    private static final byte[] BLOCK_TICKS = NbtWriter.asciiName("block_ticks");
    private static final byte[] FLUID_TICKS = NbtWriter.asciiName("fluid_ticks");
    private static final byte[] POST_PROCESSING = NbtWriter.asciiName("PostProcessing");
    private static final byte[] HEIGHTMAPS = NbtWriter.asciiName("Heightmaps");
    private static final ConcurrentHashMap<Heightmap.Types, byte[]> HEIGHTMAP_NAMES = new ConcurrentHashMap<>();

    private ChunkTickPostProcessWriter() {
    }

    static void writeTicks(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
        long time = level.getLevelData().getGameTime();
        ChunkAccess.TicksToSave ticks = chunk.getTicksForSerialization();
        writer.putTag(BLOCK_TICKS, ticks.blocks().save(
                time, block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        writer.putTag(FLUID_TICKS, ticks.fluids().save(
                time, fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()));
    }

    static void writePostProcessing(NbtWriter writer, ShortList[] lists) {
        writer.startFixedList(POST_PROCESSING, lists.length, Tag.TAG_LIST);
        for (ShortList list : lists) {
            int size = list == null ? 0 : list.size();
            writer.startFixedListEntry(size, size == 0 ? Tag.TAG_END : Tag.TAG_SHORT);
            for (int i = 0; i < size; ++i) {
                writer.writeShort(list.getShort(i));
            }
        }
    }

    static void writeHeightmaps(NbtWriter writer, ChunkAccess chunk) {
        writer.startCompound(HEIGHTMAPS);
        for (java.util.Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getPersistedStatus().getChunkSaveHeightmaps().contains(entry.getKey())) {
                writer.putLongArray(heightmapName(entry.getKey()), entry.getValue().getRawData());
            }
        }
        writer.finishCompound();
    }

    private static byte[] heightmapName(Heightmap.Types type) {
        return HEIGHTMAP_NAMES.computeIfAbsent(type, key -> NbtWriter.asciiName(key.getSerializationKey()));
    }
}
