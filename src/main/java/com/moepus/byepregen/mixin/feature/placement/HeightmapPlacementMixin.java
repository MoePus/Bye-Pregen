package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.Feature.FastPlacementContext;
import com.moepus.byepregen.Feature.FastPlacementModifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = HeightmapPlacement.class, remap = false)
public abstract class HeightmapPlacementMixin implements FastPlacementModifier {
    @Shadow
    @Final
    private Heightmap.Types heightmap;

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        int height = context.placementContext().getHeight(this.heightmap, x, z);
        if (height > context.placementContext().getMinBuildHeight()) {
            context.apply(nextIndex, x, height, z);
        }
    }
}
