package com.moepus.byepregen.mixin.climate;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.biome.ClimateRTreeCacheNode;
import net.minecraft.world.level.biome.Climate;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Numbers every node of a climate tree so the depth-column cache can memoize one fixed distance per
 * node. The descent itself lives in {@code ClimateRTreeSearchMixin} next to the plain search; 26.2
 * exposed it through {@code DepthClimateRTree} for its biome column filler.
 */
@MixinGate(
        feature = MixinFeature.DFC,
        conflictingMods = {"reterraforged", "terrablender", "blueprint"}
)
@Mixin(Climate.RTree.class)
public abstract class ClimateRTreeColumnMixin<T> {
    @Shadow @Final private Climate.RTree.Node<T> root;

    @InjectLite(method = "<init>", at = @At("RETURN"))
    private void byepregen$indexColumnCacheNodes() {
        byepregen$indexNode(this.root, 0);
    }

    @Unique
    private static int byepregen$indexNode(Climate.RTree.Node<?> node, int index) {
        ((ClimateRTreeCacheNode)(Object)node).byepregen$setCacheIndex(index++);
        if (node instanceof Climate.RTree.SubTree<?> subtree) {
            for (Climate.RTree.Node<?> child : subtree.children) {
                index = byepregen$indexNode(child, index);
            }
        }
        return index;
    }
}
