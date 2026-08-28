package com.moepus.byepregen.mixin.climate;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.biome.ClimateRTreeCacheNode;
import com.moepus.byepregen.worldgen.biome.ClimateRTreeSearchContext;
import com.moepus.byepregen.worldgen.biome.DepthClimateRTree;
import net.minecraft.world.level.biome.Climate;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(
        feature = MixinFeature.DFC,
        conflictingMods = {"reterraforged", "terrablender", "blueprint"}
)
@Mixin(Climate.RTree.class)
public abstract class ClimateRTreeColumnMixin<T> implements DepthClimateRTree<T> {
    @Shadow @Final private Climate.RTree.Node<T> root;
    @Shadow @Final private ThreadLocal<Climate.RTree.Leaf<T>> lastResult;
    @Unique private int byepregen$columnCacheNodeCount;

    @InjectLite(method = "<init>", at = @At("RETURN"))
    private void byepregen$indexColumnCacheNodes() {
        this.byepregen$columnCacheNodeCount = byepregen$indexNode(this.root, 0);
    }

    @Override
    public final void byepregen$beginDepthColumn(long[] target) {
        this.byepregen$columnContext().beginDepthColumn(
                target, this.byepregen$columnCacheNodeCount);
    }

    @Override
    public final T byepregen$searchDepth(long depth) {
        ClimateRTreeSearchContext.State<T> state = this.byepregen$columnContext();
        state.setDepth(depth);
        state.bestDistance = state.bestLeaf == null
                ? Long.MAX_VALUE : state.depthOnlyDistance(state.bestLeaf);
        if (state.bestDistance != 0L) this.byepregen$searchDepthNode(this.root, state);
        return state.bestLeaf.value;
    }

    @Unique
    private boolean byepregen$searchDepthNode(
            Climate.RTree.Node<T> node,
            ClimateRTreeSearchContext.State<T> state
    ) {
        long nodeDistance = state.depthOnlyDistance(node);
        if (state.bestDistance <= nodeDistance) return false;
        if (node instanceof Climate.RTree.Leaf<T> leaf) {
            state.bestLeaf = leaf;
            state.bestDistance = nodeDistance;
            return nodeDistance == 0L;
        }
        Climate.RTree.SubTree<T> subtree = (Climate.RTree.SubTree<T>)node;
        for (Climate.RTree.Node<T> child : subtree.children) {
            if (this.byepregen$searchDepthNode(child, state)) return true;
        }
        return false;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private ClimateRTreeSearchContext.State<T> byepregen$columnContext() {
        return ((ClimateRTreeSearchContext<T>)this.lastResult).context();
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
