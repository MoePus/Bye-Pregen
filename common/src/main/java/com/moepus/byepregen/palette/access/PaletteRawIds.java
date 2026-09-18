package com.moepus.byepregen.palette.access;

import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.SingleValuePalette;

/** Raw-ID access without assuming third-party palettes have the vanilla representation. */
public final class PaletteRawIds {
    private PaletteRawIds() { }

    public static <T> int get(Palette<T> palette, int localId, IdMap<T> globalMap) {
        Class<?> type = palette.getClass();
        boolean vanilla = type == GlobalPalette.class || type == HashMapPalette.class
                || type == LinearPalette.class || type == SingleValuePalette.class;
        if (vanilla && palette instanceof PaletteRawIdAccess rawIds) {
            return rawIds.byepregen$rawIdForLocalId(localId, globalMap);
        }
        // Subclasses may override valueFor even when they inherit our mixin interface.
        return globalMap.getId(palette.valueFor(localId));
    }

    /** Global raw ID of an already resolved palette value; the wildcard needs the raw call. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static int rawId(IdMap<?> globalMap, Object value) {
        return ((IdMap) globalMap).getId(value);
    }
}
