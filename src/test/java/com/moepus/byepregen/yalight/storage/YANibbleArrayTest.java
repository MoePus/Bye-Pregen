package com.moepus.byepregen.yalight.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.chunk.DataLayer;
import org.junit.jupiter.api.Test;

public final class YANibbleArrayTest {
    private static final int X = 3;
    private static final int Y = 7;
    private static final int Z = 11;

    @Test
    void keepsUpdatingLightHiddenUntilPublish() {
        YANibbleArray light = new YANibbleArray();

        assertEquals(0, light.getVisible(X, Y, Z));
        assertEquals(0, light.getUpdating(X, Y, Z));
        assertFalse(light.isDirty());

        light.setUpdating(X, Y, Z, 12);

        assertEquals(0, light.getVisible(X, Y, Z));
        assertEquals(12, light.getUpdating(X, Y, Z));
        assertEquals(0, light.toVanilla().get(X, Y, Z));
        assertTrue(light.isDirty());

        light.publish();

        assertEquals(12, light.getVisible(X, Y, Z));
        assertEquals(12, light.toVanilla().get(X, Y, Z));
        assertFalse(light.isDirty());
        assertEquals(YANibbleArray.SAVE_DATA, light.visibleSaveKind());
    }

    @Test
    void publishedSnapshotsStayStableAcrossCopyOnWrite() {
        YANibbleArray light = new YANibbleArray();
        light.setUpdating(X, Y, Z, 5);
        light.publish();
        DataLayer firstSnapshot = light.toVanilla();
        byte[] firstSaveData = light.visibleDataForSave();

        light.setUpdating(X, Y, Z, 9);

        assertEquals(5, light.getVisible(X, Y, Z));
        assertEquals(9, light.getUpdating(X, Y, Z));
        assertEquals(5, firstSnapshot.get(X, Y, Z));
        assertSame(firstSaveData, light.visibleDataForSave());

        light.publish();
        DataLayer secondSnapshot = light.toVanilla();

        assertEquals(5, firstSnapshot.get(X, Y, Z));
        assertEquals(9, secondSnapshot.get(X, Y, Z));
        assertNotSame(firstSnapshot, secondSnapshot);
        assertNotSame(firstSaveData, light.visibleDataForSave());
        assertSame(secondSnapshot, light.toVanilla());
    }

    @Test
    void exposesPublishedLayersAsReadOnlySnapshots() {
        YANibbleArray light = new YANibbleArray();
        light.setUpdating(X, Y, Z, 6);
        light.publish();
        DataLayer snapshot = light.toVanilla();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.set(X, Y, Z, 2));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.fill(3));

        byte[] exported = snapshot.getData();
        exported[YANibbleArray.index(X, Y, Z) >>> 1] = 0;
        assertEquals(6, snapshot.get(X, Y, Z));

        DataLayer mutableCopy = snapshot.copy();
        mutableCopy.set(X, Y, Z, 2);
        assertEquals(2, mutableCopy.get(X, Y, Z));
        assertEquals(6, snapshot.get(X, Y, Z));
    }

    @Test
    void preservesCompactNullZeroAndFullSaveForms() {
        YANibbleArray nullLight = YANibbleArray.nullArray();
        YANibbleArray zeroLight = new YANibbleArray();
        YANibbleArray fullLight = YANibbleArray.fullArray();

        assertTrue(nullLight.isNullVisible());
        assertFalse(nullLight.hasVisibleLayer());
        assertNull(nullLight.toVanilla());
        assertEquals(YANibbleArray.SAVE_EMPTY, nullLight.visibleSaveKind());
        assertNull(nullLight.visibleDataForSave());

        assertEquals(0, zeroLight.toVanilla().get(X, Y, Z));
        assertEquals(YANibbleArray.SAVE_EMPTY, zeroLight.visibleSaveKind());
        assertNull(zeroLight.visibleDataForSave());

        assertTrue(fullLight.isFullUpdating());
        assertEquals(15, fullLight.toVanilla().get(X, Y, Z));
        assertEquals(YANibbleArray.SAVE_FULL, fullLight.visibleSaveKind());
        assertNull(fullLight.visibleDataForSave());

        assertTrue(YANibbleArray.fromVanilla(null).isNullVisible());
        assertEquals(YANibbleArray.SAVE_EMPTY, YANibbleArray.fromVanilla(new DataLayer()).visibleSaveKind());
        assertEquals(YANibbleArray.SAVE_FULL, YANibbleArray.fromVanilla(new DataLayer(15)).visibleSaveKind());
        assertEquals(YANibbleArray.SAVE_EMPTY,
                YANibbleArray.fromOwnedBytes(new byte[YANibbleArray.SIZE]).visibleSaveKind());
        assertEquals(YANibbleArray.SAVE_FULL,
                YANibbleArray.fromOwnedBytes(YANibbleArray.FULL_LIGHT_DATA.clone()).visibleSaveKind());
    }

    @Test
    void importsVanillaDataWithoutSharingItsMutableBytes() {
        DataLayer vanilla = new DataLayer();
        vanilla.set(X, Y, Z, 13);

        YANibbleArray light = YANibbleArray.fromVanilla(vanilla);
        vanilla.set(X, Y, Z, 1);

        assertEquals(13, light.getVisible(X, Y, Z));
        assertEquals(YANibbleArray.SAVE_DATA, light.visibleSaveKind());
        assertNotSame(vanilla.getData(), light.visibleDataForSave());

        byte[] ownedData = vanilla.getData().clone();
        ownedData[YANibbleArray.index(X, Y, Z) >>> 1] = (byte)0xDD;
        YANibbleArray owned = YANibbleArray.fromOwnedBytes(ownedData);
        assertSame(ownedData, owned.visibleDataForSave());
        assertEquals(13, owned.getVisible(X, Y, Z));
        assertThrows(IllegalArgumentException.class, () -> YANibbleArray.fromOwnedBytes(new byte[1]));
    }

    @Test
    void fillsOneColumnRunWithoutChangingAdjacentNibbles() {
        assertColumnRun(2);
        assertColumnRun(3);
    }

    private static void assertColumnRun(int localX) {
        YANibbleArray light = YANibbleArray.fullArray();
        int siblingX = localX ^ 1;

        light.fillColumnRun(localX, Z, 4, 9, 2);

        for (int y = 0; y < 16; ++y) {
            int expected = y >= 4 && y <= 9 ? 2 : 15;
            assertEquals(expected, light.getUpdating(localX, y, Z),
                    "target x=" + localX + ", y=" + y);
            assertEquals(15, light.getUpdating(siblingX, y, Z),
                    "sibling x=" + siblingX + ", y=" + y);
        }
        assertEquals(15, light.getVisible(localX, 4, Z));

        light.publish();
        byte[] saveData = light.visibleDataForSave();
        assertEquals(2, light.getVisible(localX, 4, Z));
        assertEquals(15, light.getVisible(siblingX, 4, Z));
        assertEquals(YANibbleArray.SAVE_DATA, light.visibleSaveKind());
        assertEquals(YANibbleArray.SIZE, saveData.length);
        assertArrayEquals(saveData, light.toVanilla().getData());
    }
}
