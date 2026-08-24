package com.moepus.byepregen.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Consumer;

final class ChunkyRuntime {
    private static final String PROVIDER_CLASS = "org.popcraft.chunky.ChunkyProvider";

    private final Object api;

    private ChunkyRuntime(Object api) {
        this.api = api;
    }

    static ChunkyRuntime load() {
        try {
            Class<?> provider = Class.forName(PROVIDER_CLASS);
            Object chunky = provider.getMethod("get").invoke(null);
            return chunky == null ? null : new ChunkyRuntime(invoke(chunky, "getApi"));
        } catch (ReflectiveOperationException exception) {
            throw invocationFailure("load provider", exception);
        }
    }

    void onGenerationProgress(Consumer<Event> listener) {
        this.register("onGenerationProgress", listener);
    }

    void onGenerationComplete(Consumer<Event> listener) {
        this.register("onGenerationComplete", listener);
    }

    boolean startTask(Task task) {
        return (boolean) invoke(
                this.api,
                "startTask",
                task.world(), task.shape().name(), task.area().center().x(), task.area().center().z(),
                task.area().radius(), task.area().radius(), task.shape().pattern()
        );
    }

    private void register(String method, Consumer<Event> listener) {
        Consumer<Object> callback = event -> listener.accept(new Event(event));
        invoke(this.api, method, callback);
    }

    private static Object invoke(Object target, String name, Object... arguments) {
        Method method = Arrays.stream(target.getClass().getMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .filter(candidate -> !Modifier.isStatic(candidate.getModifiers()))
                .filter(candidate -> candidate.getParameterCount() == arguments.length)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Chunky API method is unavailable: " + name + "/" + arguments.length));
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw invocationFailure(name, exception);
        }
    }

    private static RuntimeException invocationFailure(String action, ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation
                ? invocation.getCause()
                : exception;
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Failed to invoke Chunky API: " + action, cause);
    }

    record Task(String world, Shape shape, Area area) {
    }

    record Shape(String name, String pattern) {
    }

    record Area(Center center, double radius) {
    }

    record Center(double x, double z) {
    }

    record Event(Object delegate) {
        Object value(String method) {
            return invoke(this.delegate, method);
        }

        String world() {
            return String.valueOf(this.value("world"));
        }
    }
}
