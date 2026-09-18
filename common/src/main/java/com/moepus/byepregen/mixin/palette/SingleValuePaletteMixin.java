package com.moepus.byepregen.mixin.palette;

import com.moepus.byepregen.palette.access.PaletteRawIdAccess;
import com.moepus.byepregen.palette.access.PaletteRawIds;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SingleValuePalette.class, remap = false)
public abstract class SingleValuePaletteMixin<T> implements PaletteRawIdAccess {
    @Shadow
    private T value;

    @Override
    public int byepregen$rawIdForLocalId(int localId, net.minecraft.core.IdMap<?> globalMap) {
        T value = this.value;
        return localId == 0 && value != null ? PaletteRawIds.rawId(globalMap, value) : -1;
    }
}
