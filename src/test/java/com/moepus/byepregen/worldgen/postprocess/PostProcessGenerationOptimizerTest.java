package com.moepus.byepregen.worldgen.postprocess;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

final class PostProcessGenerationOptimizerTest {
    private static final Class<?>[] PARAMETERS = {
            BlockState.class,
            Direction.class,
            BlockState.class,
            LevelAccessor.class,
            BlockPos.class,
            BlockPos.class
    };

    @Test
    void recognizesProductionMappedUpdateShapeByDescriptor() throws ReflectiveOperationException {
        Method mapped = ProductionMappedMethods.class.getDeclaredMethod("m_12345_", PARAMETERS);
        Method unrelated = ProductionMappedMethods.class.getDeclaredMethod("unrelated", PARAMETERS);

        assertTrue(PostProcessGenerationOptimizer.hasUpdateShapeSignature(mapped));
        assertFalse(PostProcessGenerationOptimizer.hasUpdateShapeSignature(unrelated));
    }

    private static final class ProductionMappedMethods {
        BlockState m_12345_(
                BlockState state,
                Direction direction,
                BlockState neighbor,
                LevelAccessor level,
                BlockPos pos,
                BlockPos neighborPos
        ) {
            return state;
        }

        void unrelated(
                BlockState state,
                Direction direction,
                BlockState neighbor,
                LevelAccessor level,
                BlockPos pos,
                BlockPos neighborPos
        ) {
        }
    }
}
