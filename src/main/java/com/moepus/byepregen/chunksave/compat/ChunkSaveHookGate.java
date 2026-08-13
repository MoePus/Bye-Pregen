package com.moepus.byepregen.chunksave.compat;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.integration.architectury.ArchitecturyChunkSaveCompat;
import com.moepus.byepregen.integration.neoforge.NeoForgeEventBusCompat;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import java.util.List;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.EventListener;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import org.slf4j.Logger;

public final class ChunkSaveHookGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARCHITECTURY_CHUNK_EVENT =
            "dev.architectury.event.events.common.ChunkEvent";
    private static final String ARCHITECTURY_BRIDGE =
            "dev.architectury.event.forge.EventHandlerImplCommon";
    private static final String ARCHITECTURY_CHUNK_SAVE_METHOD =
            "eventChunkDataEvent";
    public static final boolean CAN_USE_RAW_SAVE = computeCanUseRawSave();

    private ChunkSaveHookGate() {
    }

    private static boolean computeCanUseRawSave() {
        boolean hasArchitecturyListeners = hasArchitecturySaveDataListeners();
        boolean hasNeoForgeListeners = hasNeoForgeSaveListeners();
        boolean canUseRawSave = !hasArchitecturyListeners && !hasNeoForgeListeners;
        LOGGER.info("ByePregen GC-free raw chunk save gate: CAN_USE_RAW_SAVE={}", canUseRawSave);
        return canUseRawSave;
    }

    private static boolean hasArchitecturySaveDataListeners() {
        if (!ModEnvironment.isClassAvailable(ARCHITECTURY_CHUNK_EVENT)) {
            return false;
        }
        try {
            List<String> classNames = ArchitecturyChunkSaveCompat.saveDataListenerClassNames();
            for (String className : classNames) {
                LOGGER.warn("ByePregen raw chunk save disabled by Architectury ChunkEvent.SAVE_DATA listener: {}",
                        className);
            }
            return !classNames.isEmpty();
        } catch (LinkageError | RuntimeException throwable) {
            LOGGER.warn("ByePregen raw chunk save disabled: failed to inspect Architectury ChunkEvent.SAVE_DATA listeners",
                    throwable);
            return true;
        }
    }

    private static boolean hasNeoForgeSaveListeners() {
        try {
            return hasNeoForgeSaveListenersImpl();
        } catch (LinkageError | RuntimeException throwable) {
            LOGGER.warn("ByePregen raw chunk save disabled: failed to inspect NeoForge ChunkDataEvent.Save listeners",
                    throwable);
            return true;
        }
    }

    private static boolean hasNeoForgeSaveListenersImpl() {
        ListenerList list = NeoForgeEventBusCompat.getListenerList(ChunkDataEvent.Save.class);
        return hasNeoForgeSaveListeners(list);
    }

    private static boolean hasNeoForgeSaveListeners(ListenerList list) {
        boolean hasListeners = false;
        for (List<EventListener> priorityListeners : NeoForgeEventBusCompat.rawListenerPriorities(list)) {
            for (EventListener listener : priorityListeners) {
                if (isArchitecturyBridge(listener)) {
                    LOGGER.info("ByePregen raw chunk save gate ignored Architectury NeoForge bridge listener: {}",
                            listenerDescription(listener));
                } else {
                    LOGGER.warn("ByePregen raw chunk save disabled by NeoForge ChunkDataEvent.Save listener: {}",
                            listenerDescription(listener));
                    hasListeners = true;
                }
            }
        }

        ListenerList parent = NeoForgeEventBusCompat.listenerParent(list);
        if (parent != null) {
            hasListeners |= hasNeoForgeSaveListeners(parent);
        }
        return hasListeners;
    }

    private static boolean isArchitecturyBridge(EventListener listener) {
        String text = listener.toString();
        return text.contains(ARCHITECTURY_BRIDGE) && text.contains(ARCHITECTURY_CHUNK_SAVE_METHOD);
    }

    private static String listenerDescription(EventListener listener) {
        String text = listener.toString();
        return text.equals(listener.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(listener)))
                ? listener.getClass().getName()
                : text;
    }
}
