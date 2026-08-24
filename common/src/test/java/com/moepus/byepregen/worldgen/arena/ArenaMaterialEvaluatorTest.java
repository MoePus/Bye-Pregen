package com.moepus.byepregen.worldgen.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.junit.jupiter.api.Test;

final class ArenaMaterialEvaluatorTest {
    @Test
    void evaluatesPreparedBeardifierDensity() {
        double[] observedDensity = new double[1];
        Aquifer aquifer = new Aquifer() {
            @Override
            public BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
                observedDensity[0] = density;
                return null;
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
        NoiseChunk.BlockStateFiller aquiferRule = context -> null;
        DensityFunction beardifier = DensityFunctions
                .yClampedGradient(0, 4, 0.0D, 4.0D)
                .square();
        ArenaMaterialEvaluator evaluator = ArenaMaterialEvaluator.create(
                new MaterialRuleList(new NoiseChunk.BlockStateFiller[]{aquiferRule}),
                aquifer,
                aquiferRule,
                beardifier
        );
        evaluator.configureColumnRange(0, 4);
        evaluator.prepareColumn(3, 7);

        BlockState result = evaluator.calculateWithColumnDensity(
                new DensityFunction.SinglePointContext(3, 1, 7), 10.0D);

        assertNull(result);
        assertEquals(11.0D, observedDensity[0]);
    }

    @Test
    void usesBeardifierArrayEvaluationOncePerColumn() {
        NoiseChunk.BlockStateFiller aquiferRule = context -> null;
        TrackingBeardifier beardifier = new TrackingBeardifier();
        ArenaMaterialEvaluator evaluator = ArenaMaterialEvaluator.create(
                new MaterialRuleList(new NoiseChunk.BlockStateFiller[]{aquiferRule}),
                new NullAquifer(),
                aquiferRule,
                beardifier
        );
        evaluator.configureColumnRange(-2, 4);

        evaluator.prepareColumn(3, 7);
        evaluator.calculateWithColumnDensity(
                new DensityFunction.SinglePointContext(3, 0, 7), 10.0D);

        assertEquals(1, beardifier.fillCalls);
        assertEquals(0, beardifier.computeCalls);
    }

    private static final class NullAquifer implements Aquifer {
        @Override public BlockState computeSubstance(
                DensityFunction.FunctionContext context, double density
        ) { return null; }
        @Override public boolean shouldScheduleFluidUpdate() { return false; }
    }

    private static final class TrackingBeardifier implements DensityFunction {
        private int fillCalls;
        private int computeCalls;

        @Override
        public void fillArray(double[] values, ContextProvider provider) {
            ++this.fillCalls;
            for (int i = 0; i < values.length; ++i) values[i] = provider.forIndex(i).blockY();
        }

        @Override public double compute(FunctionContext context) {
            ++this.computeCalls;
            return context.blockY();
        }
        @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(this); }
        @Override public double minValue() { return -1000.0D; }
        @Override public double maxValue() { return 1000.0D; }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return null; }
    }
}
