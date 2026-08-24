package com.moepus.byepregen.integration.neoforge;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.integration.architectury.ArchitecturyChunkSaveCompat;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import java.util.List;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.EventListener;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import org.slf4j.Logger;

final class NeoForgeChunkSaveHookGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARCHITECTURY_CHUNK_EVENT =
            "dev.architectury.event.events.common.ChunkEvent";
    private static final String ARCHITECTURY_BRIDGE =
            "dev.architectury.event.forge.EventHandlerImplCommon";
    private static final String ARCHITECTURY_CHUNK_SAVE_METHOD = "eventChunkDataEvent";
    private static final boolean CAN_USE_RAW_SAVE = computeCanUseRawSave();

    private NeoForgeChunkSaveHookGate() {
    }

    static boolean canUseRawSave() {
        return CAN_USE_RAW_SAVE;
    }

    private static boolean computeCanUseRawSave() {
        boolean hasArchitecturyListeners = hasArchitecturySaveDataListeners();
        boolean hasNeoForgeListeners = hasNeoForgeSaveListeners();
        boolean canUseRawSave = !hasArchitecturyListeners && !hasNeoForgeListeners;
        LOGGER.info("ByePregen GC-free raw chunk save gate: CAN_USE_RAW_SAVE={}", canUseRawSave);
        return canUseRawSave;
    }

    private static boolean hasArchitecturySaveDataListeners() {
        try {
            if (!ModEnvironment.isClassAvailable(ARCHITECTURY_CHUNK_EVENT)) {
                return false;
            }
            List<String> classNames = ArchitecturyChunkSaveCompat.saveDataListenerClassNames();
            for (String className : classNames) {
                LOGGER.warn("ByePregen raw chunk save disabled by Architectury ChunkEvent.SAVE_DATA listener: {}",
                        className);
            }
            return !classNames.isEmpty();
        } catch (LinkageError | RuntimeException throwable) {
            LOGGER.warn("ByePregen raw chunk save disabled: failed to inspect Architectury save listeners", throwable);
            return true;
        }
    }

    private static boolean hasNeoForgeSaveListeners() {
        try {
            return hasNeoForgeSaveListenersImpl();
        } catch (LinkageError | RuntimeException throwable) {
            LOGGER.warn("ByePregen raw chunk save disabled: failed to inspect NeoForge save listeners", throwable);
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
            hasListeners |= hasPriorityListeners(priorityListeners);
        }
        ListenerList parent = NeoForgeEventBusCompat.listenerParent(list);
        return parent == null ? hasListeners : hasListeners | hasNeoForgeSaveListeners(parent);
    }

    private static boolean hasPriorityListeners(List<EventListener> listeners) {
        boolean found = false;
        for (EventListener listener : listeners) {
            if (isArchitecturyBridge(listener)) {
                LOGGER.info("Ignored Architectury NeoForge chunk-save bridge listener: {}", listenerDescription(listener));
            } else {
                LOGGER.warn("ByePregen raw chunk save disabled by NeoForge listener: {}", listenerDescription(listener));
                found = true;
            }
        }
        return found;
    }

    private static boolean isArchitecturyBridge(EventListener listener) {
        String text = listener.toString();
        return text.contains(ARCHITECTURY_BRIDGE) && text.contains(ARCHITECTURY_CHUNK_SAVE_METHOD);
    }

    private static String listenerDescription(EventListener listener) {
        String text = listener.toString();
        String defaultText = listener.getClass().getName() + '@'
                + Integer.toHexString(System.identityHashCode(listener));
        return text.equals(defaultText) ? listener.getClass().getName() : text;
    }
}
