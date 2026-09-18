package com.moepus.byepregen.mixin.climate;

import com.moepus.byepregen.worldgen.biome.ClimateRTreeCacheNode;
import com.moepus.byepregen.worldgen.biome.FastClimateRTree;
import com.moepus.byepregen.worldgen.biome.ClimateRTreeSearchContext;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Climate;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Climate.RTree.class)
public abstract class ClimateRTreeSearchMixin<T> implements FastClimateRTree<T> {
    @Shadow
    @Final
    private Climate.RTree.Node<T> root;

    @Shadow
    @Final
    @Mutable
    private ThreadLocal<Climate.RTree.Leaf<T>> lastResult;

    @InjectLite(method = "<init>", at = @At("RETURN"))
    private void byepregen$installSearchContext() {
        this.lastResult = new ClimateRTreeSearchContext<>();
    }

    @Override
    public final T byepregen$search(final Climate.TargetPoint targetPoint) {
        final ClimateRTreeSearchContext.State<T> state = this.byepregen$context();
        state.setTarget(targetPoint);
        return this.byepregen$dispatch(state);
    }

    @Override
    public final T byepregen$search(final long[] target) {
        final ClimateRTreeSearchContext.State<T> state = this.byepregen$context();
        state.setTarget(target);
        return this.byepregen$dispatch(state);
    }

    /**
     * Uses the depth-column cache when this tree has one and the target only moved in depth.
     *
     * <p>The cache is present exactly when the node mixin numbered the tree ({@link
     * ClimateRTreeCacheNode}); without it (the DFC gate is off) this is the plain search. 26.2
     * entered the depth path from the biome column filler, the search recognizes the column itself,
     * so the vanilla per-quart walk and structure lookups benefit as well.</p>
     */
    @Unique
    private T byepregen$dispatch(final ClimateRTreeSearchContext.State<T> state) {
        if (this.root instanceof ClimateRTreeCacheNode) {
            if (state.columnMatches()) {
                return this.byepregen$searchDepth(state);
            }
            state.beginDepthColumn(state.values);
        }
        return this.byepregen$search(state);
    }

    /** 26.2's depth-only descent: the six fixed distances are memoized per column. */
    @Unique
    private T byepregen$searchDepth(final ClimateRTreeSearchContext.State<T> state) {
        ClimateRTreeSearchContext.recordColumnSearch();
        state.setDepth(state.values[ClimateRTreeSearchContext.DEPTH_PARAMETER_INDEX]);
        state.bestDistance = state.bestLeaf == null
                ? Long.MAX_VALUE : state.depthOnlyDistance(state.bestLeaf);
        if (state.bestDistance != 0L) {
            this.byepregen$searchDepthNode(this.root, state);
        }
        return state.bestLeaf.value;
    }

    @Unique
    private boolean byepregen$searchDepthNode(
            final Climate.RTree.Node<T> node,
            final ClimateRTreeSearchContext.State<T> state
    ) {
        final long nodeDistance = state.depthOnlyDistance(node);
        if (state.bestDistance <= nodeDistance) return false;
        if (node instanceof Climate.RTree.Leaf<T> leaf) {
            state.bestLeaf = leaf;
            state.bestDistance = nodeDistance;
            return nodeDistance == 0L;
        }
        for (Climate.RTree.Node<T> child : ((Climate.RTree.SubTree<T>) node).children) {
            if (this.byepregen$searchDepthNode(child, state)) return true;
        }
        return false;
    }

    @Unique
    private T byepregen$search(final ClimateRTreeSearchContext.State<T> state) {
        state.bestDistance = state.bestLeaf == null ? Long.MAX_VALUE : byepregen$distance(state.bestLeaf, state.values);

        // The root is entered unconditionally, as in the native search. Keep strict
        // comparisons and child order so ties retain the previous query's candidate.
        if (this.root instanceof Climate.RTree.Leaf<T> leaf) {
            state.bestLeaf = leaf;
        } else {
            this.byepregen$searchChildren((Climate.RTree.SubTree<T>) this.root, state);
        }
        return state.bestLeaf.value;
    }

    @Unique
    private void byepregen$searchChildren(
            final Climate.RTree.SubTree<T> subtree,
            final ClimateRTreeSearchContext.State<T> state
    ) {
        for (Climate.RTree.Node<T> child : subtree.children) {
            final long distance = byepregen$distance(child, state.values);
            if (state.bestDistance <= distance) continue;
            if (child instanceof Climate.RTree.Leaf<T> leaf) {
                state.bestLeaf = leaf;
                state.bestDistance = distance;
            } else {
                this.byepregen$searchChildren((Climate.RTree.SubTree<T>) child, state);
            }
        }
    }

    @Unique
    private ClimateRTreeSearchContext.State<T> byepregen$context() {
        return ((ClimateRTreeSearchContext<T>)this.lastResult).context();
    }

    @Unique
    private static long byepregen$distance(final Climate.RTree.Node<?> node, final long[] values) {
        long distance = 0L;
        for (int index = 0; index < ClimateRTreeSearchContext.PARAMETER_COUNT; index++) {
            distance += Mth.square(node.parameterSpace[index].distance(values[index]));
        }
        return distance;
    }
}
