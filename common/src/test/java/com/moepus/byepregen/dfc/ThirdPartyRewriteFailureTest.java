package com.moepus.byepregen.dfc;

import com.mojang.serialization.MapCodec;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import com.moepus.byepregen.dfc.runtime.CompiledDensitySampler;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ThirdPartyRewriteFailureTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void aSuccessfulIdentityProbeDoesNotTurnALocalRewriteFailureIntoRootFailure() {
        for (Refusal refusal : Refusal.values()) {
            DensityFunction wrapper = new RejectingWrapper(DensityFunctions.yClampedGradient(-40, 40, -1, 1), refusal);
            DensityFunction graph = DensityFunctions.add(DensityFunctions.square(wrapper), DensityFunctions.constant(0.25F));
            long before = ColumnClassBuilder.generatedCount();
            DensitySampler compiled = DensitySamplerCompiler.compile(graph, DfcFixtures.CONTEXT);
            assertInstanceOf(CompiledDensitySampler.class, compiled);
            assertTrue(ColumnClassBuilder.generatedCount() > before);
            DfcFixtures.compare(graph.compileSampler(DfcFixtures.CONTEXT), compiled);
        }
    }

    private enum Refusal { THROW, RETURN_NULL, CHANGE_TYPE }

    private record RejectingWrapper(DensityFunction input, Refusal refusal) implements DensityFunction {
        @Override public DensitySampler compileSampler(CompileContext context) { return this.input.compileSampler(context); }
        @Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
            DensityFunction replacement = rule.rewrite(this.input);
            if (replacement == this.input) return this;
            return switch (this.refusal) {
                case THROW -> throw new IllegalArgumentException("This wrapper requires its original child");
                case RETURN_NULL -> null;
                case CHANGE_TYPE -> DensityFunctions.constant(17);
            };
        }
        @Override public Interval range() { return this.input.range(); }
        @Override public int domainAxes() { return this.input.domainAxes(); }
        @Override public MapCodec<? extends DensityFunction> codec() { return MapCodec.unit(this); }
    }
}
