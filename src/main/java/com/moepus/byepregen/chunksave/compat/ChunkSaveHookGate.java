package com.moepus.byepregen.chunksave.compat;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.integration.architectury.ArchitecturyChunkSaveCompat;
import com.moepus.byepregen.integration.forge.ForgeEventBusCompat;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import com.moepus.byepregen.integration.tectonic.TectonicCompat;
import java.util.List;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.IEventListener;
import org.slf4j.Logger;

public final class ChunkSaveHookGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARCHITECTURY_CHUNK_EVENT =
            "dev.architectury.event.events.common.ChunkEvent";
    private static final String ARCHITECTURY_BRIDGE =
            "dev.architectury.event.forge.EventHandlerImplCommon";
    private static final String ARCHITECTURY_CHUNK_SAVE_METHOD = "eventChunkDataEvent";
    public static final boolean CAN_USE_RAW_SAVE = computeCanUseRawSave();

    private ChunkSaveHookGate() {
    }

    private static boolean computeCanUseRawSave() {
        boolean architecturyListeners = hasArchitecturySaveDataListeners();
        boolean forgeListeners = hasForgeSaveListeners();
        boolean canUseRawSave = !architecturyListeners
                && !forgeListeners
                && TectonicCompat.canUseRawSave();
        LOGGER.info("ByePregen GC-free raw chunk save gate: CAN_USE_RAW_SAVE={}", canUseRawSave);
        return canUseRawSave;
    }

    private static boolean hasArchitecturySaveDataListeners() {
        try {
            if (!ModEnvironment.isClassAvailable(ARCHITECTURY_CHUNK_EVENT)) {
                return false;
            }
            List<String> classNames = ArchitecturyChunkSaveCompat.saveDataListenerClassNames();
            classNames.forEach(className -> LOGGER.warn(
                    "ByePregen raw chunk save disabled by Architectury ChunkEvent.SAVE_DATA listener: {}",
                    className));
            return !classNames.isEmpty();
        } catch (LinkageError | RuntimeException throwable) {
            LOGGER.warn("ByePregen raw chunk save disabled: failed to inspect Architectury listeners", throwable);
            return true;
        }
    }

    private static boolean hasForgeSaveListeners() {
        try {
            boolean found = false;
            for (IEventListener listener : ForgeEventBusCompat.listeners(ChunkDataEvent.Save.class)) {
                if (isArchitecturyBridge(listener)) {
                    continue;
                }
                LOGGER.warn("ByePregen raw chunk save disabled by Forge ChunkDataEvent.Save listener: {}",
                        listenerDescription(listener));
                found = true;
            }
            return found;
        } catch (LinkageError | RuntimeException throwable) {
            LOGGER.warn("ByePregen raw chunk save disabled: failed to inspect Forge save listeners", throwable);
            return true;
        }
    }

    private static boolean isArchitecturyBridge(IEventListener listener) {
        String text = listener.toString();
        return text.contains(ARCHITECTURY_BRIDGE) && text.contains(ARCHITECTURY_CHUNK_SAVE_METHOD);
    }

    private static String listenerDescription(IEventListener listener) {
        String text = listener.toString();
        String identity = listener.getClass().getName() + '@'
                + Integer.toHexString(System.identityHashCode(listener));
        return text.equals(identity) ? listener.getClass().getName() : text;
    }
}
