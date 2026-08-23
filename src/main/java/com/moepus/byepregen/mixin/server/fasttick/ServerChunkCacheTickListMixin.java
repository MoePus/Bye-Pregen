package com.moepus.byepregen.mixin.server.fasttick;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import net.minecraft.server.level.ServerChunkCache;
import org.mixinlite.injector.MethodScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

// Lithium 1.21.1 ships an alloc.chunk_ticking mixin that redirects the same call, but does not
// register that mixin in either of its mixin configs. Recheck this if a later Lithium enables it.
@MixinGate(config = ConfigFlag.FAST_CHUNK_TICKING, conflictingMods = "servercore")
@Mixin(value = ServerChunkCache.class, remap = false, priority = 900)
public abstract class ServerChunkCacheTickListMixin {
    @Unique
    private final ArrayList<Object> byepregen$cachedTickingChunks = new ArrayList<>();

    @MethodScope(method = "tickChunks", exit = "byepregen$exitTickChunks")
    private ArrayList<Object> byepregen$enterTickChunks() {
        this.byepregen$cachedTickingChunks.clear();
        return this.byepregen$cachedTickingChunks;
    }

    @Unique
    private void byepregen$exitTickChunks(ArrayList<Object> chunks) {
        chunks.clear();
    }

    @Redirect(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayListWithCapacity(I)Ljava/util/ArrayList;"
            ),
            require = 1,
            allow = 1
    )
    private ArrayList<Object> byepregen$reuseTickingChunkList(
            int initialArraySize) {
        return this.byepregen$cachedTickingChunks;
    }
}
