package com.moepus.byepregen.api.dfc;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Compatibility registry for custom density functions whose value never depends on block Y.
 */
public final class ColumnDensityFunctionRegistry {
    private static final Set<Class<? extends DensityFunction>> Y_INDEPENDENT_DELEGATES =
            ConcurrentHashMap.newKeySet();

    private ColumnDensityFunctionRegistry() {
    }

    /**
     * Registers an exact runtime class as Y-independent. Repeated registration is harmless.
     *
     * <p>Call this during mod construction, before worldgen density functions are compiled. The
     * registered implementation must return the same value for every Y at fixed X/Z, including
     * any density functions it invokes internally. This promise applies to both point and volume
     * sampling: DFC may collapse a batch to one Y sample and reuse its X/Z results within a request,
     * even when the function conservatively reports {@code ALL_AXES}. Sampling must not depend on
     * invocation count or the number of Y samples requested.</p>
     */
    public static void registerYIndependentDelegate(Class<? extends DensityFunction> type) {
        Y_INDEPENDENT_DELEGATES.add(Objects.requireNonNull(type, "type"));
    }

    public static boolean isYIndependentDelegate(DensityFunction function) {
        Objects.requireNonNull(function, "function");
        return isYIndependentDelegateType(function.getClass());
    }

    public static boolean isYIndependentDelegateType(Class<? extends DensityFunction> type) {
        return Y_INDEPENDENT_DELEGATES.contains(Objects.requireNonNull(type, "type"));
    }
}
