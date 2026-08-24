package com.moepus.byepregen.chunksave.compat;

import com.moepus.byepregen.integration.platform.PlatformServices;

public final class ChunkSaveHookGate {
    public static final boolean CAN_USE_RAW_SAVE = PlatformServices.get().canUseGcFreeRawChunkSave();

    private ChunkSaveHookGate() {
    }
}
