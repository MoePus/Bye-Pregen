package com.moepus.byepregen.mixin.palette;

import com.moepus.byepregen.palette.access.PaletteRawIdAccess;
import net.minecraft.world.level.chunk.LinearPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LinearPalette.class, remap = false)
public abstract class LinearPaletteMixin<T> implements PaletteRawIdAccess {
    @Shadow
    @Final
    private T[] values;

    @Shadow
    private int size;

    @Override
    public int byepregen$rawIdForLocalId(int localId, net.minecraft.core.IdMap<?> globalMap) {
        if (localId < 0 || localId >= this.size) {
            return -1;
        }
        return byepregen$getRawId(globalMap, this.values[localId]);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int byepregen$getRawId(net.minecraft.core.IdMap<?> globalMap, Object value) {
        return ((net.minecraft.core.IdMap) globalMap).getId(value);
    }
}
