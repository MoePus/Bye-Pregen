package com.moepus.byepregen.dfc;

import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import com.moepus.byepregen.dfc.runtime.CompiledDensitySampler;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercising generated spline branches, outside extensions and nested coordinates. */
final class SplineCodegenTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void generatedSplinesMatchNativeAcrossRandomAndNestedSegments() {
        Random random = new Random(0x5EEDC0DEL);
        var outer = new SplineFunction.Coordinate(DensityFunctions.yClampedGradient(-16, 32, -4, 4));
        var inner = new SplineFunction.Coordinate(DensityFunctions.yClampedGradient(-8, 40, 5, -3));
        for (int round = 0; round < 64; ++round) {
            CubicSpline<SplineFunction.Coordinate> spline = randomSpline(random, outer);
            if ((round & 1) == 0) spline = CubicSpline.builder(outer)
                    .addPoint(-2, spline).addPoint(0, randomSpline(random, inner))
                    .addPoint(2, randomSpline(random, inner)).build();
            DensityFunction graph = new SplineFunction(spline).mul(0.7F).add(0.1F);
            var compiled = DensitySamplerCompiler.compile(graph, DfcFixtures.CONTEXT);
            assertInstanceOf(CompiledDensitySampler.class, compiled);
            DfcFixtures.compare(graph.compileSampler(DfcFixtures.CONTEXT), compiled);
        }
    }

    @Test void constantSplineAndMoreThanFiveCoordinatesCompile() {
        CubicSpline<SplineFunction.Coordinate> spline = CubicSpline.constant(0.375F);
        for (int i = 0; i < 7; ++i) {
            var coordinate = new SplineFunction.Coordinate(DensityFunctions.yClampedGradient(-20 + i, 31 + i, -1, 1));
            spline = CubicSpline.builder(coordinate).addPoint(-1, spline).addPoint(1, 0.125F * i).build();
        }
        for (var candidate : java.util.List.of(spline, CubicSpline.<SplineFunction.Coordinate>constant(0.375F))) {
            DensityFunction graph = new SplineFunction(candidate);
            DfcFixtures.compare(graph.compileSampler(DfcFixtures.CONTEXT), DensitySamplerCompiler.compile(graph, DfcFixtures.CONTEXT));
        }
    }

    @Test void splineLocationAffineRewriteRetainsNegativeScaleAndNestedValues() {
        DensityFunction y = DensityFunctions.yClampedGradient(-20, 30, -2, 2);
        for (float scale : new float[]{0.5F, -2.0F}) {
            var coordinate = new SplineFunction.Coordinate(y.mul(scale).add(0.25F));
            var spline = CubicSpline.builder(coordinate).addPoint(-2, -1, 0.5F)
                    .addPoint(0, 0.25F, -0.7F).addPoint(2, 0.75F, 0.25F).build();
            DensityFunction graph = new SplineFunction(spline).mul(-0.75F).add(0.125F);
            DfcFixtures.compare(graph.compileSampler(DfcFixtures.CONTEXT),
                    DensitySamplerCompiler.compile(graph, DfcFixtures.CONTEXT));
        }
    }

    private static CubicSpline<SplineFunction.Coordinate> randomSpline(Random random, SplineFunction.Coordinate coordinate) {
        var builder = CubicSpline.builder(coordinate);
        float location = -3 + random.nextFloat();
        for (int i = 0, count = 2 + random.nextInt(6); i < count; ++i) {
            builder.addPoint(location, random.nextFloat() * 4 - 2, random.nextFloat() * 2 - 1);
            location += 0.25F + random.nextFloat() * 2;
        }
        return builder.build();
    }
}
