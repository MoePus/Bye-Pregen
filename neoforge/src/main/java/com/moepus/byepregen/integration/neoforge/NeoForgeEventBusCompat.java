package com.moepus.byepregen.integration.neoforge;

import net.neoforged.bus.EventBus;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.EventListener;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class NeoForgeEventBusCompat {
    private static final MethodHandle GET_LISTENER_LIST = getListenerListHandle();
    private static final MethodHandle GET_LISTENER_PRIORITIES = getListenerListFieldHandle("priorities");
    private static final MethodHandle GET_LISTENER_PARENT = getListenerListFieldHandle("parent");

    private NeoForgeEventBusCompat() {
    }

    public static ListenerList getListenerList(Class<?> eventClass) {
        if (!(NeoForge.EVENT_BUS instanceof EventBus eventBus)) {
            throw new IllegalStateException("NeoForge.EVENT_BUS is not an EventBus");
        }

        try {
            return (ListenerList) GET_LISTENER_LIST.invoke(eventBus, eventClass);
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to read NeoForge event bus listeners", throwable);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<? extends List<EventListener>> rawListenerPriorities(ListenerList list) {
        try {
            return (List<? extends List<EventListener>>) GET_LISTENER_PRIORITIES.invoke(list);
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to read NeoForge listener priorities", throwable);
        }
    }

    public static ListenerList listenerParent(ListenerList list) {
        try {
            return (ListenerList) GET_LISTENER_PARENT.invoke(list);
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to read NeoForge listener parent", throwable);
        }
    }

    private static MethodHandle getListenerListHandle() {
        try {
            Method method = EventBus.class.getDeclaredMethod("getListenerList", Class.class);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (ReflectiveOperationException | RuntimeException throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    private static MethodHandle getListenerListFieldHandle(String name) {
        try {
            Field field = ListenerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return MethodHandles.lookup().unreflectGetter(field);
        } catch (ReflectiveOperationException | RuntimeException throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }
}
