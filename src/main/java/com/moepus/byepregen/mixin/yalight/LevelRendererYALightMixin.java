package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.yalight.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererYALightMixin {
    /**
     * @author
     * @reason
     */
    @Overwrite
    public static int getLightColor(BlockAndTintGetter blockGetter, BlockPos pos) {
        if (blockGetter instanceof Level level) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (!level.isOutsideBuildHeight(pos)) {
                LevelChunk levelchunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
                int rawId = YAChunkRunCache.rawIdAt(levelchunk, x, y, z);
                int lightClass = YABlockStateLightClass.fromRawId(rawId);
                if (lightClass == YABlockStateLightClass.SLOW && rawId >= 0) {
                    BlockState state = Block.stateById(rawId);
                    return getLightColor(level, state, pos);
                }
            }
            YALightEngine engine = ((YALightEngineHolder) blockGetter.getLightEngine()).byepregen$getYALightEngine();
            return engine.getLightColor(pos);
        }
        return getLightColor(blockGetter, blockGetter.getBlockState(pos), pos);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static int getLightColor(BlockAndTintGetter blockGetter, BlockState state, BlockPos pos) {
        int light;
        if (blockGetter instanceof Level level) {
            YALightEngine engine = ((YALightEngineHolder) level.getLightEngine()).byepregen$getYALightEngine();
            light = engine.getLightColor(pos);
        } else {
            int sky = blockGetter.getBrightness(LightLayer.SKY, pos);
            int block = blockGetter.getBrightness(LightLayer.BLOCK, pos);
            light = sky << 20 | block << 4;
        }
        if (state.isAir()) {
            return light;
        }
        if (state.emissiveRendering(blockGetter, pos)) {
            return 0xF000F0; // FULL_BRIGHT_LIGHT_COLOR
        }
        int block = light >> 4 & 15;
        int emission = state.getLightEmission(blockGetter, pos);
        if (block < emission) {
            return (light & ~0xF0) | emission << 4;
        }
        return light;
    }
}
