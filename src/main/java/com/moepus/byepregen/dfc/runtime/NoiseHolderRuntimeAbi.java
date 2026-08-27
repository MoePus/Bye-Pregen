package com.moepus.byepregen.dfc.runtime;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class NoiseHolderRuntimeAbi {
    private static final Class<?>[] VALUE_PARAMETERS = {
            double.class, double.class, double.class
    };
    private static final String VALUE_METHOD_NAME = resolveValueMethodName(
            DensityFunction.NoiseHolder.class
    );

    private NoiseHolderRuntimeAbi() {
    }

    public static String valueMethodName() {
        return VALUE_METHOD_NAME;
    }

    static String resolveValueMethodName(Class<?> owner) {
        String result = null;
        try {
            for (Method method : owner.getDeclaredMethods()) {
                if (!isValueMethod(method)) continue;
                if (result != null) {
                    throw new IllegalStateException("Ambiguous NoiseHolder value method on " + owner.getName());
                }
                result = method.getName();
            }
        } catch (LinkageError | SecurityException exception) {
            throw new IllegalStateException("Cannot inspect NoiseHolder runtime ABI", exception);
        }
        if (result == null) {
            throw new IllegalStateException("Cannot find NoiseHolder value method on " + owner.getName());
        }
        return result;
    }

    private static boolean isValueMethod(Method method) {
        return !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() == double.class
                && Arrays.equals(method.getParameterTypes(), VALUE_PARAMETERS);
    }
}
