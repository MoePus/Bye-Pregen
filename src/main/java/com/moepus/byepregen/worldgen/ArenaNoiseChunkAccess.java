package com.moepus.byepregen.worldgen;

import net.minecraft.world.level.levelgen.NoiseChunk;

public interface ArenaNoiseChunkAccess {
    NoiseChunk.BlockStateFiller byepregen$getAquiferMaterialRule();

    void byepregen$initializeArenaInterpolation(double inverseCellWidth);

    void byepregen$advanceArenaCellX(int cellX);

    void byepregen$prepareArenaCellXZ(int cellZ, int blockX, double deltaX);

    void byepregen$beginArenaColumn(int blockZ);

    boolean byepregen$prepareArenaDensityColumn(int blockX, int blockZ);

    double byepregen$getArenaDensity(int blockY);

    void byepregen$selectArenaColumnCellY(int cellY);

    void byepregen$startArenaPage();

    void byepregen$advanceArenaPageY();

    void byepregen$setArenaPageLowerStepY();

    void byepregen$setArenaPageLowerY();

    void byepregen$finishArenaCellX();

    void byepregen$releaseArenaInterpolation();
}
