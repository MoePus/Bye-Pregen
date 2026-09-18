package com.moepus.byepregen.palette.arena.codec;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.serialization.nbt.BlockStateNbtCache;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.nbt.Tag;

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
        boolean shortForm = BlockStateNbtCache.rawIdUsesShortForm(rawId);
        writer.startCompound(BLOCK_STATES);
        writer.startFixedList(PALETTE, 1, shortForm ? Tag.TAG_STRING : Tag.TAG_COMPOUND);
        if (shortForm) {
            writer.write(BlockStateNbtCache.rawIdFileEntryBytes(rawId));
        } else {
            writer.compoundEntryStart();
            writer.write(BlockStateNbtCache.rawIdFileEntryBytes(rawId));
            writer.finishCompound();
        }
        writer.finishCompound();
    }

    /**
     * Writes the palette the way the block-state codec does: a list of bare block names when every entry
     * is a default state, otherwise a compound list with the shortened entries wrapped.
     */
    private static void writePalette(NbtWriter writer, SerializationScratch scratch) {
        int size = scratch.paletteSize();
        boolean shortForms = true;
        for (int i = 0; i < size; ++i) {
            if (!BlockStateNbtCache.rawIdUsesShortForm(scratch.paletteRawId(i))) {
                shortForms = false;
                break;
            }
        }
        writer.startFixedList(PALETTE, size, shortForms ? Tag.TAG_STRING : Tag.TAG_COMPOUND);
        for (int i = 0; i < size; ++i) {
            int rawId = scratch.paletteRawId(i);
            if (shortForms) {
                writer.write(BlockStateNbtCache.rawIdFileEntryBytes(rawId));
            } else {
                writer.compoundEntryStart();
                writer.write(BlockStateNbtCache.rawIdFileWrappedEntryBytes(rawId));
                writer.finishCompound();
            }
        }
    }
}
