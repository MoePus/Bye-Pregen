package com.moepus.byepregen.yalight;

import net.minecraft.core.BlockPos;

public final class YAVisibleLightReader {
    static final YANibbleArray[] EMPTY_SECTIONS = new YANibbleArray[0];
    private static final int FULL_LIGHT = 15;

    private YAVisibleLightReader() {
    }

    public static int blockLight(YANibbleArray[] sections, int sectionIndex, BlockPos pos) {
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return 0;
        }
        YANibbleArray nibble = sections[sectionIndex];
        return nibble == null ? 0 : nibble.getVisible(pos.getX(), pos.getY(), pos.getZ());
    }

    public static int skyLight(YANibbleArray[] sections, int sectionIndex, BlockPos pos) {
        int readY = pos.getY();
        if (sectionIndex < 0) {
            sectionIndex = 0;
            readY = 0;
        }
        while (sectionIndex < sections.length) {
            YANibbleArray nibble = sections[sectionIndex++];
            if (nibble != null) {
                return nibble.getVisible(pos.getX(), readY, pos.getZ());
            }
            // Vanilla flattens the position before walking upward through missing sky sections.
            readY = 0;
        }
        return FULL_LIGHT;
    }
}
