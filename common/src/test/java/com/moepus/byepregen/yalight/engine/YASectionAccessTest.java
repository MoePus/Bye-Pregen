package com.moepus.byepregen.yalight.engine;

import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YALightStorage;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YASectionAccessTest {
    private final LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
    private final YALightStorage storage = new YALightStorage(null, this.height, LightLayer.BLOCK);
    private final YAChunkLightData data = new YAChunkLightData(new ChunkPos(0, 0), this.height);
    private final YAChunkRunCache cache = new YAChunkRunCache();

    @Test
    void firstWriteMaterializesTheSameSectionUsedBySubsequentReads() {
        this.data.setLightEnabled(true);
        YASectionAccess access = this.access(0);
        assertTrue(access.enabled());
        assertEquals(0, access.light(4095));
        access.setLight(this.cache, this.storage, 4095, 12);
        assertEquals(12, access.light(4095));
        assertEquals(12, this.data.getUpdatingSectionByIndex(this.storage.sectionIndex(0)).getUpdating(4095));
    }

    @Test
    void refreshObservesReplacementAndWritesKeepPublishedBytesStable() {
        this.data.setLightEnabled(true);
        YASectionAccess access = this.access(0);
        access.setLight(this.cache, this.storage, 31, 7);
        YANibbleArray original = this.data.getUpdatingSectionByIndex(this.storage.sectionIndex(0));
        original.publish();
        byte[] visible = original.visibleDataForSave();
        access.setLight(this.cache, this.storage, 31, 10);
        assertEquals(7, original.getVisible(15, 0, 1));
        assertSame(visible, original.visibleDataForSave());

        this.data.setFullSection(0, this.storage);
        access.refresh();
        assertEquals(15, access.light(31));
        access.setLight(this.cache, this.storage, 31, 6);
        YANibbleArray replacement = this.data.getUpdatingSectionByIndex(this.storage.sectionIndex(0));
        assertNotSame(original, replacement);
        assertEquals(6, replacement.getUpdating(31));
        assertEquals(15, replacement.getVisible(15, 0, 1));
    }

    @Test
    void nullStateIsReplacedOnWriteAndDisableIsObservedOnRefresh() {
        this.data.setLightEnabled(true);
        int index = this.storage.sectionIndex(0);
        YANibbleArray retired = this.data.getOrCreateUpdatingSectionByIndex(index);
        retired.discardUnpublished();
        YASectionAccess access = this.access(0);
        assertEquals(0, access.light(1));
        access.setLight(this.cache, this.storage, 1, 5);
        assertNotSame(retired, this.data.getUpdatingSectionByIndex(index));
        assertEquals(5, access.light(1));
        this.data.setLightEnabled(false);
        access.refresh();
        assertFalse(access.enabled());
        assertEquals(5, access.light(1)); // Source reads do not require enabled lighting.
    }

    @Test
    void bothLightSentinelsAreValidButBeyondThemIsUnavailable() {
        this.data.setLightEnabled(true);
        assertTrue(this.access(this.storage.minLightSection()).enabled());
        assertTrue(this.access(this.storage.maxLightSection()).enabled());
        YASectionAccess below = this.access(this.storage.minLightSection() - 1);
        YASectionAccess above = this.access(this.storage.maxLightSection() + 1);
        assertFalse(below.enabled());
        assertFalse(above.enabled());
        below.setLight(this.cache, this.storage, 0, 15);
        above.setLight(this.cache, this.storage, 4095, 15);
        assertEquals(0, below.light(0));
        assertEquals(0, above.light(4095));
    }

    private YASectionAccess access(int sectionY) {
        YASectionAccess access = new YASectionAccess();
        access.bind(this.storage, null, this.data, sectionY);
        return access;
    }
}
