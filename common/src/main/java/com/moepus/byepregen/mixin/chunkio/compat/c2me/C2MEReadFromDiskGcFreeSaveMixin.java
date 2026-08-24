package com.moepus.byepregen.mixin.chunkio.compat.c2me;

import com.ishland.c2me.base.mixin.access.IThreadedAnvilChunkStorage;
import com.ishland.c2me.rewrites.chunksystem.common.ChunkLoadingContext;
import com.mojang.logging.LogUtils;
import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.chunksave.serialize.GcFreeChunkSerializer;
import com.moepus.byepregen.chunksave.storage.RawChunkData;
import com.moepus.byepregen.chunksave.storage.RawChunkStorage;
import io.reactivex.rxjava3.core.Completable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@MixinGate(feature = MixinFeature.GC_FREE_RAW_CHUNK_IO, requiredMods = "c2me")
@Mixin(targets = "com.ishland.c2me.rewrites.chunksystem.common.statuses.ReadFromDisk", remap = false)
public abstract class C2MEReadFromDiskGcFreeSaveMixin {
    @Unique
    private static final Logger byepregen$LOGGER = LogUtils.getLogger();
    @Unique
    private static final AtomicBoolean byepregen$PATH_LOGGED = new AtomicBoolean();

    @Inject(
            method = "asyncSave(Lcom/ishland/c2me/rewrites/chunksystem/common/ChunkLoadingContext;"
                    + "Lnet/minecraft/world/level/chunk/ChunkAccess;)Lio/reactivex/rxjava3/core/Completable;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false
    )
    private void byepregen$saveRawChunk(
            ChunkLoadingContext context,
            ChunkAccess chunk,
            CallbackInfoReturnable<Completable> cir
    ) {
        if (!GcFreeChunkSerializer.shouldUseGcFree(chunk)) {
            return;
        }

        IThreadedAnvilChunkStorage storage = (IThreadedAnvilChunkStorage) context.tacs();
        ChunkPos pos = chunk.getPos();
        storage.getPointOfInterestStorage().flush(pos);
        if (!chunk.tryMarkSaved()) {
            cir.setReturnValue(Completable.complete());
            return;
        }

        if (byepregen$PATH_LOGGED.compareAndSet(false, true)) {
            byepregen$LOGGER.info("ByePregen GC-free raw chunk save intercepted C2ME ReadFromDisk");
        }
        ServerLevel level = storage.getWorld();
        RawChunkData data = GcFreeChunkSerializer.serializeRawData(level, chunk);
        cir.setReturnValue(Completable.defer(() -> Completable.fromCompletionStage(
                ((RawChunkStorage) context.tacs()).byepregen$writeRawChunkData(pos, data)
        )));
    }
}
