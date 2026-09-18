package com.moepus.byepregen.mixin.feature.placement;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastPlacedFeature;
import com.moepus.byepregen.worldgen.feature.FeaturePlan;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@MixinGate(config = ConfigFlag.PLACED_FEATURES, conflictingMods = {"biolith", "confluence"})
@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin implements FastPlacedFeature {
    @Shadow @Final private Holder<Feature> feature;
    @Shadow @Final private List<PlacementModifier> placement;
    @Unique private volatile FeaturePlan byepregen$plan;

    @Override
    public FeaturePlan byepregen$featurePlan() {
        FeaturePlan plan = this.byepregen$plan;
        if (plan == null) {
            plan = FeaturePlan.create(this.feature.value(), this.placement);
            this.byepregen$plan = plan;
        }
        return plan;
    }
}
