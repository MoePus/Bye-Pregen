package com.moepus.byepregen.mixin.yalight;

import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.yalight.YAChunkLightAccess;
import com.moepus.byepregen.yalight.YAChunkLightData;
import com.moepus.byepregen.yalight.YANibbleArray;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerYALightMixin {
    @Redirect(
            method = "write",
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
            method = "write",
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
                    target = "Lnet/minecraft/nbt/CompoundTag;contains(Ljava/lang/String;I)Z"
            ),
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "stringValue=BlockLight"),
                    to = @At(value = "CONSTANT", args = "stringValue=SkyLight")
            )
    )
    private static boolean byepregen$skipYABlockLightTag(
            CompoundTag sectionTag,
            String key,
            int type,
            ServerLevel level,
            PoiManager poiManager,
            RegionStorageInfo regionStorageInfo,
            ChunkPos chunkPos,
            CompoundTag chunkTag
    ) {
        return false;
    }

    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/CompoundTag;contains(Ljava/lang/String;I)Z"
            ),
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "stringValue=SkyLight"),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;queueSectionData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;)V"
                    )
            )
    )
    private static boolean byepregen$skipYASkyLightTag(
            CompoundTag sectionTag,
            String key,
            int type,
            ServerLevel level,
            PoiManager poiManager,
            RegionStorageInfo regionStorageInfo,
            ChunkPos chunkPos,
            CompoundTag chunkTag
    ) {
        return false;
    }

    @Inject(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setLightCorrect(Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void byepregen$installYALightData(
            ServerLevel level,
            PoiManager poiManager,
            RegionStorageInfo regionStorageInfo,
            ChunkPos chunkPos,
            CompoundTag chunkTag,
            CallbackInfoReturnable<ProtoChunk> cir,
            @Local(ordinal = 0) ListTag sections,
            @Local(ordinal = 0) ChunkAccess chunk
    ) {
        byepregen$readYALightTags(level, sections, chunk);
    }

    @Unique
    private static void byepregen$readYALightTags(ServerLevel level, ListTag sections, ChunkAccess chunk) {
        YAChunkLightAccess access = (YAChunkLightAccess)chunk;

        YAChunkLightData blockData = null;
        YAChunkLightData skyData = null;
        boolean hasSkyLight = level.dimensionType().hasSkyLight();
        for (int i = 0; i < sections.size(); ++i) {
            CompoundTag sectionTag = sections.getCompound(i);
            int sectionY = sectionTag.getByte("Y");
            if (sectionTag.contains("BlockLight", 7)) {
                if (blockData == null) {
                    blockData = access.byepregen$yaLightData(LightLayer.BLOCK);
                }
                blockData.loadInitialSection(sectionY, YANibbleArray.fromOwnedBytes(sectionTag.getByteArray("BlockLight")));
            }
            if (hasSkyLight && sectionTag.contains("SkyLight", 7)) {
                if (skyData == null) {
                    skyData = access.byepregen$yaLightData(LightLayer.SKY);
                }
                skyData.loadInitialSection(sectionY, YANibbleArray.fromOwnedBytes(sectionTag.getByteArray("SkyLight")));
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
