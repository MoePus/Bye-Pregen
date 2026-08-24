package com.moepus.byepregen.mixin.climate;

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
        state.bestDistance = state.bestLeaf == null ? Long.MAX_VALUE : byepregen$distance(state.bestLeaf, state.values);

        if (state.bestDistance != 0L) {
            this.byepregen$searchNode(this.root, state);
        }

        return state.bestLeaf.value;
    }

    @Unique
    private boolean byepregen$searchNode(
            final Climate.RTree.Node<T> node,
            final ClimateRTreeSearchContext.State<T> state
    ) {
        final long nodeDistance = byepregen$distance(node, state.values);
        if (state.bestDistance <= nodeDistance) {
            return false;
        }

        if (node instanceof Climate.RTree.Leaf<T> leaf) {
            state.bestLeaf = leaf;
            state.bestDistance = nodeDistance;
            return nodeDistance == 0L;
        }

        final Climate.RTree.SubTree<T> subtree = (Climate.RTree.SubTree<T>)node;
        for (Climate.RTree.Node<T> child : subtree.children) {
            if (this.byepregen$searchNode(child, state)) {
                return true;
            }
        }
        return false;
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
