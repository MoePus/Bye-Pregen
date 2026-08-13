package com.moepus.byepregen.palette.arena.codec;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.serialization.nbt.BlockStateNbtCache;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

public final class SectionWriter {
    private static final byte[] BLOCK_STATES = NbtWriter.asciiName("block_states");
    private static final byte[] PALETTE = NbtWriter.asciiName("palette");
    private static final byte[] DATA = NbtWriter.asciiName("data");

    private SectionWriter() {}

    public static void write(NbtWriter writer, ArenaBlockStatePalettedContainer container) {
        if (container.isUniform()) {
            writeUniform(writer, container.uniformRawId());
            return;
        }
        SerializationScratch scratch = SerializationScratch.get();
        try {
            scratch.collect(container);
            writer.startCompound(BLOCK_STATES);
            writePalette(writer, scratch);
            if (scratch.paletteSize() > 1) {
                writer.startLongArray(DATA, scratch.beginSectionPack());
                scratch.writeSectionData(writer, container);
            }
            writer.finishCompound();
        } finally {
            scratch.clear();
        }
    }

    private static void writeUniform(NbtWriter writer, int rawId) {
        writer.startCompound(BLOCK_STATES);
        writer.startFixedList(PALETTE, 1, Tag.TAG_COMPOUND);
        writer.compoundEntryStart();
        BlockStateNbtCache.writeRawIdEntry(writer, rawId);
        writer.finishCompound();
        writer.finishCompound();
    }

    private static void writePalette(NbtWriter writer, SerializationScratch scratch) {
        int size = scratch.paletteSize();
        writer.startFixedList(PALETTE, size, Tag.TAG_COMPOUND);
        for (int i = 0; i < size; ++i) {
            writer.compoundEntryStart();
            BlockStateNbtCache.writeRawIdEntry(writer, scratch.paletteRawId(i));
            writer.finishCompound();
        }
    }

    public static void writeStateEntry(NbtWriter writer, BlockState state) {
        BlockStateNbtCache.writeStateEntry(writer, state);
    }
}
