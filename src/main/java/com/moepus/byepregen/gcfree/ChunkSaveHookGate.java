package com.moepus.byepregen.gcfree;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

public final class ChunkSaveHookGate {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final boolean CAN_USE_RAW_SAVE = computeCanUseRawSave();

    private ChunkSaveHookGate() {
    }

    private static boolean computeCanUseRawSave() {
        try {
            List<String> forgeSaveListeners = forgeSaveListeners();
            boolean canUseRawSave = forgeSaveListeners.isEmpty();
            if (!canUseRawSave) {
                LOGGER.warn("ByePregen raw chunk save disabled by {} Forge ChunkDataEvent.Save listener(s)",
                        forgeSaveListeners.size());
                for (int index = 0; index < forgeSaveListeners.size(); ++index) {
                    LOGGER.warn("ByePregen raw chunk save listener[{}]: {}", index, forgeSaveListeners.get(index));
                }
            }
            LOGGER.info("ByePregen GC-free raw chunk save gate: CAN_USE_RAW_SAVE={}", canUseRawSave);
            return canUseRawSave;
        } catch (ClassNotFoundException ignored) {
            LOGGER.info("ByePregen GC-free raw chunk save gate: CAN_USE_RAW_SAVE=true (Forge event classes not found)");
            return true;
        } catch (ReflectiveOperationException | RuntimeException throwable) {
            LOGGER.warn("ByePregen raw chunk save disabled: failed to inspect Forge ChunkDataEvent.Save listeners",
                    throwable);
            return false;
        }
    }

    private static List<String> forgeSaveListeners() throws ReflectiveOperationException {
        ClassLoader loader = ChunkSaveHookGate.class.getClassLoader();
        Class<?> saveEventClass = Class.forName("net.minecraftforge.event.level.ChunkDataEvent$Save", false, loader);
        Class<?> listenerHelperClass =
                Class.forName("net.minecraftforge.eventbus.api.EventListenerHelper", false, loader);
        Method getListenerList = listenerHelperClass.getDeclaredMethod("getListenerListInternal", Class.class, boolean.class);
        getListenerList.setAccessible(true);
        Object listenerList = getListenerList.invoke(null, saveEventClass, true);

        Class<?> minecraftForgeClass = Class.forName("net.minecraftforge.common.MinecraftForge", false, loader);
        Object eventBus = minecraftForgeClass.getField("EVENT_BUS").get(null);
        int busId = eventBusId(eventBus);

        Method getListeners = listenerList.getClass().getMethod("getListeners", int.class);
        Object[] listeners = (Object[]) getListeners.invoke(listenerList, busId);
        return nonPriorityListenerDescriptions(listeners);
    }

    private static int eventBusId(Object eventBus) throws ReflectiveOperationException {
        Class<?> type = eventBus.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("busID");
                field.setAccessible(true);
                return field.getInt(eventBus);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException("busID");
    }

    private static List<String> nonPriorityListenerDescriptions(Object[] listeners) {
        List<String> descriptions = new ArrayList<>();
        for (Object listener : listeners) {
            if (listener != null
                    && !"net.minecraftforge.eventbus.api.EventPriority".equals(listener.getClass().getName())) {
                descriptions.add(listenerDescription(listener));
            }
        }
        return descriptions;
    }

    private static String listenerDescription(Object listener) {
        try {
            return listener + " [" + listener.getClass().getName() + "]";
        } catch (RuntimeException ignored) {
            return listener.getClass().getName();
        }
    }
}
