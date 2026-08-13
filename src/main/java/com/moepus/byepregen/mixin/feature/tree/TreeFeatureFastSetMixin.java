package com.moepus.byepregen.mixin.feature.tree;

import com.moepus.byepregen.worldgen.feature.FastObjectHashSet;
import java.util.HashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = net.minecraft.world.level.levelgen.feature.TreeFeature.class, remap = false)
public abstract class TreeFeatureFastSetMixin {
    @Redirect(
            method = "place",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;"
            )
    )
    private static <E> HashSet<E> byepregen$placeSet() {
        return new FastObjectHashSet<>();
    }

    @Redirect(
            method = "updateLeaves",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;"
            )
    )
    private static <E> HashSet<E> byepregen$updateLeavesSet() {
        return new FastObjectHashSet<>();
    }
}
