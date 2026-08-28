package com.moepus.byepregen.mixin.climate;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.biome.ClimateRTreeCacheNode;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@MixinGate(
        feature = MixinFeature.DFC,
        conflictingMods = {"reterraforged", "terrablender", "blueprint"}
)
@Mixin(Climate.RTree.Node.class)
public abstract class ClimateRTreeNodeCacheMixin implements ClimateRTreeCacheNode {
    @Unique private int byepregen$cacheIndex;

    @Override
    public int byepregen$cacheIndex() {
        return this.byepregen$cacheIndex;
    }

    @Override
    public void byepregen$setCacheIndex(int index) {
        this.byepregen$cacheIndex = index;
    }
}
