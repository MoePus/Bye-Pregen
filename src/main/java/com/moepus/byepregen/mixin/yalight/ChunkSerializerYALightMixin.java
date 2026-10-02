package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.moepus.byepregen.yalight.storage.YALightSaveSnapshot;
import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
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
import net.minecraft.world.level.chunk.status.ChunkStatus;
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

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerYALightMixin {
    @Inject(method = "write", at = @At("HEAD"))
    private static void byepregen$captureSaveLight(
            ServerLevel level,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompoundTag> cir,
            @Share("yaSaveLight") LocalRef<YALightSaveSnapshot> snapshot
    ) {
        snapshot.set(YALightSaveSnapshot.capture(chunk));
    }

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
            @Share("yaSaveLight") LocalRef<YALightSaveSnapshot> snapshot
    ) {
        return snapshot.get().vanillaLayer(LightLayer.BLOCK, sectionPos.y());
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
            @Share("yaSaveLight") LocalRef<YALightSaveSnapshot> snapshot
    ) {
        return snapshot.get().vanillaLayer(LightLayer.SKY, sectionPos.y());
    }

    @Redirect(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;isLightCorrect()Z"
            )
    )
    private static boolean byepregen$writeYALightCorrect(
            ChunkAccess chunk,
            @Share("yaSaveLight") LocalRef<YALightSaveSnapshot> snapshot
    ) {
        return snapshot.get().valid();
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
        // isLightOn is a trust flag, not a request to seed a relight with old bytes.
        // Fresh lighting only adds current sources; stale interior light has no removal seed.
        if (!chunk.isLightCorrect() || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT)) {
            chunk.setLightCorrect(false);
            return;
        }
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
