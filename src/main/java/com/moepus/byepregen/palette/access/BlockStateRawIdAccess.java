package com.moepus.byepregen.palette.access;

public interface BlockStateRawIdAccess {
    int getRawId(int x, int y, int z);

    /** Block-section storage index: x | (z << 4) | (y << 8). */
    default int getRawId(int index) {
        return this.getRawId(index & 15, index >>> 8, (index >>> 4) & 15);
    }
}
