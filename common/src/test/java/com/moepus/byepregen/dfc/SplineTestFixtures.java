package com.moepus.byepregen.dfc;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction;

/** Creates an identity-only spline coordinate. */
public final class SplineTestFixtures {
    private SplineTestFixtures() {
    }

    public static SplineFunction.Coordinate coordinate() {
        // 26.3: 26.2 set Bootstrap.isBootstrapped through reflection to skip the work. That also turned a
        // later Bootstrap.bootStrap() into a no-op, so a registry-dependent test could pass or fail
        // depending on which suite ran first. Bootstrap for real instead.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        return new SplineFunction.Coordinate(DensityFunctions.constant(0.0F));
    }
}
