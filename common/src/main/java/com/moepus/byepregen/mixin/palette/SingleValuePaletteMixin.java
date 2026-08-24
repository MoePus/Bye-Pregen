package com.moepus.byepregen.mixin.palette;

import com.moepus.byepregen.palette.access.PaletteRawIdAccess;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SingleValuePalette.class, remap = false)
public abstract class SingleValuePaletteMixin<T> implements PaletteRawIdAccess {
    @Shadow
    private T value;

    @Override
    public int byepregen$rawIdForLocalId(int localId, net.minecraft.core.IdMap<?> globalMap) {
        T value = this.value;
        return localId == 0 && value != null ? byepregen$getRawId(globalMap, value) : -1;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int byepregen$getRawId(net.minecraft.core.IdMap<?> globalMap, Object value) {
        return ((net.minecraft.core.IdMap) globalMap).getId(value);
    }
}
