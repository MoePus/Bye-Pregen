package com.moepus.byepregen.worldgen;

public interface ArenaNoiseInterpolatorAccess {
    void byepregen$allocateArenaGrid(int pointCountY, int pointCountXZ);

    void byepregen$storeArenaColumn(int pointX, int pointZ, double[] values);

    void byepregen$selectArenaCell(int cellX, int cellY, int cellZ);

    void byepregen$releaseArenaGrid();
}
