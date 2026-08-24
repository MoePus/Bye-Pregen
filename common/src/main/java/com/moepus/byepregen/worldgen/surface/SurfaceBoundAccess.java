package com.moepus.byepregen.worldgen.surface;

import net.minecraft.world.level.block.state.BlockState;

public final class SurfaceBoundAccess {
    private SurfaceBoundAccess() {
    }

    public interface Condition {
        boolean test();
    }

    public interface Rule {
        BlockState tryApply(int x, int y, int z);
    }
}
