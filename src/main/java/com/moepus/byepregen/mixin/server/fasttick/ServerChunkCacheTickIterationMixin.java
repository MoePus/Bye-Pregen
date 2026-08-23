package com.moepus.byepregen.mixin.server.fasttick;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.server.tick.ChunkTickPermutationIterator;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Iterator;
import java.util.List;

@MixinGate(config = ConfigFlag.FAST_CHUNK_TICKING)
@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class ServerChunkCacheTickIterationMixin {
    @Unique
    private final ChunkTickPermutationIterator byepregen$chunkTickPermutation =
            new ChunkTickPermutationIterator();

    @Redirect(
            method = "tickChunks",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/RandomSource;)V"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerLevel;tickCustomSpawners(ZZ)V"
                    )
            ),
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/RandomSource;)V"
            ),
            require = 1,
            allow = 1
    )
    private void byepregen$prepareChunkTickPermutation(
            List<?> chunks,
            RandomSource random) {
        this.byepregen$chunkTickPermutation.reset(chunks, random);
    }

    @Redirect(
            method = "tickChunks",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/RandomSource;)V"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerLevel;tickCustomSpawners(ZZ)V"
                    )
            ),
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;iterator()Ljava/util/Iterator;"
            ),
            require = 1,
            allow = 1
    )
    private Iterator<?> byepregen$iterateChunkTickPermutation(List<?> chunks) {
        return this.byepregen$chunkTickPermutation.iteratorFor(chunks);
    }
}
