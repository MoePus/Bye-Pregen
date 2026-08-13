package com.moepus.byepregen.mixin.chunksave;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.mojang.datafixers.util.Either;
import com.moepus.byepregen.integration.c2me.C2MEDirectStorageCompat;
import com.moepus.byepregen.chunksave.storage.RawChunkData;
import com.moepus.byepregen.chunksave.storage.RawIoWorker;
import com.moepus.byepregen.mixin.accessor.chunksave.IOWorkerPendingStoreAccessor;
import com.moepus.byepregen.mixin.accessor.chunksave.RegionFileStorageAccessor;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;

@MixinGate(feature = MixinFeature.GC_FREE_CHUNK_SAVE)
@Mixin(value = IOWorker.class, remap = false)
public abstract class IOWorkerRawMixin implements RawIoWorker {
    @Unique
    private static final String byepregen$C2ME_STORAGE_INTERFACE =
            "com.ishland.c2me.rewrites.chunkio.common.C2MEStorageVanillaInterface";

    @Shadow
    @Final
    private RegionFileStorage storage;

    @Shadow
    @Final
    private Map<ChunkPos, Object> pendingWrites;

    @Invoker("submitTask")
    protected abstract <T> CompletableFuture<T> byepregen$submitTask(Supplier<Either<T, Exception>> task);

    @Override
    public CompletableFuture<Void> byepregen$storeRawChunkData(ChunkPos pos, RawChunkData data) {
        if (this.byepregen$isC2MEStorageInterface()) {
            return C2MEDirectStorageCompat.setRawChunkData(this, pos, data);
        }
        return this.byepregen$submitTask(() -> this.byepregen$writeRawNow(pos, data));
    }

    @Unique
    private boolean byepregen$isC2MEStorageInterface() {
        return byepregen$C2ME_STORAGE_INTERFACE.equals(((Object) this).getClass().getName());
    }

    @Unique
    private Either<Void, Exception> byepregen$writeRawNow(ChunkPos pos, RawChunkData data) {
        Object pending = this.pendingWrites.remove(pos);
        try {
            RegionFile regionFile = ((RegionFileStorageAccessor) (Object) this.storage).byepregen$getRegionFile(pos);
            try (DataOutputStream output = regionFile.getChunkDataOutputStream(pos)) {
                output.write(data.bytes(), 0, data.length());
            }
            this.byepregen$completePending(pending, null);
            return Either.left(null);
        } catch (Exception exception) {
            this.byepregen$completePending(pending, exception);
            return Either.right(exception);
        }
    }

    @Unique
    private void byepregen$completePending(Object pending, Exception exception) {
        if (pending == null) {
            return;
        }

        CompletableFuture<Void> result = ((IOWorkerPendingStoreAccessor) pending).byepregen$result();
        if (exception == null) {
            result.complete(null);
        } else {
            result.completeExceptionally(exception);
        }
    }
}
