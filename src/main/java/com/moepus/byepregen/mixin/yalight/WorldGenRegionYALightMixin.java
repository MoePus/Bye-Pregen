package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import com.moepus.byepregen.yalight.engine.YALightEngine;
import com.moepus.byepregen.yalight.access.YALightEngineHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionYALightMixin implements WorldGenLevel {
    @Shadow
    public abstract ChunkAccess getChunk(int chunkX, int chunkZ);

    @Shadow
    public abstract boolean hasChunk(int chunkX, int chunkZ);

    @Shadow
    public abstract LevelLightEngine getLightEngine();

    /**
     * @author Spottedleaf
     * @reason Avoid YA skylight fallback reading unlit worldgen chunks as fully sky-lit.
     */
    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        LevelLightEngine lightEngine = this.getLightEngine();
        if (!this.hasChunk(chunkX, chunkZ)) {
            return lightEngine.getLayerListener(layer).getLightValue(pos);
        }
        ChunkAccess chunk = this.getChunk(chunkX, chunkZ);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        YALightEngine engine = ((YALightEngineHolder)lightEngine).byepregen$getYALightEngine();
        return engine.getBrightness(layer, pos, (YAChunkLightAccess)chunk);
    }

    /**
     * @author Spottedleaf
     * @reason Avoid YA skylight fallback reading unlit worldgen chunks as fully sky-lit.
     */
    @Override
    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        LevelLightEngine lightEngine = this.getLightEngine();
        if (!this.hasChunk(chunkX, chunkZ)) {
            return lightEngine.getRawBrightness(pos, ambientDarkness);
        }
        ChunkAccess chunk = this.getChunk(chunkX, chunkZ);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        YALightEngine engine = ((YALightEngineHolder)lightEngine).byepregen$getYALightEngine();
        return engine.getRawBrightness(pos, ambientDarkness, (YAChunkLightAccess)chunk);
    }
}
