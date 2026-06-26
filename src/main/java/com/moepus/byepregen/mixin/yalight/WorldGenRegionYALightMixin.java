package com.moepus.byepregen.mixin.yalight;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionYALightMixin implements WorldGenLevel {
    @Shadow
    public abstract ChunkAccess getChunk(int chunkX, int chunkZ);

    @Shadow
    public abstract LevelLightEngine getLightEngine();

    /**
     * @author Spottedleaf
     * @reason Avoid YA skylight fallback reading unlit worldgen chunks as fully sky-lit.
     */
    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        ChunkAccess chunk = this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        return this.getLightEngine().getLayerListener(layer).getLightValue(pos);
    }

    /**
     * @author Spottedleaf
     * @reason Avoid YA skylight fallback reading unlit worldgen chunks as fully sky-lit.
     */
    @Override
    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        ChunkAccess chunk = this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        return this.getLightEngine().getRawBrightness(pos, ambientDarkness);
    }
}
