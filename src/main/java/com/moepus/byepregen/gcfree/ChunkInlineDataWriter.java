package com.moepus.byepregen.gcfree;

import java.util.Optional;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.blending.BlendingData;

final class ChunkInlineDataWriter {
    private static final byte[] BLENDING_DATA = NbtWriter.asciiName("blending_data");
    private static final byte[] BELOW_ZERO_RETROGEN = NbtWriter.asciiName("below_zero_retrogen");
    private static final byte[] UPGRADE_DATA = NbtWriter.asciiName("UpgradeData");

    private ChunkInlineDataWriter() {
    }

    static void write(NbtWriter writer, ChunkAccess chunk) {
        BlendingData blendingData = chunk.getBlendingData();
        if (blendingData != null) {
            putEncoded(writer, BLENDING_DATA, BlendingData.CODEC.encodeStart(NbtOps.INSTANCE, blendingData).result());
        }

        BelowZeroRetrogen retrogen = chunk.getBelowZeroRetrogen();
        if (retrogen != null) {
            putEncoded(writer, BELOW_ZERO_RETROGEN, BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, retrogen).result());
        }

        if (!chunk.getUpgradeData().isEmpty()) {
            writer.putTag(UPGRADE_DATA, chunk.getUpgradeData().write());
        }
    }

    private static void putEncoded(NbtWriter writer, byte[] name, Optional<Tag> result) {
        result.ifPresent(tag -> writer.putTag(name, tag));
    }
}
