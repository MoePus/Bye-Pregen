package com.moepus.byepregen.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EntitySectionStorage.class, remap = false)
public interface EntitySectionStorageAccessor<T extends EntityAccess> {
    @Accessor("sections")
    Long2ObjectMap<EntitySection<T>> byepregen$getSections();
}
