package com.moepus.byepregen.mixin.climate;

import com.moepus.byepregen.Feature.FastClimateRTree;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Climate.ParameterList.class)
public abstract class ClimateParameterListSearchMixin<T> {
    @Shadow
    @Final
    private Climate.RTree<T> index;

    /**
     * @author moepus
     * @reason Use the allocation-free search path for the vanilla distance metric.
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public T findValueIndex(final Climate.TargetPoint targetPoint) {
        return ((FastClimateRTree<T>)(Object)this.index).bpg$search(targetPoint);
    }
}
