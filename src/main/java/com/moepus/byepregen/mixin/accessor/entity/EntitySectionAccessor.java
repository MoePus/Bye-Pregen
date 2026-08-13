package com.moepus.byepregen.mixin.accessor.entity;

import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EntitySection.class, remap = false)
public interface EntitySectionAccessor<T> {
    @Accessor("storage")
    ClassInstanceMultiMap<T> byepregen$getStorage();
}
