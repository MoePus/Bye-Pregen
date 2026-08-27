package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.CompiledFeaturePlan;
import com.moepus.byepregen.worldgen.feature.FastFeaturePlacement;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import com.moepus.byepregen.worldgen.feature.FastPlacedFeature;
import com.moepus.byepregen.worldgen.feature.FeaturePlanCompiler;
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
    private volatile CompiledFeaturePlan byepregen$featurePlan;

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
            this.byepregen$featurePlan = FeaturePlanCompiler.compile(feature.value(), placement);
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
            CompiledFeaturePlan featurePlan = this.byepregen$featurePlan;
            if (featurePlan == null) {
                featurePlan = FeaturePlanCompiler.compile(this.feature.value(), this.placement);
                this.byepregen$featurePlan = featurePlan;
            }
            FastFeaturePlacement fastPlacement = featurePlan.open(fastContext);
            if (fastPlacement == null) {
                return fastContext.apply(0, pos.getX(), pos.getY(), pos.getZ());
            }
            fastContext.terminal(fastPlacement);
            return fastContext.apply(0, pos.getX(), pos.getY(), pos.getZ());
        } finally {
            FastPlacementContext.release(fastContext);
        }
    }
}
