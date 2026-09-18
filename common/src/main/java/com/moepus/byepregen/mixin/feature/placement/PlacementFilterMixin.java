package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.FastPlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import org.spongepowered.asm.mixin.Mixin;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(PlacementFilter.class)
public interface PlacementFilterMixin extends FastPlacementModifier {
    @Override
    default void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z) {
        if (((PlacementFilter) this).shouldPlace(context.placementContext(), context.random(), context.modifierPos(x, y, z))) {
            context.emit(x, y, z);
        }
    }
}
