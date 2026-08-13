package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.engine.YALightEngine;
import com.moepus.byepregen.yalight.access.YALightEngineHolder;
import com.moepus.byepregen.yalight.engine.YABlockLightEngine;
import com.moepus.byepregen.yalight.sky.YASkyLightEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineYAMixin implements YALightEngineHolder {
    @Shadow
    @Final
    private LightEngine<?, ?> blockEngine;

    @Shadow
    @Final
    private LightEngine<?, ?> skyEngine;

    @Unique
    private YALightEngine byepregen$yaLightEngine;

    @Override
    public YALightEngine byepregen$getYALightEngine() {
        return this.byepregen$yaLightEngine;
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/world/level/chunk/LightChunkGetter;)Lnet/minecraft/world/level/lighting/BlockLightEngine;"
            )
    )
    private BlockLightEngine byepregen$createYABlockLightEngine(LightChunkGetter chunkGetter) {
        return new YABlockLightEngine(chunkGetter);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/world/level/chunk/LightChunkGetter;)Lnet/minecraft/world/level/lighting/SkyLightEngine;"
            )
    )
    private SkyLightEngine byepregen$createYASkyLightEngine(LightChunkGetter chunkGetter) {
        return new YASkyLightEngine(chunkGetter);
    }

    @InjectLite(method = "<init>", at = @At("TAIL"))
    private void byepregen$initYALightEngine(
            LightChunkGetter chunkGetter,
            boolean blockLight,
            boolean skyLight
    ) {
        this.byepregen$yaLightEngine = new YALightEngine(
                chunkGetter,
                (YABlockLightEngine)this.blockEngine,
                (YASkyLightEngine)this.skyEngine
        );
    }

    /**
     * @author MoePus
     * @reason Route block checks to YA light engine.
     */
    @Overwrite
    public void checkBlock(BlockPos pos) {
        this.byepregen$yaLightEngine.checkBlock(pos);
    }

    /**
     * @author MoePus
     * @reason Route work state to YA light engine.
     */
    @Overwrite
    public boolean hasLightWork() {
        return this.byepregen$yaLightEngine.hasLightWork();
    }

    /**
     * @author MoePus
     * @reason Route light update execution to YA light engine.
     */
    @Overwrite
    public int runLightUpdates() {
        return this.byepregen$yaLightEngine.runLightUpdates();
    }

    /**
     * @author MoePus
     * @reason Route section status updates to YA light engine.
     */
    @Overwrite
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        this.byepregen$yaLightEngine.updateSectionStatus(pos, isEmpty);
    }

    /**
     * @author MoePus
     * @reason Route chunk light enable state to YA light engine.
     */
    @Overwrite
    public void setLightEnabled(ChunkPos pos, boolean lightEnabled) {
        this.byepregen$yaLightEngine.setLightEnabled(pos, lightEnabled);
    }

    /**
     * @author MoePus
     * @reason Route source propagation to YA light engine.
     */
    @Overwrite
    public void propagateLightSources(ChunkPos pos) {
        this.byepregen$yaLightEngine.propagateLightSources(pos);
    }

    /**
     * @author MoePus
     * @reason Return YA layer readers.
     */
    @Overwrite
    public LayerLightEventListener getLayerListener(LightLayer layer) {
        return this.byepregen$yaLightEngine.getLayerListener(layer);
    }

    /**
     * @author MoePus
     * @reason Return YA debug data.
     */
    @Overwrite
    public String getDebugData(LightLayer layer, SectionPos pos) {
        return this.byepregen$yaLightEngine.getDebugData(layer, pos);
    }

    /**
     * @author MoePus
     * @reason Return YA section type.
     */
    @Overwrite
    public LayerLightSectionStorage.SectionType getDebugSectionType(LightLayer layer, SectionPos pos) {
        return this.byepregen$yaLightEngine.getDebugSectionType(layer, pos);
    }

    /**
     * @author MoePus
     * @reason Queue section data into YA light engine.
     */
    @Overwrite
    public void queueSectionData(LightLayer layer, SectionPos pos, DataLayer dataLayer) {
        this.byepregen$yaLightEngine.queueSectionData(layer, pos, dataLayer);
    }

    /**
     * @author MoePus
     * @reason YA light engine owns retention policy.
     */
    @Overwrite
    public void retainData(ChunkPos pos, boolean retainData) {
        this.byepregen$yaLightEngine.retainData(pos, retainData);
    }

    /**
     * @author MoePus
     * @reason Route brightness queries to YA light engine.
     */
    @Overwrite
    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        return this.byepregen$yaLightEngine.getRawBrightness(pos, ambientDarkness);
    }

    /**
     * @author MoePus
     * @reason Route section light state to YA light engine.
     */
    @Overwrite
    public boolean lightOnInSection(SectionPos pos) {
        return this.byepregen$yaLightEngine.lightOnInSection(pos);
    }
}
