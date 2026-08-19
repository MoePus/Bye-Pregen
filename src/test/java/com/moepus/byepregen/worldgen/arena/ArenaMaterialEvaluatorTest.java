package com.moepus.byepregen.worldgen.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.junit.jupiter.api.Test;

final class ArenaMaterialEvaluatorTest {
    @Test
    void evaluatesBeardifierAtEachBlockPoint() {
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
                new MaterialRuleList(List.of(aquiferRule)),
                aquifer,
                aquiferRule,
                beardifier
        );

        BlockState result = evaluator.calculateWithColumnDensity(
                new DensityFunction.SinglePointContext(3, 1, 7), 10.0D);

        assertNull(result);
        assertEquals(11.0D, observedDensity[0]);
    }
}
