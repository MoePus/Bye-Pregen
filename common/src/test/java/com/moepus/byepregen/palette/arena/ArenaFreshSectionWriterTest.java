package com.moepus.byepregen.palette.arena;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaFreshSectionWriterTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void preservesEveryVoxelWhenPrimaryAndSecondaryStatesAlternate() {
        var arena = new ArenaBlockStatePalettedContainer();
        var writer = arena.beginTerrainWrite(1);
        int[] expected = new int[Layout.SECTION_SIZE];
        for (int column = 0; column < Layout.SECTION_WIDTH * Layout.SECTION_WIDTH; column++) {
            for (int y = Layout.SECTION_WIDTH - 1; y >= 0; y--) {
                int page = y / Layout.PAGE_HEIGHT;
                int local = (y % Layout.PAGE_HEIGHT) * 256 + column;
                int value = (y + column) % 3 == 0 ? 5 + page : 1;
                writer.page(page).write(local, value);
                expected[Layout.sectionIndex(page, local)] = value;
            }
        }
        assertVoxels(arena, expected);
        assertTrue(arena.hasPagePalettes());
    }

    @Test void keepsAllBoundPagesValidAfterAnotherPagePromotesTheSection() {
        var arena = new ArenaBlockStatePalettedContainer();
        var writer = arena.beginTerrainWrite(1);
        int[] expected = new int[Layout.SECTION_SIZE];
        Arrays.fill(expected, ArenaBlockStatePalettedContainer.AIR_RAW_ID);
        for (int page = 0; page < Layout.PAGE_COUNT; page++) {
            writer.page(page).write(0, 1);
            expected[Layout.sectionIndex(page, 0)] = 1;
        }
        for (int local = 1; local <= Layout.PAGE_PALETTE_SIZE; local++) {
            writer.page(1).write(local, local + 1);
            expected[Layout.sectionIndex(1, local)] = local + 1;
        }
        assertTrue(arena.hasDenseIds());
        for (int page = 0; page < Layout.PAGE_COUNT; page++) {
            writer.page(page).write(31, 1);
            writer.page(page).write(32, 9);
            expected[Layout.sectionIndex(page, 31)] = 1;
            expected[Layout.sectionIndex(page, 32)] = 9;
        }
        assertVoxels(arena, expected);
        assertEquals(Arrays.stream(expected).filter(id -> id == 1).count(), arena.denseRawIdCounts().get(1));
        arena.setRawId(31, 13);
        assertEquals(13, arena.rawIdAt(31));
    }

    @Test void leavesAirSectionsCompactAndRejectsPreviouslyPopulatedContainers() {
        var arena = new ArenaBlockStatePalettedContainer();
        var writer = arena.beginTerrainWrite(1);
        writer.page(2).write(3, ArenaBlockStatePalettedContainer.AIR_RAW_ID);
        assertTrue(arena.isFreshAirForWorldgen());
        writer.page(2).write(3, 1);
        assertThrows(IllegalStateException.class, () -> arena.beginTerrainWrite(1));
    }

    private static void assertVoxels(ArenaBlockStatePalettedContainer arena, int[] expected) {
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], arena.rawIdAt(i), "Voxel " + i);
    }
}
