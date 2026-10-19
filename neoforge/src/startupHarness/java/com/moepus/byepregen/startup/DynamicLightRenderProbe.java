package com.moepus.byepregen.startup;

import com.moepus.byepregen.lighttest.DynamicLightTestBlock;
import com.moepus.byepregen.lighttest.DynamicLightTestView;
import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Calls the real client-side, Mixin-transformed light helpers, not a render notification sink. */
final class DynamicLightRenderProbe {
    private static final BlockPos FIRST = new BlockPos(-1, -17, -8);
    private static final BlockPos SECOND = new BlockPos(0, -16, -8);
    private static final int SKY_LIGHT = 5;
    private static final int FIRST_LIGHT = 14;
    private static final int SECOND_LIGHT = 7;
    private static final int EXTERNAL_BLOCK_LIGHT = 10;

    private DynamicLightRenderProbe() {
    }

    static void verify() {
        DynamicLightTestView view = new DynamicLightTestView(LevelHeightAccessor.create(-64, 384));
        BlockState state = DynamicLightTestBlock.state();
        view.put(FIRST, state, FIRST_LIGHT);
        view.put(SECOND, state, SECOND_LIGHT);
        require(state.getLightEmission() == 0, "fixture must have zero static emission");
        require(YABlockStateLightClass.fromRawId(Block.getId(state)) == YABlockStateLightClass.SLOW,
                "dynamic emitter was classified as non-emissive");
        assertLight(view, FIRST, state, FIRST_LIGHT);
        assertLight(view, SECOND, state, SECOND_LIGHT);
        for (int emission : new int[]{SECOND_LIGHT, 0, FIRST_LIGHT}) {
            view.setEmission(FIRST, emission);
            assertLight(view, FIRST, state, emission);
            assertLight(view, SECOND, state, SECOND_LIGHT);
        }
        System.out.println("YA dynamic light client probe passed: real LightCoordsUtil overloads, "
                + "position-dependent emission, unchanged state, no sky engine");
    }

    private static void assertLight(DynamicLightTestView view, BlockPos pos, BlockState state, int emission) {
        require(view.getBlockState(pos) == state, "fixture changed BlockState");
        require(LightCoordsUtil.getLightCoords(view, pos) == LightCoordsUtil.pack(emission, 0),
                "default brightness getter ignored dynamic emission at " + pos);
        int custom = LightCoordsUtil.getLightCoords(
                (world, sample) -> LightCoordsUtil.pack(0, SKY_LIGHT), view, state, pos);
        require(custom == LightCoordsUtil.pack(emission, SKY_LIGHT),
                "explicit brightness getter lost dynamic emission or external sky light at " + pos);
        int external = LightCoordsUtil.getLightCoords(
                (world, sample) -> LightCoordsUtil.pack(EXTERNAL_BLOCK_LIGHT, SKY_LIGHT), view, state, pos);
        require(external == LightCoordsUtil.pack(Math.max(emission, EXTERNAL_BLOCK_LIGHT), SKY_LIGHT),
                "dynamic emission replaced brighter external block light at " + pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("YA dynamic client light: " + message);
        }
    }
}
