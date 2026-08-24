package com.moepus.byepregen.mixin.palette;

import com.moepus.byepregen.palette.access.PaletteRawIdAccess;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.chunk.HashMapPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = HashMapPalette.class, remap = false)
public abstract class HashMapPaletteMixin<T> implements PaletteRawIdAccess {
    @Shadow
    @Final
    private CrudeIncrementalIntIdentityHashBiMap<T> values;

    @Override
    public int byepregen$rawIdForLocalId(int localId, net.minecraft.core.IdMap<?> globalMap) {
        T value = this.values.byId(localId);
        return value == null ? -1 : byepregen$getRawId(globalMap, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int byepregen$getRawId(net.minecraft.core.IdMap<?> globalMap, Object value) {
        return ((net.minecraft.core.IdMap) globalMap).getId(value);
    }
}
