package com.moepus.byepregen.mixin.climate;

import com.moepus.byepregen.worldgen.biome.FastClimateRTree;
import com.moepus.byepregen.worldgen.biome.FastClimateParameterList;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Climate.ParameterList.class)
public abstract class ClimateParameterListSearchMixin<T> implements FastClimateParameterList<T> {
    @Shadow
    @Final
    private Climate.RTree<T> index;

    @Override
    @SuppressWarnings("unchecked")
    public final T byepregen$findValue(final long[] target) {
        return ((FastClimateRTree<T>)(Object)this.index).byepregen$search(target);
    }
}
