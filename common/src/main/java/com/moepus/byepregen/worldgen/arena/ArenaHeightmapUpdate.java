package com.moepus.byepregen.worldgen.arena;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ArenaHeightmapUpdate {
    private ArenaHeightmapUpdate() {
    }

    public static boolean isNeeded(
            Heightmap.Types type,
            BlockState state,
            int heightComparison
    ) {
        return isNeeded(type.isOpaque().test(state), heightComparison);
    }

    public static boolean isNeeded(boolean opaque, int heightComparison) {
        return opaque ? heightComparison > 0 : heightComparison == 0;
    }
}
