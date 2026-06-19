package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PaletteContainer.FastPalette.FastPlacementContext;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = PlacedFeature.class, remap = false)
public abstract class PlacedFeatureMixin {
    @Shadow
    @Final
    private Holder<ConfiguredFeature<?, ?>> feature;

    @Shadow
    @Final
    private List<PlacementModifier> placement;

    /**
     * @author MoePus, Codex
     * @reason Avoid stream, lambda, iterator, MutableBoolean, and intermediate BlockPos allocation in feature placement.
     */
    @Overwrite
    private boolean placeWithContext(PlacementContext context, RandomSource random, BlockPos pos) {
        FastPlacementContext fastContext = FastPlacementContext.acquire(context, random, this.feature.value(), this.placement);
        try {
            return fastContext.apply(0, pos.getX(), pos.getY(), pos.getZ());
        } finally {
            FastPlacementContext.release(fastContext);
        }
    }
}
