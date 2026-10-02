package com.moepus.byepregen.yalight.storage;

import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;

/**
 * One save decision and immutable COW byte references, shared by vanilla and raw serialization.
 * The caller still owns the normal chunk-save lifecycle (main-thread save or unloaded chunk).
 * This captures lighting only; it does not make concurrent block-state serialization atomic.
 */
public final class YALightSaveSnapshot {
    public static final YALightSaveSnapshot UNLIT = new YALightSaveSnapshot(false, 0, null, null);

    private final boolean valid;
    private final int minSection;
    private final YANibbleArray.SaveState[] block;
    private final YANibbleArray.SaveState[] sky;

    private YALightSaveSnapshot(boolean valid, int minSection,
                               YANibbleArray.SaveState[] block, YANibbleArray.SaveState[] sky) {
        this.valid = valid;
        this.minSection = minSection;
        this.block = block;
        this.sky = sky;
    }

    public static YALightSaveSnapshot capture(ChunkAccess chunk) {
        return capture((YAChunkLightAccess)chunk, chunk.isLightCorrect(), chunk.getMinSection() - 1);
    }

    public static YALightSaveSnapshot capture(YAChunkLightAccess access, boolean correct, int minSection) {
        if (!correct) {
            return UNLIT;
        }
        YALightSaveState state = access.byepregen$yaLightSaveState();
        long revision = state.captureRevision();
        if (revision == YALightSaveState.UNAVAILABLE) {
            return UNLIT;
        }
        YANibbleArray.SaveState[] block = captureLayer(access.byepregen$blockLightData());
        YANibbleArray.SaveState[] sky = captureLayer(access.byepregen$skyLightData());
        // Check only the brief capture interval, not the entire serialization. No retry or wait.
        return state.isCurrent(revision) ? new YALightSaveSnapshot(true, minSection, block, sky) : UNLIT;
    }

    private static YANibbleArray.SaveState[] captureLayer(YAChunkLightData data) {
        if (data == null) {
            return null;
        }
        YANibbleArray[] visible = data.visibleSections();
        YANibbleArray.SaveState[] result = new YANibbleArray.SaveState[visible.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = visible[i] == null ? null : visible[i].captureSaveState();
        }
        return result;
    }

    public boolean valid() {
        return this.valid;
    }

    public YANibbleArray.SaveState section(LightLayer layer, int sectionY) {
        YANibbleArray.SaveState[] sections = layer == LightLayer.BLOCK ? this.block : this.sky;
        int index = sectionY - this.minSection;
        return sections == null || index < 0 || index >= sections.length ? null : sections[index];
    }

    public DataLayer vanillaLayer(LightLayer layer, int sectionY) {
        YANibbleArray.SaveState section = this.section(layer, sectionY);
        return section == null ? null : section.toVanilla();
    }
}
