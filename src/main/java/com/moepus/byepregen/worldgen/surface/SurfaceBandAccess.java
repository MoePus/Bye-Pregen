package com.moepus.byepregen.worldgen.surface;

import net.minecraft.world.level.block.state.BlockState;

public interface SurfaceBandAccess {
    BlockState byepregen$getBand(int x, int y, int z);
}
