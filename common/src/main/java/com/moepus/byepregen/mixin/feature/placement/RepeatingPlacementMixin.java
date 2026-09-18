package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.FastPlacementModifier;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;
import org.spongepowered.asm.mixin.Mixin;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(RepeatingPlacement.class)
public interface RepeatingPlacementMixin extends FastPlacementModifier {
    @Override
    default void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z) {
        int count = ((RepeatingPlacement) this).count(context.random(), context.modifierPos(x, y, z));
        for (int i = 0; i < count; ++i) context.emit(x, y, z);
    }
}
