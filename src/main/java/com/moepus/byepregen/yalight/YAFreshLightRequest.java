package com.moepus.byepregen.yalight;

import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;

import java.util.concurrent.CompletableFuture;

public final class YAFreshLightRequest extends CompletableFuture<ChunkAccess> {
    private static final int BLOCK_LAYER = 1;
    private static final int SKY_LAYER = 1 << 1;
    private static final int FAILED = 1 << 2;

    private final ChunkAccess owner;
    private int state;
    private YAFreshLightRequest nextBlockQueue;
    private YAFreshLightRequest nextSkyQueue;

    static YAFreshLightRequest create(ChunkAccess owner, boolean hasBlock, boolean hasSky) {
        int requiredLayers = (hasBlock ? BLOCK_LAYER : 0) | (hasSky ? SKY_LAYER : 0);
        return new YAFreshLightRequest(owner, requiredLayers);
    }

    private YAFreshLightRequest(ChunkAccess owner, int state) {
        this.owner = canonicalOwner(owner);
        this.state = state;
    }

    ChunkAccess owner() {
        return this.owner;
    }

    void markExecuted(LightLayer layer) {
        this.state &= ~mask(layer);
    }

    void markFailed() {
        this.state |= FAILED;
    }

    void cancel() {
        this.markFailed();
    }

    YAFreshLightRequest nextQueued(LightLayer layer) {
        return layer == LightLayer.BLOCK ? this.nextBlockQueue : this.nextSkyQueue;
    }

    void setNextQueued(LightLayer layer, YAFreshLightRequest next) {
        if (layer == LightLayer.BLOCK) {
            this.nextBlockQueue = next;
        } else {
            this.nextSkyQueue = next;
        }
    }

    public boolean succeeded() {
        return this.state == 0;
    }

    public boolean matchesOwner(ChunkAccess other) {
        return sameOwner(this.owner, other);
    }

    static boolean sameOwner(ChunkAccess first, ChunkAccess second) {
        return canonicalOwner(first) == canonicalOwner(second);
    }

    static ChunkAccess canonicalOwner(ChunkAccess chunk) {
        return chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : chunk;
    }

    private static int mask(LightLayer layer) {
        return layer == LightLayer.BLOCK ? BLOCK_LAYER : SKY_LAYER;
    }
}
