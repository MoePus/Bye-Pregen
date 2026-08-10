package com.moepus.byepregen.worldgen.surface;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

final class SurfaceDifferential {
    private static final String ENABLED_PROPERTY = "byepregen.surfaceDifferential";
    private static final Class<?> RULE_INTERFACE = resolveRuleInterface();

    private SurfaceDifferential() {
    }

    static boolean enabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    static Object wrap(
            SurfaceScalarTarget target,
            Object generated,
            Object vanilla
    ) {
        Objects.requireNonNull(generated, "generated");
        Objects.requireNonNull(vanilla, "vanilla");
        return Proxy.newProxyInstance(
                RULE_INTERFACE.getClassLoader(),
                new Class<?>[]{RULE_INTERFACE},
                (proxy, method, arguments) -> invoke(
                        proxy, method, arguments, target, generated, vanilla
                )
        );
    }

    private static Object invoke(
            Object proxy,
            Method method,
            Object[] arguments,
            SurfaceScalarTarget target,
            Object generated,
            Object vanilla
    ) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObject(proxy, method, arguments);
        }
        if (method.getParameterCount() != 3 || method.getReturnType() != BlockState.class) {
            throw new IllegalStateException("Unexpected SurfaceRule method " + method);
        }
        int x = (int) arguments[0];
        int y = (int) arguments[1];
        int z = (int) arguments[2];
        Outcome actual = evaluate(generated, x, y, z);
        Outcome expected = evaluate(vanilla, x, y, z);
        SurfaceScalarMetrics.differentialEvaluation(target);
        if (!actual.matches(expected)) {
            SurfaceScalarMetrics.differentialMismatch(target);
            throw mismatch(target, x, y, z, expected, actual);
        }
        return actual.unwrap();
    }

    private static Outcome evaluate(Object rule, int x, int y, int z) {
        try {
            BlockState state = ((SurfaceBoundAccess.Rule) rule).tryApply(x, y, z);
            return new Outcome(state, null);
        } catch (Throwable throwable) {
            return new Outcome(null, throwable);
        }
    }

    private static AssertionError mismatch(
            SurfaceScalarTarget target,
            int x,
            int y,
            int z,
            Outcome expected,
            Outcome actual
    ) {
        AssertionError error = new AssertionError(
                "Surface differential mismatch target=" + target
                        + " position=(" + x + "," + y + "," + z + ")"
                        + " expected=" + expected.describe()
                        + " actual=" + actual.describe()
        );
        if (expected.failure() != null) error.addSuppressed(expected.failure());
        if (actual.failure() != null) error.addSuppressed(actual.failure());
        return error;
    }

    private static Object invokeObject(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            case "toString" -> "SurfaceDifferential[" + System.identityHashCode(proxy) + "]";
            default -> throw new IllegalStateException("Unexpected Object method " + method);
        };
    }

    private static Class<?> resolveRuleInterface() {
        try {
            return Class.forName(
                    SurfaceRules.class.getName() + "$SurfaceRule",
                    false,
                    SurfaceRules.class.getClassLoader()
            );
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Outcome(BlockState state, Throwable failure) {
        private boolean matches(Outcome other) {
            if (this.failure == null || other.failure == null) {
                return this.failure == null && other.failure == null && this.state == other.state;
            }
            return this.failure.getClass() == other.failure.getClass()
                    && Objects.equals(this.failure.getMessage(), other.failure.getMessage());
        }

        private BlockState unwrap() throws Throwable {
            if (this.failure != null) throw this.failure;
            return this.state;
        }

        private String describe() {
            return this.failure == null
                    ? String.valueOf(this.state)
                    : this.failure.getClass().getName() + ": " + this.failure.getMessage();
        }
    }
}
