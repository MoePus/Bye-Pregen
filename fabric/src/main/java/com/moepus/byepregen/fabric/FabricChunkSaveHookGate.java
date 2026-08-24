package com.moepus.byepregen.fabric;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.integration.architectury.ArchitecturyChunkSaveCompat;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import java.util.List;
import org.slf4j.Logger;

final class FabricChunkSaveHookGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARCHITECTURY_CHUNK_EVENT =
            "dev.architectury.event.events.common.ChunkEvent";
    private static final boolean CAN_USE_RAW_SAVE = computeCanUseRawSave();

    private FabricChunkSaveHookGate() {
    }

    static boolean canUseRawSave() {
        return CAN_USE_RAW_SAVE;
    }

    private static boolean computeCanUseRawSave() {
        boolean canUseRawSave = !hasArchitecturySaveDataListeners();
        LOGGER.info("ByePregen Fabric GC-free raw chunk save gate: CAN_USE_RAW_SAVE={}", canUseRawSave);
        return canUseRawSave;
    }

    private static boolean hasArchitecturySaveDataListeners() {
        try {
            if (!ModEnvironment.isClassAvailable(ARCHITECTURY_CHUNK_EVENT)) {
                return false;
            }
            List<String> classNames = ArchitecturyChunkSaveCompat.saveDataListenerClassNames();
            for (String className : classNames) {
                LOGGER.warn("ByePregen raw chunk save disabled by Architectury listener: {}", className);
            }
            return !classNames.isEmpty();
        } catch (LinkageError | RuntimeException throwable) {
            LOGGER.warn("ByePregen raw chunk save disabled: failed to inspect Fabric save listeners", throwable);
            return true;
        }
    }
}
