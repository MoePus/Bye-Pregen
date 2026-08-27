package com.moepus.byepregen.integration.forge;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventListener;

public final class ForgeEventBusCompat {
    private static final Field BUS_ID = busIdField();

    private ForgeEventBusCompat() {
    }

    public static List<IEventListener> listeners(Class<?> eventClass) {
        try {
            int busId = BUS_ID.getInt(MinecraftForge.EVENT_BUS);
            return Arrays.stream(EventListenerHelper.getListenerList(eventClass).getListeners(busId))
                    .filter(listener -> !(listener instanceof EventPriority))
                    .toList();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to inspect Forge event bus listeners", exception);
        }
    }

    private static Field busIdField() {
        if (!(MinecraftForge.EVENT_BUS instanceof EventBus)) {
            throw new ExceptionInInitializerError("MinecraftForge.EVENT_BUS is not an EventBus");
        }
        try {
            Field field = EventBus.class.getDeclaredField("busID");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
