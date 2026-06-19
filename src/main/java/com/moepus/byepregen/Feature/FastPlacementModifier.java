package com.moepus.byepregen.Feature;

import com.moepus.byepregen.PaletteContainer.FastPalette.FastPlacementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public interface FastPlacementModifier {
    default void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        BlockPos pos = new BlockPos(x, y, z);
        ((PlacementModifier)(Object)this)
            .getPositions(context.placementContext(), context.random(), pos)
            .forEach(nextPos -> context.apply(nextIndex, nextPos.getX(), nextPos.getY(), nextPos.getZ()));
    }
}
