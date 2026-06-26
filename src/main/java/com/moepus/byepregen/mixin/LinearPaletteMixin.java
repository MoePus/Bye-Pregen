package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PaletteContainer.PaletteRawIdAccess;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.LinearPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LinearPalette.class, remap = false)
public abstract class LinearPaletteMixin<T> implements PaletteRawIdAccess {
    @Shadow
    @Final
    private IdMap<T> registry;

    @Shadow
    @Final
    private T[] values;

    @Shadow
    private int size;

    @Override
    public int byepregen$rawIdForLocalId(int localId) {
        if (localId < 0 || localId >= this.size) {
            return -1;
        }
        return this.registry.getId(this.values[localId]);
    }
}
