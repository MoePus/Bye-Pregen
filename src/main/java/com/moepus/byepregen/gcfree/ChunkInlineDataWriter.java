package com.moepus.byepregen.gcfree;

import com.moepus.byepregen.mixin.accessor.BlendingDataAccessor;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.blending.BlendingData;

final class ChunkInlineDataWriter {
    private static final byte[] BLENDING_DATA = NbtWriter.asciiName("blending_data");
    private static final byte[] BELOW_ZERO_RETROGEN = NbtWriter.asciiName("below_zero_retrogen");
    private static final byte[] UPGRADE_DATA = NbtWriter.asciiName("UpgradeData");
    private static final byte[] MIN_SECTION = NbtWriter.asciiName("min_section");
    private static final byte[] MAX_SECTION = NbtWriter.asciiName("max_section");
    private static final byte[] HEIGHTS = NbtWriter.asciiName("heights");
    private static final byte[] TARGET_STATUS = NbtWriter.asciiName("target_status");
    private static final byte[] MISSING_BEDROCK = NbtWriter.asciiName("missing_bedrock");
    private static final ThreadLocal<long[]> BEDROCK_MASK_SCRATCH = ThreadLocal.withInitial(() -> new long[4]);

    private ChunkInlineDataWriter() {
    }

    static void write(NbtWriter writer, ChunkAccess chunk) {
        BlendingData blendingData = chunk.getBlendingData();
        if (blendingData != null) {
            writer.startCompound(BLENDING_DATA);
            writeBlendingData(writer, (BlendingDataAccessor) blendingData);
            writer.finishCompound();
        }

        BelowZeroRetrogen retrogen = chunk.getBelowZeroRetrogen();
        if (retrogen != null) {
            writer.startCompound(BELOW_ZERO_RETROGEN);
            writeBelowZeroRetrogen(writer, retrogen);
            writer.finishCompound();
        }

        if (!chunk.getUpgradeData().isEmpty()) {
            writer.putTag(UPGRADE_DATA, chunk.getUpgradeData().write());
        }
    }

    private static void writeBlendingData(NbtWriter writer, BlendingDataAccessor blendingData) {
        LevelHeightAccessor oldHeight = blendingData.byepregen$areaWithOldGeneration();
        writer.putInt(MIN_SECTION, oldHeight.getMinSection());
        writer.putInt(MAX_SECTION, oldHeight.getMaxSection());
        double[] heights = blendingData.byepregen$heights();
        if (hasStoredHeight(heights)) {
            writer.putDoubles(HEIGHTS, heights);
        }
    }

    private static void writeBelowZeroRetrogen(NbtWriter writer, BelowZeroRetrogen retrogen) {
        writer.putString(TARGET_STATUS, BuiltInRegistries.CHUNK_STATUS.getKey(retrogen.targetStatus()).toString());
        if (!retrogen.hasBedrockHoles()) {
            return;
        }

        long[] mask = BEDROCK_MASK_SCRATCH.get();
        for (int i = 0; i < mask.length; ++i) {
            mask[i] = 0L;
        }
        writer.putLongArray(MISSING_BEDROCK, mask, fillBedrockMask(retrogen, mask));
    }

    private static boolean hasStoredHeight(double[] heights) {
        for (double height : heights) {
            if (height != Double.MAX_VALUE) {
                return true;
            }
        }
        return false;
    }

    private static int fillBedrockMask(BelowZeroRetrogen retrogen, long[] mask) {
        int length = 0;
        for (int z = 0; z < 16; ++z) {
            for (int x = 0; x < 16; ++x) {
                if (retrogen.hasBedrockHole(x, z)) {
                    int index = (z << 4) | x;
                    int word = index >>> 6;
                    mask[word] |= 1L << (index & 63);
                    length = word + 1;
                }
            }
        }
        return length;
    }
}
