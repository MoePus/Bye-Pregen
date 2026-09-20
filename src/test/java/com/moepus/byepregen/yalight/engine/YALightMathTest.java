package com.moepus.byepregen.yalight.engine;

import com.moepus.byepregen.yalight.storage.YANibbleArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class YALightMathTest {
    @Test
    void neighborsMatchCartesianCoordinatesForEveryCellAndDirection() {
        for (int index = 0; index < 4096; ++index) {
            int x = index & 15;
            int y = index >>> 8;
            int z = (index >>> 4) & 15;
            boolean interior = x > 0 && x < 15 && y > 0 && y < 15 && z > 0 && z < 15;
            assertEquals(interior, YALightMath.isSectionInterior(index));
            for (Direction direction : Direction.values()) {
                int nx = x + direction.getStepX();
                int ny = y + direction.getStepY();
                int nz = z + direction.getStepZ();
                int edge = YALightMath.neighbor(index, direction.ordinal());
                assertEquals(YANibbleArray.index(nx, ny, nz), YALightMath.neighborIndex(edge));
                assertEquals((nx >> 4) + (ny >> 4) + (nz >> 4), YALightMath.sectionCarry(edge));
                if (interior) {
                    assertEquals(YANibbleArray.index(nx, ny, nz), index + YALightMath.localStep(direction.ordinal()));
                }
            }
        }
    }

    @Test
    void absoluteOffsetsAndSectionKeysHandleNegativeAndExtremeCoordinates() {
        int[] horizontal = {-33554432, -30000000, -17, -16, -1, 0, 15, 16, 30000000, 33554431};
        int[] vertical = {-2048, -81, -64, -17, -16, -1, 0, 15, 16, 319, 335, 2047};
        for (int x : horizontal) {
            for (int z : horizontal) {
                for (int y : vertical) {
                    verifyPosition(x, y, z);
                }
            }
        }
    }

    private static void verifyPosition(int x, int y, int z) {
        long pos = BlockPos.asLong(x, y, z);
        assertEquals(YANibbleArray.index(x, y, z), YALightMath.localIndex(pos));
        assertEquals(BlockPos.asLong(x & ~15, y & ~15, z & ~15), YALightMath.sectionKey(pos));
        assertNotEquals(1L, YALightMath.sectionKey(pos));
        for (Direction direction : Direction.values()) {
            long expected = BlockPos.asLong(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
            assertEquals(expected, YALightMath.offset(pos, direction.ordinal()));
        }
    }
}
