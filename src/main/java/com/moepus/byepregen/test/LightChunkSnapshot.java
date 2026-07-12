package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.YAChunkLightAccess;
import com.moepus.byepregen.yalight.YAChunkLightData;
import com.moepus.byepregen.yalight.YANibbleArray;
import java.util.Arrays;
import net.minecraft.world.level.chunk.ChunkAccess;

record LightChunkSnapshot(byte[][] block, byte[][] sky) {
    static LightChunkSnapshot capture(ChunkAccess chunk) {
        YAChunkLightAccess access = (YAChunkLightAccess)chunk;
        return new LightChunkSnapshot(
                snapshotLayer(access.byepregen$blockLightData()),
                snapshotLayer(access.byepregen$skyLightData())
        );
    }

    boolean matches(LightChunkSnapshot other) {
        return Arrays.deepEquals(this.block, other.block) && Arrays.deepEquals(this.sky, other.sky);
    }

    String summary() {
        return "{block=" + nonEmptySections(this.block) + ",sky=" + nonEmptySections(this.sky) + "}";
    }

    private static byte[][] snapshotLayer(YAChunkLightData data) {
        if (data == null) {
            return null;
        }
        YANibbleArray[] sections = data.visibleSections();
        byte[][] snapshot = new byte[sections.length][];
        for (int i = 0; i < sections.length; ++i) {
            snapshot[i] = snapshotSection(sections[i]);
        }
        return snapshot;
    }

    private static byte[] snapshotSection(YANibbleArray section) {
        if (section == null || section.visibleSaveKind() == YANibbleArray.SAVE_EMPTY) {
            return null;
        }
        return section.visibleSaveKind() == YANibbleArray.SAVE_FULL
                ? YANibbleArray.FULL_LIGHT_DATA.clone()
                : section.visibleDataForSave().clone();
    }

    private static int nonEmptySections(byte[][] sections) {
        if (sections == null) {
            return 0;
        }
        int count = 0;
        for (byte[] section : sections) {
            count += section == null ? 0 : 1;
        }
        return count;
    }
}
