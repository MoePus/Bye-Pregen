package com.moepus.byepregen.palette.arena.codec;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.serialization.nbt.BlockStateNbtCache;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.nbt.Tag;

public final class PayloadBuilder {
    private static final byte[] PALETTE = NbtWriter.asciiName("palette");
    private static final byte[] DATA = NbtWriter.asciiName("data");

    private PayloadBuilder() {}

    public static byte[] uniform(int rawId) {
        boolean shortForms = BlockStateNbtCache.rawIdUsesShortForm(rawId);
        byte[] payload = new byte[uniformPayloadSize(rawId, shortForms)];
        PayloadWriter writer = new PayloadWriter(payload);
        writePaletteHeader(writer, 1, shortForms);
        writePaletteEntry(writer, rawId, shortForms);
        writer.writeByte(Tag.TAG_END);
        writer.finish();
        return payload;
    }

    public static byte[] packed(
            SerializationScratch scratch, ArenaBlockStatePalettedContainer container) {
        int paletteSize = scratch.paletteSize();
        int packedLength = paletteSize > 1 ? packedLength(paletteSize) : 0;
        boolean shortForms = usesShortForms(scratch);
        byte[] payload = new byte[packedPayloadSize(scratch, packedLength, shortForms)];
        PayloadWriter writer = new PayloadWriter(payload);
        writePalette(writer, scratch, shortForms);
        if (paletteSize > 1) {
            writeData(writer, scratch, container, packedLength);
        }
        writer.writeByte(Tag.TAG_END);
        writer.finish();
        return payload;
    }

    private static boolean usesShortForms(SerializationScratch scratch) {
        for (int i = 0; i < scratch.paletteSize(); ++i) {
            if (!BlockStateNbtCache.rawIdUsesShortForm(scratch.paletteRawId(i))) {
                return false;
            }
        }
        return true;
    }

    private static int uniformPayloadSize(int rawId, boolean shortForms) {
        return namedHeaderSize(PALETTE) + listHeaderSize() + paletteEntrySize(rawId, shortForms) + Byte.BYTES;
    }

    private static int packedPayloadSize(SerializationScratch scratch, int packedLength, boolean shortForms) {
        int size = namedHeaderSize(PALETTE) + listHeaderSize();
        for (int i = 0; i < scratch.paletteSize(); ++i) {
            size += paletteEntrySize(scratch.paletteRawId(i), shortForms);
        }
        if (packedLength > 0) {
            size += namedHeaderSize(DATA) + Integer.BYTES + packedLength * Long.BYTES;
        }
        return size + Byte.BYTES;
    }

    private static void writePalette(PayloadWriter writer, SerializationScratch scratch, boolean shortForms) {
        int size = scratch.paletteSize();
        writePaletteHeader(writer, size, shortForms);
        for (int i = 0; i < size; ++i) {
            writePaletteEntry(writer, scratch.paletteRawId(i), shortForms);
        }
    }

    private static void writeData(
            PayloadWriter writer, SerializationScratch scratch,
            ArenaBlockStatePalettedContainer container, int packedLength) {
        writer.writeNamedType(Tag.TAG_LONG_ARRAY, DATA);
        writer.writeInt(packedLength);
        int actualLength = scratch.beginSectionPack();
        if (actualLength != packedLength) {
            throw new IllegalStateException("Packed data length changed during block state encode");
        }
        scratch.writePayloadData(writer, container);
    }

    private static void writePaletteHeader(PayloadWriter writer, int size, boolean shortForms) {
        writer.writeNamedType(Tag.TAG_LIST, PALETTE);
        writer.writeByte(shortForms ? Tag.TAG_STRING : Tag.TAG_COMPOUND);
        writer.writeInt(size);
    }

    private static void writePaletteEntry(PayloadWriter writer, int rawId, boolean shortForms) {
        if (shortForms) {
            writer.writeBytes(BlockStateNbtCache.rawIdFileEntryBytes(rawId));
            return;
        }
        writer.writeBytes(BlockStateNbtCache.rawIdFileWrappedEntryBytes(rawId));
        writer.writeByte(Tag.TAG_END);
    }

    private static int paletteEntrySize(int rawId, boolean shortForms) {
        return shortForms
                ? BlockStateNbtCache.rawIdFileEntryBytes(rawId).length
                : BlockStateNbtCache.rawIdFileWrappedEntryBytes(rawId).length + Byte.BYTES;
    }

    private static int namedHeaderSize(byte[] name) {
        return Byte.BYTES + name.length;
    }

    private static int listHeaderSize() {
        return Byte.BYTES + Integer.BYTES;
    }

    private static int packedLength(int paletteSize) {
        int bits = SerializationScratch.localPaletteBits(paletteSize);
        return SerializationScratch.packedLength(bits);
    }
}
