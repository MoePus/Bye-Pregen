package com.moepus.byepregen.mixin;

import com.moepus.byepregen.Feature.FastPlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlacementModifier.class)
public abstract class PlacementModifierMixin implements FastPlacementModifier {
}
