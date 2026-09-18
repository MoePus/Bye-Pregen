package com.moepus.byepregen.arenatest.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.featuretest.TreeSetVerifier;
import java.util.Set;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TreeFeature.class)
public abstract class TreeSetProbeMixin {
    @Inject(method = "place", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/feature/TreeFeature;doPlace("
            + "Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/util/RandomSource;"
            + "Lnet/minecraft/core/BlockPos;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;"
            + "Lnet/minecraft/world/level/levelgen/feature/foliageplacers/FoliagePlacer$FoliageSetter;)Z"))
    private void byepregen$checkSets(CallbackInfoReturnable<Boolean> callback,
                                    @Local(ordinal = 0) Set<BlockPos> roots,
                                    @Local(ordinal = 1) Set<BlockPos> trunks,
                                    @Local(ordinal = 2) Set<BlockPos> foliage,
                                    @Local(ordinal = 3) Set<BlockPos> decorations) {
        TreeSetVerifier.checkSets((TreeFeature) (Object) this, roots, trunks, foliage, decorations);
    }

    @Inject(method = "updateLeaves", at = @At(value = "INVOKE",
            target = "Ljava/util/Set;addAll(Ljava/util/Collection;)Z"))
    private static void byepregen$checkBuckets(CallbackInfoReturnable<DiscreteVoxelShape> callback,
                                              @Local List<Set<BlockPos>> buckets) {
        TreeSetVerifier.checkBuckets(buckets);
    }
}
