package com.moepus.byepregen.gcfree;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.slf4j.Logger;

public final class ChunkSaveHookGate {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final boolean CAN_USE_RAW_SAVE = computeCanUseRawSave();

    private ChunkSaveHookGate() {
    }

    private static boolean computeCanUseRawSave() {
        try {
            int forgeSaveListeners = forgeSaveListenerCount();
            boolean canUseRawSave = forgeSaveListeners == 0;
            if (!canUseRawSave) {
                LOGGER.warn("ByePregen raw chunk save disabled by {} Forge ChunkDataEvent.Save listener(s)",
                        forgeSaveListeners);
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

    private static int forgeSaveListenerCount() throws ReflectiveOperationException {
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
        return nonPriorityListenerCount(listeners);
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

    private static int nonPriorityListenerCount(Object[] listeners) {
        int count = 0;
        for (Object listener : listeners) {
            if (!"net.minecraftforge.eventbus.api.EventPriority".equals(listener.getClass().getName())) {
                ++count;
            }
        }
        return count;
    }
}
