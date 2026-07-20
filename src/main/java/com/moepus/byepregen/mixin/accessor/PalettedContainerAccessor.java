package com.moepus.byepregen.mixin.accessor;

import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PalettedContainer.class)
public interface PalettedContainerAccessor<T> {
    @Accessor("data")
    PalettedContainer.Data<T> byepregen$getData();

    @Accessor("data")
    void byepregen$setData(PalettedContainer.Data<T> data);

    @Accessor("registry")
    IdMap<T> byepregen$getRegistry();

    @Accessor("strategy")
    PalettedContainer.Strategy byepregen$getStrategy();

    @Invoker("createOrReuseData")
    PalettedContainer.Data<T> byepregen$invokeCreateOrReuseData(PalettedContainer.Data<T> data, int bits);
}
