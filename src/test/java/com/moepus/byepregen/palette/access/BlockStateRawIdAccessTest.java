package com.moepus.byepregen.palette.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockStateRawIdAccessTest {
    @Test
    void coordinateOnlyProvidersRemainUsableByLinearReaders() {
        BlockStateRawIdAccess provider = (x, y, z) -> x == 3 && y == 11 && z == 7 ? 42 : 0;
        for (int index = 0; index < 4096; ++index) {
            assertEquals(index == 2931 ? 42 : 0, provider.getRawId(index));
        }
    }
}
