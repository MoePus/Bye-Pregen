package com.moepus.byepregen.worldgen;

public interface ArenaNoiseChunkAccess {
    void byepregen$initializeArenaInterpolation();

    void byepregen$selectArenaCell(int cellX, int cellY, int cellZ);

    void byepregen$updateArenaForX(int blockX, double deltaX);

    void byepregen$updateArenaForZ(int blockZ, double deltaZ);

    void byepregen$updateArenaForY(int blockY, double deltaY);

    void byepregen$releaseArenaInterpolation();
}
