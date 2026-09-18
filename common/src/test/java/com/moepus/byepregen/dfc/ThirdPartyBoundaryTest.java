package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.MapCodec;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A third-party density function is a sampling boundary like vanilla's noise or spline: the
 * arithmetic underneath it must still be compiled, and a function that cannot produce a structural
 * copy of itself has to keep working through vanilla instead.
 */
final class ThirdPartyBoundaryTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void compilesArithmeticBeneathARewritableThirdPartyBoundary() {
        DensityFunction wrapped = new DoublingWrapper(arithmetic());

        long before = ColumnClassBuilder.generatedCount();
        DensitySampler compiled = DensitySamplerCompiler.compile(wrapped, DfcFixtures.CONTEXT);

        assertTrue(ColumnClassBuilder.generatedCount() > before,
                "the arithmetic region under the wrapper must be compiled");
        DfcFixtures.compare(wrapped.compileSampler(DfcFixtures.CONTEXT), compiled);
    }

    @Test
    void keepsAnUnrewritableThirdPartyFunctionOpaque() {
        DensityFunction wrapped = new UnrewritableWrapper(arithmetic());

        long before = ColumnClassBuilder.generatedCount();
        DensitySampler compiled = DensitySamplerCompiler.compile(wrapped, DfcFixtures.CONTEXT);

        assertEquals(before, ColumnClassBuilder.generatedCount(),
                "an unrewritable wrapper keeps the vanilla path");
        DfcFixtures.compare(wrapped.compileSampler(DfcFixtures.CONTEXT), compiled);
    }

    private static DensityFunction arithmetic() {
        DensityFunction gradient = DensityFunctions.yClampedGradient(-20, 31, -0.8F, 1.2F);
        return DensityFunctions.add(
                DensityFunctions.mul(gradient, DensityFunctions.constant(0.7F)),
                DensityFunctions.constant(0.03F));
    }

    /** Doubles whatever it wraps; stands in for a mod's own density function. */
    private record DoublingWrapper(DensityFunction input) implements DensityFunction {
        @Override
        public DensitySampler compileSampler(CompileContext context) {
            DensitySampler child = this.input.compileSampler(context);
            return new DensitySampler() {
                @Override
                public float sampleValue(SamplerContext context, int x, int y, int z) {
                    return child.sampleValue(context, x, y, z) * 2.0F;
                }

                @Override
                public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
                    child.sampleVolume(context, output, volume);
                    for (int i = 0; i < volume.size(); ++i) {
                        output.set(i, output.get(i) * 2.0F);
                    }
                }
            };
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return new DoublingWrapper(rule.rewrite(this.input));
        }

        @Override
        public Interval range() {
            return this.input.range();
        }

        @Override
        public int domainAxes() {
            return this.input.domainAxes();
        }

        @Override
        public MapCodec<? extends DensityFunction> codec() {
            return MapCodec.unit(this);
        }
    }

    /** Refuses to be copied, like a wrapper that owns mutable state; its sampler stays opaque. */
    private record UnrewritableWrapper(DensityFunction input) implements DensityFunction {
        @Override
        public DensitySampler compileSampler(CompileContext context) {
            DensitySampler child = this.input.compileSampler(context);
            return new DensitySampler() {
                @Override
                public float sampleValue(SamplerContext context, int x, int y, int z) {
                    return child.sampleValue(context, x, y, z);
                }

                @Override
                public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
                    child.sampleVolume(context, output, volume);
                }
            };
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            throw new UnsupportedOperationException("this wrapper cannot be copied");
        }

        @Override
        public Interval range() {
            return this.input.range();
        }

        @Override
        public int domainAxes() {
            return this.input.domainAxes();
        }

        @Override
        public MapCodec<? extends DensityFunction> codec() {
            return MapCodec.unit(this);
        }
    }
}
