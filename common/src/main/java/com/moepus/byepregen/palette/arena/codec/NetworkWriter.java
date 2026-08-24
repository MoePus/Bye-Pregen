package com.moepus.byepregen.palette.arena.codec;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;

public final class NetworkWriter {
    private static final int MAX_LOCAL_PALETTE_BITS = 8;

    private NetworkWriter() {
    }

    public static void write(FriendlyByteBuf buffer, ArenaBlockStatePalettedContainer container) {
        if (container.isUniform()) {
            writeUniform(buffer, container.uniformRawId());
            return;
        }

        SerializationScratch scratch = SerializationScratch.get();
        try {
            scratch.collect(container);
            write(buffer, container, scratch);
        } finally {
            scratch.clear();
        }
    }

    public static int serializedSize(ArenaBlockStatePalettedContainer container) {
        if (container.isUniform()) {
            return uniformSerializedSize(container.uniformRawId());
        }

        SerializationScratch scratch = SerializationScratch.get();
        try {
            scratch.collectForNetworkSize(container);
            return serializedSize(scratch);
        } finally {
            scratch.clear();
        }
    }

    private static void writeUniform(FriendlyByteBuf buffer, int rawId) {
        buffer.writeByte(0);
        buffer.writeVarInt(rawId);
    }

    private static int uniformSerializedSize(int rawId) {
        return 1 + VarInt.getByteSize(rawId);
    }

    private static int serializedBits(int paletteSize) {
        int bits = SerializationScratch.localPaletteBits(paletteSize);
        if (bits > MAX_LOCAL_PALETTE_BITS) {
            return Mth.ceillog2(Block.BLOCK_STATE_REGISTRY.size());
        }
        return bits;
    }

    private static boolean useGlobalPalette(int bits) {
        return bits > MAX_LOCAL_PALETTE_BITS;
    }

    private static void write(
            FriendlyByteBuf buffer,
            ArenaBlockStatePalettedContainer container,
            SerializationScratch scratch) {
        int paletteSize = scratch.paletteSize();
        int bits = serializedBits(paletteSize);
        boolean globalPalette = useGlobalPalette(bits);

        buffer.writeByte(bits);
        writePalette(buffer, scratch, bits, globalPalette, paletteSize);
        if (bits == 0) {
            return;
        }

        scratch.beginPack(bits);
        scratch.writeNetworkData(buffer, container, globalPalette);
    }

    private static int serializedSize(SerializationScratch scratch) {
        int paletteSize = scratch.paletteSize();
        int bits = serializedBits(paletteSize);
        int packedLength = bits == 0 ? 0 : SerializationScratch.packedLength(bits);
        return 1 + paletteSerializedSize(scratch, bits, useGlobalPalette(bits), paletteSize)
                + packedLength * Long.BYTES;
    }

    private static void writePalette(
            FriendlyByteBuf buffer,
            SerializationScratch scratch,
            int bits,
            boolean globalPalette,
            int paletteSize) {
        if (globalPalette) {
            return;
        }
        if (bits == 0) {
            buffer.writeVarInt(scratch.paletteRawId(0));
            return;
        }

        buffer.writeVarInt(paletteSize);
        for (int i = 0; i < paletteSize; ++i) {
            buffer.writeVarInt(scratch.paletteRawId(i));
        }
    }

    private static int paletteSerializedSize(
            SerializationScratch scratch, int bits, boolean globalPalette, int paletteSize) {
        if (globalPalette) {
            return 0;
        }
        if (bits == 0) {
            return VarInt.getByteSize(scratch.paletteRawId(0));
        }

        int size = VarInt.getByteSize(paletteSize);
        for (int i = 0; i < paletteSize; ++i) {
            size += VarInt.getByteSize(scratch.paletteRawId(i));
        }
        return size;
    }
}
