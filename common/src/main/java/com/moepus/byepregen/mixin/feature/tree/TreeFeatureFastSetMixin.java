package com.moepus.byepregen.mixin.feature.tree;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.feature.FastObjectHashSet;
import java.util.HashSet;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(config = ConfigFlag.PLACED_FEATURE_LOCAL_OPTIMIZATIONS)
@Mixin(TreeFeature.class)
public abstract class TreeFeatureFastSetMixin {
    @Redirect(method = "place", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;"), require = 4, allow = 4)
    private <E> HashSet<E> byepregen$placeSet() {
        // Decorators sort only by Y, so changing equal-height order can move visible decorations.
        return ((TreeFeature) (Object) this).decorators().isEmpty() ? new FastObjectHashSet<>() : new HashSet<>();
    }

    // Open addressing changes traversal within each distance bucket and may change assigned distances.
    // Accept live leaf distances 1..6 differing while preserving vanilla bucket priorities and appearance.
    // Runtime comparisons still check the distance=7 decay boundary, positions and all other properties.
    // Related leaf update events may differ; leaf update and decay logic remain vanilla.
    @Redirect(method = "updateLeaves", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;"), require = 1, allow = 1)
    private static <E> HashSet<E> byepregen$updateLeavesSet() {
        return new FastObjectHashSet<>();
    }
}
