package com.moepus.byepregen.palette.access;

import net.minecraft.core.IdMap;

public interface PaletteRawIdAccess {
    int byepregen$rawIdForLocalId(int localId, IdMap<?> globalMap);
}
