package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.yalight.YALightEngine;
import com.moepus.byepregen.yalight.YALightEngineHolder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererYALightMixin {
    /**
     * @author
     * @reason
     */
    @Overwrite
    public static int getLightColor(BlockAndTintGetter level, BlockState state, BlockPos pos) {
        YALightEngine engine = ((YALightEngineHolder)level.getLightEngine()).byepregen$getYALightEngine();
        int light = engine.getLightColor(pos);
        if (state.isAir()) {
            return light;
        }
        if (state.emissiveRendering(level, pos)) {
            return 0xF000F0; // FULL_BRIGHT_LIGHT_COLOR
        }
        int block = light >> 4 & 15;
        int emission = state.getLightEmission(level, pos);
        if (block < emission) {
            return (light & ~0xF0) | emission << 4;
        }
        return light;
    }
}
