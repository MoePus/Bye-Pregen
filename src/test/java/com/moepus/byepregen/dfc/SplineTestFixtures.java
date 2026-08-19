package com.moepus.byepregen.dfc;

import java.lang.reflect.Field;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunctions;

/** Creates an identity-only spline coordinate without bootstrapping Minecraft registries. */
public final class SplineTestFixtures {
    private SplineTestFixtures() {
    }

    public static DensityFunctions.Spline.Coordinate coordinate() {
        try {
            SharedConstants.tryDetectVersion();
            Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrapped.setAccessible(true);
            bootstrapped.setBoolean(null, true);
            BuiltInRegistries.REGISTRY.size();
            return new DensityFunctions.Spline.Coordinate(
                    Holder.direct(DensityFunctions.constant(0.0D)));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot allocate identity-only spline coordinate", exception);
        }
    }
}
