package com.moepus.byepregen.chunksave.compat;

import com.moepus.byepregen.integration.platform.PlatformServices;
import com.moepus.byepregen.integration.tectonic.TectonicCompat;

public final class ChunkSaveHookGate {
    public static final boolean CAN_USE_RAW_SAVE = PlatformServices.get().canUseGcFreeRawChunkSave()
            && TectonicCompat.canUseRawSave();

    private ChunkSaveHookGate() {
    }
}
