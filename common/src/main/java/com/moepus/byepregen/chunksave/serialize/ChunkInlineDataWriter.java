package com.moepus.byepregen.chunksave.serialize;

import com.mojang.serialization.Codec;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.blending.BlendingData;

final class ChunkInlineDataWriter {
    private static final byte[] BLENDING_DATA = NbtWriter.asciiName("blending_data");
    private static final byte[] BELOW_ZERO_RETROGEN = NbtWriter.asciiName("below_zero_retrogen");
    private static final byte[] UPGRADE_DATA = NbtWriter.asciiName("UpgradeData");

    private ChunkInlineDataWriter() { }

    static void write(NbtWriter writer, ChunkAccess chunk) {
        BlendingData blendingData = chunk.getBlendingData();
        if (blendingData != null) {
            writeEncoded(writer, BLENDING_DATA, BlendingData.Packed.CODEC, blendingData.pack());
        }
        BelowZeroRetrogen retrogen = chunk.getBelowZeroRetrogen();
        if (retrogen != null) {
            writeEncoded(writer, BELOW_ZERO_RETROGEN, BelowZeroRetrogen.CODEC, retrogen);
        }
        if (!chunk.getUpgradeData().isEmpty()) {
            writer.putTag(UPGRADE_DATA, chunk.getUpgradeData().write());
        }
    }

    private static <T> void writeEncoded(NbtWriter writer, byte[] name, Codec<T> codec, T value) {
        // Keep rare migration data on the native codec, including RC2's float height list.
        writer.putTag(name, codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow());
    }
}
