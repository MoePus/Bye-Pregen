package com.moepus.byepregen.yalight.storage;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YALightRenderUpdatesTest {
    private static final ChunkPos CHUNK = new ChunkPos(-80, -66);
    private static final int SECTION_Y = 2;
    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);

    @Test
    void everyLocalPositionMatchesTheOneBlockSamplingHalo() {
        for (int index = 0; index < 4096; ++index) {
            Set<Long> actual = new HashSet<>();
            YALightRenderUpdates updates = new YALightRenderUpdates(-4, 20, actual::add);
            updates.add(CHUNK, SECTION_Y, YALightRenderUpdates.maskForBlock(index));
            updates.flush();
            assertEquals(expectedHalo(index), actual, "local index=" + index);
        }
    }

    @Test
    void interiorFaceEdgeAndCornerHaveOneTwoFourAndEightTargets() {
        assertEquals(1, Integer.bitCount(mask(8, 8, 8)));
        assertEquals(2, Integer.bitCount(mask(8, 0, 8)));
        assertEquals(4, Integer.bitCount(mask(0, 0, 8)));
        assertEquals(8, Integer.bitCount(mask(0, 0, 15)));
    }

    @Test
    void laterBoundaryWritesAreNotLostAndSeparateFacesDoNotInventACorner() {
        List<Long> notifications = new ArrayList<>();
        YALightRenderUpdates updates = new YALightRenderUpdates(-4, 20, notifications::add);
        YAChunkLightData data = new YAChunkLightData(CHUNK, HEIGHT);
        int index = SECTION_Y - data.minLightSection();
        data.markDirty(index);
        data.markRenderMask(index, mask(8, 8, 8));
        data.markDirty(index);
        data.markRenderMask(index, mask(0, 8, 8));
        data.markRenderMask(index, mask(8, 8, 0));
        data.publishDirty(null, LightLayer.BLOCK, updates);
        assertTrue(notifications.isEmpty(), "publication must not rebuild meshes yet");
        updates.flush();
        assertEquals(Set.of(section(0, 0, 0), section(-1, 0, 0), section(0, 0, -1)),
                new HashSet<>(notifications));
        assertEquals(3, notifications.size());
    }

    @Test
    void layersDeduplicateAndAllVisibleBytesPrecedeNotifications() {
        YAChunkLightData block = new YAChunkLightData(CHUNK, HEIGHT);
        YAChunkLightData sky = new YAChunkLightData(CHUNK, HEIGHT);
        List<Long> notifications = new ArrayList<>();
        YALightRenderUpdates updates = new YALightRenderUpdates(-4, 20, pos -> {
            assertEquals(12, block.getVisibleSection(SECTION_Y).getVisible(8, 0, 8));
            assertEquals(7, sky.getVisibleSection(SECTION_Y).getVisible(8, 0, 8));
            notifications.add(pos);
        });
        publishCell(block, 12, LightLayer.BLOCK, updates);
        publishCell(sky, 7, LightLayer.SKY, updates);
        assertTrue(notifications.isEmpty());
        updates.flush();
        assertEquals(Set.of(section(0, 0, 0), section(0, -1, 0)), new HashSet<>(notifications));
        assertEquals(2, notifications.size());
        updates.flush();
        assertEquals(2, notifications.size(), "a drained batch must not replay");
    }

    @Test
    void wholeSectionReplacementAndRemovalNotifyAfterPublication() {
        YAChunkLightData data = new YAChunkLightData(CHUNK, HEIGHT);
        YALightStorage storage = new YALightStorage(null, HEIGHT, LightLayer.BLOCK);
        List<Long> notifications = new ArrayList<>();
        YALightRenderUpdates updates = new YALightRenderUpdates(-4, 20, notifications::add);
        storage.setRenderUpdates(updates);
        data.setSection(SECTION_Y, YANibbleArray.fullArray(), storage);
        storage.publishDirty(null, LightLayer.BLOCK);
        assertTrue(notifications.isEmpty());
        updates.flush();
        assertEquals(27, notifications.size());
        storage.finishPublications(true);
        notifications.clear();
        data.removeSection(SECTION_Y, storage);
        storage.publishDirty(null, LightLayer.BLOCK);
        updates.flush();
        assertEquals(27, notifications.size());
        storage.finishPublications(true);
    }

    @Test
    void masksResetBetweenBatchesAndOnClear() {
        YAChunkLightData data = new YAChunkLightData(CHUNK, HEIGHT);
        Set<Long> notifications = new HashSet<>();
        YALightRenderUpdates updates = new YALightRenderUpdates(-4, 20, notifications::add);
        int index = SECTION_Y - data.minLightSection();
        data.markDirty(index);
        data.markRenderMask(index, mask(0, 0, 0));
        data.publishDirty(null, LightLayer.BLOCK, updates);
        data.finishPublication(true);
        updates.flush();
        notifications.clear();
        data.markDirty(index);
        data.markRenderMask(index, mask(8, 8, 8));
        data.publishDirty(null, LightLayer.BLOCK, updates);
        updates.flush();
        assertEquals(Set.of(section(0, 0, 0)), notifications);
        data.markRenderMask(index, mask(0, 0, 0));
        data.clear();
        notifications.clear();
        data.markDirty(index);
        data.publishDirty(null, LightLayer.BLOCK, updates);
        updates.flush();
        assertEquals(Set.of(section(0, 0, 0)), notifications);
    }

    @Test
    void sentinelChangesCanNotifyRealSectionsButNotOutOfWorldMeshes() {
        Set<Long> notifications = new HashSet<>();
        YALightRenderUpdates updates = new YALightRenderUpdates(-4, 20, notifications::add);
        updates.add(CHUNK, -5, mask(8, 15, 8));
        updates.add(CHUNK, 20, mask(8, 0, 8));
        updates.flush();
        assertEquals(Set.of(SectionPos.asLong(CHUNK.x, -4, CHUNK.z),
                SectionPos.asLong(CHUNK.x, 19, CHUNK.z)), notifications);
    }

    private static void publishCell(YAChunkLightData data, int value, LightLayer layer, YALightRenderUpdates updates) {
        int index = SECTION_Y - data.minLightSection();
        data.getOrCreateUpdatingSection(SECTION_Y).setUpdating(8, 0, 8, value);
        data.markDirty(index);
        data.markRenderMask(index, mask(8, 0, 8));
        data.publishDirty(null, layer, updates);
    }

    private static int mask(int x, int y, int z) {
        return YALightRenderUpdates.maskForBlock(YANibbleArray.index(x, y, z));
    }

    private static long section(int dx, int dy, int dz) {
        return SectionPos.asLong(CHUNK.x + dx, SECTION_Y + dy, CHUNK.z + dz);
    }

    private static Set<Long> expectedHalo(int index) {
        int x = CHUNK.getMinBlockX() + (index & 15);
        int y = SECTION_Y * 16 + (index >>> 8);
        int z = CHUNK.getMinBlockZ() + ((index >>> 4) & 15);
        Set<Long> expected = new HashSet<>();
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dz = -1; dz <= 1; ++dz) {
                    expected.add(SectionPos.asLong(Math.floorDiv(x + dx, 16),
                            Math.floorDiv(y + dy, 16), Math.floorDiv(z + dz, 16)));
                }
            }
        }
        return expected;
    }
}
