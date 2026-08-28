package com.moepus.byepregen.mixin.climate;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.biome.DepthClimateParameterList;
import com.moepus.byepregen.worldgen.biome.DepthClimateRTree;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(
        feature = MixinFeature.DFC,
        conflictingMods = {"reterraforged", "terrablender", "blueprint"}
)
@Mixin(Climate.ParameterList.class)
public abstract class ClimateParameterListColumnMixin<T> implements DepthClimateParameterList<T> {
    @Shadow @Final private Climate.RTree<T> index;

    @Override
    @SuppressWarnings("unchecked")
    public final void byepregen$beginDepthColumn(long[] target) {
        ((DepthClimateRTree<T>)(Object)this.index).byepregen$beginDepthColumn(target);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final T byepregen$findValueAtDepth(long depth) {
        return ((DepthClimateRTree<T>)(Object)this.index).byepregen$searchDepth(depth);
    }
}
