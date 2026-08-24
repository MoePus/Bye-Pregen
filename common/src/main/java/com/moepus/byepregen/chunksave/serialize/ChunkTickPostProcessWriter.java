package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.SavedTick;

final class ChunkTickPostProcessWriter {
    private static final byte[] BLOCK_TICKS = NbtWriter.asciiName("block_ticks");
    private static final byte[] FLUID_TICKS = NbtWriter.asciiName("fluid_ticks");
    private static final byte[] POST_PROCESSING = NbtWriter.asciiName("PostProcessing");
    private static final byte[] HEIGHTMAPS = NbtWriter.asciiName("Heightmaps");
    private static final byte[] ID = NbtWriter.asciiName("i");
    private static final byte[] X = NbtWriter.asciiName("x");
    private static final byte[] Y = NbtWriter.asciiName("y");
    private static final byte[] Z = NbtWriter.asciiName("z");
    private static final byte[] DELAY = NbtWriter.asciiName("t");
    private static final byte[] PRIORITY = NbtWriter.asciiName("p");
    private static final ConcurrentHashMap<Heightmap.Types, byte[]> HEIGHTMAP_NAMES = new ConcurrentHashMap<>();

    private ChunkTickPostProcessWriter() {
    }

    static void writeTicks(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
        ChunkAccess.PackedTicks ticks = chunk.getTicksForSerialization(level.getGameTime());
        writer.startFixedList(BLOCK_TICKS, ticks.blocks().size(), Tag.TAG_COMPOUND);
        for (SavedTick<Block> tick : ticks.blocks()) {
            writeTick(writer, tick, BuiltInRegistries.BLOCK.getKey(tick.type()).toString());
        }
        writer.startFixedList(FLUID_TICKS, ticks.fluids().size(), Tag.TAG_COMPOUND);
        for (SavedTick<Fluid> tick : ticks.fluids()) {
            writeTick(writer, tick, BuiltInRegistries.FLUID.getKey(tick.type()).toString());
        }
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
            if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                writer.putLongArray(heightmapName(entry.getKey()), entry.getValue().getRawData());
            }
        }
        writer.finishCompound();
    }

    private static byte[] heightmapName(Heightmap.Types type) {
        return HEIGHTMAP_NAMES.computeIfAbsent(type, key -> NbtWriter.asciiName(key.getSerializationKey()));
    }

    private static void writeTick(NbtWriter writer, SavedTick<?> tick, String id) {
        BlockPos pos = tick.pos();
        writer.compoundEntryStart();
        writer.putString(ID, id);
        writer.putInt(X, pos.getX());
        writer.putInt(Y, pos.getY());
        writer.putInt(Z, pos.getZ());
        writer.putInt(DELAY, tick.delay());
        writer.putInt(PRIORITY, tick.priority().getValue());
        writer.finishCompound();
    }
}
