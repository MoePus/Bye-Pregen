package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.FastPlacementModifier;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.OffsetPlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(OffsetPlacement.class)
public abstract class OffsetPlacementMixin implements FastPlacementModifier {
    @Shadow @Final private IntProvider x;
    @Shadow @Final private IntProvider y;
    @Shadow @Final private IntProvider z;

    @Override
    public void byepregen$collectPositions(FastPlacementContext context, int x, int y, int z) {
        context.emit(x + this.x.sample(context.random()), y + this.y.sample(context.random()),
                z + this.z.sample(context.random()));
    }
}
