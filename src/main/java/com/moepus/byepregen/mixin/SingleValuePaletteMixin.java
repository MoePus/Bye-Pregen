package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PaletteContainer.PaletteRawIdAccess;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SingleValuePalette.class, remap = false)
public abstract class SingleValuePaletteMixin<T> implements PaletteRawIdAccess {
    @Shadow
    @Final
    private IdMap<T> registry;

    @Shadow
    private T value;

    @Override
    public int byepregen$rawIdForLocalId(int localId) {
        T value = this.value;
        return localId == 0 && value != null ? this.registry.getId(value) : -1;
    }
}
