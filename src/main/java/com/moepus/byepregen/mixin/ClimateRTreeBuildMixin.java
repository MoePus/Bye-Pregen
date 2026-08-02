package com.moepus.byepregen.mixin;

import com.moepus.byepregen.optimization.ClimateRTreeBuildOptimizer;
import java.util.List;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Climate.RTree.class)
public abstract class ClimateRTreeBuildMixin {
    /**
     * @author moepus
     * @reason Evaluate split candidates without constructing discarded temporary subtrees.
     */
    @Overwrite
    private static <T> Climate.RTree.Node<T> build(
            final int parameterCount,
            final List<? extends Climate.RTree.Node<T>> children
    ) {
        return ClimateRTreeBuildOptimizer.build(parameterCount, children);
    }

    /**
     * @author moepus
     * @reason Aggregate primitive bounds before allocating the final seven parameters.
     */
    @Overwrite
    static <T> List<Climate.Parameter> buildParameterSpace(
            final List<? extends Climate.RTree.Node<T>> children
    ) {
        return ClimateRTreeBuildOptimizer.buildParameterSpace(children);
    }
}
