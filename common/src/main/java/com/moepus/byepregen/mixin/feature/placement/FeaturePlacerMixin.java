package com.moepus.byepregen.mixin.feature.placement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastPlacedFeature;
import com.moepus.byepregen.worldgen.feature.FastPlacementContext;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(config = ConfigFlag.PLACED_FEATURES, conflictingMods = {"biolith", "confluence"})
@Mixin(FeaturePlacer.class)
public abstract class FeaturePlacerMixin {
    @Shadow @Final private WorldGenLevel level;
    @Shadow @Final private ChunkGenerator generator;

    @WrapMethod(method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;"
            + "Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z")
    private boolean byepregen$place(PlacedFeature feature, RandomSource random, BlockPos origin,
                                    boolean biomeCheck, Operation<Boolean> original) {
        if (!((Object) feature instanceof FastPlacedFeature fast)) return original.call(feature, random, origin, biomeCheck);
        var context = new PlacementContext(this.level, this.generator,
                biomeCheck ? Optional.of(feature) : Optional.empty());
        FastPlacementContext fastContext = FastPlacementContext.acquire(context, random,
                feature.feature().value(), feature.placement());
        try {
            return fastContext.place(origin, fast.byepregen$featurePlan());
        } finally {
            FastPlacementContext.release(fastContext);
        }
    }
}
