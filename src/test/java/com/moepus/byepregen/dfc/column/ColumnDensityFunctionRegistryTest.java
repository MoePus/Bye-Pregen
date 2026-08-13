package com.moepus.byepregen.dfc.column;

import com.moepus.byepregen.api.dfc.ColumnDensityFunctionRegistry;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.Test;

public final class ColumnDensityFunctionRegistryTest {
    private ColumnDensityFunctionRegistryTest() {
    }

    @Test
    void registersExactDelegateTypesIdempotently() {
        check(!ColumnDensityFunctionRegistry.isYIndependentDelegateType(UnknownDensityFunction.class),
                "unknown type must remain unregistered");

        ColumnDensityFunctionRegistry.registerYIndependentDelegate(IndependentDensityFunction.class);
        ColumnDensityFunctionRegistry.registerYIndependentDelegate(IndependentDensityFunction.class);
        check(ColumnDensityFunctionRegistry.isYIndependentDelegateType(IndependentDensityFunction.class),
                "registered type must be recognized after repeated registration");
        check(!ColumnDensityFunctionRegistry.isYIndependentDelegateType(UnknownDensityFunction.class),
                "registration must use exact runtime classes");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private abstract static class TestDensityFunction implements DensityFunction {
    }

    private abstract static class UnknownDensityFunction extends TestDensityFunction {
    }

    private abstract static class IndependentDensityFunction extends TestDensityFunction {
    }
}
