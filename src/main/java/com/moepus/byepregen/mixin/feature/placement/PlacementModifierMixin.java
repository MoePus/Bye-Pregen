package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.worldgen.feature.FastPlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PlacementModifier.class, remap = false)
public abstract class PlacementModifierMixin implements FastPlacementModifier {
}
