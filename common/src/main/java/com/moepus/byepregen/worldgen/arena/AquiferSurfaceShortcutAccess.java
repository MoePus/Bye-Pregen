package com.moepus.byepregen.worldgen.arena;

public interface AquiferSurfaceShortcutAccess {
    void byepregen$beginAquiferSurfaceColumn(int fluidUpperBound);

    void byepregen$endAquiferSurfaceColumn();

    boolean byepregen$canSkipAquifer(int blockY);
}
