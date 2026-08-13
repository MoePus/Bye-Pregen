package com.moepus.byepregen.mixin.chunksave;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.chunksave.storage.RawChunkData;
import com.moepus.byepregen.chunksave.storage.RawChunkStorage;
import com.moepus.byepregen.chunksave.storage.RawIoWorker;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(feature = MixinFeature.GC_FREE_CHUNK_SAVE)
@Mixin(value = ChunkStorage.class, remap = false)
public abstract class ChunkStorageRawMixin implements RawChunkStorage {
    @Shadow
    @Final
    private IOWorker worker;

    @Override
    public CompletableFuture<Void> byepregen$writeRawChunkData(ChunkPos pos, RawChunkData data) {
        return ((RawIoWorker) this.worker).byepregen$storeRawChunkData(pos, data);
    }
}
