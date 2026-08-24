package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.FastPlacementModifier;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import org.spongepowered.asm.mixin.Mixin;

@MixinGate(config = ConfigFlag.PLACED_FEATURES)
@Mixin(value = InSquarePlacement.class, remap = false)
public abstract class InSquarePlacementMixin implements FastPlacementModifier {
    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z, int nextIndex) {
        context.apply(nextIndex, x + context.random().nextInt(16), y, z + context.random().nextInt(16));
    }
}
