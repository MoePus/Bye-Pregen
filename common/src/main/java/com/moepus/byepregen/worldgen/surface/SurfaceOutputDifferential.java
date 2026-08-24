package com.moepus.byepregen.worldgen.surface;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Compares generated and vanilla return values as an output-only diagnostic.
 *
 * <p>The rules share one mutable Context and the generated rule runs first. This catches output and
 * exception-shape mismatches, but it is not an oracle for evaluation order, laziness, or Context
 * mutation.</p>
 */
final class SurfaceOutputDifferential {
    private static final String ENABLED_PROPERTY = "byepregen.surfaceOutputDifferential";
    private static final String LEGACY_ENABLED_PROPERTY = "byepregen.surfaceDifferential";
    private static final Class<?> RULE_INTERFACE = resolveRuleInterface();

    private SurfaceOutputDifferential() {
    }

    static boolean enabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY)
                || Boolean.getBoolean(LEGACY_ENABLED_PROPERTY);
    }

    static Object wrap(Object generated, Object vanilla) {
        Objects.requireNonNull(generated, "generated");
        Objects.requireNonNull(vanilla, "vanilla");
        return Proxy.newProxyInstance(
                RULE_INTERFACE.getClassLoader(),
                new Class<?>[]{RULE_INTERFACE},
                new OutputComparator(generated, vanilla)
        );
    }

    private static Outcome evaluate(Object rule, Point point) {
        try {
            BlockState state = ((SurfaceBoundAccess.Rule) rule).tryApply(
                    point.x(), point.y(), point.z()
            );
            return new Outcome(state, null);
        } catch (Throwable throwable) {
            return new Outcome(null, throwable);
        }
    }

    private static AssertionError mismatch(
            Point point,
            Outcome vanilla,
            Outcome generated
    ) {
        AssertionError error = new AssertionError(
                "Surface output differential mismatch position=" + point
                        + " vanilla=" + vanilla.describe()
                        + " generated=" + generated.describe()
        );
        if (vanilla.failure() != null) error.addSuppressed(vanilla.failure());
        if (generated.failure() != null) error.addSuppressed(generated.failure());
        return error;
    }

    private static Object invokeObject(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            case "toString" -> "SurfaceOutputDifferential[" + System.identityHashCode(proxy) + "]";
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

    private record OutputComparator(Object generated, Object vanilla)
            implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObject(proxy, method, arguments);
            }
            if (method.getParameterCount() != 3 || method.getReturnType() != BlockState.class) {
                throw new IllegalStateException("Unexpected SurfaceRule method " + method);
            }
            Point point = new Point(
                    (int) arguments[0], (int) arguments[1], (int) arguments[2]
            );
            Outcome generatedOutcome = evaluate(this.generated, point);
            Outcome vanillaOutcome = evaluate(this.vanilla, point);
            SurfaceScalarMetrics.outputComparison();
            if (!generatedOutcome.matches(vanillaOutcome)) {
                SurfaceScalarMetrics.outputMismatch();
                throw mismatch(point, vanillaOutcome, generatedOutcome);
            }
            return generatedOutcome.unwrap();
        }
    }

    private record Point(int x, int y, int z) {
        @Override
        public String toString() {
            return "(" + this.x + "," + this.y + "," + this.z + ")";
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
