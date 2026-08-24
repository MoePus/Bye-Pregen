package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(SerializableChunkData.class)
public abstract class ChunkSerializerYALightMixin {
    @Shadow @Final private List<SerializableChunkData.SectionData> sectionData;

    @Redirect(
            method = "copyOf",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/LayerLightEventListener;getDataLayerData(Lnet/minecraft/core/SectionPos;)Lnet/minecraft/world/level/chunk/DataLayer;",
                    ordinal = 0
            )
    )
    private static DataLayer byepregen$writeYABlockLight(
            LayerLightEventListener listener,
            SectionPos sectionPos,
            @Local(argsOnly = true) ChunkAccess chunk
    ) {
        return byepregen$visibleLayer(chunk, LightLayer.BLOCK, sectionPos.y());
    }

    @Redirect(
            method = "copyOf",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/LayerLightEventListener;getDataLayerData(Lnet/minecraft/core/SectionPos;)Lnet/minecraft/world/level/chunk/DataLayer;",
                    ordinal = 1
            )
    )
    private static DataLayer byepregen$writeYASkyLight(
            LayerLightEventListener listener,
            SectionPos sectionPos,
            @Local(argsOnly = true) ChunkAccess chunk
    ) {
        return byepregen$visibleLayer(chunk, LightLayer.SKY, sectionPos.y());
    }

    @Unique
    @Nullable
    private static DataLayer byepregen$visibleLayer(ChunkAccess chunk, LightLayer layer, int sectionY) {
        YAChunkLightData data = ((YAChunkLightAccess)chunk).byepregen$yaLightData(layer, false);
        YANibbleArray nibble = data == null ? null : data.getVisibleSection(sectionY);
        return nibble == null ? null : nibble.toVanilla();
    }

    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;queueSectionData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;)V"
            )
    )
    private void byepregen$skipVanillaLightQueue(
            LevelLightEngine lightEngine, LightLayer layer, SectionPos sectionPos, DataLayer data) {
    }

    @Inject(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setLightCorrect(Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void byepregen$installYALightData(
            ServerLevel level,
            PoiManager poiManager,
            RegionStorageInfo regionStorageInfo,
            ChunkPos chunkPos,
            CallbackInfoReturnable<ProtoChunk> cir,
            @Local(ordinal = 0) ChunkAccess chunk
    ) {
        byepregen$readYALightData(level, this.sectionData, chunk);
    }

    @Unique
    private static void byepregen$readYALightData(
            ServerLevel level, List<SerializableChunkData.SectionData> sections, ChunkAccess chunk) {
        YAChunkLightAccess access = (YAChunkLightAccess)chunk;

        YAChunkLightData blockData = null;
        YAChunkLightData skyData = null;
        boolean hasSkyLight = level.dimensionType().hasSkyLight();
        for (SerializableChunkData.SectionData section : sections) {
            if (section.blockLight() != null) {
                if (blockData == null) {
                    blockData = access.byepregen$yaLightData(LightLayer.BLOCK);
                }
                blockData.loadInitialSection(
                        section.y(), YANibbleArray.fromOwnedBytes(section.blockLight().getData()));
            }
            if (hasSkyLight && section.skyLight() != null) {
                if (skyData == null) {
                    skyData = access.byepregen$yaLightData(LightLayer.SKY);
                }
                skyData.loadInitialSection(
                        section.y(), YANibbleArray.fromOwnedBytes(section.skyLight().getData()));
            }
        }
        if (blockData != null) {
            blockData.finishInitialLoad();
        }
        if (skyData != null) {
            skyData.finishInitialLoad();
        }
    }
}
