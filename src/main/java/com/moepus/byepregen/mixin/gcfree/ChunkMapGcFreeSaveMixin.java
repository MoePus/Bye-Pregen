package com.moepus.byepregen.mixin.gcfree;

import com.moepus.byepregen.gcfree.GcFreeChunkSerializer;
import com.moepus.byepregen.gcfree.RawChunkData;
import com.moepus.byepregen.gcfree.RawChunkStorage;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;

@Mixin(ChunkMap.class)
public abstract class ChunkMapGcFreeSaveMixin {
    @Unique
    private static final Logger BYEPREGEN_LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    @Final
    private PoiManager poiManager;

    @Shadow
    private boolean isExistingChunkFull(ChunkPos chunkPos) {
        throw new AssertionError();
    }

    @Shadow
    private byte markPosition(ChunkPos chunkPos, ChunkStatus.ChunkType type) {
        throw new AssertionError();
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void byepregen$saveRawWorldgenChunk(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (!GcFreeChunkSerializer.shouldUseGcFree(chunk)) {
            return;
        }
        cir.setReturnValue(this.byepregen$saveRaw(chunk));
    }

    @Unique
    private boolean byepregen$saveRaw(ChunkAccess chunk) {
        this.poiManager.flush(chunk.getPos());
        if (!chunk.isUnsaved()) {
            return false;
        }

        chunk.setUnsaved(false);
        ChunkPos pos = chunk.getPos();
        try {
            ChunkStatus status = chunk.getStatus();
            if (!this.byepregen$shouldSaveChunk(chunk, pos, status)) {
                return false;
            }

            this.level.getProfiler().incrementCounter("chunkSave");
            this.byepregen$writeRawChunk(chunk, pos);
            this.markPosition(pos, status.getChunkType());
            return true;
        } catch (Exception exception) {
            byepregen$reportSaveFailure(pos, exception);
            return false;
        }
    }

    @Unique
    private boolean byepregen$shouldSaveChunk(ChunkAccess chunk, ChunkPos pos, ChunkStatus status) {
        if (status.getChunkType() == ChunkStatus.ChunkType.LEVELCHUNK) {
            return true;
        }
        if (this.isExistingChunkFull(pos)) {
            return false;
        }
        return status != ChunkStatus.EMPTY || this.byepregen$hasValidStructureStart(chunk);
    }

    @Unique
    private boolean byepregen$hasValidStructureStart(ChunkAccess chunk) {
        for (StructureStart start : chunk.getAllStarts().values()) {
            if (start.isValid()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void byepregen$writeRawChunk(ChunkAccess chunk, ChunkPos pos) {
        RawChunkData rawData = GcFreeChunkSerializer.serializeRawData(this.level, chunk);
        ((RawChunkStorage) this).byepregen$writeRawChunkData(pos, rawData)
                .exceptionally(throwable -> {
                    byepregen$reportSaveFailure(pos, throwable);
                    return null;
                });
    }

    @Unique
    private static void byepregen$reportSaveFailure(ChunkPos pos, Throwable throwable) {
        BYEPREGEN_LOGGER.error("Failed to save chunk {},{}", pos.x, pos.z, throwable);
    }
}
