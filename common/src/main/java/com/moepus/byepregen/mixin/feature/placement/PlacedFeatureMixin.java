package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.FastPlacedFeature;
import com.moepus.byepregen.worldgen.feature.FeaturePlan;
import com.moepus.byepregen.worldgen.feature.PredicateMemoizedDiskPlacement;
import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(config = ConfigFlag.PLACED_FEATURES)
@Mixin(value = PlacedFeature.class, remap = false)
public abstract class PlacedFeatureMixin implements FastPlacedFeature {
    @Unique
    private volatile FeaturePlan byepregen$featurePlan;

    @Shadow
    @Final
    private Holder<ConfiguredFeature<?, ?>> feature;

    @Shadow
    @Final
    private List<PlacementModifier> placement;

    @InjectLite(method = "<init>", at = @At("RETURN"))
    private void byepregen$compileFeaturePlan(
            Holder<ConfiguredFeature<?, ?>> feature,
            List<PlacementModifier> placement
    ) {
        if (feature.isBound()) {
            this.byepregen$featurePlan = FeaturePlan.create(feature.value(), placement);
        }
    }

    /**
     * @author MoePus, Codex
     * @reason Avoid stream, lambda, iterator, MutableBoolean, and intermediate BlockPos allocation in feature placement.
     */
    @Overwrite
    public boolean placeWithContext(PlacementContext context, RandomSource random, BlockPos pos) {
        return this.byepregen$place(context, random, pos);
    }

    @Override
    public boolean byepregen$placeWithContext(FastPlacementContext parent, RandomSource random, BlockPos pos) {
        return this.byepregen$place(parent.nestedPlacementContext(), random, pos);
    }

    private boolean byepregen$place(PlacementContext context, RandomSource random, BlockPos pos) {
        FastPlacementContext fastContext = FastPlacementContext.acquire(context, random, this.feature.value(), this.placement);
        try {
            FeaturePlan featurePlan = this.byepregen$featurePlan;
            if (featurePlan == null) {
                featurePlan = FeaturePlan.create(this.feature.value(), this.placement);
                this.byepregen$featurePlan = featurePlan;
            }
            PredicateMemoizedDiskPlacement memoizedPlacement = featurePlan.open(fastContext);
            if (memoizedPlacement == null) {
                return fastContext.apply(0, pos.getX(), pos.getY(), pos.getZ());
            }
            fastContext.terminal(memoizedPlacement::placeOrigin);
            fastContext.apply(0, pos.getX(), pos.getY(), pos.getZ());
            return memoizedPlacement.placed();
        } finally {
            FastPlacementContext.release(fastContext);
        }
    }
}
