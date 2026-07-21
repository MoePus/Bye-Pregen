package com.moepus.byepregen.worldgen;

public interface ArenaNoiseChunkAccess {
    void byepregen$initializeArenaInterpolation(double inverseCellWidth);

    void byepregen$advanceArenaCellX(int cellX);

    void byepregen$prepareArenaCellXZ(int cellZ, int blockX, double deltaX);

    void byepregen$beginArenaColumn(int blockZ);

    void byepregen$selectArenaColumnCellY(int cellY);

    void byepregen$startArenaPage();

    void byepregen$advanceArenaPageY();

    void byepregen$setArenaPageLowerStepY();

    void byepregen$setArenaPageLowerY();

    void byepregen$finishArenaCellX();

    void byepregen$releaseArenaInterpolation();
}
